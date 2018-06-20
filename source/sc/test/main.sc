(pre-include "./helper.c")

(pre-define (db-field-set a a-type a-name a-name-len)
  (set
    a.type a-type
    a.name a-name
    a.name-len a-name-len))

(sc-comment
  "these values should not be below 3, or important cases would not be tested.
   the values should also not be so high that the linearly created ordinals exceed the size of the ordinal type.
   tip: reduce when debugging to make tests run faster")

(define common-element-count b32 3)
(define common-label-count b32 3)

(define (test-open-empty env) (status-t db-env-t*)
  status-init
  (test-helper-assert "env.open is true" (= #t env:open))
  (test-helper-assert "env.root is set" (= 0 (strcmp env:root test-helper-db-root)))
  (label exit
    (return status)))

(define (test-statistics env) (status-t db-env-t*)
  status-init
  (declare stat db-statistics-t)
  (db-txn-declare env txn)
  (db-txn-begin txn)
  (status-require! (db-statistics txn (address-of stat)))
  (test-helper-assert "dbi-system contanis only one entry" (= 1 stat.system.ms_entries))
  (label exit
    (db-txn-abort-if-active txn)
    (return status)))

(define (test-type-create-get-delete env) (status-t db-env-t*)
  status-init
  (declare
    fields (array db-field-t 3)
    fields-2 (array db-field-t* 3)
    i db-field-count-t
    type-1 db-type-t*
    type-2 db-type-t*
    type-1-1 db-type-t*
    type-2-1 db-type-t*)
  (sc-comment "type 1")
  (status-require! (db-type-create env "test-type-1" 0 0 0 &type-1))
  (test-helper-assert "type id" (= 1 type-1:id))
  (test-helper-assert "type sequence" (= 1 type-1:sequence))
  (test-helper-assert "type field count" (= 0 type-1:fields-len))
  (sc-comment "type 2")
  (db-field-set (array-get fields 0) db-field-type-int8 "test-field-1" 12)
  (db-field-set (array-get fields 1) db-field-type-int8 "test-field-2" 12)
  (db-field-set (array-get fields 2) db-field-type-string "test-field-3" 12)
  (status-require! (db-type-create env "test-type-2" fields 3 0 &type-2))
  (test-helper-assert "second type id" (= 2 type-2:id))
  (test-helper-assert "second type sequence" (= 1 type-2:sequence))
  (test-helper-assert "second type field-count" (= 3 type-2:fields-len))
  (test-helper-assert "second type name" (= 0 (strcmp "test-type-2" type-2:name)))
  (for ((set i 0) (< i type-2:fields-len) (set i (+ 1 i)))
    (test-helper-assert
      "second type field name len equality"
      (= (: (+ i fields) name-len) (: (+ i type-2:fields) name-len)))
    (test-helper-assert
      "second type field name equality"
      (= 0 (strcmp (: (+ i fields) name) (: (+ i type-2:fields) name))))
    (test-helper-assert
      "second type type equality" (= (: (+ i fields) type) (: (+ i type-2:fields) type))))
  (array-set
    fields-2
    0
    (db-type-field-get type-2 "test-field-1")
    1 (db-type-field-get type-2 "test-field-2") 2 (db-type-field-get type-2 "test-field-3"))
  (test-helper-assert
    "type-field-get"
    (=
      (array-get fields-2 0)
      (+ 0 type-2:fields)
      (array-get fields-2 1) (+ 1 type-2:fields) (array-get fields-2 2) (+ 2 type-2:fields)))
  (sc-comment "type-get")
  (test-helper-assert "non existent type" (not (db-type-get env "test-type-x")))
  (set
    type-1-1 (db-type-get env "test-type-1")
    type-2-1 (db-type-get env "test-type-2"))
  (test-helper-assert "existent types" (and type-1-1 type-2-1))
  (test-helper-assert "existent type ids" (and (= type-1:id type-1-1:id) (= type-2:id type-2-1:id)))
  (test-helper-assert
    "existent types" (and (db-type-get env "test-type-1") (db-type-get env "test-type-2")))
  (sc-comment "type-delete")
  (status-require! (db-type-delete env type-1:id))
  (status-require! (db-type-delete env type-2:id))
  (set
    type-1-1 (db-type-get env "test-type-1")
    type-2-1 (db-type-get env "test-type-2"))
  (test-helper-assert "type-delete type-get" (not (or type-1-1 type-2-1)))
  (label exit
    (return status)))

(define (test-type-create-many env) (status-t db-env-t*)
  "create several types, particularly to test automatic env:types array resizing"
  status-init
  (declare
    i db-type-id-t
    name (array b8 255)
    type db-type-t*)
  (sc-comment "10 times as many as there is extra room left for new types in env:types")
  (for ((set i 0) (< i (* 10 db-env-types-extra-count)) (set i (+ 1 i)))
    (sprintf name "test-type-%lu" i)
    (status-require! (db-type-create env name 0 0 0 &type)))
  (label exit
    (return status)))

(define (test-sequence env) (status-t db-env-t*)
  status-init
  (declare
    i size-t
    id db-id-t
    prev-id db-id-t
    prev-type-id db-type-id-t
    type db-type-t*
    type-id db-type-id-t)
  (sc-comment "node sequence. note that sequences only persist through data inserts")
  (status-require! (db-type-create env "test-type" 0 0 0 &type))
  (set
    type:sequence (- db-element-id-limit 100)
    prev-id (db-id-add-type (- db-element-id-limit 100 1) type:id))
  (for ((set i db-element-id-limit) (<= i db-element-id-limit) (set i (+ i 1)))
    (set status (db-sequence-next env type:id &id))
    (if (<= db-element-id-limit (db-id-element (+ 1 prev-id)))
      (begin
        (test-helper-assert "node sequence is limited" (= db-status-id-max-element-id status.id))
        (status-set-id status-id-success))
      (test-helper-assert
        "node sequence is monotonically increasing" (and status-success? (= 1 (- id prev-id)))))
    (set prev-id id))
  (sc-comment "system sequence. test last, otherwise type ids would be exhausted")
  (set prev-type-id type:id)
  (for ((set i type:id) (<= i db-type-id-limit) (set i (+ i 1)))
    (set status (db-sequence-next-system env &type-id))
    (if (<= db-type-id-limit (+ 1 prev-type-id))
      (begin
        (test-helper-assert "system sequence is limited" (= db-status-id-max-type-id status.id))
        (status-set-id status-id-success))
      (begin
        (test-helper-assert
          "system sequence is monotonically increasing"
          (and status-success? (= 1 (- type-id prev-type-id))))))
    (set prev-type-id type-id))
  (label exit
    (return status)))

(define (test-open-nonempty env) (status-t db-env-t*)
  status-init
  (status-require! (test-type-create-get-delete env))
  (status-require! (test-helper-reset env #t))
  (label exit
    (return status)))

(define (test-id-construction env) (status-t db-env-t*)
  "test features related to the combination of element and type id to node id"
  status-init
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

(define (test-helper-create-type-1 env result) (status-t db-env-t* db-type-t**)
  "create a new type with three fields for testing"
  status-init
  (declare fields (array db-field-t 3))
  (db-field-set (array-get fields 0) db-field-type-int8 "test-field-1" 12)
  (db-field-set (array-get fields 1) db-field-type-int8 "test-field-2" 12)
  (db-field-set (array-get fields 2) db-field-type-string "test-field-3" 12)
  (status-require! (db-type-create env "test-type-1" fields 3 0 result))
  (label exit
    (return status)))

(define (test-node-create env) (status-t db-env-t*)
  status-init
  ; todo: setting to big data for node value. add many nodes
  (db-txn-declare env txn)
  (declare
    type db-type-t*
    values db-node-value-t*
    ;fields (array db-field-t 4)
    value-1 b8
    value-2 b8
    id db-id-t
    )
  (define value-3 b8* "abc")
  (status-require! (test-helper-create-type-1 env &type))
  (status-require! (db-node-values-new type &values))
  (set
    value-1 11
    value-2 128)
  (db-node-values-set values 0 &value-1 0)
  (db-node-values-set values 1 &value-2 0)
  (db-node-values-set values 2 &value-3 3)
  (db-txn-write-begin txn)
  (status-require! (db-node-create txn type values &id))
  (db-txn-commit txn)
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
  status-init
  (define ids db-ids-t*)
  (db-define-ids-3 left right label)
  (status-require!
    (test-helper-create-relations txn
      common-label-count
      common-element-count common-label-count &left &right &label))
  (status-require! (test-helper-create-interns common-element-count &ids))
  db-txn-introduce
  (status-require! (db-index-recreate-intern))
  (status-require! (db-index-recreate-extern))
  ;(status-require! (db-index-recreate-graph))
  (define index-errors-extern db-index-errors-extern-t)
  (define index-errors-intern db-index-errors-intern-t)
  (define index-errors-graph db-index-errors-graph-t)
  db-txn-begin
  (status-require! (db-index-errors-intern db-txn &index-errors-intern))
  (status-require! (db-index-errors-extern db-txn &index-errors-extern))
  (status-require! (db-index-errors-graph db-txn &index-errors-graph))
  (test-helper-assert "errors-intern?" (not (struct-get index-errors-intern errors?)))
  (test-helper-assert "errors-extern?" (not (struct-get index-errors-extern errors?)))
  (test-helper-assert "errors-graph?" (not (struct-get index-errors-graph errors?)))
  (label exit
    (if db-txn db-txn-abort)
    (return status)))

(define (test-node-read) status-t
  status-init
  (define ids-intern db-ids-t* 0)
  (define ids-id db-ids-t* 0)
  (status-require! (test-helper-create-interns common-element-count &ids-intern))
  (status-require! (test-helper-create-ids txn common-element-count &ids-id))
  db-txn-introduce
  db-txn-begin
  (define state db-node-read-state-t)
  (status-require! (db-node-select db-txn 0 0 &state))
  (define records db-data-records-t* 0)
  (db-status-require-read! (db-node-read &state 0 &records))
  (db-node-selection-destroy &state)
  (test-helper-assert
    "result length" (= (db-data-records-length records) (* 2 common-element-count)))
  (db-data-records-destroy records)
  ; with type filter
  (set records 0)
  (status-require! (db-node-select db-txn 1 0 &state))
  (db-status-require-read! (db-node-read &state 0 &records))
  (db-node-selection-destroy &state)
  (test-helper-assert
    "result length with type filter" (= (db-data-records-length records) common-element-count))
  (db-data-records-destroy records)
  (label exit
    (if db-txn db-txn-abort)
    db-status-success-if-no-more-data
    (return status)))

(define (test-concurrent-write/read-thread status-pointer) (b0* b0*)
  status-init
  (set status (pointer-get (convert-type status-pointer status-t*)))
  (define state db-graph-read-state-t)
  (define records db-graph-records-t* 0)
  db-txn-introduce
  db-txn-begin
  (set records 0)
  (status-require! (db-graph-select db-txn 0 0 0 0 0 &state))
  (db-status-require-read! (db-graph-read &state 2 &records))
  (db-status-require-read! (db-graph-read &state 0 &records))
  db-txn-abort
  (label exit
    db-status-success-if-no-more-data
    (set (pointer-get (convert-type status-pointer status-t*)) status)))

(define (test-concurrent-write/read) status-t
  status-init
  (define
    thread-two pthread_t
    thread-three pthread_t)
  (status-require! (test-helper-db-reset #f))
  (db-define-ids-3 left right label)
  (status-require!
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