(define (db-debug-display-content-graph-lr txn) (status-t db-txn-t)
  status-init
  (db-mdb-cursor-define txn.mdb-txn txn.env:dbi-graph-lr graph-lr)
  (declare
    id-left db-id-t
    id-right db-id-t
    id-label db-id-t
    ordinal db-ordinal-t)
  db-mdb-declare-val-graph-key
  db-mdb-declare-val-graph-data
  (printf "graph-lr\n")
  (db-mdb-cursor-each-key
    graph-lr
    val-graph-key
    val-graph-data
    (compound-statement
      (set
        id-left (db-mdb-val->id-at val-graph-key 0)
        id-label (db-mdb-val->id-at val-graph-key 1))
      (do-while db-mdb-status-success?
        (set
          id-right (db-mdb-val-graph-data->id val-graph-data)
          ordinal (db-mdb-val-graph-data->ordinal val-graph-data))
        (printf "  (%lu %lu) (%lu %lu)\n" id-left id-label ordinal id-right)
        (db-mdb-cursor-next-dup! graph-lr val-graph-key val-graph-data))))
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
  (db-mdb-cursor-define txn.mdb-txn txn.env:dbi-graph-rl graph-rl)
  (printf "graph-rl\n")
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
        (printf "  (%lu %lu) %lu\n" id-right id-label id-left)
        (db-mdb-cursor-next-dup! graph-rl val-graph-key val-id))))
  (label exit
    (mdb-cursor-close graph-rl)
    db-status-success-if-mdb-notfound
    (return status)))