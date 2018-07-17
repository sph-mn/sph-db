(pre-include "./helper.c")

(sc-comment
  "these values should not be below 3, or important cases would not be tested.
   the values should also not be so high that the linearly created ordinals exceed the size of the ordinal type.
   tip: reduce when debugging to make tests run faster")

(define common-element-count ui32 3)
(define common-label-count ui32 3)

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
  (db-txn-begin txn)
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
    "type-id-size + element-id-size = id-size" (= db-size-id (+ db-size-type-id db-size-element-id)))
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
  (test-helper-graph-read-header env)
  (test-helper-graph-read-one txn left 0 0 0 0)
  (test-helper-graph-read-one txn left 0 label 0 0)
  (test-helper-graph-read-one txn left right 0 0 0)
  (test-helper-graph-read-one txn left right label 0 0)
  (test-helper-graph-read-one txn 0 0 0 0 0)
  (test-helper-graph-read-one txn 0 0 label 0 0)
  (test-helper-graph-read-one txn 0 right 0 0 0)
  (test-helper-graph-read-one txn 0 right label 0 0)
  (test-helper-graph-read-one txn left 0 0 ordinal 0)
  (test-helper-graph-read-one txn left 0 label ordinal 0)
  (test-helper-graph-read-one txn left right 0 ordinal 0)
  (test-helper-graph-read-one txn left right label ordinal 0)
  test-helper-graph-read-footer)

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

(define (debug-display-array-ui8 a size) (void ui8* size-t)
  (declare i size-t)
  (for ((set i 0) (< i size) (set i (+ 1 i)))
    (printf "%lu " (array-get a i))))

(define (test-node-create env) (status-t db-env-t*)
  status-declare
  ; todo: setting to big data for node value. add many nodes
  (db-txn-declare env txn)
  (declare
    type db-type-t*
    values-1 db-node-values-t
    values-2 db-node-values-t
    ;fields (array db-field-t 4)
    value-1 ui8
    value-2 i8
    id-1 db-id-t
    id-2 db-id-t
    node-data-1 db-node-data-t
    node-data-2 db-node-data-t
    field-data db-node-data-t)
  (define value-3 ui8* "abc")
  (status-require (test-helper-create-type-1 env &type))
  (sc-comment "prepare values")
  (status-require (db-node-values-new type &values-1))
  (set
    value-1 11
    value-2 -128)
  (db-node-values-set &values-1 0 &value-1 0)
  (db-node-values-set &values-1 1 &value-2 0)
  (db-node-values-set &values-1 2 &value-3 3)
  (sc-comment "node values/data conversion")
  (db-node-values->data values-1 &node-data-1)
  (test-helper-assert "node-values->data size" (= (+ (sizeof db-data-len-t) 5) node-data-1.size))
  (db-node-data->values type node-data-1 &values-2)
  (test-helper-assert "node-data->values type equal" (= values-1.type values-2.type))
  (debug-log "%lu" (struct-get (array-get values-1.data 0) size))
  (debug-log "%lu" (struct-get (array-get values-1.data 1) size))
  (debug-log "%lu" (struct-get (array-get values-1.data 2) size))
  (debug-log "%lu" (struct-get (array-get values-2.data 0) size))
  (debug-log "%lu" (struct-get (array-get values-2.data 1) size))
  (debug-log "%lu" (struct-get (array-get values-2.data 2) size))
  (test-helper-assert
    "node-data->values size equal"
    (and
      (=
        (struct-get (array-get values-1.data 0) size) (struct-get (array-get values-2.data 0) size))
      (=
        (struct-get (array-get values-1.data 1) size) (struct-get (array-get values-2.data 1) size))
      (=
        (struct-get (array-get values-1.data 1) size) (struct-get (array-get values-2.data 1) size))))
  (test-helper-assert
    "node-data->values data equal"
    (= 0 (memcmp values-1.data values-2.data (* type:fields-len (sizeof db-node-value-t)))))
  (db-node-values->data values-2 &node-data-2)
  (debug-log "node-data sizes %lu %lu" node-data-1.size node-data-2.size)
  (test-helper-assert
    "node-values->data"
    (and
      (= node-data-1.size node-data-2.size)
      (= 0 (memcmp node-data-1.data node-data-2.data node-data-1.size))))
  (db-txn-write-begin txn)
  (status-require (db-node-create txn values-1 &id-1))
  (test-helper-assert "element id 1" (= 1 (db-id-element id-1)))
  (status-require (db-node-create txn values-1 &id-2))
  (test-helper-assert "element id 2" (= 2 (db-id-element id-2)))
  (db-txn-commit txn)
  (db-txn-begin txn)
  (status-require (db-node-get txn id-1 &node-data-1))
  (set field-data (db-node-data-ref type node-data-1 1))
  (test-helper-assert
    "node-data-ref"
    (and
      (= (sizeof i8) field-data.size) (= value-2 (pointer-get (convert-type field-data.data i8*)))))
  ;(status-require (db-node-get txn id-2 &data &size))
  ;(set status (db-node-get txn 9999 &data &size))
  ;(debug-log "status id %d" status.id)
  (db-txn-abort txn)
  #;(
  (db-node-select txn ids type offset matcher matcher-state result-state)
  (status-t db-txn-t db-ids-t* db-type-t* db-count-t db-node-matcher-t void* db-node-selection-t*)
  (db-node-skip state count) (status-t db-node-selection-t* db-count-t)
  (db-node-next state) (status-t db-node-selection-t*)
  (db-node-delete txn ids) (status-t db-txn-t db-ids-t*)
  (db-node-update txn id values) (status-t db-txn-t db-id-t db-node-values-t)
  (db-node-selection-destroy state) (void db-node-selection-t*)
  (db-node-exists txn ids result) (status-t db-txn-t db-ids-t* boolean*)
  )
  (label exit
    (db-txn-abort-if-active txn)
    (return status)))

