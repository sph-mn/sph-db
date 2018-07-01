(define (db-node-index-key index values result-data result-size)
  (status-t db-index-t db-node-values-t b0** size-t*)
  "calculate size and prepare data"
  status-declare
  (declare
    value-size size-t
    data b8*
    i db-field-count-t
    size size-t
    data-temp b0*)
  (for ((set i 0) (< i index.fields-len) (set i (+ 1 i)))
    (set size (+ size (struct-get (array-get values.data (array-get index.fields i)) size))))
  (if (< txn.env:maxkeysize size)
    (status-set-both-goto db-status-group-db db-status-id-index-keysize))
  (db-malloc data size)
  (set data-temp data)
  (for ((set i 0) (< i index.fields-len) (set i (+ 1 i)))
    (set value-size (struct-get (array-get values.data (array-get index.fields i)) size))
    (memcpy data-temp (struct-get (array-get values.data i) data) value-size)
    (set data-temp (+ value-size data-temp)))
  (set
    *result-data data
    *result-size size)
  (label exit
    (return status)))

(define (db-node-indices-ensure txn values id) (status-t db-txn-t db-node-values-t db-id-t)
  "create entries in all indices for id and values.
  index: field-data ... -> id"
  status-declare
  db-mdb-declare-val-id
  (db-mdb-cursor-declare node-index-cursor)
  (declare
    data b0*
    val-data MDB-val
    size size-t
    i db-index-count-t
    node-index db-index-t
    node-indices db-index-t*
    node-indices-len db-index-count-t)
  (set
    val-id.mv-data &id
    data 0
    node-indices-len values.type:indices-len
    node-indices values.type:indices)
  (for ((set i 0) (< i node-indices-len) (set i (+ 1 i)))
    (set node-index (array-get node-indices i))
    (status-require (db-node-index-key node-index values &data &val-data.mv-size))
    (set val-data.mv-data data)
    (db-mdb-status-require (mdb-cursor-open txn.mdb-txn node-index.dbi &node-index-cursor))
    (db-mdb-cursor-put node-index-cursor val-data val-id)
    (db-mdb-cursor-close node-index-cursor))
  (label exit
    (db-mdb-cursor-close-if-active node-index-cursor)
    (if data (free data))
    (return status)))

(define (db-node-indices-delete txn values id) (status-t db-txn-t db-node-values-t db-id-t)
  "delete all entries from all indices for id and values"
  status-declare
  db-mdb-declare-val-id
  (db-mdb-cursor-declare node-index-cursor)
  (declare
    data b8*
    val-data MDB-val
    i db-index-count-t
    node-index db-index-t
    node-indices db-index-t*
    node-indices-len db-index-count-t)
  (set
    val-id.mv-data &id
    data 0
    node-indices-len values.type:indices-len
    node-indices values.type:indices)
  (for ((set i 0) (< i node-indices-len) (set i (+ 1 i)))
    (set node-index (array-get node-indices i))
    (status-require (db-node-index-key node-index values &data &val-data.mv-size))
    (set val-data.mv-data data)
    ; delete
    (db-mdb-status-require (mdb-cursor-open txn.mdb-txn node-index.dbi &node-index-cursor))
    (db-mdb-cursor-put node-index-cursor val-data val-id)
    (db-mdb-cursor-get-norequire node-index-cursor val-data val-id MDB-GET-BOTH)
    (if db-status-is-success (db-mdb-cursor-del node-index-cursor))
    (db-mdb-cursor-close node-index-cursor))
  (label exit
    (db-mdb-cursor-close-if-active node-index-cursor)
    (if data (free data))
    (return status)))

(define (db-node-values->data values result result-size) (status-t db-node-values-t b0** size-t*)
  status-declare
  (declare
    i db-field-count-t
    field-count db-field-count-t
    field-size b8
    size size-t
    data b0*
    data-temp b8*
    field-type db-field-type-t)
  (set
    field-count values.type:fields-len
    size 0)
  (sc-comment "prepare size information")
  (for ((set i 0) (< i field-count) (set i (+ 1 i)))
    (if (>= i values.type:fields-fixed-count)
      (set field-size (struct-get (array-get values.data i) size))
      (begin
        (set
          field-type (struct-get (array-get values.type:fields i) type)
          field-size (db-field-type-size field-type)
          (struct-get (array-get values.data i) size) field-size)))
    (if (> field-size db-data-len-max)
      (status-set-both-goto db-status-group-db db-status-id-data-length))
    (set size (+ field-size size)))
  (db-malloc data size)
  (set data-temp data)
  (sc-comment "copy data")
  (for ((set i 0) (< i field-count) (set i (+ 1 i)))
    (set field-size (struct-get (array-get values.data i) size))
    (if (>= i values.type:fields-fixed-count)
      (set
        (pointer-get (convert-type data-temp db-data-len-t*)) field-size
        data-temp (+ (sizeof db-data-len-t) data-temp)))
    (memcpy data-temp (struct-get (array-get values.data i) data) field-size)
    (set data-temp (+ size data-temp)))
  (set
    *result data
    *result-size size)
  (label exit
    (return status)))

