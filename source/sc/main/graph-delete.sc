(define (db-graph-internal-delete-graph-ll graph-ll id-label id-left)
  (status-t MDB-cursor* db-id-t db-id-t)
  status-declare
  db-mdb-declare-val-id
  db-mdb-declare-val-id-2
  (set
    val-id.mv-data &id-label
    val-id-2.mv-data &id-left)
  (set status.id (mdb-cursor-get graph-ll &val-id &val-id-2 MDB-GET-BOTH))
  (if db-mdb-status-is-success (db-mdb-status-require (mdb-cursor-del graph-ll 0))
    db-mdb-status-expect-notfound)
  (set status.id status-id-success)
  (label exit
    (return status)))

(define (db-graph-internal-delete-graph-ll-conditional graph-lr graph-ll id-label id-left)
  (status-t MDB-cursor* MDB-cursor* db-id-t db-id-t)
  status-declare
  db-mdb-declare-val-null
  db-mdb-declare-val-graph-key
  (db-declare-graph-key graph-key)
  (set
    (array-get graph-key 0) id-left
    (array-get graph-key 1) id-label
    val-graph-key.mv-data graph-key
    status.id (mdb-cursor-get graph-lr &val-graph-key &val-null MDB-SET))
  (return
    (if* db-mdb-status-is-notfound (db-graph-internal-delete-graph-ll graph-ll id-label id-left)
      status)))

(define (db-graph-internal-delete-graph-rl graph-rl id-left id-right id-label)
  (status-t MDB-cursor* db-id-t db-id-t db-id-t)
  status-declare
  db-mdb-declare-val-id
  db-mdb-declare-val-graph-key
  (db-declare-graph-key graph-key)
  (set
    (array-get graph-key 0) id-right
    (array-get graph-key 1) id-label
    val-graph-key.mv-data graph-key
    val-id.mv-data &id-left)
  (set status.id (mdb-cursor-get graph-rl &val-graph-key &val-id MDB-GET-BOTH))
  (if db-mdb-status-is-success (db-mdb-status-require (mdb-cursor-del graph-rl 0))
    db-mdb-status-expect-notfound)
  (label exit
    (return status)))

(pre-define db-graph-internal-delete-0010
  (begin
    (set label *label-pointer)
    (label set-key-0010
      (set
        id-label (i-array-get label)
        val-id.mv-data &id-label)
      (set status.id (mdb-cursor-get graph-ll &val-id &val-id-2 MDB-SET-KEY))
      (if db-mdb-status-is-success (goto each-data-0010)
        db-mdb-status-expect-notfound)
      (label each-key-0010
        (i-array-forward label)
        (if label.current (goto set-key-0010)
          (goto exit))))
    (label each-data-0010
      (set
        id-left (db-pointer->id val-id-2.mv-data)
        (array-get graph-key 0) id-left
        (array-get graph-key 1) id-label
        val-graph-key.mv-data graph-key)
      (set status.id (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-SET-KEY))
      (if db-mdb-status-is-success
        (label each-data-2-0010
          (set status
            (db-graph-internal-delete-graph-rl
              graph-rl id-left (db-graph-data->id val-graph-data.mv-data) id-label))
          db-mdb-status-expect-read
          (db-mdb-status-require (mdb-cursor-del graph-lr 0))
          (set status.id (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-NEXT-DUP))
          (if db-mdb-status-is-success (goto each-data-2-0010)
            db-mdb-status-expect-notfound))
        db-mdb-status-expect-notfound)
      (db-mdb-status-require (mdb-cursor-del graph-ll 0))
      (set status.id (mdb-cursor-get graph-ll &val-id &val-id-2 MDB-NEXT-DUP))
      (if db-mdb-status-is-success (goto each-data-0010)
        db-mdb-status-expect-notfound)
      (goto each-key-0010))))

