(pre-define
  notfound-exit (status-set-goto db-status-group-db db-status-id-notfound)
  (db-relation-select-cursor-initialise name selection selection-field-name)
  (begin
    (db-mdb-status-require (db-mdb-env-cursor-open txn name))
    (db-mdb-status-require (mdb-cursor-get name &val-null &val-null MDB-FIRST))
    (if (not db-mdb-status-is-success)
      (begin
        db-mdb-status-expect-notfound
        (status-set-goto db-status-group-db db-status-id-notfound)))
    (set selection:selection-field-name name))
  (db-relation-reader-header selection)
  (begin
    status-declare
    db-mdb-declare-val-relation-key
    (db-declare-relation-key relation-key)
    (db-declare-relation relation)
    (declare skip boolean)
    (set skip (bit-and db-selection-flag-skip selection:options)))
  (db-relation-reader-header-0000 selection)
  (begin
    status-declare
    db-mdb-declare-val-relation-key
    (db-declare-relation relation)
    (declare skip boolean)
    (set skip (bit-and db-selection-flag-skip selection:options)))
  (db-relation-reader-define-ordinal-variables selection)
  (begin
    (declare
      ordinal-min db-ordinal-t
      ordinal-max db-ordinal-t)
    (set
      ordinal-min selection:ordinal.min
      ordinal-max selection:ordinal.max)))

(define (db-relation-read-1000 selection count result)
  (status-t db-relation-selection-t* db-count-t db-relations-t*)
  (db-relation-reader-header selection)
  db-mdb-declare-val-relation-data
  (declare
    relation-lr MDB-cursor*
    left db-ids-t)
  (set
    relation-lr selection:cursor
    left selection:left)
  (db-mdb-status-require
    (mdb-cursor-get relation-lr &val-relation-key &val-relation-data MDB-GET-CURRENT))
  (set (array-get relation-key 0) (i-array-get left))
  (if (= (db-pointer->id val-relation-key.mv-data) (array-get relation-key 0)) (goto each-data)
    (label set-range
      (set
        val-relation-key.mv-data relation-key
        status.id (mdb-cursor-get relation-lr &val-relation-key &val-relation-data MDB-SET-RANGE))
      (label each-key
        (if db-mdb-status-is-success
          (if (= (db-pointer->id val-relation-key.mv-data) (array-get relation-key 0))
            (goto each-data))
          db-mdb-status-expect-notfound)
        (i-array-forward left)
        (if (i-array-in-range left)
          (begin
            (set (array-get relation-key 0) (i-array-get left))
            (goto set-range))
          notfound-exit))))
  (label each-data
    stop-if-count-zero
    (if (not skip)
      (begin
        (set
          relation.left (db-pointer->id val-relation-key.mv-data)
          relation.right (db-relation-data->id val-relation-data.mv-data)
          relation.label (db-pointer->id-at val-relation-key.mv-data 1)
          relation.ordinal (db-relation-data->ordinal val-relation-data.mv-data))
        (i-array-add *result relation)))
    reduce-count
    (set status.id (mdb-cursor-get relation-lr &val-relation-key &val-relation-data MDB-NEXT-DUP))
    (if db-mdb-status-is-success (goto each-data)
      db-mdb-status-expect-notfound))
  (set status.id (mdb-cursor-get relation-lr &val-relation-key &val-relation-data MDB-NEXT-NODUP))
  (goto each-key)
  (label exit
    (set selection:left.current left.current)
    (return status)))

