(pre-define notfound-exit (status-set-both-goto db-status-group-db db-status-id-notfound))

(pre-define (db-graph-select-cursor-initialise name state state-field-name)
  (begin
    (db-mdb-status-require (db-mdb-env-cursor-open txn name))
    (db-mdb-status-require (mdb-cursor-get name &val-null &val-null MDB-FIRST))
    (if (not db-mdb-status-is-success)
      (begin
        db-mdb-status-expect-notfound
        (status-set-both-goto db-status-group-db db-status-id-notfound)))
    (set state:state-field-name name)))

(pre-define (db-graph-reader-header state)
  (begin
    status-declare
    db-mdb-declare-val-graph-key
    (db-declare-graph-key graph-key)
    (db-declare-graph-record record)
    (declare skip boolean)
    (set skip (bit-and db-selection-flag-skip state:options))))

(pre-define (db-graph-reader-header-0000 state)
  (begin
    status-declare
    db-mdb-declare-val-graph-key
    (db-declare-graph-record record)
    (declare skip boolean)
    (set skip (bit-and db-selection-flag-skip state:options))))

(pre-define (db-graph-reader-get-ordinal-data state)
  (begin
    (define ordinal-min db-ordinal-t state:ordinal:min)
    (define ordinal-max db-ordinal-t state:ordinal:max)))

(define (db-graph-read-1000 state count result)
  (status-t db-graph-selection-t* db-count-t db-graph-records-t*)
  (db-graph-reader-header state)
  db-mdb-declare-val-graph-data
  (declare
    graph-lr MDB-cursor*
    left db-ids-t)
  (set
    graph-lr state:cursor
    left state:left)
  (db-mdb-status-require (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-GET-CURRENT))
  (set (array-get graph-key 0) (i-array-get left))
  (if (db-id-equal (db-pointer->id val-graph-key.mv-data) (array-get graph-key 0)) (goto each-data)
    (label set-range
      (set val-graph-key.mv-data graph-key)
      (set status.id (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-SET-RANGE))
      (label each-key
        (if db-mdb-status-is-success
          (if (db-id-equal (db-pointer->id val-graph-key.mv-data) (array-get graph-key 0))
            (goto each-data))
          db-mdb-status-expect-notfound)
        (i-array-forward left)
        (if (i-array-in-range left)
          (begin
            (set (array-get graph-key 0) (i-array-get left))
            (goto set-range))
          notfound-exit))))
  (label each-data
    stop-if-count-zero
    (if (not skip)
      (begin
        (set
          record.left (db-pointer->id val-graph-key.mv-data)
          record.right (db-graph-data->id val-graph-data.mv-data)
          record.label (db-pointer->id-at val-graph-key.mv-data 1)
          record.ordinal (db-graph-data->ordinal val-graph-data.mv-data))
        (i-array-add *result record)))
    reduce-count
    (set status.id (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-NEXT-DUP))
    (if db-mdb-status-is-success (goto each-data)
      db-mdb-status-expect-notfound))
  (set status.id (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-NEXT-NODUP))
  (goto each-key)
  (label exit
    (set
      state:left.current left.current
      state:status status)
    (return status)))

(define (db-graph-read-1010 state count result)
  (status-t db-graph-selection-t* db-count-t db-graph-records-t*)
  (db-graph-reader-header state)
  db-mdb-declare-val-graph-data
  (declare
    graph-lr MDB-cursor*
    left db-ids-t
    label db-ids-t)
  (set
    graph-lr state:cursor
    left state:left
    label state:label)
  (db-mdb-status-require (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-GET-CURRENT))
  (set
    (array-get graph-key 0) (i-array-get left)
    (array-get graph-key 1) (i-array-get label))
  (if (db-graph-key-equal graph-key (db-mdb-val->graph-key val-graph-key)) (goto each-data)
    (label set-key
      (set val-graph-key.mv-data graph-key)
      (set status.id (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-SET-KEY))
      (if db-mdb-status-is-success (goto each-data)
        db-mdb-status-expect-notfound)
      (label next-key
        (i-array-forward left)
        (if (i-array-in-range left)
          (begin
            (set (array-get graph-key 0) (i-array-get left))
            (goto set-key))
          (begin
            (i-array-forward label)
            (if (i-array-in-range label)
              (begin
                (i-array-rewind left)
                (set
                  (array-get graph-key 0) (i-array-get left)
                  (array-get graph-key 1) (i-array-get label))
                (goto set-key))
              notfound-exit))))))
  (label each-data
    stop-if-count-zero
    (if (not skip)
      (begin
        (set
          record.left (db-pointer->id val-graph-key.mv-data)
          record.right (db-graph-data->id val-graph-data.mv-data)
          record.label (db-pointer->id-at val-graph-key.mv-data 1)
          record.ordinal (db-graph-data->ordinal val-graph-data.mv-data))
        (i-array-add *result record)))
    reduce-count
    (set status.id (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-NEXT-DUP))
    (if db-mdb-status-is-success (goto each-data)
      (goto next-key)))
  (label exit
    (set
      state:left.current left.current
      state:label.current label.current
      state:status status)
    (return status)))

