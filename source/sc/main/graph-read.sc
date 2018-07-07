(pre-define no-more-data-exit (status-set-both-goto db-status-group-db db-status-id-no-more-data))

(pre-define (db-graph-select-cursor-initialise name state state-field-name)
  (begin
    (db-mdb-cursor-open txn name)
    (db-mdb-cursor-get-norequire name val-null val-null MDB-FIRST)
    (if (not db-mdb-status-is-success)
      (begin
        db-mdb-status-require-notfound
        (status-set-both-goto db-status-group-db db-status-id-no-more-data)))
    (set state:state-field-name name)))

(pre-define (db-graph-select-initialise-set name state)
  (begin
    (declare (pre-concat name _set) imht-set-t*)
    (status-require (db-ids->set name (address-of (pre-concat name _set))))
    (set
      state:name (pre-concat name _set)
      state:options (bit-or (pre-concat db-read-option-is-set_ name) state:options))))

(pre-define (db-graph-reader-header state)
  (begin
    status-declare
    db-mdb-declare-val-graph-key
    (db-declare-graph-key graph-key)
    (db-declare-graph-record record)
    (declare result-temp db-graph-records-t*)
    (define skip? boolean (bit-and db-read-option-skip state:options))))

(pre-define (db-graph-reader-header-0000 state)
  (begin
    status-declare
    db-mdb-declare-val-graph-key
    (db-declare-graph-record record)
    (declare result-temp db-graph-records-t*)
    (define skip? boolean (bit-and db-read-option-skip state:options))))

(pre-define (db-graph-reader-get-ordinal-data state)
  (begin
    (define ordinal-min db-ordinal-t state:ordinal:min)
    (define ordinal-max db-ordinal-t state:ordinal:max)))

(define (db-graph-read-1000 state count result)
  (status-t db-graph-read-state-t* b32 db-graph-records-t**)
  (db-graph-reader-header state)
  db-mdb-declare-val-graph-data
  (declare
    graph-lr MDB-cursor*
    left db-ids-t*)
  (set
    graph-lr state:cursor
    left state:left)
  (db-mdb-cursor-get-norequire graph-lr val-graph-key val-graph-data MDB-GET-CURRENT)
  db-mdb-status-require
  (set (array-get graph-key 0) (db-ids-first left))
  (if (db-id-equal? (db-pointer->id val-graph-key.mv-data) (array-get graph-key 0))
    (goto each-data)
    (label set-range
      (set val-graph-key.mv-data graph-key)
      (db-mdb-cursor-get-norequire graph-lr val-graph-key val-graph-data MDB-SET-RANGE)
      (label each-key
        (if db-mdb-status-is-success
          (if (db-id-equal? (db-pointer->id val-graph-key.mv-data) (array-get graph-key 0))
            (goto each-data))
          db-mdb-status-require-notfound)
        (set left (db-ids-rest left))
        (if left
          (begin
            (set (array-get graph-key 0) (db-ids-first left))
            (goto set-range))
          no-more-data-exit))))
  (label each-data
    stop-if-count-zero
    (if (not skip?)
      (begin
        (set
          record.left (db-pointer->id val-graph-key.mv-data)
          record.right (db-graph-data->id val-graph-data.mv-data)
          record.label (db-pointer->id-at val-graph-key.mv-data 1)
          record.ordinal (db-graph-data->ordinal val-graph-data.mv-data))
        (db-graph-records-add! *result record result-temp)))
    reduce-count
    (db-mdb-cursor-next-dup-norequire graph-lr val-graph-key val-graph-data)
    (if db-mdb-status-is-success (goto each-data)
      db-mdb-status-require-notfound))
  (db-mdb-cursor-next-nodup-norequire graph-lr val-graph-key val-graph-data)
  (goto each-key)
  (label exit
    (set
      state:status status
      state:left left)
    (return status)))

