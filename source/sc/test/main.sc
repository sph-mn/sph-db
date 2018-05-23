(pre-include "./helper.c")

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
  (test-helper-assert "dbi-system has one entry" (= 1 stat.system.ms_entries))
  (label exit
    (db-txn-abort txn)
    (return status)))

(define (test-type-create env) (status-t db-env-t*)
  status-init
  (declare fields (array dg-field-t 3)  type-id db-type-id-t)
  (status-require! (db-type-create env "test-type-2" 0 0 0 &type-id))
  (test-helper-assert "first type id" (= 1 type-id))
  (test-helper-assert "type sequence" (= 2 env:types:sequence))
  (test-helper-assert "type cache id" (= type-id (: (+ type-id env:types) id)))

  ; (db-type-create env name field-count fields flags result)
  (set
    fields:type (db-field-type-integer #f )
    fields:name
    fields:name-len

    )
  (status-require! (db-type-create env "test-type-2" 3 fields 0 &type-id))
  (label exit
    (return status)))

(define (test-open-nonempty env) (status-t db-env-t*)
  status-init
  (test-type-create env)
  (test-helper-reset env #t)
  (label exit
    (return status)))

(define (main) int
  (test-helper-init env)
  ;(test-helper-test-one env test-open-empty)
  (test-helper-test-one env test-type-create)
  ;(test-helper-test-one env test-open-nonempty)
  ;(test-helper-test-one env test-statistics)
  #;(
  (test-helper-test-one test-concurrent-write/read)
  (test-helper-test-one test-relation-read)
  (test-helper-test-one test-relation-delete)
  )
  (label exit
    test-helper-report-status
    (return status.id)))

#;(
; these values should not be below 3, or important cases would not be tested.
; the values should also not be so high that the linearly created ordinals exceed the size of the ordinal type.
; tip: reduce for debugging
(define common-element-count b32 40)
(define common-label-count b32 40)

(pre-define (test-relation-read-records-validate-one name)
  ;test that the result records contain all filter-ids, and the filter-ids contain all result record values for field "name".
  (set records-temp records)
  (while records-temp
    (if
      (not
        (db-ids-contains?
          (pre-concat existing_ name) (struct-get (db-relation-records-first records-temp) name)))
      (begin
        (printf "\n  result records contain inexistant %s ids\n" (pre-stringify name))
        (db-debug-display-relation-records records)
        (status-set-id-goto 1)))
    (set records-temp (db-relation-records-rest records-temp)))
  (set ids-temp (pre-concat existing_ name))
  (while ids-temp
    (if
      (not
        ( (pre-concat db-debug-relation-records-contains-at_ name _p)
          records (db-ids-first ids-temp)))
      (begin
        (printf "\n  %s result records do not contain all existing-ids\n" (pre-stringify name))
        (db-debug-display-relation-records records)
        (status-set-id-goto 2)))
    (set ids-temp (db-ids-rest ids-temp))))

(define
  (test-relation-read-records-validate
    records left existing-left right existing-right label existing-label ordinal)
  (status-t
    db-relation-records-t*
    db-ids-t* db-ids-t* db-ids-t* db-ids-t* db-ids-t* db-ids-t* db-ordinal-match-data-t*)
  status-init
  (define
    records-temp db-relation-records-t*
    ids-temp db-ids-t*)
  (test-relation-read-records-validate-one left)
  (test-relation-read-records-validate-one right)
  (test-relation-read-records-validate-one label)
  (label exit
    (return status)))

(pre-define test-relation-read-header
  (begin
    status-init
    (define state db-relation-read-state-t)
    (define ordinal-min b32 2)
    (define ordinal-max b32 5)
    (define ordinal-match-data db-ordinal-match-data-t (struct-literal ordinal-min ordinal-max))
    (define ordinal db-ordinal-match-data-t* (address-of ordinal-match-data))
    (define records db-relation-records-t* 0)
    (define existing-left-count b32 common-label-count)
    (define existing-right-count b32 common-element-count)
    (define existing-label-count b32 common-label-count)
    (define
      expected-count b32
      reader-suffix b8
      reader-suffix-string b8*)
    (db-define-ids-3 existing-left existing-right existing-label)
    (db-define-ids-3 left right label)
    (status-require!
      (test-helper-create-relations
        existing-left-count
        existing-right-count
        existing-label-count
        (address-of existing-left) (address-of existing-right) (address-of existing-label)))
    ;add additional ids that do not exist in any relation
    (status-require! (test-helper-ids-add-new-ids existing-left (address-of left)))
    (status-require! (test-helper-ids-add-new-ids existing-right (address-of right)))
    (status-require! (test-helper-ids-add-new-ids existing-label (address-of label)))
    db-txn-introduce
    db-txn-begin
    (printf " ")))

(pre-define (test-relation-read-one left right label ordinal offset)
  (set reader-suffix (test-helper-filter-ids->reader-suffix-integer left right label ordinal))
  (set reader-suffix-string (test-helper-reader-suffix-integer->string reader-suffix))
  (printf " %s" reader-suffix-string)
  (free reader-suffix-string)
  (set records 0)
  (status-require! (db-relation-select db-txn left right label ordinal offset (address-of state)))
  (db-status-require-read! (db-relation-read (address-of state) 2 (address-of records)))
  (db-status-require-read! (db-relation-read (address-of state) 0 (address-of records)))
  (if (status-id-is? db-status-id-no-more-data)
    (status-set-id status-id-success)
    (begin
      (printf "\n  final read result does not indicate that there is no more data")
      (status-set-id-goto 1)))
  (set expected-count
    (test-helper-estimate-relation-read-result-count
      existing-left-count existing-right-count existing-label-count ordinal))
  (if (not (= (db-relation-records-length records) expected-count))
    (begin
      (printf
        "\n  expected %lu read %lu. ordinal min %d max %d\n"
        expected-count
        (db-relation-records-length records) (if* ordinal ordinal-min 0) (if* ordinal ordinal-max 0))
      (printf "the read ")
      (db-debug-display-relation-records records)
      (db-debug-display-all-relations db-txn)
      (status-set-id-goto 1)))
  (if (not ordinal)
    (status-require!
      (test-relation-read-records-validate
        records left existing-left right existing-right label existing-label ordinal)))
  db-status-success-if-no-more-data
  (db-relation-selection-destroy (address-of state))
  (db-relation-records-destroy records))

(pre-define test-relation-delete-header
  (begin
    status-init
    (define state db-relation-read-state-t)
    (define records db-relation-records-t* 0)
    (db-define-ids-3 left right label)
    (define ordinal-match-data db-ordinal-match-data-t (struct-literal 2 5))
    (define ordinal db-ordinal-match-data-t* (address-of ordinal-match-data))
    (define read-count-before-expected b32)
    (define btree-count-after-delete b32)
    (define existing-left-count b32 common-label-count)
    (define btree-count-before-delete b32)
    (define btree-count-deleted-expected b32)
    (define btree-count-after-expected b32)
    (define existing-right-count b32 common-element-count)
    (define existing-label-count b32 common-label-count)
    db-txn-introduce
    (printf " ")))

(pre-define (test-relation-delete-one left? right? label? ordinal?)
  ; for any given argument permutation:
  ; * checks btree entry count difference
  ; * checks read result count after deletion, using the same search query
  (printf " %d%d%d%d" left? right? label? ordinal?)
  db-txn-begin
  (db-debug-count-all-btree-entries db-txn (address-of btree-count-before-delete))
  db-txn-abort
  ; add non-relation elements
  (status-require!
    (test-helper-create-relations
      common-label-count
      common-element-count common-label-count (address-of left) (address-of right) (address-of label)))
  db-txn-write-begin
  (status-require!
    (db-relation-delete
      db-txn (if* left? left 0) (if* right? right 0) (if* label? label 0) (if* ordinal? ordinal 0)))
  (db-debug-count-all-btree-entries db-txn (address-of btree-count-after-delete))
  (db-status-require-read!
    (db-relation-select
      db-txn
      (if* left? left 0)
      (if* right? right 0) (if* label? label 0) (if* ordinal? ordinal 0) 0 (address-of state)))
  ;checks that readers can handle selections with no elements
  (db-status-require-read! (db-relation-read (address-of state) 0 (address-of records)))
  (db-relation-selection-destroy (address-of state))
  db-txn-commit
  (set read-count-before-expected
    (test-helper-estimate-relation-read-result-count
      existing-left-count existing-right-count existing-label-count ordinal))
  ;relations are assumed to have linearly incremented ordinals starting with 1
  (if (not (= 0 (db-relation-records-length records)))
    (begin
      (printf
        "\n    failed deletion. %lu relations not deleted\n" (db-relation-records-length records))
      (db-debug-display-relation-records records)
      db-txn-begin
      ;(db-debug-display-all-relations db-txn)
      db-txn-abort
      (status-set-id-goto 1)))
  (db-relation-records-destroy records)
  (set records 0)
  (set btree-count-before-delete
    (+ btree-count-before-delete existing-left-count existing-right-count existing-label-count))
  (set btree-count-deleted-expected
    (test-helper-estimate-relation-read-btree-entry-count
      existing-left-count existing-right-count existing-label-count ordinal))
  (set btree-count-after-expected (- btree-count-after-delete btree-count-deleted-expected))
  (if
    (not
      (and
        (= btree-count-after-expected btree-count-after-delete)
        (if* ordinal? #t (= btree-count-after-delete btree-count-before-delete))))
    (begin
      (printf
        "\n    failed deletion. %lu btree entries remaining, expected %lu\n"
        btree-count-after-delete btree-count-after-expected)
      db-txn-begin
      (db-debug-display-btree-counts db-txn)
      (db-status-require-read! (db-relation-select db-txn 0 0 0 0 0 (address-of state)))
      (db-status-require-read! (db-relation-read (address-of state) 0 (address-of records)))
      (printf "all remaining ")
      (db-debug-display-relation-records records)
      (db-relation-selection-destroy (address-of state))
      db-txn-abort
      (status-set-id-goto 1)))
  (db-ids-destroy left)
  (db-ids-destroy right)
  (db-ids-destroy label)
  (set
    records 0
    left 0
    right 0
    label 0))

(define (test-id-create-identify-exists) status-t
  status-init
  (define ids-result db-ids-t* 0)
  db-txn-introduce
  db-txn-write-begin
  (status-require! (db-id-create db-txn 3 (address-of ids-result)))
  (if (not (and ids-result (= 3 (db-ids-length ids-result)))) (status-set-id-goto 1))
  (define boolean-result boolean)
  (status-require! (db-exists? db-txn ids-result (address-of boolean-result)))
  (test-helper-assert "db-exists?" boolean-result)
  (define ids db-ids-t* ids-result)
  (set ids-result 0)
  (status-require! (db-identify db-txn ids (address-of ids-result)))
  (test-helper-assert "result length" (and ids-result (= 3 (db-ids-length ids-result))))
  (label exit
    (if db-txn db-txn-abort)
    (db-ids-destroy ids)
    (db-ids-destroy ids-result)
    (return status)))



(define (test-extern) status-t
  (define data-ids db-ids-t* 0)
  status-init
  db-txn-introduce
  db-txn-write-begin
  (define data-element-value db-id-t 2)
  (define data-element db-data-t
    (struct-literal (size db-size-id) (data (address-of data-element-value))))
  (status-require! (db-extern-create db-txn 50 (address-of data-element) (address-of data-ids)))
  (test-helper-assert "db-extern? 1" (and data-ids (db-extern? (db-ids-first data-ids))))
  (define data-list db-data-list-t* 0)
  (status-require! (db-extern-id->data db-txn data-ids #t (address-of data-list)))
  (test-helper-assert
    "data-equal?"
    (equal?
      (pointer-get (convert-type data-element.data db-id-t*))
      (pointer-get (convert-type (struct-get (db-data-list-first data-list) data) db-id-t*))))
  (define data-ids-2 db-ids-t* 0)
  (db-status-require-read! (db-extern-data->id db-txn data-element (address-of data-ids-2)))
  (test-helper-assert "db-extern? 2" (db-extern? (db-ids-first data-ids)))
  (test-helper-assert "db-extern? 3 " (db-extern? (db-ids-first data-ids-2)))
  db-txn-commit
  (label exit
    (if db-txn db-txn-abort)
    (return status)))

(define (test-intern) status-t
  ; todo: should test more than one data element
  (define data-ids db-ids-t* 0)
  (define data-element-value db-id-t 2)
  (define data-element db-data-t
    (struct-literal (size db-size-id) (data (address-of data-element-value))))
  (define data db-data-list-t* (db-data-list-add 0 data-element))
  status-init
  db-txn-introduce
  db-txn-write-begin
  (status-require! (db-intern-ensure db-txn data (address-of data-ids)))
  (test-helper-assert
    "db-intern-ensure result length" (= (db-ids-length data-ids) (db-data-list-length data)))
  (define id-first db-id-t (db-ids-first data-ids))
  (test-helper-assert "db-intern?" (and data-ids (db-intern? id-first)))
  (define data-2 db-data-list-t* 0)
  (define data-ids-2 db-ids-t* 0)
  (status-require! (db-intern-id->data db-txn data-ids #t (address-of data-2)))
  (test-helper-assert
    "id->data return length" (and data (= (db-ids-length data-ids) (db-data-list-length data-2))))
  (status-require! (db-intern-data->id db-txn data #t (address-of data-ids-2)))
  db-txn-commit
  (test-helper-assert
    "data->id return length" (and data (= (db-ids-length data-ids-2) (db-data-list-length data))))
  db-txn-write-begin
  (set data-element-value 9)
  (status-require! (db-intern-update db-txn id-first data-element))
  (define status-2 status-t (struct-literal 0 0))
  (set status-2 (db-intern-update db-txn id-first data-element))
  (test-helper-assert "duplicate update prevention" (= db-status-id-duplicate status-2.id))
  db-txn-commit
  db-txn-begin
  (define data-ids-3 db-ids-t* (db-ids-add 0 id-first))
  (define data-3 db-data-list-t* 0)
  (status-require! (db-intern-id->data db-txn data-ids-3 #t (address-of data-3)))
  db-txn-abort
  (label exit
    (if db-txn db-txn-abort)
    (db-ids-destroy data-ids)
    (db-ids-destroy data-ids-2)
    (db-data-list-destroy data)
    (db-data-list-destroy data-2)
    (return status)))

(define (test-index) status-t
  status-init
  (define ids db-ids-t*)
  (db-define-ids-3 left right label)
  (status-require!
    (test-helper-create-relations
      common-label-count
      common-element-count common-label-count (address-of left) (address-of right) (address-of label)))
  (status-require! (test-helper-create-interns common-element-count (address-of ids)))
  db-txn-introduce
  (status-require! (db-index-recreate-intern))
  (status-require! (db-index-recreate-extern))
  ;(status-require! (db-index-recreate-relation))
  (define index-errors-extern db-index-errors-extern-t)
  (define index-errors-intern db-index-errors-intern-t)
  (define index-errors-relation db-index-errors-relation-t)
  db-txn-begin
  (status-require! (db-index-errors-intern db-txn (address-of index-errors-intern)))
  (status-require! (db-index-errors-extern db-txn (address-of index-errors-extern)))
  (status-require! (db-index-errors-relation db-txn (address-of index-errors-relation)))
  (test-helper-assert "errors-intern?" (not (struct-get index-errors-intern errors?)))
  (test-helper-assert "errors-extern?" (not (struct-get index-errors-extern errors?)))
  (test-helper-assert "errors-relation?" (not (struct-get index-errors-relation errors?)))
  (label exit
    (if db-txn db-txn-abort)
    (return status)))

(define (test-relation) status-t
  status-init
  (db-define-ids-3 right left label)
  (status-require!
    (test-helper-create-relations
      common-label-count
      common-element-count common-label-count (address-of left) (address-of right) (address-of label)))
  db-txn-introduce
  db-txn-write-begin
  (status-require! (db-relation-ensure db-txn left right label 0 0))
  db-txn-commit
  (label exit
    (if db-txn db-txn-abort)
    (return status)))

(define (test-node-read) status-t
  status-init
  (define ids-intern db-ids-t* 0)
  (define ids-id db-ids-t* 0)
  (status-require! (test-helper-create-interns common-element-count (address-of ids-intern)))
  (status-require! (test-helper-create-ids common-element-count (address-of ids-id)))
  db-txn-introduce
  db-txn-begin
  (define state db-node-read-state-t)
  (status-require! (db-node-select db-txn 0 0 (address-of state)))
  (define records db-data-records-t* 0)
  (db-status-require-read! (db-node-read (address-of state) 0 (address-of records)))
  (db-node-selection-destroy (address-of state))
  (test-helper-assert
    "result length" (= (db-data-records-length records) (* 2 common-element-count)))
  (db-data-records-destroy records)
  ; with type filter
  (set records 0)
  (status-require! (db-node-select db-txn 1 0 (address-of state)))
  (db-status-require-read! (db-node-read (address-of state) 0 (address-of records)))
  (db-node-selection-destroy (address-of state))
  (test-helper-assert
    "result length with type filter" (= (db-data-records-length records) common-element-count))
  (db-data-records-destroy records)
  (label exit
    (if db-txn db-txn-abort)
    db-status-success-if-no-more-data
    (return status)))

(pre-define test-relation-read-body
  (begin
    (test-relation-read-one left 0 0 0 0)
    (test-relation-read-one left 0 label 0 0)
    (test-relation-read-one left right 0 0 0)
    (test-relation-read-one left right label 0 0)
    (test-relation-read-one 0 0 0 0 0)
    (test-relation-read-one 0 0 label 0 0)
    (test-relation-read-one 0 right 0 0 0)
    (test-relation-read-one 0 right label 0 0)
    (test-relation-read-one left 0 0 ordinal 0)
    (test-relation-read-one left 0 label ordinal 0)
    (test-relation-read-one left right 0 ordinal 0)
    (test-relation-read-one left right label ordinal 0)
    db-status-success-if-no-more-data))

(define (test-relation-read) status-t
  test-relation-read-header
  test-relation-read-body
  (label exit
    (printf "\n")
    (if db-txn db-txn-abort)
    (return status)))

(define (test-relation-delete) status-t
  ;the tests depend partly on the correctness of relation-read
  test-relation-delete-header
  (test-relation-delete-one 1 0 0 0)
  (test-relation-delete-one 1 0 1 0)
  (test-relation-delete-one 1 1 0 0)
  (test-relation-delete-one 1 1 1 0)
  (test-relation-delete-one 0 0 1 0)
  (test-relation-delete-one 0 1 0 0)
  (test-relation-delete-one 0 1 1 0)
  (test-relation-delete-one 1 0 0 1)
  (test-relation-delete-one 1 0 1 1)
  (test-relation-delete-one 1 1 0 1)
  (test-relation-delete-one 1 1 1 1)
  (label exit
    (printf "\n")
    (return status)))

(define (test-concurrent-write/read-thread status-pointer) (b0* b0*)
  status-init
  (set status (pointer-get (convert-type status-pointer status-t*)))
  (define state db-relation-read-state-t)
  (define records db-relation-records-t* 0)
  db-txn-introduce
  db-txn-begin
  (set records 0)
  (status-require! (db-relation-select db-txn 0 0 0 0 0 (address-of state)))
  (db-status-require-read! (db-relation-read (address-of state) 2 (address-of records)))
  (db-status-require-read! (db-relation-read (address-of state) 0 (address-of records)))
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
    (test-helper-create-relations
      common-element-count
      common-element-count common-label-count (address-of left) (address-of right) (address-of label)))
  (define thread-two-result status-t (struct-literal 0 0))
  (define thread-three-result status-t (struct-literal 0 0))
  (if
    (pthread-create
      (address-of thread-two) 0 test-concurrent-write/read-thread (address-of thread-two-result))
    (begin
      (printf "error creating thread")
      (status-set-id-goto 1)))
  (if
    (pthread-create
      (address-of thread-three) 0 test-concurrent-write/read-thread (address-of thread-three-result))
    (begin
      (printf "error creating thread")
      (status-set-id-goto 1)))
  (test-concurrent-write/read-thread (address-of status))
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

(define (test-id-creation) status-t
  status-init
  db-txn-introduce
  (define count b32 32)
  (define ids db-ids-t* 0)
  (while count
    db-txn-write-begin
    (db-id-create db-txn 2 (address-of ids))
    db-txn-commit
    ; re-open the database so that the id initialisation is done again
    (test-helper-db-reset #t)
    (set count (- count 1)))
  (define ids-a db-ids-t* ids)
  (define ids-b db-ids-t* 0)
  (define ids-c db-ids-t* ids-b)
  ; check for duplicates
  (while ids-a
    (while ids-c
      (if (= (db-ids-first ids-a) (db-ids-first ids-c)) (status-set-id-goto 1))
      (set ids-c (db-ids-rest ids-c)))
    (set ids-b (db-ids-add ids-b (db-ids-first ids-a)))
    (set ids-c ids-b)
    (set ids-a (db-ids-rest ids-a)))
  (db-ids-destroy ids)
  (label exit
    (return status)))

  )