(define (db-graph-read-1100 state count result)
  (status-t db-graph-selection-t* db-count-t db-graph-records-t*)
  db-mdb-declare-val-id
  (db-graph-reader-header state)
  (declare
    graph-rl MDB-cursor*
    left db-ids-t
    right db-ids-t)
  (set
    graph-rl state:cursor
    left state:left
    right state:right)
  (db-mdb-status-require (mdb-cursor-get graph-rl &val-graph-key &val-id MDB-GET-CURRENT))
  (set (array-get graph-key 0) (i-array-get right))
  (if (db-id-equal (db-pointer->id val-graph-key.mv-data) (array-get graph-key 0)) (goto each-left)
    (label set-range
      (set val-graph-key.mv-data graph-key)
      (set status.id (mdb-cursor-get graph-rl &val-graph-key &val-id MDB-SET-RANGE))
      (label each-right
        (if db-mdb-status-is-success
          (if (db-id-equal (db-pointer->id val-graph-key.mv-data) (array-get graph-key 0))
            (goto each-left))
          db-mdb-status-expect-notfound)
        (i-array-forward right)
        (if (i-array-in-range right) (set (array-get graph-key 0) (i-array-get right))
          notfound-exit)
        (goto set-range))))
  (label each-left
    stop-if-count-zero
    (set val-id.mv-data left.current)
    (set status.id (mdb-cursor-get graph-rl &val-graph-key &val-id MDB-GET-BOTH))
    (if db-mdb-status-is-success
      (begin
        (if (not skip)
          (begin
            (set
              record.left (db-pointer->id val-id.mv-data)
              record.right (db-pointer->id val-graph-key.mv-data)
              record.label (db-pointer->id-at val-graph-key.mv-data 1))
            (i-array-add *result record)
            reduce-count)))
      db-mdb-status-expect-notfound)
    (i-array-forward left)
    (if (i-array-in-range left) (goto each-left)
      (i-array-rewind left)))
  (set status.id (mdb-cursor-get graph-rl &val-graph-key &val-id MDB-NEXT-NODUP))
  (goto each-right)
  (label exit
    (set
      state:left.current left.current
      state:right.current right.current
      state:status status)
    (return status)))

(define (db-graph-read-1110 state count result)
  (status-t db-graph-selection-t* db-count-t db-graph-records-t*)
  (db-graph-reader-header state)
  db-mdb-declare-val-id
  (declare
    graph-rl MDB-cursor*
    left db-ids-t
    right db-ids-t
    label db-ids-t
    id-left db-id-t)
  (set
    graph-rl state:cursor
    left state:left
    right state:right
    label state:label
    (array-get graph-key 1) (i-array-get label)
    id-left (i-array-get left)
    (array-get graph-key 0) (i-array-get right))
  (label set-cursor
    (set
      val-graph-key.mv-data graph-key
      val-id.mv-data &id-left)
    (set status.id (mdb-cursor-get graph-rl &val-graph-key &val-id MDB-GET-BOTH))
    (if db-mdb-status-is-success (goto match)
      db-mdb-status-expect-notfound)
    (label next-query
      (i-array-forward right)
      (if (i-array-in-range right)
        (begin
          stop-if-count-zero
          (set (array-get graph-key 0) (i-array-get right))
          (goto set-cursor))
        (begin
          (i-array-rewind right)
          (set (array-get graph-key 0) (i-array-get right))
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
                  (set (array-get graph-key 1) (i-array-get label))
                  (goto set-cursor))
                notfound-exit)))))))
  (label match
    (if (not skip)
      (begin
        (set
          record.left (db-pointer->id val-id.mv-data)
          record.right (db-pointer->id val-graph-key.mv-data)
          record.label (db-pointer->id-at val-graph-key.mv-data 1))
        (i-array-add *result record)))
    reduce-count
    (goto next-query))
  (label exit
    (set
      state:left.current left.current
      state:right.current right.current
      state:label.current label.current
      state:status status)
    (return status)))

