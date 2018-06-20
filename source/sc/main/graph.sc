(pre-define
  (db-graph-key-equal? a b)
  (and
    (db-id-equal? (array-get a 0) (array-get b 0)) (db-id-equal? (array-get a 1) (array-get b 1)))
  (db-graph-data-ordinal-set graph-data value)
  (set (array-get (convert-type graph-data db-ordinal-t*) 0) value)
  (db-graph-data-id-set graph-data value)
  (set (array-get (convert-type (+ 1 (convert-type graph-data db-ordinal-t*)) db-id-t*) 0) value)
  (db-declare-graph-key name) (declare name (array db-id-t 2 0 0))
  (db-declare-graph-data name)
  (begin
    (declare name (array b8 ((+ db-size-ordinal db-size-id))))
    (memset name 0 (+ db-size-ordinal db-size-id)))
  (db-declare-graph-record name) (define name db-graph-record-t (struct-literal 0 0 0 0))
  (db-graph-records-add! target record target-temp)
  (db-pointer-allocation-set target (db-graph-records-add target record) target-temp))

(pre-define (db-index-errors-graph-log message left right label)
  (db-error-log
    "(groups index graph) (description \"%s\") (left %lu) (right %lu) (label %lu)"
    message left right label))

(define (db-mdb-graph-lr-seek-right graph-lr id-right) (status-t MDB-cursor* db-id-t)
  "search data until the given id-right has been found"
  status-init
  db-mdb-declare-val-graph-key
  db-mdb-declare-val-graph-data
  (db-mdb-cursor-get-norequire graph-lr val-graph-key val-graph-data MDB-GET-CURRENT)
  (label each-data
    (if db-mdb-status-success?
      (if (= id-right (db-graph-data->id val-graph-data.mv-data)) (return status)
        (begin
          (db-mdb-cursor-next-dup-norequire graph-lr val-graph-key val-graph-data)
          (goto each-data)))
      db-mdb-status-require-notfound))
  (label exit
    (return status)))

(define (db-graph-ensure txn left right label ordinal-generator ordinal-generator-state)
  (status-t db-txn-t db-ids-t* db-ids-t* db-ids-t* db-graph-ordinal-generator-t b0*)
  "check if a relation exists and create it if not"
  status-init
  (declare
    id-label db-id-t
    id-left db-id-t
    id-right db-id-t
    label-pointer db-ids-t*
    ordinal db-ordinal-t
    right-pointer db-ids-t*)
  db-mdb-declare-val-id
  db-mdb-declare-val-id-2
  db-mdb-declare-val-graph-key
  db-mdb-declare-val-graph-data
  (db-declare-graph-key graph-key)
  (db-declare-graph-data graph-data)
  (db-mdb-cursor-declare-three graph-lr graph-rl graph-ll)
  (db-mdb-cursor-open txn graph-lr)
  (db-mdb-cursor-open txn graph-rl)
  (db-mdb-cursor-open txn graph-ll)
  (set ordinal
    (if* (and (not ordinal-generator) ordinal-generator-state)
      (set ordinal (pointer-get (convert-type ordinal-generator-state db-ordinal-t*)))
      0))
  (while left
    (set
      id-left (db-ids-first left)
      label-pointer label)
    (while label-pointer
      (set
        id-label (db-ids-first label-pointer)
        right-pointer right
        val-id-2.mv-data &id-label)
      (while right-pointer
        (set
          id-right (db-ids-first right-pointer)
          (array-get graph-key 0) id-right
          (array-get graph-key 1) id-label
          val-graph-key.mv-data graph-key
          val-id.mv-data &id-left)
        (db-mdb-cursor-get-norequire graph-rl val-graph-key val-id MDB-GET-BOTH)
        (if (= MDB-NOTFOUND status.id)
          (begin
            (db-mdb-status-require! (mdb-cursor-put graph-rl &val-graph-key &val-id 0))
            (db-mdb-status-require! (mdb-cursor-put graph-ll &val-id-2 &val-id 0))
            (set
              (array-get graph-key 0) id-left
              (array-get graph-key 1) id-label)
            (if ordinal-generator
              (set ordinal ((pointer-get ordinal-generator) ordinal-generator-state)))
            (db-graph-data-ordinal-set graph-data ordinal)
            (db-graph-data-id-set graph-data id-right)
            (set val-graph-data.mv-data graph-data)
            (db-mdb-status-require! (mdb-cursor-put graph-lr &val-graph-key &val-graph-data 0)))
          (if (not db-mdb-status-success?) (status-set-group-goto db-status-group-lmdb)))
        (set right-pointer (db-ids-rest right-pointer)))
      (set label-pointer (db-ids-rest label-pointer)))
    (set left (db-ids-rest left)))
  (label exit
    (db-mdb-cursor-close-if-active graph-lr)
    (db-mdb-cursor-close-if-active graph-rl)
    (db-mdb-cursor-close-if-active graph-ll)
    (return status)))