(pre-define db-graph-internal-delete-0110
  (begin
    (set
      label *label-pointer
      right *right-pointer)
    (label set-key-0110
      (set
        id-right (i-array-get right)
        id-label (i-array-get label)
        (array-get graph-key 0) id-right
        (array-get graph-key 1) id-label
        val-graph-key.mv-data graph-key)
      (set status.id (mdb-cursor-get graph-rl &val-graph-key &val-id MDB-SET-KEY))
      (if db-mdb-status-is-success (goto each-data-0110)
        db-mdb-status-expect-notfound)
      (label each-key-0110
        (i-array-forward right)
        (if right.current (goto set-key-0110)
          (begin
            (i-array-forward label)
            (if label.current
              (begin
                (i-array-rewind right)
                (goto set-key-0110))
              (goto exit))))))
    (label each-data-0110
      (set
        id-left (db-pointer->id val-id.mv-data)
        (array-get graph-key 0) id-left
        (array-get graph-key 1) id-label
        val-graph-key.mv-data graph-key)
      (set status.id (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-SET-KEY))
      (if db-mdb-status-is-success
        (begin
          (set status (db-mdb-graph-lr-seek-right graph-lr id-right))
          (if db-mdb-status-is-success (db-mdb-status-require (mdb-cursor-del graph-lr 0))
            db-mdb-status-expect-notfound))
        db-mdb-status-expect-notfound)
      (set status (db-graph-internal-delete-graph-ll graph-ll id-label id-left))
      db-mdb-status-expect-read
      (db-mdb-status-require (mdb-cursor-del graph-rl 0))
      (set status.id (mdb-cursor-get graph-rl &val-graph-key &val-id MDB-NEXT-DUP))
      (if db-mdb-status-is-success (goto each-data-0110)
        db-mdb-status-expect-notfound))
    (goto each-key-0110)))

(pre-define db-graph-internal-delete-1010
  (begin
    (set
      left *left-pointer
      label *label-pointer)
    (while left.current
      (set id-left (i-array-get left))
      (while label.current
        (set
          id-label (i-array-get label)
          (array-get graph-key 0) id-left
          (array-get graph-key 1) id-label
          val-graph-key.mv-data graph-key)
        (set status.id (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-SET-KEY))
        (if db-mdb-status-is-success
          (begin
            (do-while db-mdb-status-is-success
              (set status
                (db-graph-internal-delete-graph-rl
                  graph-rl id-left (db-graph-data->id val-graph-data.mv-data) id-label))
              db-mdb-status-expect-read
              (set status (db-graph-internal-delete-graph-ll graph-ll id-label id-left))
              db-mdb-status-expect-read
              (set status.id (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-NEXT-DUP)))
            db-mdb-status-expect-notfound
            (set
              (array-get graph-key 0) id-left
              (array-get graph-key 1) id-label
              val-graph-key.mv-data graph-key)
            (db-mdb-status-require
              (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-SET-KEY))
            (db-mdb-status-require (mdb-cursor-del graph-lr MDB-NODUPDATA)))
          db-mdb-status-expect-notfound)
        (i-array-forward label))
      (i-array-rewind label)
      (i-array-forward left))))

(pre-define db-graph-internal-delete-0100
  (begin
    (declare
      id-left db-id-t
      id-right db-id-t
      id-label db-id-t)
    (set right *right-pointer)
    (label set-range-0100
      (set
        id-right (i-array-get right)
        (array-get graph-key 0) id-right
        (array-get graph-key 1) 0
        val-graph-key.mv-data graph-key)
      (set status.id (mdb-cursor-get graph-rl &val-graph-key &val-id MDB-SET-RANGE))
      (if db-mdb-status-is-success
        (if (= id-right (db-pointer->id val-graph-key.mv-data))
          (begin
            (set id-label (db-pointer->id-at val-graph-key.mv-data 1))
            (goto each-data-0100)))
        db-mdb-status-expect-notfound)
      (i-array-forward right)
      (if right.current (goto set-range-0100)
        (goto exit)))
    (label each-data-0100
      (set
        id-left (db-pointer->id val-id.mv-data)
        (array-get graph-key 0) id-left
        (array-get graph-key 1) id-label
        val-graph-key.mv-data graph-key)
      (set status.id (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-SET-KEY))
      (if db-mdb-status-is-success
        (begin
          (set status (db-mdb-graph-lr-seek-right graph-lr id-right))
          (if db-mdb-status-is-success (db-mdb-status-require (mdb-cursor-del graph-lr 0))
            db-mdb-status-expect-notfound))
        db-mdb-status-expect-notfound)
      (set status
        (db-graph-internal-delete-graph-ll-conditional graph-lr graph-ll id-label id-left))
      db-mdb-status-expect-read
      (db-mdb-status-require (mdb-cursor-del graph-rl 0))
      (set status.id (mdb-cursor-get graph-rl &val-graph-key &val-id MDB-NEXT-DUP))
      (if db-mdb-status-is-success (goto each-data-0100)
        db-mdb-status-expect-notfound))
    (goto set-range-0100)))