(define (db-graph-read-1001-1101 state count result)
  (status-t db-graph-selection-t* db-count-t db-graph-records-t*)
  (db-graph-reader-header state)
  db-mdb-declare-val-graph-data
  (db-declare-graph-data graph-data)
  (declare
    graph-lr MDB-cursor*
    left db-ids-t
    right imht-set-t*)
  (set
    graph-lr state:cursor
    left state:left
    right state:ids-set)
  (db-graph-reader-get-ordinal-data state)
  (db-graph-data-set-ordinal graph-data ordinal-min)
  (db-mdb-status-require (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-GET-CURRENT))
  (if (i-array-in-range left) (set (array-get graph-key 0) (i-array-get left))
    notfound-exit)
  (if (db-id-equal (db-pointer->id val-graph-key.mv-data) (array-get graph-key 0)) (goto each-data))
  (label each-left
    (set val-graph-key.mv-data graph-key)
    (set status.id (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-SET-RANGE))
    (label each-key
      (if db-mdb-status-is-success
        (if (db-id-equal (db-pointer->id val-graph-key.mv-data) (array-get graph-key 0))
          (begin
            (set val-graph-data.mv-data graph-data)
            (set status.id
              (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-GET-BOTH-RANGE))
            (if db-mdb-status-is-success (goto each-data)
              db-mdb-status-expect-notfound)
            (set status.id (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-NEXT-NODUP))
            (goto each-key)))
        db-mdb-status-expect-notfound)
      (i-array-forward left)
      (if (i-array-in-range left) (set (array-get graph-key 0) (i-array-get left))
        notfound-exit)
      (goto each-left)))
  (label each-data
    stop-if-count-zero
    (if
      (and
        (or (not ordinal-min) (>= (db-graph-data->ordinal val-graph-data.mv-data) ordinal-min))
        (or (not ordinal-max) (<= (db-graph-data->ordinal val-graph-data.mv-data) ordinal-max)))
      (begin
        (if (or (not right) (imht-set-contains right (db-graph-data->id val-graph-data.mv-data)))
          (begin
            (if (not skip)
              (begin
                (set
                  record.left (db-pointer->id val-graph-key.mv-data)
                  record.label (db-pointer->id-at val-graph-key.mv-data 1)
                  record.ordinal (db-graph-data->ordinal val-graph-data.mv-data)
                  record.right (db-graph-data->id val-graph-data.mv-data))
                (i-array-add *result record)))
            reduce-count))
        (set status.id (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-NEXT-DUP))
        (if db-mdb-status-is-success (goto each-data)
          db-mdb-status-expect-notfound))))
  (set status.id (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-NEXT-NODUP))
  (goto each-key)
  (label exit
    (set
      state:left.current left.current
      state:status status)
    (return status)))

(define (db-graph-read-1011-1111 state count result)
  (status-t db-graph-selection-t* db-count-t db-graph-records-t*)
  (db-graph-reader-header state)
  (db-declare-graph-data graph-data)
  db-mdb-declare-val-graph-data
  (declare
    graph-lr MDB-cursor*
    left db-ids-t
    label db-ids-t
    right imht-set-t*)
  (set
    graph-lr state:cursor
    left state:left
    label state:label
    right state:ids-set)
  (db-graph-reader-get-ordinal-data state)
  (db-graph-data-set-ordinal graph-data ordinal-min)
  (db-mdb-status-require (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-GET-CURRENT))
  (set
    (array-get graph-key 0) (i-array-get left)
    (array-get graph-key 1) (i-array-get label))
  (if (db-graph-key-equal graph-key (db-mdb-val->graph-key val-graph-key)) (goto each-data)
    (label set-key
      (set
        val-graph-key.mv-data graph-key
        val-graph-data.mv-data graph-data)
      (set status.id (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-GET-BOTH-RANGE))
      (if db-mdb-status-is-success (goto each-data)
        (begin
          db-mdb-status-expect-notfound
          (label each-key
            (i-array-forward left)
            (if (i-array-in-range left) (set (array-get graph-key 0) (i-array-get left))
              (begin
                (i-array-forward label)
                (if (i-array-in-range label)
                  (begin
                    (set (array-get graph-key 1) (i-array-get label))
                    (i-array-rewind left)
                    (set (array-get graph-key 0) (i-array-get left)))
                  notfound-exit)))
            (goto set-key))))))
  (label each-data
    stop-if-count-zero
    (if (or (not ordinal-max) (<= (db-graph-data->ordinal val-graph-data.mv-data) ordinal-max))
      (begin
        (if (or (not right) (imht-set-contains right (db-graph-data->id val-graph-data.mv-data)))
          (begin
            (if (not skip)
              (begin
                (set
                  record.left (db-pointer->id val-graph-key.mv-data)
                  record.right (db-graph-data->id val-graph-data.mv-data)
                  record.label (db-pointer->id-at val-graph-key.mv-data 1)
                  record.ordinal (db-graph-data->ordinal val-graph-data.mv-data))
                (i-array-add *result record)))
            reduce-count))
        (set status.id (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-NEXT-DUP))
        (if db-mdb-status-is-success (goto each-data)
          (goto each-key)))
      (goto each-key)))
  (label exit
    (set
      state:left.current left.current
      state:label.current label.current
      state:status status)
    (return status)))

