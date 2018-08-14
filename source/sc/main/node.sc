(define (db-node-values->data values result) (status-t db-node-values-t db-node-data-t*)
  "convert a node-values array to the data format that is used as btree value for nodes.
  the data for unset trailing fields is not included"
  status-declare
  (declare
    data void*
    data-temp uint8-t*
    field-size uint8-t
    field-type db-field-type-t
    i db-fields-len-t
    size size-t)
  (set size 0)
  (sc-comment "prepare size information")
  (for ((set i 0) (<= i values.last) (set i (+ 1 i)))
    (if (< i values.type:fields-fixed-count)
      (begin
        (sc-comment "fixed length field")
        (set
          field-type (struct-get (array-get values.type:fields i) type)
          field-size (db-field-type-size field-type)
          (struct-get (array-get values.data i) size) field-size
          size (+ field-size size)))
      (begin
        (set
          field-size (struct-get (array-get values.data i) size)
          size (+ (sizeof db-data-len-t) field-size size))
        (sc-comment "check if data is larger than the size prefix can specify")
        (if (> field-size db-data-len-max)
          (status-set-both-goto db-status-group-db db-status-id-data-length)))))
  (db-malloc data size)
  (set data-temp data)
  (sc-comment "copy data")
  (for ((set i 0) (<= i values.last) (set i (+ 1 i)))
    (set field-size (struct-get (array-get values.data i) size))
    (if (>= i values.type:fields-fixed-count)
      (set
        (pointer-get (convert-type data-temp db-data-len-t*)) field-size
        data-temp (+ (sizeof db-data-len-t) data-temp)))
    (memcpy data-temp (struct-get (array-get values.data i) data) field-size)
    (set data-temp (+ field-size data-temp)))
  (set
    result:data data
    result:size size)
  (label exit
    (return status)))

(define (db-node-values-new type result) (status-t db-type-t* db-node-values-t*)
  "allocate memory for a new node values array"
  status-declare
  (declare data db-node-value-t*)
  (db-calloc data type:fields-len (sizeof db-node-value-t))
  (struct-set *result
    type type
    data data
    last 0)
  (label exit
    (return status)))

(define (db-node-values-free a) (void db-node-values-t*) (free-and-set-null a:data))

(define (db-node-values-set values field data size)
  (void db-node-values-t* db-fields-len-t void* size-t)
  "set a value for a field in node values.
  size is ignored for fixed length types"
  (declare
    field-type db-field-type-t
    values-temp db-node-values-t)
  (set
    values-temp *values
    field-type (struct-get (array-get values-temp.type:fields field) type))
  (struct-set (array-get values-temp.data field)
    data data
    size
    (if* (db-field-type-is-fixed field-type) (db-field-type-size field-type)
      size))
  (if (or (not values-temp.last) (> field values-temp.last)) (set values-temp.last field))
  (set *values values-temp))

(define (db-node-create txn values result) (status-t db-txn-t db-node-values-t db-id-t*)
  status-declare
  db-mdb-declare-val-id
  (db-mdb-cursor-declare nodes)
  (declare
    val-data MDB-val
    id db-id-t
    node-data db-node-data-t)
  (set
    node-data.data 0
    val-id.mv-data &id)
  (status-require (db-node-values->data values &node-data))
  (set
    val-data.mv-data node-data.data
    val-data.mv-size node-data.size)
  (db-mdb-status-require (db-mdb-env-cursor-open txn nodes))
  (sc-comment "sequence updated as late as possible")
  (status-require (db-sequence-next txn.env values.type:id &id))
  (db-mdb-status-require (mdb-cursor-put nodes &val-id &val-data 0))
  (db-mdb-cursor-close nodes)
  (status-require (db-indices-entry-ensure txn values id))
  (set *result id)
  (label exit
    (db-mdb-cursor-close-if-active nodes)
    (free node-data.data)
    (return status)))

