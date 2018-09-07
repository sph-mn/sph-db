(pre-include "./sph-db.h" "./sph-db-extra.h" "../foreign/sph/one.c" "math.h" "./lib/lmdb.c")

(pre-define
  (free-and-set-null a)
  (begin
    (free a)
    (set a 0))
  (db-error-log pattern ...)
  (fprintf stderr (pre-string-concat "%s:%d error: " pattern "\n") __func__ __LINE__ __VA_ARGS__)
  reduce-count (set count (- count 1))
  stop-if-count-zero (if (= 0 count) (goto exit))
  db-size-system-key (+ 1 (sizeof db-type-id-t)))

(define (uint->string a result-len) (uint8-t* uintmax-t size-t*)
  (declare
    size size-t
    result uint8-t*)
  (set
    size
    (+ 1
      (if* (= 0 a) 1
        (+ 1 (log10 a))))
    result (malloc size))
  (if (not result) (return 0))
  (if (< (snprintf result size "%ju" a) 0)
    (begin
      (free result)
      (return 0))
    (begin
      (set *result-len (- size 1))
      (return result))))

(define (string-join strings strings-len delimiter result-len)
  (uint8-t* uint8-t** size-t uint8-t* size-t*)
  "join strings into one string with each input string separated by delimiter.
  zero if strings-len is zero or memory could not be allocated"
  (declare
    result uint8-t*
    result-temp uint8-t*
    size size-t
    size-temp size-t
    i size-t
    delimiter-len size-t)
  (if (not strings-len) (return 0))
  (sc-comment "size: string-null + delimiters + string-lengths")
  (set
    delimiter-len (strlen delimiter)
    size (+ 1 (* delimiter-len (- strings-len 1))))
  (for ((set i 0) (< i strings-len) (set i (+ 1 i)))
    (set size (+ size (strlen (array-get strings i)))))
  (set result (malloc size))
  (if (not result) (return 0))
  (set
    result-temp result
    size-temp (strlen (array-get strings 0)))
  (memcpy result-temp (array-get strings 0) size-temp)
  (set result-temp (+ size-temp result-temp))
  (for ((set i 1) (< i strings-len) (set i (+ 1 i)))
    (memcpy result-temp delimiter delimiter-len)
    (set
      result-temp (+ delimiter-len result-temp)
      size-temp (strlen (array-get strings i)))
    (memcpy result-temp (array-get strings i) size-temp)
    (set result-temp (+ size-temp result-temp)))
  (set
    (array-get result (- size 1)) 0
    *result-len (- size 1))
  (return result))

(define (db-status-group-id->name a) (uint8-t* status-id-t)
  (declare b char*)
  (case = a
    (db-status-group-db (set b "sph-db"))
    (db-status-group-lmdb (set b "lmdb"))
    (db-status-group-libc (set b "libc"))
    (else (set b "")))
  (return b))

(define (db-status-description a) (uint8-t* status-t)
  "get the description if available for a status"
  (declare b char*)
  (case = a.group
    (db-status-group-lmdb (set b (mdb-strerror a.id)))
    (else
      (case = a.id
        (db-status-id-success (set b "success"))
        (db-status-id-invalid-argument (set b "input argument is of wrong type"))
        (db-status-id-input-type (set b "input argument is of wrong type"))
        (db-status-id-data-length (set b "data too large"))
        (db-status-id-duplicate (set b "element already exists"))
        (db-status-id-not-implemented (set b "not implemented"))
        (db-status-id-missing-argument-db-root (set b "missing argument 'db-root'"))
        (db-status-id-path-not-accessible-db-root (set b "root not accessible"))
        (db-status-id-memory (set b "not enough memory or other memory allocation error"))
        (db-status-id-max-element-id
          (set b "maximum element identifier value has been reached for the type"))
        (db-status-id-max-type-id (set b "maximum type identifier value has been reached"))
        (db-status-id-max-type-id-size
          (set b
            "type identifier size is either configured to be greater than 16 bit, which is currently not supported, or is not smaller than record id size"))
        (db-status-id-condition-unfulfilled (set b "condition unfulfilled"))
        (db-status-id-notfound (set b "no more data to read"))
        (db-status-id-different-format
          (set b "configured format differs from the format the database was created with"))
        (db-status-id-index-keysize (set b "index key to be inserted exceeds mdb maxkeysize"))
        (db-status-id-type-field-order
          (set b "all fixed length type fields must come before variable length type fields"))
        (else (set b "")))))
  (return (convert-type b uint8-t*)))

