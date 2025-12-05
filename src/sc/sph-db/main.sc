(pre-include "math.h" "./sph-db.h"
  "stdio.h" "sph-db/sph/memory.c" "sph-db/sph/string.h"
  "sph-db/sph/filesystem.h" "./sph-db-extra.h" "./lmdb.c")

(pre-define
  (free-and-set-null a) (begin (free a) (set a 0))
  (db-error-log pattern ...)
  (fprintf stderr (pre-concat-string "%s:%d error: " pattern "\n") __func__ __LINE__ __VA_ARGS__)
  reduce-count (set count (- count 1))
  stop-if-count-zero (if (= 0 count) (goto exit)))

(define (db-status-description a) (char* status-t)
  "get the description if available for a status"
  (declare b char*)
  (cond
    ((not (strcmp db-status-group-lmdb a.group)) (set b (mdb-strerror a.id)))
    ( (not (strcmp db-status-group-db a.group))
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
        (db-status-id-notfound (set b "entry not found or no more data to read"))
        (db-status-id-different-format
          (set b "configured format differs from the format the database was created with"))
        (db-status-id-index-keysize (set b "index key to be inserted exceeds mdb maxkeysize"))
        (db-status-id-invalid-field-type (set b "invalid type for field"))
        (db-status-id-type-field-order
          (set b "all fixed length type fields must come before variable length type fields"))
        (else (set b ""))))
    (else (if (= status-id-success a.id) (set b "success") (set b ""))))
  (return b))

(define (db-status-name a) (char* status-t)
  "get the name if available for a status"
  (declare b char*)
  (cond
    ((not (strcmp db-status-group-lmdb a.group)) (set b (mdb-strerror a.id)))
    ( (not (strcmp db-status-group-db a.group))
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
        (db-status-id-invalid-field-type (set b "invalid-field-type"))
        (db-status-id-type-field-order (set b "type-field-order"))
        (else (set b "unknown"))))
    (else (set b "unknown")))
  (return b))

(define (db-txn-begin a) (status-t db-txn-t*)
  status-declare
  (db-mdb-status-require (mdb-txn-begin a:env:mdb-env 0 MDB-RDONLY &a:mdb-txn))
  (label exit status-return))

(define (db-txn-write-begin a) (status-t db-txn-t*)
  status-declare
  (db-mdb-status-require (mdb-txn-begin a:env:mdb-env 0 0 &a:mdb-txn))
  (label exit status-return))

(define (db-txn-begin-child parent-txn a) (status-t db-txn-t db-txn-t*)
  status-declare
  (db-mdb-status-require (mdb-txn-begin a:env:mdb-env parent-txn.mdb-txn MDB-RDONLY &a:mdb-txn))
  (label exit status-return))

(define (db-txn-write-begin-child parent-txn a) (status-t db-txn-t db-txn-t*)
  status-declare
  (db-mdb-status-require (mdb-txn-begin a:env:mdb-env parent-txn.mdb-txn 0 &a:mdb-txn))
  (label exit status-return))

(define (db-txn-abort a) (void db-txn-t*) (mdb-txn-abort a:mdb-txn) (set a:mdb-txn 0))

(define (db-txn-commit a) (status-t db-txn-t*)
  status-declare
  (db-mdb-status-require (mdb-txn-commit a:mdb-txn))
  (set a:mdb-txn 0)
  (label exit status-return))

(define (db-debug-log-id-bits a) (void db-id-t)
  (declare index db-id-t)
  (printf db-id-printf-format (bit-and 1 a))
  (for ((set index 1) (< index (* 8 (sizeof db-id-t))) (set+ index 1))
    (printf (pre-concat-string "%" PRIu8)
      (if* (bit-and (bit-shift-left (convert-type 1 db-id-t) index) a) 1 0)))
  (printf "\n"))

(define (db-debug-log-ids a) (void db-ids-t)
  "display an ids array"
  (printf "ids (%zu):" a.used)
  (while (sph-array-current-in-range a)
    (printf (pre-concat-string "  " db-id-printf-format) (sph-array-current-get a))
    (sph-array-current-forward a))
  (printf "\n"))

(define (db-debug-log-ids-set a) (void db-id-set-t)
  "display an ids set"
  (printf "id set (%zu):" a.size)
  (for-each-index i uint32-t
    a.size
    (if (array-get a.values i)
      (printf (pre-concat-string "  " db-id-printf-format) (array-get a.values i))))
  (printf "\n"))

(define (db-debug-log-relations a) (void db-relations-t)
  (declare b db-relation-t)
  (printf "relation records (ll -> or)\n")
  (while (sph-array-current-in-range a)
    (set b (sph-array-current-get a))
    (printf
      (pre-concat-string "  " db-id-printf-format
        db-id-printf-format " -> " db-ordinal-printf-format db-id-printf-format "\n")
      b.left b.label b.ordinal b.right)
    (sph-array-current-forward a)))

(define (db-debug-log-btree-counts txn) (status-t db-txn-t)
  status-declare
  (declare stat db-statistics-t)
  (status-require (db-statistics txn &stat))
  (printf
    "btree entry count: system %zu, records %zu, relation-lr %zu, relation-rl %zu, relation-ll %zu\n"
    stat.system.ms_entries stat.records.ms_entries stat.relation-lr.ms_entries
    stat.relation-rl.ms_entries stat.relation-ll.ms_entries)
  (label exit status-return))

(define (db-debug-count-all-btree-entries txn result) (status-t db-txn-t size-t*)
  "sum of all entries in all btrees used by the database"
  status-declare
  (declare stat db-statistics-t)
  (status-require (db-statistics txn &stat))
  (set *result
    (+ stat.system.ms_entries stat.records.ms_entries
      stat.relation-lr.ms_entries stat.relation-rl.ms_entries stat.relation-ll.ms_entries))
  (label exit status-return))

