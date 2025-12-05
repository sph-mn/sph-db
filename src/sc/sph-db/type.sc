(define (db-env-types-extend env type-id) (status-t db-env-t* db-type-id-t)
  "extend the size of the types array if type-id is an index out of bounds.
   entries from and including index type-id will be set to zero .id"
  status-declare
  (declare types-len db-type-id-t types db-type-t* i db-type-id-t)
  (set types-len env:types-len)
  (if (> types-len type-id) (goto exit))
  (sc-comment "resize")
  (set types env:types types-len (+ db-env-types-extra-count type-id))
  (status-require
    (sph-memory-realloc (* types-len (sizeof db-type-t)) (convert-type &types void**)))
  (sc-comment "set new type struct ids to zero")
  (for ((set i type-id) (< i types-len) (set i (+ 1 i)))
    (set (struct-get (array-get types i) id) 0))
  (set env:types types env:types-len types-len)
  (label exit status-return))

(define (db-type-get env name) (db-type-t* db-env-t* char*)
  "return a pointer to the type struct for the type with the given name. zero if not found"
  (declare i db-type-id-t types-len db-type-id-t type db-type-t*)
  (set types-len env:types-len)
  (for ((set i 0) (< i types-len) (set i (+ 1 i)))
    (set type (+ i env:types))
    (if (and type:id type:name (= 0 (strcmp name type:name))) (return type)))
  (return 0))

(define (db-type-field-get type name) (db-field-t* db-type-t* char*)
  (declare index db-fields-len-t fields-len db-fields-len-t fields db-field-t*)
  (set fields-len type:fields-len fields type:fields)
  (for ((set index 0) (< index fields-len) (set index (+ 1 index)))
    (if (= 0 (strcmp name (struct-get (array-get fields index) name))) (return (+ fields index))))
  (return 0))