(define (db-graph-read-0010 state count result)
  (status-t db-graph-selection-t* db-count-t db-graph-records-t*)
  (db-graph-reader-header state)
  db-mdb-declare-val-id
  db-mdb-declare-val-id-2
  db-mdb-declare-val-graph-data
  (declare
    graph-ll MDB-cursor*
    graph-lr MDB-cursor*
    label db-ids-t
    id-left db-id-t
    id-label db-id-t)
  (set
    graph-ll state:cursor
    graph-lr state:cursor-2
    label state:label)
  (db-mdb-status-require (mdb-cursor-get graph-ll &val-id &val-id-2 MDB-GET-CURRENT))
  (db-mdb-status-require (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-GET-CURRENT))
  (if (i-array-in-range label) (set id-label (i-array-get label))
    notfound-exit)
  (if (db-id-equal id-label (db-pointer->id val-id.mv-data))
    (begin
      (set (array-get graph-key 1) id-label)
      (goto each-label-data))
    (label set-label-key
      (set val-id.mv-data &id-label)
      (set status.id (mdb-cursor-get graph-ll &val-id &val-id-2 MDB-SET-KEY))
      (if db-mdb-status-is-success
        (begin
          (set (array-get graph-key 1) id-label)
          (goto each-label-data))
        (begin
          db-mdb-status-expect-notfound
          (i-array-forward label)
          (if (i-array-in-range label) (set id-label (i-array-get label))
            notfound-exit)
          (goto set-label-key)))))
  (label each-label-data
    (set id-left (db-pointer->id val-id-2.mv-data))
    (if (db-id-equal id-left (db-pointer->id val-graph-key.mv-data)) (goto each-left-data)
      (begin
        (set (array-get graph-key 0) id-left)
        (set val-graph-key.mv-data graph-key)
        (set status.id (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-SET-KEY))
        (if db-mdb-status-is-success (goto each-left-data)
          (goto exit))))
    (label each-left-data
      stop-if-count-zero
      (if (not skip)
        (begin
          (set
            record.left id-left
            record.right (db-graph-data->id val-graph-data.mv-data)
            record.label id-label)
          (i-array-add *result record)))
      reduce-count
      (set status.id (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-NEXT-DUP))
      (if db-mdb-status-is-success (goto each-left-data)
        db-mdb-status-expect-notfound))
    (set status.id (mdb-cursor-get graph-ll &val-id &val-id-2 MDB-NEXT-DUP))
    (if db-mdb-status-is-success (goto each-label-data)
      (begin
        (i-array-forward label)
        (if (i-array-in-range label) (set id-label (i-array-get label))
          notfound-exit)
        (goto set-label-key))))
  (label exit
    (set
      state:status status
      state:label.current label.current)
    (return status)))