(define (db-relation-read-1010 selection count result)
  (status-t db-relation-selection-t* db-count-t db-relations-t*)
  (db-relation-reader-header selection)
  db-mdb-declare-val-relation-data
  (declare
    relation-lr MDB-cursor*
    left db-ids-t
    label db-ids-t)
  (set
    relation-lr selection:cursor
    left selection:left
    label selection:label)
  (db-mdb-status-require
    (mdb-cursor-get relation-lr &val-relation-key &val-relation-data MDB-GET-CURRENT))
  (set
    (array-get relation-key 0) (i-array-get left)
    (array-get relation-key 1) (i-array-get label))
  (if (db-relation-key-equal relation-key (db-mdb-val->relation-key val-relation-key))
    (goto each-data)
    (label set-key
      (set val-relation-key.mv-data relation-key)
      (set status.id (mdb-cursor-get relation-lr &val-relation-key &val-relation-data MDB-SET-KEY))
      (if db-mdb-status-is-success (goto each-data)
        db-mdb-status-expect-notfound)
      (label next-key
        (i-array-forward left)
        (if (i-array-in-range left)
          (begin
            (set (array-get relation-key 0) (i-array-get left))
            (goto set-key))
          (begin
            (i-array-forward label)
            (if (i-array-in-range label)
              (begin
                (i-array-rewind left)
                (set
                  (array-get relation-key 0) (i-array-get left)
                  (array-get relation-key 1) (i-array-get label))
                (goto set-key))
              notfound-exit))))))
  (label each-data
    stop-if-count-zero
    (if (not skip)
      (begin
        (set
          relation.left (db-pointer->id val-relation-key.mv-data)
          relation.right (db-relation-data->id val-relation-data.mv-data)
          relation.label (db-pointer->id-at val-relation-key.mv-data 1)
          relation.ordinal (db-relation-data->ordinal val-relation-data.mv-data))
        (i-array-add *result relation)))
    reduce-count
    (set status.id (mdb-cursor-get relation-lr &val-relation-key &val-relation-data MDB-NEXT-DUP))
    (if db-mdb-status-is-success (goto each-data)
      (goto next-key)))
  (label exit
    (set
      selection:left.current left.current
      selection:label.current label.current)
    (return status)))

(define (db-relation-read-1100 selection count result)
  (status-t db-relation-selection-t* db-count-t db-relations-t*)
  db-mdb-declare-val-id
  (db-relation-reader-header selection)
  (declare
    relation-rl MDB-cursor*
    left db-ids-t
    right db-ids-t)
  (set
    relation-rl selection:cursor
    left selection:left
    right selection:right)
  (db-mdb-status-require (mdb-cursor-get relation-rl &val-relation-key &val-id MDB-GET-CURRENT))
  (set (array-get relation-key 0) (i-array-get right))
  (if (= (db-pointer->id val-relation-key.mv-data) (array-get relation-key 0)) (goto each-left)
    (label set-range
      (set val-relation-key.mv-data relation-key)
      (set status.id (mdb-cursor-get relation-rl &val-relation-key &val-id MDB-SET-RANGE))
      (label each-right
        (if db-mdb-status-is-success
          (if (= (db-pointer->id val-relation-key.mv-data) (array-get relation-key 0))
            (goto each-left))
          db-mdb-status-expect-notfound)
        (i-array-forward right)
        (if (i-array-in-range right) (set (array-get relation-key 0) (i-array-get right))
          notfound-exit)
        (goto set-range))))
  (label each-left
    stop-if-count-zero
    (set val-id.mv-data left.current)
    (set status.id (mdb-cursor-get relation-rl &val-relation-key &val-id MDB-GET-BOTH))
    (if db-mdb-status-is-success
      (begin
        (if (not skip)
          (begin
            (set
              relation.left (db-pointer->id val-id.mv-data)
              relation.right (db-pointer->id val-relation-key.mv-data)
              relation.label (db-pointer->id-at val-relation-key.mv-data 1))
            (i-array-add *result relation)
            reduce-count)))
      db-mdb-status-expect-notfound)
    (i-array-forward left)
    (if (i-array-in-range left) (goto each-left)
      (i-array-rewind left)))
  (set status.id (mdb-cursor-get relation-rl &val-relation-key &val-id MDB-NEXT-NODUP))
  (goto each-right)
  (label exit
    (set
      selection:left.current left.current
      selection:right.current right.current)
    (return status)))

