(pre-include "./sph-db.h" "../foreign/sph/one.c" "./lib/lmdb.c" "./lib/debug.c")

(pre-define
  (db-error-log pattern ...)
  (fprintf stderr (pre-string-concat "%s:%d error: " pattern "\n") __func__ __LINE__ __VA_ARGS__)
  reduce-count (set count (- count 1))
  stop-if-count-zero (if (= 0 count) (goto exit))
  (optional-count count) (if* (= 0 count) UINT32_MAX count)
  (db-cursor-open txn name) (db-mdb-cursor-open txn.mdb-txn (: txn.env (pre-concat dbi- name)) name)
  (db-cursor-declare name) (db-mdb-cursor-declare name)
  db-field-type-float32 4
  db-field-type-float64 6
  db-field-type-vbinary 1
  db-field-type-vstring 3
  db-system-label-format 0
  db-system-label-type 1
  db-system-label-index 2
  db-size-system-key (+ 1 (sizeof db-type-id-t))
  (db-field-type-fixed? a) (not (bit-and 1 a))
  (db-system-key-label a) (pointer-get (convert-type a b8*))
  (db-system-key-id a) (pointer-get (convert-type (+ 1 (convert-type a b8*)) db-type-id-t*))
  (db-field-type-integer? a) (not (bit-and 15 a))
  (db-field-type-string? a) (= 2 (bit-and 15 a))
  (db-id-set-type id type-id) (set (db-type-id id) type-id)
  (db-field-type-integer signed size-exponent)
  (begin
    "3b:size-exponent 1b:signed 4b:id-prefix:0000
    size-bit-count: 2 ** size-exponent + 3 = 8
    example (size-exponent 4): 10000000, 10010000"
    (bit-and (bit-shift-left size-exponent 5) (if* signed 16 0)))
  (db-field-type-string size-exponent)
  (begin
    "4b:size-exponent 4b:id-prefix:0010"
    (bit-and (bit-shift-left size-exponent 4) 2))
  (db-status-memory-error-if-null variable)
  (if (not variable) (status-set-both-goto db-status-group-db db-status-id-memory))
  (db-malloc variable size)
  (begin
    (set variable (malloc size))
    (db-status-memory-error-if-null variable))
  (db-malloc-string variable size)
  (begin
    "allocate memory and set the last element to zero"
    (db-malloc variable size)
    (pointer-set (+ (- size 1) variable) 0))
  (db-calloc variable count size)
  (begin
    (set variable (calloc count size))
    (db-status-memory-error-if-null variable))
  (db-realloc variable variable-temp size)
  (begin
    (set variable-temp (realloc variable size))
    (db-status-memory-error-if-null variable-temp)
    (set variable variable-temp))
  (db-select-ensure-offset state offset reader)
  (if offset
    (begin
      (struct-pointer-set state options (bit-or db-read-option-skip state:options))
      (set status (reader state offset 0))
      (if (not db-mdb-status-success?) db-mdb-status-require-notfound)
      (struct-pointer-set state options (bit-xor db-read-option-skip state:options))))
  (free-and-set-null a)
  (begin
    (free a)
    (set a 0)))

(define (db-field-type-size a) (b8 b8)
  "size in octets. only for fixed size types"
  (return
    (cond*
      ((= db-field-type-float32 a) 4)
      ((= db-field-type-float64 a) 8)
      ((db-field-type-integer? a) (bit-shift-right a 5))
      ((db-field-type-string? a) (bit-shift-right a 4)) (else 0))))

(define (db-ids->set a result) (status-t db-ids-t* imht-set-t**)
  status-init
  (if (not (imht-set-create (db-ids-length a) result)) (db-status-set-id-goto db-status-id-memory))
  (while a
    (imht-set-add (pointer-get result) (db-ids-first a))
    (set a (db-ids-rest a)))
  (label exit
    (return status)))

(define (db-statistics txn result) (status-t db-txn-t db-statistics-t*)
  "expects an allocated db-statistics-t"
  status-init
  (pre-let
    ( (result-set dbi-name)
      (db-mdb-status-require!
        (mdb-stat
          txn.mdb-txn
          (: txn.env (pre-concat dbi- dbi-name))
          (address-of (struct-get (pointer-get result) dbi-name)))))
    (result-set system)
    (result-set id->data) (result-set left->right) (result-set right->left) (result-set label->left))
  (label exit
    (return status)))

(define (db-read-length-prefixed-string-b8 data-pointer result) (status-t b8** b8**)
  "read a length prefixed string from system type data.
  on success set result to a newly allocated string and data to the next byte after the string"
  status-init
  (define data b8* (pointer-get data-pointer))
  (define len b8 (pointer-get data))
  (define name b8* (malloc (+ 1 len)))
  (if (not name) (status-set-both-goto db-status-group-db db-status-id-memory))
  (pointer-set (+ len name) 0)
  (memcpy name (+ 1 data) len)
  (label exit
    (set
      (pointer-get result) name
      (pointer-get data-pointer) (+ len data))
    (return status)))

(define (db-sequence-next env type result) (status-t db-env-t* db-type-t* db-id-t*)
  "return one new, unique and typed identifier"
  status-init
  (define mutex pthread-mutex-t env:mutex)
  (define sequence db-id-t type:sequence)
  (pthread-mutex-lock (address-of mutex))
  (if (< sequence db-id-id-max)
    (begin
      (set
        (pointer-get result) (db-id-set-type sequence type:id)
        type:sequence (+ 1 sequence))
      (pthread-mutex-unlock (address-of mutex)))
    (status-set-both-goto db-status-group-db db-status-id-max-id))
  (label exit
    (return status)))

(define (db-free-env-types-indices indices indices-len) (b0 db-index-t** db-field-count-t)
  (declare
    index db-field-count-t
    index-pointer db-index-t*)
  (if (not (pointer-get indices)) return)
  (set index 0)
  (while (< index indices-len)
    (set index-pointer (+ index (pointer-get indices)))
    (free-and-set-null index-pointer:fields)
    (set index (+ 1 index)))
  (free-and-set-null (pointer-get indices)))

(define (db-free-env-types-fields fields fields-len) (b0 db-field-t** db-field-count-t)
  (declare index db-field-count-t)
  (if (not (pointer-get fields)) return)
  (set index 0)
  (while (< index fields-len)
    (free-and-set-null (: (+ index (pointer-get fields)) name))
    (set index (+ 1 index)))
  (free-and-set-null (pointer-get fields)))

(define (db-free-env-types types types-len) (b0 db-type-t** db-type-id-t)
  (declare
    index db-type-id-t
    type db-type-t*)
  (if (not (pointer-get types)) return)
  (set index 0)
  (while (< index types-len)
    (set type (+ index (pointer-get types)))
    (if (= 0 type:id)
      (begin
        (free-and-set-null type:fields-fixed-offsets)
        (db-free-env-types-fields (address-of type:fields) type:fields-count)
        (db-free-env-types-indices (address-of type:indices) type:indices-count)))
    (set index (+ 1 index)))
  (free-and-set-null (pointer-get types)))

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
      (struct-pointer-set env mdb-env 0)))
  (db-free-env-types (address-of env:types) env:types-len)
  (if env:root (free-and-set-null env:root))
  (set env:open #f)
  (pthread-mutex-destroy (address-of env:mutex)))

(pre-include "./open.c" "./node.c"
  ;"main/graph"
  ;"index"
  )