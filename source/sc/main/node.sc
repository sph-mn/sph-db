(define (db-node-values->data values result) (status-t db-node-values-t db-node-t*)
  "convert a node-values array to the data format that is used as btree value for nodes.
  the data for unset trailing fields is not included"
  status-declare
  (declare
    data void*
    data-temp uint8-t*
    field-data void*
    field-size uint8-t
    field-type db-field-type-t
    i db-fields-len-t
    size size-t)
  (set size 0)
  (sc-comment "prepare size information")
  (for ((set i 0) (< i values.extent) (set i (+ 1 i)))
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
  (if size (db-malloc data size)
    (set data 0))
  (set data-temp data)
  (sc-comment "copy data")
  (for ((set i 0) (< i values.extent) (set i (+ 1 i)))
    (set field-size (struct-get (array-get values.data i) size))
    (if (>= i values.type:fields-fixed-count)
      (set
        (pointer-get (convert-type data-temp db-data-len-t*)) field-size
        data-temp (+ (sizeof db-data-len-t) data-temp)))
    (set field-data (struct-get (array-get values.data i) data))
    (sc-comment "field-data pointer is zero for unset fields")
    (if (not field-data) (memset data-temp 0 field-size)
      (memcpy data-temp field-data field-size))
    (set data-temp (+ field-size data-temp)))
  (set
    result:data data
    result:size size)
  (label exit
    (return status)))

(define (db-node-values-new type result) (status-t db-type-t* db-node-values-t*)
  "allocate memory for a new node values array.
  extent is last field index plus one"
  status-declare
  (declare data db-node-value-t*)
  (db-calloc data type:fields-len (sizeof db-node-value-t))
  (struct-set *result
    type type
    data data
    extent 0)
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
  (if (or (= 0 values-temp.extent) (>= field values-temp.extent))
    (set values-temp.extent (+ 1 field)))
  (set *values values-temp))

(define (db-node-create txn values result) (status-t db-txn-t db-node-values-t db-id-t*)
  status-declare
  db-mdb-declare-val-id
  (db-mdb-cursor-declare nodes)
  (declare
    val-data MDB-val
    id db-id-t
    node db-node-t)
  (set
    node.data 0
    val-id.mv-data &id)
  (status-require (db-node-values->data values &node))
  (set
    val-data.mv-data node.data
    val-data.mv-size node.size)
  (db-mdb-status-require (db-mdb-env-cursor-open txn nodes))
  (sc-comment "sequence updated as late as possible")
  (status-require (db-sequence-next txn.env values.type:id &id))
  (db-mdb-status-require (mdb-cursor-put nodes &val-id &val-data 0))
  (db-mdb-cursor-close nodes)
  (if values.extent (status-require (db-indices-entry-ensure txn values id)))
  (set *result id)
  (label exit
    (db-mdb-cursor-close-if-active nodes)
    (free node.data)
    (return status)))

(define (db-node-ref type node field) (db-node-value-t db-type-t* db-node-t db-fields-len-t)
  "from the full btree value a node (all fields), return a reference
  to the data for specific field and the size"
  (declare
    data-temp uint8-t*
    end uint8-t*
    i db-fields-len-t
    offset size-t
    result db-node-value-t
    size size-t)
  (if (< field type:fields-fixed-count)
    (begin
      (sc-comment "fixed length field")
      (set offset (array-get type:fields-fixed-offsets field))
      (if (< offset node.size)
        (set
          result.data (+ offset (convert-type node.data uint8-t*))
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
      (if (< offset node.size)
        (begin
          (set
            data-temp (+ offset (convert-type node.data uint8-t*))
            end (+ node.size (convert-type node.data uint8-t*))
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

(define (db-free-node-values values) (void db-node-values-t*) (free-and-set-null values:data))

(define (db-node-data->values type data result) (status-t db-type-t* db-node-t db-node-values-t*)
  status-declare
  (declare
    field-data db-node-value-t
    fields-len db-fields-len-t
    values db-node-values-t
    i db-fields-len-t)
  (set fields-len type:fields-len)
  (status-require (db-node-values-new type &values))
  (for ((set i 0) (< i fields-len) (set i (+ 1 i)))
    (set field-data (db-node-ref type data i))
    (if (not field-data.data) break)
    (db-node-values-set &values i field-data.data field-data.size))
  (set *result values)
  (label exit
    (if status-is-failure (db-free-node-values &values))
    (return status)))

(define (db-node-read selection count result-nodes)
  (status-t db-node-selection-t db-count-t db-nodes-t*)
  status-declare
  db-mdb-declare-val-id
  (declare
    val-data MDB-val
    matcher db-node-matcher-t
    matcher-state void*
    node db-node-t
    id db-id-t
    skip boolean
    match boolean
    type-id db-type-id-t)
  (set
    matcher selection.matcher
    matcher-state selection.matcher-state
    skip (bit-and selection.options db-selection-flag-skip)
    type-id selection.type:id)
  (db-mdb-status-require (mdb-cursor-get selection.cursor &val-id &val-data MDB-GET-CURRENT))
  (while
    (and db-mdb-status-is-success count (= type-id (db-id-type (db-pointer->id val-id.mv-data))))
    (if matcher
      (set
        node.id (db-pointer->id val-id.mv-data)
        node.data val-data.mv-data
        node.size val-data.mv-size
        match (matcher node matcher-state))
      (set match #t))
    (if match
      (begin
        (if (not skip)
          (begin
            (set
              node.id (db-pointer->id val-id.mv-data)
              node.data val-data.mv-data
              node.size val-data.mv-size)
            (i-array-add *result-nodes node)))
        (set count (- count 1))))
    (db-mdb-status-require (mdb-cursor-get selection.cursor &val-id &val-data MDB-NEXT-NODUP)))
  (label exit
    db-mdb-status-notfound-if-notfound
    (return status)))

(define (db-node-skip selection count) (status-t db-node-selection-t db-count-t)
  "skip the next count matches"
  status-declare
  (set
    selection.options (bit-or selection.options db-selection-flag-skip)
    status (db-node-read selection count 0)
    selection.options (bit-xor selection.options db-selection-flag-skip))
  (return status))

(define (db-node-select txn type offset matcher matcher-state result-selection)
  (status-t db-txn-t db-type-t* db-count-t db-node-matcher-t void* db-node-selection-t*)
  "get nodes by type and optionally filtering data.
  result count is unknown on call or can be large, that is why a selection state
  for partial reading is used.
  offset: skip this number of matches first.
  matcher: zero if unused. a function that is called for each node of type
  matcher-state: zero if unused. a pointer passed to each call of matcher"
  status-declare
  db-mdb-declare-val-id
  db-mdb-declare-val-null
  (db-mdb-cursor-declare nodes)
  (declare id db-id-t)
  (db-mdb-status-require (db-mdb-env-cursor-open txn nodes))
  (sc-comment "position at first node of type")
  (set
    id (db-id-add-type 0 type:id)
    val-id.mv-data &id)
  (db-mdb-status-require (mdb-cursor-get nodes &val-id &val-null MDB-SET-RANGE))
  (if (not (= type:id (db-id-type (db-pointer->id val-id.mv-data))))
    (status-set-id-goto db-status-id-notfound))
  (set
    result-selection:type type
    result-selection:cursor nodes
    result-selection:matcher matcher
    result-selection:matcher-state matcher-state
    result-selection:options 0)
  (if offset (set status (db-node-skip *result-selection offset)))
  (label exit
    (if status-is-failure
      (begin
        (mdb-cursor-close nodes)
        db-mdb-status-notfound-if-notfound))
    (return status)))

(define (db-node-get-internal nodes-cursor ids result-nodes)
  (status-t MDB-cursor* db-ids-t db-nodes-t*)
  "get nodes by id.
  returns and status is notfound if any id could not be found.
  like node-get with a given mdb-cursor"
  status-declare
  db-mdb-declare-val-id
  (declare
    val-data MDB-val
    node db-node-t)
  (while (i-array-in-range ids)
    (set
      val-id.mv-data ids.current
      status.id (mdb-cursor-get nodes-cursor &val-id &val-data MDB-SET-KEY))
    (if db-mdb-status-is-success
      (begin
        (set
          node.id (i-array-get ids)
          node.data val-data.mv-data
          node.size val-data.mv-size)
        (i-array-add *result-nodes node))
      (if db-mdb-status-is-notfound (status-set-id-goto db-status-id-notfound)
        (status-set-group-goto db-status-group-lmdb)))
    (i-array-forward ids))
  (label exit
    (return status)))

(define (db-node-get txn ids result-nodes) (status-t db-txn-t db-ids-t db-nodes-t*)
  "get a reference to data for one node identified by id.
  fields can be accessed with db-node-ref.
  if node could not be found, status is status-id-notfound"
  status-declare
  (db-mdb-cursor-declare nodes)
  (db-mdb-status-require (db-mdb-env-cursor-open txn nodes))
  (set status (db-node-get-internal nodes ids result-nodes))
  (label exit
    (db-mdb-cursor-close nodes)
    (return status)))

(define (db-graph-internal-delete left right label ordinal graph-lr graph-rl graph-ll)
  (status-t
    db-ids-t* db-ids-t* db-ids-t* db-ordinal-condition-t* MDB-cursor* MDB-cursor* MDB-cursor*)
  "declare because it is defined later")

(define (db-node-delete txn ids) (status-t db-txn-t db-ids-t)
  "delete nodes and all their relations"
  status-declare
  db-mdb-declare-val-id
  (declare
    id db-id-t
    val-data MDB-val
    values db-node-values-t
    node db-node-t)
  (db-mdb-cursor-declare nodes)
  (db-mdb-cursor-declare graph-lr)
  (db-mdb-cursor-declare graph-rl)
  (db-mdb-cursor-declare graph-ll)
  (sc-comment "first delete references")
  (db-mdb-status-require (db-mdb-env-cursor-open txn graph-lr))
  (db-mdb-status-require (db-mdb-env-cursor-open txn graph-rl))
  (db-mdb-status-require (db-mdb-env-cursor-open txn graph-ll))
  (status-require (db-graph-internal-delete &ids 0 0 0 graph-lr graph-rl graph-ll))
  (status-require (db-graph-internal-delete 0 &ids 0 0 graph-lr graph-rl graph-ll))
  (status-require (db-graph-internal-delete 0 0 &ids 0 graph-lr graph-rl graph-ll))
  (db-mdb-status-require (db-mdb-env-cursor-open txn nodes))
  (sc-comment "delete node and index btree entries")
  (while (i-array-in-range ids)
    (set
      val-id.mv-data ids.current
      status.id (mdb-cursor-get nodes &val-id &val-data MDB-SET-KEY))
    (if db-mdb-status-is-success
      (begin
        (set
          id (i-array-get ids)
          node.data val-data.mv-data
          node.size val-data.mv-size)
        (status-require
          (db-node-data->values (db-type-get-by-id txn.env (db-id-type id)) node &values))
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

(define (db-node-delete-type txn type-id) (status-t db-txn-t db-type-id-t)
  status-declare
  (sc-comment "delete all nodes of type and all their relations")
  db-mdb-declare-val-id
  db-mdb-declare-val-null
  (db-mdb-cursor-declare nodes)
  (declare id db-id-t)
  (db-mdb-status-require (db-mdb-env-cursor-open txn nodes))
  (set
    id (db-id-add-type (convert-type 0 db-id-t) type-id)
    val-id.mv-data &id)
  (db-mdb-status-require (mdb-cursor-get nodes &val-id &val-null MDB-SET-RANGE))
  (while (and db-mdb-status-is-success (= type-id (db-id-type (db-pointer->id val-id.mv-data))))
    (db-mdb-status-require (mdb-cursor-del nodes 0))
    (set status.id (mdb-cursor-get nodes &val-id &val-null MDB-NEXT-NODUP)))
  (label exit
    db-mdb-status-notfound-if-notfound
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
    node db-node-t)
  (set
    val-id.mv-data &id
    node.data 0)
  (status-require (db-node-values->data values &node))
  (db-mdb-status-require (db-mdb-env-cursor-open txn nodes))
  (db-mdb-status-require (mdb-cursor-get nodes &val-id &val-data MDB-SET))
  (set
    val-data.mv-data node.data
    val-data.mv-size node.size)
  (db-mdb-status-require (mdb-cursor-put nodes &val-id &val-data 0))
  (db-mdb-cursor-close nodes)
  (status-require (db-indices-entry-ensure txn values id))
  (label exit
    (db-mdb-cursor-close-if-active nodes)
    (free node.data)
    (return status)))

(define (db-node-select-delete txn type matcher matcher-state)
  (status-t db-txn-t db-type-t* db-node-matcher-t void*)
  "delete nodes selected by type or custom matcher routine.
  collects ids in batches and calls db-node-delete"
  status-declare
  (i-array-declare ids db-ids-t)
  (i-array-declare nodes db-nodes-t)
  (db-node-selection-declare selection)
  (status-require (db-node-select txn type 0 matcher matcher-state &selection))
  (db-nodes-new db-batch-len &nodes)
  (db-ids-new db-batch-len &ids)
  (do-while status-is-success
    (status-require-read (db-node-read selection db-batch-len &nodes))
    (if (not (i-array-length nodes)) continue)
    (db-nodes->ids nodes &ids)
    (status-require (db-node-delete txn ids))
    (i-array-clear ids)
    (i-array-clear nodes))
  (label exit
    (db-node-selection-finish &selection)
    (i-array-free ids)
    (i-array-free nodes)
    (return status)))

(define (db-node-index-read selection count result-nodes)
  (status-t db-node-index-selection-t db-count-t db-nodes-t*)
  status-declare
  (db-ids-declare ids)
  (db-ids-new count &ids)
  (status-require-read (db-index-read selection.index-selection count &ids))
  (status-require (db-node-get-internal selection.nodes-cursor ids result-nodes))
  (label exit
    (return status)))

(define (db-node-index-selection-finish selection) (void db-node-index-selection-t*)
  (db-index-selection-finish &selection:index-selection)
  (db-mdb-cursor-close-if-active selection:nodes-cursor))

(define (db-node-index-select txn index values result-selection)
  (status-t db-txn-t db-index-t db-node-values-t db-node-index-selection-t*)
  status-declare
  (db-mdb-cursor-declare nodes)
  (db-index-selection-declare index-selection)
  (status-require (db-index-select txn index values &index-selection))
  (db-mdb-status-require (db-mdb-env-cursor-open txn nodes))
  (set
    result-selection:index-selection index-selection
    result-selection:nodes-cursor nodes)
  (label exit
    (if status-is-failure (db-mdb-cursor-close-if-active nodes))
    (return status)))