(define (db-node-values-new type result) (status-t db-type-t* db-node-values-t*)
  "allocate memory for a new node values array"
  status-declare
  (declare data db-node-value-t*)
  (db-malloc data (* type:fields-len (sizeof db-node-value-t)))
  (struct-set *result
    type type
    data data)
  (label exit
    (return status)))

(define (db-node-values-set values field-index data size)
  (b0 db-node-values-t db-field-count-t b0* size-t)
  "set a value for a field in node values.
  size is ignored for fixed length types"
  (declare field-type db-field-type-t)
  (set field-type (struct-get (array-get values.type:fields field-index) type))
  (struct-set (array-get values.data field-index)
    data data
    size
    (if* (db-field-type-fixed? field-type) (db-field-type-size field-type)
      size)))

(define (db-node-create txn values result) (status-t db-txn-t db-node-values-t db-id-t*)
  status-declare
  db-mdb-declare-val-id
  (db-mdb-cursor-declare nodes)
  (declare
    val-data MDB-val
    data b0*
    id db-id-t)
  (set
    data 0
    val-id.mv-data &id)
  (status-require (db-node-values->data values &data &val-data.mv-size))
  (set val-data.mv-data data)
  (db-mdb-cursor-open txn nodes)
  (sc-comment "sequence updated as late as possible")
  (status-require (db-sequence-next txn.env values.type:id &id))
  (db-cursor-put nodes val-id val-data)
  (db-mdb-cursor-close nodes)
  (status-require (db-node-indices-ensure txn values id))
  (set *result id)
  (label exit
    (db-mdb-cursor-close-if-active nodes)
    (free data)
    (return status)))

(define (db-node-data-ref type data data-size field) (db-type-t* b0* size-t db-field-count-t)
  "get a reference to field data from node data (btree value)"
  (declare
    result-data b8*
    end b8*
    field-index db-field-count-t
    result db-data-t
    size size-t)
  (if (> type:fields-fixed-count field)
    (begin
      (sc-comment "fixed length field")
      (set
        result.data (+ state.current (array-get type:fields-fixed-offsets field))
        result.size (db-field-type-size (struct-get (array-get type:fields field) type)))
      (return result))
    (begin
      (sc-comment "variable length field")
      (set
        result-data (+ state.current (array-get type:fields-fixed-offsets (- i 1)))
        field-index type:fields-fixed-count
        end (+ result-data state.current-size))
      (while (and (<= field-index field) (< result-data end))
        (set
          size (pointer-get (convert-type result-data db-data-len-t*))
          result-data (+ (sizeof db-data-len-t) result-data))
        (if (= field-index field)
          (begin
            (set
              result.data result-data
              result.size size)
            (return result)))
        (set
          field-index (+ 1 field-index)
          result-data (+ size result-data)))
      (set
        result.data 0
        result.size 0)
      (return result))))

(define (db-node-ref state field) (db-node-data-t db-node-read-state-t* db-field-count-t)
  "return a reference to the data in the database without copying"
  (return (db-node-data-ref state:type state:current state:current-size field)))

(define (db-free-node-values values) (b0 db-node-values-t*) (free-and-set-null values:data))

(define (db-node-data->values type data data-size result)
  (status-t db-type-t* b0* size-t db-node-values-t*)
  status-declare
  (declare
    i db-field-count-t
    fields-len db-field-count-t
    node-data db-node-data-t
    values db-node-values-t
    field-size b8
    size size-t
    data b0*
    data-temp b8*
    field-type db-field-type-t)
  (set
    fields-len type:fields-len
    size 0)
  (status-require (db-node-values-new type &values))
  (for ((set i 0) (< i fields-len) (set i (+ 1 i)))
    (set node-data (db-node-data-ref type data data-size i))
    (db-node-values-set values i data size))
  (set *db-node-values-t values)
  (label exit
    (if status-is-failure (db-free-node-values &values))
    (return status)))

