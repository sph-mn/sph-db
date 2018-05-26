(define (db-graph-internal-delete-graph-ll graph-ll id-label id-left)
  (status-t MDB-cursor* db-id-t db-id-t)
  db-mdb-declare-val-id
  db-mdb-declare-val-id-2
  status-init
  (struct-set val-id
    mv-data (address-of id-label))
  (struct-set val-id-2
    mv-data (address-of id-left))
  (db-mdb-cursor-get! graph-ll val-id val-id-2 MDB-GET-BOTH)
  (if db-mdb-status-success?
    (begin
      (db-mdb-cursor-del! graph-ll 0)
      db-mdb-status-require)
    db-mdb-status-require-notfound)
  (status-set-id status-id-success)
  (label exit
    (return status)))

(define (db-graph-internal-delete-graph-ll-conditional graph-lr graph-ll id-label id-left)
  (status-t MDB-cursor* MDB-cursor* db-id-t db-id-t)
  status-init
  db-mdb-declare-val-graph-key
  (db-define-graph-key graph-key)
  (array-set-index graph-key 0 id-left 1 id-label)
  (struct-set val-graph-key
    mv-data graph-key)
  (db-mdb-cursor-get! graph-lr val-graph-key val-null MDB-SET)
  (if (status-id-is? MDB-NOTFOUND)
    (return (db-graph-internal-delete-graph-ll graph-ll id-label id-left))
    db-mdb-status-require)
  (label exit
    (return status)))

(define (db-graph-internal-delete-graph-rl graph-rl id-left id-right id-label)
  (status-t MDB-cursor* db-id-t db-id-t db-id-t)
  status-init
  db-mdb-declare-val-graph-key
  db-mdb-declare-val-id
  (db-define-graph-key graph-key)
  (array-set-index graph-key 0 id-right 1 id-label)
  (struct-set val-graph-key
    mv-data graph-key)
  (struct-set val-id
    mv-data (address-of id-left))
  (db-mdb-cursor-get! graph-rl val-graph-key val-id MDB-GET-BOTH)
  (if db-mdb-status-success?
    (begin
      (db-mdb-cursor-del! graph-rl 0)
      db-mdb-status-require)
    db-mdb-status-require-notfound)
  (label exit
    (return status)))

(pre-define (db-graph-internal-delete-0010)
  (begin
    (declare
      id-label db-id-t
      id-left db-id-t)
    (label set-key-0010
      (set id-label (db-ids-first label))
      (struct-set val-id
        mv-data (address-of id-label))
      (db-mdb-cursor-get! graph-ll val-id val-id-2 MDB-SET-KEY)
      (if db-mdb-status-success?
        (goto each-data-0010)
        db-mdb-status-require-notfound)
      (label each-key-0010
        (set label (db-ids-rest label))
        (if label
          (goto set-key-0010)
          (goto exit))))
    (label each-data-0010
      (set id-left (db-mdb-val->id val-id-2))
      (array-set-index graph-key 0 id-left 1 id-label)
      (struct-set val-graph-key
        mv-data graph-key)
      (db-mdb-cursor-get! graph-lr val-graph-key val-graph-data MDB-SET-KEY)
      (if db-mdb-status-success?
        (label each-data-2-0010
          (set status
            (db-graph-internal-delete-graph-rl
              graph-rl id-left (db-mdb-val-graph-data->id val-graph-data) id-label))
          db-mdb-status-require-read
          (db-mdb-cursor-del! graph-lr 0)
          db-mdb-status-require
          (db-mdb-cursor-next-dup! graph-lr val-graph-key val-graph-data)
          (if db-mdb-status-success?
            (goto each-data-2-0010)
            db-mdb-status-require-notfound))
        db-mdb-status-require-notfound)
      (db-mdb-cursor-del! graph-ll 0)
      db-mdb-status-require
      (db-mdb-cursor-next-dup! graph-ll val-id val-id-2)
      (if db-mdb-status-success?
        (goto each-data-0010)
        db-mdb-status-require-notfound)
      (goto each-key-0010))))

