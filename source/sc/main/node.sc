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
  (struct-pointer-set env
    types types
    types-len types-len)
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
    key (array b8 (db-size-system-key))
    name-len b8
    data-size size-t
    type-id db-type-id-t
    val-data MDB-val
    val-key MDB-val)
  (db-txn-declare env txn)
  (db-cursor-declare system)
  (db-cursor-declare nodes)
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
      field (pointer-get fields i)
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
  (db-cursor-open txn system)
  (db-mdb-cursor-put system val-key val-data)
  (db-cursor-close system)
  (sc-comment "update cache")
  (status-require! (db-env-types-extend txn.env type-id))
  (db-cursor-open txn nodes)
  (status-require! (db-open-type val-key.mv-data val-data.mv-data txn.env:types nodes))
  (db-cursor-close nodes)
  (db-txn-commit txn)
  (set *result type-id)
  (label exit
    (if (db-txn-active? txn)
      (begin
        (db-cursor-close-if-active system)
        (db-cursor-close-if-active nodes)
        (db-txn-abort txn)))
    (return status)))

(define (db-type-delete env type-id) (status-t db-env-t* db-type-id-t)
  "delete system entry and/or all nodes and cache entries"
  status-init
  (declare
    id db-id-t
    key (array b8 (db-size-system-key)))
  db-mdb-declare-val-null
  (db-cursor-declare system)
  (db-cursor-declare nodes)
  (db-mdb-declare-val val-key db-size-system-key)
  (db-txn-declare env txn)
  (set
    (db-system-key-label key) db-system-label-type
    (db-system-key-id key) type-id
    val-key.mv-data key)
  (db-txn-write-begin txn)
  (sc-comment "system. continue even if not found")
  (db-cursor-open txn system)
  (db-mdb-cursor-get-norequire system val-key val-null MDB-SET)
  (if db-mdb-status-success? (db-mdb-status-require! (mdb-cursor-del system 0))
    (begin
      db-mdb-status-require-notfound
      (status-set-id status-id-success)))
  (db-cursor-close system)
  (sc-comment "nodes")
  (db-cursor-open txn nodes)
  (set
    val-key.mv-size db-size-id
    id (db-id-add-type 0 type-id)
    val-key.mv-data &id)
  (db-mdb-cursor-get-norequire nodes val-key val-null MDB-SET-RANGE)
  (while (and db-mdb-status-success? (= type-id (db-id-type (db-mdb-val->id val-key))))
    (db-mdb-status-require! (mdb-cursor-del nodes 0))
    (db-mdb-cursor-get-norequire nodes val-key val-null MDB-NEXT-NODUP))
  (if status-failure?
    (if db-mdb-status-notfound? (status-set-id status-id-success)
      (status-set-group-goto db-status-group-lmdb)))
  (sc-comment "cache")
  (db-free-env-type (+ type-id env:types))
  (label exit
    (db-cursor-close-if-active system)
    (db-cursor-close-if-active nodes)
    (if status-success? (db-txn-commit txn)
      (db-txn-abort txn))
    (return status)))

#;(
(define (db-node-ensure txn data result) (status-t db-txn-t* db-data-list-t* db-ids-t**)
  status-init
  db-mdb-declare-val-id
  db-mdb-declare-val-data
  (db-mdb-cursor-define-2 txn nodes data-intern->id)
  (define id db-id-t)
  (struct-set val-id mv-data (address-of id))
  (while data
    (if
      (or
        (> (struct-get (db-data-list-first data) size) db-size-data-max)
        (< (struct-get (db-data-list-first data) size) db-size-data-min))
      (db-status-set-id-goto db-status-id-data-length))
    (struct-set val-data mv-size (struct-get (db-data-list-first data) size))
    (struct-set val-data mv-data (struct-get (db-data-list-first data) data))
    (db-mdb-cursor-get! data-intern->id val-data val-id MDB-SET-KEY)
    (if (status-id-is? MDB-NOTFOUND)
      (begin
        (status-require! (db-id-next-intern (address-of id)))
        (db-mdb-status-require!
          (mdb-cursor-put nodes (address-of val-id) (address-of val-data) 0))
        (db-mdb-status-require!
          (mdb-cursor-put data-intern->id (address-of val-data) (address-of val-id) 0))
        (set (pointer-get result) (db-ids-add (pointer-get result) id)))
      (if db-mdb-status-success?
        (begin
          (set (pointer-get result) (db-ids-add (pointer-get result) (db-mdb-val->id val-id)))
          (struct-set val-id mv-data (address-of id)))
        (status-set-group-goto db-status-group-lmdb)))
    (set data (db-data-list-rest data)))
  (label exit
    (db-mdb-cursor-close-2 nodes data-intern->id)
    (return status)))

