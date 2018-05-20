(pre-define no-more-data-exit (status-set-both-goto db-status-group-db db-status-id-no-more-data))

(pre-define (db-graph-select-cursor-initialise name state state-field-name)
  (begin
    (db-mdb-cursor-open txn.mdb-txn (struct-pointer-get txn.s (pre-concat dbi- name)) name)
    (status-set-id (mdb-cursor-get name (address-of val-null) (address-of val-null) MDB-FIRST))
    (if (not db-mdb-status-success?)
      (begin
        db-mdb-status-require-notfound
        (status-set-both-goto db-status-group-db db-status-id-no-more-data)))
    (struct-pointer-set state state-field-name name)))

(pre-define (db-graph-select-initialise-set name state)
  (begin
    (define (pre-concat name _set) imht-set-t*)
    (status-require! (db-ids->set name (address-of (pre-concat name _set))))
    (struct-pointer-set state
      name (pre-concat name _set)
      options (bit-or (pre-concat db-read-option-is-set_ name) (struct-pointer-get state options)))))

(pre-define (db-graph-reader-header state)
  (begin
    status-init
    db-mdb-declare-val-graph-key
    (db-define-graph-key graph-key)
    (db-define-graph-record record)
    (define result-temp db-graph-records-t*)
    (define skip? boolean (bit-and db-read-option-skip (struct-pointer-get state options)))))

(pre-define (db-graph-reader-header-0000 state)
  (begin
    status-init
    db-mdb-declare-val-graph-key
    (db-define-graph-record record)
    (define result-temp db-graph-records-t*)
    (define skip? boolean (bit-and db-read-option-skip (struct-pointer-get state options)))))

(pre-define (db-graph-reader-get-ordinal-data state)
  (begin
    (define ordinal-min db-ordinal-t
      (struct-get (pointer-get (struct-pointer-get state ordinal)) min))
    (define ordinal-max db-ordinal-t
      (struct-get (pointer-get (struct-pointer-get state ordinal)) max))))

(define (db-graph-read-1000 state count result)
  (status-t db-graph-read-state-t* b32 db-graph-records-t**)
  (db-graph-reader-header state)
  db-mdb-declare-val-graph-data
  (define left->right MDB-cursor* (struct-pointer-get state cursor))
  (define left db-ids-t* (struct-pointer-get state left))
  (db-mdb-cursor-get! left->right val-graph-key val-graph-data MDB-GET-CURRENT)
  db-mdb-status-require
  (array-set-index graph-key 0 (db-ids-first left))
  (if (db-id-equal? (db-mdb-val->id-at val-graph-key 0) (array-get graph-key 0))
    (goto each-data)
    (label set-range
      (struct-set val-graph-key mv-data graph-key)
      (db-mdb-cursor-get! left->right val-graph-key val-graph-data MDB-SET-RANGE)
      (label each-key
        (if db-mdb-status-success?
          (if (db-id-equal? (db-mdb-val->id-at val-graph-key 0) (array-get graph-key 0))
            (goto each-data))
          db-mdb-status-require-notfound)
        (set left (db-ids-rest left))
        (if left
          (begin
            (array-set-index graph-key 0 (db-ids-first left))
            (goto set-range))
          no-more-data-exit))))
  (label each-data
    stop-if-count-zero
    (if (not skip?)
      (begin
        (struct-set record
          left (db-mdb-val->id-at val-graph-key 0)
          right (db-mdb-val-graph-data->id val-graph-data)
          label (db-mdb-val->id-at val-graph-key 1)
          ordinal (db-mdb-val-graph-data->ordinal val-graph-data))
        (db-graph-records-add! (pointer-get result) record result-temp)))
    reduce-count
    (db-mdb-cursor-next-dup! left->right val-graph-key val-graph-data)
    (if db-mdb-status-success? (goto each-data) db-mdb-status-require-notfound))
  (db-mdb-cursor-next-nodup! left->right val-graph-key val-graph-data)
  (goto each-key)
  (label exit
    (struct-pointer-set state status status left left)
    (return status)))