(pre-define (db-graph-internal-delete-0110)
  (begin
    (declare
      id-right db-id-t
      id-left db-id-t
      id-label db-id-t)
    (define right-pointer db-ids-t* right)
    (label set-key-0110
      (set
        id-right (db-ids-first right-pointer)
        id-label (db-ids-first label))
      (array-set-index graph-key 0 id-right 1 id-label)
      (struct-set val-graph-key
        mv-data graph-key)
      (db-mdb-cursor-get! graph-rl val-graph-key val-id MDB-SET-KEY)
      (if db-mdb-status-success?
        (goto each-data-0110)
        db-mdb-status-require-notfound)
      (label each-key-0110
        (set right-pointer (db-ids-rest right-pointer))
        (if right-pointer
          (goto set-key-0110)
          (begin
            (set label (db-ids-rest label))
            (if label
              (begin
                (set right-pointer right)
                (goto set-key-0110))
              (goto exit))))))
    (label each-data-0110
      (set id-left (db-mdb-val->id val-id))
      (array-set-index graph-key 0 id-left 1 id-label)
      (struct-set val-graph-key
        mv-data graph-key)
      (db-mdb-cursor-get! graph-lr val-graph-key val-graph-data MDB-SET-KEY)
      (if db-mdb-status-success?
        (begin
          (set status (db-mdb-graph-lr-seek-right graph-lr id-right))
          (if db-mdb-status-success?
            (begin
              (db-mdb-cursor-del! graph-lr 0)
              db-mdb-status-require)
            db-mdb-status-require-notfound))
        db-mdb-status-require-notfound)
      (set status (db-graph-internal-delete-graph-ll graph-ll id-label id-left))
      db-mdb-status-require-read
      (db-mdb-cursor-del! graph-rl 0)
      db-mdb-status-require
      (db-mdb-cursor-next-dup! graph-rl val-graph-key val-id)
      (if db-mdb-status-success?
        (goto each-data-0110)
        db-mdb-status-require-notfound))
    (goto each-key-0110)))

(pre-define (db-graph-internal-delete-1010)
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
        (set id-label (db-ids-first label-pointer))
        (array-set-index graph-key 0 id-left 1 id-label)
        (struct-set val-graph-key
          mv-data graph-key)
        (db-mdb-cursor-get! graph-lr val-graph-key val-graph-data MDB-SET-KEY)
        (if db-mdb-status-success?
          (begin
            (do-while db-mdb-status-success?
              (db-graph-internal-delete-graph-rl
                graph-rl id-left (db-mdb-val-graph-data->id val-graph-data) id-label)
              (db-graph-internal-delete-graph-ll graph-ll id-label id-left)
              (db-mdb-cursor-next-dup! graph-lr val-graph-key val-graph-data))
            db-mdb-status-require-notfound
            (array-set-index graph-key 0 id-left 1 id-label)
            (struct-set val-graph-key
              mv-data graph-key)
            (db-mdb-cursor-get! graph-lr val-graph-key val-graph-data MDB-SET-KEY)
            db-mdb-status-require
            (db-mdb-cursor-del! graph-lr MDB-NODUPDATA)
            db-mdb-status-require)
          db-mdb-status-require-notfound)
        (set label-pointer (db-ids-rest label-pointer)))
      (set left (db-ids-rest left)))))

(pre-define (db-graph-internal-delete-0100)
  (begin
    (declare
      id-left db-id-t
      id-right db-id-t
      id-label db-id-t)
    (label set-range-0100
      (set id-right (db-ids-first right))
      (array-set-index graph-key 0 id-right 1 0)
      (struct-set val-graph-key
        mv-data graph-key)
      (db-mdb-cursor-get! graph-rl val-graph-key val-id MDB-SET-RANGE)
      (if db-mdb-status-success?
        (if (= id-right (db-mdb-val->id-at val-graph-key 0))
          (begin
            (if db-mdb-status-success?
              (begin))
            (set id-label (db-mdb-val->id-at val-graph-key 1))
            (goto each-data-0100)))
        db-mdb-status-require-notfound)
      (set right (db-ids-rest right))
      (if right
        (goto set-range-0100)
        (goto exit)))
    (label each-data-0100
      (set id-left (db-mdb-val->id val-id))
      (array-set-index graph-key 0 id-left 1 id-label)
      (struct-set val-graph-key
        mv-data graph-key)
      (db-mdb-cursor-get! graph-lr val-graph-key val-graph-data MDB-SET-KEY)
      (if db-mdb-status-success?
        (begin
          (set status (db-mdb-graph-lr-seek-right graph-lr id-right))
          (if db-mdb-status-success?
            (begin
              (db-mdb-cursor-del! graph-lr 0)
              db-mdb-status-require)
            db-mdb-status-require-notfound))
        db-mdb-status-require-notfound)
      (status-require!
        (db-graph-internal-delete-graph-ll-conditional graph-lr graph-ll id-label id-left))
      (db-mdb-cursor-del! graph-rl 0)
      db-mdb-status-require
      (db-mdb-cursor-next-dup! graph-rl val-graph-key val-id)
      (if db-mdb-status-success?
        (goto each-data-0100)
        db-mdb-status-require-notfound))
    (goto set-range-0100)))

