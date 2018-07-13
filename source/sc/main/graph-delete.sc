(define (db-graph-internal-delete-graph-ll graph-ll id-label id-left)
  (status-t MDB-cursor* db-id-t db-id-t)
  status-declare
  db-mdb-declare-val-id
  db-mdb-declare-val-id-2
  (set
    val-id.mv-data &id-label
    val-id-2.mv-data &id-left)
  (set status.id (mdb-cursor-get graph-ll &val-id &val-id-2 MDB-GET-BOTH))
  (if db-mdb-status-is-success
    (begin
      (db-mdb-status-require (mdb-cursor-del graph-ll 0)))
    db-mdb-status-expect-notfound)
  (set status.id status-id-success)
  (label exit
    (return status)))

(define (db-graph-internal-delete-graph-ll-conditional graph-lr graph-ll id-label id-left)
  (status-t MDB-cursor* MDB-cursor* db-id-t db-id-t)
  status-declare
  db-mdb-declare-val-graph-key
  db-mdb-declare-val-null
  (db-declare-graph-key graph-key)
  (set
    (array-get graph-key 0) id-left
    (array-get graph-key 1) id-label
    val-graph-key.mv-data graph-key
    status.id (mdb-cursor-get graph-lr &val-graph-key &val-null MDB-SET))
  (if db-mdb-status-is-notfound
    (return (db-graph-internal-delete-graph-ll graph-ll id-label id-left)))
  (label exit
    (return status)))

