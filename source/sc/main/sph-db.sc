(sc-comment "this file is for declarations and macros needed to use sph-db as a shared library")
(pre-include "math.h" "pthread.h" "lmdb.h")
(sc-include "foreign/sph" "main/lib/status" "main/config")
(pre-define (db-pointer->id a index) (pointer-get (+ index (convert-type a db-id-t*))))

(pre-define-if-not-defined
  db-id-t b64
  db-type-id-t b8
  db-ordinal-t b32
  db-index-count-t b8
  db-field-count-t b8
  db-id-max UINT64_MAX
  db-size-id (sizeof db-id-t)
  db-size-ordinal (sizeof db-ordinal-t)
  db-size-type-id (sizeof db-type-id-t)
  (db-id-equal? a b) (= a b)
  (db-id-compare a b) (if* (< a b) -1 (> a b))
  (db-pointer->id a index) (pointer-get (+ index (convert-type a db-id-t*))))

(pre-define
  db-ordinal-compare db-id-compare
  db-size-graph-data (+ db-size-ordinal db-size-id)
  db-size-graph-key (* 2 db-size-id)
  db-read-option-skip 1
  db-read-option-is-set-left 2
  db-read-option-is-set-right 4
  db-read-option-initialised 8
  db-null 0
  (env-define name) (define name db-env-t* (calloc 1 (sizeof db-env-t)))
  db-data-t MDB-val
  (db-data-data a) data.mv-data
  (db-data-data-set a value) (struct-set data mv-data value)
  (db-data-size a) data.mv-size
  (db-data-size-set a value) (struct-set data mv-size value)
  (db-type-id id) (pointer-get (convert-type (address-of id) db-type-id-t*))
  (db-id-id id)
  (pointer-get (convert-type (+ 1 (convert-type (address-of id) db-type-id-t*)) db-id-t*))
  (db-txn-declare env name) (define name db-txn-t (struct-literal 0 env))
  (db-txn-begin a)
  (db-mdb-status-require!
    (mdb-txn-begin (struct-pointer-get a.env mdb-env) 0 MDB-RDONLY (address-of a.mdb-txn)))
  (db-txn-write-begin a)
  (db-mdb-status-require!
    (mdb-txn-begin (struct-pointer-get a.env mdb-env) 0 0 (address-of a.mdb-txn)))
  (db-txn-abort a)
  (begin
    (mdb-txn-abort a.mdb-txn)
    (set a.mdb-txn 0))
  (db-txn-commit a)
  (begin
    (db-mdb-status-require! (mdb-txn-commit a.mdb-txn))
    (set a.mdb-txn 0))
  (db-node-virtual->data id)
  (begin
    "db-id-t -> db-id-t"
    (bit-shift-right id 2))
  (db-pointer-allocation-set result expression result-temp)
  (begin
    (set result-temp expression)
    (if result-temp (set result result-temp) (db-status-set-id-goto db-status-id-memory)))
  (db-ids-add! target source ids-temp)
  (db-pointer-allocation-set target (db-ids-add target source) ids-temp) (db-define-ids name)
  (define name db-ids-t* 0) (db-define-ids-2 name-1 name-2)
  (begin
    (db-define-ids name-1)
    (db-define-ids name-2))
  (db-define-ids-3 name-1 name-2 name-3)
  (begin
    (db-define-ids-2 name-1 name-2)
    (db-define-ids name-3))
  (db-graph-data->id a) (db-pointer->id (+ 1 (convert-type a db-ordinal-t*)) 0)
  (db-graph-data->ordinal a) (pointer-get (convert-type a db-ordinal-t*))
  (db-graph-data-set-id a value) (set (db-graph-data->id a) value)
  (db-graph-data-set-ordinal a value) (set (db-graph-data->ordinal a) value)
  (db-graph-data-set-both a ordinal id)
  (begin
    (db-graph-data-set-ordinal ordinal)
    (db-graph-data-set-id id)))