(define (db-debug-display-content-graph-lr txn) (status-t db-txn-t)
  status-init
  (db-mdb-cursor-declare graph-lr)
  db-mdb-declare-val-graph-key
  db-mdb-declare-val-graph-data
  (declare
    id-left db-id-t
    id-right db-id-t
    id-label db-id-t
    ordinal db-ordinal-t)
  (db-mdb-cursor-open txn graph-lr)
  (printf "graph-lr\n")
  (db-mdb-cursor-each-key
    graph-lr
    val-graph-key
    val-graph-data
    (compound-statement
      (set
        id-left (db-pointer->id val-graph-key.mv-data)
        id-label (db-pointer->id-at val-graph-key.mv-data 1))
      (do-while db-mdb-status-success?
        (set
          id-right (db-graph-data->id val-graph-data.mv-data)
          ordinal (db-graph-data->ordinal val-graph-data.mv-data))
        (printf "  (%lu %lu) (%lu %lu)\n" id-left id-label ordinal id-right)
        (db-mdb-cursor-next-dup-norequire graph-lr val-graph-key val-graph-data))))
  (label exit
    (mdb-cursor-close graph-lr)
    db-status-success-if-mdb-notfound
    (return status)))

(define (db-debug-display-content-graph-rl txn) (status-t db-txn-t)
  status-init
  (declare
    id-left db-id-t
    id-right db-id-t
    id-label db-id-t)
  db-mdb-declare-val-graph-key
  db-mdb-declare-val-id
  (db-mdb-cursor-declare graph-rl)
  (db-mdb-cursor-open txn graph-rl)
  (printf "graph-rl\n")
  (db-mdb-cursor-each-key
    graph-rl
    val-graph-key
    val-id
    (compound-statement
      (set
        id-right (db-pointer->id val-graph-key.mv-data)
        id-label (db-pointer->id-at val-graph-key.mv-data 1))
      (do-while db-mdb-status-success?
        (set id-left (db-pointer->id val-id.mv-data))
        (printf "  (%lu %lu) %lu\n" id-right id-label id-left)
        (db-mdb-cursor-next-dup-norequire graph-rl val-graph-key val-id))))
  (label exit
    (mdb-cursor-close graph-rl)
    db-status-success-if-mdb-notfound
    (return status)))

#;(define (db-graph-index-rebuild env) (status-t db-env-t*)
  "rebuild graph-rl and graph-ll based on graph-lr"
  status-init
  db-mdb-declare-val-graph-key
  db-mdb-declare-val-graph-data
  db-mdb-declare-val-id
  db-mdb-declare-val-id-2
  (db-define-graph-data graph-data)
  (db-define-graph-key graph-key)
  (db-mdb-cursor-declare-3 graph-lr graph-rl graph-ll)
  db-txn-introduce
  db-txn-write-begin
  (db-mdb-status-require! (mdb-drop db-txn dbi-graph-rl 0))
  (db-mdb-status-require! (mdb-drop db-txn dbi-graph-ll 0))
  db-txn-commit
  db-txn-write-begin
  (db-mdb-cursor-open-3 db-txn graph-lr graph-rl graph-ll)
  (declare
    id-left db-id-t
    id-right db-id-t
    id-label db-id-t)
  (db-mdb-cursor-each-key
    graph-lr
    val-graph-key
    val-graph-data
    (compound-statement
      (set
        id-left (db-mdb-val->id-at val-graph-key 0)
        id-label (db-mdb-val->id-at val-graph-key 1))
      (do-while db-mdb-status-success?
        (set id-right (db-mdb-val-graph-data->id val-graph-data))
        ;create graph-rl
        (set
          (array-get graph-key 0) id-right
          (array-get graph-key 1) id-label)
        (set val-graph-key.mv-data graph-key)
        (set val-id.mv-data *id-left)
        (db-mdb-status-require! (mdb-cursor-put graph-rl &val-graph-key &val-id 0))
        ;create graph-ll
        (set val-id-2.mv-data &id-label)
        (db-mdb-status-require! (mdb-cursor-put graph-ll &val-id-2 &val-id 0))
        (db-mdb-cursor-next-dup! graph-lr val-graph-key val-graph-data))))
  db-txn-commit
  (label exit
    (if db-txn db-txn-abort)
    (return status)))

