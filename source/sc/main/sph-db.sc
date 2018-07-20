(sc-comment "this file is for declarations and macros needed to use sph-db as a shared library")
(pre-include "math.h" "pthread.h" "lmdb.h")
(sc-include "foreign/sph" "main/lib/status" "main/config")

(pre-define-if-not-defined
  db-id-t ui64
  db-type-id-t ui16
  db-ordinal-t ui32
  db-count-t ui32
  db-indices-len-t ui8
  db-fields-len-t ui8
  db-name-len-t ui8
  db-name-len-max UINT8_MAX
  db-field-type-t ui8
  db-id-mask UINT64_MAX
  db-type-id-mask UINT16_MAX
  (db-id-equal a b) (= a b)
  (db-id-compare a b)
  (if* (< a b) -1
    (> a b)))

(pre-define
  db-size-id (sizeof db-id-t)
  db-size-type-id (sizeof db-type-id-t)
  db-size-ordinal (sizeof db-ordinal-t)
  db-ordinal-compare db-id-compare
  db-size-graph-data (+ db-size-ordinal db-size-id)
  db-size-graph-key (* 2 db-size-id)
  db-selection-flag-skip 1
  db-graph-selection-flag-is-set-left 2
  db-graph-selection-flag-is-set-right 4
  db-null 0
  db-type-id-limit db-type-id-mask
  db-size-element-id (- (sizeof db-id-t) (sizeof db-type-id-t))
  db-id-type-mask (bit-shift-left (convert-type db-type-id-mask db-id-t) (* 8 db-size-element-id))
  db-id-element-mask (bit-not db-id-type-mask)
  db-element-id-limit db-id-element-mask
  db-type-flag-virtual 1
  db-system-label-format 0
  db-system-label-type 1
  db-system-label-index 2
  db-field-type-float32 4
  db-field-type-float64 6
  db-field-type-binary 1
  db-field-type-string 3
  db-field-type-int8 48
  db-field-type-int16 80
  db-field-type-int32 112
  db-field-type-int64 144
  db-field-type-uint8 32
  db-field-type-uint16 64
  db-field-type-uint32 96
  db-field-type-uint64 128
  db-field-type-char8 34
  db-field-type-char16 66
  db-field-type-char32 98
  db-field-type-char64 130
  db-env-types-extra-count 20
  db-size-type-id-max 16
  db-size-system-label 1
  (db-id-add-type id type-id)
  (bit-or id (bit-shift-left (convert-type type-id db-id-t) (* 8 db-size-element-id)))
  (db-id-type id)
  (begin
    "get the type id part from a node id. a node id without element id"
    (bit-shift-right id (* 8 db-size-element-id)))
  (db-id-element id)
  (begin
    "get the element id part from a node id. a node id without type id"
    (bit-and db-id-element-mask id))
  (db-pointer->id-at a index) (pointer-get (+ index (convert-type a db-id-t*)))
  (db-pointer->id a) (pointer-get (convert-type a db-id-t*))
  (db-field-type-is-fixed a) (not (bit-and 1 a))
  (db-system-key-label a) (pointer-get (convert-type a ui8*))
  (db-system-key-id a)
  (pointer-get (convert-type (+ db-size-system-label (convert-type a ui8*)) db-type-id-t*))
  (db-status-memory-error-if-null variable)
  (if (not variable) (status-set-both-goto db-status-group-db db-status-id-memory))
  (db-malloc variable size)
  (begin
    (set variable (malloc size))
    (db-status-memory-error-if-null variable))
  (db-malloc-string variable len)
  (begin
    "allocate memory for a string with size and one extra last null element"
    (db-malloc variable (+ 1 len))
    (set (pointer-get (+ len variable)) 0))
  (db-calloc variable count size)
  (begin
    (set variable (calloc count size))
    (db-status-memory-error-if-null variable))
  (db-realloc variable variable-temp size)
  (begin
    (set variable-temp (realloc variable size))
    (db-status-memory-error-if-null variable-temp)
    (set variable variable-temp))
  (db-env-define name)
  (begin
    (declare name db-env-t*)
    (db-calloc name 1 (sizeof db-env-t)))
  (db-node-virtual->data id)
  (begin
    "db-id-t -> db-id-t"
    (bit-shift-right id 2))
  (db-pointer-allocation-set result expression result-temp)
  (begin
    (set result-temp expression)
    (if result-temp (set result result-temp)
      (db-status-set-id-goto db-status-id-memory)))
  (db-ids-add-require target source ids-temp)
  (db-pointer-allocation-set target (db-ids-add target source) ids-temp) (db-declare-ids name)
  (define name db-ids-t* 0) (db-declare-ids-two name-1 name-2)
  (begin
    (db-declare-ids name-1)
    (db-declare-ids name-2))
  (db-declare-ids-three name-1 name-2 name-3)
  (begin
    (db-declare-ids-two name-1 name-2)
    (db-declare-ids name-3))
  (db-graph-data->id a) (db-pointer->id (+ 1 (convert-type a db-ordinal-t*)))
  (db-graph-data->ordinal a) (pointer-get (convert-type a db-ordinal-t*))
  (db-graph-data-set-id a value) (set (db-graph-data->id a) value)
  (db-graph-data-set-ordinal a value) (set (db-graph-data->ordinal a) value)
  (db-graph-data-set-both a ordinal id)
  (begin
    (db-graph-data-set-ordinal ordinal)
    (db-graph-data-set-id id))
  (db-txn-declare env name) (define name db-txn-t (struct-literal 0 env))
  (db-txn-abort-if-active a) (if a.mdb-txn (db-txn-abort &a))
  (db-txn-is-active a)
  (if* a.mdb-txn #t
    #f))

(declare
  db-field-t
  (type
    (struct
      (name ui8*)
      (name-len db-name-len-t)
      (type db-field-type-t)
      (index db-fields-len-t)))
  db-index-t struct
  db-type-t
  (type
    (struct
      (fields-len db-fields-len-t)
      (fields-fixed-count db-fields-len-t)
      ; example: field-sizes-in-bytes: 1 4 2, fields-fixed-offsets: 1 5 7
      (fields-fixed-offsets size-t*)
      (fields db-field-t*)
      (flags ui8)
      (id db-type-id-t)
      (indices
        (struct
          db-index-t*))
      (indices-len db-indices-len-t)
      (name ui8*)
      (sequence db-id-t)))
  db-index-t
  (type
    (struct
      db-index-t
      (dbi MDB-dbi)
      (fields db-fields-len-t*)
      (fields-len db-fields-len-t)
      (type db-type-t*)))
  db-env-t
  (type
    (struct
      (dbi-nodes MDB-dbi)
      (dbi-graph-ll MDB-dbi)
      (dbi-graph-lr MDB-dbi)
      (dbi-graph-rl MDB-dbi)
      (dbi-system MDB-dbi)
      (mdb-env MDB-env*)
      (open boolean)
      (root ui8*)
      (mutex pthread-mutex-t)
      (maxkeysize int)
      (types db-type-t*)
      (types-len db-type-id-t)))
  db-data-record-t
  (type
    (struct
      (id db-id-t)
      (size size-t)
      (data void*)))
  db-txn-t
  (type
    (struct
      (mdb-txn MDB-txn*)
      (env db-env-t*)))
  db-statistics-t
  (type
    (struct
      (system MDB-stat)
      (nodes MDB-stat)
      (graph-lr MDB-stat)
      (graph-rl MDB-stat)
      (graph-ll MDB-stat)))
  db-open-options-t
  (type
    (struct
      (is-read-only boolean)
      (maximum-size size-t)
      (maximum-reader-count db-count-t)
      (maximum-db-count db-count-t)
      (filesystem-has-ordered-writes boolean)
      (env-open-flags ui32-least)
      (file-permissions ui16)))
  db-graph-record-t
  (type
    (struct
      (left db-id-t)
      (right db-id-t)
      (label db-id-t)
      (ordinal db-ordinal-t)))
  db-graph-ordinal-generator-t (type (function-pointer db-ordinal-t void*))
  db-ordinal-condition-t
  (type
    (struct
      (min db-ordinal-t)
      (max db-ordinal-t)))
  db-node-value-t
  (type
    (struct
      (size db-data-len-t)
      (data void*)))
  db-node-values-t
  (type
    (struct
      (data db-node-value-t*)
      (last db-fields-len-t)
      (type db-type-t*)))
  db-node-data-t
  (type
    (struct
      (data void*)
      (size size-t)))
  db-node-matcher-t (type (function-pointer boolean db-id-t db-node-data-t void*))
  db-index-selection-t
  (type
    (struct
      (current db-id-t)
      (cursor MDB-cursor*)))
  db-node-index-selection-t
  (type
    (struct
      (index-state db-index-selection-t*)
      (nodes MDB-cursor*)
      (current db-id-t))))

(pre-include "./lib/data-structures.c")

(declare
  db-node-selection-t
  (type
    (struct
      (count db-count-t)
      (current db-node-data-t)
      (current-id db-id-t)
      (cursor MDB-cursor*)
      (env db-env-t*)
      (ids db-ids-t*)
      (matcher db-node-matcher-t)
      (matcher-state void*)
      (options ui8)
      (type db-type-t*)))
  db-graph-selection-t
  (type
    (struct
      (status status-t)
      (cursor (MDB-cursor* restrict))
      (cursor-2 (MDB-cursor* restrict))
      (left void*)
      (right void*)
      (label void*)
      (left-first db-ids-t*)
      (right-first db-ids-t*)
      (ordinal db-ordinal-condition-t*)
      (options ui8)
      (reader void*)))
  db-graph-reader-t
  (type (function-pointer status-t db-graph-selection-t* db-count-t db-graph-records-t**))
  ; routines
  (db-statistics txn result) (status-t db-txn-t db-statistics-t*)
  (db-close env) (void db-env-t*)
  (db-open root options env) (status-t ui8* db-open-options-t* db-env-t*)
  (db-type-field-get type name) (db-field-t* db-type-t* ui8*)
  (db-type-get env name) (db-type-t* db-env-t* ui8*)
  (db-type-create env name fields fields-len flags result)
  (status-t db-env-t* ui8* db-field-t* db-fields-len-t ui8 db-type-t**) (db-type-delete env id)
  (status-t db-env-t* db-type-id-t) (db-sequence-next-system env result)
  (status-t db-env-t* db-type-id-t*) (db-sequence-next env type-id result)
  (status-t db-env-t* db-type-id-t db-id-t*) (db-field-type-size a)
  (ui8 ui8) (db-graph-ensure txn left right label ordinal-generator ordinal-generator-state)
  (status-t db-txn-t db-ids-t* db-ids-t* db-ids-t* db-graph-ordinal-generator-t void*)
  (db-status-description a) (ui8* status-t)
  (db-status-name a) (ui8* status-t)
  (db-status-group-id->name a) (ui8* status-id-t)
  ; db-graph
  (db-graph-selection-destroy state) (void db-graph-selection-t*)
  (db-graph-select txn left right label ordinal offset result)
  (status-t
    db-txn-t db-ids-t* db-ids-t* db-ids-t* db-ordinal-condition-t* db-count-t db-graph-selection-t*)
  (db-graph-read state count result) (status-t db-graph-selection-t* db-count-t db-graph-records-t**)
  (db-graph-ensure txn left right label ordinal-generator ordinal-generator-state)
  (status-t db-txn-t db-ids-t* db-ids-t* db-ids-t* db-graph-ordinal-generator-t void*)
  (db-graph-selection-destroy state) (void db-graph-selection-t*)
  (db-graph-delete txn left right label ordinal)
  (status-t db-txn-t db-ids-t* db-ids-t* db-ids-t* db-ordinal-condition-t*)
  (db-graph-select txn left right label ordinal offset result)
  (status-t
    db-txn-t db-ids-t* db-ids-t* db-ids-t* db-ordinal-condition-t* db-count-t db-graph-selection-t*)
  ; db-debug
  (db-debug-log-ids a) (void db-ids-t*)
  (db-debug-log-ids-set a) (void imht-set-t)
  (db-debug-display-graph-records records) (void db-graph-records-t*)
  (db-debug-count-all-btree-entries txn result) (status-t db-txn-t db-count-t*)
  (db-debug-display-btree-counts txn) (status-t db-txn-t)
  (db-debug-display-content-graph-lr txn) (status-t db-txn-t)
  (db-debug-display-content-graph-rl txn) (status-t db-txn-t)
  ; db-node
  (db-node-values-new type result) (status-t db-type-t* db-node-values-t*)
  (db-node-values-set values field-index data size)
  (void db-node-values-t* db-fields-len-t void* size-t) (db-node-values->data values result)
  (status-t db-node-values-t db-node-data-t*) (db-node-data->values type data result)
  (status-t db-type-t* db-node-data-t db-node-values-t*) (db-node-create txn values result)
  (status-t db-txn-t db-node-values-t db-id-t*) (db-node-get txn id result)
  (status-t db-txn-t db-id-t db-node-data-t*) (db-node-delete txn ids)
  (status-t db-txn-t db-ids-t*) (db-node-data-ref type data field)
  (db-node-data-t db-type-t* db-node-data-t db-fields-len-t) (db-node-ref state field)
  (db-node-data-t db-node-selection-t* db-fields-len-t) (db-node-exists txn ids result)
  (status-t db-txn-t db-ids-t* boolean*)
  (db-node-select txn ids type offset matcher matcher-state result-state)
  (status-t db-txn-t db-ids-t* db-type-t* db-count-t db-node-matcher-t void* db-node-selection-t*)
  (db-node-next state) (status-t db-node-selection-t*)
  (db-node-skip state count) (status-t db-node-selection-t* db-count-t)
  (db-node-selection-destroy state) (void db-node-selection-t*)
  (db-node-update txn id values) (status-t db-txn-t db-id-t db-node-values-t)
  (db-txn-write-begin a) (status-t db-txn-t*)
  (db-txn-begin a) (status-t db-txn-t*)
  (db-txn-commit a) (status-t db-txn-t*)
  (db-txn-abort a) (void db-txn-t*))