(define (db-env-types-extend env type-id) (status-t db-env-t* db-type-id-t)
  "extend the types array if type-id is an index out of bounds"
  status-init
  (declare
    types-temp db-type-t*
    types-len db-type-id-t
    types db-type-t*
    i db-type-id-t)
  (set types-len env:types-len)
  (if (> types-len type-id) (goto exit))
  (sc-comment "resize")
  (set
    types env:types
    types-len (+ db-env-types-extra-count type-id))
  (db-realloc types types-temp (* types-len (sizeof db-type-t)))
  (sc-comment "set new type struct ids to zero")
  (for ((set i type-id) (< i types-len) (set i (+ 1 i)))
    (set (: (+ i types) id) 0))
  (set
    env:types types
    env:types-len types-len)
  (label exit
    (return status)))

(define (db-type-get env name) (db-type-t* db-env-t* b8*)
  "return a pointer to the type struct for the type with the given name. zero if not found"
  (declare
    i db-type-id-t
    types-len db-type-id-t
    type db-type-t*)
  (set types-len env:types-len)
  (for ((set i 0) (< i types-len) (set i (+ 1 i)))
    (set type (+ i env:types))
    (if (and type:id (= 0 (strcmp name type:name))) (return type)))
  (return 0))

(define (db-type-create env name field-count fields flags result)
  (status-t db-env-t* b8* db-field-count-t db-field-t* b8 db-type-id-t*)
  "the data format is documented in main/open.c"
  status-init
  (declare
    data b8*
    data-start b8*
    field db-field-t
    i db-field-count-t
    type-pointer db-type-t*
    key (array b8 (db-size-system-key))
    name-len b8
    data-size size-t
    type-id db-type-id-t
    val-data MDB-val
    val-key MDB-val)
  (db-txn-declare env txn)
  (db-mdb-cursor-declare system)
  (db-mdb-cursor-declare nodes)
  (sc-comment "check if type with name exists")
  (if (db-type-get txn.env name) (status-set-both-goto db-status-group-db db-status-id-duplicate))
  (sc-comment "check name length")
  (set name-len (strlen name))
  (if (< db-name-len-max name-len)
    (status-set-both-goto db-status-group-db db-status-id-data-length))
  (sc-comment "allocate insert data")
  (set data-size (+ (sizeof db-name-len-t) name-len (sizeof db-field-count-t)))
  (for ((set i 0) (< i field-count) (set i (+ 1 i)))
    (set data-size
      (+ data-size (sizeof db-field-type-t) (sizeof db-name-len-t) (: (+ i fields) name-len))))
  (db-malloc data data-size)
  (sc-comment "set insert data")
  (set
    data-start data
    (pointer-get (convert-type data db-name-len-t*)) name-len
    data (+ (sizeof db-name-len-t) data))
  (memcpy data name name-len)
  (set
    data (+ name-len data)
    (pointer-get (convert-type data db-field-count-t*)) field-count
    data (+ (sizeof db-field-count-t) data))
  (for ((set i 0) (< i field-count) (set i (+ 1 i)))
    (set
      field (array-get fields i)
      (pointer-get (convert-type data db-field-type-t*)) field.type
      data (+ 1 (convert-type data db-field-type-t*))
      (pointer-get (convert-type data db-name-len-t*)) field.name-len
      data (+ 1 (convert-type data db-name-len-t*)))
    (memcpy data field.name field.name-len)
    (set data (+ field.name-len data)))
  (status-require! (db-sequence-next-system txn.env &type-id))
  (set
    (db-system-key-label key) db-system-label-type
    (db-system-key-id key) type-id
    val-key.mv-data key
    val-key.mv-size db-size-system-key
    val-data.mv-data data-start
    val-data.mv-size data-size)
  (sc-comment "insert data")
  (db-txn-write-begin txn)
  (db-mdb-cursor-open txn system)
  (db-mdb-cursor-put system val-key val-data)
  (db-mdb-cursor-close system)
  (sc-comment "update cache")
  (status-require! (db-env-types-extend txn.env type-id))
  (db-mdb-cursor-open txn nodes)
  (status-require!
    (db-open-type val-key.mv-data val-data.mv-data txn.env:types nodes &type-pointer))
  (db-mdb-cursor-close nodes)
  (db-txn-commit txn)
  (set *result type-id)
  (label exit
    (if (db-txn-active? txn)
      (begin
        (db-mdb-cursor-close-if-active system)
        (db-mdb-cursor-close-if-active nodes)
        (db-txn-abort txn)))
    (return status)))