(define (db-status-name a) (uint8-t* status-t)
  "get the name if available for a status"
  (declare b char*)
  (case = a.group
    (db-status-group-lmdb (set b (mdb-strerror a.id)))
    (else
      (case = a.id
        (db-status-id-success (set b "success"))
        (db-status-id-invalid-argument (set b "invalid-argument"))
        (db-status-id-input-type (set b "input-type"))
        (db-status-id-data-length (set b "data-length"))
        (db-status-id-duplicate (set b "duplicate"))
        (db-status-id-not-implemented (set b "not-implemented"))
        (db-status-id-missing-argument-db-root (set b "missing-argument-db-root"))
        (db-status-id-path-not-accessible-db-root (set b "path-not-accessible-db-root"))
        (db-status-id-memory (set b "memory"))
        (db-status-id-max-element-id (set b "max-element-id-reached"))
        (db-status-id-max-type-id (set b "max-type-id-reached"))
        (db-status-id-max-type-id-size (set b "type-id-size-too-big"))
        (db-status-id-condition-unfulfilled (set b "condition-unfulfilled"))
        (db-status-id-notfound (set b "notfound"))
        (db-status-id-different-format (set b "differing-db-format"))
        (db-status-id-index-keysize (set b "index-key-mdb-keysize"))
        (db-status-id-type-field-order (set b "type-field-order"))
        (else (set b "unknown")))))
  (return (convert-type b uint8-t*)))

(define (db-txn-begin a) (status-t db-txn-t*)
  status-declare
  (db-mdb-status-require (mdb-txn-begin a:env:mdb-env 0 MDB-RDONLY &a:mdb-txn))
  (label exit
    (return status)))

(define (db-txn-write-begin a) (status-t db-txn-t*)
  status-declare
  (db-mdb-status-require (mdb-txn-begin a:env:mdb-env 0 0 &a:mdb-txn))
  (label exit
    (return status)))

(define (db-txn-begin-child parent-txn a) (status-t db-txn-t db-txn-t*)
  status-declare
  (db-mdb-status-require (mdb-txn-begin a:env:mdb-env parent-txn.mdb-txn MDB-RDONLY &a:mdb-txn))
  (label exit
    (return status)))

(define (db-txn-write-begin-child parent-txn a) (status-t db-txn-t db-txn-t*)
  status-declare
  (db-mdb-status-require (mdb-txn-begin a:env:mdb-env parent-txn.mdb-txn 0 &a:mdb-txn))
  (label exit
    (return status)))

(define (db-txn-abort a) (void db-txn-t*)
  (mdb-txn-abort a:mdb-txn)
  (set a:mdb-txn 0))

(define (db-txn-commit a) (status-t db-txn-t*)
  status-declare
  (db-mdb-status-require (mdb-txn-commit a:mdb-txn))
  (set a:mdb-txn 0)
  (label exit
    (return status)))

(define (db-debug-log-id-bits a) (void db-id-t)
  (declare index db-id-t)
  (printf "%u" (bit-and 1 a))
  (for ((set index 1) (< index (* 8 (sizeof db-id-t))) (set index (+ 1 index)))
    (printf "%u"
      (if* (bit-and (bit-shift-left (convert-type 1 db-id-t) index) a) 1
        0)))
  (printf "\n"))

(define (db-debug-log-ids a) (void db-ids-t)
  "display an ids array"
  (printf "ids (%lu):" (i-array-length a))
  (while (i-array-in-range a)
    (printf " %lu" (i-array-get a))
    (i-array-forward a))
  (printf "\n"))

(define (db-debug-log-ids-set a) (void imht-set-t)
  "display an ids set"
  (define i uint32-t 0)
  (printf "id set (%lu):" a.size)
  (while (< i a.size)
    (printf " %lu" (array-get a.content i))
    (set i (+ 1 i)))
  (printf "\n"))