(define (db-graph-read-1010 state count result)
  (status-t db-graph-read-state-t* b32 db-graph-records-t**)
  (db-graph-reader-header state)
  db-mdb-declare-val-graph-data
  (define left->right MDB-cursor* (struct-pointer-get state cursor))
  (define left db-ids-t* (struct-pointer-get state left))
  (define left-first db-ids-t* (struct-pointer-get state left-first))
  (define label db-ids-t* (struct-pointer-get state label))
  (db-mdb-cursor-get! left->right val-graph-key val-graph-data MDB-GET-CURRENT)
  db-mdb-status-require
  (array-set-index graph-key 0 (db-ids-first left) 1 (db-ids-first label))
  (if (db-graph-key-equal? graph-key (db-mdb-val->graph-key val-graph-key))
    (goto each-data)
    (label set-key
      (struct-set val-graph-key mv-data graph-key)
      (db-mdb-cursor-get! left->right val-graph-key val-graph-data MDB-SET-KEY)
      (if db-mdb-status-success? (goto each-data) db-mdb-status-require-notfound)
      (label next-key
        (set left (db-ids-rest left))
        (if left
          (begin
            (array-set-index graph-key 0 (db-ids-first left))
            (goto set-key))
          (begin
            (set label (db-ids-rest label))
            (if label
              (begin
                (set left left-first)
                (array-set-index graph-key 0 (db-ids-first left) 1 (db-ids-first label))
                (goto set-key))
              no-more-data-exit))))))
  (label each-data
    stop-if-count-zero
    (if (not skip?)
      (begin
        (struct-set record
          left (db-mdb-val->id-at val-graph-key 0)
          right (db-mdb-val-graph-data->id val-graph-data)
          label (db-mdb-val->id-at val-graph-key 1)
          ordinal (db-mdb-val-graph-data->ordinal val-graph-data))
        (db-graph-records-add! (pointer-get result) record result-temp)))
    reduce-count
    (db-mdb-cursor-next-dup! left->right val-graph-key val-graph-data)
    (if db-mdb-status-success? (goto each-data) (goto next-key)))
  (label exit
    (struct-pointer-set state status status left left label label)
    (return status)))

(define (db-graph-read-1100 state count result)
  (status-t db-graph-read-state-t* b32 db-graph-records-t**)
  (db-graph-reader-header state)
  db-mdb-declare-val-id
  (define right->left MDB-cursor* (struct-pointer-get state cursor))
  (define left db-ids-t* (struct-pointer-get state left))
  (define left-first db-ids-t* (struct-pointer-get state left-first))
  (define right db-ids-t* (struct-pointer-get state right))
  (db-mdb-cursor-get! right->left val-graph-key val-id MDB-GET-CURRENT)
  db-mdb-status-require
  (array-set-index graph-key 0 (db-ids-first right))
  (if (db-id-equal? (db-mdb-val->id-at val-graph-key 0) (array-get graph-key 0))
    (goto each-left)
    (label set-range
      (struct-set val-graph-key mv-data graph-key)
      (db-mdb-cursor-get! right->left val-graph-key val-id MDB-SET-RANGE)
      (label each-right
        (if db-mdb-status-success?
          (if (db-id-equal? (db-mdb-val->id-at val-graph-key 0) (array-get graph-key 0))
            (goto each-left))
          db-mdb-status-require-notfound)
        (set right (db-ids-rest right))
        (if right (array-set-index graph-key 0 (db-ids-first right)) no-more-data-exit)
        (goto set-range))))
  (label each-left
    stop-if-count-zero
    (struct-set val-id mv-data (db-ids-first-address left))
    (db-mdb-cursor-get! right->left val-graph-key val-id MDB-GET-BOTH)
    (if db-mdb-status-success?
      (begin
        (if (not skip?)
          (begin
            (struct-set record
              left (db-mdb-val->id val-id)
              right (db-mdb-val->id-at val-graph-key 0) label (db-mdb-val->id-at val-graph-key 1))
            (db-graph-records-add! (pointer-get result) record result-temp)
            reduce-count)))
      db-mdb-status-require-notfound)
    (set left (db-ids-rest left))
    (if left (goto each-left) (set left left-first)))
  (db-mdb-cursor-next-nodup! right->left val-graph-key val-id)
  (goto each-right)
  (label exit
    (struct-pointer-set state status status left left right right)
    (return status)))