(define (db-graph-internal-delete-graph-rl graph-rl id-left id-right id-label)
  (status-t MDB-cursor* db-id-t db-id-t db-id-t)
  status-declare
  db-mdb-declare-val-graph-key
  db-mdb-declare-val-id
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
    (declare
      id-label db-id-t
      id-left db-id-t)
    (label set-key-0010
      (set
        id-label (db-ids-first label)
        val-id.mv-data &id-label)
      (set status.id (mdb-cursor-get graph-ll &val-id &val-id-2 MDB-SET-KEY))
      (if db-mdb-status-is-success (goto each-data-0010)
        db-mdb-status-expect-notfound)
      (label each-key-0010
        (set label (db-ids-rest label))
        (if label (goto set-key-0010)
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
          db-mdb-status-require-read
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
    (declare
      id-right db-id-t
      id-left db-id-t
      id-label db-id-t
      right-pointer db-ids-t*)
    (set right-pointer right)
    (label set-key-0110
      (set
        id-right (db-ids-first right-pointer)
        id-label (db-ids-first label)
        (array-get graph-key 0) id-right
        (array-get graph-key 1) id-label
        val-graph-key.mv-data graph-key)
      (set status.id (mdb-cursor-get graph-rl &val-graph-key &val-id MDB-SET-KEY))
      (if db-mdb-status-is-success (goto each-data-0110)
        db-mdb-status-expect-notfound)
      (label each-key-0110
        (set right-pointer (db-ids-rest right-pointer))
        (if right-pointer (goto set-key-0110)
          (begin
            (set label (db-ids-rest label))
            (if label
              (begin
                (set right-pointer right)
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
          (if db-mdb-status-is-success (db-mdb-status- (mdb-cursor-del graph-lr 0))
            db-mdb-status-expect-notfound))
        db-mdb-status-expect-notfound)
      (db-mdb-status-requir-read (db-graph-internal-delete-graph-ll graph-ll id-label id-left))
      (db-mdb-status-require (mdb-cursor-del graph-rl 0))
      (set status.id (mdb-cursor-get graph-rl &val-graph-key &val-id MDB-NEXT-DUP))
      (if db-mdb-status-is-success (goto each-data-0110)
        db-mdb-status-expect-notfound))
    (goto each-key-0110)))

(pre-define db-graph-internal-delete-1010
  (begin
    (declare
      id-label db-id-t
      id-left db-id-t
      label-pointer db-ids-t*)
    (while left
      (set
        id-left (db-ids-first left)
        label-pointer label)
      (while label-pointer
        (set
          id-label (db-ids-first label-pointer)
          (array-get graph-key 0) id-left
          (array-get graph-key 1) id-label
          val-graph-key.mv-data graph-key)
        (set status.id (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-SET-KEY))
        (if db-mdb-status-is-success
          (begin
            (do-while db-mdb-status-is-success
              (db-graph-internal-delete-graph-rl
                graph-rl id-left (db-graph-data->id val-graph-data.mv-data) id-label)
              (db-graph-internal-delete-graph-ll graph-ll id-label id-left)
              (set status.id (mdb-cursor-get graph-lr &val-graph-key &val-graph-data NEXT-DUP)))
            db-mdb-status-expect-notfound
            (set
              (array-get graph-key 0) id-left
              (array-get graph-key 1) id-label
              val-graph-key.mv-data graph-key)
            (db-mdb-status-require
              (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-SET-KEY))
            (db-mdb-status-require (mdb-cursor-del graph-lr MDB-NODUPDATA)))
          db-mdb-status-expect-notfound)
        (set label-pointer (db-ids-rest label-pointer)))
      (set left (db-ids-rest left)))))

(pre-define db-graph-internal-delete-0100
  (begin
    (declare
      id-left db-id-t
      id-right db-id-t
      id-label db-id-t)
    (label set-range-0100
      (set
        id-right (db-ids-first right)
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
      (set right (db-ids-rest right))
      (if right (goto set-range-0100)
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
      (status-require
        (db-graph-internal-delete-graph-ll-conditional graph-lr graph-ll id-label id-left))
      (db-mdb-status-require (mdb-cursor-del graph-rl 0))
      (set status.id (mdb-cursor-get graph-rl &val-graph-key &val-id MDB-NEXT-DUP))
      (if db-mdb-status-is-success (goto each-data-0100)
        db-mdb-status-expect-notfound))
    (goto set-range-0100)))

(pre-define db-graph-internal-delete-1000
  (begin
    (declare
      id-left db-id-t
      id-label db-id-t
      id-right db-id-t)
    (label set-range-1000
      (set
        id-left (db-ids-first left)
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
        (set left (db-ids-rest left))
        (if left (goto set-range-1000)
          (goto exit))))
    (label each-data-1000
      (set id-right (db-graph-data->id val-graph-data.mv-data))
      (db-graph-internal-delete-graph-rl graph-rl id-left id-right id-label)
      (db-graph-internal-delete-graph-ll graph-ll id-label id-left)
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
    (declare
      id-left db-id-t
      id-right db-id-t
      id-label db-id-t
      right-set imht-set-t*)
    (status-require (db-ids->set right &right-set))
    (set (array-get graph-key 1) 0)
    (label set-range-1100
      (set
        id-left (db-ids-first left)
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
        (set left (db-ids-rest left))
        (if left
          (begin
            (set (array-get graph-key 1) 0)
            (goto set-range-1100))
          (goto exit))))
    (label each-data-1100
      (set id-right (db-graph-data->id val-graph-data.mv-data))
      (if (imht-set-contains? right-set id-right)
        (begin
          (db-graph-internal-delete-graph-rl graph-rl id-left id-right id-label)
          (db-mdb-status-require (mdb-cursor-del graph-lr 0))))
      (set status.id (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-NEXT-DUP))
      (if db-mdb-status-is-success (goto each-data-1100)
        db-mdb-status-expect-notfound))
    (status-require
      (db-graph-internal-delete-graph-ll-conditional graph-lr graph-ll id-label id-left))
    (set
      (array-get graph-key 0) id-left
      (array-get graph-key 1) id-label
      val-graph-key.mv-data graph-key)
    (set status.id (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-SET-KEY))
    (cond
      (db-mdb-status-is-success
        (set status.id (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-NEXT-NODUP))
        (goto each-key-1100))
      ((status-id-is? MDB-NOTFOUND) (goto set-range-1100))
      (else (status-set-group-goto db-status-group-lmdb)))))

(pre-define db-graph-internal-delete-1110
  (begin
    (declare
      id-left db-id-t
      id-label db-id-t
      right-set imht-set-t*
      id-right db-id-t)
    (define label-first db-ids-t* label)
    (status-require (db-ids->set right &right-set))
    (while left
      (set id-left (db-ids-first left))
      (while label
        (set
          id-label (db-ids-first label)
          (array-get graph-key 0) id-left
          (array-get graph-key 1) id-label
          val-graph-key.mv-data graph-key)
        (set status.id (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-SET-KEY))
        (while db-mdb-status-is-success
          (if (imht-set-contains? right-set (db-graph-data->id val-graph-data.mv-data))
            (begin
              (set id-right (db-graph-data->id val-graph-data.mv-data))
              (db-graph-internal-delete-graph-rl graph-rl id-left id-right id-label)
              (db-mdb-status-require (mdb-cursor-del graph-lr 0))))
          (set status.id (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-NEXT-DUP)))
        (db-graph-internal-delete-graph-ll-conditional graph-lr graph-ll id-label id-left)
        (set label (db-ids-rest label)))
      (set
        label label-first
        left (db-ids-rest left)))))

(pre-define (db-graph-internal-delete-get-ordinal-data ordinal)
  (begin
    (define ordinal-min db-ordinal-t ordinal:min)
    (define ordinal-max db-ordinal-t ordinal:max)))

(pre-define db-graph-internal-delete-1001-1101
  (begin
    (declare
      id-left db-id-t
      id-right db-id-t
      id-label db-id-t
      right-set imht-set-t*)
    (db-graph-internal-delete-get-ordinal-data ordinal)
    (set
      (array-get graph-data 0) ordinal-min
      (array-get graph-key 1) 0)
    (if right (status-require (db-ids->set right &right-set)))
    (label set-range-1001-1101
      (set
        id-left (db-ids-first left)
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
        (set left (db-ids-rest left))
        (if left
          (begin
            (set (array-get graph-key 1) 0)
            (goto set-range-1001-1101))
          (goto exit))))
    (label each-data-1001-1101
      (if (or (not ordinal-max) (<= (db-graph-data->ordinal val-graph-data.mv-data) ordinal-max))
        (begin
          (set id-right (db-graph-data->id val-graph-data.mv-data))
          (if (or (not right) (imht-set-contains? right-set id-right))
            (begin
              (set status (db-graph-internal-delete-graph-rl graph-rl id-left id-right id-label))
              db-mdb-status-require-read
              (db-mdb-status-require (mdb-cursor-del graph-lr 0)))))
        (goto next-label-1001-1101))
      (set status.id (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-NEXT-DUP))
      (if db-mdb-status-is-success (goto each-data-1001-1101)
        db-mdb-status-expect-notfound))
    (status-require
      (db-graph-internal-delete-graph-ll-conditional graph-lr graph-ll id-label id-left))
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
        ((status-id-is? MDB-NOTFOUND) (goto set-range-1001-1101))
        (else (status-set-group-goto db-status-group-lmdb))))))

(pre-define db-graph-internal-delete-1011-1111
  (begin
    (declare
      id-left db-id-t
      id-label db-id-t
      right-set imht-set-t*
      id-right db-id-t
      left-pointer db-ids-t*)
    (db-graph-internal-delete-get-ordinal-data ordinal)
    (if right (status-require (db-ids->set right &right-set)))
    (set
      left-pointer left
      (array-get graph-data 0) ordinal-min
      id-label (db-ids-first label))
    (label set-key-1011-1111
      (set
        id-left (db-ids-first left-pointer)
        (array-get graph-key 0) id-left
        (array-get graph-key 1) id-label
        val-graph-key.mv-data graph-key
        val-graph-data.mv-data graph-data)
      (set status.id (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-GET-BOTH-RANGE))
      (if db-mdb-status-is-success (goto each-data-1011-1111)
        (label each-key-1011-1111
          (set left-pointer (db-ids-rest left-pointer))
          (if left-pointer (goto set-key-1011-1111)
            (begin
              (set label (db-ids-rest label))
              (if label
                (begin
                  (set
                    left-pointer left
                    id-label (db-ids-first label))
                  (goto set-key-1011-1111))
                (goto exit)))))))
    (label each-data-1011-1111
      (if (or (not ordinal-max) (<= (db-graph-data->ordinal val-graph-data.mv-data) ordinal-max))
        (begin
          (if
            (or
              (not right) (imht-set-contains? right-set (db-graph-data->id val-graph-data.mv-data)))
            (begin
              (sc-comment "delete graph-rl")
              (set
                id-right (db-graph-data->id val-graph-data.mv-data)
                status (db-graph-internal-delete-graph-rl graph-rl id-left id-right id-label))
              db-mdb-status-require-read
              (db-mdb-status-require (mdb-cursor-del graph-lr 0))))
          (set status.id (mdb-cursor-get graph-lr &val-graph-key &val-graph-data MDB-NEXT-DUP))
          (if db-mdb-status-is-success (goto each-data-1011-1111)
            db-mdb-status-expect-notfound))))
    (status-require
      (db-graph-internal-delete-graph-ll-conditional graph-lr graph-ll id-label id-left))
    (goto each-key-1011-1111)))

(define (db-graph-internal-delete left right label ordinal graph-lr graph-rl graph-ll)
  (status-t
    db-ids-t* db-ids-t* db-ids-t* db-ordinal-condition-t* MDB-cursor* MDB-cursor* MDB-cursor*)
  "db-graph-internal-delete does not open/close cursors.
   1111 / left-right-label-ordinal.
   tip: the code is nice to debug if current state information is displayed near the
     beginning of goto labels before cursor operations.
     example: (debug-log \"each-key-1100 %lu %lu\" id-left id-right)
   db-graph-internal-delete-* macros are allowed to leave status on MDB-NOTFOUND"
  status-declare
  db-mdb-declare-val-graph-key
  db-mdb-declare-val-graph-data
  db-mdb-declare-val-id
  db-mdb-declare-val-id-2
  (db-declare-graph-key graph-key)
  (db-declare-graph-data graph-data)
  (if left
    (if ordinal
      (if label db-graph-internal-delete-1011-1111
        db-graph-internal-delete-1001-1101)
      (if label
        (if right db-graph-internal-delete-1110
          db-graph-internal-delete-1010)
        (if right db-graph-internal-delete-1100
          db-graph-internal-delete-1000)))
    (if right
      (if label db-graph-internal-delete-0110
        db-graph-internal-delete-0100)
      (if label db-graph-internal-delete-0010
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