(define (db-graph-read-1010 state count result)
  (status-t db-graph-read-state-t* b32 db-graph-records-t**)
  (db-graph-reader-header state)
  db-mdb-declare-val-graph-data
  (define graph-lr MDB-cursor* state:cursor)
  (define left db-ids-t* state:left)
  (define left-first db-ids-t* state:left-first)
  (define label db-ids-t* state:label)
  (db-mdb-cursor-get-norequire graph-lr val-graph-key val-graph-data MDB-GET-CURRENT)
  db-mdb-status-require
  (set
    (array-get graph-key 0) (db-ids-first left)
    (array-get graph-key 1) (db-ids-first label))
  (if (db-graph-key-equal? graph-key (db-mdb-val->graph-key val-graph-key)) (goto each-data)
    (label set-key
      (set val-graph-key.mv-data graph-key)
      (db-mdb-cursor-get-norequire graph-lr val-graph-key val-graph-data MDB-SET-KEY)
      (if db-mdb-status-is-success (goto each-data)
        db-mdb-status-require-notfound)
      (label next-key
        (set left (db-ids-rest left))
        (if left
          (begin
            (set (array-get graph-key 0) (db-ids-first left))
            (goto set-key))
          (begin
            (set label (db-ids-rest label))
            (if label
              (begin
                (set left left-first)
                (set
                  (array-get graph-key 0) (db-ids-first left)
                  (array-get graph-key 1) (db-ids-first label))
                (goto set-key))
              no-more-data-exit))))))
  (label each-data
    stop-if-count-zero
    (if (not skip?)
      (begin
        (set
          record.left (db-pointer->id val-graph-key.mv-data)
          record.right (db-graph-data->id val-graph-data.mv-data)
          record.label (db-pointer->id-at val-graph-key.mv-data 1)
          record.ordinal (db-graph-data->ordinal val-graph-data.mv-data))
        (db-graph-records-add! *result record result-temp)))
    reduce-count
    (db-mdb-cursor-next-dup-norequire graph-lr val-graph-key val-graph-data)
    (if db-mdb-status-is-success (goto each-data)
      (goto next-key)))
  (label exit
    (set
      state:status status
      state:left left
      state:label label)
    (return status)))

(define (db-graph-read-1100 state count result)
  (status-t db-graph-read-state-t* b32 db-graph-records-t**)
  (db-graph-reader-header state)
  db-mdb-declare-val-id
  (define graph-rl MDB-cursor* state:cursor)
  (define left db-ids-t* state:left)
  (define left-first db-ids-t* state:left-first)
  (define right db-ids-t* state:right)
  (db-mdb-cursor-get-norequire graph-rl val-graph-key val-id MDB-GET-CURRENT)
  db-mdb-status-require
  (set (array-get graph-key 0) (db-ids-first right))
  (if (db-id-equal? (db-pointer->id val-graph-key.mv-data) (array-get graph-key 0))
    (goto each-left)
    (label set-range
      (set val-graph-key.mv-data graph-key)
      (db-mdb-cursor-get-norequire graph-rl val-graph-key val-id MDB-SET-RANGE)
      (label each-right
        (if db-mdb-status-is-success
          (if (db-id-equal? (db-pointer->id val-graph-key.mv-data) (array-get graph-key 0))
            (goto each-left))
          db-mdb-status-require-notfound)
        (set right (db-ids-rest right))
        (if right (set (array-get graph-key 0) (db-ids-first right))
          no-more-data-exit)
        (goto set-range))))
  (label each-left
    stop-if-count-zero
    (set val-id.mv-data (db-ids-first-address left))
    (db-mdb-cursor-get-norequire graph-rl val-graph-key val-id MDB-GET-BOTH)
    (if db-mdb-status-is-success
      (begin
        (if (not skip?)
          (begin
            (set
              record.left (db-pointer->id val-id.mv-data)
              record.right (db-pointer->id val-graph-key.mv-data)
              record.label (db-pointer->id-at val-graph-key.mv-data 1))
            (db-graph-records-add! *result record result-temp)
            reduce-count)))
      db-mdb-status-require-notfound)
    (set left (db-ids-rest left))
    (if left (goto each-left)
      (set left left-first)))
  (db-mdb-cursor-next-nodup-norequire graph-rl val-graph-key val-id)
  (goto each-right)
  (label exit
    (set
      state:status status
      state:left left
      state:right right)
    (return status)))

