(pre-define (db-index-errors-graph-log message left right label)
  (db-error-log
    "(groups index graph) (description \"%s\") (left %lu) (right %lu) (label %lu)"
    message left right label))

(pre-define (db-index-errors-data-log message type id)
  (db-error-log "(groups index %s) (description %s) (id %lu)" type message id))

(define (db-index-recreate-graph) status-t
  status-init
  db-mdb-declare-val-graph-key
  db-mdb-declare-val-graph-data
  db-mdb-declare-val-id
  db-mdb-declare-val-id-2
  (db-define-graph-data graph-data)
  (db-define-graph-key graph-key)
  (db-mdb-cursor-declare-3 left->right right->left label->left)
  db-txn-introduce
  db-txn-write-begin
  (db-mdb-status-require! (mdb-drop db-txn dbi-right->left 0))
  (db-mdb-status-require! (mdb-drop db-txn dbi-label->left 0))
  db-txn-commit
  db-txn-write-begin
  (db-mdb-cursor-open-3 db-txn left->right right->left label->left)
  (declare
    id-left db-id-t
    id-right db-id-t
    id-label db-id-t)
  (db-mdb-cursor-each-key
    left->right
    val-graph-key
    val-graph-data
    (compound-statement
      (set
        id-left (db-mdb-val->id-at val-graph-key 0)
        id-label (db-mdb-val->id-at val-graph-key 1))
      (do-while db-mdb-status-success?
        (set id-right (db-mdb-val-graph-data->id val-graph-data))
        ;create right->left
        (array-set-index graph-key 0 id-right 1 id-label)
        (struct-set val-graph-key
          mv-data graph-key)
        (struct-set val-id
          mv-data (address-of id-left))
        (db-mdb-status-require!
          (mdb-cursor-put right->left (address-of val-graph-key) (address-of val-id) 0))
        ;create label->left
        (struct-set val-id-2
          mv-data (address-of id-label))
        (db-mdb-status-require!
          (mdb-cursor-put label->left (address-of val-id-2) (address-of val-id) 0))
        (db-mdb-cursor-next-dup! left->right val-graph-key val-graph-data))))
  db-txn-commit
  (label exit
    (if db-txn
      db-txn-abort)
    (return status)))

(define (db-index-recreate-intern) status-t
  status-init
  db-mdb-declare-val-id
  db-mdb-declare-val-data
  (db-mdb-cursor-declare-2 id->data data-intern->id)
  db-txn-introduce
  db-txn-write-begin
  (mdb-drop db-txn dbi-data-intern->id 0)
  db-txn-commit
  db-txn-write-begin
  (db-mdb-cursor-open-2 db-txn id->data data-intern->id)
  (db-mdb-cursor-each-key
    id->data
    val-id
    val-data
    (compound-statement
      (if (and (struct-get val-data mv-size) (db-intern? (db-mdb-val->id val-id)))
        (db-mdb-status-require!
          (mdb-cursor-put data-intern->id (address-of val-data) (address-of val-id) 0)))))
  db-txn-commit
  (label exit
    (if db-txn
      db-txn-abort)
    (return status)))

(define (db-index-recreate-extern) status-t
  status-init
  db-mdb-declare-val-id
  db-mdb-declare-val-data
  (db-mdb-cursor-declare id->data)
  (db-mdb-cursor-declare data-intern->id)
  db-txn-introduce
  db-txn-write-begin
  (mdb-drop db-txn dbi-data-intern->id 0)
  db-txn-commit
  db-txn-write-begin
  (db-mdb-cursor-open db-txn id->data)
  (db-mdb-cursor-open db-txn data-intern->id)
  (db-mdb-cursor-each-key
    id->data
    val-id
    val-data
    (compound-statement
      (if (and (struct-get val-data mv-size) (db-intern? (db-mdb-val->id val-id)))
        (db-mdb-status-require!
          (mdb-cursor-put data-intern->id (address-of val-data) (address-of val-id) 0)))))
  db-txn-commit
  (label exit
    (if db-txn
      db-txn-abort)
    (return status)))

