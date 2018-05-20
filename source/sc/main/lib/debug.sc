;development helpers

(define (db-debug-log-ids a) (b0 db-ids-t*)
  (while a
    (debug-log "%lu" (db-ids-first a))
    (set a (db-ids-rest a))))

(define (db-debug-log-ids-set a) (b0 imht-set-t)
  (define index b32 0)
  (while (< index a.size)
    (debug-log "%lu" (pointer-get a.content index))
    (set index (+ 1 index))))

(define (db-debug-display-graph-records records) (b0 db-graph-records-t*)
  (define record db-graph-record-t)
  (printf "graph records\n")
  (while records
    (set record (db-graph-records-first records))
    (printf
      "  lcor %lu %lu %lu %lu\n"
      (struct-get record left)
      (struct-get record label) (struct-get record ordinal) (struct-get record right))
    (set records (db-graph-records-rest records))))

(define (db-debug-count-all-btree-entries txn result) (status-t db-txn-t b32*)
  status-init
  (define stat db-statistics-t)
  (status-require! (db-statistics txn (address-of stat)))
  (set (pointer-get result)
    (+
      (struct-get stat system ms_entries)
      (struct-get stat id->data ms_entries)
      (struct-get stat left->right ms_entries)
      (struct-get stat right->left ms_entries) (struct-get stat label->left ms_entries)))
  (label exit
    (return status)))

(define (db-debug-display-btree-counts txn) (status-t db-txn-t)
  status-init
  (define stat db-statistics-t)
  (status-require! (db-statistics txn (address-of stat)))
  (printf
    "btree entry count\n  id->data %d data-intern->id %d\n  data-extern->extern %d left->right %d\n  right->left %d label->left %d\n"
    (struct-get stat system ms_entries)
    (struct-get stat id->data ms_entries)
    (struct-get stat left->right ms_entries)
    (struct-get stat right->left ms_entries) (struct-get stat label->left ms_entries))
  (label exit
    (return status)))
