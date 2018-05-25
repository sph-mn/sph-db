(pre-include "./sph-db.h" "../foreign/sph/one.c" "./lib/lmdb.c" "./lib/debug.c")

(pre-define
  (db-error-log pattern ...)
  (fprintf stderr (pre-string-concat "%s:%d error: " pattern "\n") __func__ __LINE__ __VA_ARGS__)
  reduce-count (set count (- count 1))
  stop-if-count-zero (if (= 0 count) (goto exit))
  (optional-count count)
  (if* (= 0 count) UINT32_MAX
    count)
  (db-cursor-declare name) (db-mdb-cursor-declare name)
  (db-cursor-open txn name) (db-mdb-cursor-open txn.mdb-txn (: txn.env (pre-concat dbi- name)) name)
  db-size-system-key (+ 1 (sizeof db-type-id-t))
  (db-select-ensure-offset state offset reader)
  (if offset
    (begin
      (set state:options (bit-or db-read-option-skip state:options))
      (set status (reader state offset 0))
      (if (not db-mdb-status-success?) db-mdb-status-require-notfound)
      (set state:options (bit-xor db-read-option-skip state:options)))))

(define (db-field-type-size a) (b8 b8)
  "size in octets. only for fixed size types"
  (return
    (case* = a
      ((db-field-type-int64 db-field-type-uint64 db-field-type-char64 db-field-type-float64) 64)
      ((db-field-type-int32 db-field-type-uint32 db-field-type-char32 db-field-type-float32) 32)
      ((db-field-type-int16 db-field-type-uint16 db-field-type-char16) 16)
      ((db-field-type-int8 db-field-type-uint8 db-field-type-char8) 8)
      (else 0))))

(define (db-ids->set a result) (status-t db-ids-t* imht-set-t**)
  status-init
  (if (not (imht-set-create (db-ids-length a) result)) (db-status-set-id-goto db-status-id-memory))
  (while a
    (imht-set-add *result (db-ids-first a))
    (set a (db-ids-rest a)))
  (label exit
    (return status)))

(define (db-statistics txn result) (status-t db-txn-t db-statistics-t*)
  "expects an allocated db-statistics-t"
  status-init
  (pre-let
    ( (result-set dbi-name)
      (db-mdb-status-require!
        (mdb-stat txn.mdb-txn (: txn.env (pre-concat dbi- dbi-name)) &result:dbi-name)))
    (result-set system)
    (result-set id->data) (result-set left->right) (result-set right->left) (result-set label->left))
  (label exit
    (return status)))

(define (db-read-length-prefixed-string-b8 data-pointer result) (status-t b8** b8**)
  "read a length prefixed string from system type data.
  on success set result to a newly allocated string and data to the next byte after the string"
  status-init
  (declare
    data b8*
    len b8
    name b8*)
  (set
    data *data-pointer
    len *data
    name (malloc (+ 1 len)))
  (if (not name) (status-set-both-goto db-status-group-db db-status-id-memory))
  (pointer-set (+ len name) 0)
  (memcpy name (+ 1 data) len)
  (label exit
    (set
      *result name
      *data-pointer (+ len data))
    (return status)))

(define (db-sequence-next env type-id result) (status-t db-env-t* db-type-id-t db-id-t*)
  "return one new, unique and typed identifier"
  status-init
  (declare
    sequence db-id-t
    sequence-pointer db-id-t*)
  (pthread-mutex-lock &env:mutex)
  (set sequence-pointer (address-of (: (+ type-id env:types) sequence)))
  (if (< sequence db-element-id-max)
    (begin
      (set *sequence-pointer (+ 1 sequence))
      (pthread-mutex-unlock &env:mutex)
      (set *result (db-id-add-type sequence type-id)))
    (begin
      (pthread-mutex-unlock &env:mutex)
      (status-set-both-goto db-status-group-db db-status-id-max-id)))
  (label exit
    (return status)))

(define (db-sequence-next-system env result) (status-t db-env-t* db-type-id-t*)
  "return one new, unique and typed identifier"
  status-init
  (declare sequence db-type-id-t)
  (pthread-mutex-lock &env:mutex)
  (set sequence (convert-type env:types:sequence db-type-id-t))
  (if (< sequence db-type-id-max)
    (begin
      (set env:types:sequence (+ 1 sequence))
      (pthread-mutex-unlock &env:mutex)
      (set *result sequence))
    (begin
      (pthread-mutex-unlock &env:mutex)
      (status-set-both-goto db-status-group-db db-status-id-max-id)))
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

(define (db-free-env-types types types-len) (b0 db-type-t** db-type-id-t)
  (declare
    i db-type-id-t
    type db-type-t*)
  (if (not *types) return)
  (for ((set i 0) (< i types-len) (set i (+ 1 i)))
    (set type (+ i *types))
    (if (= 0 type:id)
      (begin
        (free-and-set-null type:fields-fixed-offsets)
        (db-free-env-types-fields &type:fields type:fields-count)
        (db-free-env-types-indices &type:indices type:indices-count))))
  (free-and-set-null *types))

(define (db-close env) (b0 db-env-t*)
  (define mdb-env MDB-env* env:mdb-env)
  (if mdb-env
    (begin
      (mdb-dbi-close mdb-env env:dbi-system)
      (mdb-dbi-close mdb-env env:dbi-id->data)
      (mdb-dbi-close mdb-env env:dbi-left->right)
      (mdb-dbi-close mdb-env env:dbi-right->left)
      (mdb-dbi-close mdb-env env:dbi-label->left)
      (mdb-env-close mdb-env)
      (set env:mdb-env 0)))
  (db-free-env-types &env:types env:types-len)
  (if env:root (free-and-set-null env:root))
  (set env:open #f)
  (pthread-mutex-destroy &env:mutex))

(pre-include "./open.c" "./node.c"
  ;"main/graph"
  ;"index"
  )