(define (db-relation-read-1110 selection count result)
  (status-t db-relation-selection-t* db-count-t db-relations-t*)
  (db-relation-reader-header selection)
  db-mdb-declare-val-id
  (declare
    relation-rl MDB-cursor*
    left db-ids-t
    right db-ids-t
    label db-ids-t
    id-left db-id-t)
  (set
    relation-rl selection:cursor
    left selection:left
    right selection:right
    label selection:label
    (array-get relation-key 1) (i-array-get label)
    id-left (i-array-get left)
    (array-get relation-key 0) (i-array-get right))
  (label set-cursor
    (set
      val-relation-key.mv-data relation-key
      val-id.mv-data &id-left)
    (set status.id (mdb-cursor-get relation-rl &val-relation-key &val-id MDB-GET-BOTH))
    (if db-mdb-status-is-success (goto match)
      db-mdb-status-expect-notfound)
    (label next-query
      (i-array-forward right)
      (if (i-array-in-range right)
        (begin
          stop-if-count-zero
          (set (array-get relation-key 0) (i-array-get right))
          (goto set-cursor))
        (begin
          (i-array-rewind right)
          (set (array-get relation-key 0) (i-array-get right))
          (i-array-forward left)
          (if (i-array-in-range left)
            (begin
              stop-if-count-zero
              (set id-left (i-array-get left))
              (goto set-cursor))
            (begin
              (i-array-rewind left)
              (set id-left (i-array-get left))
              (i-array-forward label)
              (if (i-array-in-range label)
                (begin
                  stop-if-count-zero
                  (set (array-get relation-key 1) (i-array-get label))
                  (goto set-cursor))
                notfound-exit)))))))
  (label match
    (if (not skip)
      (begin
        (set
          relation.left (db-pointer->id val-id.mv-data)
          relation.right (db-pointer->id val-relation-key.mv-data)
          relation.label (db-pointer->id-at val-relation-key.mv-data 1))
        (i-array-add *result relation)))
    reduce-count
    (goto next-query))
  (label exit
    (set
      selection:left.current left.current
      selection:right.current right.current
      selection:label.current label.current)
    (return status)))

(define (db-relation-read-1001-1101 selection count result)
  (status-t db-relation-selection-t* db-count-t db-relations-t*)
  (db-relation-reader-header selection)
  (set (array-get relation-key 1) 0)
  db-mdb-declare-val-relation-data
  (db-declare-relation-data relation-data)
  (declare
    relation-lr MDB-cursor*
    left db-ids-t
    right imht-set-t*)
  (set
    relation-lr selection:cursor
    left selection:left
    right selection:ids-set
    (array-get relation-key 0) (i-array-get left))
  (db-relation-reader-define-ordinal-variables selection)
  (db-relation-data-set-ordinal relation-data ordinal-min)
  (db-mdb-status-require
    (mdb-cursor-get relation-lr &val-relation-key &val-relation-data MDB-GET-CURRENT))
  (sc-comment "already set from select or previous call")
  (if (= (db-pointer->id val-relation-key.mv-data) (array-get relation-key 0)) (goto each-data))
  (label each-left
    (set val-relation-key.mv-data relation-key)
    (set status.id (mdb-cursor-get relation-lr &val-relation-key &val-relation-data MDB-SET-RANGE))
    (label each-key
      (if db-mdb-status-is-success
        (if (= (db-pointer->id val-relation-key.mv-data) (array-get relation-key 0))
          (begin
            (set
              val-relation-data.mv-data relation-data
              status.id
              (mdb-cursor-get relation-lr &val-relation-key &val-relation-data MDB-GET-BOTH-RANGE))
            (if db-mdb-status-is-success (goto each-data)
              db-mdb-status-expect-notfound)
            (set status.id
              (mdb-cursor-get relation-lr &val-relation-key &val-relation-data MDB-NEXT-NODUP))
            (goto each-key)))
        db-mdb-status-expect-notfound)
      (i-array-forward left)
      (if (i-array-in-range left) (set (array-get relation-key 0) (i-array-get left))
        notfound-exit)
      (goto each-left)))
  (label each-data
    stop-if-count-zero
    (if
      (or (not ordinal-max) (<= (db-relation-data->ordinal val-relation-data.mv-data) ordinal-max))
      (begin
        (sc-comment "ordinal-min is checked because the set-range can be skipped")
        (if
          (and
            (or
              (not ordinal-min)
              (>= (db-relation-data->ordinal val-relation-data.mv-data) ordinal-min))
            (or
              (not right) (imht-set-contains right (db-relation-data->id val-relation-data.mv-data))))
          (begin
            (if (not skip)
              (begin
                (set
                  relation.left (db-pointer->id val-relation-key.mv-data)
                  relation.label (db-pointer->id-at val-relation-key.mv-data 1)
                  relation.ordinal (db-relation-data->ordinal val-relation-data.mv-data)
                  relation.right (db-relation-data->id val-relation-data.mv-data))
                (i-array-add *result relation)))
            reduce-count))
        (set status.id
          (mdb-cursor-get relation-lr &val-relation-key &val-relation-data MDB-NEXT-DUP))
        (if db-mdb-status-is-success (goto each-data)
          db-mdb-status-expect-notfound))))
  (set status.id (mdb-cursor-get relation-lr &val-relation-key &val-relation-data MDB-NEXT-NODUP))
  (goto each-key)
  (label exit
    (set selection:left.current left.current)
    (return status)))