(define (db-type-delete env type-id) (status-t db-env-t* db-type-id-t)
  "delete system entry and/or all nodes and cache entries"
  status-init
  (declare
    id db-id-t
    key (array b8 (db-size-system-key)))
  db-mdb-declare-val-null
  (db-mdb-cursor-declare system)
  (db-mdb-cursor-declare nodes)
  (db-mdb-declare-val val-key db-size-system-key)
  (db-txn-declare env txn)
  (set
    (db-system-key-label key) db-system-label-type
    (db-system-key-id key) type-id
    val-key.mv-data key)
  (db-txn-write-begin txn)
  (sc-comment "system. continue even if not found")
  (db-mdb-cursor-open txn system)
  (db-mdb-cursor-get-norequire system val-key val-null MDB-SET)
  (if db-mdb-status-success? (db-mdb-status-require! (mdb-cursor-del system 0))
    (begin
      db-mdb-status-require-notfound
      (status-set-id status-id-success)))
  (db-mdb-cursor-close system)
  (sc-comment "nodes")
  (db-mdb-cursor-open txn nodes)
  (set
    val-key.mv-size db-size-id
    id (db-id-add-type 0 type-id)
    val-key.mv-data &id)
  (db-mdb-cursor-get-norequire nodes val-key val-null MDB-SET-RANGE)
  (while (and db-mdb-status-success? (= type-id (db-id-type (db-pointer->id val-key.mv-data))))
    (db-mdb-status-require! (mdb-cursor-del nodes 0))
    (db-mdb-cursor-get-norequire nodes val-key val-null MDB-NEXT-NODUP))
  (if status-failure?
    (if db-mdb-status-notfound? (status-set-id status-id-success)
      (status-set-group-goto db-status-group-lmdb)))
  (sc-comment "cache")
  (db-free-env-type (+ type-id env:types))
  (label exit
    (db-mdb-cursor-close-if-active system)
    (db-mdb-cursor-close-if-active nodes)
    (if status-success? (db-txn-commit txn)
      (db-txn-abort txn))
    (return status)))

(declare db-node-value-t
  (type
    (struct
      (size db-data-len-t)
      (data b0*))))

(define (db-node-values-new type result) (status-t db-type-t* db-node-values-t**)
  status-init
  (declare a db-node-value-t*)
  (db-malloc a (* type:fields-count (sizeof db-node-value-t)))
  (set *result a)
  (label exit
    (return status)))

(define (db-node-values-set values field-index data size)
  (b0 db-node-values-t* db-field-count-t b0* size-t)
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
  (set field-count type:fields-count)
  (for ((set index 0) (< index field-count) (set index (+ 1 index)))
    (set size (+ size (struct-get (array-get values index) size))))
  (db-malloc data size)
  (for ((set index 0) (< index field-count) (set index (+ 1 index)))
    (set
      field-type (struct-get (array-get type:fields index) type)
      size (db-field-type-size field-type))
    (if (not size)
      (begin
        (set
          size (struct-get (array-get values index) size)
          (convert-type data db-data-len-t*) size
          data (+ (sizeof db-data-len-t) data))
        (if (> size db-data-len-max)
          (status-set-both-goto db-status-group-db db-status-id-data-length))
        (memcpy data (struct-get (array-get values index) data) size)
        (set data (+ size data))))
    (memcpy data (struct-get (array-get values index) data) size)
    (set data (+ size data)))
  (set
    *result data
    *result-size size)
  (label exit
    (return status)))

(define (db-node-create txn type values result)
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
  (sc-comment "update indices")
  (declare
    index db-index-count-t
    node-indices db-index-t*
    node-indices-count db-index-count-t
    fields db-field-count-t*
    fields-count db-field-count-t)
  (db-mdb-cursor-declare node-index-cursor)
  (set
    node-indices-count type:indices-count
    node-indices type:indices
    node-index db-index-t)
  (for ((set index 0) (< index node-indices-count) (set index (+ 1 index)))
    (set
      node-index (array-get node-indices index)
      fields-count node-index.fields-count
      fields node-index.fields)
    (for ((set fields-index 0) (< field-index fields-count) (set fields-index (+ 1 fields-index)))
      (set size (+ size (struct-get (array-get values field-index) size))))
    (if (< txn.env:maxkeysize size)
      (status-set-both-goto db-status-group-db db-status-id-index-keysize))
    (db-malloc data size)
    (for ((set fields-index 0) (< field-index fields-count) (set fields-index (+ 1 fields-index)))
      (memcpy
        data
        (struct-get (array-get values field-index) data)
        (struct-get (array-get values field-index) size))
      (set data (+ (struct-get (array-get values field-index) size) data)))
    (db-mdb-cursor-open node-index-cursor)
    (db-mdb-cursor-put node-index-cursor val-data val-id)
    (status-set-id (mdb-cursor-put node-index-cursor &val-data &val-id 0))
    (free data)
    status-require)
  (set *result id)
  (label exit
    (free val-data.mv-data)
    (db-mdb-cursor-close nodes)
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
  (status-require! (db-graph-internal-delete 0 0 ids 0 graph-lr graph-rl graph-ll))
  (status-require! (db-graph-internal-delete ids 0 0 0 graph-lr graph-rl graph-ll))
  (status-require! (db-graph-internal-delete 0 ids 0 0 graph-lr graph-rl graph-ll))
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