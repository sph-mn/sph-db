(pre-include "./helper.c")

(sc-comment
  "these values should not be below 3, or important cases would not be tested.
   the values should also not be so high that the linearly created ordinals exceed the size of the ordinal type.
   tip: reduce when debugging to make tests run faster")

(define common-element-count ui32 3)
(define common-label-count ui32 3)
(pre-define db-env-types-extra-count 20)

(define (test-open-empty env) (status-t db-env-t*)
  status-declare
  (test-helper-assert "env.open is true" (= #t env:open))
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
    name (array ui8 255)
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
  (test-helper-graph-read-setup
    env common-element-count common-element-count common-label-count &data)
  (db-txn-begin &txn)
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
    (return status)))

(define (test-graph-delete env) (status-t db-env-t*)
  "some assertions depend on the correctness of graph-read"
  test-helper-graph-delete-header
  (test-helper-graph-delete-one 1 0 0 0)
  (test-helper-graph-delete-one 1 0 1 0)
  (test-helper-graph-delete-one 1 1 0 0)
  (test-helper-graph-delete-one 1 1 1 0)
  (test-helper-graph-delete-one 0 0 1 0)
  (test-helper-graph-delete-one 0 1 0 0)
  (test-helper-graph-delete-one 0 1 1 0)
  (test-helper-graph-delete-one 1 0 0 1)
  (test-helper-graph-delete-one 1 0 1 1)
  (test-helper-graph-delete-one 1 1 0 1)
  (test-helper-graph-delete-one 1 1 1 1)
  test-helper-graph-delete-footer)

(define (test-node-create env) (status-t db-env-t*)
  status-declare
  ; todo: setting to big data for node value. add many nodes
  (db-txn-declare env txn)
  (declare
    field-data db-node-data-t
    field-index db-fields-len-t
    ids db-ids-t
    id-1 db-id-t
    id-2 db-id-t
    node-data-1 db-node-data-t
    node-data-2 db-node-data-t
    size-1 size-t
    size-2 size-t
    exists boolean
    type db-type-t*
    value-1 ui8
    value-2 i8
    values-1 db-node-values-t
    values-2 db-node-values-t)
  (define value-3 ui8* (convert-type "abc" ui8*))
  (define value-4 ui8* (convert-type "abcde" ui8*))
  (status-require (test-helper-create-type-1 env &type))
  (sc-comment "prepare node values")
  (status-require (db-node-values-new type &values-1))
  (set
    value-1 11
    value-2 -128)
  (db-node-values-set &values-1 0 &value-1 0)
  (db-node-values-set &values-1 1 &value-2 0)
  (db-node-values-set &values-1 2 value-3 3)
  (db-node-values-set &values-1 3 value-4 5)
  (sc-comment "test node values/data conversion")
  (db-node-values->data values-1 &node-data-1)
  (test-helper-assert
    "node-values->data size" (= (+ (* 2 (sizeof db-data-len-t)) 10) node-data-1.size))
  (db-node-data->values type node-data-1 &values-2)
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
    (and
      (= 0 (memcmp value-3 (struct-get (array-get values-1.data 2) data) 3))
      (= 0 (memcmp value-4 (struct-get (array-get values-1.data 3) data) 5))))
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
  (db-node-values->data values-2 &node-data-2)
  (test-helper-assert
    "node-values->data"
    (and
      (= node-data-1.size node-data-2.size)
      (=
        0
        (memcmp
          node-data-1.data
          node-data-2.data
          (if* (< node-data-1.size node-data-2.size) node-data-2.size
            node-data-1.size)))))
  (sc-comment "test node-data-ref")
  (set field-data (db-node-data-ref type node-data-1 3))
  (test-helper-assert
    "node-data-ref-1"
    (and (= 5 field-data.size) (= 0 (memcmp value-4 field-data.data field-data.size))))
  (sc-comment "test actual node creation")
  (status-require (db-txn-write-begin &txn))
  (status-require (db-node-create txn values-1 &id-1))
  (test-helper-assert "element id 1" (= 1 (db-id-element id-1)))
  (status-require (db-node-create txn values-1 &id-2))
  (test-helper-assert "element id 2" (= 2 (db-id-element id-2)))
  (status-require (db-txn-commit &txn))
  (status-require (db-txn-begin &txn))
  (sc-comment "test node-get")
  (status-require (db-node-get txn id-1 &node-data-1))
  (set field-data (db-node-data-ref type node-data-1 1))
  (test-helper-assert
    "node-data-ref-2"
    (and
      (= (sizeof i8) field-data.size) (= value-2 (pointer-get (convert-type field-data.data i8*)))))
  (set field-data (db-node-data-ref type node-data-1 3))
  (test-helper-assert
    "node-data-ref-3"
    (and (= 5 field-data.size) (= 0 (memcmp value-4 field-data.data field-data.size))))
  (status-require (db-node-get txn id-2 &node-data-1))
  (set status (db-node-get txn 9999 &node-data-1))
  (test-helper-assert "node-get non-existing" (= db-status-id-notfound status.id))
  (set status.id status-id-success)
  (sc-comment "test node-exists")
  (i-array-allocate-db-ids-t &ids 3)
  (i-array-add ids id-1)
  (i-array-add ids id-2)
  (status-require (db-node-exists txn ids &exists))
  (test-helper-assert "node-exists exists" exists)
  (i-array-add ids 9999)
  (status-require (db-node-exists txn ids &exists))
  (test-helper-assert "node-exists does not exist" (not exists))
  (db-txn-abort &txn)
  (label exit
    (db-txn-abort-if-active txn)
    (return status)))

(define (node-matcher id data matcher-state) (boolean db-id-t db-node-data-t void*)
  (set (pointer-get (convert-type matcher-state ui8*)) 1)
  (return #t))

(define (test-node-select env) (status-t db-env-t*)
  status-declare
  (db-txn-declare env txn)
  (i-array-declare ids db-ids-t)
  (declare
    value-1 ui8
    matcher-state ui8
    data db-node-data-t
    selection db-node-selection-t
    btree-size-before-delete ui32
    btree-size-after-delete ui32
    type db-type-t*
    values db-node-values-t*
    node-ids db-id-t*
    node-ids-len ui32
    values-len ui32)
  (sc-comment "create nodes")
  (status-require (test-helper-create-type-1 env &type))
  (status-require (test-helper-create-values-1 env type &values &values-len))
  (status-require (test-helper-create-nodes-1 env values &node-ids &node-ids-len))
  (set value-1
    (pointer-get
      (convert-type (struct-get (array-get (struct-get (array-get values 0) data) 0) data) ui8*)))
  (status-require (db-txn-begin &txn))
  (sc-comment "type")
  (status-require (db-node-select txn 0 type 0 0 0 &selection))
  (status-require (db-node-next &selection))
  (set data (db-node-ref &selection 0))
  (test-helper-assert "node-ref size" (= 1 data.size))
  (test-helper-assert "node-ref value" (= value-1 (pointer-get (convert-type data.data ui8*))))
  (test-helper-assert "current id set" (db-id-element selection.current-id))
  (status-require (db-node-next &selection))
  (status-require (db-node-next &selection))
  (set status (db-node-next &selection))
  (test-helper-assert "all type entries found" (= db-status-id-notfound status.id))
  (set status.id status-id-success)
  (db-node-selection-destroy &selection)
  (sc-comment "ids")
  (if (not (i-array-allocate-db-ids-t &ids 5)) (status-set-id-goto db-status-id-memory))
  (i-array-add ids (array-get node-ids 0))
  (i-array-add ids 9999)
  (i-array-add ids (array-get node-ids 1))
  (i-array-add ids (array-get node-ids 2))
  (i-array-add ids (array-get node-ids 3))
  (status-require (db-node-select txn &ids 0 0 0 0 &selection))
  (status-require (db-node-next &selection))
  (set data (db-node-ref &selection 3))
  (db-node-selection-destroy &selection)
  (sc-comment "matcher")
  (set matcher-state 0)
  (status-require (db-node-select txn 0 type 0 node-matcher &matcher-state &selection))
  (status-require (db-node-next &selection))
  (set data (db-node-ref &selection 0))
  (test-helper-assert "node-ref size" (= 1 data.size))
  (test-helper-assert "matcher-state" (= 1 matcher-state))
  (db-node-selection-destroy &selection)
  (sc-comment "type and skip")
  (status-require (db-node-select txn 0 type 0 0 0 &selection))
  (status-require (db-node-skip &selection 2))
  (status-require (db-node-next &selection))
  (set status (db-node-next &selection))
  (test-helper-assert "entries skipped" (= db-status-id-notfound status.id))
  (set status.id status-id-success)
  (db-node-selection-destroy &selection)
  (db-txn-abort &txn)
  (status-require (db-txn-write-begin &txn))
  (db-debug-count-all-btree-entries txn &btree-size-before-delete)
  (status-require (db-node-update txn (array-get node-ids 1) (array-get values 1)))
  (status-require (db-node-delete txn &ids))
  (status-require (db-txn-commit &txn))
  (status-require (db-txn-begin &txn))
  (db-debug-count-all-btree-entries txn &btree-size-after-delete)
  (db-txn-abort &txn)
  (test-helper-assert "after size" (= 4 (- btree-size-before-delete btree-size-after-delete)))
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

(define (test-index env) (status-t db-env-t*)
  status-declare
  (db-txn-declare env txn)
  (declare
    fields (array db-fields-len-t 2 1 2)
    fields-len db-fields-len-t
    type db-type-t*
    index db-index-t*
    index-name ui8*
    index-name-len size-t
    values db-node-values-t*
    values-len ui32
    key-data void*
    key-size size-t
    node-ids db-id-t*
    node-ids-len ui32
    selection db-index-selection-t)
  (define index-name-expected ui8* "i-1-1-2")
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
  (test-helper-assert "key memory ref" (array-get (convert-type key-data ui8*) 3))
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
  (db-txn-begin &txn)
  (status-require (db-index-select txn *index (array-get values 1) &selection))
  (test-helper-assert "index-select type-id 1" (= type:id (db-id-type selection.current)))
  (status-require (db-index-next selection))
  (test-helper-assert "index-select type-id 2" (= type:id (db-id-type selection.current)))
  (set status (db-index-next selection))
  (test-helper-assert "index-select next end" (= db-status-id-notfound status.id))
  (set status.id status-id-success)
  (db-index-selection-destroy &selection)
  (db-txn-abort &txn)
  (db-txn-begin &txn)
  (status-require (db-index-rebuild env index))
  (status-require (db-index-select txn *index (array-get values 0) &selection))
  (test-helper-assert "index-select type-id 1" (= type:id (db-id-type selection.current)))
  (db-txn-abort &txn)
  (db-txn-begin &txn)
  (declare node-index-selection db-node-index-selection-t)
  (status-require (db-node-index-select txn *index (array-get values 0) &node-index-selection))
  (db-node-index-selection-destroy &node-index-selection)
  (db-txn-abort &txn)
  (label exit
    (db-txn-abort-if-active txn)
    (return status)))

(define (main) int
  (declare env db-env-t*)
  (test-helper-init env)
  ;(test-helper-test-one test-open-empty env)
  ;(test-helper-test-one test-statistics env)
  ;(test-helper-test-one test-id-construction env)
  ;(test-helper-test-one test-sequence env)
  ;(test-helper-test-one test-type-create-get-delete env)
  ;(test-helper-test-one test-type-create-many env)
  ;(test-helper-test-one test-open-nonempty env)
  ;(test-helper-test-one test-graph-read env)
  (test-helper-test-one test-graph-delete env)
  ;(test-helper-test-one test-node-create env)
  ;(test-helper-test-one test-node-select env)
  ;(test-helper-test-one test-index env)
  (label exit
    test-helper-report-status
    (return status.id)))