(declare db-node-value-t
  (type
    (struct
      (size db-data-len-t)
      (data b0*))))

(define (db-node-values-prepare type result) (status-t db-type-t* db-node-value-t**)
  "allocate memory for a new node values array"
  status-init
  (declare a db-node-value-t*)
  (db-malloc a (* type:fields-len (sizeof db-node-value-t)))
  (set *result a)
  (label exit
    (return status)))

(define (db-node-values-set values field-index data size)
  (b0 db-node-value-t* db-field-count-t b0* size-t)
  "set a value for a field in node values.
  size can be set to zero and is ignored for fixed length types"
  (struct-set (array-get values field-index)
    data data
    size size))

(define (db-node-values->data type values result result-size)
  (status-t db-type-t* db-node-value-t* b8** *size-t)
  status-init
  (declare
    index db-field-count-t
    field-count db-field-count-t
    field-size b8
    size size-t
    data b8*
    field-type db-field-type-t)
  (set
    field-count type:fields-len
    size 0)
  (sc-comment "prepare size information")
  (for ((set index 0) (< index field-count) (set index (+ 1 index)))
    (if (>= index type:fields-fixed-count)
      (set field-size (struct-get (array-get values index) size))
      (begin
        (set
          field-type (struct-get (array-get type:fields index) type)
          field-size (db-field-type-size field-type)
          (struct-get (array-get values index) size) field-size)))
    (if (> field-size db-data-len-max)
      (status-set-both-goto db-status-group-db db-status-id-data-length))
    (set size (+ field-size size)))
  (db-malloc data size)
  (sc-comment "copy data")
  (for ((set index 0) (< index field-count) (set index (+ 1 index)))
    (set field-size (struct-get (array-get values index) size))
    (if (>= index type:fields-fixed-count)
      (set
        (convert-type data db-data-len-t*) field-size
        data (+ (sizeof db-data-len-t) data)))
    (memcpy data (struct-get (array-get values index) data) field-size)
    (set data (+ size data)))
  (set
    *result data
    *result-size size)
  (label exit
    (return status)))

