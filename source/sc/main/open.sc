(sc-comment
  "system btree entry format. key -> value
     type-label type-id -> uint8_t:flags db-name-len-t:name-len name db-field-len-t:field-count (int8-t:field-type uint8-t:name-len name) ...
     index-label type-id db-field-len-t:field-offset ... -> ()")

(pre-define db-env-types-extra-count 20)

(define (db-open-root env options path) (status-t db-env-t* db-open-options-t* uint8-t*)
  "prepare the database filesystem root path.
  create the full directory path if it does not exist"
  status-declare
  (declare path-temp uint8-t*)
  (set
    path-temp 0
    path-temp (string-clone path))
  (if (not path-temp) (status-set-both-goto db-status-group-db db-status-id-memory))
  (if (not (ensure-directory-structure path-temp (bit-or 73 options:file-permissions)))
    (status-set-both-goto db-status-group-db db-status-id-path-not-accessible-db-root))
  (set env:root path-temp)
  (label exit
    (if status-is-failure (free path-temp))
    (return status)))

(define (db-open-mdb-env-flags options) (uint32-t db-open-options-t*)
  (return
    (if* options:env-open-flags options:env-open-flags
      (bit-or
        MDB-NOSUBDIR
        MDB-WRITEMAP
        (if* options:is-read-only MDB-RDONLY
          0)
        (if* options:filesystem-has-ordered-writes MDB-MAPASYNC
          0)))))

(define (db-open-mdb-env env options) (status-t db-env-t* db-open-options-t*)
  status-declare
  (declare
    data-path uint8-t*
    mdb-env MDB-env*)
  (set
    mdb-env 0
    data-path (string-append env:root "/data"))
  (if (not data-path) (status-set-both-goto db-status-group-db db-status-id-memory))
  (db-mdb-status-require (mdb-env-create &mdb-env))
  (db-mdb-status-require (mdb-env-set-maxdbs mdb-env options:maximum-db-count))
  (db-mdb-status-require (mdb-env-set-mapsize mdb-env options:maximum-size))
  (db-mdb-status-require (mdb-env-set-maxreaders mdb-env options:maximum-reader-count))
  (db-mdb-status-require
    (mdb-env-open mdb-env data-path (db-open-mdb-env-flags options) options:file-permissions))
  (set
    env:maxkeysize (mdb-env-get-maxkeysize mdb-env)
    env:mdb-env mdb-env)
  (label exit
    (free data-path)
    (if status-is-failure (if mdb-env (mdb-env-close mdb-env)))
    (return status)))

(define (db-open-format system txn) (status-t MDB-cursor* db-txn-t)
  "check that the format the database was created with matches the current configuration.
  id, type and ordinal sizes are set at compile time and cant be changed for a database
  after data has been inserted"
  status-declare
  (declare
    data uint8-t*
    label uint8-t
    format-data uint32-t
    format uint8-t*
    val-key MDB-val
    val-data MDB-val
    stat-info MDB-stat)
  (set
    format (convert-type &format-data uint8-t*)
    (array-get format 0) db-format-version
    (array-get format 1) (sizeof db-id-t)
    (array-get format 2) (sizeof db-type-id-t)
    (array-get format 3) (sizeof db-ordinal-t)
    val-key.mv-size (sizeof uint8-t)
    val-data.mv-size (sizeof format-data)
    label db-system-label-format
    val-key.mv-data &label
    status.id (mdb-cursor-get system &val-key &val-data MDB-SET))
  (if db-mdb-status-is-success
    (begin
      (set data val-data.mv-data)
      (if
        (not
          (and
            (= 4 val-data.mv-size)
            (= (array-get data 0) (array-get format 0))
            (= (array-get data 1) (array-get format 1))
            (= (array-get data 2) (array-get format 2)) (= (array-get data 3) (array-get format 3))))
        (begin
          (sc-comment
            "differing type sizes are not a problem if there is no data yet.
             this only checks if any tables/indices exist by checking the contents of the system btree")
          (db-mdb-status-require (mdb-stat txn.mdb-txn txn.env:dbi-system &stat-info))
          (if (= 1 stat-info.ms-entries)
            (begin
              ; only one entry, the previous format entry. update
              (set val-data.mv-data format)
              (db-mdb-status-require (mdb-cursor-put system &val-key &val-data 0)))
            (begin
              (fprintf
                stderr
                "database sizes: (id %u) (type: %u) (ordinal %u)"
                (array-get data 0) (array-get data 1) (array-get data 2))
              (status-set-both-goto db-status-group-db db-status-id-different-format))))))
    (begin
      db-mdb-status-expect-notfound
      (sc-comment "no format entry exists yet")
      (set val-data.mv-data format)
      (db-mdb-status-require (mdb-cursor-put system &val-key &val-data 0))))
  (set txn.env:format format-data)
  (label exit
    (return status)))

(define (db-open-system-sequence system result) (status-t MDB-cursor* db-type-id-t*)
  "initialise the sequence for system ids like type ids in result.
  set result to the next sequence value. id zero is reserved for null"
  status-declare
  db-mdb-declare-val-null
  (declare
    val-key MDB-val
    current db-type-id-t
    key (array uint8-t (db-size-system-key)))
  (set
    current 0
    (db-system-key-label key) db-system-label-type
    (db-system-key-id key) db-type-id-limit
    val-key.mv-size db-size-system-key
    val-key.mv-data key)
  (sc-comment "search from the last possible type or the key after")
  (set status.id (mdb-cursor-get system &val-key &val-null MDB-SET-RANGE))
  (if db-mdb-status-is-success
    (begin
      (if (= db-system-label-type (db-system-key-label val-key.mv-data))
        (begin
          (set current (db-system-key-id val-key.mv-data))
          (goto exit))
        (begin
          (set status.id (mdb-cursor-get system &val-key &val-null MDB-PREV))
          (if
            (and
              db-mdb-status-is-success (= db-system-label-type (db-system-key-label val-key.mv-data)))
            (begin
              (set current (db-system-key-id val-key.mv-data))
              (goto exit))
            db-mdb-status-expect-notfound))))
    db-mdb-status-expect-notfound)
  (sc-comment "search from the last key")
  (set status.id (mdb-cursor-get system &val-key &val-null MDB-LAST))
  (if db-mdb-status-is-success
    (begin
      (while
        (and
          db-mdb-status-is-success
          (not (= db-system-label-type (db-system-key-label val-key.mv-data))))
        (set status.id (mdb-cursor-get system &val-key &val-null MDB-PREV)))
      (if db-mdb-status-is-success
        (begin
          (set current (db-system-key-id val-key.mv-data))
          (goto exit))
        db-mdb-status-expect-notfound))
    db-mdb-status-expect-notfound)
  (label exit
    db-mdb-status-success-if-notfound
    (set *result
      (if* (= db-type-id-limit current) current
        (+ 1 current)))
    (return status)))

(define (db-type-first-id records type-id result) (status-t MDB-cursor* db-type-id-t db-id-t*)
  "get the first data id of type and save it in result. result is set to zero if none has been found"
  status-declare
  db-mdb-declare-val-null
  db-mdb-declare-val-id
  (declare id db-id-t)
  (set
    *result 0
    id (db-id-add-type 0 type-id)
    val-id.mv-data &id)
  (set status.id (mdb-cursor-get records &val-id &val-null MDB-SET-RANGE))
  (if db-mdb-status-is-success
    (if (= type-id (db-id-type (db-pointer->id val-id.mv-data)))
      (set *result (db-pointer->id val-id.mv-data)))
    (if db-mdb-status-is-notfound (set status.id status-id-success)
      (status-set-group-goto db-status-group-lmdb)))
  (label exit
    (return status)))

(define (db-type-last-key-id records type-id result) (status-t MDB-cursor* db-type-id-t db-id-t*)
  "sets result to the last key id if the last key is of type, otherwise sets result to zero.
  leaves cursor at last key. status is mdb-notfound if database is empty"
  status-declare
  db-mdb-declare-val-id
  db-mdb-declare-val-null
  (set *result 0)
  (set status.id (mdb-cursor-get records &val-id &val-null MDB-LAST))
  (if (and db-mdb-status-is-success (= type-id (db-id-type (db-pointer->id val-id.mv-data))))
    (set *result (db-pointer->id val-id.mv-data)))
  (return status))

(define (db-type-last-id records type-id result) (status-t MDB-cursor* db-type-id-t db-id-t*)
  "get the last existing record id for type or zero if none exist.
   algorithm: check if data of type exists, if yes then check if last key is of type or
   position next type and step back"
  status-declare
  db-mdb-declare-val-id
  db-mdb-declare-val-null
  (declare id db-id-t)
  (sc-comment
    "if last key is of type then there are no greater type-ids and data of type exists.
    if there is no last key, the database is empty")
  (set status (db-type-last-key-id records type-id &id))
  (if db-mdb-status-is-success
    (if id
      (begin
        (set *result id)
        (goto exit)))
    (if db-mdb-status-is-notfound
      (begin
        (sc-comment "database is empty")
        (set *result 0)
        (status-set-both-goto db-status-group-db status-id-success))
      status-goto))
  (sc-comment
    "database is not empty and the last key is not of searched type.
     type-id +1 is not greater than max possible type-id")
  (status-require (db-type-first-id records (+ 1 type-id) &id))
  (if (not id)
    (begin
      (sc-comment
        "no greater type-id found. since the searched type is not the last,
         all existing type-ids are smaller")
      (set *result 0)
      (goto exit)))
  (sc-comment "greater type found, step back")
  (db-mdb-status-require (mdb-cursor-get records &val-id &val-null MDB-PREV))
  (set *result
    (if* (= type-id (db-id-type (db-pointer->id val-id.mv-data))) (db-pointer->id val-id.mv-data)
      0))
  (label exit
    (return status)))

(define (db-open-sequence records type) (status-t MDB-cursor* db-type-t*)
  "initialise the sequence for a type by searching the max used id for the type.
   lowest sequence value is 1.
   algorithm:
     check if any entry for type exists, then position at max or first next type key,
     take or step back to previous key"
  status-declare
  (declare id db-id-t)
  (status-require (db-type-last-id records type:id &id))
  (set
    id (db-id-element id)
    type:sequence
    (if* (< id db-element-id-limit) (+ 1 id)
      id))
  (label exit
    (return status)))

(define (db-open-type-read-fields data-pointer type) (status-t uint8-t** db-type-t*)
  "read information for fields from system btree type data.
  assumes that pointer is positioned at field-count"
  status-declare
  (declare
    count db-fields-len-t
    data uint8-t*
    field-pointer db-field-t*
    field-type db-field-type-t
    fields db-field-t*
    fixed-count db-fields-len-t
    fixed-offsets size-t*
    i db-fields-len-t
    offset db-fields-len-t)
  (set
    data *data-pointer
    fixed-offsets 0
    fixed-count 0
    fields 0
    offset 0
    count (pointer-get (convert-type data db-fields-len-t*))
    data (+ (sizeof db-fields-len-t) data))
  (status-require (db-helper-calloc (* count (sizeof db-field-t)) &fields))
  (for ((set i 0) (< i count) (set i (+ 1 i)))
    (set
      field-type *data
      data (+ (sizeof db-field-type-t) data)
      field-pointer (+ i fields)
      field-pointer:type field-type
      field-pointer:offset i
      field-pointer:size (db-field-type-size field-type))
    (db-read-name &data &field-pointer:name)
    (if (db-field-type-is-fixed field-type) (set fixed-count (+ 1 fixed-count))))
  (sc-comment
    "fixed-field offsets" "example: field-sizes-in-bytes: 1 4 2. fields-fixed-offsets: 1 5 7")
  (if fixed-count
    (begin
      (status-require (db-helper-malloc (* (+ 1 fixed-count) (sizeof size-t)) &fixed-offsets))
      (for ((set i 0) (< i fixed-count) (set i (+ 1 i)))
        (set
          (array-get fixed-offsets i) offset
          offset (+ offset (struct-get (array-get fields i) size))))
      (set (array-get fixed-offsets i) offset)))
  (set
    type:fields fields
    type:fields-len count
    type:fields-fixed-count fixed-count
    type:fields-fixed-offsets fixed-offsets
    *data-pointer data)
  (label exit
    (if status-is-failure (db-free-env-types-fields &fields count))
    (return status)))

(define (db-open-type system-key system-value types records result-type)
  (status-t uint8-t* uint8-t* db-type-t* MDB-cursor* db-type-t**)
  status-declare
  (declare
    id db-type-id-t
    type-pointer db-type-t*)
  (set
    id (db-system-key-id system-key)
    type-pointer (+ id types)
    type-pointer:indices 0
    type-pointer:indices-len 0
    type-pointer:id id
    type-pointer:sequence 1
    type-pointer:flags *system-value
    system-value (+ 1 system-value))
  (status-require (db-read-name &system-value &type-pointer:name))
  (status-require (db-open-type-read-fields &system-value type-pointer))
  (set *result-type type-pointer)
  (label exit
    (return status)))

(define (db-open-types system records txn) (status-t MDB-cursor* MDB-cursor* db-txn-t)
  "load type info into cache. open all dbi.
   max type id size is currently 16 bit because of using an array to cache types
   instead of a slower hash table which would be needed otherwise.
   the type array has free space at the end for possible new types.
   type id zero is the system btree, only its sequence value is used in the cache"
  status-declare
  (declare
    val-key MDB-val
    val-data MDB-val
    key (array uint8-t (db-size-system-key))
    type-pointer db-type-t*
    types db-type-t*
    types-len db-type-id-t
    i db-type-id-t
    system-sequence db-type-id-t)
  (set types 0)
  (if (< db-size-type-id-max (sizeof db-type-id-t))
    (status-set-both-goto db-status-group-db db-status-id-max-type-id-size))
  (sc-comment "initialise system sequence, type id zero")
  (status-require (db-open-system-sequence system &system-sequence))
  (set
    types-len
    (+
      (if* (> db-env-types-extra-count (- db-type-id-limit system-sequence)) db-type-id-limit
        (+ system-sequence db-env-types-extra-count)))
    (db-system-key-label key) db-system-label-type
    (db-system-key-id key) 0
    val-key.mv-size db-size-system-key
    val-key.mv-data key)
  (status-require (db-helper-malloc (* types-len (sizeof db-type-t)) &types))
  (sc-comment "calloc doesnt necessarily set struct fields to zero")
  (for ((set i 0) (< i types-len) (set i (+ 1 i)))
    (set (struct-get (array-get types i) id) 0))
  (set
    (struct-get (array-get types 0) sequence) system-sequence
    status.id (mdb-cursor-get system &val-key &val-data MDB-SET-RANGE))
  (sc-comment "record type sequences")
  (while
    (and db-mdb-status-is-success (= db-system-label-type (db-system-key-label val-key.mv-data)))
    (status-require (db-open-type val-key.mv-data val-data.mv-data types records &type-pointer))
    (status-require (db-open-sequence records type-pointer))
    (set status.id (mdb-cursor-get system &val-key &val-data MDB-NEXT)))
  (if db-mdb-status-is-notfound (set status.id status-id-success)
    status-goto)
  (set
    txn.env:types types
    txn.env:types-len types-len)
  (label exit
    (if status-is-failure (db-free-env-types &types types-len))
    (return status)))

(define (db-open-indices system txn) (status-t MDB-cursor* db-txn-t)
  "extend type cache with index information. there can be multiple indices per type"
  status-declare
  db-mdb-declare-val-null
  (declare
    val-key MDB-val
    current-type-id db-type-id-t
    fields db-fields-len-t*
    fields-len db-fields-len-t
    indices db-index-t*
    indices-alloc-len db-fields-len-t
    indices-len db-fields-len-t
    key (array uint8-t (db-size-system-key))
    type-id db-type-id-t
    types db-type-t*
    types-len db-type-id-t)
  (set
    (db-system-key-label key) db-system-label-index
    (db-system-key-id key) 0
    indices 0
    fields 0
    current-type-id 0
    indices-len 0
    val-key.mv-size db-size-system-key
    val-key.mv-data key
    types txn.env:types
    types-len txn.env:types-len)
  (set status.id (mdb-cursor-get system &val-key &val-null MDB-SET-RANGE))
  (while
    (and db-mdb-status-is-success (= db-system-label-index (db-system-key-label val-key.mv-data)))
    (set type-id (db-system-key-id val-key.mv-data))
    (sc-comment "prepare indices array")
    (if (= current-type-id type-id)
      (begin
        (sc-comment "another index for the same type")
        (set indices-len (+ 1 indices-len))
        (if (> indices-len indices-alloc-len)
          (begin
            (set indices-alloc-len (+ 10 indices-alloc-len))
            (status-require (db-helper-realloc (* indices-alloc-len (sizeof db-index-t)) &indices)))))
      (begin
        (sc-comment "index for the first or a different type")
        (if indices-len
          (begin
            (sc-comment "not the first" "readjust size to save memory")
            (if (> indices-alloc-len indices-len)
              (status-require (db-helper-realloc (* indices-len (sizeof db-index-t)) &indices)))
            (sc-comment "add indices array to type")
            (set (struct-get (array-get types current-type-id) indices) indices)))
        (sc-comment "set current type and allocate indices array")
        (set
          current-type-id type-id
          indices-len 1
          indices-alloc-len 10)
        (status-require (db-helper-calloc (* indices-alloc-len (sizeof db-index-t)) &indices))))
    (set fields-len (/ (- val-key.mv-size db-size-system-key) (sizeof db-fields-len-t)))
    (status-require (db-helper-calloc (* fields-len (sizeof db-fields-len-t)) &fields))
    (struct-set (array-get indices (- indices-len 1))
      fields fields
      fields-len fields-len)
    (set status.id (mdb-cursor-get system &val-key &val-null MDB-NEXT)))
  (if db-mdb-status-is-notfound (set status.id status-id-success)
    status-goto)
  (if current-type-id (set (struct-get (array-get types current-type-id) indices) indices))
  (label exit
    (if status-is-failure (db-free-env-types-indices &indices indices-len))
    (return status)))

(define (db-open-system txn) (status-t db-txn-t)
  "ensure that the system tree exists with default values.
  check format and load cached values"
  status-declare
  (db-mdb-cursor-declare system)
  (db-mdb-cursor-declare records)
  (db-mdb-status-require (mdb-dbi-open txn.mdb-txn "system" MDB-CREATE &txn.env:dbi-system))
  (db-mdb-env-cursor-open txn system)
  (status-require (db-open-format system txn))
  (db-mdb-env-cursor-open txn records)
  (status-require (db-open-types system records txn))
  ;(status-require (db-open-indices system txn))
  (label exit
    (db-mdb-cursor-close-if-active system)
    (db-mdb-cursor-close-if-active records)
    (return status)))

(define (db-open-relation txn) (status-t db-txn-t)
  "ensure that the trees used for the relation exist, configure and open dbi"
  status-declare
  (declare
    db-options uint32-t
    dbi-relation-lr MDB-dbi
    dbi-relation-rl MDB-dbi
    dbi-relation-ll MDB-dbi)
  (set db-options (bit-or MDB-CREATE MDB-DUPSORT MDB-DUPFIXED))
  (db-mdb-status-require (mdb-dbi-open txn.mdb-txn "relation-lr" db-options &dbi-relation-lr))
  (db-mdb-status-require (mdb-dbi-open txn.mdb-txn "relation-rl" db-options &dbi-relation-rl))
  (db-mdb-status-require (mdb-dbi-open txn.mdb-txn "relation-ll" db-options &dbi-relation-ll))
  (db-mdb-status-require
    (mdb-set-compare
      txn.mdb-txn dbi-relation-lr (convert-type db-mdb-compare-relation-key MDB-cmp-func*)))
  (db-mdb-status-require
    (mdb-set-compare
      txn.mdb-txn dbi-relation-rl (convert-type db-mdb-compare-relation-key MDB-cmp-func*)))
  (db-mdb-status-require
    (mdb-set-compare txn.mdb-txn dbi-relation-ll (convert-type db-mdb-compare-id MDB-cmp-func*)))
  (db-mdb-status-require
    (mdb-set-dupsort
      txn.mdb-txn dbi-relation-lr (convert-type db-mdb-compare-relation-data MDB-cmp-func*)))
  (db-mdb-status-require
    (mdb-set-dupsort txn.mdb-txn dbi-relation-rl (convert-type db-mdb-compare-id MDB-cmp-func*)))
  (db-mdb-status-require
    (mdb-set-dupsort txn.mdb-txn dbi-relation-ll (convert-type db-mdb-compare-id MDB-cmp-func*)))
  (set
    txn.env:dbi-relation-lr dbi-relation-lr
    txn.env:dbi-relation-rl dbi-relation-rl
    txn.env:dbi-relation-ll dbi-relation-ll)
  (label exit
    (return status)))

(define (db-open-options-set-defaults a) (void db-open-options-t*)
  (set
    a:is-read-only #f
    a:maximum-size 17179869183
    a:maximum-reader-count 65535
    a:maximum-db-count 255
    a:env-open-flags 0
    a:filesystem-has-ordered-writes #t
    a:file-permissions 384))

(define (db-open-records txn) (status-t db-txn-t)
  status-declare
  (db-mdb-status-require (mdb-dbi-open txn.mdb-txn "records" MDB-CREATE &txn.env:dbi-records))
  (db-mdb-status-require
    (mdb-set-compare txn.mdb-txn txn.env:dbi-records (convert-type db-mdb-compare-id MDB-cmp-func*)))
  (label exit
    (return status)))

(define (db-open path options-pointer env) (status-t uint8-t* db-open-options-t* db-env-t*)
  status-declare
  (declare options db-open-options-t)
  (if (not (> (sizeof db-id-t) (sizeof db-type-id-t)))
    (status-set-both-goto db-status-group-db db-status-id-max-type-id-size))
  (db-txn-declare env txn)
  (if env:is-open (return status))
  (if (not path) (status-set-both-goto db-status-group-db db-status-id-missing-argument-db-root))
  (if options-pointer (set options *options-pointer)
    (db-open-options-set-defaults &options))
  (status-require (db-open-root env &options path))
  (status-require (db-open-mdb-env env &options))
  (status-require (db-txn-write-begin &txn))
  (status-require (db-open-records txn))
  (status-require (db-open-system txn))
  (status-require (db-open-relation txn))
  (status-require (db-txn-commit &txn))
  (pthread-mutex-init &env:mutex 0)
  (set env:is-open #t)
  (label exit
    (if status-is-failure
      (begin
        (db-txn-abort-if-active txn)
        (db-close env)))
    (return status)))