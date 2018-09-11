(sc-comment "this file is for declarations and macros needed to use sph-db as a shared library")
(pre-include "inttypes.h" "math.h" "pthread.h" "lmdb.h")
(sc-include "foreign/sph" "foreign/sph/status" "foreign/sph/i-array" "main/config")

(declare
  db-relation-t
  (type
    (struct
      (left db-id-t)
      (right db-id-t)
      (label db-id-t)
      (ordinal db-ordinal-t)))
  db-record-t
  (type
    (struct
      (id db-id-t)
      (data void*)
      (size size-t))))

(i-array-declare-type db-ids-t db-id-t)
(i-array-declare-type db-records-t db-record-t)
(i-array-declare-type db-relations-t db-relation-t)

(pre-define
  db-status-group-db "sph-db"
  db-status-group-lmdb "lmdb"
  db-status-group-libc "libc"
  db-ids-add i-array-add
  db-ids-clear i-array-clear
  db-ids-forward i-array-forward
  db-ids-free i-array-free
  db-ids-get i-array-get
  db-ids-get-at i-array-get-at
  db-ids-in-range i-array-in-range
  db-ids-length i-array-length
  db-ids-max-length i-array-max-length
  db-ids-remove i-array-remove
  db-ids-rewind i-array-rewind
  db-ids-set-null i-array-set-null
  db-relations-add i-array-add
  db-relations-clear i-array-clear
  db-relations-forward i-array-forward
  db-relations-free i-array-free
  db-relations-get i-array-get
  db-relations-get-at i-array-get-at
  db-relations-in-range i-array-in-range
  db-relations-length i-array-length
  db-relations-max-length i-array-max-length
  db-relations-remove i-array-remove
  db-relations-rewind i-array-rewind
  db-relations-set-null i-array-set-null
  db-records-add i-array-add
  db-records-clear i-array-clear
  db-records-forward i-array-forward
  db-records-free i-array-free
  db-records-get i-array-get
  db-records-get-at i-array-get-at
  db-records-in-range i-array-in-range
  db-records-length i-array-length
  db-records-max-length i-array-max-length
  db-records-remove i-array-remove
  db-records-rewind i-array-rewind
  db-records-set-null i-array-set-null
  boolean uint8-t
  db-size-relation-data (+ (sizeof db-ordinal-t) (sizeof db-id-t))
  db-size-relation-key (* 2 (sizeof db-id-t))
  db-null 0
  db-size-element-id (- (sizeof db-id-t) (sizeof db-type-id-t))
  db-field-type-t int8-t
  db-field-type-string64 -8
  db-field-type-string32 -7
  db-field-type-string16 -6
  db-field-type-string8 -5
  db-field-type-binary64 -4
  db-field-type-binary32 -3
  db-field-type-binary16 -2
  db-field-type-binary8 -1
  db-field-type-binary8f 1
  db-field-type-binary16f 2
  db-field-type-binary32f 3
  db-field-type-binary64f 4
  db-field-type-binary128f 5
  db-field-type-binary256f 6
  db-field-type-uint8f 8
  db-field-type-uint16f 9
  db-field-type-uint32f 10
  db-field-type-uint64f 11
  db-field-type-uint128f 12
  db-field-type-uint256f 13
  db-field-type-int8f 15
  db-field-type-int16f 16
  db-field-type-int32f 17
  db-field-type-int64f 18
  db-field-type-int128f 19
  db-field-type-int256f 20
  db-field-type-string8f 22
  db-field-type-string16f 23
  db-field-type-string32f 24
  db-field-type-string64f 25
  db-field-type-string128f 26
  db-field-type-string256f 27
  db-field-type-float32f 29
  db-field-type-float64f 30
  db-type-flag-virtual 1
  db-id-type-mask (convert-type db-type-id-mask db-id-t)
  db-id-element-mask (bit-not db-id-type-mask)
  (db-status-set-id-goto status-id) (status-set-both-goto db-status-group-db status-id)
  (status-require-read expression)
  (begin
    (set status expression)
    (if (not (or status-is-success (= status.id db-status-id-notfound))) status-goto))
  db-status-success-if-notfound
  (if (= status.id db-status-id-notfound) (set status.id status-id-success))
  (db-record-values-declare name) (define name db-record-values-t (struct-literal 0 0 0))
  (db-env-declare name) (define env db-env-t* 0)
  (db-ids-declare name) (i-array-declare name db-ids-t)
  (db-relations-declare name) (i-array-declare name db-relations-t)
  (db-records-declare name) (i-array-declare name db-records-t)
  (db-type-get-by-id env type-id) (+ type-id env:types)
  (db-type-is-virtual type) (bit-and db-type-flag-virtual type:flags)
  (db-record-is-virtual env record-id)
  (db-type-is-virtual (db-type-get-by-id env (db-id-type record-id))) (db-id-add-type id type-id)
  (begin
    "convert id and type-id to db-id-t to be able to pass c literals which might be initialised with some other type.
    string type part from id with db-id-element in case there are type bits set after for example typecasting from a smaller datatype"
    (bit-or
      (db-id-type (convert-type type-id db-id-t))
      (bit-shift-left (convert-type id db-id-t) (* 8 (sizeof db-type-id-t)))))
  (db-id-type id)
  (begin
    "get the type id part from a record id. a record id minus element id"
    (bit-and db-id-type-mask id))
  (db-id-element id)
  (begin
    "get the element id part from a record id. a record id minus type id"
    (bit-shift-right id (* 8 (sizeof db-type-id-t))))
  (db-record-virtual-from-uint type-id data)
  (begin
    "create a virtual record, which is a db-id-t"
    (db-id-add-type data type-id))
  db-record-virtual-from-int db-record-virtual-from-uint
  (db-record-virtual-data id type-name)
  (begin
    "get the data associated with a virtual record as a db-id-t
    this only works because the target type should be equal or smaller than db-size-id-element"
    (convert-type (db-id-element id) type-name))
  (db-txn-declare env name) (define name db-txn-t (struct-literal 0 env))
  (db-txn-abort-if-active a) (if a.mdb-txn (db-txn-abort &a))
  (db-txn-is-active a)
  (if* a.mdb-txn #t
    #f)
  (db-field-set a a-type a-name a-name-len)
  (set
    a.type a-type
    a.name a-name
    a.name-len a-name-len)
  (db-relation-selection-set-null name)
  (begin
    "set so that *-finish succeeds even if it has not yet been initialised.
      for having cleanup tasks at one place like with a goto exit label"
    (set
      name.cursor 0
      name.cursor-2 0
      name.options 0
      name.ids-set 0))
  (db-relation-selection-declare name)
  (begin
    (declare name db-relation-selection-t)
    (db-relation-selection-set-null name))
  (db-record-selection-set-null name) (set name.cursor 0)
  (db-record-selection-declare name)
  (begin
    (declare name db-record-selection-t)
    (db-record-selection-set-null name))
  (db-index-selection-set-null name) (set name.cursor 0)
  (db-index-selection-declare name)
  (begin
    (declare name db-index-selection-t)
    (db-index-selection-set-null name))
  (db-record-index-selection-set-null name)
  (set
    name.records-cursor 0
    name.index-selection.cursor 0)
  (db-record-index-selection-declare name)
  (begin
    (declare name db-record-index-selection-t)
    (db-record-index-selection-set-null name)))

(enum
  (db-status-id-success
    db-status-id-undefined
    db-status-id-condition-unfulfilled
    db-status-id-data-length
    db-status-id-different-format
    db-status-id-duplicate
    db-status-id-input-type
    db-status-id-invalid-argument
    db-status-id-max-element-id
    db-status-id-max-type-id
    db-status-id-max-type-id-size
    db-status-id-memory
    db-status-id-missing-argument-db-root
    db-status-id-notfound
    db-status-id-not-implemented
    db-status-id-path-not-accessible-db-root
    db-status-id-index-keysize db-status-id-type-field-order db-status-id-last))

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
      (dbi-records MDB-dbi)
      (dbi-relation-ll MDB-dbi)
      (dbi-relation-lr MDB-dbi)
      (dbi-relation-rl MDB-dbi)
      (dbi-system MDB-dbi)
      (mdb-env MDB-env*)
      (is-open boolean)
      (root uint8-t*)
      (mutex pthread-mutex-t)
      (maxkeysize uint32-t)
      (format uint32-t)
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
      (records MDB-stat)
      (relation-lr MDB-stat)
      (relation-rl MDB-stat)
      (relation-ll MDB-stat)))
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
  db-relation-ordinal-generator-t (type (function-pointer db-ordinal-t void*))
  db-ordinal-condition-t
  (type
    (struct
      (min db-ordinal-t)
      (max db-ordinal-t)))
  db-record-value-t
  (type
    (struct
      (size size-t)
      (data void*)))
  db-record-values-t
  (type
    (struct
      (data db-record-value-t*)
      (extent db-fields-len-t)
      (type db-type-t*)))
  db-record-matcher-t (type (function-pointer boolean db-type-t* db-record-t void*))
  db-index-selection-t
  (type
    (struct
      (cursor MDB-cursor*)))
  db-record-index-selection-t
  (type
    (struct
      (index-selection db-index-selection-t)
      (records-cursor MDB-cursor*)))
  db-record-selection-t
  (type
    (struct
      (cursor MDB-cursor*)
      (matcher db-record-matcher-t)
      (matcher-state void*)
      (options uint8-t)
      (type db-type-t*)))
  db-relation-selection-t
  (type
    (struct
      (cursor (MDB-cursor* restrict))
      (cursor-2 (MDB-cursor* restrict))
      (left db-ids-t)
      (right db-ids-t)
      (label db-ids-t)
      (ids-set void*)
      (ordinal db-ordinal-condition-t)
      (options uint8-t)
      (reader void*)))
  db-relation-reader-t
  (type (function-pointer status-t db-relation-selection-t* db-count-t db-relations-t*))
  ; routines
  (db-open-options-set-defaults a) (void db-open-options-t*)
  (db-env-new result) (status-t db-env-t**)
  (db-statistics txn result) (status-t db-txn-t db-statistics-t*)
  (db-close env) (void db-env-t*)
  (db-open root options env) (status-t uint8-t* db-open-options-t* db-env-t*)
  (db-type-field-get type name) (db-field-t* db-type-t* uint8-t*)
  (db-type-get env name) (db-type-t* db-env-t* uint8-t*)
  (db-type-create env name fields fields-len flags result)
  (status-t db-env-t* uint8-t* db-field-t* db-fields-len-t uint8-t db-type-t**)
  (db-type-delete env id) (status-t db-env-t* db-type-id-t)
  (db-field-type-size a) (uint8-t db-field-type-t)
  (db-status-description a) (uint8-t* status-t)
  (db-status-name a) (uint8-t* status-t)
  (db-ids-new length result-ids) (status-t size-t db-ids-t*)
  (db-records-new length result-records) (status-t size-t db-records-t*)
  (db-relations-new length result-relations) (status-t size-t db-relations-t*)
  (db-records->ids records result-ids) (void db-records-t db-ids-t*)
  ; -- relation
  (db-relation-selection-finish selection) (void db-relation-selection-t*)
  (db-relation-select txn left right label ordinal result)
  (status-t db-txn-t db-ids-t* db-ids-t* db-ids-t* db-ordinal-condition-t* db-relation-selection-t*)
  (db-relation-read selection count result)
  (status-t db-relation-selection-t* db-count-t db-relations-t*) (db-relation-skip selection count)
  (status-t db-relation-selection-t* db-count-t)
  (db-relation-ensure txn left right label ordinal-generator ordinal-generator-state)
  (status-t db-txn-t db-ids-t db-ids-t db-ids-t db-relation-ordinal-generator-t void*)
  (db-relation-delete txn left right label ordinal)
  (status-t db-txn-t db-ids-t* db-ids-t* db-ids-t* db-ordinal-condition-t*)
  ; -- record
  (db-record-values-free a) (void db-record-values-t*)
  (db-record-values-new type result) (status-t db-type-t* db-record-values-t*)
  (db-record-values-set values field-index data size)
  (void db-record-values-t* db-fields-len-t void* size-t) (db-record-values->data values result)
  (status-t db-record-values-t db-record-t*) (db-record-data->values type data result)
  (status-t db-type-t* db-record-t db-record-values-t*) (db-record-create txn values result)
  (status-t db-txn-t db-record-values-t db-id-t*) (db-record-get txn ids result-records)
  (status-t db-txn-t db-ids-t db-records-t*) (db-record-delete txn ids)
  (status-t db-txn-t db-ids-t) (db-record-delete-type txn type-id)
  (status-t db-txn-t db-type-id-t) (db-record-ref type record field)
  (db-record-value-t db-type-t* db-record-t db-fields-len-t)
  (db-record-select txn type matcher matcher-state result-selection)
  (status-t db-txn-t db-type-t* db-record-matcher-t void* db-record-selection-t*)
  (db-record-read selection count result-records)
  (status-t db-record-selection-t db-count-t db-records-t*) (db-record-skip selection count)
  (status-t db-record-selection-t db-count-t) (db-record-selection-finish selection)
  (void db-record-selection-t*) (db-record-update txn id values)
  (status-t db-txn-t db-id-t db-record-values-t) (db-record-virtual-from-any type-id data data-size)
  (db-id-t db-type-id-t void* uint8-t) (db-txn-write-begin a)
  (status-t db-txn-t*) (db-txn-begin a)
  (status-t db-txn-t*) (db-txn-commit a)
  (status-t db-txn-t*) (db-txn-abort a)
  (void db-txn-t*) (db-txn-begin-child parent-txn a)
  (status-t db-txn-t db-txn-t*) (db-txn-write-begin-child parent-txn a)
  (status-t db-txn-t db-txn-t*) (db-index-get type fields fields-len)
  (db-index-t* db-type-t* db-fields-len-t* db-fields-len-t)
  (db-index-create env type fields fields-len result-index)
  (status-t db-env-t* db-type-t* db-fields-len-t* db-fields-len-t db-index-t**)
  (db-index-delete env index) (status-t db-env-t* db-index-t*)
  (db-index-rebuild env index) (status-t db-env-t* db-index-t*)
  (db-index-read selection count result-ids) (status-t db-index-selection-t db-count-t db-ids-t*)
  (db-index-selection-finish selection) (void db-index-selection-t*)
  (db-index-select txn index values result)
  (status-t db-txn-t db-index-t db-record-values-t db-index-selection-t*)
  (db-record-index-read selection count result-records)
  (status-t db-record-index-selection-t db-count-t db-records-t*)
  (db-record-index-select txn index values result)
  (status-t db-txn-t db-index-t db-record-values-t db-record-index-selection-t*)
  (db-record-index-selection-finish selection) (void db-record-index-selection-t*))