(define (db-graph-read-1110 state count result)
  (status-t db-graph-read-state-t* b32 db-graph-records-t**)
  (db-graph-reader-header state)
  db-mdb-declare-val-id
  (define graph-rl MDB-cursor* state:cursor)
  (define left db-ids-t* state:left)
  (define left-first db-ids-t* state:left-first)
  (define right db-ids-t* state:right)
  (define right-first db-ids-t* state:right-first)
  (define label db-ids-t* state:label)
  (declare id-left db-id-t)
  (set
    (array-get graph-key 1) (db-ids-first label)
    id-left (db-ids-first left)
    (array-get graph-key 0) (db-ids-first right))
  (label set-cursor
    (set
      val-graph-key.mv-data graph-key
      val-id.mv-data &id-left)
    (db-mdb-cursor-get-norequire graph-rl val-graph-key val-id MDB-GET-BOTH)
    (if db-mdb-status-is-success (goto match)
      db-mdb-status-require-notfound)
    (label next-query
      (set right (db-ids-rest right))
      (if right
        (begin
          stop-if-count-zero
          (set (array-get graph-key 0) (db-ids-first right))
          (goto set-cursor))
        (begin
          (set right right-first)
          (set (array-get graph-key 0) (db-ids-first right))
          (set left (db-ids-rest left))
          (if left
            (begin
              stop-if-count-zero
              (set id-left (db-ids-first left))
              (goto set-cursor))
            (begin
              (set left left-first)
              (set id-left (db-ids-first left))
              (set label (db-ids-rest label))
              (if label
                (begin
                  stop-if-count-zero
                  (set (array-get graph-key 1) (db-ids-first label))
                  (goto set-cursor))
                no-more-data-exit)))))))
  (label match
    (if (not skip?)
      (begin
        (set
          record.left (db-pointer->id val-id.mv-data)
          record.right (db-pointer->id val-graph-key.mv-data)
          record.label (db-pointer->id-at val-graph-key.mv-data 1))
        (db-graph-records-add! *result record result-temp)))
    reduce-count
    (goto next-query))
  (label exit
    (set
      state:status status
      state:left left
      state:right right
      state:label label)
    (return status)))