(declare
  db-type-id-max db-type-id-t
  db-type-id-mask db-id-t
  db-id-id-max db-id-t
  db-field-t
  (type
    (struct
      (name b8*)
      (type b8)))
  db-index-t
  (type
    (struct
      (dbi MDB-dbi)
      (fields db-field-t*)
      (fields-count db-field-count-t)
      (type db-type-id-t)))
  db-type-t
  (type
    (struct
      (fields-count db-field-count-t)
      (fields-fixed-count db-field-count-t)
      ; example: field-sizes-in-bits: 8 32 16, fields-fixed-offsets: 8 40 56
      (fields-fixed-offsets db-field-count-t*)
      (fields db-field-t*)
      (flags b8)
      (id db-type-id-t)
      (indices db-index-t*)
      (indices-count db-index-count-t)
      (name b8*)
      (sequence db-id-t)))
  db-env-t
  (type
    (struct
      (dbi-id->data MDB-dbi)
      (dbi-label->left MDB-dbi)
      (dbi-left->right MDB-dbi)
      (dbi-right->left MDB-dbi)
      (dbi-system MDB-dbi)
      (mdb-env MDB-env*)
      (open boolean)
      (root b8*)
      (mutex pthread-mutex-t)
      (types db-type-t*)
      (types-len db-type-id-t)))
  db-data-record-t
  (type
    (struct
      (id db-id-t)
      (size size-t)
      (data b0*)))
  db-graph-record-t
  (type
    (struct
      (left db-id-t)
      (right db-id-t)
      (label db-id-t)
      (ordinal db-ordinal-t)))
  db-txn-t
  (type
    (struct
      (mdb-txn MDB-txn*)
      (env db-env-t*)))
  db-statistics-t
  (type
    (struct
      (system MDB-stat)
      (id->data MDB-stat)
      (left->right MDB-stat)
      (right->left MDB-stat)
      (label->left MDB-stat)))
  db-open-options-t
  (type
    (struct
      (read-only? b8)
      (maximum-size-octets size-t)
      (maximum-reader-count b32)
      (maximum-db-count b32)
      (filesystem-has-ordered-writes? b8)
      (env-open-flags b32)
      (file-permissions b16)))
  (db-statistics txn result) (status-t db-txn-t db-statistics-t*)
  (db-close env) (b0 db-env-t*)
  (db-open root options env) (status-t b8* db-open-options-t* db-env-t*))

(pre-define imht-set-key-t db-id-t)
(sc-include "foreign/sph/imht-set")

(pre-define
  mi-list-name-prefix db-ids
  mi-list-element-t db-id-t)

(sc-include "foreign/sph/mi-list")

(pre-define
  mi-list-name-prefix db-data-list
  mi-list-element-t db-data-t)

(sc-include "foreign/sph/mi-list")

(pre-define
  mi-list-name-prefix db-data-records
  mi-list-element-t db-data-record-t)

(sc-include "foreign/sph/mi-list")

(pre-define
  mi-list-name-prefix db-graph-records
  mi-list-element-t db-graph-record-t)

(sc-include "foreign/sph/mi-list")

(pre-define
  db-ids-first mi-list-first
  db-ids-first-address mi-list-first-address
  db-ids-rest mi-list-rest
  db-data-list-first mi-list-first
  db-data-list-first-address mi-list-first-address
  db-data-list-rest mi-list-rest
  db-data-records-first mi-list-first
  db-data-records-first-address mi-list-first-address
  db-data-records-rest mi-list-rest
  db-graph-records-first mi-list-first
  db-graph-records-first-address mi-list-first-address
  db-graph-records-rest mi-list-rest)

;-- old --;

#;(
(declare (type db-index-errors-graph-t
    (struct
      (errors? boolean)
      (missing-right-left db-graph-records-t*)
      (missing-label-left db-graph-records-t*)
      (excess-right-left db-graph-records-t*)
      (excess-label-left db-graph-records-t*))))

(declare (type db-index-errors-t
    (struct
      (errors? boolean)
      (different-data-id db-ids-t*)
      (excess-data-id db-ids-t*)
      (different-id-data db-ids-t*)
      (missing-id-data db-ids-t*))))

(define db-index-errors-graph-null db-index-errors-graph-t (struct-literal 0 0 0 0 0))
(define db-index-errors-null db-index-errors-t (struct-literal 0 0 0 0 0))

(define-type db-ordinal-condition-t
  (struct
    (min db-ordinal-t)
    (max db-ordinal-t)))

(define-type db-node-read-state-t
  (struct
    (status status-t)
    (cursor (MDB-cursor* restrict))
    (types b8)
    (options b8)))

(define-type db-intern-read-state-t
  (struct
    (status status-t)
    (cursor (MDB-cursor* restrict))
    (options b8)))