(pre-define db-graph-internal-delete-1000
  (begin
    (set left *left-pointer)
    (label set-range-1000
      (set
        id-left (i-array-get left)
        (array-get graph-key 0) id-left
        (array-get graph-key 1) 0
        val-graph-key.mv-data graph-key
        status.id (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-SET-RANGE))
      (label each-key-1000
        (if db-mdb-status-is-success
          (if (= id-left (db-pointer->id val-graph-key.mv-data))
            (begin
              (set id-label (db-pointer->id-at val-graph-key.mv-data 1))
              (goto each-data-1000)))
          db-mdb-status-expect-notfound)
        (i-array-forward left)
        (if left.current (goto set-range-1000)
          (goto exit))))
    (label each-data-1000
      (set id-right (db-graph-data->id val-graph-data.mv-data))
      (set status (db-graph-internal-delete-graph-rl graph-rl id-left id-right id-label))
      db-mdb-status-expect-read
      (set status (db-graph-internal-delete-graph-ll graph-ll id-label id-left))
      db-mdb-status-expect-read
      (set status.id (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-NEXT-DUP))
      (if db-mdb-status-is-success (goto each-data-1000)
        db-mdb-status-expect-notfound))
    (array-set graph-key 0 id-left 1 id-label)
    (db-mdb-status-require (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-SET-KEY))
    (db-mdb-status-require (mdb-cursor-del graph-lr MDB-NODUPDATA))
    (set status.id (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-NEXT-NODUP))
    (goto each-key-1000)))

(pre-define db-graph-internal-delete-1100
  (begin
    (status-require (db-ids->set *right-pointer &right-set))
    (set
      left *left-pointer
      (array-get graph-key 1) 0)
    (label set-range-1100
      (set
        id-left (i-array-get left)
        (array-get graph-key 0) id-left
        val-graph-key.mv-data graph-key
        status.id (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-SET-RANGE))
      (label each-key-1100
        (if db-mdb-status-is-success
          (if (= id-left (db-pointer->id val-graph-key.mv-data))
            (begin
              (set id-label (db-pointer->id-at val-graph-key.mv-data 1))
              (goto each-data-1100)))
          db-mdb-status-expect-notfound)
        (i-array-forward left)
        (if left.current
          (begin
            (set (array-get graph-key 1) 0)
            (goto set-range-1100))
          (goto exit))))
    (label each-data-1100
      (set id-right (db-graph-data->id val-graph-data.mv-data))
      (if (imht-set-contains right-set id-right)
        (begin
          (set status (db-graph-internal-delete-graph-rl graph-rl id-left id-right id-label))
          db-mdb-status-expect-read
          (db-mdb-status-require (mdb-cursor-del graph-lr 0))))
      (set status.id (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-NEXT-DUP))
      (if db-mdb-status-is-success (goto each-data-1100)
        db-mdb-status-expect-notfound))
    (set status (db-graph-internal-delete-graph-ll-conditional graph-lr graph-ll id-label id-left))
    db-mdb-status-expect-read
    (set
      (array-get graph-key 0) id-left
      (array-get graph-key 1) id-label
      val-graph-key.mv-data graph-key)
    (set status.id (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-SET-KEY))
    (cond
      (db-mdb-status-is-success
        (set status.id (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-NEXT-NODUP))
        (goto each-key-1100))
      ((= status.id MDB-NOTFOUND) (goto set-range-1100))
      (else (status-set-group-goto db-status-group-lmdb)))))