(define (db-graph-read-1001-1101 state count result)
  (status-t db-graph-read-state-t* b32 db-graph-records-t**)
  (db-graph-reader-header state)
  db-mdb-declare-val-graph-data
  (define graph-lr MDB-cursor* state:cursor)
  (define left db-ids-t* state:left)
  (define right imht-set-t* state:right)
  (db-graph-reader-get-ordinal-data state)
  (db-declare-graph-data graph-data)
  (db-graph-data-set-ordinal graph-data ordinal-min)
  (db-mdb-cursor-get-norequire graph-lr val-graph-key val-graph-data MDB-GET-CURRENT)
  db-mdb-status-require
  (if left (set (array-get graph-key 0) (db-ids-first left))
    no-more-data-exit)
  (if (db-id-equal? (db-pointer->id val-graph-key.mv-data) (array-get graph-key 0))
    (goto each-data))
  (label each-left
    (set val-graph-key.mv-data graph-key)
    (db-mdb-cursor-get-norequire graph-lr val-graph-key val-graph-data MDB-SET-RANGE)
    (label each-key
      (if db-mdb-status-is-success
        (if (db-id-equal? (db-pointer->id val-graph-key.mv-data) (array-get graph-key 0))
          (begin
            (set val-graph-data.mv-data graph-data)
            (db-mdb-cursor-get-norequire graph-lr val-graph-key val-graph-data MDB-GET-BOTH-RANGE)
            (if db-mdb-status-is-success (goto each-data)
              db-mdb-status-require-notfound)
            (db-mdb-cursor-next-nodup-norequire graph-lr val-graph-key val-graph-data)
            (goto each-key)))
        db-mdb-status-require-notfound)
      (set left (db-ids-rest left))
      (if left (set (array-get graph-key 0) (db-ids-first left))
        no-more-data-exit)
      (goto each-left)))
  (label each-data
    stop-if-count-zero
    (if
      (and
        (or (not ordinal-min) (>= (db-graph-data->ordinal val-graph-data.mv-data) ordinal-min))
        (or (not ordinal-max) (<= (db-graph-data->ordinal val-graph-data.mv-data) ordinal-max)))
      (begin
        (if (or (not right) (imht-set-contains? right (db-graph-data->id val-graph-data.mv-data)))
          (begin
            (if (not skip?)
              (begin
                (set
                  record.left (db-pointer->id val-graph-key.mv-data)
                  record.label (db-pointer->id-at val-graph-key.mv-data 1)
                  record.ordinal (db-graph-data->ordinal val-graph-data.mv-data)
                  record.right (db-graph-data->id val-graph-data.mv-data))
                (db-graph-records-add! *result record result-temp)))
            reduce-count))
        (db-mdb-cursor-next-dup-norequire graph-lr val-graph-key val-graph-data)
        (if db-mdb-status-is-success (goto each-data)
          db-mdb-status-require-notfound))))
  (db-mdb-cursor-next-nodup-norequire graph-lr val-graph-key val-graph-data)
  (goto each-key)
  (label exit
    (set
      state:status status
      state:left left)
    (return status)))

(define (db-graph-read-1011-1111 state count result)
  (status-t db-graph-read-state-t* b32 db-graph-records-t**)
  (db-graph-reader-header state)
  db-mdb-declare-val-graph-data
  (db-declare-graph-data graph-data)
  (define graph-lr MDB-cursor* state:cursor)
  (define left db-ids-t* state:left)
  (define left-first db-ids-t* state:left-first)
  (define label db-ids-t* state:label)
  (define right imht-set-t* state:right)
  (db-graph-reader-get-ordinal-data state)
  (db-graph-data-set-ordinal graph-data ordinal-min)
  (db-mdb-cursor-get-norequire graph-lr val-graph-key val-graph-data MDB-GET-CURRENT)
  db-mdb-status-require
  (set
    (array-get graph-key 0) (db-ids-first left)
    (array-get graph-key 1) (db-ids-first label))
  (if (db-graph-key-equal? graph-key (db-mdb-val->graph-key val-graph-key)) (goto each-data)
    (label set-key
      (set
        val-graph-key.mv-data graph-key
        val-graph-data.mv-data graph-data)
      (db-mdb-cursor-get-norequire graph-lr val-graph-key val-graph-data MDB-GET-BOTH-RANGE)
      (if db-mdb-status-is-success (goto each-data)
        (begin
          db-mdb-status-require-notfound
          (label each-key
            (set left (db-ids-rest left))
            (if left (set (array-get graph-key 0) (db-ids-first left))
              (begin
                (set label (db-ids-rest label))
                (if label
                  (begin
                    (set
                      (array-get graph-key 1) (db-ids-first label)
                      left left-first
                      (array-get graph-key 0) (db-ids-first left)))
                  no-more-data-exit)))
            (goto set-key))))))
  (label each-data
    stop-if-count-zero
    (if (or (not ordinal-max) (<= (db-graph-data->ordinal val-graph-data.mv-data) ordinal-max))
      (begin
        (if (or (not right) (imht-set-contains? right (db-graph-data->id val-graph-data.mv-data)))
          (begin
            (if (not skip?)
              (begin
                (set
                  record.left (db-pointer->id val-graph-key.mv-data)
                  record.right (db-graph-data->id val-graph-data.mv-data)
                  record.label (db-pointer->id-at val-graph-key.mv-data 1)
                  record.ordinal (db-graph-data->ordinal val-graph-data.mv-data))
                (db-graph-records-add! *result record result-temp)))
            reduce-count))
        (db-mdb-cursor-next-dup-norequire graph-lr val-graph-key val-graph-data)
        (if db-mdb-status-is-success (goto each-data)
          (goto each-key)))
      (goto each-key)))
  (label exit
    (set
      state:status status
      state:left left
      state:label label)
    (return status)))