(define (db-relation-read-1011-1111 selection count result)
  (status-t db-relation-selection-t* db-count-t db-relations-t*)
  (db-relation-reader-header selection)
  (db-declare-relation-data relation-data)
  db-mdb-declare-val-relation-data
  (declare
    relation-lr MDB-cursor*
    left db-ids-t
    label db-ids-t
    right imht-set-t*)
  (set
    relation-lr selection:cursor
    left selection:left
    label selection:label
    right selection:ids-set
    (array-get relation-key 0) (i-array-get left)
    (array-get relation-key 1) (i-array-get label))
  (db-relation-reader-define-ordinal-variables selection)
  (db-relation-data-set-ordinal relation-data ordinal-min)
  (db-mdb-status-require
    (mdb-cursor-get relation-lr &val-relation-key &val-relation-data MDB-GET-CURRENT))
  (if
    (and
      (= (db-pointer->id val-relation-key.mv-data) (array-get relation-key 0))
      (= (db-pointer->id-at val-relation-key.mv-data 1) (array-get relation-key 1)))
    (goto each-data))
  (label set-key
    (set
      val-relation-key.mv-data relation-key
      val-relation-data.mv-data relation-data
      status.id (mdb-cursor-get relation-lr &val-relation-key &val-relation-data MDB-GET-BOTH-RANGE))
    (if db-mdb-status-is-success (goto each-data)
      (begin
        db-mdb-status-expect-notfound
        (label each-key
          (i-array-forward left)
          (if (i-array-in-range left) (set (array-get relation-key 0) (i-array-get left))
            (begin
              (i-array-forward label)
              (if (i-array-in-range label)
                (begin
                  (set (array-get relation-key 1) (i-array-get label))
                  (i-array-rewind left)
                  (set (array-get relation-key 0) (i-array-get left)))
                notfound-exit)))
          (goto set-key)))))
  (label each-data
    stop-if-count-zero
    (if
      (or (not ordinal-max) (<= (db-relation-data->ordinal val-relation-data.mv-data) ordinal-max))
      (begin
        (sc-comment "ordinal-min is checked because the get-both-range can be skipped")
        (if
          (and
            (or
              (not ordinal-min)
              (>= (db-relation-data->ordinal val-relation-data.mv-data) ordinal-min))
            (or
              (not right) (imht-set-contains right (db-relation-data->id val-relation-data.mv-data))))
          (begin
            (if (not skip)
              (begin
                (set
                  relation.left (db-pointer->id val-relation-key.mv-data)
                  relation.label (db-pointer->id-at val-relation-key.mv-data 1)
                  relation.ordinal (db-relation-data->ordinal val-relation-data.mv-data)
                  relation.right (db-relation-data->id val-relation-data.mv-data))
                (i-array-add *result relation)))
            reduce-count))
        (set status.id
          (mdb-cursor-get relation-lr &val-relation-key &val-relation-data MDB-NEXT-DUP))
        (if db-mdb-status-is-success (goto each-data)
          db-mdb-status-expect-notfound)))
    (goto each-key))
  (label exit
    (set
      selection:left.current left.current
      selection:label.current label.current)
    (return status)))