(pre-define db-graph-internal-delete-1110
  (begin
    (status-require (db-ids->set *right-pointer &right-set))
    (set
      left *left-pointer
      label *label-pointer)
    (while left.current
      (set id-left (i-array-get left))
      (while label.current
        (set
          id-label (i-array-get label)
          (array-get graph-key 0) id-left
          (array-get graph-key 1) id-label
          val-graph-key.mv-data graph-key)
        (set status.id (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-SET-KEY))
        (while db-mdb-status-is-success
          (if (imht-set-contains right-set (db-graph-data->id val-graph-data.mv-data))
            (begin
              (set id-right (db-graph-data->id val-graph-data.mv-data))
              (set status (db-graph-internal-delete-graph-rl graph-rl id-left id-right id-label))
              db-mdb-status-expect-read
              (db-mdb-status-require (mdb-cursor-del graph-lr 0))))
          (set status.id (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-NEXT-DUP)))
        (set status
          (db-graph-internal-delete-graph-ll-conditional graph-lr graph-ll id-label id-left))
        db-mdb-status-expect-read
        (i-array-forward label))
      (i-array-rewind label)
      (i-array-forward left))))

(pre-define (db-graph-internal-delete-get-ordinal-data ordinal)
  (begin
    (define ordinal-min db-ordinal-t ordinal:min)
    (define ordinal-max db-ordinal-t ordinal:max)))

(pre-define db-graph-internal-delete-1001-1101
  (begin
    (db-graph-internal-delete-get-ordinal-data ordinal)
    (set
      left *left-pointer
      (array-get graph-data 0) ordinal-min
      (array-get graph-key 1) 0)
    (if right.current (status-require (db-ids->set right &right-set)))
    (label set-range-1001-1101
      (set
        id-left (i-array-get left)
        (array-get graph-key 0) id-left
        val-graph-key.mv-data graph-key)
      (set status.id (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-SET-RANGE))
      (label each-key-1001-1101
        (if db-mdb-status-is-success
          (if (= id-left (db-pointer->id val-graph-key.mv-data))
            (begin
              (set val-graph-data.mv-data graph-data)
              (set status.id
                (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-GET-BOTH-RANGE))
              (if db-mdb-status-is-success
                (begin
                  (set id-label (db-pointer->id-at val-graph-key.mv-data 1))
                  (goto each-data-1001-1101))
                db-mdb-status-expect-notfound)
              (set status.id
                (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-NEXT-NODUP))
              (goto each-key-1001-1101)))
          db-mdb-status-expect-notfound)
        (i-array-forward left)
        (if left.current
          (begin
            (set (array-get graph-key 1) 0)
            (goto set-range-1001-1101))
          (goto exit))))
    (label each-data-1001-1101
      (if (or (not ordinal-max) (<= (db-graph-data->ordinal val-graph-data.mv-data) ordinal-max))
        (begin
          (set id-right (db-graph-data->id val-graph-data.mv-data))
          (if (or (not right-pointer) (imht-set-contains right-set id-right))
            (begin
              (set status (db-graph-internal-delete-graph-rl graph-rl id-left id-right id-label))
              db-mdb-status-expect-read
              (db-mdb-status-require (mdb-cursor-del graph-lr 0)))))
        (goto next-label-1001-1101))
      (set status.id (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-NEXT-DUP))
      (if db-mdb-status-is-success (goto each-data-1001-1101)
        db-mdb-status-expect-notfound))
    (set status (db-graph-internal-delete-graph-ll-conditional graph-lr graph-ll id-label id-left))
    db-mdb-status-expect-read
    (label next-label-1001-1101
      (set
        (array-get graph-key 0) id-left
        (array-get graph-key 1) id-label
        val-graph-key.mv-data graph-key)
      (set status.id (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-SET-KEY))
      (cond
        (db-mdb-status-is-success
          (set status.id (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-NEXT-NODUP))
          (goto each-key-1001-1101))
        ((= status.id MDB-NOTFOUND) (goto set-range-1001-1101))
        (else (status-set-group-goto db-status-group-lmdb))))))