(define (db-graph-read-0110 state count result)
  (status-t db-graph-selection-t* db-count-t db-graph-records-t*)
  (db-graph-reader-header state)
  db-mdb-declare-val-id
  (declare
    graph-rl MDB-cursor*
    label db-ids-t
    right db-ids-t)
  (set
    graph-rl state:cursor
    label state:label
    right state:right)
  (db-mdb-status-require (mdb-cursor-get graph-rl &val-graph-key &val-id MDB-GET-CURRENT))
  (set
    (array-get graph-key 1) (i-array-get label)
    (array-get graph-key 0) (i-array-get right))
  (if (db-graph-key-equal graph-key (db-mdb-val->graph-key val-graph-key)) (goto each-data)
    (label set-key
      (set
        val-graph-key.mv-data graph-key
        status.id (mdb-cursor-get graph-rl &val-graph-key &val-id MDB-SET-KEY))
      (if db-mdb-status-is-success (goto each-data)
        (label each-key
          db-mdb-status-expect-notfound
          (i-array-forward right)
          (if (i-array-in-range right) (set (array-get graph-key 0) (i-array-get right))
            (begin
              (i-array-forward label)
              (if (i-array-in-range label)
                (begin
                  (set (array-get graph-key 1) (i-array-get label))
                  (i-array-rewind right)
                  (set (array-get graph-key 0) (i-array-get right)))
                notfound-exit)))
          (goto set-key)))))
  (label each-data
    stop-if-count-zero
    (if (not skip)
      (begin
        (set
          record.left (db-pointer->id val-id.mv-data)
          record.right (array-get graph-key 0)
          record.label (array-get graph-key 1))
        (i-array-add *result record)))
    reduce-count
    (set status.id (mdb-cursor-get graph-rl &val-graph-key &val-id MDB-NEXT-DUP))
    (if db-mdb-status-is-success (goto each-data)
      (goto each-key)))
  (label exit
    (set
      state:right.current right.current
      state:label.current label.current
      state:status status)
    (return status)))

(define (db-graph-read-0100 state count result)
  (status-t db-graph-selection-t* db-count-t db-graph-records-t*)
  (db-graph-reader-header state)
  db-mdb-declare-val-id
  (declare
    graph-rl MDB-cursor*
    right db-ids-t)
  (set
    graph-rl state:cursor
    right state:right)
  (db-mdb-status-require (mdb-cursor-get graph-rl &val-graph-key &val-id MDB-GET-CURRENT))
  (set (array-get graph-key 0) (i-array-get right))
  (if (db-id-equal (array-get graph-key 0) (db-pointer->id val-graph-key.mv-data)) (goto each-key)
    (label set-range
      (set val-graph-key.mv-data graph-key)
      (set status.id (mdb-cursor-get graph-rl &val-graph-key &val-id MDB-SET-RANGE))
      (if db-mdb-status-is-success
        (if (db-id-equal (array-get graph-key 0) (db-pointer->id val-graph-key.mv-data))
          (goto each-key))
        db-mdb-status-expect-notfound)
      (i-array-forward right)
      (if (i-array-in-range right) (set (array-get graph-key 0) (i-array-get right))
        notfound-exit)
      (goto set-range)))
  (label each-key
    (label each-data
      stop-if-count-zero
      (if (not skip)
        (begin
          (set
            record.left (db-pointer->id val-id.mv-data)
            record.right (db-pointer->id val-graph-key.mv-data)
            record.label (db-pointer->id-at val-graph-key.mv-data 1))
          (i-array-add *result record)))
      reduce-count
      (set status.id (mdb-cursor-get graph-rl &val-graph-key &val-id MDB-NEXT-DUP))
      (if db-mdb-status-is-success (goto each-data)
        db-mdb-status-expect-notfound))
    (set status.id (mdb-cursor-get graph-rl &val-graph-key &val-id MDB-NEXT-NODUP))
    (if db-mdb-status-is-success
      (if (db-id-equal (array-get graph-key 0) (db-pointer->id val-graph-key.mv-data))
        (goto each-key))
      db-mdb-status-expect-notfound)
    (i-array-forward right)
    (if (i-array-in-range right) (set (array-get graph-key 0) (i-array-get right))
      notfound-exit)
    (goto set-range))
  (label exit
    (set
      state:status status
      state:right.current right.current)
    (return status)))

