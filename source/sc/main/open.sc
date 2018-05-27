(sc-comment
  "system btree entry format. key -> value
     type-label id -> 8b:name-len name db-field-count-t:field-count (b8:field-type b8:name-len name) ...
     index-label db-type-id-t:type-id db-field-count-t:field-offset ... -> ()")

(define (db-open-root env options path) (status-t db-env-t* db-open-options-t* b8*)
  "prepare the database filesystem root path.
  create the full directory path if it does not exist"
  status-init
  (declare path-temp b8*)
  (set
    path-temp 0
    path-temp (string-clone path))
  (if (not path-temp) (db-status-set-id-goto db-status-id-memory))
  (if (not (ensure-directory-structure path-temp (bit-or 73 options:file-permissions)))
    (db-status-set-id-goto db-status-id-path-not-accessible-db-root))
  (set env:root path-temp)
  (label exit
    (if status-failure? (free path-temp))
    (return status)))

(define (db-open-mdb-env-flags options) (b32 db-open-options-t*)
  (return
    (if* options:env-open-flags options:env-open-flags
      (bit-or
        MDB-NOSUBDIR
        MDB-WRITEMAP
        (if* options:read-only? MDB-RDONLY
          0)
        (if* options:filesystem-has-ordered-writes? MDB-MAPASYNC
          0)))))

(define (db-open-mdb-env env options) (status-t db-env-t* db-open-options-t*)
  status-init
  (declare
    data-path b8*
    mdb-env MDB-env*)
  (set
    mdb-env 0
    data-path (string-append env:root "/data"))
  (if (not data-path) (db-status-set-id-goto db-status-id-memory))
  (db-mdb-status-require! (mdb-env-create &mdb-env))
  (db-mdb-status-require! (mdb-env-set-maxdbs mdb-env options:maximum-db-count))
  (db-mdb-status-require! (mdb-env-set-mapsize mdb-env options:maximum-size-octets))
  (db-mdb-status-require! (mdb-env-set-maxreaders mdb-env options:maximum-reader-count))
  (db-mdb-status-require!
    (mdb-env-open mdb-env data-path (db-open-mdb-env-flags options) options:file-permissions))
  (set env:mdb-env mdb-env)
  (label exit
    (free data-path)
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
    val-key.mv-data &label)
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
          (declare stat-info MDB-stat)
          (db-mdb-status-require! (mdb-stat txn.mdb-txn txn.env:dbi-system &stat-info))
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
      (sc-comment "no format entry exists yet")
      (set val-data.mv-data format)
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
  db-mdb-declare-val-null
  (db-mdb-declare-val val-key 1)
  (set
    current 0
    (db-system-key-label key) db-system-label-type
    (db-system-key-id key) db-type-id-limit
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
    (set (pointer-get result)
      (if* (= db-type-id-limit current) current
        (+ 1 current)))
    (return status)))

(define (db-type-first-id nodes type-id result) (status-t MDB-cursor* db-type-id-t db-id-t*)
  "get the first data id of type and save it in result. result is set to zero if none has been found"
  status-init
  db-mdb-declare-val-null
  db-mdb-declare-val-id
  (declare id db-id-t)
  (set
    (pointer-get result) 0
    id (db-id-add-type 0 type-id)
    val-id.mv-data &id)
  (db-mdb-cursor-get-norequire nodes val-id val-null MDB-SET-RANGE)
  (if db-mdb-status-success?
    (if (= type-id (db-id-type (db-mdb-val->id val-id)))
      (set (pointer-get result) (db-mdb-val->id val-id)))
    (if db-mdb-status-notfound? (status-set-id status-id-success)
      (status-set-group-goto db-status-group-lmdb)))
  (label exit
    (return status)))

(define (db-type-last-key-id nodes type-id result) (status-t MDB-cursor* db-type-id-t db-id-t*)
  "sets result to the last key id if the last key is of type, otherwise sets result to zero.
  leaves cursor at last key. status is mdb-notfound if database is empty"
  status-init
  db-mdb-declare-val-id
  db-mdb-declare-val-null
  (set (pointer-get result) 0)
  (db-mdb-cursor-get-norequire nodes val-id val-null MDB-LAST)
  (if (and db-mdb-status-success? (= type-id (db-id-type (db-mdb-val->id val-id))))
    (set (pointer-get result) (db-mdb-val->id val-id)))
  (return status))

(define (db-type-last-id nodes type-id result) (status-t MDB-cursor* db-type-id-t db-id-t*)
  "get the last existing node id for type or zero if none exist.
   algorithm: check if data of type exists, if yes then check if last key is of type or
   position next type and step back"
  status-init
  (declare id db-id-t)
  db-mdb-declare-val-null
  db-mdb-declare-val-id
  (sc-comment
    "if last key is of type then there are no greater type-ids and data of type exists.
    if there is no last key, the database is empty")
  (set status (db-type-last-key-id nodes type-id &id))
  (if db-mdb-status-success?
    (if id
      (begin
        (set *result id)
        (goto exit)))
    (if db-mdb-status-notfound?
      ; database is empty
      (begin
        (set *result 0)
        (status-set-id-goto status-id-success))
      status-goto))
  (sc-comment
    "database is not empty and the last key is not of searched type.
     type-id +1 is not greater than max possible type-id")
  (status-require! (db-type-first-id nodes (+ 1 type-id) &id))
  (if (not id)
    (begin
      (sc-comment
        "no greater type-id found. since the searched type is not the last,
         all existing type-ids are smaller")
      (set (pointer-get result) 0)
      (goto exit)))
  (sc-comment "greater type found, step back")
  (db-mdb-cursor-get nodes val-id val-null MDB-PREV)
  (set (pointer-get result)
    (if* (= type-id (db-id-type (db-mdb-val->id val-id))) (db-mdb-val->id val-id)
      0))
  (label exit
    (return status)))

(define (db-open-sequence nodes type) (status-t MDB-cursor* db-type-t*)
  "initialise the sequence for a type by searching the max used id for the type.
   lowest sequence value is 1.
   algorithm:
     check if any entry for type exists, then position at max or first next type key,
     take or step back to previous key"
  status-init
  (declare id db-id-t)
  (status-require! (db-type-last-id nodes type:id &id))
  (set
    id (db-id-element id)
    type:sequence
    (if* (< id db-element-id-limit) (+ 1 id)
      id))
  (label exit
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
    i db-field-count-t
    offset db-field-count-t)
  (set
    data (pointer-get data-pointer)
    fixed-offsets 0
    fixed-count 0
    fields 0
    count (pointer-get (convert-type data db-field-count-t*))
    data (+ (sizeof db-field-count-t) data))
  (db-calloc fields count (sizeof db-field-t))
  (sc-comment "field")
  (for ((set i 0) (< i count) (set i (+ 1 i)))
    (sc-comment "type")
    (set
      field-pointer (+ i fields)
      field-type (pointer-get data)
      data (+ (sizeof db-field-type-t) data)
      field-pointer:type field-type
      field-pointer:name-len (pointer-get (convert-type data db-name-len-t*)))
    (db-read-name &data (address-of (: field-pointer name)))
    (if (db-field-type-fixed? field-type) (set fixed-count (+ 1 fixed-count))))
  (sc-comment "offsets")
  (if fixed-count
    (begin
      (db-malloc fixed-offsets (* fixed-count (sizeof db-field-count-t)))
      (for ((set i 0) (< i fixed-count) (set i (+ 1 i)))
        (set
          offset (+ offset (db-field-type-size (: (+ i fields) type)))
          (pointer-get (+ i fixed-offsets)) offset))))
  (set
    type:fields fields
    type:fields-count count
    type:fields-fixed-count fixed-count
    type:fields-fixed-offsets fixed-offsets
    *data-pointer data)
  (label exit
    (if status-failure? (db-free-env-types-fields &fields count))
    (return status)))

(define (db-open-type system-key system-value types nodes)
  (status-t b8* b8* db-type-t* MDB-cursor*)
  status-init
  (declare
    id db-type-id-t
    type-pointer db-type-t*)
  (set
    id (db-system-key-id system-key)
    type-pointer (+ id types)
    type-pointer:id id)
  (status-require! (db-read-name &system-value &type-pointer:name))
  (status-require! (db-open-type-read-fields &system-value type-pointer))
  (status-require! (db-open-sequence nodes type-pointer))
  (label exit
    (return status)))

(define (db-open-types system nodes txn) (status-t MDB-cursor* MDB-cursor* db-txn-t)
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
  (if (< db-size-type-id-max (sizeof db-type-id-t))
    (status-set-both-goto db-status-group-db db-status-id-max-type-id-size))
  (sc-comment "initialise system sequence (type 0)")
  (status-require! (db-open-system-sequence system &system-sequence))
  (set
    types-len (- db-type-id-limit system-sequence)
    types-len
    (+
      system-sequence
      (if* (< db-env-types-extra-count types-len) db-env-types-extra-count
        types-len))
    (db-system-key-label key) db-system-label-type
    (db-system-key-id key) 0
    val-key.mv-data key)
  (db-calloc types types-len (sizeof db-type-t))
  (set types:sequence system-sequence)
  (sc-comment "node types")
  (db-mdb-cursor-get-norequire system val-key val-data MDB-SET-RANGE)
  (while
    (and db-mdb-status-success? (= db-system-label-type (db-system-key-label val-key.mv-data)))
    (status-require! (db-open-type val-key.mv-data val-data.mv-data types nodes))
    (db-mdb-cursor-get-norequire system val-key val-data MDB-NEXT))
  (if db-mdb-status-notfound? (status-set-id status-id-success)
    status-goto)
  (set
    txn.env:types types
    txn.env:types-len types-len)
  (label exit
    (if status-failure? (db-free-env-types &types types-len))
    (return status)))

(define (db-open-indices system txn) (status-t MDB-cursor* db-txn-t)
  "extend type cache with index information. there can be multiple indices per type"
  status-init
  db-mdb-declare-val-null
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
    types txn.env:types
    types-len txn.env:types-len)
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
            (set (: (+ current-type-id types) indices) indices)))
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
    (struct-pointer-set (+ (- indices-count 1) indices)
      fields fields
      fields-count fields-count)
    (db-mdb-cursor-get-norequire system val-key val-null MDB-NEXT))
  (if db-mdb-status-notfound? (status-set-id status-id-success)
    status-goto)
  (if current-type-id (set (: (+ current-type-id types) indices) indices))
  (label exit
    (if status-failure? (db-free-env-types-indices &indices indices-count))
    (return status)))

