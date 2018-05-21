(pre-define debug-log? #t)
(pre-include "stdio.h" "stdlib.h" "errno.h" "pthread.h" "../main/sph-db.h" "../foreign/sph/one.c")

(pre-define
  test-helper-db-root "/tmp/test-sph-db"
  test-helper-path-data (pre-string-concat test-helper-db-root "/data")
  (set-plus-one a) (set a (+ 1 a))
  (set-minus-one a) (set a (- a 1)))

(pre-define (test-helper-init env-name)
  (begin
    status-init
    (db-env-define env-name)))

(pre-define test-helper-report-status
  (if status-success? (printf "--\ntests finished successfully.\n")
    (printf "\ntests failed. %d %s\n" status.id (db-status-description status))))

(pre-define (test-helper-test-one env func)
  (begin
    (printf "%s\n" (pre-stringify func))
    (status-require! (test-helper-reset env #f))
    (status-require! (func env))))

(define (test-helper-reset env re-use) (status-t db-env-t* boolean)
  status-init
  (if env:open (db-close env))
  (if (and (not re-use) (file-exists? test-helper-path-data))
    (begin
      (status-set-id (system (pre-string-concat "rm " test-helper-path-data)))
      status-require))
  (status-require! (db-open test-helper-db-root 0 env))
  (label exit
    (return status)))

#;(
(define (db-ids-reverse source result) (status-t db-ids-t* db-ids-t**)
  status-init
  (define ids-temp db-ids-t*)
  (while source
    (db-ids-add! (pointer-get result) (db-ids-first source) ids-temp)
    (set source (db-ids-rest source)))
  (label exit
    (return status)))

(define (db-ids-contains? ids id) (boolean db-ids-t* db-id-t)
  (while ids
    (if (equal? id (db-ids-first ids)) (return #t))
    (set ids (db-ids-rest ids)))
  (return #f))

(define (db-debug-display-all-relations txn) (status-t MDB-txn*)
  status-init
  (define
    records db-relation-records-t*
    state db-relation-read-state-t)
  (set records 0)
  (db-status-require-read! (db-relation-select txn 0 0 0 0 0 (address-of state)))
  (db-status-require-read! (db-relation-read (address-of state) 0 (address-of records)))
  (printf "all ")
  (db-relation-selection-destroy (address-of state))
  (db-debug-display-relation-records records)
  (db-relation-records-destroy records)
  (label exit
    (return status)))

(pre-define (db-debug-define-relation-records-contains-at? field)
  (define ((pre-concat db-debug-relation-records-contains-at_ field _p) records id)
    (boolean db-relation-records-t* db-id-t)
    (while records
      (if (equal? id (struct-get (db-relation-records-first records) field)) (return #t))
      (set records (db-relation-records-rest records)))
    (return #f)))

(db-debug-define-relation-records-contains-at? left)
(db-debug-define-relation-records-contains-at? right)
(db-debug-define-relation-records-contains-at? label)

(pre-define (test-helper-assert description expression)
  (if (not expression)
    (begin
      (printf "%s failed\n" description)
      (status-set-id-goto 1))))

(pre-define (test-helper-filter-ids->reader-suffix-integer left right label ordinal)
  (bit-or (if* left 8 0) (if* right 4 0) (if* label 2 0) (if* ordinal 1 0)))



(define (test-helper-create-ids count result) (status-t b32 db-ids-t**)
  status-init
  (db-define-ids ids-temp)
  db-txn-introduce
  db-txn-write-begin
  (db-id-create db-txn count (address-of ids-temp))
  db-txn-commit
  ; reverse for sorting that aids debugging. some tests depend on it
  (status-require (db-ids-reverse ids-temp result))
  (label exit
    (return status)))

(define (test-helper-create-interns count result) (status-t b32 db-ids-t**)
  status-init
  (define data-element db-data-t (struct-literal (size db-size-id) (data 0)))
  (define data-list db-data-list-t* 0)
  (while count
    (struct-set data-element data (calloc 1 (sizeof db-ids-t)))
    (set (pointer-get (convert-type (struct-get data-element data) db-id-t*)) (+ 123 count))
    (set data-list (db-data-list-add data-list data-element))
    (set count (- count 1)))
  db-txn-introduce
  db-txn-write-begin
  (status-require! (db-intern-ensure db-txn data-list result))
  db-txn-commit
  (label exit
    (if db-txn db-txn-abort)
    (return status)))

(define (test-helper-default-ordinal-generator state) (db-ordinal-t b0*)
  (define ordinal-pointer db-ordinal-t* state)
  (define result db-ordinal-t (+ 1 (pointer-get ordinal-pointer)))
  (set (pointer-get ordinal-pointer) result)
  (return result))

(define (test-helper-create-relations count-left count-right count-label left right label)
  (status-t b32 b32 b32 db-ids-t** db-ids-t** db-ids-t**)
  status-init
  (status-require! (test-helper-create-ids count-left left))
  (status-require! (test-helper-create-ids count-right right))
  (status-require! (test-helper-create-ids count-label label))
  (define ordinal-state-value db-ordinal-t 0)
  db-txn-introduce
  db-txn-write-begin
  (status-require!
    (db-relation-ensure
      db-txn
      (pointer-get left)
      (pointer-get right)
      (pointer-get label) test-helper-default-ordinal-generator (address-of ordinal-state-value)))
  db-txn-commit
  (label exit
    (if db-txn db-txn-abort)
    (return status)))

(define (test-helper-calculate-relation-count left-count right-count label-count) (b32 b32 b32 b32)
  (return (* left-count right-count label-count)))

(define (test-helper-calculate-relation-count-from-ids left right label)
  (b32 db-ids-t* db-ids-t* db-ids-t*)
  (return
    (test-helper-calculate-relation-count
      (db-ids-length left) (db-ids-length right) (db-ids-length label))))

(define
  (test-helper-estimate-relation-read-result-count left-count right-count label-count ordinal)
  (b32 b32 b32 b32 db-ordinal-match-data-t*)
  ;assumes linearly set-plus-oneed ordinal integers starting at 1 and queries for all or no ids
  (define count b32 (* left-count right-count label-count))
  (define max b32)
  (define min b32)
  (if ordinal
    (begin
      (set
        min (if* (struct-pointer-get ordinal min) (- (struct-pointer-get ordinal min) 1) 0)
        max (struct-pointer-get ordinal max))
      (if* (> max count) (set max count)))
    (set
      min 0
      max count))
  (return (- count min (- count max))))

(define
  (test-helper-estimate-relation-read-btree-entry-count
    existing-left-count existing-right-count existing-label-count ordinal)
  (b32 b32 b32 b32 db-ordinal-match-data-t*)
  ;calculates the number of btree entries affected by a relation read or delete.
  ;assumes linearly set-plus-oneed ordinal integers starting at 1 and queries for all or no ids
  (define ordinal-min b32 0)
  (define ordinal-max b32 0)
  (if ordinal
    (set
      ordinal-min (struct-pointer-get ordinal min)
      ordinal-max (struct-pointer-get ordinal max)))
  (define label-left-count b32 0)
  (define left-right-count b32 0)
  (define right-left-count b32 0)
  ;test relation ordinals currently start at one
  (define ordinal-value b32 1)
  (define left-count b32 0)
  (define right-count b32 0)
  (define label-count b32 0)
  ;the number of relations is not proportional to the number of entries in label->left.
  ;use a process similar to relation creation to correctly calculate label->left and ordinal dependent entries
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

(define (test-helper-ids-add-new-ids ids-old result) (status-t db-ids-t* db-ids-t**)
  ;interleave new ids starting from half the given ids, and add another half of only new ids to the end
  ;approximately like this: 1 1 1 1 -> 1 1 2 1 2 1 2 2
  ;this is to ensure that we have a subsequent existing ids/new-ids and alternating existing/new ids
  status-init
  (db-define-ids ids-new)
  (set (pointer-get result) 0)
  (status-require! (test-helper-create-ids (db-ids-length ids-old) (address-of ids-new)))
  (define target-count b32 (* 2 (db-ids-length ids-old)))
  (define start-mixed b32 (/ target-count 4))
  (define start-new b32 (- target-count start-mixed))
  (define count b32 0)
  (while (< count target-count)
    (if (< count start-mixed)
      (begin
        (set (pointer-get result) (db-ids-add (pointer-get result) (db-ids-first ids-old)))
        (set ids-old (db-ids-rest ids-old)))
      (if (< count start-new)
        (if (bit-and 1 count)
          (begin
            (set (pointer-get result) (db-ids-add (pointer-get result) (db-ids-first ids-old)))
            (set ids-old (db-ids-rest ids-old)))
          (begin
            (set (pointer-get result) (db-ids-add (pointer-get result) (db-ids-first ids-new)))
            (set ids-new (db-ids-rest ids-new))))
        (begin
          (set (pointer-get result) (db-ids-add (pointer-get result) (db-ids-first ids-new)))
          (set ids-new (db-ids-rest ids-new)))))
    (set count (+ 1 count)))
  (label exit
    (return status)))

(define (test-helper-reader-suffix-integer->string a) (b8* b8)
  (define result b8* (malloc 40))
  (array-set-index
    result
    0
    (if* (bit-and 8 a) #\1 #\0)
    1 (if* (bit-and 4 a) #\1 #\0) 2 (if* (bit-and 2 a) #\1 #\0) 3 (if* (bit-and 1 a) #\1 #\0) 4 0)
  (return result))


)