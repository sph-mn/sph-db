(pre-define
  db-type-flag-virtual 1
  db-type-name-max-len 255)

(define (db-env-types-extend env type-id) (status-t db-env-t* db-type-id-t)
  "extend the types array if type-id is an index out of bounds"
  status-init
  (declare
    types-temp db-type-t*
    types-len db-type-id-t
    types db-type-t*
    index db-type-id-t)
  (set types-len env:types-len)
  (if (< types-len type-id)
    (begin
      (set
        types env:types
        index types-len
        types-len (+ 20 type-id))
      (db-realloc types types-temp types-len)
      (while (< index types-len)
        (set (: (+ index types) id) 0))
      (struct-pointer-set env types types types-len types-len)))
  (label exit
    (return status)))

(define (db-type-get env name) (db-type-t* db-env-t* b8*)
  "return a pointer to the type struct for the type with the given name. zero if not found"
  (declare
    index db-type-id-t
    types-len db-type-id-t
    type db-type-t*)
  (set
    index 0
    types-len env:types-len)
  (while (< index types-len)
    (set type (+ index env:types))
    (if (not (strcmp name type:name)) (return type))
    (set index (+ 1 index)))
  (return 0))

#;(
(define (db-type-new env name field-count fields flags result)
  (status-t db-env-t* b8* db-field-count-t db-field-t* b8 db-type-id-t)
  "key-value: type-label id -> name-len name field-count (field-type name-len name) ..."
  status-init
  (declare
    val-key MDB-val
    val-data MDB-val
    data b8*
    data-temp b8*
    name-len b8
    index db-field-count-t
    field db-field-t
    types-temp db-type-t*
    type-id db-type-id-t
    key (array b8 (db-size-system-key)))
  (db-txn-declare env txn)
  (db-txn-write-begin txn)
  (sc-comment "check if type with name exists")
  (if (db-type-get txn.env name) (status-set-both-goto db-status-group-db db-status-id-duplicate))
  (sc-comment "check name length limit")
  (if (< db-type-name-max-len (strlen name))
    (status-set-both-goto db-status-group-db db-status-id-data-length))
  (sc-comment "prepare system key/value data")
  (set
    val-key.mv-size db-size-id-type
    val-data.mv-size 0
    name-len (strlen name)
    (db-system-key-label key) db-system-label-type
    (pointer-get (convert-type data-temp b8*)) name-len
    data-temp (+ 1 data-temp))
  (memcpy data-temp name name-len)
  (set
    data-temp (+ name-len data-temp)
    field-count (pointer-get (convert-type data-temp db-field-count-t*))
    data-temp (+ (sizeof db-field-count-t) data-temp)
    index 0)
  (while (< index field-count)
    (set
      field (pointer-get fields index)
      (pointer-get data-temp) field.type
      data-temp (+ 1 data-temp)
      name-len (strlen field.name)
      (pointer-get data-temp) name-len
      data-temp (+ 1 data-temp))
    ; todo: use memcpy?
    (memcpy data-temp field.name name-len)
    (set
      data-temp (+ name-len data-temp)
      index (+ 1 index)))
  (sc-comment "create id and insert")
  (db-mdb-cursor-define txn.mdb-txn (struct-pointer-get txn.env dbi-system) system)
  (status-require! (db-sequence-next txn.env 0 (address-of type-id)))
  (set (db-system-key-id key) type-id)
  (db-mdb-cursor-put system val-key val-data)
  (sc-comment "update schema cache")
  (status-require! (db-env-types-extend txn.env type-id))
  (db-open-type val-key.mv-data val-data.mv-data (struct-pointer-get txn.env types))
  (label exit
    (mdi-cursor-close system)
    (return status)))

(define (db-type-delete env id) (status-t db-env-t* db-type-id-t)
  status-init
  (db-mdb-cursor-define txn.mdb-txn (struct-pointer-get txn.env dbi-system) system)
  (db-mdb-cursor-get-norequire system val-key val-null)
  (if db-mdb-status-success?
    (db-mdb-status-require! (mdb-cursor-del system 0)) db-mdb-status-require-notfound)
  (label exit
    db-status-no-more-data-if-mdb-notfound
    (return status)))

;-- old code --;