(define (main) int
  (test-helper-init env)
  ;(test-helper-test-one test-open-empty env)
  ;(test-helper-test-one test-statistics env)
  ;(test-helper-test-one test-id-construction env)
  ;(test-helper-test-one test-sequence env)
  ;(test-helper-test-one test-type-create-get-delete env)
  ;(test-helper-test-one test-type-create-many env)
  ;(test-helper-test-one test-open-nonempty env)
  ;(test-helper-test-one test-graph-read env)
  ;(test-helper-test-one test-graph-delete env)
  (test-helper-test-one test-node-create env)
  (label exit
    test-helper-report-status
    (return status.id)))

#;(
(define (test-index) status-t
  status-declare
  (define ids db-ids-t*)
  (db-define-ids-3 left right label)
  (status-require
    (test-helper-create-relations txn
      common-label-count
      common-element-count common-label-count &left &right &label))
  (status-require (test-helper-create-interns common-element-count &ids))
  db-txn-introduce
  (status-require (db-index-recreate-intern))
  (status-require (db-index-recreate-extern))
  ;(status-require (db-index-recreate-graph))
  (define index-errors-extern db-index-errors-extern-t)
  (define index-errors-intern db-index-errors-intern-t)
  (define index-errors-graph db-index-errors-graph-t)
  db-txn-begin
  (status-require (db-index-errors-intern db-txn &index-errors-intern))
  (status-require (db-index-errors-extern db-txn &index-errors-extern))
  (status-require (db-index-errors-graph db-txn &index-errors-graph))
  (test-helper-assert "errors-intern?" (not (struct-get index-errors-intern errors?)))
  (test-helper-assert "errors-extern?" (not (struct-get index-errors-extern errors?)))
  (test-helper-assert "errors-graph?" (not (struct-get index-errors-graph errors?)))
  (label exit
    (if db-txn db-txn-abort)
    (return status)))

(define (test-node-read) status-t
  status-declare
  (define ids-intern db-ids-t* 0)
  (define ids-id db-ids-t* 0)
  (status-require (test-helper-create-interns common-element-count &ids-intern))
  (status-require (test-helper-create-ids txn common-element-count &ids-id))
  db-txn-introduce
  db-txn-begin
  (define state db-node-read-state-t)
  (status-require (db-node-select db-txn 0 0 &state))
  (define records db-data-records-t* 0)
  (db-status-require-read! (db-node-read &state 0 &records))
  (db-node-selection-destroy &state)
  (test-helper-assert
    "result length" (= (db-data-records-length records) (* 2 common-element-count)))
  (db-data-records-destroy records)
  ; with type filter
  (set records 0)
  (status-require (db-node-select db-txn 1 0 &state))
  (db-status-require-read! (db-node-read &state 0 &records))
  (db-node-selection-destroy &state)
  (test-helper-assert
    "result length with type filter" (= (db-data-records-length records) common-element-count))
  (db-data-records-destroy records)
  (label exit
    (if db-txn db-txn-abort)
    db-status-success-if-no-more-data
    (return status)))

(define (test-concurrent-write/read-thread status-pointer) (void* void*)
  status-declare
  (set status (pointer-get (convert-type status-pointer status-t*)))
  (define state db-graph-read-state-t)
  (define records db-graph-records-t* 0)
  db-txn-introduce
  db-txn-begin
  (set records 0)
  (status-require (db-graph-select db-txn 0 0 0 0 0 &state))
  (db-status-require-read! (db-graph-read &state 2 &records))
  (db-status-require-read! (db-graph-read &state 0 &records))
  db-txn-abort
  (label exit
    db-status-success-if-no-more-data
    (set (pointer-get (convert-type status-pointer status-t*)) status)))

(define (test-concurrent-write/read) status-t
  status-declare
  (define
    thread-two pthread_t
    thread-three pthread_t)
  (status-require (test-helper-db-reset #f))
  (db-define-ids-3 left right label)
  (status-require
    (test-helper-create-relations txn
      common-element-count
      common-element-count common-label-count &left &right &label))
  (define thread-two-result status-t (struct-literal 0 0))
  (define thread-three-result status-t (struct-literal 0 0))
  (if
    (pthread-create
      &thread-two 0 test-concurrent-write/read-thread &thread-two-result)
    (begin
      (printf "error creating thread")
      (status-set-id-goto 1)))
  (if
    (pthread-create
      &thread-three 0 test-concurrent-write/read-thread &thread-three-result)
    (begin
      (printf "error creating thread")
      (status-set-id-goto 1)))
  (test-concurrent-write/read-thread &status)
  status-require
  (if (pthread-join thread-two 0)
    (begin
      (printf "error joining thread")
      (status-set-id-goto 2)))
  (if (pthread-join thread-three 0)
    (begin
      (printf "error joining thread")
      (status-set-id-goto 2)))
  (set status thread-two-result)
  status-require
  (set status thread-three-result)
  (label exit
    (return status)))
  )