(define (db-debug-log-relations a) (void db-relations-t)
  (declare b db-relation-t)
  (printf "relation records (ll -> or)\n")
  (while (i-array-in-range a)
    (set b (i-array-get a))
    (printf "  %lu %lu -> %lu %lu\n" b.left b.label b.ordinal b.right)
    (i-array-forward a)))

(define (db-debug-log-btree-counts txn) (status-t db-txn-t)
  status-declare
  (declare stat db-statistics-t)
  (status-require (db-statistics txn &stat))
  (printf
    "btree entry count: system %zu, records %zu, relation-lr %zu, relation-rl %zu, relation-ll %zu\n"
    stat.system.ms_entries
    stat.records.ms_entries
    stat.relation-lr.ms_entries stat.relation-rl.ms_entries stat.relation-ll.ms_entries)
  (label exit
    (return status)))

(define (db-debug-count-all-btree-entries txn result) (status-t db-txn-t uint32-t*)
  "sum the count of all entries in all btrees used by the database"
  status-declare
  (declare stat db-statistics-t)
  (status-require (db-statistics txn &stat))
  (set *result
    (+
      stat.system.ms_entries
      stat.records.ms_entries
      stat.relation-lr.ms_entries stat.relation-rl.ms_entries stat.relation-ll.ms_entries))
  (label exit
    (return status)))

(define (db-field-type-size a) (uint8-t uint8-t)
  "size in octets. zero for variable size types"
  (case = a
    ( (db-field-type-int64 db-field-type-uint64 db-field-type-string64 db-field-type-float64)
      (return 8))
    ( (db-field-type-int32 db-field-type-uint32 db-field-type-string32 db-field-type-float32)
      (return 4))
    ((db-field-type-int16 db-field-type-uint16 db-field-type-string16) (return 2))
    ((db-field-type-int8 db-field-type-uint8 db-field-type-string8) (return 1))
    (else (return 0))))

(define (db-record-virtual-from-any type-id data data-size) (db-id-t db-type-id-t void* uint8-t)
  "create a virtual record with data of any type equal or smaller in size than db-size-id-element"
  (declare id db-id-t)
  (memcpy &id data data-size)
  (return (db-id-add-type id type-id)))

(define (db-ids->set a result) (status-t db-ids-t imht-set-t**)
  status-declare
  (db-status-memory-error-if-null (imht-set-create (i-array-length a) result))
  (while (i-array-in-range a)
    (imht-set-add *result (i-array-get a))
    (i-array-forward a))
  (label exit
    (return status)))

(define (db-read-name data-pointer result) (status-t uint8-t** uint8-t**)
  "read a length prefixed string.
  on success set result to a newly allocated string and data to the next byte after the string"
  status-declare
  (declare
    data uint8-t*
    len db-name-len-t
    name uint8-t*)
  (set
    data *data-pointer
    len (pointer-get (convert-type data db-name-len-t*))
    data (+ (sizeof db-name-len-t) data))
  (db-malloc-string name len)
  (memcpy name data len)
  (label exit
    (set
      *data-pointer (+ len data)
      *result name)
    (return status)))

(pre-define (db-define-i-array-new name type)
  (define (name length result) (status-t size-t type*)
    "like i-array-allocate-* but returns status-t"
    status-declare
    (if (not ((pre-concat i-array-allocate_ type) length result))
      (set
        status.id db-status-id-memory
        status.group db-status-group-db))
    (return status)))

(db-define-i-array-new db-ids-new db-ids-t)
(db-define-i-array-new db-records-new db-records-t)
(db-define-i-array-new db-relations-new db-relations-t)

(define (db-records->ids records result-ids) (void db-records-t db-ids-t*)
  "copies to a db-ids-t array all ids from a db-records-t array. result-ids is allocated by the caller"
  (while (i-array-in-range records)
    (i-array-add *result-ids (struct-get (i-array-get records) id))
    (i-array-forward records)))

(define (db-statistics txn result) (status-t db-txn-t db-statistics-t*)
  "expects an allocated db-statistics-t"
  status-declare
  (db-mdb-status-require (mdb-stat txn.mdb-txn txn.env:dbi-system &result:system))
  (db-mdb-status-require (mdb-stat txn.mdb-txn txn.env:dbi-records &result:records))
  (db-mdb-status-require (mdb-stat txn.mdb-txn txn.env:dbi-relation-lr &result:relation-lr))
  (db-mdb-status-require (mdb-stat txn.mdb-txn txn.env:dbi-relation-ll &result:relation-ll))
  (db-mdb-status-require (mdb-stat txn.mdb-txn txn.env:dbi-relation-rl &result:relation-rl))
  (label exit
    (return status)))

