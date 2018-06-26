(define (db-node-update-indices txn values id) (status-t db-txn-t db-node-values-t db-id-t)
  "add relevant field data to all indices of a type"
  status-init
  db-mdb-declare-val-id
  (db-mdb-cursor-declare node-index-cursor)
  (declare
    data b8*
    val-data MDB-val
    size size-t
    i db-index-count-t
    node-index db-index-t
    node-indices db-index-t*
    node-indices-len db-index-count-t
    value-size size-t
    fields-len db-field-count-t
    fields-index db-field-count-t)
  (set
    val-id.mv-data &id
    data 0
    node-indices-len values.type:indices-len
    node-indices values.type:indices)
  (for ((set i 0) (< i node-indices-len) (set i (+ 1 i)))
    (set node-index (array-get node-indices i))
    (sc-comment "calculate size")
    (for
      ( (set fields-index 0)
        (< fields-index node-index.fields-len) (set fields-index (+ 1 fields-index)))
      (set size
        (+
          size (struct-get (array-get values.data (array-get node-index.fields fields-index)) size))))
    (if (< txn.env:maxkeysize size)
      (status-set-both-goto db-status-group-db db-status-id-index-keysize))
    (sc-comment "prepare insert data")
    (db-malloc data size)
    (set val-data.mv-data data)
    (for ((set fields-index 0) (< fields-index fields-len) (set fields-index (+ 1 fields-index)))
      (set value-size
        (struct-get (array-get values.data (array-get node-index.fields fields-index)) size))
      (memcpy data (struct-get (array-get values.data fields-index) data) value-size)
      (set data (+ value-size data)))
    (db-mdb-status-require! (mdb-cursor-open txn.mdb-txn node-index.dbi &node-index-cursor))
    (db-mdb-cursor-put node-index-cursor val-data val-id)
    (db-mdb-status-require! (mdb-cursor-put node-index-cursor &val-data &val-id 0))
    (db-mdb-cursor-close node-index-cursor)
    (free-and-set-null data))
  (label exit
    (db-mdb-cursor-close-if-active node-index-cursor)
    (if data (free data))
    (return status)))

(define (db-node-values->data values result result-size) (status-t db-node-values-t b0** size-t*)
  status-init
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
  status-init
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
  status-init
  db-mdb-declare-val-id
  (db-mdb-cursor-declare nodes)
  (declare
    val-data MDB-val
    data b0*
    id db-id-t)
  (set
    data 0
    val-id.mv-data &id)
  (status-require! (db-node-values->data values &data &val-data.mv-size))
  (set val-data.mv-data data)
  (db-mdb-cursor-open txn nodes)
  (status-require! (db-sequence-next txn.env values.type:id &id))
  (db-mdb-status-require! (mdb-cursor-put nodes (address-of val-id) (address-of val-data) 0))
  (db-mdb-cursor-close nodes)
  (status-require! (db-node-update-indices txn values id))
  (set *result id)
  (label exit
    (db-mdb-cursor-close-if-active nodes)
    (free data)
    (return status)))

(define (db-node-ref state field) (db-node-data-t db-node-read-state-t db-field-count-t)
  "return a reference to the data in the database without copying"
  (declare
    data b8*
    end b8*
    field-index db-field-count-t
    result db-data-t
    size size-t)
  (if (> state.type:fields-fixed-count field)
    (begin
      (sc-comment "fixed length field")
      (set
        result.data (+ state.current (array-get state.type:fields-fixed-offsets field))
        result.size (db-field-type-size (struct-get (array-get state.type:fields field) type)))
      (return result))
    (begin
      (sc-comment "variable length field")
      (set
        data (+ state.current (array-get state.type:fields-fixed-offsets (- i 1)))
        field-index state.type:fields-fixed-count
        end (+ data state.current-size))
      (while (and (<= field-index field) (< data end))
        (set
          size (pointer-get (convert-type data db-data-len-t*))
          data (+ (sizeof db-data-len-t) data))
        (if (= field-index field)
          (begin
            (set
              result.data data
              result.size size)
            (return result)))
        (set
          field-index (+ 1 field-index)
          data (+ size data)))
      (set
        result.data 0
        result.size 0)
      (return result))))

(define (db-node-skip state count) (status-t db-node-read-state-t* b32)
  status-init
  db-mdb-declare-val-id
  (declare
    val-data MDB-val
    type db-type-t*)
  (set type state:type)
  (while count
    (if
      (and
        (= type (db-id-type (pointer->id val-id.mv-data)))
        (or (not matcher) (matcher type id matcher-state val-data.mv-data type val-data.mv-size)))
      (set count (- count 1)))
    (db-mdb-status-require! (mdb-cursor-get state:cursor &val-id &val-data MDB-NEXT-NODUP)))
  (label exit
    db-status-no-more-data-if-mdb-notfound
    (return status)))

(define (db-node-next state) (status-t db-node-read-state-t*)
  status-init
  db-mdb-declare-val-id
  (declare
    val-data MDB-val
    type db-type-id-t)
  (status-require! state:status)
  (set type state:type)
  (db-mdb-status-require! (mdb-cursor-get state:cursor &val-id &val-data MDB-GET-CURRENT))
  (while count
    (if (db-node-types-match? types (db-mdb-val->id val-id))
      (begin
        (struct-set data-record
          id (db-mdb-val->id val-id)
          data (struct-get val-data mv-data)
          size (struct-get val-data mv-size))
        (set data-records (db-data-records-add (pointer-get result) data-record))
        (if (not data-records) (db-status-set-id-goto db-status-id-memory))
        (set (pointer-get result) data-records)))
    (db-mdb-status-require! (mdb-cursor-get state:cursor &val-id &val-data MDB-NEXT-NODUP)))
  (label exit
    db-status-no-more-data-if-mdb-notfound
    (set state:status status)
    (return status)))