(define (db-node-ensure txn data result) (status-t db-txn-t* db-data-list-t* db-ids-t**)
  status-init
  db-mdb-declare-val-id
  db-mdb-declare-val-data
  (db-mdb-cursor-define-2 txn id->data data-intern->id)
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
          (mdb-cursor-put id->data (address-of val-id) (address-of val-data) 0))
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
    (db-mdb-cursor-close-2 id->data data-intern->id)
    (return status)))

(define (db-node-identify txn ids result) (status-t db-txn-t* db-ids-t* db-ids-t**)
  "filter existing ids from the list of given ids and add the result to \"result\""
  status-init
  db-mdb-declare-val-id
  (db-mdb-cursor-declare id->data)
  (db-mdb-cursor-open txn id->data)
  (while ids
    (set (struct-get val-id mv-data) (db-ids-first-address ids))
    (set status.id (mdb-cursor-get id->data (address-of val-id) (address-of val-null) MDB-SET))
    (if db-mdb-status-success?
      (set (pointer-get result) (db-ids-add (pointer-get result) (db-ids-first ids)))
      db-mdb-status-require-notfound)
    (set ids (db-ids-rest ids)))
  (label exit
    (mdb-cursor-close id->data)
    (if (= MDB-NOTFOUND status.id) (set status.id status-id-success))
    (return status)))