(define (db-index-errors-graph db-txn result) (status-t db-txn-t* db-index-errors-graph-t*)
  status-init
  (set (pointer-get result) db-index-errors-graph-null)
  db-mdb-declare-val-id
  db-mdb-declare-val-id-2
  db-mdb-declare-val-graph-key
  db-mdb-declare-val-graph-data
  (declare
    id-right db-id-t
    id-left db-id-t
    id-label db-id-t
    records-temp db-graph-records-t*
    record db-graph-record-t)
  (db-define-graph-key graph-key)
  (db-define-graph-data graph-data)
  (db-mdb-cursor-define-3 db-txn left->right right->left label->left)
  ;left->right
  (db-mdb-cursor-each-key
    left->right
    val-graph-key
    val-graph-data
    (compound-statement
      (set
        id-left (db-mdb-val->id-at val-graph-key 0)
        id-label (db-mdb-val->id-at val-graph-key 1))
      (do-while db-mdb-status-success?
        (set id-right (db-mdb-val-graph-data->id val-graph-data))
        ;-> right->left
        (array-set-index graph-key 0 id-right 1 id-label)
        (struct-set val-graph-key
          mv-data graph-key)
        (struct-set val-id
          mv-data (address-of id-left))
        (db-mdb-cursor-get! right->left val-graph-key val-id MDB-SET-KEY)
        (db-mdb-cursor-get! right->left val-graph-key val-id MDB-GET-BOTH)
        (if db-mdb-status-failure?
          (if (= MDB-NOTFOUND status.id)
            (begin
              (db-index-errors-graph-log
                "entry from left->right not in right->left" id-left id-right id-label)
              (struct-pointer-set result
                errors? #t)
              (struct-set record
                left id-left
                right id-right
                label id-label)
              (db-graph-records-add!
                (struct-pointer-get result missing-right-left) record records-temp))
            status-goto))
        ;-> label->left
        (struct-set val-id-2
          mv-data (address-of id-label))
        (db-mdb-cursor-get! label->left val-id-2 val-id MDB-GET-BOTH)
        (if (not db-mdb-status-success?)
          (if (= MDB-NOTFOUND status.id)
            (begin
              (db-index-errors-graph-log
                "entry from left->right not in label->left" id-left id-right id-label)
              (struct-pointer-set result
                errors? #t)
              (struct-set record
                left id-left
                right id-right
                label id-label)
              (db-graph-records-add!
                (struct-pointer-get result missing-label-left) record records-temp))
            status-goto))
        (db-mdb-cursor-next-dup! left->right val-graph-key val-graph-data))))
  ;right->left -> left->right
  (db-mdb-cursor-each-key
    right->left
    val-graph-key
    val-id
    (compound-statement
      (set
        id-right (db-mdb-val->id-at val-graph-key 0)
        id-label (db-mdb-val->id-at val-graph-key 1))
      (do-while db-mdb-status-success?
        (set id-left (db-mdb-val->id val-id))
        (array-set-index graph-key 0 id-left 1 id-label)
        (struct-set val-graph-key
          mv-data graph-key)
        (db-mdb-cursor-get! left->right val-graph-key val-graph-data MDB-SET-KEY)
        (if db-mdb-status-success?
          (set status (db-mdb-left->right-seek-right left->right id-right)))
        (if (not db-mdb-status-success?)
          (if (= MDB-NOTFOUND status.id)
            (begin
              (db-index-errors-graph-log
                "entry from right->left not in left->right" id-left id-right id-label)
              (struct-pointer-set result
                errors? #t)
              (struct-set record
                left id-left
                right id-right
                label id-label)
              (db-graph-records-add!
                (struct-pointer-get result excess-right-left) record records-temp))
            status-goto))
        (db-mdb-cursor-next-dup! right->left val-graph-key val-id))))
  ;label->left -> left->right
  (db-mdb-cursor-each-key
    label->left
    val-id
    val-id-2
    (compound-statement
      (set id-label (db-mdb-val->id val-id))
      (do-while db-mdb-status-success?
        (set id-left (db-mdb-val->id val-id-2))
        (array-set-index graph-key 0 id-left 1 id-label)
        (struct-set val-graph-key
          mv-data graph-key)
        (db-mdb-cursor-get! left->right val-graph-key val-graph-data MDB-SET)
        (if (not db-mdb-status-success?)
          (if (= MDB-NOTFOUND status.id)
            (begin
              (db-index-errors-graph-log
                "entry from label->left not in left->right" id-left id-right id-label)
              (struct-pointer-set result
                errors? #t)
              (struct-set record
                left id-left
                right 0
                label id-label)
              (db-graph-records-add!
                (struct-pointer-get result excess-label-left) record records-temp))
            status-goto))
        (db-mdb-cursor-next-dup! label->left val-id val-id-2))))
  db-status-success-if-mdb-notfound
  (label exit
    (db-mdb-cursor-close-3 left->right right->left label->left)
    (return status)))

(define (db-index-errors-intern txn result) (status-t db-txn-t* db-index-errors-intern-t*)
  status-init
  (set (pointer-get result) db-index-errors-intern-null)
  db-mdb-declare-val-id
  db-mdb-declare-val-data
  db-mdb-declare-val-data-2
  (db-mdb-cursor-define-2 txn data-intern->id id->data)
  (declare ids-temp db-ids-t*)
  ;index->main-tree comparison
  (db-mdb-cursor-each-key
    data-intern->id
    val-data
    val-id
    (compound-statement
      (db-mdb-cursor-get! id->data val-id val-data-2 MDB-SET-KEY)
      (if db-mdb-status-success?
        (begin
          ;compare data
          (if (db-mdb-compare-data (address-of val-data) (address-of val-data-2))
            (begin
              (db-index-errors-data-log
                "intern" "data from data-intern->id differs in id->data" (db-mdb-val->id val-id))
              (struct-pointer-set result
                errors? #t)
              (db-ids-add!
                (struct-pointer-get result different-data-id) (db-mdb-val->id val-id) ids-temp))))
        (if (= MDB-NOTFOUND status.id)
          (begin
            (db-index-errors-data-log
              "intern" "data from data-intern->id not in id->data" (db-mdb-val->id val-id))
            (struct-pointer-set result
              errors? #t)
            (db-ids-add!
              (struct-pointer-get result excess-data-id) (db-mdb-val->id val-id) ids-temp))
          status-goto))))
  ;main-tree->index comparison
  db-mdb-declare-val-id-2
  (db-mdb-cursor-each-key
    id->data
    val-id
    val-data
    (compound-statement
      (if (db-intern? (db-mdb-val->id val-id))
        (begin
          (db-mdb-cursor-get! data-intern->id val-data val-id-2 MDB-SET-KEY)
          (if db-mdb-status-success?
            (if (not (db-id-equal? (db-mdb-val->id val-id) (db-mdb-val->id val-id-2)))
              (begin
                (db-index-errors-data-log
                  "intern" "data from id->data differs in data-intern->id" (db-mdb-val->id val-id))
                (struct-pointer-set result
                  errors? #t)
                (db-ids-add!
                  (struct-pointer-get result different-id-data) (db-mdb-val->id val-id) ids-temp)))
            (if (= MDB-NOTFOUND status.id)
              (begin
                (db-index-errors-data-log
                  "intern" "data from id->data not in data-intern->id" (db-mdb-val->id val-id-2))
                (struct-pointer-set result
                  errors? #t)
                (db-ids-add!
                  (struct-pointer-get result missing-id-data) (db-mdb-val->id val-id-2) ids-temp))
              status-goto))))))
  db-status-success-if-mdb-notfound
  (label exit
    (db-mdb-cursor-close-2 id->data data-intern->id)
    (return status)))

(define (db-index-errors-extern txn result) (status-t db-txn-t* db-index-errors-extern-t*)
  status-init
  (set (pointer-get result) db-index-errors-extern-null)
  db-mdb-declare-val-id
  db-mdb-declare-val-data
  db-mdb-declare-val-data-2
  (declare ids-temp db-ids-t*)
  (db-mdb-cursor-declare-2 id->data data-extern->extern)
  (db-mdb-cursor-open-2 txn id->data data-extern->extern)
  ;index->main-tree comparison
  (db-mdb-cursor-each-key
    data-extern->extern
    val-data
    val-id
    (compound-statement
      (if (struct-get val-data mv-size)
        (begin
          (db-mdb-cursor-get! id->data val-id val-data-2 MDB-SET-KEY)
          (if db-mdb-status-success?
            (begin
              ;different data
              (if (db-mdb-compare-data (address-of val-data) (address-of val-data-2))
                (begin
                  (db-index-errors-data-log
                    "extern"
                    "data from data-extern->extern differs in id->data" (db-mdb-val->id val-id))
                  (struct-pointer-set result
                    errors? #t)
                  (db-ids-add!
                    (struct-pointer-get result different-data-extern)
                    (db-mdb-val->id val-id) ids-temp))))
            (if (= MDB-NOTFOUND status.id)
              (begin
                (db-index-errors-data-log
                  "extern" "data from data-extern->extern not in id->data" (db-mdb-val->id val-id))
                (struct-pointer-set result
                  errors? #t)
                (db-ids-add!
                  (struct-pointer-get result excess-data-extern) (db-mdb-val->id val-id) ids-temp))
              status-goto))))))
  ;main-tree->index comparison
  (db-mdb-cursor-each-key
    id->data
    val-id
    val-data
    (compound-statement
      (if (and (db-extern? (db-mdb-val->id val-id)) (struct-get val-data mv-size))
        (begin
          (db-mdb-cursor-get! data-extern->extern val-data val-id MDB-GET-BOTH)
          (if (= MDB-NOTFOUND status.id)
            (begin
              (db-index-errors-data-log
                "extern" "data from id->data not in data-extern->extern" (db-mdb-val->id val-id))
              (struct-pointer-set result
                errors? #t)
              (db-ids-add!
                (struct-pointer-get result missing-id-data) (db-mdb-val->id val-id) ids-temp))
            status-goto)))))
  db-status-success-if-mdb-notfound
  (label exit
    (db-mdb-cursor-close-2 id->data data-extern->extern)
    (return status)))