(db-node-select txn 0 0 0 0 0 &state)

(define (db-node-select txn ids type offset matcher matcher-state result-state)
  (status-t db-txn-t db-ids-t* db-type-t* b32 node-matcher-t b0* db-node-read-state-t*)
  "select nodes optionally filtered by any of a list of ids or type.
  ids: zero if unused
  type: zero if unused
  offset: skip this number of matches first
  matcher: zero if unused. a function that is called for each node after filtering by ids or type
  matcher-state: zero if unused. a pointer passed to each call of matcher"
  status-init
  db-mdb-declare-val-null
  (db-mdb-cursor-declare nodes)
  (db-mdb-cursor-open txn nodes)
  (db-mdb-cursor-get nodes val-null val-null MDB-FIRST)
  (set
    result-state:cursor nodes
    result-state:type type
    result-state:status status
    result-state:options 0)
  (if offset (set status (db-node-skip result-state matcher offset)))
  (label exit
    (if (not db-mdb-status-success?)
      (begin
        (mdb-cursor-close nodes)
        (if db-mdb-status-notfound? (status-set-id db-status-id-no-more-data))))
    (set result-state:status status)
    (return status)))

(define (db-graph-internal-delete left right label ordinal graph-lr graph-rl graph-ll)
  (status-t
    db-ids-t* db-ids-t* db-ids-t* db-ordinal-condition-t* MDB-cursor* MDB-cursor* MDB-cursor*)
  "declare because it is defined later")

#;(define (db-node-delete txn ids) (status-t db-txn-t db-ids-t*)
  "delete a node and all its relations.
  check if ids zero because in db-graph-internal-delete it would be an non-filter and match all.
  todo: update indices"
  status-init
  db-mdb-declare-val-id
  db-mdb-declare-val-null
  (db-mdb-cursor-declare nodes)
  (db-mdb-cursor-declare-three graph-lr graph-rl graph-ll)
  (if (not ids) (return status))
  (db-mdb-cursor-open txn graph-lr)
  (db-mdb-cursor-open txn graph-rl)
  (db-mdb-cursor-open txn graph-ll)
  (status-require! (db-graph-internal-delete ids 0 0 0 graph-lr graph-rl graph-ll))
  (status-require! (db-graph-internal-delete 0 ids 0 0 graph-lr graph-rl graph-ll))
  (status-require! (db-graph-internal-delete 0 0 ids 0 graph-lr graph-rl graph-ll))
  (db-mdb-cursor-open txn nodes)
  (while ids
    (set val-id.mv-data (db-ids-first-address ids))
    (db-mdb-cursor-get-norequire nodes val-id val-null MDB-SET-KEY)
    (if db-mdb-status-success? (db-mdb-cursor-del-norequire nodes 0)
      (if db-mdb-status-notfound? (status-set-id status-id-success)
        (status-set-group-goto db-status-group-lmdb)))
    (set ids (db-ids-rest ids)))
  (label exit
    (db-mdb-cursor-close nodes)
    (db-mdb-cursor-close graph-lr)
    (db-mdb-cursor-close graph-rl)
    (db-mdb-cursor-close graph-ll)
    (return status)))

#;(define (db-node-selection-destroy state) (b0 db-node-read-state-t*)
  (if (struct-pointer-get state cursor) (mdb-cursor-close (struct-pointer-get state cursor))))

#;(define (db-node-update txn id values) (status-t db-txn-t db-id-t db-node-value-t*)
  status-init
  db-mdb-declare-val-id
  (declare val-data MDB-val)
  (set
    val-data.mv-data data.data
    mv-size data.size)
  (db-mdb-cursor-declare nodes)
  (db-mdb-cursor-open txn nodes)
  (struct-set val-id
    mv-data &id)
  (db-mdb-cursor-get nodes val-id val-data MDB-SET-KEY)
  (if db-mdb-status-success?
    (begin
      ; delete index entries
      (struct-set val-data
        mv-data (struct-get data data)
        mv-size (struct-get data size))
      (db-mdb-status-require! (mdb-cursor-put nodes &val-id &val-data MDB-CURRENT))
      (db-mdb-status-require! (mdb-cursor-put data-intern->id &val-data &val-id 0)))
    db-mdb-status-require-notfound)
  (label exit
    (return status)))

#;(define (db-node-identify txn ids result) (status-t db-txn-t db-ids-t* db-ids-t**)
  "filter existing ids from the list of given ids and add the result to \"result\""
  status-init
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
    (if db-mdb-status-success? (set result-ids (db-ids-add result-ids (db-ids-first ids)))
      db-mdb-status-require-notfound)
    (set ids (db-ids-rest ids)))
  (label exit
    (mdb-cursor-close nodes)
    db-status-success-if-mdb-notfound
    (if status-failure? (db-ids-destroy result-ids)
      (set *result result-ids))
    (return status)))

#;(define (db-node-exists? txn ids result) (status-t db-txn-t* db-ids-t* boolean*)
  status-init
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