(define (db-sequence-next-system env result) (status-t db-env-t* db-type-id-t*)
  "return one new unique type identifier.
  the maximum identifier returned is db-type-id-limit minus one"
  status-declare
  (declare sequence db-type-id-t)
  (pthread-mutex-lock &env:mutex)
  (set sequence (convert-type env:types:sequence db-type-id-t))
  (if (> db-type-id-limit sequence)
    (begin
      (set env:types:sequence (+ 1 sequence))
      (pthread-mutex-unlock &env:mutex)
      (set *result sequence))
    (begin
      (pthread-mutex-unlock &env:mutex)
      (status-set-both-goto db-status-group-db db-status-id-max-type-id)))
  (label exit
    (return status)))

(define (db-sequence-next env type-id result) (status-t db-env-t* db-type-id-t db-id-t*)
  "return one new unique type record identifier.
  the maximum identifier returned is db-id-limit minus one"
  status-declare
  (declare sequence db-id-t)
  (pthread-mutex-lock &env:mutex)
  (set sequence (: (+ type-id env:types) sequence))
  (if (> db-element-id-limit sequence)
    (begin
      (set (: (+ type-id env:types) sequence) (+ 1 sequence))
      (pthread-mutex-unlock &env:mutex)
      (set *result (db-id-add-type sequence type-id)))
    (begin
      (pthread-mutex-unlock &env:mutex)
      (status-set-both-goto db-status-group-db db-status-id-max-element-id)))
  (label exit
    (return status)))

(define (db-free-env-types-indices indices indices-len) (void db-index-t** db-fields-len-t)
  (declare
    i db-fields-len-t
    index-pointer db-index-t*)
  (if (not *indices) return)
  (for ((set i 0) (< i indices-len) (set i (+ 1 i)))
    (set index-pointer (+ i *indices))
    (free-and-set-null index-pointer:fields))
  (free-and-set-null *indices))

(define (db-free-env-types-fields fields fields-len) (void db-field-t** db-fields-len-t)
  (declare i db-fields-len-t)
  (if (not *fields) return)
  (for ((set i 0) (< i fields-len) (set i (+ 1 i)))
    (free-and-set-null (: (+ i *fields) name)))
  (free-and-set-null *fields))

(define (db-free-env-type type) (void db-type-t*)
  (if (= 0 type:id) return)
  (free-and-set-null type:fields-fixed-offsets)
  (db-free-env-types-fields &type:fields type:fields-len)
  (db-free-env-types-indices &type:indices type:indices-len)
  (set type:id 0))

(define (db-free-env-types types types-len) (void db-type-t** db-type-id-t)
  (declare i db-type-id-t)
  (if (not *types) return)
  (for ((set i 0) (< i types-len) (set i (+ 1 i)))
    (db-free-env-type (+ i *types)))
  (free-and-set-null *types))

(define (db-env-new result) (status-t db-env-t**)
  "caller has to free result when not needed anymore.
  this routine makes sure that .is-open is zero"
  status-declare
  (declare a db-env-t*)
  (db-calloc a 1 (sizeof db-env-t))
  (set *result a)
  (label exit
    (return status)))

(define (db-close env) (void db-env-t*)
  (define mdb-env MDB-env* env:mdb-env)
  (if mdb-env
    (begin
      (mdb-dbi-close mdb-env env:dbi-system)
      (mdb-dbi-close mdb-env env:dbi-records)
      (mdb-dbi-close mdb-env env:dbi-relation-lr)
      (mdb-dbi-close mdb-env env:dbi-relation-rl)
      (mdb-dbi-close mdb-env env:dbi-relation-ll)
      (mdb-env-close mdb-env)
      (set env:mdb-env 0)))
  (db-free-env-types &env:types env:types-len)
  (if env:root (free-and-set-null env:root))
  (set env:is-open #f)
  (pthread-mutex-destroy &env:mutex))

(pre-include "./open.c" "./type.c" "./index.c" "./record.c" "./relation.c")