(define (db-node-identify txn ids result) (status-t db-txn-t* db-ids-t* db-ids-t**)
  "filter existing ids from the list of given ids and add the result to \"result\""
  status-init
  db-mdb-declare-val-id
  (db-mdb-cursor-declare nodes)
  (db-mdb-cursor-open txn nodes)
  (while ids
    (set (struct-get val-id mv-data) (db-ids-first-address ids))
    (set status.id (mdb-cursor-get nodes (address-of val-id) (address-of val-null) MDB-SET))
    (if db-mdb-status-success?
      (set (pointer-get result) (db-ids-add (pointer-get result) (db-ids-first ids)))
      db-mdb-status-require-notfound)
    (set ids (db-ids-rest ids)))
  (label exit
    (mdb-cursor-close nodes)
    (if (= MDB-NOTFOUND status.id) (set status.id status-id-success))
    (return status)))

(define (db-node-exists? txn ids result) (status-t db-txn-t* db-ids-t* boolean*)
  status-init
  db-mdb-declare-val-id
  (db-mdb-cursor-define txn nodes)
  (while ids
    (set (struct-get val-id mv-data) (db-ids-first-address ids))
    (set status.id (mdb-cursor-get nodes (address-of val-id) (address-of val-null) MDB-SET))
    (if (= MDB-NOTFOUND status.id)
      (begin
        (set (pointer-get result) #f)
        (set status.id status-id-success)
        (goto exit))
      status-require)
    (set ids (db-ids-rest ids)))
  (set (pointer-get result) #t)
  (label exit
    (mdb-cursor-close nodes)
    (return status)))

(define (db-id-create txn count result) (status-t db-txn-t* b32 db-ids-t**)
  "create \"count\" number of id-type nodes and add their ids to \"result\""
  status-init
  db-mdb-reset-val-null
  (db-mdb-cursor-declare nodes)
  (db-mdb-cursor-open txn nodes)
  db-mdb-declare-val-id
  (define
    id db-id-t
    ids-temp db-ids-t*)
  (set (struct-get val-id mv-data) (address-of id))
  (while count
    (status-require! (db-id-next-id (address-of id)))
    (db-mdb-status-require! (mdb-cursor-put nodes (address-of val-id) (address-of val-null) 0))
    (db-ids-add! (pointer-get result) id ids-temp)
    (decrement count))
  (label exit
    (mdb-cursor-close nodes)
    (return status)))

(define (db-intern-update txn id data) (status-t db-txn-t* db-id-t db-data-t)
  status-init
  db-mdb-declare-val-id
  db-mdb-declare-val-data
  (struct-set val-data mv-data (struct-get data data) mv-size (struct-get data size))
  (db-mdb-cursor-declare-2 nodes data-intern->id)
  (db-mdb-cursor-open-2 txn nodes data-intern->id)
  ; duplicate prevention
  (db-mdb-cursor-get! data-intern->id val-data val-null MDB-SET)
  (if db-mdb-status-success?
    (db-status-set-id-goto db-status-id-duplicate) db-mdb-status-require-notfound)
  (struct-set val-id mv-data (address-of id))
  (set status.id (mdb-cursor-get nodes (address-of val-id) (address-of val-data) MDB-SET-KEY))
  (if db-mdb-status-success?
    (begin
      (set status.id
        (mdb-cursor-get data-intern->id (address-of val-data) (address-of val-null) MDB-SET))
      (if db-mdb-status-success? (mdb-cursor-del data-intern->id 0) db-mdb-status-require-notfound)
      (struct-set val-data mv-data (struct-get data data) mv-size (struct-get data size))
      (db-mdb-status-require!
        (mdb-cursor-put nodes (address-of val-id) (address-of val-data) MDB-CURRENT))
      (db-mdb-status-require!
        (mdb-cursor-put data-intern->id (address-of val-data) (address-of val-id) 0)))
    db-mdb-status-require-notfound)
  (label exit
    (return status)))

(define (db-graph-internal-delete txn left right label ordinal graph-lr graph-rl graph-ll)
  (status-t
    db-txn-t*
    db-ids-t* db-ids-t* db-ids-t* db-ordinal-match-data-t* MDB-cursor* MDB-cursor* MDB-cursor*))

(define
  (db-delete-one
    txn id nodes data-intern->id data-extern->extern graph-lr graph-rl graph-ll)
  (status-t
    db-txn-t* db-id-t MDB-cursor* MDB-cursor* MDB-cursor* MDB-cursor* MDB-cursor* MDB-cursor*)
  status-init
  db-mdb-declare-val-graph-data
  db-mdb-declare-val-id
  db-mdb-declare-val-id-2
  db-mdb-declare-val-data
  (struct-set val-id mv-data (address-of id))
  (db-mdb-cursor-get! nodes val-id val-data MDB-SET-KEY)
  (if db-mdb-status-success?
    ;delete index references
    (cond
      ( (db-intern? id)
        (begin
          (set status.id
            (mdb-cursor-get data-intern->id (address-of val-data) (address-of val-null) MDB-SET))
          (if db-mdb-status-success?
            (db-mdb-cursor-del! data-intern->id 0) db-mdb-status-require-notfound)))
      ( (and (db-extern? id) (struct-get val-data mv-size))
        (set status.id
          (mdb-cursor-get data-extern->extern (address-of val-data) (address-of val-null) MDB-SET))
        (if db-mdb-status-success?
          (db-mdb-cursor-del! data-extern->extern 0) db-mdb-status-require-notfound)))
    db-mdb-status-require-notfound)
  (db-mdb-cursor-del! nodes 0)
  (label exit
    (return status)))

(define (db-delete txn ids) (status-t db-txn-t* db-ids-t*)
  status-init
  ;checking for empty ids because it would be an non-filter in db-graph-internal-delete
  (if (not ids) (return status))
  (db-mdb-cursor-declare-3 nodes data-intern->id data-extern->extern)
  (db-mdb-cursor-declare-3 graph-lr graph-rl graph-ll)
  (db-mdb-cursor-open-3 txn graph-lr graph-rl graph-ll)
  (status-require! (db-graph-internal-delete txn 0 0 ids 0 graph-lr graph-rl graph-ll))
  (status-require! (db-graph-internal-delete txn ids 0 0 0 graph-lr graph-rl graph-ll))
  (status-require! (db-graph-internal-delete txn 0 ids 0 0 graph-lr graph-rl graph-ll))
  (db-mdb-cursor-open-3 txn nodes data-intern->id data-extern->extern)
  (while (and ids status-success?)
    (set status
      (db-delete-one
        txn
        (db-ids-first ids)
        nodes data-intern->id data-extern->extern graph-lr graph-rl graph-ll))
    (set ids (db-ids-rest ids)))
  (label exit
    (db-mdb-cursor-close-3 nodes data-intern->id data-extern->extern)
    (db-mdb-cursor-close-3 graph-lr graph-rl graph-ll)
    (return status)))

(define (db-node-read state count result) (status-t db-node-read-state-t* b32 db-data-records-t**)
  status-init
  (set count (optional-count count))
  db-mdb-declare-val-id
  db-mdb-declare-val-data
  (pre-let
    (nodes
      state:cursor
      skip
      (bit-and db-read-option-skip state:options)
      types state:types)
    (status-require! state:status)
    (define
      data-records db-data-records-t*
      data-record db-data-record-t)
    (if skip
      (while count
        (db-mdb-status-require!
          (mdb-cursor-get nodes (address-of val-null) (address-of val-null) MDB-NEXT-NODUP))
        (if (db-node-types-match? types (db-mdb-val->id val-id)) (set count (- count 1))))
      (begin
        (db-mdb-status-require!
          (mdb-cursor-get nodes (address-of val-id) (address-of val-data) MDB-GET-CURRENT))
        (while count
          (if (db-node-types-match? types (db-mdb-val->id val-id))
            (begin
              (struct-set data-record
                id (db-mdb-val->id val-id)
                data (struct-get val-data mv-data) size (struct-get val-data mv-size))
              (set data-records (db-data-records-add (pointer-get result) data-record))
              (if (not data-records) (db-status-set-id-goto db-status-id-memory))
              (set (pointer-get result) data-records)
              (set count (- count 1))))
          (db-mdb-status-require!
            (mdb-cursor-get nodes (address-of val-id) (address-of val-data) MDB-NEXT-NODUP)))))
    (label exit
      db-status-no-more-data-if-mdb-notfound
      (set state:status status)
      (return status))))

(define (db-node-select txn type-bits offset result)
  (status-t db-txn-t* b8 b32 db-node-read-state-t*)
  "select nodes optionally filtered by type.
  type-bits is the bit-or of db-type-bit-* values"
  status-init
  (db-mdb-cursor-define txn nodes)
  (db-mdb-status-require!
    (mdb-cursor-get nodes (address-of val-null) (address-of val-null) MDB-FIRST))
  (struct-pointer-set result cursor nodes types type-bits status status options 0)
  (db-select-ensure-offset result offset db-node-read)
  (label exit
    (if (not db-mdb-status-success?)
      (begin
        (mdb-cursor-close nodes)
        (if (status-id-is? MDB-NOTFOUND)
          (begin
            (status-set-id db-status-id-no-more-data)
            (struct-pointer-set result cursor 0)))))
    (struct-pointer-set result status status)
    (return status)))

(define (db-node-selection-destroy state) (b0 db-node-read-state-t*)
  (if (struct-pointer-get state cursor) (mdb-cursor-close (struct-pointer-get state cursor))))
)