(define (db-relation-read-0010 selection count result)
  (status-t db-relation-selection-t* db-count-t db-relations-t*)
  (db-relation-reader-header selection)
  db-mdb-declare-val-id
  db-mdb-declare-val-id-2
  db-mdb-declare-val-relation-data
  (declare
    relation-ll MDB-cursor*
    relation-lr MDB-cursor*
    label db-ids-t
    id-left db-id-t
    id-label db-id-t)
  (set
    relation-ll selection:cursor
    relation-lr selection:cursor-2
    label selection:label)
  (db-mdb-status-require (mdb-cursor-get relation-ll &val-id &val-id-2 MDB-GET-CURRENT))
  (db-mdb-status-require
    (mdb-cursor-get relation-lr &val-relation-key &val-relation-data MDB-GET-CURRENT))
  (if (i-array-in-range label) (set id-label (i-array-get label))
    notfound-exit)
  (if (= id-label (db-pointer->id val-id.mv-data))
    (begin
      (set (array-get relation-key 1) id-label)
      (goto each-label-data))
    (label set-label-key
      (set val-id.mv-data &id-label)
      (set status.id (mdb-cursor-get relation-ll &val-id &val-id-2 MDB-SET-KEY))
      (if db-mdb-status-is-success
        (begin
          (set (array-get relation-key 1) id-label)
          (goto each-label-data))
        (begin
          db-mdb-status-expect-notfound
          (i-array-forward label)
          (if (i-array-in-range label) (set id-label (i-array-get label))
            notfound-exit)
          (goto set-label-key)))))
  (label each-label-data
    (set id-left (db-pointer->id val-id-2.mv-data))
    (if (= id-left (db-pointer->id val-relation-key.mv-data)) (goto each-left-data)
      (begin
        (set (array-get relation-key 0) id-left)
        (set val-relation-key.mv-data relation-key)
        (set status.id
          (mdb-cursor-get relation-lr &val-relation-key &val-relation-data MDB-SET-KEY))
        (if db-mdb-status-is-success (goto each-left-data)
          (goto exit))))
    (label each-left-data
      stop-if-count-zero
      (if (not skip)
        (begin
          (set
            relation.left id-left
            relation.right (db-relation-data->id val-relation-data.mv-data)
            relation.label id-label)
          (i-array-add *result relation)))
      reduce-count
      (set status.id (mdb-cursor-get relation-lr &val-relation-key &val-relation-data MDB-NEXT-DUP))
      (if db-mdb-status-is-success (goto each-left-data)
        db-mdb-status-expect-notfound))
    (set status.id (mdb-cursor-get relation-ll &val-id &val-id-2 MDB-NEXT-DUP))
    (if db-mdb-status-is-success (goto each-label-data)
      (begin
        (i-array-forward label)
        (if (i-array-in-range label) (set id-label (i-array-get label))
          notfound-exit)
        (goto set-label-key))))
  (label exit
    (set selection:label.current label.current)
    (return status)))

(define (db-relation-read-0110 selection count result)
  (status-t db-relation-selection-t* db-count-t db-relations-t*)
  (db-relation-reader-header selection)
  db-mdb-declare-val-id
  (declare
    relation-rl MDB-cursor*
    label db-ids-t
    right db-ids-t)
  (set
    relation-rl selection:cursor
    label selection:label
    right selection:right)
  (db-mdb-status-require (mdb-cursor-get relation-rl &val-relation-key &val-id MDB-GET-CURRENT))
  (set
    (array-get relation-key 1) (i-array-get label)
    (array-get relation-key 0) (i-array-get right))
  (if (db-relation-key-equal relation-key (db-mdb-val->relation-key val-relation-key))
    (goto each-data)
    (label set-key
      (set
        val-relation-key.mv-data relation-key
        status.id (mdb-cursor-get relation-rl &val-relation-key &val-id MDB-SET-KEY))
      (if db-mdb-status-is-success (goto each-data)
        (label each-key
          db-mdb-status-expect-notfound
          (i-array-forward right)
          (if (i-array-in-range right) (set (array-get relation-key 0) (i-array-get right))
            (begin
              (i-array-forward label)
              (if (i-array-in-range label)
                (begin
                  (set (array-get relation-key 1) (i-array-get label))
                  (i-array-rewind right)
                  (set (array-get relation-key 0) (i-array-get right)))
                notfound-exit)))
          (goto set-key)))))
  (label each-data
    stop-if-count-zero
    (if (not skip)
      (begin
        (set
          relation.left (db-pointer->id val-id.mv-data)
          relation.right (array-get relation-key 0)
          relation.label (array-get relation-key 1))
        (i-array-add *result relation)))
    reduce-count
    (set status.id (mdb-cursor-get relation-rl &val-relation-key &val-id MDB-NEXT-DUP))
    (if db-mdb-status-is-success (goto each-data)
      (goto each-key)))
  (label exit
    (set
      selection:right.current right.current
      selection:label.current label.current)
    (return status)))