(pre-define db-graph-internal-delete-1011-1111
  (begin
    (db-graph-internal-delete-get-ordinal-data ordinal)
    (if right-pointer (status-require (db-ids->set *right-pointer &right-set)))
    (set
      left *left-pointer
      label *label-pointer
      (array-get graph-data 0) ordinal-min
      id-label (i-array-get label))
    (label set-key-1011-1111
      (set
        id-left (i-array-get left)
        (array-get graph-key 0) id-left
        (array-get graph-key 1) id-label
        val-graph-key.mv-data graph-key
        val-graph-data.mv-data graph-data)
      (set status.id (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-GET-BOTH-RANGE))
      (if db-mdb-status-is-success (goto each-data-1011-1111)
        (label each-key-1011-1111
          (i-array-forward left)
          (if left.current (goto set-key-1011-1111)
            (begin
              (i-array-forward label)
              (if label.current
                (begin
                  (set id-label (i-array-get label))
                  (i-array-rewind left)
                  (goto set-key-1011-1111))
                (goto exit)))))))
    (label each-data-1011-1111
      (if (or (not ordinal-max) (<= (db-graph-data->ordinal val-graph-data.mv-data) ordinal-max))
        (begin
          (if
            (or
              (not right-pointer)
              (imht-set-contains right-set (db-graph-data->id val-graph-data.mv-data)))
            (begin
              (sc-comment "delete graph-rl")
              (set
                id-right (db-graph-data->id val-graph-data.mv-data)
                status (db-graph-internal-delete-graph-rl graph-rl id-left id-right id-label))
              db-mdb-status-expect-read
              (db-mdb-status-require (mdb-cursor-del graph-lr 0))))
          (set status.id (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-NEXT-DUP))
          (if db-mdb-status-is-success (goto each-data-1011-1111)
            db-mdb-status-expect-notfound))))
    (set status (db-graph-internal-delete-graph-ll-conditional graph-lr graph-ll id-label id-left))
    db-mdb-status-expect-read
    (goto each-key-1011-1111)))

(define
  (db-graph-internal-delete
    left-pointer right-pointer label-pointer ordinal graph-lr graph-rl graph-ll)
  (status-t
    db-ids-t* db-ids-t* db-ids-t* db-ordinal-condition-t* MDB-cursor* MDB-cursor* MDB-cursor*)
  "db-graph-internal-delete does not open/close cursors.
   1111 / left-right-label-ordinal.
   tip: the code is nice to debug if current state information is displayed near the
     beginning of goto labels before cursor operations.
     example: (debug-log \"each-key-1100 %lu %lu\" id-left id-right)
   db-graph-internal-delete-* macros are allowed to leave status on MDB-NOTFOUND"
  status-declare
  (declare
    id-left db-id-t
    id-right db-id-t
    id-label db-id-t
    left db-ids-t
    right db-ids-t
    label db-ids-t
    right-set imht-set-t*)
  db-mdb-declare-val-graph-key
  db-mdb-declare-val-graph-data
  db-mdb-declare-val-id
  db-mdb-declare-val-id-2
  (db-declare-graph-key graph-key)
  (db-declare-graph-data graph-data)
  (if left-pointer
    (if ordinal
      (if label-pointer db-graph-internal-delete-1011-1111
        db-graph-internal-delete-1001-1101)
      (if label-pointer
        (if right-pointer db-graph-internal-delete-1110
          db-graph-internal-delete-1010)
        (if right-pointer db-graph-internal-delete-1100
          db-graph-internal-delete-1000)))
    (if right-pointer
      (if label-pointer db-graph-internal-delete-0110
        db-graph-internal-delete-0100)
      (if label-pointer db-graph-internal-delete-0010
        (db-status-set-id-goto db-status-id-not-implemented))))
  (label exit
    db-mdb-status-success-if-notfound
    (return status)))

(define (db-graph-delete txn left right label ordinal)
  (status-t db-txn-t db-ids-t* db-ids-t* db-ids-t* db-ordinal-condition-t*)
  "db-relation-delete differs from db-relation-read in that it does not support
  partial processing and therefore does not need a state for repeated calls.
   it also differs in that it always needs all relation dbi
   to complete the deletion instead of just any dbi necessary to find relations.
  algorithm: delete all relations with any of the given ids at the corresponding position"
  status-declare
  (db-mdb-cursor-declare graph-lr)
  (db-mdb-cursor-declare graph-rl)
  (db-mdb-cursor-declare graph-ll)
  (db-mdb-status-require (db-mdb-env-cursor-open txn graph-lr))
  (db-mdb-status-require (db-mdb-env-cursor-open txn graph-rl))
  (db-mdb-status-require (db-mdb-env-cursor-open txn graph-ll))
  (set status (db-graph-internal-delete left right label ordinal graph-lr graph-rl graph-ll))
  (label exit
    (db-mdb-cursor-close-if-active graph-lr)
    (db-mdb-cursor-close-if-active graph-rl)
    (db-mdb-cursor-close-if-active graph-ll)
    (return status)))