(define-type db-graph-read-state-t
  (struct
    (status status-t)
    (cursor (MDB-cursor* restrict))
    (cursor-2 (MDB-cursor* restrict))
    (left b0*)
    (right b0*)
    (label b0*)
    (left-first db-ids-t*)
    (right-first db-ids-t*)
    (ordinal db-ordinal-condition-t*)
    (options b8)
    (reader b0*)))

(define-type db-graph-reader-t
  (function-pointer status-t db-graph-read-state-t* b32 db-graph-records-t**))

(define-type db-graph-ordinal-generator-t (function-pointer db-ordinal-t b0*))
(pre-define (db-type? db-type-name id) (= db-type-name (bit-and id db-type-mask)))
(define (db-node-read state count result) (status-t db-node-read-state-t* b32 db-data-records-t**))
(define (db-node-select txn types offset state) (status-t db-txn-t b8 b32 db-node-read-state-t*))
(define (db-node-selection-destroy state) (b0 db-node-read-state-t*))
(define (db-graph-selection-destroy state) (b0 db-graph-read-state-t*))

(define (db-intern-data->id txn data every? result)
  (status-t db-txn-t db-data-list-t* boolean db-ids-t**))

(define (db-intern-ensure txn data result) (status-t db-txn-t db-data-list-t* db-ids-t**))
(define (db-intern-update txn id data) (status-t db-txn-t db-id-t db-data-t))
(define (db-extern-update txn id data) (status-t db-txn-t db-id-t db-data-t))

(define (db-intern-id->data txn ids every? result)
  (status-t db-txn-t db-ids-t* boolean db-data-list-t**))

(define (db-extern-create txn count data result) (status-t db-txn-t b32 db-data-t* db-ids-t**))

(define (db-extern-id->data txn ids every? result)
  (status-t db-txn-t db-ids-t* boolean db-data-list-t**))

(define (db-extern-data->id txn data result) (status-t db-txn-t db-data-t db-ids-t**))
(define (db-id-create txn count result) (status-t db-txn-t b32 db-ids-t**))
(define (db-exists? txn ids result) (status-t db-txn-t db-ids-t* boolean*))
(define (db-extern? id) (boolean db-id-t))
(define (db-id? id) (boolean db-id-t))
(define (db-intern-small? id) (boolean db-id-t))
(define (db-identify txn ids result) (status-t db-txn-t db-ids-t* db-ids-t**))
(define (db-intern? id) (boolean db-id-t))
(define (db-graph? id) (boolean db-id-t))
(define (db-index-errors txn result) (status-t db-txn-t db-index-errors-t*))
(define (db-index-errors-graph txn result) (status-t db-txn-t db-index-errors-graph-t*))
(define (db-index-recreate-extern) status-t)
(define (db-index-recreate-intern) status-t)
(define (db-index-recreate-graph) status-t)

(define (db-open-options-set-defaults a) (db-open-options-t db-open-options-t*))

(define (db-graph-ensure txn left right label ordinal-generator ordinal-generator-state)
  (status-t db-txn-t db-ids-t* db-ids-t* db-ids-t* db-graph-ordinal-generator-t b0*))

(define (db-delete txn ids) (status-t db-txn-t db-ids-t*))

(define (db-graph-delete txn left right label ordinal)
  (status-t db-txn-t db-ids-t* db-ids-t* db-ids-t* db-ordinal-condition-t*))

(define (db-status-description a) (b8* status-t))
(define (db-status-name a) (b8* status-t))
(define (db-status-group-id->name a) (b8* status-i-t))

(define (db-graph-select txn left right label ordinal offset result)
  (status-t
    db-txn-t db-ids-t* db-ids-t* db-ids-t* db-ordinal-condition-t* b32 db-graph-read-state-t*))

(define (db-graph-read state count result)
  (status-t db-graph-read-state-t* b32 db-graph-records-t**))

(define (db-debug-log-ids a) (b0 db-ids-t*))
(define (db-debug-log-ids-set a) (b0 imht-set-t))
(define (db-debug-display-graph-records records) (b0 db-graph-records-t*))
(define (db-debug-count-all-btree-entries txn result) (status-t db-txn-t b32*))
(define (db-debug-display-btree-counts txn) (status-t db-txn-t))
(define (db-debug-display-content-left->right txn) (status-t db-txn-t))
(define (db-debug-display-content-right->left txn) (status-t db-txn-t))
)