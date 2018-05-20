(pre-define
  (db-graph-key-equal? a b)
  (and
    (db-id-equal? (array-get a 0) (array-get b 0)) (db-id-equal? (array-get a 1) (array-get b 1)))
  (db-graph-data-ordinal-set graph-data value)
  (array-set-index (convert-type graph-data db-ordinal-t*) 0 value)
  (db-graph-data-id-set graph-data value)
  (array-set-index (convert-type (+ 1 (convert-type graph-data db-ordinal-t*)) db-id-t*) 0 value)
  (db-define-graph-key name) (declare name (array db-id-t (2) 0 0))
  (db-define-graph-data name)
  (begin
    (declare name (array b8 ((+ db-size-ordinal db-size-id))))
    (memset name 0 (+ db-size-ordinal db-size-id)))
  (db-define-graph-record name) (define name db-graph-record-t (struct-literal 0 0 0 0))
  (db-graph-records-add! target record target-temp)
  (db-pointer-allocation-set target (db-graph-records-add target record) target-temp))

(define (db-mdb-left->right-seek-right left->right id-right) (status-t MDB-cursor* db-id-t)
  "search data until the given id-right has been found"
  status-init
  db-mdb-declare-val-graph-key
  db-mdb-declare-val-graph-data
  (db-mdb-cursor-get-norequire left->right val-graph-key val-graph-data MDB-GET-CURRENT)
  (label each-data
    (if db-mdb-status-success?
      (if (= id-right (db-mdb-val-graph-data->id val-graph-data))
        (return status)
        (begin
          (db-mdb-cursor-next-dup! left->right val-graph-key val-graph-data)
          (goto each-data)))
      db-mdb-status-require-notfound))
  (label exit
    (return status)))

(define (db-graph-ensure txn left right label ordinal-generator ordinal-generator-state)
  (status-t db-txn-t db-ids-t* db-ids-t* db-ids-t* db-graph-ordinal-generator-t b0*)
  status-init
  (declare
    right-pointer db-ids-t*
    label-pointer db-ids-t*
    id-left db-id-t
    id-right db-id-t
    id-label db-id-t)
  (define ordinal db-ordinal-t
    (if* (and (not ordinal-generator) ordinal-generator-state)
      (set ordinal (pointer-get (convert-type ordinal-generator-state db-ordinal-t*))) 0))
  (db-define-graph-key graph-key)
  (db-define-graph-data graph-data)
  db-mdb-declare-val-id
  db-mdb-declare-val-id-2
  db-mdb-declare-val-graph-key
  db-mdb-declare-val-graph-data
  (db-mdb-cursor-define-3
    txn.mdb-txn
    (struct-pointer-get txn.s dbi-left->right)
    left->right
    (struct-pointer-get txn.s dbi-right->left)
    right->left (struct-pointer-get txn.s dbi-label->left) label->left)
  (while left
    (set
      id-left (db-ids-first left)
      label-pointer label)
    (while label-pointer
      (set
        id-label (db-ids-first label-pointer)
        right-pointer right)
      (struct-set val-id-2 mv-data (address-of id-label))
      (while right-pointer
        (set id-right (db-ids-first right-pointer))
        (array-set-index graph-key 0 id-right 1 id-label)
        (struct-set val-graph-key mv-data graph-key)
        (struct-set val-id mv-data (address-of id-left))
        (db-mdb-cursor-get-norequire right->left val-graph-key val-id MDB-GET-BOTH)
        (if (= MDB-NOTFOUND status.id)
          (begin
            (db-mdb-status-require!
              (mdb-cursor-put right->left (address-of val-graph-key) (address-of val-id) 0))
            (db-mdb-status-require!
              (mdb-cursor-put label->left (address-of val-id-2) (address-of val-id) 0))
            (array-set-index graph-key 0 id-left 1 id-label)
            (if ordinal-generator
              (set ordinal ((pointer-get ordinal-generator) ordinal-generator-state)))
            (db-graph-data-ordinal-set graph-data ordinal)
            (db-graph-data-id-set graph-data id-right)
            (struct-set val-graph-data mv-data graph-data)
            (db-mdb-status-require!
              (mdb-cursor-put left->right (address-of val-graph-key) (address-of val-graph-data) 0)))
          (if (not db-mdb-status-success?) (status-set-group-goto db-status-group-lmdb)))
        (set right-pointer (db-ids-rest right-pointer)))
      (set label-pointer (db-ids-rest label-pointer)))
    (set left (db-ids-rest left)))
  (label exit
    (db-mdb-cursor-close-3 left->right right->left label->left)
    (return status)))

(pre-include "graph-delete" "graph-read" "lib/debug-graph")