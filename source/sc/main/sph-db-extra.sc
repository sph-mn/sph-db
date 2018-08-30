(sc-comment "secondary api for dealing with internals")
(sc-comment "imht-set is used for example for matching ids in relation-read")
(pre-define imht-set-key-t db-id-t)
(sc-include "foreign/sph/imht-set")

(pre-define
  db-system-label-format 0
  db-system-label-type 1
  db-system-label-index 2
  db-selection-flag-skip 1
  db-relation-selection-flag-is-set-left 2
  db-relation-selection-flag-is-set-right 4
  db-type-id-limit db-type-id-mask
  db-element-id-limit db-id-element-mask
  db-type-flag-virtual 1
  db-size-type-id-max 16
  db-size-system-label 1
  (db-pointer->id-at a index) (pointer-get (+ index (convert-type a db-id-t*)))
  (db-pointer->id a) (pointer-get (convert-type a db-id-t*))
  (db-field-type-is-fixed a) (not (bit-and 1 a))
  (db-system-key-label a) (pointer-get (convert-type a uint8-t*))
  (db-system-key-id a)
  (pointer-get (convert-type (+ db-size-system-label (convert-type a uint8-t*)) db-type-id-t*))
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
  (db-relation-data->id a) (db-pointer->id (+ 1 (convert-type a db-ordinal-t*)))
  (db-relation-data->ordinal a) (pointer-get (convert-type a db-ordinal-t*))
  (db-relation-data-set-id a value) (set (db-relation-data->id a) value)
  (db-relation-data-set-ordinal a value) (set (db-relation-data->ordinal a) value)
  (db-relation-data-set-both a ordinal id)
  (begin
    (db-relation-data-set-ordinal ordinal)
    (db-relation-data-set-id id)))

(declare
  (db-sequence-next-system env result) (status-t db-env-t* db-type-id-t*)
  (db-sequence-next env type-id result) (status-t db-env-t* db-type-id-t db-id-t*)
  ; db-debug
  (db-debug-log-id-bits a) (void db-id-t)
  (db-debug-log-ids a) (void db-ids-t)
  (db-debug-log-ids-set a) (void imht-set-t)
  (db-debug-log-relations records) (void db-relations-t)
  (db-debug-log-btree-counts txn) (status-t db-txn-t)
  (db-debug-count-all-btree-entries txn result) (status-t db-txn-t db-count-t*)
  ; index
  (db-index-key env index values result-data result-size)
  (status-t db-env-t* db-index-t db-record-values-t void** size-t*)
  (db-indices-entry-ensure txn values id) (status-t db-txn-t db-record-values-t db-id-t)
  (db-index-name type-id fields fields-len result result-size)
  (status-t db-type-id-t db-fields-len-t* db-fields-len-t uint8-t** size-t*)
  (db-indices-entry-delete txn values id) (status-t db-txn-t db-record-values-t db-id-t))