(define (db-node-exists? txn ids result) (status-t db-txn-t* db-ids-t* boolean*)
  status-init
  db-mdb-declare-val-id
  (db-mdb-cursor-define txn id->data)
  (while ids
    (set (struct-get val-id mv-data) (db-ids-first-address ids))
    (set status.id (mdb-cursor-get id->data (address-of val-id) (address-of val-null) MDB-SET))
    (if (= MDB-NOTFOUND status.id)
      (begin
        (set (pointer-get result) #f)
        (set status.id status-id-success)
        (goto exit))
      status-require)
    (set ids (db-ids-rest ids)))
  (set (pointer-get result) #t)
  (label exit
    (mdb-cursor-close id->data)
    (return status)))

(define (db-id-create txn count result) (status-t db-txn-t* b32 db-ids-t**)
  "create \"count\" number of id-type nodes and add their ids to \"result\""
  status-init
  db-mdb-reset-val-null
  (db-mdb-cursor-declare id->data)
  (db-mdb-cursor-open txn id->data)
  db-mdb-declare-val-id
  (define
    id db-id-t
    ids-temp db-ids-t*)
  (set (struct-get val-id mv-data) (address-of id))
  (while count
    (status-require! (db-id-next-id (address-of id)))
    (db-mdb-status-require! (mdb-cursor-put id->data (address-of val-id) (address-of val-null) 0))
    (db-ids-add! (pointer-get result) id ids-temp)
    (decrement count))
  (label exit
    (mdb-cursor-close id->data)
    (return status)))

(define (db-intern-update txn id data) (status-t db-txn-t* db-id-t db-data-t)
  status-init
  db-mdb-declare-val-id
  db-mdb-declare-val-data
  (struct-set val-data mv-data (struct-get data data) mv-size (struct-get data size))
  (db-mdb-cursor-declare-2 id->data data-intern->id)
  (db-mdb-cursor-open-2 txn id->data data-intern->id)
  ; duplicate prevention
  (db-mdb-cursor-get! data-intern->id val-data val-null MDB-SET)
  (if db-mdb-status-success?
    (db-status-set-id-goto db-status-id-duplicate) db-mdb-status-require-notfound)
  (struct-set val-id mv-data (address-of id))
  (set status.id (mdb-cursor-get id->data (address-of val-id) (address-of val-data) MDB-SET-KEY))
  (if db-mdb-status-success?
    (begin
      (set status.id
        (mdb-cursor-get data-intern->id (address-of val-data) (address-of val-null) MDB-SET))
      (if db-mdb-status-success? (mdb-cursor-del data-intern->id 0) db-mdb-status-require-notfound)
      (struct-set val-data mv-data (struct-get data data) mv-size (struct-get data size))
      (db-mdb-status-require!
        (mdb-cursor-put id->data (address-of val-id) (address-of val-data) MDB-CURRENT))
      (db-mdb-status-require!
        (mdb-cursor-put data-intern->id (address-of val-data) (address-of val-id) 0)))
    db-mdb-status-require-notfound)
  (label exit
    (return status)))

(define (db-graph-internal-delete txn left right label ordinal left->right right->left label->left)
  (status-t
    db-txn-t*
    db-ids-t* db-ids-t* db-ids-t* db-ordinal-match-data-t* MDB-cursor* MDB-cursor* MDB-cursor*))

(define
  (db-delete-one
    txn id id->data data-intern->id data-extern->extern left->right right->left label->left)
  (status-t
    db-txn-t* db-id-t MDB-cursor* MDB-cursor* MDB-cursor* MDB-cursor* MDB-cursor* MDB-cursor*)
  status-init
  db-mdb-declare-val-graph-data
  db-mdb-declare-val-id
  db-mdb-declare-val-id-2
  db-mdb-declare-val-data
  (struct-set val-id mv-data (address-of id))
  (db-mdb-cursor-get! id->data val-id val-data MDB-SET-KEY)
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
  (db-mdb-cursor-del! id->data 0)
  (label exit
    (return status)))

(define (db-delete txn ids) (status-t db-txn-t* db-ids-t*)
  status-init
  ;checking for empty ids because it would be an non-filter in db-graph-internal-delete
  (if (not ids) (return status))
  (db-mdb-cursor-declare-3 id->data data-intern->id data-extern->extern)
  (db-mdb-cursor-declare-3 left->right right->left label->left)
  (db-mdb-cursor-open-3 txn left->right right->left label->left)
  (status-require! (db-graph-internal-delete txn 0 0 ids 0 left->right right->left label->left))
  (status-require! (db-graph-internal-delete txn ids 0 0 0 left->right right->left label->left))
  (status-require! (db-graph-internal-delete txn 0 ids 0 0 left->right right->left label->left))
  (db-mdb-cursor-open-3 txn id->data data-intern->id data-extern->extern)
  (while (and ids status-success?)
    (set status
      (db-delete-one
        txn
        (db-ids-first ids)
        id->data data-intern->id data-extern->extern left->right right->left label->left))
    (set ids (db-ids-rest ids)))
  (label exit
    (db-mdb-cursor-close-3 id->data data-intern->id data-extern->extern)
    (db-mdb-cursor-close-3 left->right right->left label->left)
    (return status)))

(define (db-node-read state count result) (status-t db-node-read-state-t* b32 db-data-records-t**)
  status-init
  (set count (optional-count count))
  db-mdb-declare-val-id
  db-mdb-declare-val-data
  (pre-let
    (id->data
      (struct-pointer-get state cursor)
      skip
      (bit-and db-read-option-skip (struct-pointer-get state options))
      types (struct-pointer-get state types))
    (status-require! (struct-pointer-get state status))
    (define
      data-records db-data-records-t*
      data-record db-data-record-t)
    (if skip
      (while count
        (db-mdb-status-require!
          (mdb-cursor-get id->data (address-of val-null) (address-of val-null) MDB-NEXT-NODUP))
        (if (db-node-types-match? types (db-mdb-val->id val-id)) (set count (- count 1))))
      (begin
        (db-mdb-status-require!
          (mdb-cursor-get id->data (address-of val-id) (address-of val-data) MDB-GET-CURRENT))
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
            (mdb-cursor-get id->data (address-of val-id) (address-of val-data) MDB-NEXT-NODUP)))))
    (label exit
      db-status-no-more-data-if-mdb-notfound
      (struct-pointer-set state status status)
      (return status))))

(define (db-node-select txn type-bits offset result)
  (status-t db-txn-t* b8 b32 db-node-read-state-t*)
  "select nodes optionally filtered by type.
  type-bits is the bit-or of db-type-bit-* values"
  status-init
  (db-mdb-cursor-define txn id->data)
  (db-mdb-status-require!
    (mdb-cursor-get id->data (address-of val-null) (address-of val-null) MDB-FIRST))
  (struct-pointer-set result cursor id->data types type-bits status status options 0)
  (db-select-ensure-offset result offset db-node-read)
  (label exit
    (if (not db-mdb-status-success?)
      (begin
        (mdb-cursor-close id->data)
        (if (status-id-is? MDB-NOTFOUND)
          (begin
            (status-set-id db-status-id-no-more-data)
            (struct-pointer-set result cursor 0)))))
    (struct-pointer-set result status status)
    (return status)))

(define (db-node-selection-destroy state) (b0 db-node-read-state-t*)
  (if (struct-pointer-get state cursor) (mdb-cursor-close (struct-pointer-get state cursor))))
)
