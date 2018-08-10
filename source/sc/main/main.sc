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
  (optional-count count)
  (if* (= 0 count) UINT32_MAX
    count)
  db-size-system-key (+ 1 (sizeof db-type-id-t)))

(define (uint->string a result-len) (ui8* uintmax-t size-t*)
  (declare
    size size-t
    result ui8*)
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

(define (string-join strings strings-len delimiter result-len) (ui8* ui8** size-t ui8* size-t*)
  "join strings into one string with each input string separated by delimiter.
  zero if strings-len is zero or memory could not be allocated"
  (declare
    result ui8*
    result-temp ui8*
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

(define (db-txn-abort a) (void db-txn-t*)
  (mdb-txn-abort a:mdb-txn)
  (set a:mdb-txn 0))

(define (db-txn-commit a) (status-t db-txn-t*)
  status-declare
  (db-mdb-status-require (mdb-txn-commit a:mdb-txn))
  (set a:mdb-txn 0)
  (label exit
    (return status)))

(define (db-debug-log-ids a) (void db-ids-t)
  "display an ids array"
  (printf "ids (%lu):" (i-array-length a))
  (while (i-array-in-range a)
    (printf " %lu" (i-array-get a))
    (i-array-forward a))
  (printf "\n"))

(define (db-debug-log-ids-set a) (void imht-set-t)
  "display an ids set"
  (define i ui32 0)
  (printf "id set (%lu):" a.size)
  (while (< i a.size)
    (printf " %lu" (array-get a.content i))
    (set i (+ 1 i)))
  (printf "\n"))

(define (db-debug-log-graph-records a) (void db-graph-records-t)
  (declare b db-graph-record-t)
  (printf "graph records (ll -> or)\n")
  (while (i-array-in-range a)
    (set b (i-array-get a))
    (printf "  %lu %lu -> %lu %lu\n" b.left b.label b.ordinal b.right)
    (i-array-forward a)))

(define (db-debug-log-btree-counts txn) (status-t db-txn-t)
  status-declare
  (declare stat db-statistics-t)
  (status-require (db-statistics txn &stat))
  (printf
    "btree entry count: system %zu, nodes %zu, graph-lr %zu, graph-rl %zu, graph-ll %zu\n"
    stat.system.ms_entries
    stat.nodes.ms_entries stat.graph-lr.ms_entries stat.graph-rl.ms_entries stat.graph-ll.ms_entries)
  (label exit
    (return status)))

(define (db-debug-count-all-btree-entries txn result) (status-t db-txn-t ui32*)
  "sum the count of all entries in all btrees used by the database"
  status-declare
  (declare stat db-statistics-t)
  (status-require (db-statistics txn &stat))
  (set *result
    (+
      stat.system.ms_entries
      stat.nodes.ms_entries
      stat.graph-lr.ms_entries stat.graph-rl.ms_entries stat.graph-ll.ms_entries))
  (label exit
    (return status)))

(define (db-field-type-size a) (ui8 ui8)
  "size in octets. zero for variable size types"
  (case = a
    ( (db-field-type-int64 db-field-type-uint64 db-field-type-char64 db-field-type-float64)
      (return 8))
    ( (db-field-type-int32 db-field-type-uint32 db-field-type-char32 db-field-type-float32)
      (return 4))
    ((db-field-type-int16 db-field-type-uint16 db-field-type-char16) (return 2))
    ((db-field-type-int8 db-field-type-uint8 db-field-type-char8) (return 1))
    (else (return 0))))

(define (db-ids->set a result) (status-t db-ids-t imht-set-t**)
  status-declare
  (db-status-memory-error-if-null (imht-set-create (i-array-length a) result))
  (while (i-array-in-range a)
    (imht-set-add *result (i-array-get a))
    (i-array-forward a))
  (label exit
    (return status)))

(define (db-read-name data-pointer result) (status-t ui8** ui8**)
  "read a length prefixed string from system type data.
  on success set result to a newly allocated string and data to the next byte after the string"
  status-declare
  (declare
    data ui8*
    len db-name-len-t
    name ui8*)
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

(define (db-statistics txn result) (status-t db-txn-t db-statistics-t*)
  "expects an allocated db-statistics-t"
  status-declare
  (db-mdb-status-require (mdb-stat txn.mdb-txn txn.env:dbi-system &result:system))
  (db-mdb-status-require (mdb-stat txn.mdb-txn txn.env:dbi-nodes &result:nodes))
  (db-mdb-status-require (mdb-stat txn.mdb-txn txn.env:dbi-graph-lr &result:graph-lr))
  (db-mdb-status-require (mdb-stat txn.mdb-txn txn.env:dbi-graph-ll &result:graph-ll))
  (db-mdb-status-require (mdb-stat txn.mdb-txn txn.env:dbi-graph-rl &result:graph-rl))
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
  "return one new unique type node identifier.
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
      (mdb-dbi-close mdb-env env:dbi-nodes)
      (mdb-dbi-close mdb-env env:dbi-graph-lr)
      (mdb-dbi-close mdb-env env:dbi-graph-rl)
      (mdb-dbi-close mdb-env env:dbi-graph-ll)
      (mdb-env-close mdb-env)
      (set env:mdb-env 0)))
  (db-free-env-types &env:types env:types-len)
  (if env:root (free-and-set-null env:root))
  (set env:open #f)
  (pthread-mutex-destroy &env:mutex))

(pre-include "./open.c" "./type.c" "./index.c" "./node.c" "./graph.c")