(define (db-open-system txn) (status-t db-txn-t)
  "ensure that the system tree exists with default values.
  check format and load cached values"
  status-init
  (db-mdb-cursor-declare system)
  (db-mdb-cursor-declare nodes)
  (db-mdb-status-require! (mdb-dbi-open txn.mdb-txn "system" MDB-CREATE &txn.env:dbi-system))
  (db-cursor-open txn system)
  (status-require! (db-open-format system txn))
  (db-cursor-open txn nodes)
  (status-require! (db-open-types system nodes txn))
  (status-require! (db-open-indices system txn))
  (label exit
    (db-cursor-close-if-active system)
    (db-cursor-close-if-active nodes)
    (return status)))

(define (db-open-graph txn) (status-t db-txn-t)
  "ensure that the trees used for the graph exist, configure and open dbi"
  status-init
  (declare
    db-options b32
    dbi-graph-lr MDB-dbi
    dbi-graph-rl MDB-dbi
    dbi-graph-ll MDB-dbi)
  (set db-options (bit-or MDB-CREATE MDB-DUPSORT MDB-DUPFIXED))
  (db-mdb-status-require! (mdb-dbi-open txn.mdb-txn "graph-lr" db-options &dbi-graph-lr))
  (db-mdb-status-require! (mdb-dbi-open txn.mdb-txn "graph-rl" db-options &dbi-graph-rl))
  (db-mdb-status-require! (mdb-dbi-open txn.mdb-txn "graph-ll" db-options &dbi-graph-ll))
  (db-mdb-status-require!
    (mdb-set-compare txn.mdb-txn dbi-graph-lr (convert-type db-mdb-compare-graph-key MDB-cmp-func*)))
  (db-mdb-status-require!
    (mdb-set-compare txn.mdb-txn dbi-graph-rl (convert-type db-mdb-compare-graph-key MDB-cmp-func*)))
  (db-mdb-status-require!
    (mdb-set-compare txn.mdb-txn dbi-graph-ll (convert-type db-mdb-compare-id MDB-cmp-func*)))
  (db-mdb-status-require!
    (mdb-set-dupsort
      txn.mdb-txn dbi-graph-lr (convert-type db-mdb-compare-graph-data MDB-cmp-func*)))
  (db-mdb-status-require!
    (mdb-set-dupsort txn.mdb-txn dbi-graph-rl (convert-type db-mdb-compare-id MDB-cmp-func*)))
  (db-mdb-status-require!
    (mdb-set-dupsort txn.mdb-txn dbi-graph-ll (convert-type db-mdb-compare-id MDB-cmp-func*)))
  (set
    txn.env:dbi-graph-lr dbi-graph-lr
    txn.env:dbi-graph-rl dbi-graph-rl
    txn.env:dbi-graph-ll dbi-graph-ll)
  (label exit
    (return status)))

