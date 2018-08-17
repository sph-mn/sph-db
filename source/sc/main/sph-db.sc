(sc-comment "this file is for declarations and macros needed to use sph-db as a shared library")
(pre-include "inttypes.h" "math.h" "pthread.h" "lmdb.h")
(sc-include "foreign/sph" "foreign/sph/i-array" "main/lib/status" "main/config")

(declare
  db-relation-t
  (type
    (struct
      (left db-id-t)
      (right db-id-t)
      (label db-id-t)
      (ordinal db-ordinal-t)))
  db-node-t
  (type
    (struct
      (id db-id-t)
      (data void*)
      (size size-t))))

(i-array-declare-type db-ids-t db-id-t)
(i-array-declare-type db-nodes-t db-node-t)
(i-array-declare-type db-relations-t db-relation-t)

(pre-define
  boolean uint8-t
  db-ids-new i-array-allocate-db-ids-t
  db-relations-new i-array-allocate-db-relations-t
  db-size-graph-data (+ (sizeof db-ordinal-t) (sizeof db-id-t))
  db-size-graph-key (* 2 (sizeof db-id-t))
  db-null 0
  db-size-element-id (- (sizeof db-id-t) (sizeof db-type-id-t))
  db-field-type-t uint8-t
  db-field-type-binary 1
  db-field-type-string 3
  db-field-type-float32 4
  db-field-type-float64 6
  db-field-type-int16 80
  db-field-type-int32 112
  db-field-type-int64 144
  db-field-type-int8 48
  db-field-type-string16 66
  db-field-type-string32 98
  db-field-type-string64 130
  db-field-type-string8 34
  db-field-type-uint16 64
  db-field-type-uint32 96
  db-field-type-uint64 128
  db-field-type-uint8 32
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
  (db-node-virtual->data id)
  (begin
    "db-id-t -> db-id-t"
    (bit-shift-right id 2))
  (db-txn-declare env name) (define name db-txn-t (struct-literal 0 env))
  (db-txn-abort-if-active a) (if a.mdb-txn (db-txn-abort &a))
  (db-txn-is-active a)
  (if* a.mdb-txn #t
    #f)
  (db-field-set a a-type a-name a-name-len)
  (set
    a.type a-type
    a.name a-name
    a.name-len a-name-len))

(declare
  ; types
  db-field-t
  (type
    (struct
      (name uint8-t*)
      (name-len db-name-len-t)
      (type db-field-type-t)
      (index db-fields-len-t)))
  db-index-t struct
  db-type-t
  (type
    (struct
      (fields-len db-fields-len-t)
      (fields-fixed-count db-fields-len-t)
      (fields-fixed-offsets size-t*)
      (fields db-field-t*)
      (flags uint8-t)
      (id db-type-id-t)
      (indices
        (struct
          db-index-t*))
      (indices-len db-indices-len-t)
      (indices-size size-t)
      (name uint8-t*)
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
      (root uint8-t*)
      (mutex pthread-mutex-t)
      (maxkeysize int)
      (types db-type-t*)
      (types-len db-type-id-t)))
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
      (env-open-flags uint-least32-t)
      (file-permissions uint16-t)))
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
  db-node-matcher-t (type (function-pointer boolean db-node-t void*))
  db-index-selection-t
  (type
    (struct
      (cursor MDB-cursor*)))
  db-node-index-selection-t
  (type
    (struct
      (index-selection db-index-selection-t)
      (nodes-cursor MDB-cursor*)))
  db-node-selection-t
  (type
    (struct
      (cursor MDB-cursor*)
      (matcher db-node-matcher-t)
      (matcher-state void*)
      (options uint8-t)
      (type db-type-t*)))
  db-graph-selection-t
  (type
    (struct
      (status status-t)
      (cursor (MDB-cursor* restrict))
      (cursor-2 (MDB-cursor* restrict))
      (left db-ids-t)
      (right db-ids-t)
      (label db-ids-t)
      (ids-set void*)
      (ordinal db-ordinal-condition-t*)
      (options uint8-t)
      (reader void*)))
  db-graph-reader-t
  (type (function-pointer status-t db-graph-selection-t* db-count-t db-relations-t*))
  ; routines
  (db-env-new result) (status-t db-env-t**)
  (db-statistics txn result) (status-t db-txn-t db-statistics-t*)
  (db-close env) (void db-env-t*)
  (db-open root options env) (status-t uint8-t* db-open-options-t* db-env-t*)
  (db-type-field-get type name) (db-field-t* db-type-t* uint8-t*)
  (db-type-get env name) (db-type-t* db-env-t* uint8-t*)
  (db-type-create env name fields fields-len flags result)
  (status-t db-env-t* uint8-t* db-field-t* db-fields-len-t uint8-t db-type-t**)
  (db-type-delete env id) (status-t db-env-t* db-type-id-t)
  (db-field-type-size a) (uint8-t uint8-t)
  (db-status-description a) (uint8-t* status-t)
  (db-status-name a) (uint8-t* status-t)
  (db-status-group-id->name a) (uint8-t* status-id-t)
  ; -- graph
  (db-graph-selection-finish selection) (void db-graph-selection-t*)
  (db-graph-select txn left right label ordinal offset result)
  (status-t
    db-txn-t db-ids-t* db-ids-t* db-ids-t* db-ordinal-condition-t* db-count-t db-graph-selection-t*)
  (db-graph-read selection count result) (status-t db-graph-selection-t* db-count-t db-relations-t*)
  (db-graph-ensure txn left right label ordinal-generator ordinal-generator-state)
  (status-t db-txn-t db-ids-t db-ids-t db-ids-t db-graph-ordinal-generator-t void*)
  (db-graph-delete txn left right label ordinal)
  (status-t db-txn-t db-ids-t* db-ids-t* db-ids-t* db-ordinal-condition-t*)
  ; -- node
  (db-node-values-free a) (void db-node-values-t*)
  (db-node-values-new type result) (status-t db-type-t* db-node-values-t*)
  (db-node-values-set values field-index data size)
  (void db-node-values-t* db-fields-len-t void* size-t) (db-node-values->data values result)
  (status-t db-node-values-t db-node-t*) (db-node-data->values type data result)
  (status-t db-type-t* db-node-t db-node-values-t*) (db-node-create txn values result)
  (status-t db-txn-t db-node-values-t db-id-t*) (db-node-get txn ids result-nodes)
  (status-t db-txn-t db-ids-t db-nodes-t*) (db-node-delete txn ids)
  (status-t db-txn-t db-ids-t*) (db-node-ref type node field)
  (db-node-value-t db-type-t* db-node-t db-fields-len-t) (db-node-exists txn ids result)
  (status-t db-txn-t db-ids-t boolean*)
  (db-node-select txn type offset matcher matcher-state result-selection)
  (status-t db-txn-t db-type-t* db-count-t db-node-matcher-t void* db-node-selection-t*)
  (db-node-read selection count result-nodes) (status-t db-node-selection-t* db-count-t db-nodes-t*)
  (db-node-skip selection count) (status-t db-node-selection-t* db-count-t)
  (db-node-selection-finish selection) (void db-node-selection-t*)
  (db-node-update txn id values) (status-t db-txn-t db-id-t db-node-values-t)
  (db-txn-write-begin a) (status-t db-txn-t*)
  (db-txn-begin a) (status-t db-txn-t*)
  (db-txn-commit a) (status-t db-txn-t*)
  (db-txn-abort a) (void db-txn-t*)
  (db-index-get type fields fields-len) (db-index-t* db-type-t* db-fields-len-t* db-fields-len-t)
  (db-index-create env type fields fields-len)
  (status-t db-env-t* db-type-t* db-fields-len-t* db-fields-len-t) (db-index-delete env index)
  (status-t db-env-t* db-index-t*) (db-index-rebuild env index)
  (status-t db-env-t* db-index-t*) (db-index-read selection count result-ids)
  (status-t db-index-selection-t db-count-t db-ids-t*) (db-index-selection-finish selection)
  (void db-index-selection-t*) (db-index-select txn index values result)
  (status-t db-txn-t db-index-t db-node-values-t db-index-selection-t*)
  (db-node-index-read selection count temp-ids result-nodes)
  (status-t db-node-index-selection-t db-count-t db-ids-t db-nodes-t*)
  (db-node-index-select txn index values result)
  (status-t db-txn-t db-index-t db-node-values-t db-node-index-selection-t*)
  (db-node-index-selection-finish selection) (void db-node-index-selection-t*))