(pre-define (db-graph-internal-delete-1000)
  (begin
    (declare
      id-left db-id-t
      id-label db-id-t
      id-right db-id-t)
    (label set-range-1000
      (set id-left (db-ids-first left))
      (array-set-index graph-key 0 id-left 1 0)
      (struct-set val-graph-key
        mv-data graph-key)
      (db-mdb-cursor-get! graph-lr val-graph-key val-graph-data MDB-SET-RANGE)
      (label each-key-1000
        (if db-mdb-status-success?
          (if (= id-left (db-mdb-val->id-at val-graph-key 0))
            (begin
              (set id-label (db-mdb-val->id-at val-graph-key 1))
              (goto each-data-1000)))
          db-mdb-status-require-notfound)
        (set left (db-ids-rest left))
        (if left
          (goto set-range-1000)
          (goto exit))))
    (label each-data-1000
      (set id-right (db-mdb-val-graph-data->id val-graph-data))
      (db-graph-internal-delete-graph-rl graph-rl id-left id-right id-label)
      (db-graph-internal-delete-graph-ll graph-ll id-label id-left)
      (db-mdb-cursor-next-dup! graph-lr val-graph-key val-graph-data)
      (if db-mdb-status-success?
        (goto each-data-1000)
        db-mdb-status-require-notfound))
    (array-set-index graph-key 0 id-left 1 id-label)
    (db-mdb-cursor-get! graph-lr val-graph-key val-graph-data MDB-SET-KEY)
    db-mdb-status-require
    (db-mdb-cursor-del! graph-lr MDB-NODUPDATA)
    db-mdb-status-require
    (db-mdb-cursor-next-nodup! graph-lr val-graph-key val-graph-data)
    (goto each-key-1000)))

(pre-define (db-graph-internal-delete-1100)
  (begin
    (declare
      id-left db-id-t
      id-right db-id-t
      id-label db-id-t
      right-set imht-set-t*)
    (status-require! (db-ids->set right (address-of right-set)))
    (array-set-index graph-key 1 0)
    (label set-range-1100
      (set id-left (db-ids-first left))
      (array-set-index graph-key 0 id-left)
      (struct-set val-graph-key
        mv-data graph-key)
      (db-mdb-cursor-get! graph-lr val-graph-key val-graph-data MDB-SET-RANGE)
      (label each-key-1100
        (if db-mdb-status-success?
          (if (= id-left (db-mdb-val->id-at val-graph-key 0))
            (begin
              (set id-label (db-mdb-val->id-at val-graph-key 1))
              (goto each-data-1100)))
          db-mdb-status-require-notfound)
        (set left (db-ids-rest left))
        (if left
          (begin
            (array-set-index graph-key 1 0)
            (goto set-range-1100))
          (goto exit))))
    (label each-data-1100
      (set id-right (db-mdb-val-graph-data->id val-graph-data))
      (if (imht-set-contains? right-set id-right)
        (begin
          (db-graph-internal-delete-graph-rl graph-rl id-left id-right id-label)
          (db-mdb-cursor-del! graph-lr 0)
          db-mdb-status-require))
      (db-mdb-cursor-next-dup! graph-lr val-graph-key val-graph-data)
      (if db-mdb-status-success?
        (goto each-data-1100)
        db-mdb-status-require-notfound))
    (status-require!
      (db-graph-internal-delete-graph-ll-conditional graph-lr graph-ll id-label id-left))
    (array-set-index graph-key 0 id-left 1 id-label)
    (struct-set val-graph-key
      mv-data graph-key)
    (db-mdb-cursor-get! graph-lr val-graph-key val-graph-data MDB-SET-KEY)
    (cond
      (db-mdb-status-success?
        (db-mdb-cursor-next-nodup! graph-lr val-graph-key val-graph-data) (goto each-key-1100))
      ((status-id-is? MDB-NOTFOUND) (goto set-range-1100))
      (else (status-set-group-goto db-status-group-lmdb)))))