(define (db-graph-read-1110 state count result)
  (status-t db-graph-read-state-t* b32 db-graph-records-t**)
  (db-graph-reader-header state)
  db-mdb-declare-val-id
  (define right->left MDB-cursor* (struct-pointer-get state cursor))
  (define left db-ids-t* (struct-pointer-get state left))
  (define left-first db-ids-t* (struct-pointer-get state left-first))
  (define right db-ids-t* (struct-pointer-get state right))
  (define right-first db-ids-t* (struct-pointer-get state right-first))
  (define label db-ids-t* (struct-pointer-get state label))
  (define id-left db-id-t)
  (array-set-index graph-key 1 (db-ids-first label))
  (set id-left (db-ids-first left))
  (array-set-index graph-key 0 (db-ids-first right))
  (label set-cursor
    (struct-set val-graph-key mv-data graph-key)
    (struct-set val-id mv-data (address-of id-left))
    (db-mdb-cursor-get! right->left val-graph-key val-id MDB-GET-BOTH)
    (if db-mdb-status-success? (goto match) db-mdb-status-require-notfound)
    (label next-query
      (set right (db-ids-rest right))
      (if right
        (begin
          stop-if-count-zero
          (array-set-index graph-key 0 (db-ids-first right))
          (goto set-cursor))
        (begin
          (set right right-first)
          (array-set-index graph-key 0 (db-ids-first right))
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
                  (array-set-index graph-key 1 (db-ids-first label))
                  (goto set-cursor))
                no-more-data-exit)))))))
  (label match
    (if (not skip?)
      (begin
        (struct-set record
          left (db-mdb-val->id val-id)
          right (db-mdb-val->id-at val-graph-key 0) label (db-mdb-val->id-at val-graph-key 1))
        (db-graph-records-add! (pointer-get result) record result-temp)))
    reduce-count
    (goto next-query))
  (label exit
    (struct-pointer-set state status status left left right right label label)
    (return status)))