(define (db-graph-read-0010 state count result)
  (status-t db-graph-read-state-t* b32 db-graph-records-t**)
  (db-graph-reader-header state)
  db-mdb-declare-val-id
  db-mdb-declare-val-id-2
  db-mdb-declare-val-graph-data
  (define graph-ll MDB-cursor* state:cursor)
  (define graph-lr MDB-cursor* state:cursor-2)
  (define label db-ids-t* state:label)
  (declare
    id-left db-id-t
    id-label db-id-t)
  (db-mdb-cursor-get-norequire graph-ll val-id val-id-2 MDB-GET-CURRENT)
  db-mdb-status-require
  (db-mdb-cursor-get-norequire graph-lr val-graph-key val-graph-data MDB-GET-CURRENT)
  db-mdb-status-require
  (if label (set id-label (db-ids-first label))
    no-more-data-exit)
  (if (db-id-equal? id-label (db-pointer->id val-id.mv-data))
    (begin
      (set (array-get graph-key 1) id-label)
      (goto each-label-data))
    (label set-label-key
      (set val-id.mv-data &id-label)
      (db-mdb-cursor-get-norequire graph-ll val-id val-id-2 MDB-SET-KEY)
      (if db-mdb-status-is-success
        (begin
          (set (array-get graph-key 1) id-label)
          (goto each-label-data))
        (begin
          db-mdb-status-require-notfound
          (set label (db-ids-rest label))
          (if label (set id-label (db-ids-first label))
            no-more-data-exit)
          (goto set-label-key)))))
  (label each-label-data
    (set id-left (db-pointer->id val-id-2.mv-data))
    (if (db-id-equal? id-left (db-pointer->id val-graph-key.mv-data)) (goto each-left-data)
      (begin
        (set (array-get graph-key 0) id-left)
        (set val-graph-key.mv-data graph-key)
        (db-mdb-cursor-get-norequire graph-lr val-graph-key val-graph-data MDB-SET-KEY)
        (if db-mdb-status-is-success (goto each-left-data)
          (goto exit))))
    (label each-left-data
      stop-if-count-zero
      (if (not skip?)
        (begin
          (set
            record.left id-left
            record.right (db-graph-data->id val-graph-data.mv-data)
            record.label id-label)
          (db-graph-records-add! *result record result-temp)))
      reduce-count
      (db-mdb-cursor-next-dup-norequire graph-lr val-graph-key val-graph-data)
      (if db-mdb-status-is-success (goto each-left-data)
        db-mdb-status-require-notfound))
    (db-mdb-cursor-next-dup-norequire graph-ll val-id val-id-2)
    (if db-mdb-status-is-success (goto each-label-data)
      (begin
        (set label (db-ids-rest label))
        (if label (set id-label (db-ids-first label))
          no-more-data-exit)
        (goto set-label-key))))
  (label exit
    (set
      state:status status
      state:label label)
    (return status)))