(define (db-node-next state) (status-t db-node-read-state-t*)
  status-declare
  db-mdb-declare-val-id
  db-mdb-declare-val-null
  (declare
    matcher db-node-matcher-t
    matcher-state b0*
    id db-id-t
    ids db-ids-t**
    type db-type-t)
  (status-require state:status)
  (set
    matcher state:matcher
    matcher-state state:matcher-state
    type state:type
    ids state:ids
    skip state:skip
    count state:count)
  (if ids
    (begin
      (sc-comment "filter ids")
      (while (and ids count)
        (set val-id.mv-data (db-ids-first-address ids))
        (db-cursor-get-norequire state:cursor val-id val-data MDB-SET-KEY)
        (if db-mdb-status-is-success
          (if
            (or
              (not matcher)
              (matcher (db-ids-first ids) val-data.mv-data val-data.mv-size matcher-state))
            (if (not skip)
              (set
                state:current val-data.mv-data
                state:current-size val-data.mv-size
                state:current-id (db-ids-first ids)))
            (set count (- count 1)))
          db-mdb-status-require-notfound)
        (set ids (db-ids-rest ids)))
      (goto exit))
    (begin
      (sc-comment "filter type")
      (db-cursor-get state:cursor val-id val-null MDB-GET-CURRENT)
      (if (not (= type (db-id-type (pointer->id val-id.mv-data))))
        (begin
          (set
            id (db-id-add-type 0 type)
            val-id.mv-data &id)
          (db-cursor-get state:cursor val-id val-null MDB-SET-RANGE)))
      (while (and db-status-is-success count (= type (db-id-type (pointer->id val-id.mv-data))))
        (if
          (or
            (not matcher)
            (matcher (db-ids-first ids) val-data.mv-data val-data.mv-size matcher-state))
          (if (not skip)
            (set
              state:current val-data.mv-data
              state:current-size val-data.mv-size
              state:current-id (db-ids-first ids)))
          (set count (- count 1)))
        (db-cursor-get-norequire state:cursor val-id val-null MDB-NEXT-NODUP))
      (if (not db-mdb-status-is-success) db-mdb-status-require-notfound)))
  (label exit
    db-status-no-more-data-if-mdb-notfound
    (set state:ids ids)
    (return status)))

(define (db-node-skip state count) (status-t db-node-read-state-t* b32)
  "read the next count matches and position state afterwards"
  status-declare
  (set
    state:skip #t
    state:count count
    status (db-node-next state)
    state:skip #f
    state:count 1)
  (return status))

(define (db-node-select txn type ids offset matcher matcher-state result-state)
  (status-t db-txn-t db-ids-t* db-type-t* b32 db-node-matcher-t b0* db-node-read-state-t*)
  "select nodes optionally filtered by either a list of ids or type.
  ids: zero if unused
  type: zero if unused
  offset: skip this number of matches first
  matcher: zero if unused. a function that is called for each node after filtering by ids or type
  matcher-state: zero if unused. a pointer passed to each call of matcher"
  status-declare
  db-mdb-declare-val-null
  (db-mdb-cursor-declare nodes)
  (db-mdb-cursor-open txn nodes)
  (db-mdb-cursor-get nodes val-null val-null MDB-FIRST)
  (set
    result-state:cursor nodes
    result-state:type type
    result-state:status status
    result-state:options 0
    result-state:count 1
    result-state:skip #f
    result-state:matcher matcher
    result-state:matcher-state matcher-state)
  (if offset (set status (db-node-skip result-state offset)))
  (label exit
    (if (not db-mdb-status-is-success)
      (begin
        (mdb-cursor-close nodes)
        db-status-no-more-data-if-mdb-notfound))
    (set result-state:status status)
    (return status)))

(define (db-graph-internal-delete left right label ordinal graph-lr graph-rl graph-ll)
  (status-t
    db-ids-t* db-ids-t* db-ids-t* db-ordinal-condition-t* MDB-cursor* MDB-cursor* MDB-cursor*)
  "declare because it is defined later")

