(pre-define (db-type-get-by-id env type-id) (+ type-id env:types))

(define (db-env-types-extend env type-id) (status-t db-env-t* db-type-id-t)
  "extend the size of the types array if type-id is an index out of bounds"
  status-declare
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

(define (db-type-get env name) (db-type-t* db-env-t* ui8*)
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

(define (db-type-field-get type name) (db-field-t* db-type-t* ui8*)
  (declare
    index db-fields-len-t
    fields-len db-fields-len-t
    fields db-field-t*)
  (set
    fields-len type:fields-len
    fields type:fields)
  (for ((set index 0) (< index fields-len) (set index (+ 1 index)))
    (if
      (=
        0
        (strncmp
          name
          (struct-get (array-get fields index) name) (struct-get (array-get fields index) name-len)))
      (return (+ fields index))))
  (return 0))

(define (db-type-create env name fields fields-len flags result)
  (status-t db-env-t* ui8* db-field-t* db-fields-len-t ui8 db-type-t**)
  "the data format is documented in main/open.c"
  status-declare
  (db-mdb-cursor-declare system)
  (db-mdb-cursor-declare nodes)
  (db-txn-declare env txn)
  (declare
    data ui8*
    data-start ui8*
    field db-field-t
    i db-fields-len-t
    type-pointer db-type-t*
    key (array ui8 (db-size-system-key))
    name-len ui8
    data-size size-t
    type-id db-type-id-t
    val-data MDB-val
    val-key MDB-val)
  (sc-comment "check if type with name exists")
  (if (db-type-get txn.env name) (status-set-both-goto db-status-group-db db-status-id-duplicate))
  (sc-comment "check name length")
  (set name-len (strlen name))
  (if (< db-name-len-max name-len)
    (status-set-both-goto db-status-group-db db-status-id-data-length))
  (sc-comment "allocate insert data")
  (set data-size (+ (sizeof db-name-len-t) name-len (sizeof db-fields-len-t)))
  (for ((set i 0) (< i fields-len) (set i (+ 1 i)))
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
    (pointer-get (convert-type data db-fields-len-t*)) fields-len
    data (+ (sizeof db-fields-len-t) data))
  (for ((set i 0) (< i fields-len) (set i (+ 1 i)))
    (set
      field (array-get fields i)
      (pointer-get (convert-type data db-field-type-t*)) field.type
      data (+ 1 (convert-type data db-field-type-t*))
      (pointer-get (convert-type data db-name-len-t*)) field.name-len
      data (+ 1 (convert-type data db-name-len-t*)))
    (memcpy data field.name field.name-len)
    (set data (+ field.name-len data)))
  (status-require (db-sequence-next-system txn.env &type-id))
  (set
    (db-system-key-label key) db-system-label-type
    (db-system-key-id key) type-id
    val-key.mv-data key
    val-key.mv-size db-size-system-key
    val-data.mv-data data-start
    val-data.mv-size data-size)
  (sc-comment "insert data")
  (db-txn-write-begin txn)
  (db-mdb-status-require (db-mdb-env-cursor-open txn system))
  (db-mdb-status-require (mdb-cursor-put system &val-key &val-data 0))
  (db-mdb-cursor-close system)
  (sc-comment "update cache")
  (status-require (db-env-types-extend txn.env type-id))
  (db-mdb-status-require (db-mdb-env-cursor-open txn nodes))
  (status-require (db-open-type val-key.mv-data val-data.mv-data txn.env:types nodes &type-pointer))
  (db-mdb-cursor-close nodes)
  (db-txn-commit txn)
  (set *result type-pointer)
  (label exit
    (if (db-txn-is-active txn)
      (begin
        (db-mdb-cursor-close-if-active system)
        (db-mdb-cursor-close-if-active nodes)
        (db-txn-abort txn)))
    (return status)))

(define (db-type-delete env type-id) (status-t db-env-t* db-type-id-t)
  "delete system entry and/or all nodes and cache entries"
  status-declare
  db-mdb-declare-val-null
  (db-mdb-cursor-declare system)
  (db-mdb-cursor-declare nodes)
  (db-txn-declare env txn)
  (declare
    val-key MDB-val
    id db-id-t
    key (array ui8 (db-size-system-key)))
  (set
    val-key.mv-size db-size-system-key
    (db-system-key-label key) db-system-label-type
    (db-system-key-id key) type-id
    val-key.mv-data key)
  (db-txn-write-begin txn)
  (sc-comment "system. continue even if not found")
  (db-mdb-status-require (db-mdb-env-cursor-open txn system))
  (set status.id (mdb-cursor-get system &val-key &val-null MDB-SET))
  (if db-mdb-status-is-success (db-mdb-status-require (mdb-cursor-del system 0))
    (begin
      db-mdb-status-expect-notfound
      (set status.id status-id-success)))
  (db-mdb-cursor-close system)
  (sc-comment "nodes")
  (db-mdb-status-require (db-mdb-env-cursor-open txn nodes))
  (set
    val-key.mv-size db-size-id
    id (db-id-add-type 0 type-id)
    val-key.mv-data &id)
  (set status.id (mdb-cursor-get nodes &val-key &val-null MDB-SET-RANGE))
  (while (and db-mdb-status-is-success (= type-id (db-id-type (db-pointer->id val-key.mv-data))))
    (db-mdb-status-require (mdb-cursor-del nodes 0))
    (set status.id (mdb-cursor-get nodes &val-key &val-null MDB-NEXT-NODUP)))
  (if status-is-failure
    (if db-mdb-status-is-notfound (set status.id status-id-success)
      (status-set-group-goto db-status-group-lmdb)))
  (sc-comment "cache")
  (db-free-env-type (+ type-id env:types))
  (label exit
    (db-mdb-cursor-close-if-active system)
    (db-mdb-cursor-close-if-active nodes)
    (if status-is-success (db-txn-commit txn)
      (db-txn-abort txn))
    (return status)))