(define (db-relation-read-0100 selection count result)
  (status-t db-relation-selection-t* db-count-t db-relations-t*)
  (db-relation-reader-header selection)
  db-mdb-declare-val-id
  (declare
    relation-rl MDB-cursor*
    right db-ids-t)
  (set
    relation-rl selection:cursor
    right selection:right)
  (db-mdb-status-require (mdb-cursor-get relation-rl &val-relation-key &val-id MDB-GET-CURRENT))
  (set (array-get relation-key 0) (i-array-get right))
  (if (= (array-get relation-key 0) (db-pointer->id val-relation-key.mv-data)) (goto each-key)
    (label set-range
      (set val-relation-key.mv-data relation-key)
      (set status.id (mdb-cursor-get relation-rl &val-relation-key &val-id MDB-SET-RANGE))
      (if db-mdb-status-is-success
        (if (= (array-get relation-key 0) (db-pointer->id val-relation-key.mv-data))
          (goto each-key))
        db-mdb-status-expect-notfound)
      (i-array-forward right)
      (if (i-array-in-range right) (set (array-get relation-key 0) (i-array-get right))
        notfound-exit)
      (goto set-range)))
  (label each-key
    (label each-data
      stop-if-count-zero
      (if (not skip)
        (begin
          (set
            relation.left (db-pointer->id val-id.mv-data)
            relation.right (db-pointer->id val-relation-key.mv-data)
            relation.label (db-pointer->id-at val-relation-key.mv-data 1))
          (i-array-add *result relation)))
      reduce-count
      (set status.id (mdb-cursor-get relation-rl &val-relation-key &val-id MDB-NEXT-DUP))
      (if db-mdb-status-is-success (goto each-data)
        db-mdb-status-expect-notfound))
    (set status.id (mdb-cursor-get relation-rl &val-relation-key &val-id MDB-NEXT-NODUP))
    (if db-mdb-status-is-success
      (if (= (array-get relation-key 0) (db-pointer->id val-relation-key.mv-data)) (goto each-key))
      db-mdb-status-expect-notfound)
    (i-array-forward right)
    (if (i-array-in-range right) (set (array-get relation-key 0) (i-array-get right))
      notfound-exit)
    (goto set-range))
  (label exit
    (set selection:right.current right.current)
    (return status)))

(define (db-relation-read-0000 selection count result)
  (status-t db-relation-selection-t* db-count-t db-relations-t*)
  (db-relation-reader-header-0000 selection)
  db-mdb-declare-val-relation-data
  (declare relation-lr MDB-cursor*)
  (set relation-lr selection:cursor)
  (db-mdb-status-require
    (mdb-cursor-get relation-lr &val-relation-key &val-relation-data MDB-GET-CURRENT))
  (label each-key
    (label each-data
      stop-if-count-zero
      (if (not skip)
        (begin
          (set
            relation.left (db-pointer->id val-relation-key.mv-data)
            relation.right (db-relation-data->id val-relation-data.mv-data)
            relation.label (db-pointer->id-at val-relation-key.mv-data 1)
            relation.ordinal (db-relation-data->ordinal val-relation-data.mv-data))
          (i-array-add *result relation)))
      reduce-count
      (set status.id (mdb-cursor-get relation-lr &val-relation-key &val-relation-data MDB-NEXT-DUP))
      (if db-mdb-status-is-success (goto each-data)
        db-mdb-status-expect-notfound))
    (set status.id (mdb-cursor-get relation-lr &val-relation-key &val-relation-data MDB-NEXT-NODUP))
    (if db-mdb-status-is-success (goto each-key)
      db-mdb-status-expect-notfound))
  (label exit
    (return status)))