(define (db-type-create env name fields fields-len flags result)
  (status-t db-env-t* char* db-field-t* db-fields-len-t uint8-t db-type-t**)
  "the data format is documented in main/open.c"
  status-declare
  (db-mdb-cursor-declare system)
  (db-mdb-cursor-declare records)
  (db-txn-declare env txn)
  (declare
    data uint8-t*
    data-start uint8-t*
    field db-field-t
    i db-fields-len-t
    type-pointer db-type-t*
    key (array uint8-t (db-size-system-key))
    name-len db-name-len-t
    field-name-len db-name-len-t
    data-size size-t
    after-fixed-size-fields boolean
    type-id db-type-id-t
    val-data MDB-val
    val-key MDB-val)
  (set data-start 0)
  (if (bit-and db-type-flag-virtual flags)
    (begin
      (if (not (= 1 fields-len)) (status-set-goto db-status-group-db db-status-id-not-implemented))
      (if (< db-size-element-id (db-field-type-size fields:type))
        (status-set-goto db-status-group-db db-status-id-invalid-field-type))))
  (sc-comment "check if type with name exists")
  (if (db-type-get txn.env name) (status-set-goto db-status-group-db db-status-id-duplicate))
  (sc-comment "get and check name-len")
  (set after-fixed-size-fields #f name-len (strlen name))
  (if (< db-name-len-max name-len) (status-set-goto db-status-group-db db-status-id-data-length))
  (sc-comment "calculate data size")
  (set data-size (+ 1 (sizeof db-name-len-t) name-len (sizeof db-fields-len-t)))
  (for ((set i 0) (< i fields-len) (set i (+ 1 i)))
    (sc-comment "fixed fields must come before variable length fields")
    (if (db-field-type-is-fixed (: (+ i fields) type))
      (if after-fixed-size-fields
        (status-set-goto db-status-group-db db-status-id-type-field-order))
      (set after-fixed-size-fields #t))
    (set data-size
      (+ data-size (sizeof db-field-type-t)
        (sizeof db-name-len-t) (strlen (struct-get (array-get fields i) name)))))
  (sc-comment "allocate")
  (status-require (sph-memory-malloc data-size (convert-type &data void**)))
  (sc-comment "set data")
  (set
    data-start data
    *data flags
    data (+ 1 data)
    (pointer-get (convert-type data db-name-len-t*)) name-len
    data (+ (sizeof db-name-len-t) data))
  (memcpy data name name-len)
  (set
    data (+ name-len data)
    (pointer-get (convert-type data db-fields-len-t*)) fields-len
    data (+ (sizeof db-fields-len-t) data))
  (for ((set i 0) (< i fields-len) (set i (+ 1 i)))
    (set
      field (array-get fields i)
      field-name-len (strlen field.name)
      (pointer-get (convert-type data db-field-type-t*)) field.type
      data (+ (sizeof db-field-type-t) data)
      (pointer-get (convert-type data db-name-len-t*)) field-name-len
      data (+ (sizeof db-name-len-t) data))
    (memcpy data field.name field-name-len)
    (set data (+ field-name-len data)))
  (status-require (db-sequence-next-system txn.env &type-id))
  (set
    (db-system-key-label key) db-system-label-type
    (db-system-key-id key) type-id
    val-key.mv-data key
    val-key.mv-size db-size-system-key
    val-data.mv-data data-start
    val-data.mv-size data-size)
  (sc-comment "insert data")
  (status-require (db-txn-write-begin &txn))
  (db-mdb-status-require (db-mdb-env-cursor-open txn system))
  (db-mdb-status-require (mdb-cursor-put system &val-key &val-data 0))
  (db-mdb-cursor-close system)
  (sc-comment "update cache")
  (status-require (db-env-types-extend txn.env type-id))
  (db-mdb-status-require (db-mdb-env-cursor-open txn records))
  (status-require (db-open-type key data-start txn.env:types records &type-pointer))
  (db-mdb-cursor-close records)
  (status-require (db-txn-commit &txn))
  (set *result type-pointer)
  (label exit
    (if (db-txn-is-active txn)
      (begin
        (db-mdb-cursor-close-if-active system)
        (db-mdb-cursor-close-if-active records)
        (db-txn-abort &txn)))
    status-return))

(define (db-type-delete env type-id) (status-t db-env-t* db-type-id-t)
  "delete all indices of type, all records of type, the system entry and clear cache entries, in this order"
  status-declare
  db-mdb-declare-val-null
  (db-mdb-cursor-declare system)
  (db-mdb-cursor-declare records)
  (db-txn-declare env txn)
  (declare
    val-key MDB-val
    id db-id-t
    i db-indices-len-t
    indices-len db-indices-len-t
    type db-type-t*
    key (array uint8-t (db-size-system-key)))
  (set type (+ type-id env:types) indices-len type:indices-len)
  (sc-comment "indices")
  (for ((set i 0) (< i indices-len) (set i (+ 1 i)))
    (sc-comment "check if array entry not nulled")
    (if (struct-get (array-get type:indices i) type)
      (status-require (db-index-delete env (+ type:indices i)))))
  (status-require (db-txn-write-begin &txn))
  (sc-comment "records")
  (db-mdb-status-require (db-mdb-env-cursor-open txn records))
  (set
    val-key.mv-size (sizeof db-id-t)
    id (db-id-add-type 0 type-id)
    val-key.mv-data &id
    status.id (mdb-cursor-get records &val-key &val-null MDB-SET-RANGE))
  (while (and db-mdb-status-is-success (= type-id (db-id-type (db-pointer->id val-key.mv-data))))
    (db-mdb-status-require (mdb-cursor-del records 0))
    (set status.id (mdb-cursor-get records &val-key &val-null MDB-NEXT-NODUP)))
  (if status-is-failure
    (if db-mdb-status-is-notfound (set status.id status-id-success)
      (begin (set status.group db-status-group-lmdb) (goto exit))))
  (db-mdb-cursor-close records)
  (sc-comment "system")
  (set
    val-key.mv-size db-size-system-key
    (db-system-key-label key) db-system-label-type
    (db-system-key-id key) type-id
    val-key.mv-data key)
  (db-mdb-status-require (db-mdb-env-cursor-open txn system))
  (set status.id (mdb-cursor-get system &val-key &val-null MDB-SET))
  (if db-mdb-status-is-success (db-mdb-status-require (mdb-cursor-del system 0))
    (if db-mdb-status-is-notfound (set status.id status-id-success) (goto exit)))
  (db-mdb-cursor-close system)
  (sc-comment "cache")
  (db-free-env-type (+ type-id env:types))
  (label exit
    (db-mdb-cursor-close-if-active system)
    (db-mdb-cursor-close-if-active records)
    (if status-is-success (status-require (db-txn-commit &txn)) (db-txn-abort &txn))
    status-return))
