(pre-include
  "stdio.h"
  "stdlib.h"
  "errno.h"
  "pthread.h" "../main/sph-db.h" "../main/sph-db-extra.h" "../main/lib/lmdb.c" "../foreign/sph/one.c")

(pre-define
  test-helper-db-root "/tmp/test-sph-db"
  test-helper-path-data (pre-string-concat test-helper-db-root "/data"))

(pre-define (test-helper-test-one f env)
  (begin
    (printf "%s\n" (pre-stringify f))
    (status-require (test-helper-reset env #f))
    (status-require (f env))))

(pre-define (test-helper-assert description expression)
  (if (not expression)
    (begin
      (printf "%s failed\n" description)
      (status-set-id-goto 1))))

(pre-define (test-helper-define-relations-contains-at field-name)
  (begin
    "define a function that searches for an id in an array of records at field"
    (define ((pre-concat db-debug-relations-contains-at_ field-name) records id)
      (boolean db-relations-t db-id-t)
      (declare record db-relation-t)
      (while (i-array-in-range records)
        (set record (i-array-get records))
        (if (= id record.field-name) (return #t))
        (i-array-forward records))
      (return #f))))

(pre-define (test-helper-define-relation-get field-name)
  (begin
    "define a function for getting a field from a graph record, to use with a function pointer"
    (define ((pre-concat test-helper-relation-get_ field-name) record)
      (db-id-t db-relation-t) (return record.field-name))))

(declare test-helper-graph-read-data-t
  (type
    (struct
      (txn db-txn-t)
      (records db-relations-t)
      (e-left db-ids-t)
      (e-right db-ids-t)
      (e-label db-ids-t)
      (e-left-count ui32)
      (e-right-count ui32)
      (e-label-count ui32)
      (left db-ids-t)
      (right db-ids-t)
      (label db-ids-t))))

(declare test-helper-graph-delete-data-t
  (type
    (struct
      (env db-env-t*)
      (e-left-count ui32)
      (e-right-count ui32)
      (e-label-count ui32))))

(define
  (test-helper-estimate-graph-read-btree-entry-count
    e-left-count e-right-count e-label-count ordinal)
  (ui32 ui32 ui32 ui32 db-ordinal-condition-t*)
  "calculates the number of btree entries affected by a relation read or delete.
   assumes linearly incremented ordinal integers starting at 1 and queries for all or no ids"
  (define ordinal-min ui32 0)
  (define ordinal-max ui32 0)
  (if ordinal
    (set
      ordinal-min (struct-pointer-get ordinal min)
      ordinal-max (struct-pointer-get ordinal max)))
  (define label-left-count ui32 0)
  (define left-right-count ui32 0)
  (define right-left-count ui32 0)
  ; test-relation-ordinals currently start at one
  (define ordinal-value ui32 1)
  (define left-count ui32 0)
  (define right-count ui32 0)
  (define label-count ui32 0)
  (sc-comment
    "the number of relations is not proportional to the number of entries in graph-ll.
    use a process similar to relation creation to correctly calculate graph-ll and ordinal dependent entries")
  (while (< label-count e-label-count)
    (while (< left-count e-left-count)
      (if (and (<= ordinal-value ordinal-max) (>= ordinal-value ordinal-min))
        (set label-left-count (+ 1 label-left-count)))
      (while (< right-count e-right-count)
        (if (and (<= ordinal-value ordinal-max) (>= ordinal-value ordinal-min))
          (begin
            (set ordinal-value (+ 1 ordinal-value))
            (set left-right-count (+ 1 left-right-count))
            (set right-left-count (+ 1 right-left-count))))
        (set right-count (+ 1 right-count)))
      (set left-count (+ 1 left-count)))
    (set label-count (+ 1 label-count)))
  (return (+ left-right-count right-left-count label-left-count)))

(define (test-helper-display-all-relations txn left-count right-count label-count)
  (status-t db-txn-t ui32 ui32 ui32)
  status-declare
  (declare
    records db-relations-t
    state db-graph-selection-t)
  (i-array-allocate-db-relations-t &records (* left-count right-count label-count))
  (status-require-read (db-graph-select txn 0 0 0 0 0 &state))
  (status-require-read (db-graph-read &state 0 &records))
  (printf "all ")
  (db-graph-selection-finish &state)
  (db-debug-log-relations records)
  (i-array-free records)
  (label exit
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

(test-helper-define-relations-contains-at left)
(test-helper-define-relations-contains-at right)
(test-helper-define-relations-contains-at label)
(test-helper-define-relation-get left)
(test-helper-define-relation-get right)
(test-helper-define-relation-get label)

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

(define (test-helper-display-array-ui8 a size) (void ui8* size-t)
  (declare i size-t)
  (for ((set i 0) (< i size) (set i (+ 1 i)))
    (printf "%lu " (array-get a i)))
  (printf "\n"))

(define (db-ids-contains ids id) (boolean db-ids-t db-id-t)
  (while (i-array-in-range ids)
    (if (= id (i-array-get ids)) (return #t))
    (i-array-forward ids))
  (return #f))

(define (db-ids-reverse a result) (status-t db-ids-t db-ids-t*)
  status-declare
  (declare temp db-ids-t)
  (if (not (i-array-allocate-db-ids-t &temp (i-array-length a)))
    (status-set-id-goto db-status-id-memory))
  (while (i-array-in-range a)
    (i-array-add temp (i-array-get a))
    (i-array-forward a))
  (set *result temp)
  (label exit
    (return status)))

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
  "creates several nodes with the given values"
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

(define (test-helper-create-ids txn count result) (status-t db-txn-t ui32 db-ids-t*)
  "create only ids, without nodes. doesnt depend on node creation.
  especially with relation reading where order lead to lucky success results"
  status-declare
  (declare
    id db-id-t
    result-temp db-ids-t)
  (if (not (i-array-allocate-db-ids-t &result-temp count)) (status-set-id-goto db-status-id-memory))
  (while count
    (sc-comment "use type id zero to have small node ids for testing which are easier to debug")
    (status-require (db-sequence-next txn.env 0 &id))
    (i-array-add result-temp id)
    (set count (- count 1)))
  (status-require (db-ids-reverse result-temp result))
  (label exit
    (i-array-free result-temp)
    (return status)))

(define (test-helper-interleave-ids txn ids-a ids-b result)
  (status-t db-txn-t db-ids-t db-ids-t db-ids-t*)
  "merge ids from two lists into a new list, interleave at half the size of the arrays.
   result is as long as both id lists combined.
   approximately like this: 1 1 1 1 + 2 2 2 2 -> 1 1 2 1 2 1 2 2"
  status-declare
  (i-array-declare ids-result db-ids-t)
  (declare
    target-count ui32
    start-mixed ui32
    start-new ui32
    i ui32)
  (set
    target-count (+ (i-array-length ids-a) (i-array-length ids-b))
    start-mixed (/ target-count 4)
    start-new (- target-count start-mixed))
  (if (not (i-array-allocate-db-ids-t &ids-result target-count))
    (status-set-id-goto db-status-id-memory))
  (for ((set i 0) (< i target-count) (set i (+ 1 i)))
    (if (< i start-mixed)
      (begin
        (i-array-add ids-result (i-array-get ids-a))
        (i-array-forward ids-a))
      (if (< i start-new)
        (if (bit-and 1 i)
          (begin
            (i-array-add ids-result (i-array-get ids-a))
            (i-array-forward ids-a))
          (begin
            (i-array-add ids-result (i-array-get ids-b))
            (i-array-forward ids-b)))
        (begin
          (i-array-add ids-result (i-array-get ids-b))
          (i-array-forward ids-b)))))
  (set *result ids-result)
  (label exit
    (if status-is-failure (i-array-free ids-result))
    (return status)))

(define (test-helper-calculate-relation-count left-count right-count label-count)
  (ui32 ui32 ui32 ui32) (return (* left-count right-count label-count)))

(define (test-helper-calculate-relation-count-from-ids left right label)
  (ui32 db-ids-t db-ids-t db-ids-t)
  (return
    (test-helper-calculate-relation-count
      (i-array-length left) (i-array-length right) (i-array-length label))))

(define (test-helper-graph-read-records-validate-one name e-ids records)
  (status-t ui8* db-ids-t db-relations-t)
  "test if the result records contain all filter-ids,
  and the filter-ids contain all result record values for field \"name\"."
  status-declare
  (declare
    contains-at (function-pointer boolean db-relations-t db-id-t)
    record-get (function-pointer db-id-t db-relation-t))
  (cond
    ( (= 0 (strcmp "left" name))
      (set
        contains-at db-debug-relations-contains-at-left
        record-get test-helper-relation-get-left))
    ( (= 0 (strcmp "right" name))
      (set
        contains-at db-debug-relations-contains-at-right
        record-get test-helper-relation-get-right))
    ( (= 0 (strcmp "label" name))
      (set
        contains-at db-debug-relations-contains-at-label
        record-get test-helper-relation-get-label)))
  (while (i-array-in-range records)
    (if (not (db-ids-contains e-ids (record-get (i-array-get records))))
      (begin
        (printf "\n result records contain inexistant %s ids\n" name)
        ;(db-debug-log-relations records)
        (status-set-id-goto 1)))
    (i-array-forward records))
  (i-array-rewind records)
  (while (i-array-in-range e-ids)
    (if (not (contains-at records (i-array-get e-ids)))
      (begin
        (printf "\n  %s result records do not contain all existing-ids\n" name)
        ;(db-debug-log-relations records)
        (status-set-id-goto 2)))
    (i-array-forward e-ids))
  (label exit
    (return status)))

(define (test-helper-graph-read-records-validate data) (status-t test-helper-graph-read-data-t)
  status-declare
  (status-require (test-helper-graph-read-records-validate-one "left" data.e-left data.records))
  (status-require (test-helper-graph-read-records-validate-one "right" data.e-right data.records))
  (status-require (test-helper-graph-read-records-validate-one "label" data.e-label data.records))
  (label exit
    (return status)))

(define (test-helper-default-ordinal-generator state) (db-ordinal-t void*)
  (define ordinal-pointer db-ordinal-t* state)
  (define result db-ordinal-t (+ 1 (pointer-get ordinal-pointer)))
  (set (pointer-get ordinal-pointer) result)
  (return result))

(define (test-helper-create-relations txn left right label)
  (status-t db-txn-t db-ids-t db-ids-t db-ids-t)
  "create relations with linearly increasing ordinal starting from zero"
  status-declare
  (declare ordinal-state-value db-ordinal-t)
  (set ordinal-state-value 0)
  (status-require
    (db-graph-ensure
      txn left right label test-helper-default-ordinal-generator &ordinal-state-value))
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

(define (test-helper-graph-read-one txn data use-left use-right use-label use-ordinal offset)
  (status-t db-txn-t test-helper-graph-read-data-t boolean boolean boolean boolean ui32)
  status-declare
  (declare
    left-pointer db-ids-t*
    right-pointer db-ids-t*
    label-pointer db-ids-t*
    state db-graph-selection-t
    ordinal-min ui32
    ordinal-max ui32
    ordinal-condition db-ordinal-condition-t
    ordinal db-ordinal-condition-t*
    expected-count ui32
    reader-suffix ui8
    reader-suffix-string ui8*)
  (set
    ordinal-min 2
    ordinal-max 5
    ordinal-condition.min ordinal-min
    ordinal-condition.max ordinal-max
    left-pointer
    (if* use-left &data.left
      0)
    right-pointer
    (if* use-right &data.right
      0)
    label-pointer
    (if* use-label &data.label
      0)
    ordinal
    (if* use-ordinal &ordinal-condition
      0)
    reader-suffix
    (bit-or
      (if* use-left 8
        0)
      (if* use-right 4
        0)
      (if* use-label 2
        0)
      (if* use-ordinal 1
        0))
    reader-suffix-string (test-helper-reader-suffix-integer->string reader-suffix)
    expected-count
    (test-helper-estimate-graph-read-result-count
      data.e-left-count data.e-right-count data.e-label-count ordinal))
  (printf "  %s" reader-suffix-string)
  (free reader-suffix-string)
  (status-require
    (db-graph-select txn left-pointer right-pointer label-pointer ordinal offset &state))
  (status-require-read (db-graph-read &state 2 &data.records))
  (status-require-read (db-graph-read &state 0 &data.records))
  (if (= status.id db-status-id-notfound) (set status.id status-id-success)
    (begin
      (printf "\n  final read result does not indicate that there is no more data")
      (status-set-id-goto 1)))
  (if (not (= (i-array-length data.records) expected-count))
    (begin
      (printf
        "\n  expected %lu read %lu. ordinal min %d max %d\n"
        expected-count
        (i-array-length data.records)
        (if* ordinal ordinal-min
          0)
        (if* ordinal ordinal-max
          0))
      (printf "read ")
      (db-debug-log-relations data.records)
      (test-helper-display-all-relations
        txn data.e-left-count data.e-right-count data.e-label-count)
      (status-set-id-goto 1)))
  (if (not ordinal) (status-require (test-helper-graph-read-records-validate data)))
  (db-graph-selection-finish &state)
  db-status-success-if-notfound
  (i-array-rewind data.records)
  (label exit
    (printf "\n")
    (return status)))

(define (test-helper-graph-read-setup env e-left-count e-right-count e-label-count r)
  (status-t db-env-t* ui32 ui32 ui32 test-helper-graph-read-data-t*)
  "prepare arrays with ids to be used in the graph (e, existing) and ids unused in the graph
  (ne, non-existing) and with both partly interleaved (left, right, label)"
  status-declare
  (db-txn-declare env txn)
  (i-array-declare ne-left db-ids-t)
  (i-array-declare ne-right db-ids-t)
  (i-array-declare ne-label db-ids-t)
  (if
    (not
      (and
        (i-array-allocate-db-relations-t
          &r:records (* e-left-count e-right-count e-label-count))
        (i-array-allocate-db-ids-t &r:e-left e-left-count)
        (i-array-allocate-db-ids-t &r:e-right e-right-count)
        (i-array-allocate-db-ids-t &r:e-label e-label-count)))
    (status-set-id-goto db-status-id-memory))
  (status-require (db-txn-write-begin &txn))
  (test-helper-create-ids txn e-left-count &r:e-left)
  (test-helper-create-ids txn e-right-count &r:e-right)
  (test-helper-create-ids txn e-label-count &r:e-label)
  (status-require (test-helper-create-relations txn r:e-left r:e-right r:e-label))
  (sc-comment "add ids that do not exist in the graph")
  (test-helper-create-ids txn e-left-count &ne-left)
  (test-helper-create-ids txn e-right-count &ne-right)
  (test-helper-create-ids txn e-label-count &ne-label)
  (status-require (test-helper-interleave-ids txn r:e-left ne-left &r:left))
  (status-require (test-helper-interleave-ids txn r:e-right ne-right &r:right))
  (status-require (test-helper-interleave-ids txn r:e-label ne-label &r:label))
  (status-require (db-txn-commit &txn))
  (set
    r:e-left-count e-left-count
    r:e-right-count e-right-count
    r:e-label-count e-label-count)
  (label exit
    (i-array-free ne-left)
    (i-array-free ne-right)
    (i-array-free ne-label)
    (return status)))

(define (test-helper-graph-read-teardown data) (void test-helper-graph-read-data-t*)
  (i-array-free data:records)
  (i-array-free data:e-left)
  (i-array-free data:e-right)
  (i-array-free data:e-label)
  (i-array-free data:left)
  (i-array-free data:right)
  (i-array-free data:label))

(define (test-helper-graph-delete-setup env e-left-count e-right-count e-label-count r)
  (status-t db-env-t* ui32 ui32 ui32 test-helper-graph-delete-data-t*)
  status-declare
  (set
    r:env env
    r:e-left-count e-left-count
    r:e-right-count e-right-count
    r:e-label-count e-label-count)
  (return status))

(define (test-helper-graph-delete-one data use-left use-right use-label use-ordinal)
  (status-t test-helper-graph-delete-data-t boolean boolean boolean boolean)
  (begin
    "for any given argument permutation:
     * checks btree entry count difference
     * checks read result count after deletion, using the same search query
    relations are assumed to be created with linearly incremented ordinals starting with 1"
    status-declare
    (db-txn-declare data.env txn)
    (i-array-declare left db-ids-t)
    (i-array-declare right db-ids-t)
    (i-array-declare label db-ids-t)
    (i-array-declare records db-relations-t)
    (declare
      state db-graph-selection-t
      read-count-before-expected ui32
      btree-count-after-delete ui32
      btree-count-before-create ui32
      btree-count-deleted-expected ui32
      ordinal db-ordinal-condition-t*)
    (define ordinal-condition db-ordinal-condition-t (struct-literal 2 5))
    (printf "  %d%d%d%d" use-left use-right use-label use-ordinal)
    (set
      ordinal &ordinal-condition
      read-count-before-expected
      (test-helper-estimate-graph-read-result-count
        data.e-left-count data.e-right-count data.e-label-count ordinal)
      btree-count-deleted-expected
      (test-helper-estimate-graph-read-btree-entry-count
        data.e-left-count data.e-right-count data.e-label-count ordinal))
    (i-array-allocate-db-ids-t &left data.e-left-count)
    (i-array-allocate-db-ids-t &right data.e-right-count)
    (i-array-allocate-db-ids-t &label data.e-label-count)
    (i-array-allocate-db-relations-t
      &records (* data.e-left-count data.e-right-count data.e-label-count))
    (status-require (db-txn-write-begin &txn))
    (test-helper-create-ids txn data.e-left-count &left)
    (test-helper-create-ids txn data.e-right-count &right)
    (test-helper-create-ids txn data.e-label-count &label)
    (db-debug-count-all-btree-entries txn &btree-count-before-create)
    (status-require (test-helper-create-relations txn left right label))
    (status-require (db-txn-commit &txn))
    (status-require (db-txn-write-begin &txn))
    (sc-comment "delete")
    (status-require
      (db-graph-delete
        txn
        (if* use-left &left
          0)
        (if* use-right &right
          0)
        (if* use-label &label
          0)
        (if* use-ordinal ordinal
          0)))
    (status-require (db-txn-commit &txn))
    (status-require (db-txn-begin &txn))
    (db-debug-count-all-btree-entries txn &btree-count-after-delete)
    (status-require-read
      (db-graph-select
        txn
        (if* use-left &left
          0)
        (if* use-right &right
          0)
        (if* use-label &label
          0)
        (if* use-ordinal ordinal
          0)
        0 &state))
    (sc-comment "check that readers can handle empty selections")
    (status-require-read (db-graph-read &state 0 &records))
    (db-graph-selection-finish &state)
    (db-txn-abort &txn)
    (if (not (= 0 (i-array-length records)))
      (begin
        (printf "\n    failed deletion. %lu relations not deleted\n" (i-array-length records))
        (db-debug-log-relations records)
        ;(status-require (db-txn-begin &txn))
        ;(test-helper-display-all-relations txn common-element-count common-element-count common-label-count)
        ;(db-txn-abort &txn)
        (status-set-id-goto 1)))
    (i-array-clear records)
    (sc-comment
      "test only if not using ordinal condition because the expected counts arent estimated")
    (if (not (or use-ordinal (= btree-count-after-delete btree-count-before-create)))
      (begin
        (printf
          "\n failed deletion. %lu btree entries not deleted\n"
          (- btree-count-after-delete btree-count-before-create))
        (status-require (db-txn-begin &txn))
        (db-debug-log-btree-counts txn)
        (status-require-read (db-graph-select txn 0 0 0 0 0 &state))
        (status-require-read (db-graph-read &state 0 &records))
        (printf "all remaining ")
        (db-debug-log-relations records)
        (db-graph-selection-finish &state)
        (db-txn-abort &txn)
        (status-set-id-goto 1)))
    db-status-success-if-notfound
    (label exit
      (printf "\n")
      (i-array-free left)
      (i-array-free right)
      (i-array-free label)
      (i-array-free records)
      (return status))))