(pre-define (db-graph-internal-delete-1110)
  (begin
    (declare
      id-left db-id-t
      id-label db-id-t
      right-set imht-set-t*
      id-right db-id-t)
    (define label-first db-ids-t* label)
    (status-require! (db-ids->set right (address-of right-set)))
    (while left
      (set id-left (db-ids-first left))
      (while label
        (set id-label (db-ids-first label))
        (array-set-index graph-key 0 id-left 1 id-label)
        (struct-set val-graph-key
          mv-data graph-key)
        (db-mdb-cursor-get! graph-lr val-graph-key val-graph-data MDB-SET-KEY)
        (while db-mdb-status-success?
          (if (imht-set-contains? right-set (db-mdb-val-graph-data->id val-graph-data))
            (begin
              (set id-right (db-mdb-val-graph-data->id val-graph-data))
              (db-graph-internal-delete-graph-rl graph-rl id-left id-right id-label)
              (begin
                (db-mdb-cursor-del! graph-lr 0)
                db-mdb-status-require)))
          (db-mdb-cursor-next-dup! graph-lr val-graph-key val-graph-data))
        (db-graph-internal-delete-graph-ll-conditional graph-lr graph-ll id-label id-left)
        (set label (db-ids-rest label)))
      (set label label-first)
      (set left (db-ids-rest left)))))

(pre-define (db-graph-internal-delete-get-ordinal-data ordinal)
  (begin
    (define ordinal-min db-ordinal-t ordinal:min)
    (define ordinal-max db-ordinal-t ordinal:max)))

(pre-define (db-graph-internal-delete-1001-1101)
  (begin
    (declare
      id-left db-id-t
      id-right db-id-t
      id-label db-id-t
      right-set imht-set-t*)
    (db-graph-internal-delete-get-ordinal-data ordinal)
    (array-set-index graph-data 0 ordinal-min)
    (array-set-index graph-key 1 0)
    (if right
      (status-require! (db-ids->set right (address-of right-set))))
    (label set-range-1001-1101
      (set id-left (db-ids-first left))
      (array-set-index graph-key 0 id-left)
      (struct-set val-graph-key
        mv-data graph-key)
      (db-mdb-cursor-get! graph-lr val-graph-key val-graph-data MDB-SET-RANGE)
      (label each-key-1001-1101
        (if db-mdb-status-success?
          (if (= id-left (db-mdb-val->id-at val-graph-key 0))
            (begin
              (struct-set val-graph-data
                mv-data graph-data)
              (db-mdb-cursor-get! graph-lr val-graph-key val-graph-data MDB-GET-BOTH-RANGE)
              (if db-mdb-status-success?
                (begin
                  (set id-label (db-mdb-val->id-at val-graph-key 1))
                  (goto each-data-1001-1101))
                db-mdb-status-require-notfound)
              (db-mdb-cursor-next-nodup! graph-lr val-graph-key val-graph-data)
              (goto each-key-1001-1101)))
          db-mdb-status-require-notfound)
        (set left (db-ids-rest left))
        (if left
          (begin
            (array-set-index graph-key 1 0)
            (goto set-range-1001-1101))
          (goto exit))))
    (label each-data-1001-1101
      (if (or (not ordinal-max) (<= (db-mdb-val-graph-data->ordinal val-graph-data) ordinal-max))
        (begin
          (set id-right (db-mdb-val-graph-data->id val-graph-data))
          (if (or (not right) (imht-set-contains? right-set id-right))
            (begin
              (set status
                (db-graph-internal-delete-graph-rl graph-rl id-left id-right id-label))
              db-mdb-status-require-read
              (db-mdb-cursor-del! graph-lr 0)
              db-mdb-status-require)))
        (goto next-label-1001-1101))
      (db-mdb-cursor-next-dup! graph-lr val-graph-key val-graph-data)
      (if db-mdb-status-success?
        (goto each-data-1001-1101)
        db-mdb-status-require-notfound))
    (status-require!
      (db-graph-internal-delete-graph-ll-conditional graph-lr graph-ll id-label id-left))
    (label next-label-1001-1101
      (array-set-index graph-key 0 id-left 1 id-label)
      (struct-set val-graph-key
        mv-data graph-key)
      (db-mdb-cursor-get! graph-lr val-graph-key val-graph-data MDB-SET-KEY)
      (cond
        (db-mdb-status-success?
          (db-mdb-cursor-next-nodup! graph-lr val-graph-key val-graph-data)
          (goto each-key-1001-1101))
        ((status-id-is? MDB-NOTFOUND) (goto set-range-1001-1101))
        (else (status-set-group-goto db-status-group-lmdb))))))