#;(define (db-graph-index-errors db-txn result) (status-t db-txn-t db-index-errors-graph-t*)
  "search for inconsistencies between graph btrees"
  status-init
  (set *result db-index-errors-graph-null)
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
  (db-mdb-cursor-define-3 db-txn graph-lr graph-rl graph-ll)
  (sc-comment "graph-lr")
  (db-mdb-cursor-each-key
    graph-lr
    val-graph-key
    val-graph-data
    (compound-statement
      (set
        id-left (db-mdb-val->id-at val-graph-key 0)
        id-label (db-mdb-val->id-at val-graph-key 1))
      (do-while db-mdb-status-success?
        (set id-right (db-mdb-val-graph-data->id val-graph-data))
        (sc-comment "-> graph-rl")
        (array-set graph-key 0 id-right 1 id-label)
        (set val-graph-key.mv-data graph-key)
        (set val-id.mv-data &id-left)
        (db-mdb-cursor-get! graph-rl val-graph-key val-id MDB-SET-KEY)
        (db-mdb-cursor-get! graph-rl val-graph-key val-id MDB-GET-BOTH)
        (if db-mdb-status-failure?
          (if (= MDB-NOTFOUND status.id)
            (begin
              (db-index-errors-graph-log
                "entry from graph-lr not in graph-rl" id-left id-right id-label)
              (set result:errors? #t)
              (struct-set record
                left id-left
                right id-right
                label id-label)
              (db-graph-records-add! result:missing-right-left record records-temp))
            status-goto))
        (sc-comment "-> graph-ll")
        (set val-id-2.mv-data &id-label)
        (db-mdb-cursor-get! graph-ll val-id-2 val-id MDB-GET-BOTH)
        (if (not db-mdb-status-success?)
          (if (= MDB-NOTFOUND status.id)
            (begin
              (db-index-errors-graph-log
                "entry from graph-lr not in graph-ll" id-left id-right id-label)
              (set result:errors? #t)
              (struct-set record
                left id-left
                right id-right
                label id-label)
              (db-graph-records-add! result:missing-label-left record records-temp))
            status-goto))
        (db-mdb-cursor-next-dup! graph-lr val-graph-key val-graph-data))))
  (sc-comment "graph-rl -> graph-lr")
  (db-mdb-cursor-each-key
    graph-rl
    val-graph-key
    val-id
    (compound-statement
      (set
        id-right (db-mdb-val->id-at val-graph-key 0)
        id-label (db-mdb-val->id-at val-graph-key 1))
      (do-while db-mdb-status-success?
        (set id-left (db-mdb-val->id val-id))
        (array-set graph-key 0 id-left 1 id-label)
        (set val-graph-key.mv-data graph-key)
        (db-mdb-cursor-get! graph-lr val-graph-key val-graph-data MDB-SET-KEY)
        (if db-mdb-status-success? (set status (db-mdb-graph-lr-seek-right graph-lr id-right)))
        (if (not db-mdb-status-success?)
          (if (= MDB-NOTFOUND status.id)
            (begin
              (db-index-errors-graph-log
                "entry from graph-rl not in graph-lr" id-left id-right id-label)
              (set result:errors? #t)
              (struct-set record
                left id-left
                right id-right
                label id-label)
              (db-graph-records-add! result:excess-right-left record records-temp))
            status-goto))
        (db-mdb-cursor-next-dup! graph-rl val-graph-key val-id))))
  (sc-comment "graph-ll -> graph-lr")
  (db-mdb-cursor-each-key
    graph-ll
    val-id
    val-id-2
    (compound-statement
      (set id-label (db-mdb-val->id val-id))
      (do-while db-mdb-status-success?
        (set id-left (db-mdb-val->id val-id-2))
        (array-set graph-key 0 id-left 1 id-label)
        (set val-graph-key.mv-data graph-key)
        (db-mdb-cursor-get! graph-lr val-graph-key val-graph-data MDB-SET)
        (if (not db-mdb-status-success?)
          (if (= MDB-NOTFOUND status.id)
            (begin
              (db-index-errors-graph-log
                "entry from graph-ll not in graph-lr" id-left id-right id-label)
              (set result:errors? #t)
              (struct-set record
                left id-left
                right 0
                label id-label)
              (db-graph-records-add! result:excess-label-left record records-temp))
            status-goto))
        (db-mdb-cursor-next-dup! graph-ll val-id val-id-2))))
  db-status-success-if-mdb-notfound
  (label exit
    (db-mdb-cursor-close-3 graph-lr graph-rl graph-ll)
    (return status)))

(pre-include "./graph-read.c" "./graph-delete.c")