(define (db-graph-read-0110 state count result)
  (status-t db-graph-read-state-t* b32 db-graph-records-t**)
  (db-graph-reader-header state)
  db-mdb-declare-val-id
  (define graph-rl MDB-cursor* state:cursor)
  (define label db-ids-t* state:label)
  (define right db-ids-t* state:right)
  (define right-first db-ids-t* state:right-first)
  (db-mdb-cursor-get-norequire graph-rl val-graph-key val-id MDB-GET-CURRENT)
  db-mdb-status-require
  (set
    (array-get graph-key 1) (db-ids-first label)
    (array-get graph-key 0) (db-ids-first right))
  (if (db-graph-key-equal? graph-key (db-mdb-val->graph-key val-graph-key)) (goto each-data)
    (label set-key
      (set val-graph-key.mv-data graph-key)
      (db-mdb-cursor-get-norequire graph-rl val-graph-key val-id MDB-SET-KEY)
      (if db-mdb-status-is-success (goto each-data)
        (label each-key
          db-mdb-status-require-notfound
          (set right (db-ids-rest right))
          (if right (set (array-get graph-key 0) (db-ids-first right))
            (begin
              (set label (db-ids-rest label))
              (if label
                (begin
                  (set
                    (array-get graph-key 1) (db-ids-first label)
                    right right-first
                    (array-get graph-key 0) (db-ids-first right)))
                no-more-data-exit)))
          (goto set-key)))))
  (label each-data
    stop-if-count-zero
    (if (not skip?)
      (begin
        (set
          record.left (db-pointer->id val-id.mv-data)
          record.right (array-get graph-key 0)
          record.label (array-get graph-key 1))
        (db-graph-records-add! *result record result-temp)))
    reduce-count
    (db-mdb-cursor-next-dup-norequire graph-rl val-graph-key val-id)
    (if db-mdb-status-is-success (goto each-data)
      (goto each-key)))
  (label exit
    (set
      state:status status
      state:right right
      state:label label)
    (return status)))

(define (db-graph-read-0100 state count result)
  (status-t db-graph-read-state-t* b32 db-graph-records-t**)
  (db-graph-reader-header state)
  db-mdb-declare-val-id
  (define graph-rl MDB-cursor* state:cursor)
  (define right db-ids-t* state:right)
  (db-mdb-cursor-get-norequire graph-rl val-graph-key val-id MDB-GET-CURRENT)
  db-mdb-status-require
  (set (array-get graph-key 0) (db-ids-first right))
  (if (db-id-equal? (array-get graph-key 0) (db-pointer->id val-graph-key.mv-data)) (goto each-key)
    (label set-range
      (set val-graph-key.mv-data graph-key)
      (db-mdb-cursor-get-norequire graph-rl val-graph-key val-id MDB-SET-RANGE)
      (if db-mdb-status-is-success
        (if (db-id-equal? (array-get graph-key 0) (db-pointer->id val-graph-key.mv-data))
          (goto each-key))
        db-mdb-status-require-notfound)
      (set right (db-ids-rest right))
      (if right (set (array-get graph-key 0) (db-ids-first right))
        no-more-data-exit)
      (goto set-range)))
  (label each-key
    (label each-data
      stop-if-count-zero
      (if (not skip?)
        (begin
          (set
            record.left (db-pointer->id val-id.mv-data)
            record.right (db-pointer->id val-graph-key.mv-data)
            record.label (db-pointer->id-at val-graph-key.mv-data 1))
          (db-graph-records-add! *result record result-temp)))
      reduce-count
      (db-mdb-cursor-next-dup-norequire graph-rl val-graph-key val-id)
      (if db-mdb-status-is-success (goto each-data)
        db-mdb-status-require-notfound))
    (db-mdb-cursor-next-nodup-norequire graph-rl val-graph-key val-id)
    (if db-mdb-status-is-success
      (if (db-id-equal? (array-get graph-key 0) (db-pointer->id val-graph-key.mv-data))
        (goto each-key))
      db-mdb-status-require-notfound)
    (set right (db-ids-rest right))
    (if right (set (array-get graph-key 0) (db-ids-first right))
      no-more-data-exit)
    (goto set-range))
  (label exit
    (set
      state:status status
      state:right right)
    (return status)))