(define (db-graph-read-0000 state count result)
  (status-t db-graph-selection-t* db-count-t db-graph-records-t*)
  (db-graph-reader-header-0000 state)
  db-mdb-declare-val-graph-data
  (declare graph-lr MDB-cursor*)
  (set graph-lr state:cursor)
  (db-mdb-status-require (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-GET-CURRENT))
  (label each-key
    (label each-data
      stop-if-count-zero
      (if (not skip)
        (begin
          (set
            record.left (db-pointer->id val-graph-key.mv-data)
            record.right (db-graph-data->id val-graph-data.mv-data)
            record.label (db-pointer->id-at val-graph-key.mv-data 1)
            record.ordinal (db-graph-data->ordinal val-graph-data.mv-data))
          (i-array-add *result record)))
      reduce-count
      (set status.id (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-NEXT-DUP))
      (if db-mdb-status-is-success (goto each-data)
        db-mdb-status-expect-notfound))
    (set status.id (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-NEXT-NODUP))
    (if db-mdb-status-is-success (goto each-key)
      db-mdb-status-expect-notfound))
  (label exit
    (set state:status status)
    (return status)))

(define (db-graph-select txn left right label ordinal offset state)
  (status-t
    db-txn-t db-ids-t* db-ids-t* db-ids-t* db-ordinal-condition-t* db-count-t db-graph-selection-t*)
  "prepare the state and select the reader.
  readers are specialised for filter combinations.
  the 1/0 pattern at the end of reader names corresponds to the filter combination the reader is supposed to handle.
  1 stands for filter given, 0 stands for not given. order is left, right, label, ordinal.
  readers always leave cursors at a valid entry, usually the next entry unless the results have been exhausted.
  left/right/label ids pointer can be zero which means they are unused.
  internally in the selection if unset i-array-in-range and i-array-length is zero"
  status-declare
  db-mdb-declare-val-null
  (db-mdb-cursor-declare graph-lr)
  (db-mdb-cursor-declare graph-rl)
  (db-mdb-cursor-declare graph-ll)
  (if left (set state:left *left)
    (i-array-set-null state:left))
  (if right (set state:right *right)
    (i-array-set-null state:right))
  (if label (set state:label *label)
    (i-array-set-null state:label))
  (set
    state:ids-set 0
    state:status status
    state:ordinal ordinal
    state:cursor 0
    state:cursor-2 0
    state:options 0)
  (if left
    (if ordinal
      (begin
        (if right
          (begin
            (declare right-set imht-set-t*)
            (status-require (db-ids->set *right &right-set))
            (set
              state:ids-set right-set
              state:options (bit-or db-graph-selection-flag-is-set-right state:options))))
        (db-graph-select-cursor-initialise graph-lr state cursor)
        (if label (set state:reader db-graph-read-1011-1111)
          (set state:reader db-graph-read-1001-1101)))
      (if right
        (begin
          (db-graph-select-cursor-initialise graph-rl state cursor)
          (if label (set state:reader db-graph-read-1110)
            (set state:reader db-graph-read-1100)))
        (begin
          (db-graph-select-cursor-initialise graph-lr state cursor)
          (if label (set state:reader db-graph-read-1010)
            (set state:reader db-graph-read-1000)))))
    (if right
      (begin
        (db-graph-select-cursor-initialise graph-rl state cursor)
        (set state:reader
          (if* label db-graph-read-0110
            db-graph-read-0100)))
      (if label
        (begin
          (db-graph-select-cursor-initialise graph-ll state cursor)
          (db-graph-select-cursor-initialise graph-lr state cursor-2)
          (set state:reader db-graph-read-0010))
        (begin
          (db-graph-select-cursor-initialise graph-lr state cursor)
          (set state:reader db-graph-read-0000)))))
  (define reader db-graph-reader-t state:reader)
  (if offset
    (begin
      (set state:options (bit-or db-selection-flag-skip state:options))
      (set status (reader state offset 0))
      (if (not db-mdb-status-is-success) db-mdb-status-expect-notfound)
      (set state:options (bit-xor db-selection-flag-skip state:options))))
  (label exit
    db-mdb-status-notfound-if-notfound
    (set state:status status)
    (return status)))

(define (db-graph-read state count result)
  (status-t db-graph-selection-t* db-count-t db-graph-records-t*)
  status-declare
  (set count (optional-count count))
  (status-require state:status)
  (set status ((convert-type state:reader db-graph-reader-t) state count result))
  (label exit
    db-mdb-status-notfound-if-notfound
    (return status)))

(define (db-graph-selection-destroy state) (void db-graph-selection-t*)
  (db-mdb-cursor-close state:cursor)
  (db-mdb-cursor-close state:cursor-2)
  (if (bit-and db-graph-selection-flag-is-set-right state:options)
    (begin
      (imht-set-destroy (convert-type state:ids-set imht-set-t*))
      (set state:ids-set 0))))