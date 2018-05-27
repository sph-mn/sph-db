(pre-define
  (db-graph-key-equal? a b)
  (and
    (db-id-equal? (array-get a 0) (array-get b 0)) (db-id-equal? (array-get a 1) (array-get b 1)))
  (db-graph-data-ordinal-set graph-data value)
  (array-set-index (convert-type graph-data db-ordinal-t*) 0 value)
  (db-graph-data-id-set graph-data value)
  (array-set-index (convert-type (+ 1 (convert-type graph-data db-ordinal-t*)) db-id-t*) 0 value)
  (db-declare-graph-key name) (declare name (array db-id-t 2 0 0))
  (db-declare-graph-data name)
  (begin
    (declare name (array b8 ((+ db-size-ordinal db-size-id))))
    (memset name 0 (+ db-size-ordinal db-size-id)))
  (db-declare-graph-record name) (define name db-graph-record-t (struct-literal 0 0 0 0))
  (db-graph-records-add! target record target-temp)
  (db-pointer-allocation-set target (db-graph-records-add target record) target-temp))

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
  (db-cursor-open txn graph-lr)
  (db-cursor-open txn graph-rl)
  (db-cursor-open txn graph-ll)
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
        right-pointer right)
      (set val-id-2.mv-data &id-label)
      (while right-pointer
        (set id-right (db-ids-first right-pointer))
        (array-set-index graph-key 0 id-right 1 id-label)
        (set
          val-graph-key.mv-data graph-key
          val-id.mv-data &id-left)
        (db-mdb-cursor-get-norequire graph-rl val-graph-key val-id MDB-GET-BOTH)
        (if (= MDB-NOTFOUND status.id)
          (begin
            (db-mdb-status-require! (mdb-cursor-put graph-rl &val-graph-key &val-id 0))
            (db-mdb-status-require! (mdb-cursor-put graph-ll &val-id-2 &val-id 0))
            (array-set-index graph-key 0 id-left 1 id-label)
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
    (db-cursor-close-if-active graph-lr)
    (db-cursor-close-if-active graph-rl)
    (db-cursor-close-if-active graph-ll)
    (return status)))

;(pre-include "graph-delete" "graph-read" "lib/debug-graph")