(define (db-field-type-size a) (db-field-type-size-t db-field-type-t)
  "size in octets. size of the size prefix for variable size types"
  (case = a
    ( (db-field-type-binary64f db-field-type-uint64f db-field-type-int64f
        db-field-type-string64f db-field-type-float64f db-field-type-binary64 db-field-type-string64)
      (return 8))
    ( (db-field-type-binary32f db-field-type-uint32f db-field-type-int32f
        db-field-type-string32f db-field-type-float32f db-field-type-binary32 db-field-type-string32)
      (return 4))
    ( (db-field-type-binary16f db-field-type-uint16f db-field-type-int16f
        db-field-type-string16f db-field-type-binary16 db-field-type-string16)
      (return 2))
    ( (db-field-type-binary8f db-field-type-uint8f db-field-type-int8f
        db-field-type-string8f db-field-type-binary8 db-field-type-string8)
      (return 1))
    ( (db-field-type-binary128f db-field-type-uint128f db-field-type-int128f
        db-field-type-string128f)
      (return 16))
    ( (db-field-type-binary256f db-field-type-uint256f db-field-type-int256f
        db-field-type-string256f)
      (return 32))
    (else (return 0))))

(define (db-record-virtual type-id data data-size) (db-id-t db-type-id-t void* size-t)
  (declare id db-id-t)
  (set id 0)
  (memcpy &id data data-size)
  (return (db-id-add-type id type-id)))

(define (db-record-virtual-data id result result-size) (void* db-id-t void* size-t)
  "result is allocated and owned by callee"
  (set id (db-id-element id))
  (memcpy result &id result-size)
  (return result))

(define (db-ids->set a result) (status-t db-ids-t db-id-set-t*)
  status-declare
  (declare b db-id-set-t)
  (if (db-id-set-new (db-ids-length a) &b) (status-set-goto db-status-group-db db-status-id-memory))
  (while (sph-array-current-in-range a)
    (db-id-set-add &b (sph-array-current-get a))
    (sph-array-current-forward a))
  (set *result b)
  (label exit status-return))

(define (db-read-name data-pointer result) (status-t uint8-t** char**)
  "read a length prefixed string.
   on success set result to a newly allocated, null terminated string and
   data-pointer is positioned at the first byte after the string"
  status-declare
  (declare data uint8-t* len db-name-len-t name char*)
  (set
    data *data-pointer
    len (pointer-get (convert-type data db-name-len-t*))
    data (+ (sizeof db-name-len-t) data))
  (status-require (sph-memory-malloc-string len &name))
  (memcpy name data len)
  (set *data-pointer (+ len data) *result name)
  (label exit status-return))

(define (db-records->ids records result-ids) (void db-records-t db-ids-t*)
  "copies to a db-ids-t array all ids from a db-records-t array. result-ids is allocated by the caller"
  (while (sph-array-current-in-range records)
    (sph-array-add *result-ids (struct-get (sph-array-current-get records) id))
    (sph-array-current-forward records)))

(define (db-statistics txn result) (status-t db-txn-t db-statistics-t*)
  "expects an allocated db-statistics-t"
  status-declare
  (db-mdb-status-require (mdb-stat txn.mdb-txn txn.env:dbi-system &result:system))
  (db-mdb-status-require (mdb-stat txn.mdb-txn txn.env:dbi-records &result:records))
  (db-mdb-status-require (mdb-stat txn.mdb-txn txn.env:dbi-relation-lr &result:relation-lr))
  (db-mdb-status-require (mdb-stat txn.mdb-txn txn.env:dbi-relation-ll &result:relation-ll))
  (db-mdb-status-require (mdb-stat txn.mdb-txn txn.env:dbi-relation-rl &result:relation-rl))
  (label exit status-return))

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
      (status-set-goto db-status-group-db db-status-id-max-type-id)))
  (label exit status-return))

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
      (status-set-goto db-status-group-db db-status-id-max-element-id)))
  (label exit status-return))

(define (db-free-env-types-indices indices indices-len) (void db-index-t** db-fields-len-t)
  (declare i db-fields-len-t index-pointer db-index-t*)
  (if (not *indices) return)
  (for ((set i 0) (< i indices-len) (set i (+ 1 i)))
    (set index-pointer (+ i *indices))
    (free-and-set-null index-pointer:fields))
  (free-and-set-null *indices))

(define (db-free-env-types-fields fields fields-len) (void db-field-t** db-fields-len-t)
  (declare i db-fields-len-t)
  (if (not *fields) return)
  (for ((set i 0) (< i fields-len) (set i (+ 1 i))) (free-and-set-null (: (+ i *fields) name)))
  (free-and-set-null *fields))

(define (db-free-env-type type) (void db-type-t*)
  (if (not type:id) return)
  (free-and-set-null type:fields-fixed-offsets)
  (db-free-env-types-fields &type:fields type:fields-len)
  (db-free-env-types-indices &type:indices type:indices-len)
  (set type:id 0))

(define (db-free-env-types types types-len) (void db-type-t** db-type-id-t)
  (declare i db-type-id-t)
  (if (not *types) return)
  (for ((set i 0) (< i types-len) (set i (+ 1 i))) (db-free-env-type (+ i *types)))
  (free-and-set-null *types))

(define (db-env-new result) (status-t db-env-t**)
  "caller has to free result when not needed anymore.
   this routine makes sure that .is-open is zero"
  status-declare
  (declare a db-env-t*)
  (status-require (sph-memory-calloc (sizeof db-env-t) (convert-type &a void**)))
  (set *result a)
  (label exit status-return))

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