(define (db-graph-read-1001-1101 state count result)
  (status-t db-graph-read-state-t* b32 db-graph-records-t**)
  (db-graph-reader-header state)
  db-mdb-declare-val-graph-data
  (define left->right MDB-cursor* (struct-pointer-get state cursor))
  (define left db-ids-t* (struct-pointer-get state left))
  (define right imht-set-t* (struct-pointer-get state right))
  (db-graph-reader-get-ordinal-data state)
  (db-define-graph-data graph-data)
  (db-graph-data-set-ordinal graph-data ordinal-min)
  (db-mdb-cursor-get! left->right val-graph-key val-graph-data MDB-GET-CURRENT)
  db-mdb-status-require
  (if left (array-set-index graph-key 0 (db-ids-first left)) no-more-data-exit)
  (if
    (and
      (db-id-equal? (db-mdb-val->id-at val-graph-key 0) (array-get graph-key 0))
      (or (not ordinal-min) (>= (db-mdb-val-graph-data->ordinal val-graph-data) ordinal-min))
      (or (not ordinal-max) (<= (db-mdb-val-graph-data->ordinal val-graph-data) ordinal-max)))
    (goto each-data)
    (label each-left
      (struct-set val-graph-key mv-data graph-key)
      (db-mdb-cursor-get! left->right val-graph-key val-graph-data MDB-SET-RANGE)
      (label each-key
        (if db-mdb-status-success?
          (if (db-id-equal? (db-mdb-val->id-at val-graph-key 0) (array-get graph-key 0))
            (begin
              (struct-set val-graph-data mv-data graph-data)
              (db-mdb-cursor-get! left->right val-graph-key val-graph-data MDB-GET-BOTH-RANGE)
              (if db-mdb-status-success? (goto each-data) db-mdb-status-require-notfound)
              (db-mdb-cursor-next-nodup! left->right val-graph-key val-graph-data)
              (goto each-key)))
          db-mdb-status-require-notfound)
        (set left (db-ids-rest left))
        (if left (array-set-index graph-key 0 (db-ids-first left)) no-more-data-exit)
        (goto each-left))))
  (label each-data
    stop-if-count-zero
    (if (or (not ordinal-max) (<= (db-mdb-val-graph-data->ordinal val-graph-data) ordinal-max))
      (begin
        (if (or (not right) (imht-set-contains? right (db-mdb-val-graph-data->id val-graph-data)))
          (begin
            (if (not skip?)
              (begin
                (struct-set record
                  left (db-mdb-val->id-at val-graph-key 0)
                  label (db-mdb-val->id-at val-graph-key 1)
                  ordinal (db-mdb-val-graph-data->ordinal val-graph-data)
                  right (db-mdb-val-graph-data->id val-graph-data))
                (db-graph-records-add! (pointer-get result) record result-temp)))
            reduce-count))
        (db-mdb-cursor-next-dup! left->right val-graph-key val-graph-data)
        (if db-mdb-status-success? (goto each-data) db-mdb-status-require-notfound))))
  (db-mdb-cursor-next-nodup! left->right val-graph-key val-graph-data)
  (goto each-key)
  (label exit
    (struct-pointer-set state status status left left)
    (return status)))

(define (db-graph-read-1011-1111 state count result)
  (status-t db-graph-read-state-t* b32 db-graph-records-t**)
  (db-graph-reader-header state)
  db-mdb-declare-val-graph-data
  (db-define-graph-data graph-data)
  (define left->right MDB-cursor* (struct-pointer-get state cursor))
  (define left db-ids-t* (struct-pointer-get state left))
  (define left-first db-ids-t* (struct-pointer-get state left-first))
  (define label db-ids-t* (struct-pointer-get state label))
  (define right imht-set-t* (struct-pointer-get state right))
  (db-graph-reader-get-ordinal-data state)
  (db-graph-data-set-ordinal graph-data ordinal-min)
  (db-mdb-cursor-get! left->right val-graph-key val-graph-data MDB-GET-CURRENT)
  db-mdb-status-require
  (array-set-index graph-key 0 (db-ids-first left) 1 (db-ids-first label))
  (if (db-graph-key-equal? graph-key (db-mdb-val->graph-key val-graph-key))
    (goto each-data)
    (label set-key
      (struct-set val-graph-key mv-data graph-key)
      (struct-set val-graph-data mv-data graph-data)
      (db-mdb-cursor-get! left->right val-graph-key val-graph-data MDB-GET-BOTH-RANGE)
      (if db-mdb-status-success?
        (goto each-data)
        (begin
          db-mdb-status-require-notfound
          (label each-key
            (set left (db-ids-rest left))
            (if left
              (array-set-index graph-key 0 (db-ids-first left))
              (begin
                (set label (db-ids-rest label))
                (if label
                  (begin
                    (array-set-index graph-key 1 (db-ids-first label))
                    (set left left-first)
                    (array-set-index graph-key 0 (db-ids-first left)))
                  no-more-data-exit)))
            (goto set-key))))))
  (label each-data
    stop-if-count-zero
    (if (or (not ordinal-max) (<= (db-mdb-val-graph-data->ordinal val-graph-data) ordinal-max))
      (begin
        (if (or (not right) (imht-set-contains? right (db-mdb-val-graph-data->id val-graph-data)))
          (begin
            (if (not skip?)
              (begin
                (struct-set record
                  left (db-mdb-val->id-at val-graph-key 0)
                  right (db-mdb-val-graph-data->id val-graph-data)
                  label (db-mdb-val->id-at val-graph-key 1)
                  ordinal (db-mdb-val-graph-data->ordinal val-graph-data))
                (db-graph-records-add! (pointer-get result) record result-temp)))
            reduce-count))
        (db-mdb-cursor-next-dup! left->right val-graph-key val-graph-data)
        (if db-mdb-status-success? (goto each-data) (goto each-key)))
      (goto each-key)))
  (label exit
    (struct-pointer-set state status status left left label label)
    (return status)))