(define (db-node-data-ref type data field)
  (db-node-data-t db-type-t* db-node-data-t db-fields-len-t)
  "from the full btree value a node (all fields), return a reference
  to the data for specific field and the size"
  (declare
    data-temp uint8-t*
    end uint8-t*
    i db-fields-len-t
    offset size-t
    result db-node-data-t
    size size-t)
  (if (< field type:fields-fixed-count)
    (begin
      (sc-comment "fixed length field")
      (set offset (array-get type:fields-fixed-offsets field))
      (if (< offset data.size)
        (set
          result.data (+ offset (convert-type data.data uint8-t*))
          result.size (db-field-type-size (struct-get (array-get type:fields field) type)))
        (set
          result.data 0
          result.size 0))
      (return result))
    (begin
      (sc-comment "variable length field")
      (set offset
        (if* type:fields-fixed-count (array-get type:fields-fixed-offsets type:fields-fixed-count)
          0))
      (if (< offset data.size)
        (begin
          (set
            data-temp (+ offset (convert-type data.data uint8-t*))
            end (+ data.size (convert-type data.data uint8-t*))
            i type:fields-fixed-count)
          (sc-comment "variable length data is prefixed by its size")
          (while (and (<= i field) (< data-temp end))
            (set
              size (pointer-get (convert-type data-temp db-data-len-t*))
              data-temp (+ (sizeof db-data-len-t) data-temp))
            (if (= i field)
              (begin
                (set
                  result.data data-temp
                  result.size size)
                (return result)))
            (set
              i (+ 1 i)
              data-temp (+ size data-temp)))))
      (set
        result.data 0
        result.size 0)
      (return result))))

(define (db-node-ref selection field) (db-node-data-t db-node-selection-t* db-fields-len-t)
  "return a reference to the data for a field without copying"
  (return (db-node-data-ref selection:type selection:current field)))

(define (db-free-node-values values) (void db-node-values-t*) (free-and-set-null values:data))

(define (db-node-data->values type data result)
  (status-t db-type-t* db-node-data-t db-node-values-t*)
  status-declare
  (declare
    field-data db-node-data-t
    fields-len db-fields-len-t
    values db-node-values-t
    i db-fields-len-t)
  (set fields-len type:fields-len)
  (status-require (db-node-values-new type &values))
  (for ((set i 0) (< i fields-len) (set i (+ 1 i)))
    (set field-data (db-node-data-ref type data i))
    (if (not field-data.data) break)
    (db-node-values-set &values i field-data.data field-data.size))
  (set *result values)
  (label exit
    (if status-is-failure (db-free-node-values &values))
    (return status)))