(define (db-type-indices-put-values txn type values id)
  (status-t db-txn-t db-type-t* db-node-value-t* db-id-t)
  db-mdb-declare-val-id
  (db-mdb-cursor-declare node-index-cursor)
  (declare
    data b8*
    val-data MDB-val
    size size-t
    index db-index-count-t
    node-index db-index-t
    node-indices db-index-t*
    node-indices-count db-index-count-t
    value-size size-t
    fields db-field-count-t*
    fields-len db-field-count-t)
  (set
    val-id.mv-data &id
    data 0
    node-indices-count type:indices-count
    node-indices type:indices)
  (for ((set index 0) (< index node-indices-count) (set index (+ 1 index)))
    (set node-index (array-get node-indices index))
    (sc-comment "calculate size")
    (for
      ( (set fields-index 0)
        (< field-index node-index.fields-len) (set fields-index (+ 1 fields-index)))
      (set size
        (+ size (struct-get (array-get values (array-get node-index.fields fields-index)) size))))
    (if (< txn.env:maxkeysize size)
      (status-set-both-goto db-status-group-db db-status-id-index-keysize))
    (sc-comment "prepare insert data")
    (db-malloc data size)
    (set val-data.mv-data data)
    (for ((set fields-index 0) (< field-index fields-len) (set fields-index (+ 1 fields-index)))
      (set value-size
        (struct-get (array-get values (array-get node-index.fields fields-index)) size))
      (memcpy data (struct-get (array-get values field-index) data) value-size)
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

(define (db-node-put txn type values result)
  (status-t db-txn-t db-type-t* db-node-value-t* db-id-t*)
  status-init
  db-mdb-declare-val-id
  (db-mdb-cursor-declare nodes)
  (declare
    val-data MDB-val
    id db-id-t)
  (set
    val-data.mv-data 0
    val-id.mv-data &id)
  (status-require! (db-node-values->data type values &val-data.mv-data &val-data.mv-size))
  (db-mdb-cursor-open txn nodes)
  (status-require! (db-sequence-next env type:id &id))
  (db-mdb-status-require! (mdb-cursor-put nodes (address-of val-id) (address-of val-data) 0))
  (db-mdb-cursor-close nodes)
  (status-require! (db-type-indices-put-values txn type values))
  (set *result id)
  (label exit
    (db-mdb-cursor-close-if-active nodes)
    (free val-data.mv-data)
    (return status)))

(define (db-graph-internal-delete left right label ordinal graph-lr graph-rl graph-ll)
  (status-t
    db-ids-t* db-ids-t* db-ids-t* db-ordinal-condition-t* MDB-cursor* MDB-cursor* MDB-cursor*)
  "declare because it is defined later")

(define (db-node-delete txn ids) (status-t db-txn-t db-ids-t*)
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

(define (db-node-identify txn ids result) (status-t db-txn-t db-ids-t* db-ids-t**)
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

(define (db-node-exists? txn ids result) (status-t db-txn-t* db-ids-t* boolean*)
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

(define (db-node-update txn id data) (status-t db-txn-t db-id-t db-node-value-t*)
  status-init
  db-mdb-declare-val-id
  db-mdb-declare-val-data
  (struct-set val-data
    mv-data (struct-get data data)
    mv-size (struct-get data size))
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

(define (db-node-next state count result) (status-t db-node-read-state-t* b32 db-data-records-t**)
  status-init
  (set count (optional-count count))
  db-mdb-declare-val-id
  db-mdb-declare-val-data
  (pre-let
    (nodes state:cursor skip (bit-and db-read-option-skip state:options) types state:types)
    (status-require! state:status)
    (define
      data-records db-data-records-t*
      data-record db-data-record-t)
    (if skip
      (while count
        (db-mdb-status-require! (mdb-cursor-get nodes &val-null &val-null MDB-NEXT-NODUP))
        (if (db-node-types-match? types (db-mdb-val->id val-id)) (set count (- count 1))))
      (begin
        (db-mdb-status-require! (mdb-cursor-get nodes &val-id &val-data MDB-GET-CURRENT))
        (while count
          (if (db-node-types-match? types (db-mdb-val->id val-id))
            (begin
              (struct-set data-record
                id (db-mdb-val->id val-id)
                data (struct-get val-data mv-data)
                size (struct-get val-data mv-size))
              (set data-records (db-data-records-add (pointer-get result) data-record))
              (if (not data-records) (db-status-set-id-goto db-status-id-memory))
              (set (pointer-get result) data-records)
              (set count (- count 1))))
          (db-mdb-status-require! (mdb-cursor-get nodes &val-id &val-data MDB-NEXT-NODUP)))))
    (label exit
      db-status-no-more-data-if-mdb-notfound
      (set state:status status)
      (return status))))

(define (db-node-select txn type offset result) (status-t db-txn-t* b8 b32 db-node-read-state-t*)
  "select nodes of type"
  status-init
  (db-mdb-cursor-declare nodes)
  (db-mdb-cursor-open txn nodes)
  (db-mdb-cursor-get nodes val-null val-null MDB-FIRST)
  (set
    result:cursor result:nodes
    result:types result:type-bits
    result:status result:status
    result:options 0)
  (db-select-ensure-offset result offset db-node-next)
  (label exit
    (if (not db-mdb-status-success?)
      (begin
        (mdb-cursor-close nodes)
        (if db-mdb-status-notfound?
          (begin
            (status-set-id db-status-id-no-more-data)
            (set result:cursor 0)))))
    (set result:status status)
    (return status)))

(define (db-node-selection-destroy state) (b0 db-node-read-state-t*)
  (if (struct-pointer-get state cursor) (mdb-cursor-close (struct-pointer-get state cursor))))