(pre-define (db-graph-internal-delete-1011-1111)
  (begin
    (declare
      id-left db-id-t
      id-label db-id-t
      right-set imht-set-t*
      id-right db-id-t)
    (define left-pointer db-ids-t* left)
    (if right
      (status-require! (db-ids->set right (address-of right-set))))
    (db-graph-internal-delete-get-ordinal-data ordinal)
    (array-set-index graph-data 0 ordinal-min)
    (set id-label (db-ids-first label))
    (label set-key-1011-1111
      (set id-left (db-ids-first left-pointer))
      (array-set-index graph-key 0 id-left 1 id-label)
      (struct-set val-graph-key
        mv-data graph-key)
      (struct-set val-graph-data
        mv-data graph-data)
      (db-mdb-cursor-get! graph-lr val-graph-key val-graph-data MDB-GET-BOTH-RANGE)
      (if db-mdb-status-success?
        (goto each-data-1011-1111)
        (label each-key-1011-1111
          (set left-pointer (db-ids-rest left-pointer))
          (if left-pointer
            (goto set-key-1011-1111)
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
      (if (or (not ordinal-max) (<= (db-mdb-val-graph-data->ordinal val-graph-data) ordinal-max))
        (begin
          (if
            (or
              (not right) (imht-set-contains? right-set (db-mdb-val-graph-data->id val-graph-data)))
            (begin
              ;delete graph-rl
              (set id-right (db-mdb-val-graph-data->id val-graph-data))
              (set status
                (db-graph-internal-delete-graph-rl graph-rl id-left id-right id-label))
              db-mdb-status-require-read
              (db-mdb-cursor-del! graph-lr 0)
              db-mdb-status-require))
          (db-mdb-cursor-next-dup! graph-lr val-graph-key val-graph-data)
          (if db-mdb-status-success?
            (goto each-data-1011-1111)
            db-mdb-status-require-notfound))))
    (status-require!
      (db-graph-internal-delete-graph-ll-conditional graph-lr graph-ll id-label id-left))
    (goto each-key-1011-1111)))

(define (db-graph-internal-delete left right label ordinal graph-lr graph-rl graph-ll)
  (status-t
    db-ids-t* db-ids-t* db-ids-t* db-ordinal-condition-t* MDB-cursor* MDB-cursor* MDB-cursor*)
  "db-graph-internal-delete does not open/close cursors.
  1111 / left-right-label-ordinal.
  tip: the code is nice to debug if variable state is displayed near the
    beginning of goto labels, before cursor operations.
    example display on stdout: (debug-log \"each-key-1100 %lu %lu\" id-left id-right)"
  status-init
  db-mdb-declare-val-graph-key
  db-mdb-declare-val-graph-data
  db-mdb-declare-val-id
  db-mdb-declare-val-id-2
  (db-define-graph-key graph-key)
  (db-define-graph-data graph-data)
  ; db-graph-internal-delete-* macros are allowed to leave status on MDB-NOTFOUND
  (if left
    (if ordinal
      (if label
        (db-graph-internal-delete-1011-1111)
        (db-graph-internal-delete-1001-1101))
      (if label
        (if right
          (db-graph-internal-delete-1110)
          (db-graph-internal-delete-1010))
        (if right
          (db-graph-internal-delete-1100)
          (db-graph-internal-delete-1000))))
    (if right
      (if label
        (db-graph-internal-delete-0110)
        (db-graph-internal-delete-0100))
      (if label
        (db-graph-internal-delete-0010)
        (db-status-set-id-goto db-status-id-not-implemented))))
  (label exit
    db-status-success-if-mdb-notfound
    (return status)))

(define (db-graph-delete txn left right label ordinal)
  (status-t db-txn-t db-ids-t* db-ids-t* db-ids-t* db-ordinal-condition-t*)
  status-init
  (db-mdb-cursor-define-3
    txn.mdb-txn
    (struct-pointer-get txn.s dbi-graph-lr)
    graph-lr
    (struct-pointer-get txn.s dbi-graph-rl)
    graph-rl (struct-pointer-get txn.s dbi-graph-ll) graph-ll)
  (set status
    (db-graph-internal-delete left right label ordinal graph-lr graph-rl graph-ll))
  (label exit
    (db-mdb-cursor-close-3 graph-lr graph-rl graph-ll)
    (return status)))