(define (db-graph-read-0000 state count result)
  (status-t db-graph-read-state-t* b32 db-graph-records-t**)
  (db-graph-reader-header-0000 state)
  db-mdb-declare-val-graph-data
  (define graph-lr MDB-cursor* state:cursor)
  (db-mdb-cursor-get-norequire graph-lr val-graph-key val-graph-data MDB-GET-CURRENT)
  db-mdb-status-require
  (label each-key
    (label each-data
      stop-if-count-zero
      (if (not skip?)
        (begin
          (set
            record.left (db-pointer->id val-graph-key.mv-data)
            record.right (db-graph-data->id val-graph-data.mv-data)
            record.label (db-pointer->id-at val-graph-key.mv-data 1)
            record.ordinal (db-graph-data->ordinal val-graph-data.mv-data))
          (db-graph-records-add! *result record result-temp)))
      reduce-count
      (db-mdb-cursor-next-dup-norequire graph-lr val-graph-key val-graph-data)
      (if db-mdb-status-is-success (goto each-data)
        db-mdb-status-require-notfound))
    (db-mdb-cursor-next-nodup-norequire graph-lr val-graph-key val-graph-data)
    (if db-mdb-status-is-success (goto each-key)
      db-mdb-status-require-notfound))
  (label exit
    (set state:status status)
    (return status)))

(define (db-graph-select txn left right label ordinal offset result)
  (status-t
    db-txn-t db-ids-t* db-ids-t* db-ids-t* db-ordinal-condition-t* b32 db-graph-read-state-t*)
  "prepare the state and select the reader.
  readers are specialised for filter combinations.
  the 1/0 pattern at the end of reader names corresponds to the filter combination the reader is supposed to handle.
  1 stands for filter given, 0 stands for not given. order is left, right, label, ordinal.
  readers always leave cursors at a valid entry, usually the next entry unless the results have been exhausted"
  status-declare
  db-mdb-declare-val-null
  (db-mdb-cursor-declare-three graph-lr graph-rl graph-ll)
  (set
    result:status status
    result:left left
    result:left-first left
    result:right right
    result:right-first right
    result:label label
    result:ordinal ordinal
    result:cursor 0
    result:cursor-2 0
    result:options 0)
  (if left
    (if ordinal
      (begin
        (if right (db-graph-select-initialise-set right result))
        (db-graph-select-cursor-initialise graph-lr result cursor)
        (if label (set result:reader db-graph-read-1011-1111)
          (set result:reader db-graph-read-1001-1101)))
      (if right
        (begin
          (db-graph-select-cursor-initialise graph-rl result cursor)
          (if label (set result:reader db-graph-read-1110)
            (set result:reader db-graph-read-1100)))
        (begin
          (db-graph-select-cursor-initialise graph-lr result cursor)
          (if label (set result:reader db-graph-read-1010)
            (set result:reader db-graph-read-1000)))))
    (if right
      (begin
        (db-graph-select-cursor-initialise graph-rl result cursor)
        (set result:reader
          (if* label db-graph-read-0110
            db-graph-read-0100)))
      (if label
        (begin
          (db-graph-select-cursor-initialise graph-ll result cursor)
          (db-graph-select-cursor-initialise graph-lr result cursor-2)
          (set result:reader db-graph-read-0010))
        (begin
          (db-graph-select-cursor-initialise graph-lr result cursor)
          (set result:reader db-graph-read-0000)))))
  (define reader db-graph-reader-t result:reader)
  (if offset
    (begin
      (set state:options (bit-or db-read-option-skip state:options))
      (set status (reader state offset 0))
      (if (not db-mdb-status-is-success) db-mdb-status-require-notfound)
      (set state:options (bit-xor db-read-option-skip state:options))))
  (label exit
    (set result:status status)
    (return status)))

(define (db-graph-read state count result)
  (status-t db-graph-read-state-t* b32 db-graph-records-t**)
  status-declare
  (set count (optional-count count))
  (status-require state:status)
  (set status ((convert-type state:reader db-graph-reader-t) state count result))
  (label exit
    db-mdb-status-no-more-data-if-notfound
    (return status)))

(define (db-graph-selection-destroy state) (b0 db-graph-read-state-t*)
  (db-mdb-cursor-close state:cursor)
  (db-mdb-cursor-close state:cursor-2)
  (if (bit-and db-read-option-is-set-right state:options)
    (begin
      (imht-set-destroy (convert-type state:right imht-set-t*))
      (set state:right 0))))