(define (db-relation-select txn left right label ordinal selection)
  (status-t db-txn-t db-ids-t* db-ids-t* db-ids-t* db-ordinal-condition-t* db-relation-selection-t*)
  "prepare the selection and select the reader.
  readers are specialised for filter combinations.
  the 1/0 pattern at the end of reader names corresponds to the filter combination the reader is supposed to handle.
  1 stands for filter given, 0 stands for not given. order is left, right, label, ordinal.
  readers always leave cursors at a valid entry, usually the next entry unless the results have been exhausted.
  left/right/label ids pointer can be zero which means they are unused.
  internally in the selection if unset i-array-in-range and i-array-length is zero"
  status-declare
  db-mdb-declare-val-null
  (declare right-set imht-set-t*)
  (db-mdb-cursor-declare relation-lr)
  (db-mdb-cursor-declare relation-rl)
  (db-mdb-cursor-declare relation-ll)
  (if left (set selection:left *left)
    (i-array-set-null selection:left))
  (if right (set selection:right *right)
    (i-array-set-null selection:right))
  (if label (set selection:label *label)
    (i-array-set-null selection:label))
  (if ordinal (set selection:ordinal *ordinal))
  (set
    selection:cursor 0
    selection:cursor-2 0
    selection:options 0
    selection:ids-set 0)
  (if left
    (if ordinal
      (begin
        (if right
          (begin
            (status-require (db-ids->set *right &right-set))
            (set
              selection:ids-set right-set
              selection:options (bit-or db-relation-selection-flag-is-set-right selection:options))))
        (db-relation-select-cursor-initialise relation-lr selection cursor)
        (if label (set selection:reader db-relation-read-1011-1111)
          (set selection:reader db-relation-read-1001-1101)))
      (if right
        (begin
          (db-relation-select-cursor-initialise relation-rl selection cursor)
          (if label (set selection:reader db-relation-read-1110)
            (set selection:reader db-relation-read-1100)))
        (begin
          (db-relation-select-cursor-initialise relation-lr selection cursor)
          (if label (set selection:reader db-relation-read-1010)
            (set selection:reader db-relation-read-1000)))))
    (if right
      (begin
        (db-relation-select-cursor-initialise relation-rl selection cursor)
        (set selection:reader
          (if* label db-relation-read-0110
            db-relation-read-0100)))
      (if label
        (begin
          (db-relation-select-cursor-initialise relation-ll selection cursor)
          (db-relation-select-cursor-initialise relation-lr selection cursor-2)
          (set selection:reader db-relation-read-0010))
        (begin
          (db-relation-select-cursor-initialise relation-lr selection cursor)
          (set selection:reader db-relation-read-0000)))))
  (label exit
    db-mdb-status-notfound-if-notfound
    (return status)))

(define (db-relation-skip selection count) (status-t db-relation-selection-t* db-count-t)
  "skip the next count result matches"
  status-declare
  (set
    selection:options (bit-or db-selection-flag-skip selection:options)
    status ((convert-type selection:reader db-relation-reader-t) selection count 0)
    selection:options (bit-xor db-selection-flag-skip selection:options))
  (return status))

(define (db-relation-read selection count result)
  (status-t db-relation-selection-t* db-count-t db-relations-t*)
  "result memory is to be allocated by the caller"
  status-declare
  (set status ((convert-type selection:reader db-relation-reader-t) selection count result))
  db-mdb-status-notfound-if-notfound
  (return status))

(define (db-relation-selection-finish selection) (void db-relation-selection-t*)
  (db-mdb-cursor-close-if-active selection:cursor)
  (db-mdb-cursor-close-if-active selection:cursor-2)
  (if (bit-and db-relation-selection-flag-is-set-right selection:options)
    (begin
      (imht-set-destroy selection:ids-set)
      (set selection:ids-set 0))))