(define (db-open-options-set-defaults a) (db-open-options-t db-open-options-t*)
  (set
    a:read-only? #f
    a:maximum-size-octets 17179869183
    a:maximum-reader-count 65535
    a:maximum-db-count 255
    a:env-open-flags 0
    a:filesystem-has-ordered-writes? #t
    a:file-permissions 384))

(define (db-open-nodes txn) (status-t db-txn-t)
  status-init
  (db-mdb-status-require! (mdb-dbi-open txn.mdb-txn "nodes" MDB-CREATE &txn.env:dbi-nodes))
  (db-mdb-status-require!
    (mdb-set-compare txn.mdb-txn txn.env:dbi-nodes (convert-type db-mdb-compare-id MDB-cmp-func*)))
  (label exit
    (return status)))

(define (db-open path options-pointer env) (status-t b8* db-open-options-t* db-env-t*)
  status-init
  (declare options db-open-options-t)
  (if (not (> db-size-id db-size-type-id))
    (status-set-both-goto db-status-group-db db-status-id-max-type-id-size))
  (db-txn-declare env txn)
  (if env:open (return status))
  (if (not path) (db-status-set-id-goto db-status-id-missing-argument-db-root))
  (if options-pointer (set options *options-pointer)
    (db-open-options-set-defaults &options))
  (status-require! (db-open-root env &options path))
  (status-require! (db-open-mdb-env env &options))
  (db-txn-write-begin txn)
  (status-require! (db-open-nodes txn))
  (status-require! (db-open-system txn))
  (status-require! (db-open-graph txn))
  (db-txn-commit txn)
  (pthread-mutex-init &env:mutex 0)
  (set env:open #t)
  (label exit
    (if status-failure?
      (begin
        (db-txn-abort-if-active txn)
        (db-close env)))
    (return status)))