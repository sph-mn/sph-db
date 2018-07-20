(pre-define
  (db-graph-key-equal a b)
  (and (db-id-equal (array-get a 0) (array-get b 0)) (db-id-equal (array-get a 1) (array-get b 1)))
  (db-graph-data-ordinal-set graph-data value)
  (set (array-get (convert-type graph-data db-ordinal-t*) 0) value)
  (db-graph-data-id-set graph-data value)
  (set (array-get (convert-type (+ 1 (convert-type graph-data db-ordinal-t*)) db-id-t*) 0) value)
  (db-declare-graph-key name) (declare name (array db-id-t 2 0 0))
  (db-declare-graph-data name)
  (begin
    (declare graph-data (array ui8 ((+ db-size-ordinal db-size-id))))
    (memset graph-data 0 (+ db-size-ordinal db-size-id)))
  (db-declare-graph-record name) (define name db-graph-record-t (struct-literal 0 0 0 0))
  (db-graph-records-add! target record target-temp)
  (db-pointer-allocation-set target (db-graph-records-add target record) target-temp))

(define (db-mdb-graph-lr-seek-right graph-lr id-right) (status-t MDB-cursor* db-id-t)
  "search data until the given id-right has been found"
  status-declare
  db-mdb-declare-val-graph-key
  db-mdb-declare-val-graph-data
  (set status.id (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-GET-CURRENT))
  (label each-data
    (if db-mdb-status-is-success
      (if (= id-right (db-graph-data->id val-graph-data.mv-data)) (return status)
        (begin
          (set status.id (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-NEXT-DUP))
          (goto each-data)))
      db-mdb-status-expect-notfound))
  (label exit
    (return status)))

(define (db-graph-ensure txn left right label ordinal-generator ordinal-generator-state)
  (status-t db-txn-t db-ids-t* db-ids-t* db-ids-t* db-graph-ordinal-generator-t void*)
  "check if a relation exists and create it if not"
  status-declare
  db-mdb-declare-val-id
  db-mdb-declare-val-id-2
  db-mdb-declare-val-graph-key
  db-mdb-declare-val-graph-data
  (db-declare-graph-key graph-key)
  (db-declare-graph-data graph-data)
  (db-mdb-cursor-declare graph-lr)
  (db-mdb-cursor-declare graph-rl)
  (db-mdb-cursor-declare graph-ll)
  (declare
    id-label db-id-t
    id-left db-id-t
    id-right db-id-t
    label-pointer db-ids-t*
    ordinal db-ordinal-t
    right-pointer db-ids-t*)
  (db-mdb-status-require (db-mdb-env-cursor-open txn graph-lr))
  (db-mdb-status-require (db-mdb-env-cursor-open txn graph-rl))
  (db-mdb-status-require (db-mdb-env-cursor-open txn graph-ll))
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
        (set status.id (mdb-cursor-get graph-rl &val-graph-key &val-id MDB-GET-BOTH))
        (if (= MDB-NOTFOUND status.id)
          (begin
            (db-mdb-status-require (mdb-cursor-put graph-rl &val-graph-key &val-id 0))
            (db-mdb-status-require (mdb-cursor-put graph-ll &val-id-2 &val-id 0))
            (set
              (array-get graph-key 0) id-left
              (array-get graph-key 1) id-label)
            (if ordinal-generator
              (set ordinal ((pointer-get ordinal-generator) ordinal-generator-state)))
            (db-graph-data-ordinal-set graph-data ordinal)
            (db-graph-data-id-set graph-data id-right)
            (set val-graph-data.mv-data graph-data)
            (db-mdb-status-require (mdb-cursor-put graph-lr &val-graph-key &val-graph-data 0)))
          (if (not db-mdb-status-is-success) (status-set-group-goto db-status-group-lmdb)))
        (set right-pointer (db-ids-rest right-pointer)))
      (set label-pointer (db-ids-rest label-pointer)))
    (set left (db-ids-rest left)))
  (label exit
    (db-mdb-cursor-close-if-active graph-lr)
    (db-mdb-cursor-close-if-active graph-rl)
    (db-mdb-cursor-close-if-active graph-ll)
    (return status)))

(define (db-graph-index-rebuild env) (status-t db-env-t*)
  "rebuild graph-rl and graph-ll based on graph-lr"
  status-declare
  db-mdb-declare-val-graph-key
  db-mdb-declare-val-graph-data
  db-mdb-declare-val-id
  db-mdb-declare-val-id-2
  (db-mdb-cursor-declare graph-lr)
  (db-mdb-cursor-declare graph-rl)
  (db-mdb-cursor-declare graph-ll)
  (db-txn-declare env txn)
  (db-declare-graph-data graph-data)
  (db-declare-graph-key graph-key)
  (declare
    id-left db-id-t
    id-right db-id-t
    id-label db-id-t)
  (status-require (db-txn-write-begin &txn))
  (db-mdb-status-require (mdb-drop txn.mdb-txn (: env dbi-graph-rl) 0))
  (db-mdb-status-require (mdb-drop txn.mdb-txn (: env dbi-graph-ll) 0))
  (status-require (db-txn-commit &txn))
  (status-require (db-txn-write-begin &txn))
  (db-mdb-status-require (db-mdb-env-cursor-open txn graph-lr))
  (db-mdb-status-require (db-mdb-env-cursor-open txn graph-rl))
  (db-mdb-status-require (db-mdb-env-cursor-open txn graph-ll))
  (db-mdb-cursor-each-key
    graph-lr
    val-graph-key
    val-graph-data
    (compound-statement
      (set
        id-left (db-pointer->id-at val-graph-key.mv-data 0)
        id-label (db-pointer->id-at val-graph-key.mv-data 1))
      (do-while db-mdb-status-is-success
        (set id-right (db-pointer->id val-graph-data.mv-data))
        (sc-comment "graph-rl")
        (set
          (array-get graph-key 0) id-right
          (array-get graph-key 1) id-label)
        (set val-graph-key.mv-data graph-key)
        (set val-id.mv-data &id-left)
        (db-mdb-status-require (mdb-cursor-put graph-rl &val-graph-key &val-id 0))
        (sc-comment "graph-ll")
        (set val-id-2.mv-data &id-label)
        (db-mdb-status-require (mdb-cursor-put graph-ll &val-id-2 &val-id 0))
        (set status.id (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-NEXT-DUP)))))
  (status-require (db-txn-commit &txn))
  (label exit
    (db-txn-abort-if-active txn)
    (return status)))

(pre-include "./graph-read.c" "./graph-delete.c")