(define (db-node-read selection count result-nodes)
  (status-t db-node-selection-t* db-count-t db-nodes-t*)
  status-declare
  db-mdb-declare-val-id
  (declare
    count db-count-t
    val-data MDB-val
    matcher db-node-matcher-t
    matcher-state void*
    node-data db-node-data-t
    id db-id-t
    ids db-ids-t
    skip boolean
    match boolean
    type-id db-type-id-t)
  (set
    matcher selection:matcher
    matcher-state selection:matcher-state
    skip (bit-and selection:options db-selection-flag-skip)
    count selection:count
    ids selection:ids)
  (if ids.start
    (begin
      (sc-comment "filter by ids")
      (while (and (i-array-in-range ids) count)
        (set
          val-id.mv-data ids.current
          status.id (mdb-cursor-get selection:cursor &val-id &val-data MDB-SET-KEY))
        (if db-mdb-status-is-success
          (begin
            (if matcher
              (set
                node-data.data val-data.mv-data
                node-data.size val-data.mv-size
                match (matcher (i-array-get ids) node-data matcher-state))
              (set match #t))
            (if match
              (begin
                (if (not skip)
                  (set
                    id (db-pointer->id val-id.mv-data)
                    selection:type (db-type-get-by-id selection:env (db-id-type id))
                    selection:current.data val-data.mv-data
                    selection:current.size val-data.mv-size
                    selection:current-id id))
                (set count (- count 1)))))
          db-mdb-status-expect-notfound)
        (i-array-forward ids))
      (set selection:ids ids))
    (begin
      (sc-comment "filter by type")
      (set type-id selection:type:id)
      (db-mdb-status-require (mdb-cursor-get selection:cursor &val-id &val-data MDB-GET-CURRENT))
      (while
        (and
          db-mdb-status-is-success count (= type-id (db-id-type (db-pointer->id val-id.mv-data))))
        (if matcher
          (set
            node-data.data val-data.mv-data
            node-data.size val-data.mv-size
            match (matcher (db-pointer->id val-id.mv-data) node-data matcher-state))
          (set match #t))
        (if match
          (begin
            (if (not skip)
              (set
                selection:current.data val-data.mv-data
                selection:current.size val-data.mv-size
                selection:current-id (db-pointer->id val-id.mv-data)))
            (set count (- count 1))))
        (set status.id (mdb-cursor-get selection:cursor &val-id &val-data MDB-NEXT-NODUP)))))
  (label exit
    db-mdb-status-notfound-if-notfound
    (return status)))

(define (db-node-skip selection count) (status-t db-node-selection-t* db-count-t)
  "read the next count matches and position selection afterwards"
  status-declare
  (set
    selection:options (bit-or selection:options db-selection-flag-skip)
    selection:count count
    status (db-node-next selection)
    selection:options (bit-xor selection:options db-selection-flag-skip)
    selection:count 1)
  (return status))

(define (db-node-select txn ids type offset matcher matcher-state result-selection)
  (status-t db-txn-t db-ids-t* db-type-t* db-count-t db-node-matcher-t void* db-node-selection-t*)
  "select nodes optionally filtered by either a list of ids or type.
  ids: zero if unused
  type: zero if unused
  offset: skip this number of matches first
  matcher: zero if unused. a function that is called for each node after filtering by ids or type
  matcher-state: zero if unused. a pointer passed to each call of matcher"
  status-declare
  db-mdb-declare-val-id
  db-mdb-declare-val-null
  (db-mdb-cursor-declare nodes)
  (declare id db-id-t)
  (db-mdb-status-require (db-mdb-env-cursor-open txn nodes))
  (sc-comment "if ids filter set just check if database is empty")
  (if ids
    (begin
      (db-mdb-status-require (mdb-cursor-get nodes &val-null &val-null MDB-FIRST))
      (set result-selection:ids *ids))
    (begin
      (set
        id (db-id-add-type 0 type:id)
        val-id.mv-data &id)
      (i-array-set-null result-selection:ids)
      (db-mdb-status-require (mdb-cursor-get nodes &val-id &val-null MDB-SET-RANGE))
      (if (not (= type:id (db-id-type (db-pointer->id val-id.mv-data))))
        (status-set-id-goto db-status-id-notfound))))
  (set
    result-selection:cursor nodes
    result-selection:count 1
    result-selection:env txn.env
    result-selection:matcher matcher
    result-selection:matcher-state matcher-state
    result-selection:options 0
    result-selection:type type)
  (if offset (set status (db-node-skip result-selection offset)))
  (label exit
    (if (not status-is-success)
      (begin
        (mdb-cursor-close nodes)
        db-mdb-status-notfound-if-notfound))
    (return status)))

(define (db-node-get-internal nodes id result) (status-t MDB-cursor* db-id-t db-node-data-t*)
  "used in node-get and node-index-get"
  status-declare
  db-mdb-declare-val-id
  (declare val-data MDB-val)
  (set val-id.mv-data &id)
  (set status.id (mdb-cursor-get nodes &val-id &val-data MDB-SET-KEY))
  (if db-mdb-status-is-success
    (set
      result:data val-data.mv-data
      result:size val-data.mv-size)
    (if db-mdb-status-is-notfound db-mdb-status-notfound-if-notfound
      (set status.group db-status-group-lmdb)))
  (return status))

(define (db-node-get txn id result) (status-t db-txn-t db-id-t db-node-data-t*)
  "get a reference to data for one node identified by id.
  fields can be accessed with db-node-data-ref.
  if node could not be found, status is status-id-notfound"
  status-declare
  (db-mdb-cursor-declare nodes)
  (db-mdb-status-require (db-mdb-env-cursor-open txn nodes))
  (set status (db-node-get-internal nodes id result))
  (label exit
    (db-mdb-cursor-close nodes)
    (return status)))

(define (db-graph-internal-delete left right label ordinal graph-lr graph-rl graph-ll)
  (status-t
    db-ids-t* db-ids-t* db-ids-t* db-ordinal-condition-t* MDB-cursor* MDB-cursor* MDB-cursor*)
  "declare because it is defined later")

(define (db-node-delete txn ids-pointer) (status-t db-txn-t db-ids-t*)
  "delete a node and all its relations"
  status-declare
  db-mdb-declare-val-id
  (declare
    ids db-ids-t
    id db-id-t
    val-data MDB-val
    values db-node-values-t
    node-data db-node-data-t)
  (set ids *ids-pointer)
  (db-mdb-cursor-declare nodes)
  (db-mdb-cursor-declare graph-lr)
  (db-mdb-cursor-declare graph-rl)
  (db-mdb-cursor-declare graph-ll)
  (sc-comment
    "first delete references."
    "return if ids-pointer is zero because in db-graph-internal-delete it would mean non-filter and match all.")
  (if (not ids-pointer) (return status))
  (db-mdb-status-require (db-mdb-env-cursor-open txn graph-lr))
  (db-mdb-status-require (db-mdb-env-cursor-open txn graph-rl))
  (db-mdb-status-require (db-mdb-env-cursor-open txn graph-ll))
  (status-require (db-graph-internal-delete ids-pointer 0 0 0 graph-lr graph-rl graph-ll))
  (status-require (db-graph-internal-delete 0 ids-pointer 0 0 graph-lr graph-rl graph-ll))
  (status-require (db-graph-internal-delete 0 0 ids-pointer 0 graph-lr graph-rl graph-ll))
  (db-mdb-status-require (db-mdb-env-cursor-open txn nodes))
  (sc-comment "delete node and index btree entries")
  (while (i-array-in-range ids)
    (set val-id.mv-data ids.current)
    (set status.id (mdb-cursor-get nodes &val-id &val-data MDB-SET-KEY))
    (if db-mdb-status-is-success
      (begin
        (set
          id (i-array-get ids)
          node-data.data val-data.mv-data
          node-data.size val-data.mv-size)
        (status-require
          (db-node-data->values (db-type-get-by-id txn.env (db-id-type id)) node-data &values))
        (status-require (db-indices-entry-delete txn values id))
        (db-mdb-status-require (mdb-cursor-del nodes 0)))
      (if db-mdb-status-is-notfound (set status.id status-id-success)
        (status-set-group-goto db-status-group-lmdb)))
    (i-array-forward ids))
  (label exit
    (db-mdb-cursor-close-if-active graph-lr)
    (db-mdb-cursor-close-if-active graph-rl)
    (db-mdb-cursor-close-if-active graph-ll)
    (db-mdb-cursor-close-if-active nodes)
    (return status)))

(define (db-node-selection-finish a) (void db-node-selection-t*)
  (db-mdb-cursor-close-if-active a:cursor))

(define (db-node-update txn id values) (status-t db-txn-t db-id-t db-node-values-t)
  "set new data for the node with the given id"
  status-declare
  db-mdb-declare-val-id
  (db-mdb-cursor-declare nodes)
  (declare
    val-data MDB-val
    node-data db-node-data-t)
  (set
    val-id.mv-data &id
    node-data.data 0)
  (status-require (db-node-values->data values &node-data))
  (db-mdb-status-require (db-mdb-env-cursor-open txn nodes))
  (db-mdb-status-require (mdb-cursor-get nodes &val-id &val-data MDB-SET))
  (set
    val-data.mv-data node-data.data
    val-data.mv-size node-data.size)
  (db-mdb-status-require (mdb-cursor-put nodes &val-id &val-data 0))
  (db-mdb-cursor-close nodes)
  (status-require (db-indices-entry-ensure txn values id))
  (label exit
    (db-mdb-cursor-close-if-active nodes)
    (free node-data.data)
    (return status)))

(define (db-node-exists txn ids result) (status-t db-txn-t db-ids-t boolean*)
  "true if all given ids are ids of existing nodes, false otherwise"
  status-declare
  db-mdb-declare-val-id
  db-mdb-declare-val-null
  (db-mdb-cursor-declare nodes)
  (db-mdb-status-require (db-mdb-env-cursor-open txn nodes))
  (while (i-array-in-range ids)
    (set
      val-id.mv-data ids.current
      status.id (mdb-cursor-get nodes &val-id &val-null MDB-SET))
    (if db-mdb-status-is-notfound
      (begin
        (set
          *result #f
          status.id status-id-success)
        (goto exit))
      (if (not status-is-success) status-goto))
    (i-array-forward ids))
  (set *result #t)
  (label exit
    (mdb-cursor-close nodes)
    (return status)))

(define (db-node-index-read selection count result-nodes)
  (status-t db-node-index-selection-t db-count-t db-nodes-t*)
  status-declare
  (status-require (db-index-read selection.index-selection count result))
  (status-require
    (db-node-get-internal selection.nodes selection.index-selection.current &selection.current))
  (set selection.current-id selection.index-selection.current)
  (label exit
    (return status)))

(define (db-node-index-selection-finish selection) (void db-node-index-selection-t*)
  (db-index-selection-finish &selection:index-selection)
  (db-mdb-cursor-close-if-active selection:nodes))

(define (db-node-index-select txn index values result)
  (status-t db-txn-t db-index-t db-node-values-t db-node-index-selection-t*)
  status-declare
  (db-mdb-cursor-declare nodes)
  (declare index-selection db-index-selection-t)
  (status-require (db-index-select txn index values &index-selection))
  (db-mdb-status-require (db-mdb-env-cursor-open txn nodes))
  (set
    result:index-selection index-selection
    result:nodes nodes)
  (label exit
    (if status-is-failure (db-mdb-cursor-close-if-active nodes))
    (return status)))