(define (db-node-delete txn ids) (status-t db-txn-t db-ids-t*)
  "delete a node and all its relations"
  status-declare
  db-mdb-declare-val-id
  (declare val-data MDB-val)
  (db-mdb-cursor-declare nodes)
  (db-mdb-cursor-declare-three graph-lr graph-rl graph-ll)
  (sc-comment
    "return if ids is zero because in db-graph-internal-delete it would mean non-filter and match all")
  (if (not ids) (return status))
  (db-mdb-cursor-open txn graph-lr)
  (db-mdb-cursor-open txn graph-rl)
  (db-mdb-cursor-open txn graph-ll)
  (status-require (db-graph-internal-delete ids 0 0 0 graph-lr graph-rl graph-ll))
  (status-require (db-graph-internal-delete 0 ids 0 0 graph-lr graph-rl graph-ll))
  (status-require (db-graph-internal-delete 0 0 ids 0 graph-lr graph-rl graph-ll))
  (db-mdb-cursor-open txn nodes)
  (while ids
    (set val-id.mv-data (db-ids-first-address ids))
    (db-mdb-cursor-get-norequire nodes val-id val-data MDB-SET-KEY)
    (if db-mdb-status-is-success
      (begin
        (status-require (db-node-data->values type val-data.mv-data val-data.mv-size &values))
        (status-require (db-node-indices-delete txn values (db-ids-first ids)))
        (db-mdb-cursor-del nodes 0))
      (if db-mdb-status-notfound? (status-set-id status-id-success)
        (status-set-group-goto db-status-group-lmdb)))
    (set ids (db-ids-rest ids)))
  (label exit
    (db-mdb-cursor-close graph-lr)
    (db-mdb-cursor-close graph-rl)
    (db-mdb-cursor-close graph-ll)
    (db-mdb-cursor-close nodes)
    (return status)))

(define (db-node-selection-destroy state) (b0 db-node-read-state-t*)
  (if (struct-pointer-get state cursor) (mdb-cursor-close (struct-pointer-get state cursor))))

(define (db-node-update txn id values) (status-t db-txn-t db-id-t db-node-value-t*)
  "update node data. like node-delete followed by node-create but keeps the old id"
  status-declare
  db-mdb-declare-val-id
  (db-mdb-cursor-declare nodes)
  (declare
    val-data MDB-val
    data b0*
    values db-node-values-t
    ids db-ids-t*)
  (set
    ids (db-ids-add 0 id)
    data 0
    val-id.mv-data &id)
  (status-require (db-node-values->data values &data &val-data.mv-size))
  (status-require (db-node-delete txn ids))
  (set
    val-data.mv-data data
    val-id.mv-data &id)
  (db-mdb-cursor-open txn nodes)
  (db-mdb-cursor-put nodes val-id val-data)
  (db-mdb-cursor-close nodes)
  (status-require (db-node-indices-ensure txn values id))
  (label exit
    (db-mdb-cursor-close-if-active nodes)
    (free data)
    (return status)))

(define (db-node-identify txn ids result) (status-t db-txn-t db-ids-t* db-ids-t**)
  "add the existing ids from the given ids to \"result\""
  status-declare
  db-mdb-declare-val-id
  db-mdb-declare-val-null
  (db-mdb-cursor-declare nodes)
  (declare result-ids dg-ids-t*)
  (set result-ids 0)
  (db-mdb-cursor-open txn nodes)
  (while ids
    (set
      val-id.mv-data (db-ids-first-address ids)
      status.id (mdb-cursor-get nodes (address-of val-id) (address-of val-null) MDB-SET))
    (if db-mdb-status-is-success (set result-ids (db-ids-add result-ids (db-ids-first ids)))
      db-mdb-status-require-notfound)
    (set ids (db-ids-rest ids)))
  (label exit
    (mdb-cursor-close nodes)
    db-status-success-if-mdb-notfound
    (if status-is-failure (db-ids-destroy result-ids)
      (set *result result-ids))
    (return status)))

(define (db-node-exists? txn ids result) (status-t db-txn-t* db-ids-t* boolean*)
  "true if all given ids are ids of existing nodes, false otherwise"
  status-declare
  db-mdb-declare-val-id
  db-mdb-declare-val-null
  (db-mdb-cursor-declare nodes)
  (db-mdb-cursor-open txn nodes)
  (while ids
    (set
      val-id.mv-data (db-ids-first-address ids)
      status.id (mdb-cursor-get nodes (address-of val-id) (address-of val-null) MDB-SET))
    (if db-mdb-status-notfound?
      (begin
        (set
          *result #f
          status.id status-id-success)
        (goto exit))
      status-require)
    (set ids (db-ids-rest ids)))
  (set *result #t)
  (label exit
    (mdb-cursor-close nodes)
    (return status)))