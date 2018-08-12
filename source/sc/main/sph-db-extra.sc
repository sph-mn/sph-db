(sc-comment "secondary api for dealing with internals")
(sc-comment "imht-set is used for example for matching ids in graph-read")
(pre-define imht-set-key-t db-id-t)
(sc-include "foreign/sph/imht-set")

(pre-define
  db-system-label-format 0
  db-system-label-type 1
  db-system-label-index 2
  db-selection-flag-skip 1
  db-graph-selection-flag-is-set-left 2
  db-graph-selection-flag-is-set-right 4
  db-id-type-mask (bit-shift-left (convert-type db-type-id-mask db-id-t) (* 8 db-size-element-id))
  db-id-element-mask (bit-not db-id-type-mask)
  db-type-id-limit db-type-id-mask
  db-element-id-limit db-id-element-mask
  db-type-flag-virtual 1
  db-size-type-id-max 16
  db-size-system-label 1
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
  (db-graph-data->id a) (db-pointer->id (+ 1 (convert-type a db-ordinal-t*)))
  (db-graph-data->ordinal a) (pointer-get (convert-type a db-ordinal-t*))
  (db-graph-data-set-id a value) (set (db-graph-data->id a) value)
  (db-graph-data-set-ordinal a value) (set (db-graph-data->ordinal a) value)
  (db-graph-data-set-both a ordinal id)
  (begin
    (db-graph-data-set-ordinal ordinal)
    (db-graph-data-set-id id)))

(declare
  ; db-debug
  (db-debug-log-ids a) (void db-ids-t)
  (db-debug-log-ids-set a) (void imht-set-t)
  (db-debug-log-relations records) (void db-relations-t)
  (db-debug-log-btree-counts txn) (status-t db-txn-t)
  (db-debug-count-all-btree-entries txn result) (status-t db-txn-t db-count-t*)
  ; index
  (db-index-key env index values result-data result-size)
  (status-t db-env-t* db-index-t db-node-values-t void** size-t*)
  (db-indices-entry-ensure txn values id) (status-t db-txn-t db-node-values-t db-id-t)
  (db-index-name type-id fields fields-len result result-size)
  (status-t db-type-id-t db-fields-len-t* db-fields-len-t ui8** size-t*)
  (db-indices-entry-delete txn values id) (status-t db-txn-t db-node-values-t db-id-t))