(define (db-debug-display-content-left->right txn) (status-t db-txn-t)
  status-init
  (db-mdb-cursor-define txn.mdb-txn txn.env:dbi-left->right left->right)
  (declare
    id-left db-id-t
    id-right db-id-t
    id-label db-id-t
    ordinal db-ordinal-t)
  db-mdb-declare-val-graph-key
  db-mdb-declare-val-graph-data
  (printf "left->right\n")
  (db-mdb-cursor-each-key
    left->right
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
        (db-mdb-cursor-next-dup! left->right val-graph-key val-graph-data))))
  (label exit
    (mdb-cursor-close left->right)
    db-status-success-if-mdb-notfound
    (return status)))

(define (db-debug-display-content-right->left txn) (status-t db-txn-t)
  status-init
  (declare
    id-left db-id-t
    id-right db-id-t
    id-label db-id-t)
  db-mdb-declare-val-graph-key
  db-mdb-declare-val-id
  (db-mdb-cursor-define txn.mdb-txn txn.env:dbi-right->left right->left)
  (printf "right->left\n")
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
        (printf "  (%lu %lu) %lu\n" id-right id-label id-left)
        (db-mdb-cursor-next-dup! right->left val-graph-key val-id))))
  (label exit
    (mdb-cursor-close right->left)
    db-status-success-if-mdb-notfound
    (return status)))