(define (db-graph-read-0010 state count result)
  (status-t db-graph-read-state-t* b32 db-graph-records-t**)
  (db-graph-reader-header state)
  db-mdb-declare-val-id
  db-mdb-declare-val-id-2
  db-mdb-declare-val-graph-data
  (define label->left MDB-cursor* (struct-pointer-get state cursor))
  (define left->right MDB-cursor* (struct-pointer-get state cursor-2))
  (define label db-ids-t* (struct-pointer-get state label))
  (define
    id-left db-id-t
    id-label db-id-t)
  (db-mdb-cursor-get! label->left val-id val-id-2 MDB-GET-CURRENT)
  db-mdb-status-require
  (db-mdb-cursor-get! left->right val-graph-key val-graph-data MDB-GET-CURRENT)
  db-mdb-status-require
  (if label (set id-label (db-ids-first label)) no-more-data-exit)
  (if (db-id-equal? id-label (db-mdb-val->id val-id))
    (begin
      (array-set-index graph-key 1 id-label)
      (goto each-label-data))
    (label set-label-key
      (struct-set val-id mv-data (address-of id-label))
      (db-mdb-cursor-get! label->left val-id val-id-2 MDB-SET-KEY)
      (if db-mdb-status-success?
        (begin
          (array-set-index graph-key 1 id-label)
          (goto each-label-data))
        (begin
          db-mdb-status-require-notfound
          (set label (db-ids-rest label))
          (if label (set id-label (db-ids-first label)) no-more-data-exit)
          (goto set-label-key)))))
  (label each-label-data
    (set id-left (db-mdb-val->id val-id-2))
    (if (db-id-equal? id-left (db-mdb-val->id-at val-graph-key 0))
      (goto each-left-data)
      (begin
        (array-set-index graph-key 0 id-left)
        (struct-set val-graph-key mv-data graph-key)
        (db-mdb-cursor-get! left->right val-graph-key val-graph-data MDB-SET-KEY)
        (if db-mdb-status-success? (goto each-left-data) (goto exit))))
    (label each-left-data
      stop-if-count-zero
      (if (not skip?)
        (begin
          (struct-set record
            left id-left right (db-mdb-val-graph-data->id val-graph-data) label id-label)
          (db-graph-records-add! (pointer-get result) record result-temp)))
      reduce-count
      (db-mdb-cursor-next-dup! left->right val-graph-key val-graph-data)
      (if db-mdb-status-success? (goto each-left-data) db-mdb-status-require-notfound))
    (db-mdb-cursor-next-dup! label->left val-id val-id-2)
    (if db-mdb-status-success?
      (goto each-label-data)
      (begin
        (set label (db-ids-rest label))
        (if label (set id-label (db-ids-first label)) no-more-data-exit)
        (goto set-label-key))))
  (label exit
    (struct-pointer-set state status status label label)
    (return status)))

