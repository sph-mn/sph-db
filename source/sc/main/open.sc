(sc-comment
  "system btree entry format. key -> value
     type-label id -> 8b:name-len name db-field-count-t:field-count (b8:field-type b8:name-len name) ...
     index-label db-type-id-t:type-id db-field-count-t:field-offset ... -> ()")

(define (db-open-root options path data-path) (status-t db-open-options-t* b8** b8**)
  "prepare the database filesystem root path.
  create the full directory path if it does not exist.
  on success sets path and data-path to new strings"
  status-init
  (declare
    path-temp b8*
    data-path-temp b8*)
  (set path-temp (string-clone (pointer-get path)))
  (if (not path-temp) (db-status-set-id-goto db-status-id-memory))
  (if
    (not
      (ensure-directory-structure
        path-temp (bit-or 384 (struct-pointer-get options file-permissions))))
    (db-status-set-id-goto db-status-id-path-not-accessible-db-root))
  (set data-path-temp (string-append path-temp "/data"))
  (if (not data-path-temp) (db-status-set-id-goto db-status-id-memory))
  (pointer-set path path-temp)
  (pointer-set data-path data-path-temp)
  (label exit
    (return status)))

(define (db-open-mdb-env-flags options) (b32 db-open-options-t*)
  (return
    (if* (struct-pointer-get options env-open-flags)
      (struct-pointer-get options env-open-flags)
      (bit-or
        MDB-NOSUBDIR
        MDB-WRITEMAP
        (if* (struct-pointer-get options read-only?) MDB-RDONLY 0)
        (if* (struct-pointer-get options filesystem-has-ordered-writes?) MDB-MAPASYNC 0)))))

(define (db-open-mdb-env txn data-path options) (status-t db-txn-t b8* db-open-options-t*)
  status-init
  (declare mdb-env MDB-env*)
  (db-mdb-status-require! (mdb-env-create (address-of mdb-env)))
  (db-mdb-status-require!
    (mdb-env-set-maxdbs mdb-env (struct-pointer-get options maximum-db-count)))
  (db-mdb-status-require!
    (mdb-env-set-mapsize mdb-env (struct-pointer-get options maximum-size-octets)))
  (db-mdb-status-require!
    (mdb-env-set-maxreaders mdb-env (struct-pointer-get options maximum-reader-count)))
  (db-mdb-status-require!
    (mdb-env-open
      mdb-env data-path (db-open-mdb-env-flags options) (struct-pointer-get options file-permissions)))
  (db-mdb-status-require!
    (mdb-dbi-open
      txn.mdb-txn "id->data" MDB-CREATE (address-of (struct-pointer-get txn.env dbi-id->data))))
  (db-mdb-status-require!
    (mdb-set-compare
      txn.mdb-txn
      (struct-pointer-get txn.env dbi-id->data) (convert-type db-mdb-compare-id MDB-cmp-func*)))
  (struct-pointer-set txn.env mdb-env mdb-env)
  (label exit
    (if status-failure? (if mdb-env (mdb-env-close mdb-env)))
    (return status)))

(define (db-open-format system txn) (status-t MDB-cursor* db-txn-t)
  "check that the format the database was created with matches the current configuration.
  id, type and ordinal sizes are set at compile time and cant be changed for a database
  after data has been inserted"
  status-init
  (declare
    label b8
    format (array b8 (3) db-size-id db-size-type-id db-size-ordinal))
  (db-mdb-declare-val val-key 1)
  (db-mdb-declare-val val-data 3)
  (set
    label db-system-label-format
    val-key.mv-data (address-of label))
  (db-mdb-cursor-get-norequire system val-key val-data MDB-SET)
  (if db-mdb-status-success?
    (begin
      (define data b8* val-data.mv-data)
      (if
        (not
          (and
            (= (pointer-get data 0) (pointer-get format 0))
            (= (pointer-get data 1) (pointer-get format 1))
            (= (pointer-get data 2) (pointer-get format 2))))
        (begin
          ; differing type sizes are not a problem if there is no data yet.
          ; this only checks if any tables/indices exist by checking the contents of the system btree
          (define stat-info MDB-stat)
          (db-mdb-status-require!
            (mdb-stat txn.mdb-txn (struct-pointer-get txn.env dbi-system) (address-of stat-info)))
          (if (= 1 stat-info.ms-entries)
            (begin
              ; only one entry, the previous format entry. update
              (set val-data.mv-data format)
              (db-mdb-cursor-put system val-key val-data))
            (begin
              (fprintf
                stderr
                "database sizes: (id %u) (type: %u) (ordinal %u)"
                (pointer-get data 0) (pointer-get data 1) (pointer-get data 2))
              (db-status-set-id-goto db-status-id-different-format))))))
    (begin
      db-mdb-status-require-notfound
      ; no format entry exists yet
      (struct-set val-data mv-data format)
      (db-mdb-cursor-put system val-key val-data)))
  (label exit
    (return status)))

(define (db-open-system-sequence system result) (status-t MDB-cursor* db-type-id-t*)
  "initialise the sequence for system ids like type ids in result.
  set result to the next sequence value. id zero is reserved for null"
  status-init
  (declare
    current db-type-id-t
    key (array b8 (db-size-system-key)))
  (db-mdb-declare-val val-key 1)
  (set
    current 0
    (db-system-key-label key) db-system-label-type
    (db-system-key-id key) db-type-id-max
    val-key.mv-data key)
  (sc-comment "search from the last possible type or the key after")
  (db-mdb-cursor-get-norequire system val-key val-null MDB-SET-RANGE)
  (if db-mdb-status-success?
    (begin
      (if (= db-system-label-type (db-system-key-label val-key.mv-data))
        (begin
          (set current (db-system-key-id val-key.mv-data))
          (goto exit))
        (begin
          (db-mdb-cursor-get-norequire system val-key val-null MDB-PREV)
          (if db-mdb-status-success?
            (begin
              (set current (db-system-key-id val-key.mv-data))
              (goto exit))
            db-mdb-status-require-notfound))))
    db-mdb-status-require-notfound)
  (sc-comment "search from the last key")
  (db-mdb-cursor-get-norequire system val-key val-null MDB-LAST)
  (if db-mdb-status-success?
    (begin
      (while
        (and
          db-mdb-status-success? (not (= db-system-label-type (db-system-key-label val-key.mv-data))))
        (db-mdb-cursor-get-norequire system val-key val-null MDB-PREV))
      (if db-mdb-status-success?
        (begin
          (set current (db-system-key-id val-key.mv-data))
          (goto exit))
        db-mdb-status-require-notfound))
    db-mdb-status-require-notfound)
  (label exit
    db-status-success-if-mdb-notfound
    (set (pointer-get result) (if* (= db-type-id-max current) db-type-id-max (+ 1 current)))
    (return status)))

(define (db-open-type-read-fields data-pointer type) (status-t b8** db-type-t*)
  "read information for fields from system btree type data"
  status-init
  (declare
    count db-field-count-t
    data b8*
    field-type b8
    field-pointer db-field-t*
    fields db-field-t*
    fixed-count db-field-count-t
    fixed-offsets db-field-count-t*
    index db-field-count-t
    offset db-field-count-t)
  (set
    data (pointer-get data-pointer)
    fixed-offsets 0
    fixed-count 0
    fields 0
    index 0
    count (pointer-get (convert-type data db-field-count-t*))
    data (+ 1 (convert-type data db-field-count-t*)))
  (db-calloc fields count (sizeof db-field-t))
  (sc-comment "field")
  (while (< index count)
    (sc-comment "type")
    (set
      field-pointer (+ index fields)
      field-type (pointer-get data)
      data (+ 1 data)
      (struct-pointer-get field-pointer type) field-type)
    (sc-comment "name")
    (status-require!
      (db-read-length-prefixed-string-b8
        (address-of data) (address-of (struct-pointer-get field-pointer name))))
    (if (db-field-type-fixed? field-type) (set fixed-count (+ 1 fixed-count)))
    (set index (+ 1 index)))
  (sc-comment "offsets")
  (if fixed-count
    (begin
      (db-malloc fixed-offsets (* fixed-count (sizeof db-field-count-t)))
      (set index 0)
      (while (< index fixed-count)
        (set
          offset (+ offset (db-field-type-size (struct-pointer-get (+ index fields) type)))
          (pointer-get (+ index fixed-offsets)) offset
          index (+ 1 index)))))
  (struct-pointer-set type
    fields fields
    fields-count count fields-fixed-count fixed-count fields-fixed-offsets fixed-offsets)
  (pointer-set data-pointer data)
  (label exit
    (if status-failure? (db-free-env-types-fields (address-of fields) count))
    (return status)))

(define (db-open-type system-key system-value types) (status-t b8* b8* db-type-t*)
  status-init
  (declare
    id db-type-id-t
    type-pointer db-type-t*)
  (set
    id (db-system-key-id system-key)
    type-pointer (+ id types)
    (struct-pointer-get type-pointer id) id)
  (status-require!
    (db-read-length-prefixed-string-b8
      (address-of system-value) (address-of (struct-pointer-get type-pointer name))))
  (status-require! (db-open-type-read-fields (address-of system-value) type-pointer))
  (label exit
    (return status)))

(define (db-open-types system txn) (status-t MDB-cursor* db-txn-t)
  "load type info into cache. open all dbi.
   max type id size is currently 16 bit because of using an array to cache types
   instead of a slower hash table which would be needed otherwise.
   the type array has free space at the end for possible new types.
   type id zero is the system btree"
  status-init
  (declare
    key (array b8 (db-size-system-key))
    types db-type-t*
    types-len db-type-id-t
    system-sequence db-type-id-t)
  (db-mdb-declare-val val-key (+ 1 (sizeof db-id-t)))
  (db-mdb-declare-val val-data 3)
  (set types 0)
  (if (< 16 (sizeof db-type-id-t))
    (status-set-both-goto db-status-group-db db-status-id-max-type-id-size))
  (status-require! (db-open-system-sequence system (address-of system-sequence)))
  (set
    types-len (- db-type-id-max system-sequence)
    types-len (+ system-sequence (if* (< 20 types-len) 20 types-len))
    (db-system-key-label key) db-system-label-type
    (db-system-key-id key) 0
    val-key.mv-data key)
  (db-calloc types types-len (sizeof db-type-t))
  (struct-set (pointer-get types 0) sequence system-sequence)
  (db-mdb-cursor-get-norequire system val-key val-data MDB-SET-RANGE)
  (while
    (and db-mdb-status-success? (= db-system-label-type (db-system-key-label val-key.mv-data)))
    (status-require! (db-open-type val-key.mv-data val-data.mv-data types))
    (db-mdb-cursor-get-norequire system val-key val-data MDB-NEXT))
  (if (not db-mdb-status-success?) db-mdb-status-require-notfound)
  (struct-pointer-set txn.env types types types-len types-len)
  (label exit
    (if status-failure? (db-free-env-types (address-of types) types-len))
    (return status)))

(define (db-open-indices system txn) (status-t MDB-cursor* db-txn-t)
  "extend type cache with index information. there can be multiple indices per type"
  status-init
  (db-mdb-declare-val val-key (+ 1 (sizeof db-id-t)))
  (declare
    current-type-id db-type-id-t
    fields db-field-t*
    fields-count db-field-count-t
    indices db-index-t*
    indices-alloc-count db-field-count-t
    indices-count db-field-count-t
    indices-temp db-index-t*
    key (array b8 (db-size-system-key))
    type-id db-type-id-t
    types db-type-t*
    types-len db-type-id-t)
  (set
    indices 0
    fields 0
    current-type-id 0
    indices-count 0
    (db-system-key-label key) db-system-label-index
    (db-system-key-id key) 0
    val-key.mv-data key
    types (struct-pointer-get txn.env types)
    types-len (struct-pointer-get txn.env types-len))
  (db-mdb-cursor-get-norequire system val-key val-null MDB-SET-RANGE)
  (while
    (and db-mdb-status-success? (= db-system-label-index (db-system-key-label val-key.mv-data)))
    (set type-id (db-system-key-id val-key.mv-data))
    (if (= current-type-id type-id)
      (begin
        (set indices-count (+ 1 indices-count))
        (if (> indices-count indices-alloc-count)
          (begin
            (set indices-alloc-count (* 2 indices-alloc-count))
            (db-realloc indices indices-temp (* indices-alloc-count (sizeof db-index-t))))))
      (begin
        (if indices-count
          (begin
            (sc-comment "reallocate indices from indices-alloc-count to indices-count")
            (if (not (= indices-alloc-count indices-count))
              (db-realloc indices indices-temp (* indices-count (sizeof db-index-t))))
            (set (struct-get (pointer-get types current-type-id) indices) indices)))
        (set
          current-type-id type-id
          indices-count 1
          indices-alloc-count 10)
        (db-calloc indices indices-alloc-count (sizeof db-index-t))))
    (set fields-count
      (/
        (- val-key.mv-size (sizeof db-system-label-index) (sizeof db-type-id-t))
        (sizeof db-field-count-t)))
    (db-calloc fields fields-count (sizeof db-field-count-t))
    (struct-set (pointer-get indices (- indices-count 1)) fields fields fields-count fields-count)
    (db-mdb-cursor-get-norequire system val-key val-null MDB-NEXT))
  db-status-success-if-mdb-notfound
  (if db-mdb-status-success? (set (struct-get (pointer-get types current-type-id) indices) indices))
  (label exit
    (if status-failure? (db-free-env-types-indices (address-of indices) indices-count))
    (return status)))

(define (db-type-first-id id->data type-id result) (status-t MDB-cursor* db-type-id-t db-id-t*)
  "get the first data id of type and save it in result. result is set to zero if none has been found"
  status-init
  db-mdb-declare-val-id
  (define id db-id-t)
  (set
    (pointer-get result) 0
    id 0
    val-id.mv-data (address-of id))
  (db-id-set-type id type-id)
  (db-mdb-cursor-get-norequire id->data val-id val-null MDB-SET-RANGE)
  (if db-mdb-status-success?
    (if (= type-id (db-type-id (db-mdb-val->id val-id)))
      (set (pointer-get result) (db-mdb-val->id val-id)))
    (if db-mdb-status-notfound?
      (status-set-id status-id-success) (status-set-group-goto db-status-group-lmdb)))
  (label exit
    (return status)))

(define (db-type-last-key-id id->data type-id result) (status-t MDB-cursor* db-type-id-t db-id-t*)
  "sets result to the last key id if the last key is of type, otherwise sets result to zero.
  leaves cursor at last key. status is mdb-notfound if database is empty"
  status-init
  db-mdb-declare-val-id
  (set (pointer-get result) 0)
  (db-mdb-cursor-get-norequire id->data val-id val-null MDB-LAST)
  (if (and db-mdb-status-success? (= type-id (db-type-id (db-mdb-val->id val-id))))
    (set (pointer-get result) (db-mdb-val->id val-id)))
  (return status))

(define (db-type-last-id id->data type-id result) (status-t MDB-cursor* db-type-id-t db-id-t*)
  "algorithm: check if data of type exists, if yes then check if last key is of type or
    position next type and step back"
  status-init
  db-mdb-declare-val-id
  (declare id db-id-t)
  (sc-comment
    "if last key is not of type then either there are greater type-ids or no data of type exists")
  (set status (db-type-last-key-id id->data type-id (address-of id)))
  (if db-mdb-status-success?
    (if id
      (begin
        (set (pointer-get result) id)
        (goto exit)))
    (if db-mdb-status-notfound?
      ; database is empty
      (begin
        (set (pointer-get result) 0)
        (goto exit))
      (status-set-group-goto db-status-group-lmdb)))
  (sc-comment
    "database is not empty, and the last key is not of searched type.
     type-id +1 is not greater than max")
  (status-require! (db-type-first-id id->data (+ 1 type-id) (address-of id)))
  (if (not id)
    (begin
      (sc-comment
        "no greater type-id found. since the searched type is not the last,
         all existing type-ids are smaller")
      (set (pointer-get result) 0)
      (goto exit)))
  (sc-comment "greater type found, step back")
  (db-mdb-cursor-get id->data val-id val-null MDB-PREV)
  (set (pointer-get result)
    (if* (= type-id (db-type-id (db-mdb-val->id val-id))) (db-mdb-val->id val-id) 0))
  (label exit
    (return status)))

(define (db-open-sequences txn) (status-t db-txn-t)
  "initialise the sequence for each type by searching the max key for the type.
   lowest sequence value is 1.
   algorithm:
     check if any entry for type exists, then position at max or first next type key,
     take or step back to previous key"
  status-init
  (declare
    id db-id-t
    types db-type-t*
    types-len db-type-id-t
    index db-type-id-t)
  (db-cursor-declare id->data)
  db-mdb-declare-val-id
  (set
    types (struct-pointer-get txn.env types)
    types-len (struct-pointer-get txn.env types-len)
    index 0)
  (db-cursor-open txn id->data)
  (while (< index types-len)
    (set id 0)
    (status-require!
      (db-type-last-id id->data (struct-get (pointer-get types index) id) (address-of id)))
    (set
      id (db-id-id id)
      (struct-get (pointer-get types index) sequence) (if* (< id db-id-id-max) (+ 1 id) id)
      index (+ 1 index)))
  (label exit
    (return status)))

(define (db-open-system txn) (status-t db-txn-t)
  "ensure that the system tree exists with default values.
  check format and load cached values"
  status-init
  (db-mdb-cursor-declare system)
  (db-mdb-status-require!
    (mdb-dbi-open
      txn.mdb-txn "system" MDB-CREATE (address-of (struct-pointer-get txn.env dbi-system))))
  (db-cursor-open txn system)
  (status-require! (db-open-format system txn))
  (status-require! (db-open-types system txn))
  (status-require! (db-open-indices system txn))
  (status-require! (db-open-sequences txn))
  (label exit
    (mdb-cursor-close system)
    (return status)))

(define (db-open-graph txn) (status-t db-txn-t)
  "ensure that the trees used for the graph exist, configure and open dbi"
  status-init
  (declare
    db-options b32
    dbi-left->right MDB-dbi
    dbi-right->left MDB-dbi
    dbi-label->left MDB-dbi)
  (set db-options (bit-or MDB-CREATE MDB-DUPSORT MDB-DUPFIXED))
  (db-mdb-status-require!
    (mdb-dbi-open txn.mdb-txn "left->right" db-options (address-of dbi-left->right)))
  (db-mdb-status-require!
    (mdb-dbi-open txn.mdb-txn "right->left" db-options (address-of dbi-right->left)))
  (db-mdb-status-require!
    (mdb-dbi-open txn.mdb-txn "label->left" db-options (address-of dbi-label->left)))
  (db-mdb-status-require!
    (mdb-set-compare
      txn.mdb-txn dbi-left->right (convert-type db-mdb-compare-graph-key MDB-cmp-func*)))
  (db-mdb-status-require!
    (mdb-set-compare
      txn.mdb-txn dbi-right->left (convert-type db-mdb-compare-graph-key MDB-cmp-func*)))
  (db-mdb-status-require!
    (mdb-set-compare txn.mdb-txn dbi-label->left (convert-type db-mdb-compare-id MDB-cmp-func*)))
  (db-mdb-status-require!
    (mdb-set-dupsort
      txn.mdb-txn dbi-left->right (convert-type db-mdb-compare-graph-data MDB-cmp-func*)))
  (db-mdb-status-require!
    (mdb-set-dupsort txn.mdb-txn dbi-right->left (convert-type db-mdb-compare-id MDB-cmp-func*)))
  (db-mdb-status-require!
    (mdb-set-dupsort txn.mdb-txn dbi-label->left (convert-type db-mdb-compare-id MDB-cmp-func*)))
  (struct-pointer-set txn.env
    dbi-left->right dbi-left->right dbi-right->left dbi-right->left dbi-label->left dbi-label->left)
  (label exit
    (return status)))

(define (db-open-options-set-defaults a) (db-open-options-t db-open-options-t*)
  (struct-pointer-set a
    read-only? #f
    maximum-size-octets 17179869183
    maximum-reader-count 65535
    maximum-db-count 255 env-open-flags 0 filesystem-has-ordered-writes? #t file-permissions 384))

(define (db-open path-argument options-pointer env) (status-t b8* db-open-options-t* db-env-t*)
  status-init
  (declare
    options db-open-options-t
    path b8*
    data-path b8*)
  (db-txn-declare env txn)
  (set data-path 0)
  (if (struct-pointer-get env open) (return status))
  (if (not path) (db-status-set-id-goto db-status-id-missing-argument-db-root))
  db-mdb-reset-val-null
  (set
    db-type-id-max (- (pow 2 (* 8 (sizeof db-type-id-t))) 1)
    db-type-id-mask db-type-id-max
    db-id-id-max (bit-not (bit-and (bit-not db-id-max) db-type-id-mask)))
  (if options-pointer
    (set options (pointer-get options-pointer)) (db-open-options-set-defaults (address-of options)))
  (status-require!
    (db-open-root (address-of options) (address-of path-argument) (address-of data-path)))
  (db-txn-write-begin txn)
  (status-require! (db-open-mdb-env txn data-path (address-of options)))
  (status-require! (db-open-system txn))
  (status-require! (db-open-graph txn))
  (db-txn-commit txn)
  (pthread-mutex-init (address-of (struct-pointer-get env mutex)) 0)
  (struct-pointer-set env open #t)
  (label exit
    (free data-path)
    (if status-failure?
      (begin
        (if txn.mdb-txn (db-txn-abort txn))
        (db-close env)))
    (return status)))