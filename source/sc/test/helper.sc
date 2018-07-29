(pre-include "stdio.h" "stdlib.h" "errno.h" "pthread.h" "../main/sph-db.h" "../foreign/sph/one.c")

(pre-define
  test-helper-db-root "/tmp/test-sph-db"
  test-helper-path-data (pre-string-concat test-helper-db-root "/data")
  (set-plus-one a) (set a (+ 1 a))
  (set-minus-one a) (set a (- a 1)))

(pre-define (test-helper-init env-name)
  (begin
    status-declare
    (db-env-define env-name)))

(pre-define test-helper-report-status
  (if status-is-success (printf "--\ntests finished successfully.\n")
    (printf "\ntests failed. %d %s\n" status.id (db-status-description status))))

(pre-define (test-helper-test-one func env)
  (begin
    (printf "%s\n" (pre-stringify func))
    (status-require (test-helper-reset env #f))
    (status-require (func env))))

(pre-define (test-helper-assert description expression)
  (if (not expression)
    (begin
      (printf "%s failed\n" description)
      (status-set-id-goto 1))))

(pre-define (db-field-set a a-type a-name a-name-len)
  (set
    a.type a-type
    a.name a-name
    a.name-len a-name-len))

(define (test-helper-reset env re-use) (status-t db-env-t* boolean)
  status-declare
  (if env:open (db-close env))
  (if (and (not re-use) (file-exists? test-helper-path-data))
    (begin
      (set status.id (system (pre-string-concat "rm " test-helper-path-data)))
      (if status-is-failure status-goto)))
  (status-require (db-open test-helper-db-root 0 env))
  (label exit
    (return status)))

(define (test-helper-print-binary-ui64 a) (void ui64)
  (declare
    i size-t
    result (array ui8 65))
  (set (pointer-get (+ 64 result)) 0)
  (for ((set i 0) (< i 64) (set i (+ 1 i)))
    (set (pointer-get (+ i result))
      (if* (bit-and (bit-shift-left (convert-type 1 ui64) i) a) #\1
        #\0)))
  (printf "%s\n" result))

(define (db-ids-contains ids id) (boolean db-ids-t* db-id-t)
  (while ids
    (if (= id (db-ids-first ids)) (return #t))
    (set ids (db-ids-rest ids)))
  (return #f))

(define (db-ids-reverse a result) (status-t db-ids-t* db-ids-t**)
  status-declare
  (declare ids-temp db-ids-t*)
  (set ids-temp 0)
  (while a
    (set ids-temp (db-ids-add ids-temp (db-ids-first a)))
    (if (not ids-temp) (db-status-set-id-goto db-status-id-memory))
    (set a (db-ids-rest a)))
  (set *result ids-temp)
  (label exit
    (return status)))

(pre-define (db-debug-define-graph-records-contains-at field)
  (define ((pre-concat db-debug-graph-records-contains-at_ field _p) records id)
    (boolean db-graph-records-t* db-id-t)
    (while records
      (if (= id (struct-get (db-graph-records-first records) field)) (return #t))
      (set records (db-graph-records-rest records)))
    (return #f)))

(db-debug-define-graph-records-contains-at left)
(db-debug-define-graph-records-contains-at right)
(db-debug-define-graph-records-contains-at label)

(define (test-helper-create-type-1 env result) (status-t db-env-t* db-type-t**)
  "create a new type with four fields, fixed and variable length, for testing"
  status-declare
  (declare fields (array db-field-t 4))
  (db-field-set (array-get fields 0) db-field-type-uint8 "test-field-1" 12)
  (db-field-set (array-get fields 1) db-field-type-int8 "test-field-2" 12)
  (db-field-set (array-get fields 2) db-field-type-string "test-field-3" 12)
  (db-field-set (array-get fields 3) db-field-type-string "test-field-4" 12)
  (status-require (db-type-create env "test-type-1" fields 4 0 result))
  (label exit
    (return status)))

(define (test-helper-create-values-1 env type result-values result-values-len)
  (status-t db-env-t* db-type-t* db-node-values-t** ui32*)
  "create multiple node-values"
  status-declare
  (declare
    value-1 ui8*
    value-2 i8*
    value-3 ui8*
    value-4 ui8*
    values db-node-values-t*)
  (db-malloc value-1 1)
  (db-malloc value-2 1)
  (db-malloc values (* 2 (sizeof db-node-values-t)))
  (set
    *value-1 11
    *value-2 -128)
  (db-malloc-string value-3 3)
  (db-malloc-string value-4 5)
  (memcpy value-3 (address-of "abc") 3)
  (memcpy value-4 (address-of "abcde") 5)
  (status-require (db-node-values-new type (+ 0 values)))
  (status-require (db-node-values-new type (+ 1 values)))
  (db-node-values-set (+ 0 values) 0 value-1 0)
  (db-node-values-set (+ 0 values) 1 value-2 0)
  (db-node-values-set (+ 0 values) 2 value-3 3)
  (db-node-values-set (+ 0 values) 3 value-4 5)
  (db-node-values-set (+ 1 values) 0 value-1 0)
  (db-node-values-set (+ 1 values) 1 value-1 0)
  (db-node-values-set (+ 1 values) 2 value-3 3)
  (set
    *result-values-len 4
    *result-values values)
  (label exit
    (return status)))

(define (test-helper-create-nodes-1 env values result-ids result-len)
  (status-t db-env-t* db-node-values-t* db-id-t** ui32*)
  "creates several nodes for given values"
  status-declare
  (db-txn-declare env txn)
  (declare ids db-id-t*)
  (db-malloc ids (* 4 (sizeof db-id-t)))
  (status-require (db-txn-write-begin &txn))
  (status-require (db-node-create txn (array-get values 0) (+ 0 ids)))
  (status-require (db-node-create txn (array-get values 0) (+ 1 ids)))
  (status-require (db-node-create txn (array-get values 1) (+ 2 ids)))
  (status-require (db-node-create txn (array-get values 1) (+ 3 ids)))
  (status-require (db-txn-commit &txn))
  (set
    *result-ids ids
    *result-len 4)
  (label exit
    (return status)))

(define (test-helper-create-ids txn count result) (status-t db-txn-t ui32 db-ids-t**)
  "create only ids, without nodes. doesnt depend on node creation.
  dont reverse id list because it leads to more unorderly data which can expose bugs
  especially with relation reading where order lead to lucky success results"
  status-declare
  (db-declare-ids ids-temp)
  (declare id db-id-t)
  (while count
    (sc-comment
      "use type id zero - normally not valid for nodes but it works"
      " and for tests it keeps the ids small numbers")
    (status-require (db-sequence-next txn.env 0 &id))
    (set ids-temp (db-ids-add ids-temp id))
    (if (not ids-temp) (status-set-id-goto db-status-id-memory))
    (set count (- count 1)))
  ;(status-require (db-ids-reverse ids-temp result))
  (set *result ids-temp)
  (label exit
    (return status)))

(define (test-helper-ids-add-new-ids txn ids-old result) (status-t db-txn-t db-ids-t* db-ids-t**)
  "add newly created ids to the list.
   create as many elements as there are in ids-old. add them with interleaved overlap at half of ids-old
   approximately like this: 1 1 1 1 + 2 2 2 2 -> 1 1 2 1 2 1 2 2"
  status-declare
  (db-declare-ids ids-new)
  (declare
    target-count ui32
    start-mixed ui32
    start-new ui32
    count ui32)
  (set *result 0)
  (status-require (test-helper-create-ids txn (db-ids-length ids-old) (address-of ids-new)))
  (set
    target-count (* 2 (db-ids-length ids-old))
    start-mixed (/ target-count 4)
    start-new (- target-count start-mixed))
  (for ((set count 0) (< count target-count) (set count (+ 1 count)))
    (if (< count start-mixed)
      (begin
        (set
          *result (db-ids-add *result (db-ids-first ids-old))
          ids-old (db-ids-rest ids-old)))
      (if (< count start-new)
        (if (bit-and 1 count)
          (begin
            (set
              *result (db-ids-add *result (db-ids-first ids-old))
              ids-old (db-ids-rest ids-old)))
          (begin
            (set
              *result (db-ids-add *result (db-ids-first ids-new))
              ids-new (db-ids-rest ids-new))))
        (begin
          (set
            *result (db-ids-add *result (db-ids-first ids-new))
            ids-new (db-ids-rest ids-new))))))
  (label exit
    (return status)))

(define (test-helper-calculate-relation-count left-count right-count label-count)
  (ui32 ui32 ui32 ui32) (return (* left-count right-count label-count)))

(define (test-helper-calculate-relation-count-from-ids left right label)
  (ui32 db-ids-t* db-ids-t* db-ids-t*)
  (return
    (test-helper-calculate-relation-count
      (db-ids-length left) (db-ids-length right) (db-ids-length label))))

(pre-define (test-helper-graph-read-records-validate-one name)
  (begin
    "test that the result records contain all filter-ids, and the filter-ids contain all result record values for field \"name\"."
    (set records-temp records)
    (while records-temp
      (if
        (not
          (db-ids-contains
            (pre-concat existing_ name) (struct-get (db-graph-records-first records-temp) name)))
        (begin
          (printf "\n  result records contain inexistant %s ids\n" (pre-stringify name))
          (db-debug-display-graph-records records)
          (status-set-id-goto 1)))
      (set records-temp (db-graph-records-rest records-temp)))
    (set ids-temp (pre-concat existing_ name))
    (while ids-temp
      (if
        (not
          ((pre-concat db-debug-graph-records-contains-at_ name _p) records (db-ids-first ids-temp)))
        (begin
          (printf "\n  %s result records do not contain all existing-ids\n" (pre-stringify name))
          (db-debug-display-graph-records records)
          (status-set-id-goto 2)))
      (set ids-temp (db-ids-rest ids-temp)))))

(define
  (test-helper-graph-read-records-validate
    records left existing-left right existing-right label existing-label ordinal)
  (status-t
    db-graph-records-t*
    db-ids-t* db-ids-t* db-ids-t* db-ids-t* db-ids-t* db-ids-t* db-ordinal-condition-t*)
  status-declare
  (declare
    records-temp db-graph-records-t*
    ids-temp db-ids-t*)
  (test-helper-graph-read-records-validate-one left)
  (test-helper-graph-read-records-validate-one right)
  (test-helper-graph-read-records-validate-one label)
  (label exit
    (return status)))

(define (test-helper-default-ordinal-generator state) (db-ordinal-t void*)
  (define ordinal-pointer db-ordinal-t* state)
  (define result db-ordinal-t (+ 1 (pointer-get ordinal-pointer)))
  (set (pointer-get ordinal-pointer) result)
  (return result))

(define (test-helper-create-relations txn left right label)
  (status-t db-txn-t db-ids-t* db-ids-t* db-ids-t*)
  "create relations with linearly increasing ordinal starting from zero"
  status-declare
  (declare ordinal-state-value db-ordinal-t)
  (set ordinal-state-value 0)
  (status-require
    (db-graph-ensure
      txn left right label test-helper-default-ordinal-generator (address-of ordinal-state-value)))
  (label exit
    (return status)))

(pre-define (test-helper-graph-read-one txn left right label ordinal offset)
  (begin
    (set
      reader-suffix (test-helper-filter-ids->reader-suffix-integer left right label ordinal)
      reader-suffix-string (test-helper-reader-suffix-integer->string reader-suffix))
    (printf " %s" reader-suffix-string)
    (free reader-suffix-string)
    (set records 0)
    (status-require (db-graph-select txn left right label ordinal offset (address-of state)))
    (db-status-require-read (db-graph-read (address-of state) 2 (address-of records)))
    (db-status-require-read (db-graph-read (address-of state) 0 (address-of records)))
    (if (= status.id db-status-id-no-more-data) (set status.id status-id-success)
      (begin
        (printf "\n  final read result does not indicate that there is no more data")
        (status-set-id-goto 1)))
    (set expected-count
      (test-helper-estimate-graph-read-result-count
        existing-left-count existing-right-count existing-label-count ordinal))
    (if (not (= (db-graph-records-length records) expected-count))
      (begin
        (printf
          "\n  expected %lu read %lu. ordinal min %d max %d\n"
          expected-count
          (db-graph-records-length records)
          (if* ordinal ordinal-min
            0)
          (if* ordinal ordinal-max
            0))
        (printf "read ")
        (db-debug-display-graph-records records)
        (test-helper-display-all-relations txn)
        (status-set-id-goto 1)))
    (if (not ordinal)
      (status-require
        (test-helper-graph-read-records-validate
          records left existing-left right existing-right label existing-label ordinal)))
    db-status-success-if-no-more-data
    (db-graph-selection-destroy (address-of state))
    (db-graph-records-destroy records)))

(pre-define (test-helper-graph-read-header env)
  (begin
    status-declare
    (db-txn-declare env txn)
    (db-declare-ids-three existing-left existing-right existing-label)
    (db-declare-ids-three left right label)
    (declare
      state db-graph-selection-t
      ordinal-min ui32
      ordinal-max ui32
      ordinal-condition db-ordinal-condition-t
      ordinal db-ordinal-condition-t*
      existing-left-count ui32
      existing-right-count ui32
      existing-label-count ui32
      records db-graph-records-t*
      expected-count ui32
      reader-suffix ui8
      reader-suffix-string ui8*)
    (set
      ordinal-min 2
      ordinal-max 5
      ordinal-condition.min ordinal-min
      ordinal-condition.max ordinal-max
      ordinal &ordinal-condition
      records 0
      existing-left-count common-label-count
      existing-right-count common-element-count
      existing-label-count common-label-count)
    (status-require (db-txn-write-begin &txn))
    (test-helper-create-ids txn existing-left-count &existing-left)
    (test-helper-create-ids txn existing-right-count &existing-right)
    (test-helper-create-ids txn existing-label-count &existing-label)
    (status-require (test-helper-create-relations txn existing-left existing-right existing-label))
    (sc-comment "add ids that do not exist anywhere in the graph")
    (status-require (test-helper-ids-add-new-ids txn existing-left &left))
    (status-require (test-helper-ids-add-new-ids txn existing-right &right))
    (status-require (test-helper-ids-add-new-ids txn existing-label &label))
    (printf " ")))

(pre-define test-helper-graph-read-footer
  (begin
    db-status-success-if-no-more-data
    (label exit
      (printf "\n")
      (db-txn-abort-if-active txn)
      (return status))))

(pre-define (test-helper-filter-ids->reader-suffix-integer left right label ordinal)
  (bit-or
    (if* left 8
      0)
    (if* right 4
      0)
    (if* label 2
      0)
    (if* ordinal 1
      0)))

(define (test-helper-display-all-relations txn) (status-t db-txn-t)
  status-declare
  (declare
    records db-graph-records-t*
    state db-graph-selection-t)
  (set records 0)
  (db-status-require-read (db-graph-select txn 0 0 0 0 0 (address-of state)))
  (db-status-require-read (db-graph-read (address-of state) 0 (address-of records)))
  (printf "all ")
  (db-graph-selection-destroy (address-of state))
  (db-debug-display-graph-records records)
  (db-graph-records-destroy records)
  (label exit
    (return status)))

(define (test-helper-estimate-graph-read-result-count left-count right-count label-count ordinal)
  (ui32 ui32 ui32 ui32 db-ordinal-condition-t*)
  "assumes linearly set-plus-oneed ordinal integers starting at 1 and queries for all or no ids"
  (define count ui32 (* left-count right-count label-count))
  (declare
    max ui32
    min ui32)
  (if ordinal
    (begin
      (set
        min
        (if* (struct-pointer-get ordinal min) (- (struct-pointer-get ordinal min) 1)
          0)
        max (struct-pointer-get ordinal max))
      (if* (> max count) (set max count)))
    (set
      min 0
      max count))
  (return (- count min (- count max))))

(define
  (test-helper-estimate-graph-read-btree-entry-count
    existing-left-count existing-right-count existing-label-count ordinal)
  (ui32 ui32 ui32 ui32 db-ordinal-condition-t*)
  "calculates the number of btree entries affected by a relation read or delete.
   assumes linearly set-plus-oneed ordinal integers starting at 1 and queries for all or no ids"
  (define ordinal-min ui32 0)
  (define ordinal-max ui32 0)
  (if ordinal
    (set
      ordinal-min (struct-pointer-get ordinal min)
      ordinal-max (struct-pointer-get ordinal max)))
  (define label-left-count ui32 0)
  (define left-right-count ui32 0)
  (define right-left-count ui32 0)
  ;test relation ordinals currently start at one
  (define ordinal-value ui32 1)
  (define left-count ui32 0)
  (define right-count ui32 0)
  (define label-count ui32 0)
  (sc-comment
    "the number of relations is not proportional to the number of entries in graph-ll.
    use a process similar to relation creation to correctly calculate graph-ll and ordinal dependent entries")
  (while (< label-count existing-label-count)
    (while (< left-count existing-left-count)
      (if (and (<= ordinal-value ordinal-max) (>= ordinal-value ordinal-min))
        (set-plus-one label-left-count))
      (while (< right-count existing-right-count)
        (if (and (<= ordinal-value ordinal-max) (>= ordinal-value ordinal-min))
          (begin
            (set-plus-one ordinal-value)
            (set-plus-one left-right-count)
            (set-plus-one right-left-count)))
        (set-plus-one right-count))
      (set-plus-one left-count))
    (set-plus-one label-count))
  (return (+ left-right-count right-left-count label-left-count)))

(pre-define test-helper-graph-delete-header
  (begin
    status-declare
    (db-declare-ids-three left right label)
    (db-txn-declare env txn)
    (declare
      state db-graph-selection-t
      read-count-before-expected ui32
      btree-count-after-delete ui32
      btree-count-before-create ui32
      btree-count-deleted-expected ui32
      records db-graph-records-t*
      ordinal db-ordinal-condition-t*
      existing-left-count ui32
      existing-right-count ui32
      existing-label-count ui32)
    (define ordinal-condition db-ordinal-condition-t (struct-literal 2 5))
    (set
      records 0
      ordinal &ordinal-condition
      existing-left-count common-label-count
      existing-right-count common-element-count
      existing-label-count common-label-count)
    (printf " ")))

(pre-define (test-helper-graph-delete-one left? right? label? ordinal?)
  (begin
    "for any given argument permutation:
     * checks btree entry count difference
     * checks read result count after deletion, using the same search query
    relations are assumed to be created with linearly incremented ordinals starting with 1"
    (printf " %d%d%d%d" left? right? label? ordinal?)
    (set
      read-count-before-expected
      (test-helper-estimate-graph-read-result-count
        existing-left-count existing-right-count existing-label-count ordinal)
      btree-count-deleted-expected
      (test-helper-estimate-graph-read-btree-entry-count
        existing-left-count existing-right-count existing-label-count ordinal))
    (status-require (db-txn-write-begin &txn))
    (test-helper-create-ids txn existing-left-count &left)
    (test-helper-create-ids txn existing-right-count &right)
    (test-helper-create-ids txn existing-label-count &label)
    (db-debug-count-all-btree-entries txn &btree-count-before-create)
    (status-require (test-helper-create-relations txn left right label))
    (status-require (db-txn-commit &txn))
    (status-require (db-txn-write-begin &txn))
    (sc-comment "delete")
    (status-require
      (db-graph-delete
        txn
        (if* left? left
          0)
        (if* right? right
          0)
        (if* label? label
          0)
        (if* ordinal? ordinal
          0)))
    (status-require (db-txn-commit &txn))
    (status-require (db-txn-begin &txn))
    (db-debug-count-all-btree-entries txn &btree-count-after-delete)
    (db-status-require-read
      (db-graph-select
        txn
        (if* left? left
          0)
        (if* right? right
          0)
        (if* label? label
          0)
        (if* ordinal? ordinal
          0)
        0 &state))
    (sc-comment "check that readers can handle empty selections")
    (db-status-require-read (db-graph-read &state 0 &records))
    (db-graph-selection-destroy &state)
    (db-txn-abort &txn)
    (if (not (= 0 (db-graph-records-length records)))
      (begin
        (printf
          "\n    failed deletion. %lu relations not deleted\n" (db-graph-records-length records))
        (db-debug-display-graph-records records)
        ;(status-require (db-txn-begin &txn))
        ;(test-helper-display-all-relations txn)
        ;(db-txn-abort &txn)
        (status-set-id-goto 1)))
    (db-graph-records-destroy records)
    (set records 0)
    (sc-comment
      "test only if not using ordinal condition because the expected counts arent estimated")
    (if (not (or ordinal? (= btree-count-after-delete btree-count-before-create)))
      (begin
        (printf
          "\n failed deletion. %lu btree entries not deleted\n"
          (- btree-count-after-delete btree-count-before-create))
        (status-require (db-txn-begin &txn))
        (db-debug-display-btree-counts txn)
        (db-status-require-read (db-graph-select txn 0 0 0 0 0 &state))
        (db-status-require-read (db-graph-read &state 0 &records))
        (printf "all remaining ")
        (db-debug-display-graph-records records)
        (db-graph-selection-destroy &state)
        (db-txn-abort &txn)
        (status-set-id-goto 1)))
    (db-ids-destroy left)
    (db-ids-destroy right)
    (db-ids-destroy label)
    db-status-success-if-no-more-data
    (set
      records 0
      left 0
      right 0
      label 0)))

(pre-define test-helper-graph-delete-footer
  (label exit
    (db-txn-abort-if-active txn)
    (printf "\n")
    (return status)))

(define (test-helper-reader-suffix-integer->string a) (ui8* ui8)
  "1101 -> \"1101\""
  (define result ui8* (malloc 40))
  (array-set
    result
    0
    (if* (bit-and 8 a) #\1
      #\0)
    1
    (if* (bit-and 4 a) #\1
      #\0)
    2
    (if* (bit-and 2 a) #\1
      #\0)
    3
    (if* (bit-and 1 a) #\1
      #\0)
    4 0)
  (return result))

#;(define (test-helper-create-interns count result) (status-t ui32 db-ids-t**)
  status-declare
  (db-txn-declare env txn)
  (declare
    data-element db-data-t
    data-list db-data-list-t*)
  (set
    data-element (struct-literal (size db-size-id) (data 0))
    data-list 0)
  (while count
    (struct-set data-element
      data (calloc 1 (sizeof db-ids-t)))
    (set (pointer-get (convert-type (struct-get data-element data) db-id-t*)) (+ 123 count))
    (set data-list (db-data-list-add data-list data-element))
    (set count (- count 1)))
  (status-require (db-txn-write-begin &txn))
  ;(status-require (db-intern-ensure db-txn data-list result))
  (status-require (db-txn-commit &txn))
  (label exit
    (db-txn-abort-if-active txn)
    (return status)))