(define (db-graph-read-0110 state count result)
  (status-t db-graph-read-state-t* b32 db-graph-records-t**)
  (db-graph-reader-header state)
  db-mdb-declare-val-id
  (define right->left MDB-cursor* (struct-pointer-get state cursor))
  (define label db-ids-t* (struct-pointer-get state label))
  (define right db-ids-t* (struct-pointer-get state right))
  (define right-first db-ids-t* (struct-pointer-get state right-first))
  (db-mdb-cursor-get! right->left val-graph-key val-id MDB-GET-CURRENT)
  db-mdb-status-require
  (array-set-index graph-key 1 (db-ids-first label))
  (array-set-index graph-key 0 (db-ids-first right))
  (if (db-graph-key-equal? graph-key (db-mdb-val->graph-key val-graph-key))
    (goto each-data)
    (label set-key
      (struct-set val-graph-key mv-data graph-key)
      (db-mdb-cursor-get! right->left val-graph-key val-id MDB-SET-KEY)
      (if db-mdb-status-success?
        (goto each-data)
        (label each-key
          db-mdb-status-require-notfound
          (set right (db-ids-rest right))
          (if right
            (array-set-index graph-key 0 (db-ids-first right))
            (begin
              (set label (db-ids-rest label))
              (if label
                (begin
                  (array-set-index graph-key 1 (db-ids-first label))
                  (set right right-first)
                  (array-set-index graph-key 0 (db-ids-first right)))
                no-more-data-exit)))
          (goto set-key)))))
  (label each-data
    stop-if-count-zero
    (if (not skip?)
      (begin
        (struct-set record
          left (db-mdb-val->id val-id) right (array-get graph-key 0) label (array-get graph-key 1))
        (db-graph-records-add! (pointer-get result) record result-temp)))
    reduce-count
    (db-mdb-cursor-next-dup! right->left val-graph-key val-id)
    (if db-mdb-status-success? (goto each-data) (goto each-key)))
  (label exit
    (struct-pointer-set state status status right right label label)
    (return status)))

(define (db-graph-read-0100 state count result)
  (status-t db-graph-read-state-t* b32 db-graph-records-t**)
  (db-graph-reader-header state)
  db-mdb-declare-val-id
  (define right->left MDB-cursor* (struct-pointer-get state cursor))
  (define right db-ids-t* (struct-pointer-get state right))
  (db-mdb-cursor-get! right->left val-graph-key val-id MDB-GET-CURRENT)
  db-mdb-status-require
  (array-set-index graph-key 0 (db-ids-first right))
  (if (db-id-equal? (array-get graph-key 0) (db-mdb-val->id-at val-graph-key 0))
    (goto each-key)
    (label set-range
      (struct-set val-graph-key mv-data graph-key)
      (db-mdb-cursor-get! right->left val-graph-key val-id MDB-SET-RANGE)
      (if db-mdb-status-success?
        (if (db-id-equal? (array-get graph-key 0) (db-mdb-val->id-at val-graph-key 0))
          (goto each-key))
        db-mdb-status-require-notfound)
      (set right (db-ids-rest right))
      (if right (array-set-index graph-key 0 (db-ids-first right)) no-more-data-exit)
      (goto set-range)))
  (label each-key
    (label each-data
      stop-if-count-zero
      (if (not skip?)
        (begin
          (struct-set record
            left (db-mdb-val->id val-id)
            right (db-mdb-val->id-at val-graph-key 0) label (db-mdb-val->id-at val-graph-key 1))
          (db-graph-records-add! (pointer-get result) record result-temp)))
      reduce-count
      (db-mdb-cursor-next-dup! right->left val-graph-key val-id)
      (if db-mdb-status-success? (goto each-data) db-mdb-status-require-notfound))
    (db-mdb-cursor-next-nodup! right->left val-graph-key val-id)
    (if db-mdb-status-success?
      (if (db-id-equal? (array-get graph-key 0) (db-mdb-val->id-at val-graph-key 0))
        (goto each-key))
      db-mdb-status-require-notfound)
    (set right (db-ids-rest right))
    (if right (array-set-index graph-key 0 (db-ids-first right)) no-more-data-exit)
    (goto set-range))
  (label exit
    (struct-pointer-set state status status right right)
    (return status)))

