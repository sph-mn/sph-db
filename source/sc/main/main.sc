(pre-include "./sph-db.h" "../foreign/sph/one.c" "./lib/lmdb.c")

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
  (db-cursor-declare name) (define name MDB-cursor* 0)
  (db-cursor-open txn name)
  (db-mdb-status-require! (mdb-cursor-open txn.mdb-txn (: txn.env (pre-concat dbi- name)) &name))
  (db-cursor-close name)
  (begin
    (mdb-cursor-close name)
    (set name 0))
  (db-cursor-close-if-active name) (if name (db-cursor-close name))
  db-size-system-key (+ 1 (sizeof db-type-id-t))
  (db-select-ensure-offset state offset reader)
  (if offset
    (begin
      (set state:options (bit-or db-read-option-skip state:options))
      (set status (reader state offset 0))
      (if (not db-mdb-status-success?) db-mdb-status-require-notfound)
      (set state:options (bit-xor db-read-option-skip state:options)))))

(define (db-debug-log-ids a) (b0 db-ids-t*)
  "display an ids list"
  (debug-log "length: %lu" (db-ids-length a))
  (while a
    (debug-log "%lu" (db-ids-first a))
    (set a (db-ids-rest a))))

(define (db-debug-log-ids-set a) (b0 imht-set-t)
  "display an ids set"
  (define index b32 0)
  (while (< index a.size)
    (debug-log "%lu" (array-get a.content index))
    (set index (+ 1 index))))

(define (db-debug-display-graph-records records) (b0 db-graph-records-t*)
  (declare record db-graph-record-t)
  (printf "graph records\n")
  (while records
    (set record (db-graph-records-first records))
    (printf "  lcor %lu %lu %lu %lu\n" record.left record.label record.ordinal record.right)
    (set records (db-graph-records-rest records))))

(define (db-debug-display-btree-counts txn) (status-t db-txn-t)
  status-init
  (declare stat db-statistics-t)
  (status-require! (db-statistics txn &stat))
  (printf
    "btree entry count: system %zu, nodes %zu, graph-lr %zu, graph-rl %zu, graph-ll %zu\n"
    stat.system.ms_entries
    stat.nodes.ms_entries stat.graph-lr.ms_entries stat.graph-rl.ms_entries stat.graph-ll.ms_entries)
  (label exit
    (return status)))

(define (db-debug-count-all-btree-entries txn result) (status-t db-txn-t b32*)
  status-init
  (declare stat db-statistics-t)
  (status-require! (db-statistics txn &stat))
  (set *result
    (+
      stat.system.ms_entries
      stat.nodes.ms_entries
      stat.graph-lr.ms_entries stat.graph-rl.ms_entries stat.graph-ll.ms_entries))
  (label exit
    (return status)))

(define (db-field-type-size a) (b8 b8)
  "size in octets. only for fixed size types"
  (case = a
    ( (db-field-type-int64 db-field-type-uint64 db-field-type-char64 db-field-type-float64)
      (return 64))
    ( (db-field-type-int32 db-field-type-uint32 db-field-type-char32 db-field-type-float32)
      (return 32))
    ((db-field-type-int16 db-field-type-uint16 db-field-type-char16) (return 16))
    ((db-field-type-int8 db-field-type-uint8 db-field-type-char8) (return 8))
    (else (return 0))))

(define (db-ids->set a result) (status-t db-ids-t* imht-set-t**)
  status-init
  (if (not (imht-set-create (db-ids-length a) result)) (db-status-set-id-goto db-status-id-memory))
  (while a
    (imht-set-add *result (db-ids-first a))
    (set a (db-ids-rest a)))
  (label exit
    (return status)))

(define (db-read-name data-pointer result) (status-t b8** b8**)
  "read a length prefixed string from system type data.
  on success set result to a newly allocated string and data to the next byte after the string"
  status-init
  (declare
    data b8*
    len db-name-len-t
    name b8*)
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
  status-init
  (db-mdb-status-require! (mdb-stat txn.mdb-txn txn.env:dbi-system &result:system))
  (db-mdb-status-require! (mdb-stat txn.mdb-txn txn.env:dbi-nodes &result:nodes))
  (db-mdb-status-require! (mdb-stat txn.mdb-txn txn.env:dbi-graph-lr &result:graph-lr))
  (db-mdb-status-require! (mdb-stat txn.mdb-txn txn.env:dbi-graph-ll &result:graph-ll))
  (db-mdb-status-require! (mdb-stat txn.mdb-txn txn.env:dbi-graph-rl &result:graph-rl))
  (label exit
    (return status)))

(define (db-sequence-next-system env result) (status-t db-env-t* db-type-id-t*)
  "return one new unique type identifier.
  the maximum identifier returned is db-type-id-limit minus one"
  status-init
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
  status-init
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

(define (db-free-env-types-indices indices indices-len) (b0 db-index-t** db-field-count-t)
  (declare
    i db-field-count-t
    index-pointer db-index-t*)
  (if (not *indices) return)
  (for ((set i 0) (< i indices-len) (set i (+ 1 i)))
    (set index-pointer (+ i *indices))
    (free-and-set-null index-pointer:fields))
  (free-and-set-null *indices))

(define (db-free-env-types-fields fields fields-len) (b0 db-field-t** db-field-count-t)
  (declare i db-field-count-t)
  (if (not *fields) return)
  (for ((set i 0) (< i fields-len) (set i (+ 1 i)))
    (free-and-set-null (: (+ i *fields) name)))
  (free-and-set-null *fields))

(define (db-free-env-type type) (b0 db-type-t*)
  (if (= 0 type:id) return)
  (free-and-set-null type:fields-fixed-offsets)
  (db-free-env-types-fields &type:fields type:fields-count)
  (db-free-env-types-indices &type:indices type:indices-count)
  (set type:id 0))

(define (db-free-env-types types types-len) (b0 db-type-t** db-type-id-t)
  (declare i db-type-id-t)
  (if (not *types) return)
  (for ((set i 0) (< i types-len) (set i (+ 1 i)))
    (db-free-env-type (+ i *types)))
  (free-and-set-null *types))

(define (db-close env) (b0 db-env-t*)
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

(pre-include "./open.c" "./node.c" "./graph.c"
  ;"./index.c"
  )