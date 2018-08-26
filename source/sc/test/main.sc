(pre-include "./helper.c")

(sc-comment
  "the following values should not be below 3, or important cases would not be tested.
   the values should also not be so high that the linearly created ordinals exceed the size of the ordinal type.
   tip: reduce when debugging to make tests run faster. but dont forget to increase it again to 20 or something
   or otherwise the small count will mask potential errors")

(define common-element-count uint32-t 20)
(define common-label-count uint32-t 20)
(pre-define db-env-types-extra-count 20)

(define (test-open-empty env) (status-t db-env-t*)
  status-declare
  (test-helper-assert "env.is_open is true" (= #t env:is-open))
  (test-helper-assert "env.root is set" (= 0 (strcmp env:root test-helper-db-root)))
  (label exit
    (return status)))

(define (test-statistics env) (status-t db-env-t*)
  status-declare
  (declare stat db-statistics-t)
  (db-txn-declare env txn)
  (status-require (db-txn-begin &txn))
  (status-require (db-statistics txn (address-of stat)))
  (test-helper-assert "dbi-system contanis only one entry" (= 1 stat.system.ms_entries))
  (label exit
    (db-txn-abort-if-active txn)
    (return status)))

(define (test-type-create-get-delete env) (status-t db-env-t*)
  status-declare
  (declare
    fields (array db-field-t 4)
    fields-2 (array db-field-t* 4)
    i db-fields-len-t
    type-1 db-type-t*
    type-2 db-type-t*
    type-1-1 db-type-t*
    type-2-1 db-type-t*)
  (sc-comment "create type-1")
  (status-require (db-type-create env "test-type-1" 0 0 0 &type-1))
  (test-helper-assert "type id" (= 1 type-1:id))
  (test-helper-assert "type sequence" (= 1 type-1:sequence))
  (test-helper-assert "type field count" (= 0 type-1:fields-len))
  (sc-comment "create type-2")
  (db-field-set (array-get fields 0) db-field-type-int8 "test-field-1" 12)
  (db-field-set (array-get fields 1) db-field-type-int8 "test-field-2" 12)
  (db-field-set (array-get fields 2) db-field-type-string "test-field-3" 12)
  (db-field-set (array-get fields 3) db-field-type-string "test-field-4" 12)
  (status-require (db-type-create env "test-type-2" fields 4 0 &type-2))
  (test-helper-assert "second type id" (= 2 type-2:id))
  (test-helper-assert "second type sequence" (= 1 type-2:sequence))
  (test-helper-assert "second type fields-len" (= 4 type-2:fields-len))
  (test-helper-assert "second type name" (= 0 (strcmp "test-type-2" type-2:name)))
  (sc-comment "test cached field values")
  (for ((set i 0) (< i type-2:fields-len) (set i (+ 1 i)))
    (test-helper-assert
      "second type field name len equality"
      (= (: (+ i fields) name-len) (: (+ i type-2:fields) name-len)))
    (test-helper-assert
      "second type field name equality"
      (= 0 (strcmp (: (+ i fields) name) (: (+ i type-2:fields) name))))
    (test-helper-assert
      "second type type equality" (= (: (+ i fields) type) (: (+ i type-2:fields) type))))
  (sc-comment "test db-type-field-get")
  (array-set
    fields-2
    0
    (db-type-field-get type-2 "test-field-1")
    1 (db-type-field-get type-2 "test-field-2") 2 (db-type-field-get type-2 "test-field-3"))
  (sc-comment "test fixed count and offsets")
  (test-helper-assert "fixed count" (= 2 type-2:fields-fixed-count))
  (test-helper-assert
    "fixed offsets"
    (and
      (= 0 (array-get type-2:fields-fixed-offsets 0)) (= 1 (array-get type-2:fields-fixed-offsets 1))))
  (sc-comment "test type-field-get")
  (test-helper-assert
    "type-field-get"
    (=
      (array-get fields-2 0)
      (+ 0 type-2:fields)
      (array-get fields-2 1)
      (+ 1 type-2:fields)
      (array-get fields-2 2) (+ 2 type-2:fields) (array-get fields-2 3) (+ 3 type-2:fields)))
  (sc-comment "test type-get")
  (test-helper-assert "non existent type" (not (db-type-get env "test-type-x")))
  (set
    type-1-1 (db-type-get env "test-type-1")
    type-2-1 (db-type-get env "test-type-2"))
  (test-helper-assert "existent types" (and type-1-1 type-2-1))
  (test-helper-assert "existent type ids" (and (= type-1:id type-1-1:id) (= type-2:id type-2-1:id)))
  (test-helper-assert
    "existent types" (and (db-type-get env "test-type-1") (db-type-get env "test-type-2")))
  (sc-comment "test type-delete")
  (status-require (db-type-delete env type-1:id))
  (status-require (db-type-delete env type-2:id))
  (set
    type-1-1 (db-type-get env "test-type-1")
    type-2-1 (db-type-get env "test-type-2"))
  (test-helper-assert "type-delete type-get" (not (or type-1-1 type-2-1)))
  (label exit
    (return status)))

(define (test-type-create-many env) (status-t db-env-t*)
  "create several types, particularly to test automatic env:types array resizing"
  status-declare
  (declare
    i db-type-id-t
    name (array uint8-t 255)
    type db-type-t*)
  (sc-comment "10 times as many as there is extra room left for new types in env:types")
  (for ((set i 0) (< i (* 10 db-env-types-extra-count)) (set i (+ 1 i)))
    (sprintf name "test-type-%lu" i)
    (status-require (db-type-create env name 0 0 0 &type)))
  (label exit
    (return status)))

(define (test-sequence env) (status-t db-env-t*)
  status-declare
  (declare
    i size-t
    id db-id-t
    prev-id db-id-t
    prev-type-id db-type-id-t
    type db-type-t*
    type-id db-type-id-t)
  (sc-comment "node sequence. note that sequences only persist through data inserts")
  (status-require (db-type-create env "test-type" 0 0 0 &type))
  (set
    type:sequence (- db-element-id-limit 100)
    prev-id (db-id-add-type (- db-element-id-limit 100 1) type:id))
  (for ((set i db-element-id-limit) (<= i db-element-id-limit) (set i (+ i 1)))
    (set status (db-sequence-next env type:id &id))
    (if (<= db-element-id-limit (db-id-element (+ 1 prev-id)))
      (begin
        (test-helper-assert "node sequence is limited" (= db-status-id-max-element-id status.id))
        (set status.id status-id-success))
      (test-helper-assert
        "node sequence is monotonically increasing" (and status-is-success (= 1 (- id prev-id)))))
    (set prev-id id))
  (sc-comment "system sequence. test last, otherwise type ids would be exhausted")
  (set prev-type-id type:id)
  (for ((set i type:id) (<= i db-type-id-limit) (set i (+ i 1)))
    (set status (db-sequence-next-system env &type-id))
    (if (<= db-type-id-limit (+ 1 prev-type-id))
      (begin
        (test-helper-assert "system sequence is limited" (= db-status-id-max-type-id status.id))
        (set status.id status-id-success))
      (begin
        (test-helper-assert
          "system sequence is monotonically increasing"
          (and status-is-success (= 1 (- type-id prev-type-id))))))
    (set prev-type-id type-id))
  (label exit
    (return status)))

(define (test-open-nonempty env) (status-t db-env-t*)
  status-declare
  (status-require (test-type-create-get-delete env))
  (status-require (test-helper-reset env #t))
  (label exit
    (return status)))

(define (test-id-construction env) (status-t db-env-t*)
  "test features related to the combination of element and type id to node id"
  status-declare
  (sc-comment "id creation")
  (declare type-id db-type-id-t)
  (set type-id (/ db-type-id-limit 2))
  (test-helper-assert
    "type-id-size + element-id-size = id-size"
    (= (sizeof db-id-t) (+ (sizeof db-type-id-t) db-size-element-id)))
  (test-helper-assert
    "type and element masks not conflicting" (not (bit-and db-id-type-mask db-id-element-mask)))
  (test-helper-assert
    "type-id-mask | element-id-mask = id-mask"
    (= db-id-mask (bit-or db-id-type-mask db-id-element-mask)))
  (test-helper-assert
    "id type" (= type-id (db-id-type (db-id-add-type db-element-id-limit type-id))))
  (sc-comment "take a low value to be compatible with different configurations")
  (test-helper-assert "id element" (= 254 (db-id-element (db-id-add-type 254 type-id))))
  (label exit
    (return status)))

(define (test-graph-read env) (status-t db-env-t*)
  status-declare
  (db-txn-declare env txn)
  (declare data test-helper-graph-read-data-t)
  (status-require
    (test-helper-graph-read-setup
      env common-element-count common-element-count common-label-count &data))
  (status-require (db-txn-begin &txn))

  (status-require (test-helper-graph-read-one txn data 0 0 0 0 0))
  (status-require (test-helper-graph-read-one txn data 1 0 0 0 0))
  (status-require (test-helper-graph-read-one txn data 0 1 0 0 0))
  (status-require (test-helper-graph-read-one txn data 1 1 0 0 0))
  (status-require (test-helper-graph-read-one txn data 0 0 1 0 0))
  (status-require (test-helper-graph-read-one txn data 1 0 1 0 0))
  (status-require (test-helper-graph-read-one txn data 0 1 1 0 0))
  (status-require (test-helper-graph-read-one txn data 1 1 1 0 0))
  (status-require (test-helper-graph-read-one txn data 1 0 0 1 0))
  (status-require (test-helper-graph-read-one txn data 1 1 0 1 0))
  (status-require (test-helper-graph-read-one txn data 1 0 1 1 0))
  (status-require (test-helper-graph-read-one txn data 1 1 1 1 0))
  (label exit
    (db-txn-abort-if-active txn)
    (test-helper-graph-read-teardown &data)
    (return status)))

(define (test-graph-delete env) (status-t db-env-t*)
  "some assertions depend on the correctness of graph-read"
  status-declare
  (declare data test-helper-graph-delete-data-t)
  (status-require
    (test-helper-graph-delete-setup
      env common-element-count common-element-count common-label-count &data))
  (status-require (test-helper-graph-delete-one data 1 0 0 0))
  (status-require (test-helper-graph-delete-one data 0 1 0 0))
  (status-require (test-helper-graph-delete-one data 1 1 0 0))
  (status-require (test-helper-graph-delete-one data 0 0 1 0))
  (status-require (test-helper-graph-delete-one data 1 0 1 0))
  (status-require (test-helper-graph-delete-one data 0 1 1 0))
  (status-require (test-helper-graph-delete-one data 1 1 1 0))
  (status-require (test-helper-graph-delete-one data 1 0 0 1))
  (status-require (test-helper-graph-delete-one data 1 1 0 1))
  (status-require (test-helper-graph-delete-one data 1 0 1 1))
  (status-require (test-helper-graph-delete-one data 1 1 1 1))
  (label exit
    (return status)))

(define (test-node-create env) (status-t db-env-t*)
  status-declare
  (db-txn-declare env txn)
  (i-array-declare ids db-ids-t)
  (i-array-declare nodes db-nodes-t)
  (declare
    field-data db-node-value-t
    field-index db-fields-len-t
    id-1 db-id-t
    id-2 db-id-t
    node-1 db-node-t
    node-2 db-node-t
    size-1 size-t
    size-2 size-t
    type db-type-t*
    value-1 uint8-t
    value-2 int8-t
    values-1 db-node-values-t
    values-2 db-node-values-t)
  (define value-4 uint8-t* (convert-type "abcde" uint8-t*))
  (status-require (test-helper-create-type-1 env &type))
  (sc-comment "prepare node values")
  (status-require (db-node-values-new type &values-1))
  (set
    value-1 11
    value-2 -128)
  (db-node-values-set &values-1 0 &value-1 0)
  (db-node-values-set &values-1 1 &value-2 0)
  (sc-comment "empty field in between, field 2 left out")
  (db-node-values-set &values-1 3 value-4 5)
  (sc-comment "test node values/data conversion")
  (db-node-values->data values-1 &node-1)
  (test-helper-assert "node-values->data size" (= (+ (* 2 (sizeof db-data-len-t)) 7) node-1.size))
  (db-node-data->values type node-1 &values-2)
  (test-helper-assert "node-data->values type equal" (= values-1.type values-2.type))
  (test-helper-assert
    "node-data->values size equal"
    (and
      (=
        (struct-get (array-get values-1.data 0) size) (struct-get (array-get values-2.data 0) size))
      (=
        (struct-get (array-get values-1.data 1) size) (struct-get (array-get values-2.data 1) size))
      (=
        (struct-get (array-get values-1.data 2) size) (struct-get (array-get values-2.data 2) size))
      (=
        (struct-get (array-get values-1.data 3) size) (struct-get (array-get values-2.data 3) size))))
  (test-helper-assert
    "node-data->values data equal 1"
    (= 0 (memcmp value-4 (struct-get (array-get values-1.data 3) data) 5)))
  (for ((set field-index 0) (< field-index type:fields-len) (set field-index (+ 1 field-index)))
    (set
      size-1 (struct-get (array-get values-1.data field-index) size)
      size-2 (struct-get (array-get values-2.data field-index) size))
    (test-helper-assert
      "node-data->values data equal 2"
      (=
        0
        (memcmp
          (struct-get (array-get values-1.data field-index) data)
          (struct-get (array-get values-2.data field-index) data)
          (if* (< size-1 size-2) size-2
            size-1)))))
  (db-node-values->data values-2 &node-2)
  (test-helper-assert
    "node-values->data"
    (and
      (= node-1.size node-2.size)
      (=
        0
        (memcmp
          node-1.data
          node-2.data
          (if* (< node-1.size node-2.size) node-2.size
            node-1.size)))))
  (db-node-values-free &values-2)
  (db-node-values-new type &values-2)
  (db-node-values->data values-2 &node-2)
  (test-helper-assert "node-values->data empty" (= 0 node-2.size))
  (sc-comment "test node-ref")
  (set field-data (db-node-ref type node-1 3))
  (test-helper-assert
    "node-ref-1" (and (= 5 field-data.size) (= 0 (memcmp value-4 field-data.data field-data.size))))
  (sc-comment "test actual node creation")
  (status-require (db-txn-write-begin &txn))
  (status-require (db-node-create txn values-1 &id-1))
  (test-helper-assert "element id 1" (= 1 (db-id-element id-1)))
  (status-require (db-node-create txn values-1 &id-2))
  (test-helper-assert "element id 2" (= 2 (db-id-element id-2)))
  (status-require (db-txn-commit &txn))
  (status-require (db-txn-begin &txn))
  (sc-comment "test node-get")
  (status-require (db-ids-new 3 &ids))
  (status-require (db-nodes-new 3 &nodes))
  (i-array-add ids id-1)
  (i-array-add ids id-2)
  (status-require (db-node-get txn ids &nodes))
  (test-helper-assert "node-get result length" (= 2 (i-array-length nodes)))
  (test-helper-assert
    "node-get result ids"
    (and
      (= id-1 (struct-get (i-array-get-at nodes 0) id))
      (= id-2 (struct-get (i-array-get-at nodes 1) id))))
  (set field-data (db-node-ref type (i-array-get-at nodes 0) 1))
  (test-helper-assert
    "node-ref-2"
    (and
      (= (sizeof int8-t) field-data.size)
      (= value-2 (pointer-get (convert-type field-data.data int8-t*)))))
  (set field-data (db-node-ref type (i-array-get-at nodes 0) 3))
  (test-helper-assert
    "node-ref-3" (and (= 5 field-data.size) (= 0 (memcmp value-4 field-data.data field-data.size))))
  (i-array-clear ids)
  (i-array-clear nodes)
  (i-array-add ids 9999)
  (set status (db-node-get txn ids &nodes))
  (test-helper-assert "node-get non-existing" (= db-status-id-notfound status.id))
  (set status.id status-id-success)
  (db-txn-abort &txn)
  (label exit
    (db-txn-abort-if-active txn)
    (return status)))

(define (node-matcher type node matcher-state) (boolean db-type-t* db-node-t void*)
  (set (pointer-get (convert-type matcher-state uint8-t*)) 1)
  (return #t))

(define (test-node-select env) (status-t db-env-t*)
  status-declare
  (db-txn-declare env txn)
  (i-array-declare ids db-ids-t)
  (i-array-declare nodes db-nodes-t)
  (db-node-selection-declare selection)
  (declare
    value-1 uint8-t
    matcher-state uint8-t
    node-value db-node-value-t
    btree-size-before-delete uint32-t
    btree-size-after-delete uint32-t
    type db-type-t*
    values db-node-values-t*
    node-ids db-id-t*
    node-ids-len uint32-t
    values-len uint32-t)
  (sc-comment "create nodes")
  (status-require (test-helper-create-type-1 env &type))
  (status-require (test-helper-create-values-1 env type &values &values-len))
  (status-require (test-helper-create-nodes-1 env values &node-ids &node-ids-len))
  (set value-1
    (pointer-get
      (convert-type (struct-get (array-get (struct-get (array-get values 0) data) 0) data) uint8-t*)))
  (status-require (db-txn-begin &txn))
  (sc-comment "type")
  (status-require (db-nodes-new 4 &nodes))
  (status-require (db-node-select txn type 0 0 0 &selection))
  (status-require (db-node-read selection 1 &nodes))
  (test-helper-assert "node-read size" (= 1 (i-array-length nodes)))
  (set node-value (db-node-ref type (i-array-get nodes) 0))
  (test-helper-assert "node-ref size" (= 1 node-value.size))
  (test-helper-assert
    "node-ref value" (= value-1 (pointer-get (convert-type node-value.data uint8-t*))))
  (test-helper-assert "current id set" (db-id-element (struct-get (i-array-get nodes) id)))
  (status-require (db-node-read selection 1 &nodes))
  (status-require (db-node-read selection 1 &nodes))
  (set status (db-node-read selection 1 &nodes))
  (test-helper-assert
    "all type entries found" (and (= db-status-id-notfound status.id) (= 4 (i-array-length nodes))))
  (set status.id status-id-success)
  (db-node-selection-finish &selection)
  (sc-comment "matcher")
  (i-array-clear nodes)
  (set matcher-state 0)
  (status-require (db-node-select txn type 0 node-matcher &matcher-state &selection))
  (status-require (db-node-read selection 1 &nodes))
  (set node-value (db-node-ref type (i-array-get nodes) 0))
  (test-helper-assert "node-ref size" (= 1 node-value.size))
  (test-helper-assert "matcher-state" (= 1 matcher-state))
  (db-node-selection-finish &selection)
  (sc-comment "type and skip")
  (status-require (db-node-select txn type 0 0 0 &selection))
  (status-require (db-node-skip selection 3))
  (set status (db-node-read selection 1 &nodes))
  (test-helper-assert "entries skipped" (= db-status-id-notfound status.id))
  (set status.id status-id-success)
  (db-node-selection-finish &selection)
  (db-txn-abort &txn)
  (status-require (db-txn-write-begin &txn))
  (db-debug-count-all-btree-entries txn &btree-size-before-delete)
  (status-require (db-node-update txn (array-get node-ids 1) (array-get values 1)))
  (status-require (db-ids-new 4 &ids))
  (i-array-add ids (array-get node-ids 0))
  (i-array-add ids (array-get node-ids 2))
  (status-require (db-node-delete txn ids))
  (status-require (db-txn-commit &txn))
  (status-require (db-txn-begin &txn))
  (db-debug-count-all-btree-entries txn &btree-size-after-delete)
  (db-txn-abort &txn)
  (test-helper-assert "after size" (= 2 (- btree-size-before-delete btree-size-after-delete)))
  (label exit
    (i-array-free ids)
    (db-txn-abort-if-active txn)
    (return status)))

(define (test-helper-dbi-entry-count txn dbi result) (status-t db-txn-t MDB-dbi size-t*)
  status-declare
  (declare stat MDB-stat)
  (db-mdb-status-require (mdb-stat txn.mdb-txn dbi &stat))
  (set *result stat.ms_entries)
  (label exit
    (return status)))

#;(define (test-nested-transaction env) (status-t db-env-t*)
  "wip. -30782 MDB_BAD_TXN: Transaction must abort, has a child, or is invalid"
  status-declare
  (declare
    t1 MDB-txn*
    t2 MDB-txn*)
  (db-mdb-status-require (mdb-txn-begin env:mdb-env 0 0 &t1))
  (debug-log "%d" 0)
  (db-mdb-status-require (mdb-txn-begin env:mdb-env t1 0 &t2))
  (debug-log "%d" 1)
  ;(status-require (db-txn-write-begin &parent))
  ;(status-require (db-txn-write-begin-child parent &child))
  ;(status-require (db-txn-commit &child))
  ;(status-require (db-txn-commit &parent))
  (label exit
    (return status)))

(define (test-node-virtual env) (status-t db-env-t*)
  "float data currently not implemented because it is unknown how to store it in the id"
  status-declare
  (test-helper-assert
    "configured sizes" (>= (- (sizeof db-id-t) (sizeof db-type-id-t)) (sizeof float)))
  (declare
    type db-type-t*
    id db-id-t
    data-int int8-t
    data-uint uint8-t
    data-float32 float)
  (set
    data-uint 123
    data-int -123
    data-float32 1.23)
  (declare fields (array db-field-t 1))
  (db-field-set (array-get fields 0) db-field-type-int8 0 0)
  (status-require (db-type-create env "test-type-v" fields 1 db-type-flag-virtual &type))
  (test-helper-assert "is-virtual" (db-type-is-virtual type))
  (sc-comment "int")
  (set id (db-node-virtual-from-int type:id data-int))
  (test-helper-assert "is-virtual int" (db-node-is-virtual env id))
  (test-helper-assert "type-id int" (= type:id (db-id-type id)))
  (test-helper-assert "data int" (= data-int (db-node-virtual-data id int8-t)))
  (sc-comment "uint")
  (set id (db-node-virtual-from-uint type:id data-uint))
  (test-helper-assert "is-virtual uint" (db-node-is-virtual env id))
  (test-helper-assert "type-id uint" (= type:id (db-id-type id)))
  (test-helper-assert "data uint" (= data-uint (db-node-virtual-data id uint8-t)))
  (sc-comment "float")
  (set id (db-node-virtual-from-any type:id &data-float32 (sizeof float)))
  (test-helper-assert "is-virtual float" (db-node-is-virtual env id))
  (test-helper-assert "type-id float" (= type:id (db-id-type id)))
  (test-helper-assert "data float" (= data-float32 (db-node-virtual-data id float)))
  (label exit
    (return status)))

(define (test-index env) (status-t db-env-t*)
  status-declare
  (db-txn-declare env txn)
  (i-array-declare ids db-ids-t)
  (db-index-selection-declare selection)
  (declare
    fields (array db-fields-len-t 2 1 2)
    fields-len db-fields-len-t
    type db-type-t*
    index db-index-t*
    index-name uint8-t*
    index-name-len size-t
    values db-node-values-t*
    values-len uint32-t
    key-data void*
    key-size size-t
    node-ids db-id-t*
    node-ids-len uint32-t)
  (define index-name-expected uint8-t* "i-1-1-2")
  (set fields-len 2)
  (status-require (test-helper-create-type-1 env &type))
  (status-require (test-helper-create-values-1 env type &values &values-len))
  (sc-comment "test with no existing nodes")
  (status-require (db-index-name type:id fields fields-len &index-name &index-name-len))
  (test-helper-assert "index name" (= 0 (strcmp index-name-expected index-name)))
  (status-require (db-index-create env type fields fields-len))
  (set index (db-index-get type fields fields-len))
  (test-helper-assert "index-get not null" index)
  (test-helper-assert "index-get fields-len" (= fields-len index:fields-len))
  (test-helper-assert
    "index-get fields set" (and (= 1 (array-get index:fields 0)) (= 2 (array-get index:fields 1))))
  (status-require (db-index-key env *index (array-get values 0) &key-data &key-size))
  (test-helper-assert "key size" (= 4 key-size))
  (test-helper-assert "key memory ref" (array-get (convert-type key-data uint8-t*) 3))
  (sc-comment "test node index update")
  (status-require (test-helper-create-nodes-1 env values &node-ids &node-ids-len))
  (sc-comment "test delete")
  (status-require (db-index-delete env index))
  (test-helper-assert "index-delete" (not (db-index-get type fields fields-len)))
  (sc-comment "test with existing nodes")
  (status-require (db-index-create env type fields fields-len))
  (sc-comment "this call exposed a memory error before")
  (status-require (db-index-name type:id fields fields-len &index-name &index-name-len))
  (set index (db-index-get type fields fields-len))
  (test-helper-assert "index-get not null 2" index)
  (sc-comment "test index select")
  (status-require (db-txn-begin &txn))
  (status-require (db-index-select txn *index (array-get values 1) &selection))
  (db-ids-new 4 &ids)
  (status-require-read (db-index-read selection 2 &ids))
  (test-helper-assert "index-read ids length" (= 2 (i-array-length ids)))
  (test-helper-assert "index-select type-id 1" (= type:id (db-id-type (i-array-get-at ids 0))))
  (test-helper-assert "index-select type-id 2" (= type:id (db-id-type (i-array-get-at ids 1))))
  (test-helper-assert "index-select next end" (= db-status-id-notfound status.id))
  (set status.id status-id-success)
  (db-index-selection-finish &selection)
  (db-txn-abort &txn)
  (status-require (db-txn-begin &txn))
  (status-require (db-index-rebuild env index))
  (status-require (db-index-select txn *index (array-get values 0) &selection))
  (i-array-clear ids)
  (status-require-read (db-index-read selection 1 &ids))
  (test-helper-assert
    "index-select type-id 1"
    (and (= 1 (i-array-length ids)) (= type:id (db-id-type (i-array-get ids)))))
  (db-txn-abort &txn)
  (status-require (db-txn-begin &txn))
  (db-node-index-selection-declare node-index-selection)
  (status-require (db-node-index-select txn *index (array-get values 0) &node-index-selection))
  (db-node-index-selection-finish &node-index-selection)
  (db-txn-abort &txn)
  (label exit
    (db-txn-abort-if-active txn)
    (return status)))

(define (main) int
  (declare env db-env-t*)
  status-declare
  (db-env-new &env)
  (test-helper-test-one test-graph-read env)
  (test-helper-test-one test-id-construction env)
  (test-helper-test-one test-node-virtual env)
  (test-helper-test-one test-open-empty env)
  (test-helper-test-one test-statistics env)
  (test-helper-test-one test-sequence env)
  (test-helper-test-one test-type-create-get-delete env)
  (test-helper-test-one test-type-create-many env)
  (test-helper-test-one test-open-nonempty env)
  (test-helper-test-one test-graph-delete env)
  (test-helper-test-one test-node-create env)
  (test-helper-test-one test-node-select env)
  (test-helper-test-one test-index env)
  (label exit
    (if status-is-success (printf "--\ntests finished successfully.\n")
      (printf "\ntests failed. %d %s\n" status.id (db-status-description status)))
    (return status.id)))