(define (db-graph-read-0000 state count result)
  (status-t db-graph-read-state-t* b32 db-graph-records-t**)
  (db-graph-reader-header-0000 state)
  db-mdb-declare-val-graph-data
  (define left->right MDB-cursor* (struct-pointer-get state cursor))
  (db-mdb-cursor-get! left->right val-graph-key val-graph-data MDB-GET-CURRENT)
  db-mdb-status-require
  (label each-key
    (label each-data
      stop-if-count-zero
      (if (not skip?)
        (begin
          (struct-set record
            left (db-mdb-val->id-at val-graph-key 0)
            right (db-mdb-val-graph-data->id val-graph-data)
            label (db-mdb-val->id-at val-graph-key 1)
            ordinal (db-mdb-val-graph-data->ordinal val-graph-data))
          (db-graph-records-add! (pointer-get result) record result-temp)))
      reduce-count
      (db-mdb-cursor-next-dup! left->right val-graph-key val-graph-data)
      (if db-mdb-status-success? (goto each-data) db-mdb-status-require-notfound))
    (db-mdb-cursor-next-nodup! left->right val-graph-key val-graph-data)
    (if db-mdb-status-success? (goto each-key) db-mdb-status-require-notfound))
  (label exit
    (struct-pointer-set state status status)
    (return status)))

(define (db-graph-select txn left right label ordinal offset result)
  (status-t
    db-txn-t db-ids-t* db-ids-t* db-ids-t* db-ordinal-condition-t* b32 db-graph-read-state-t*)
  "prepare the state and select the reader.
  readers are specialised for filter combinations.
  the 1/0 pattern at the end of reader names corresponds to the filter combination the reader is supposed to handle.
  1 stands for filter given, 0 stands for not given. the order is left-right-label-ordinal.
  readers always leave cursors at a valid entry, usually the next entry unless the results have been exhausted"
  status-init
  (db-mdb-cursor-declare-3 left->right right->left label->left)
  (struct-pointer-set result
    status status
    left left
    left-first left
    right right right-first right label label ordinal ordinal cursor 0 cursor-2 0 options 0)
  (if left
    (if ordinal
      (begin
        (if right (db-graph-select-initialise-set right result))
        (db-graph-select-cursor-initialise left->right result cursor)
        (if label
          (struct-pointer-set result reader db-graph-read-1011-1111)
          (struct-pointer-set result reader db-graph-read-1001-1101)))
      (if right
        (begin
          (db-graph-select-cursor-initialise right->left result cursor)
          (if label
            (struct-pointer-set result reader db-graph-read-1110)
            (struct-pointer-set result reader db-graph-read-1100)))
        (begin
          (db-graph-select-cursor-initialise left->right result cursor)
          (if label
            (struct-pointer-set result reader db-graph-read-1010)
            (struct-pointer-set result reader db-graph-read-1000)))))
    (if right
      (begin
        (db-graph-select-cursor-initialise right->left result cursor)
        (struct-pointer-set result reader (if* label db-graph-read-0110 db-graph-read-0100)))
      (if label
        (begin
          (db-graph-select-cursor-initialise label->left result cursor)
          (db-graph-select-cursor-initialise left->right result cursor-2)
          (struct-pointer-set result reader db-graph-read-0010))
        (begin
          (db-graph-select-cursor-initialise left->right result cursor)
          (struct-pointer-set result reader db-graph-read-0000)))))
  (define reader db-graph-reader-t (struct-pointer-get result reader))
  (db-select-ensure-offset result offset reader)
  (label exit
    (struct-pointer-set result status status)
    (return status)))

(define (db-graph-read state count result)
  (status-t db-graph-read-state-t* b32 db-graph-records-t**)
  status-init
  (set count (optional-count count))
  (status-require! (struct-pointer-get state status))
  (set status
    ((convert-type (struct-pointer-get state reader) db-graph-reader-t) state count result))
  (label exit
    db-status-no-more-data-if-mdb-notfound
    (return status)))

(define (db-graph-selection-destroy state) (b0 db-graph-read-state-t*)
  (db-mdb-cursor-close-2 (struct-pointer-get state cursor) (struct-pointer-get state cursor-2))
  (if (bit-and db-read-option-is-set-right (struct-pointer-get state options))
    (begin
      (imht-set-destroy (convert-type (struct-pointer-get state right) imht-set-t*))
      (struct-pointer-set state right 0))))