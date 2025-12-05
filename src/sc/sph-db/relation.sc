(pre-define
  (db-relation-key-equal a b)
  (and (= (array-get a 0) (array-get b 0)) (= (array-get a 1) (array-get b 1)))
  (db-relation-data-ordinal-set relation-data value)
  (set (array-get (convert-type relation-data db-ordinal-t*) 0) value)
  (db-relation-data-id-set relation-data value)
  (set (array-get (convert-type (+ 1 (convert-type relation-data db-ordinal-t*)) db-id-t*) 0) value)
  (db-declare-relation-key name) (declare name (array db-id-t 2 0 0))
  (db-declare-relation-data name)
  (begin
    (declare relation-data (array uint8-t ((+ (sizeof db-ordinal-t) (sizeof db-id-t)))))
    (memset relation-data 0 (+ (sizeof db-ordinal-t) (sizeof db-id-t))))
  (db-declare-relation name) (define name db-relation-t (struct-literal 0 0 0 0)))

(define (db-mdb-relation-lr-seek-right relation-lr id-right) (status-t MDB-cursor* db-id-t)
  "search data until the given id-right has been found"
  status-declare
  db-mdb-declare-val-relation-key
  db-mdb-declare-val-relation-data
  (set status.id (mdb-cursor-get relation-lr &val-relation-key &val-relation-data MDB-GET-CURRENT))
  (label each-data
    (if db-mdb-status-is-success
      (if (= id-right (db-relation-data->id val-relation-data.mv-data)) status-return
        (begin
          (set status.id
            (mdb-cursor-get relation-lr &val-relation-key &val-relation-data MDB-NEXT-DUP))
          (goto each-data)))
      db-mdb-status-expect-notfound))
  (label exit status-return))

(define (db-relation-ensure txn left right label ordinal-generator ordinal-generator-state)
  (status-t db-txn-t db-ids-t db-ids-t db-ids-t db-relation-ordinal-generator-t void*)
  "check if a relation exists and create it if not"
  status-declare
  db-mdb-declare-val-id
  db-mdb-declare-val-id-2
  db-mdb-declare-val-relation-key
  db-mdb-declare-val-relation-data
  (db-declare-relation-key relation-key)
  (db-declare-relation-data relation-data)
  (db-mdb-cursor-declare relation-lr)
  (db-mdb-cursor-declare relation-rl)
  (db-mdb-cursor-declare relation-ll)
  (declare id-label db-id-t id-left db-id-t id-right db-id-t ordinal db-ordinal-t)
  (db-mdb-status-require (db-mdb-env-cursor-open txn relation-lr))
  (db-mdb-status-require (db-mdb-env-cursor-open txn relation-rl))
  (db-mdb-status-require (db-mdb-env-cursor-open txn relation-ll))
  (set ordinal
    (if* (and (not ordinal-generator) ordinal-generator-state)
      (pointer-get (convert-type ordinal-generator-state db-ordinal-t*))
      0))
  (while (sph-array-current-in-range left)
    (set id-left (sph-array-current-get left))
    (while (sph-array-current-in-range label)
      (set id-label (sph-array-current-get label) val-id-2.mv-data &id-label)
      (while (sph-array-current-in-range right)
        (set
          id-right (sph-array-current-get right)
          (array-get relation-key 0) id-right
          (array-get relation-key 1) id-label
          val-relation-key.mv-data relation-key
          val-id.mv-data &id-left)
        (set status.id (mdb-cursor-get relation-rl &val-relation-key &val-id MDB-GET-BOTH))
        (if (= MDB-NOTFOUND status.id)
          (begin
            (db-mdb-status-require (mdb-cursor-put relation-rl &val-relation-key &val-id 0))
            (db-mdb-status-require (mdb-cursor-put relation-ll &val-id-2 &val-id 0))
            (set (array-get relation-key 0) id-left (array-get relation-key 1) id-label)
            (if ordinal-generator (set ordinal (*ordinal-generator ordinal-generator-state)))
            (db-relation-data-ordinal-set relation-data ordinal)
            (db-relation-data-id-set relation-data id-right)
            (set val-relation-data.mv-data relation-data)
            (db-mdb-status-require
              (mdb-cursor-put relation-lr &val-relation-key &val-relation-data 0)))
          (if (not db-mdb-status-is-success)
            (begin (set status.group db-status-group-lmdb) (goto exit))))
        (sph-array-current-forward right))
      (sph-array-current-rewind right)
      (sph-array-current-forward label))
    (sph-array-current-rewind label)
    (sph-array-current-forward left))
  (label exit
    (db-mdb-cursor-close-if-active relation-lr)
    (db-mdb-cursor-close-if-active relation-rl)
    (db-mdb-cursor-close-if-active relation-ll)
    status-return))

(define (db-relation-index-rebuild env) (status-t db-env-t*)
  "rebuild relation-rl and relation-ll based on relation-lr"
  status-declare
  db-mdb-declare-val-relation-key
  db-mdb-declare-val-relation-data
  db-mdb-declare-val-id
  db-mdb-declare-val-id-2
  (db-mdb-cursor-declare relation-lr)
  (db-mdb-cursor-declare relation-rl)
  (db-mdb-cursor-declare relation-ll)
  (db-txn-declare env txn)
  (db-declare-relation-data relation-data)
  (db-declare-relation-key relation-key)
  (declare id-left db-id-t id-right db-id-t id-label db-id-t)
  (status-require (db-txn-write-begin &txn))
  (db-mdb-status-require (mdb-drop txn.mdb-txn (: env dbi-relation-rl) 0))
  (db-mdb-status-require (mdb-drop txn.mdb-txn (: env dbi-relation-ll) 0))
  (status-require (db-txn-commit &txn))
  (status-require (db-txn-write-begin &txn))
  (db-mdb-status-require (db-mdb-env-cursor-open txn relation-lr))
  (db-mdb-status-require (db-mdb-env-cursor-open txn relation-rl))
  (db-mdb-status-require (db-mdb-env-cursor-open txn relation-ll))
  (set status.id (mdb-cursor-get relation-lr &val-relation-key &val-relation-data MDB-FIRST))
  (while db-mdb-status-is-success
    (set
      id-left (db-pointer->id-at val-relation-key.mv-data 0)
      id-label (db-pointer->id-at val-relation-key.mv-data 1))
    (do-while db-mdb-status-is-success
      (set id-right (db-pointer->id val-relation-data.mv-data))
      (sc-comment "relation-rl")
      (set (array-get relation-key 0) id-right (array-get relation-key 1) id-label)
      (set val-relation-key.mv-data relation-key)
      (set val-id.mv-data &id-left)
      (db-mdb-status-require (mdb-cursor-put relation-rl &val-relation-key &val-id 0))
      (sc-comment "relation-ll")
      (set val-id-2.mv-data &id-label)
      (db-mdb-status-require (mdb-cursor-put relation-ll &val-id-2 &val-id 0))
      (set status.id (mdb-cursor-get relation-lr &val-relation-key &val-relation-data MDB-NEXT-DUP)))
    (set status.id (mdb-cursor-get relation-lr &val-relation-key &val-relation-data MDB-NEXT-NODUP)))
  db-mdb-status-expect-notfound
  (status-require (db-txn-commit &txn))
  (label exit (db-txn-abort-if-active txn) status-return))

(pre-include "./relation-read.c" "./relation-delete.c")
