(define (db-index-get type fields fields-len) (db-index-t* db-type-t* db-field-count-t*)
  (declare
    indices-count db-index-count-t
    index db-index-count-t
    indices db-index-t*)
  (set
    indices type:indices
    indices-count type:indices-count)
  (for ((set index 0) (< index indices-count) (set index (+ 1 index)))
    (if
      (=
        0
        (memcmp
          (struct-get (array-get indices index) fields) fields (* (sizeof db-field-t) fields-len)))
      (return (+ index indices))))
  (return 0))

(define (string-join strings strings-len delimiter) (b8* b8** size-t b8*)
  "join strings into one string with each input string separated by delimiter.
  zero if strings-len is zero or memory could not be allocated"
  (declare
    result b8*
    result-temp b8*
    result-size size-t
    index size-t
    delimiter-len size-t
    strings-len size-t)
  (if (not strings-len) (return 0))
  (set
    delimiter-len (strlen delimiter)
    result-size (+ 1 (* delimiter-len (- strings-len 1))))
  (for ((set index 0) (< index strings-len) (set index (+ 1 index)))
    (set result-size (+ result-size (strlen (array-get strings index)))))
  (set result (malloc result-size))
  (if (not result) (return 0))
  (set
    result-temp result
    (array-get result (- result-size 1)) 0)
  (memcpy result-temp (array-get strings 0) (strlen (array-get strings 0)))
  (for ((set index 1) (< index strings-len) (set index (+ 1 index)))
    (memcpy result-temp delimiter delimiter-len)
    (memcpy result-temp (array-get strings index) (strlen (array-get strings index))))
  (return result))

(define (db-index-name type-id fields fields-len) (b8* db-type-id-t db-field-count-t*)
  (declare result-len int index db-field-count-t fields-strings b8**)
  (set fields-strings (malloc fields-len))
  (if (not fields-strings) (return 0))
  (for ((set i 0) (< i fields-len) (set i (+ 1 i)))
    (set
      len (snprintf 0 0 "%lu" (array-get fields i))
      str (malloc (+ 1 len))
      )
    ; todo: free fields
    (if (not str) (return 0))
    (set (array-get str len) 0
)
    (snprintf str "%lu" (array-get fields i))
    (set (array-get fields-strings i) str)

    )
  (string-join fields "-")
  (set result-len (snprintf 0, 0, ""));
  (sprintf index-name)
  (sprintf "%zu-%s" type:id )
)

(define (db-index-create type fields fields-len)
  (status-t db-type-t* db-field-count-t* db-field-count-t)
  db-mdb-declare-val-null
  (db-txn-declare env txn)
  (db-mdb-cursor-declare system)
  (declare
    data b8*
    val-data MDB-val
    indices db-index-t*
    node-index db-index-t)
  (sc-comment "check if already exists")
  (set indices (db-index-get type fields fields-len))
  (if indices (status-set-both-goto db-status-group-db db-status-id-duplicate))
  (sc-comment "update schema")
  (set val-data.mv-size (+ db-size-type-id (* (sizeof db-field-count-t) fields-len)))
  (db-malloc data val-data.mv-size)
  (set
    val-data.mv-data data
    (convert-type data b8*) db-system-label-index
    data (+ 1 data)
    (convert-type data db-type-id-t*) type:id
    data (+ (sizeof db-type-id-t) data))
  (memcpy data fields fields-len)
  (db-txn-write-begin txn)
  (db-mdb-cursor-open txn system)
  (db-mdb-put system val-data val-null)
  (sc-comment "add btree")
  (db-index-name type:id fields)
  (db-mdb-status-require! (mdb-dbi-open txn.mdb-txn "i-" MDB-CREATE &node-index.dbi))
  (sc-comment "update cache")
  (db-realloc type:indices indices (+ 1 type:indices-count))
  (set index (array-get type:indices type:indices-count))
  (db-mdb-status-require! (mdb-dbi-open ""))
  (struct-set node-index
    dbi fields
    fields-len type)
  (set type:indices-count (+ 1 type:indices-count))
  (type
    (struct
      (dbi MDB-dbi)
      (fields db-field-count-t*)
      (fields-len db-field-count-t)
      (type db-type-id-t)))
  (label exit
    (return status)))

(define (db-index-rebuild index) (status-t db-index-t))

(pre-define (db-index-errors-graph-log message left right label)
  (db-error-log
    "(groups index graph) (description \"%s\") (left %lu) (right %lu) (label %lu)"
    message left right label))

(pre-define (db-index-errors-data-log message type id)
  (db-error-log "(groups index %s) (description %s) (id %lu)" type message id))

#;(define (db-index-recreate-graph) status-t
  status-init
  db-mdb-declare-val-graph-key
  db-mdb-declare-val-graph-data
  db-mdb-declare-val-id
  db-mdb-declare-val-id-2
  (db-define-graph-data graph-data)
  (db-define-graph-key graph-key)
  (db-mdb-cursor-declare-3 graph-lr graph-rl graph-ll)
  db-txn-introduce
  db-txn-write-begin
  (db-mdb-status-require! (mdb-drop db-txn dbi-graph-rl 0))
  (db-mdb-status-require! (mdb-drop db-txn dbi-graph-ll 0))
  db-txn-commit
  db-txn-write-begin
  (db-mdb-cursor-open-3 db-txn graph-lr graph-rl graph-ll)
  (declare
    id-left db-id-t
    id-right db-id-t
    id-label db-id-t)
  (db-mdb-cursor-each-key
    graph-lr
    val-graph-key
    val-graph-data
    (compound-statement
      (set
        id-left (db-mdb-val->id-at val-graph-key 0)
        id-label (db-mdb-val->id-at val-graph-key 1))
      (do-while db-mdb-status-success?
        (set id-right (db-mdb-val-graph-data->id val-graph-data))
        ;create graph-rl
        (set (array-get graph-key 0) id-right
          (array-get graph-key 1) id-label)
        (set val-graph-key.mv-data graph-key)
        (struct-set val-id.mv-data (address-of id-left))
        (db-mdb-status-require!
          (mdb-cursor-put graph-rl (address-of val-graph-key) (address-of val-id) 0))
        ;create graph-ll
        (struct-set val-id-2
          mv-data (address-of id-label))
        (db-mdb-status-require!
          (mdb-cursor-put graph-ll (address-of val-id-2) (address-of val-id) 0))
        (db-mdb-cursor-next-dup! graph-lr val-graph-key val-graph-data))))
  db-txn-commit
  (label exit
    (if db-txn db-txn-abort)
    (return status)))

#;(define (db-index-recreate-intern) status-t
  status-init
  db-mdb-declare-val-id
  db-mdb-declare-val-data
  (db-mdb-cursor-declare-2 nodes data-intern->id)
  db-txn-introduce
  db-txn-write-begin
  (mdb-drop db-txn dbi-data-intern->id 0)
  db-txn-commit
  db-txn-write-begin
  (db-mdb-cursor-open-2 db-txn nodes data-intern->id)
  (db-mdb-cursor-each-key
    nodes
    val-id
    val-data
    (compound-statement
      (if (and (struct-get val-data mv-size) (db-intern? (db-mdb-val->id val-id)))
        (db-mdb-status-require!
          (mdb-cursor-put data-intern->id (address-of val-data) (address-of val-id) 0)))))
  db-txn-commit
  (label exit
    (if db-txn db-txn-abort)
    (return status)))

(define (db-index-recreate-extern) status-t
  status-init
  db-mdb-declare-val-id
  db-mdb-declare-val-data
  (db-mdb-cursor-declare nodes)
  (db-mdb-cursor-declare data-intern->id)
  db-txn-introduce
  db-txn-write-begin
  (mdb-drop db-txn dbi-data-intern->id 0)
  db-txn-commit
  db-txn-write-begin
  (db-mdb-cursor-open db-txn nodes)
  (db-mdb-cursor-open db-txn data-intern->id)
  (db-mdb-cursor-each-key
    nodes
    val-id
    val-data
    (compound-statement
      (if (and (struct-get val-data mv-size) (db-intern? (db-mdb-val->id val-id)))
        (db-mdb-status-require!
          (mdb-cursor-put data-intern->id (address-of val-data) (address-of val-id) 0)))))
  db-txn-commit
  (label exit
    (if db-txn db-txn-abort)
    (return status)))

#;(define (db-index-errors-graph db-txn result) (status-t db-txn-t* db-index-errors-graph-t*)
  status-init
  (set (pointer-get result) db-index-errors-graph-null)
  db-mdb-declare-val-id
  db-mdb-declare-val-id-2
  db-mdb-declare-val-graph-key
  db-mdb-declare-val-graph-data
  (declare
    id-right db-id-t
    id-left db-id-t
    id-label db-id-t
    records-temp db-graph-records-t*
    record db-graph-record-t)
  (db-define-graph-key graph-key)
  (db-define-graph-data graph-data)
  (db-mdb-cursor-define-3 db-txn graph-lr graph-rl graph-ll)
  ;graph-lr
  (db-mdb-cursor-each-key
    graph-lr
    val-graph-key
    val-graph-data
    (compound-statement
      (set
        id-left (db-mdb-val->id-at val-graph-key 0)
        id-label (db-mdb-val->id-at val-graph-key 1))
      (do-while db-mdb-status-success?
        (set id-right (db-mdb-val-graph-data->id val-graph-data))
        ;-> graph-rl
        (array-set graph-key 0 id-right 1 id-label)
        (struct-set val-graph-key
          mv-data graph-key)
        (struct-set val-id
          mv-data (address-of id-left))
        (db-mdb-cursor-get! graph-rl val-graph-key val-id MDB-SET-KEY)
        (db-mdb-cursor-get! graph-rl val-graph-key val-id MDB-GET-BOTH)
        (if db-mdb-status-failure?
          (if (= MDB-NOTFOUND status.id)
            (begin
              (db-index-errors-graph-log
                "entry from graph-lr not in graph-rl" id-left id-right id-label)
              (set result:errors? #t)
              (struct-set record
                left id-left
                right id-right
                label id-label)
              (db-graph-records-add!
                (struct-pointer-get result missing-right-left) record records-temp))
            status-goto))
        ;-> graph-ll
        (struct-set val-id-2
          mv-data (address-of id-label))
        (db-mdb-cursor-get! graph-ll val-id-2 val-id MDB-GET-BOTH)
        (if (not db-mdb-status-success?)
          (if (= MDB-NOTFOUND status.id)
            (begin
              (db-index-errors-graph-log
                "entry from graph-lr not in graph-ll" id-left id-right id-label)
              (set result:errors? #t)
              (struct-set record
                left id-left
                right id-right
                label id-label)
              (db-graph-records-add!
                (struct-pointer-get result missing-label-left) record records-temp))
            status-goto))
        (db-mdb-cursor-next-dup! graph-lr val-graph-key val-graph-data))))
  ;graph-rl -> graph-lr
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
        (array-set graph-key 0 id-left 1 id-label)
        (struct-set val-graph-key
          mv-data graph-key)
        (db-mdb-cursor-get! graph-lr val-graph-key val-graph-data MDB-SET-KEY)
        (if db-mdb-status-success? (set status (db-mdb-graph-lr-seek-right graph-lr id-right)))
        (if (not db-mdb-status-success?)
          (if (= MDB-NOTFOUND status.id)
            (begin
              (db-index-errors-graph-log
                "entry from graph-rl not in graph-lr" id-left id-right id-label)
              (set result:errors? #t)
              (struct-set record
                left id-left
                right id-right
                label id-label)
              (db-graph-records-add!
                (struct-pointer-get result excess-right-left) record records-temp))
            status-goto))
        (db-mdb-cursor-next-dup! graph-rl val-graph-key val-id))))
  ;graph-ll -> graph-lr
  (db-mdb-cursor-each-key
    graph-ll
    val-id
    val-id-2
    (compound-statement
      (set id-label (db-mdb-val->id val-id))
      (do-while db-mdb-status-success?
        (set id-left (db-mdb-val->id val-id-2))
        (array-set graph-key 0 id-left 1 id-label)
        (struct-set val-graph-key
          mv-data graph-key)
        (db-mdb-cursor-get! graph-lr val-graph-key val-graph-data MDB-SET)
        (if (not db-mdb-status-success?)
          (if (= MDB-NOTFOUND status.id)
            (begin
              (db-index-errors-graph-log
                "entry from graph-ll not in graph-lr" id-left id-right id-label)
              (set result:errors? #t)
              (struct-set record
                left id-left
                right 0
                label id-label)
              (db-graph-records-add!
                (struct-pointer-get result excess-label-left) record records-temp))
            status-goto))
        (db-mdb-cursor-next-dup! graph-ll val-id val-id-2))))
  db-status-success-if-mdb-notfound
  (label exit
    (db-mdb-cursor-close-3 graph-lr graph-rl graph-ll)
    (return status)))

#;(define (db-index-errors-intern txn result) (status-t db-txn-t* db-index-errors-intern-t*)
  status-init
  (set (pointer-get result) db-index-errors-intern-null)
  db-mdb-declare-val-id
  db-mdb-declare-val-data
  db-mdb-declare-val-data-2
  (db-mdb-cursor-define-2 txn data-intern->id nodes)
  (declare ids-temp db-ids-t*)
  ;index->main-tree comparison
  (db-mdb-cursor-each-key
    data-intern->id
    val-data
    val-id
    (compound-statement
      (db-mdb-cursor-get! nodes val-id val-data-2 MDB-SET-KEY)
      (if db-mdb-status-success?
        (begin
          ;compare data
          (if (db-mdb-compare-data (address-of val-data) (address-of val-data-2))
            (begin
              (db-index-errors-data-log
                "intern" "data from data-intern->id differs in nodes" (db-mdb-val->id val-id))
              (set result:errors? #t)
              (db-ids-add!
                (struct-pointer-get result different-data-id) (db-mdb-val->id val-id) ids-temp))))
        (if (= MDB-NOTFOUND status.id)
          (begin
            (db-index-errors-data-log
              "intern" "data from data-intern->id not in nodes" (db-mdb-val->id val-id))
            (set result:errors? #t)
            (db-ids-add!
              (struct-pointer-get result excess-data-id) (db-mdb-val->id val-id) ids-temp))
          status-goto))))
  ;main-tree->index comparison
  db-mdb-declare-val-id-2
  (db-mdb-cursor-each-key
    nodes
    val-id
    val-data
    (compound-statement
      (if (db-intern? (db-mdb-val->id val-id))
        (begin
          (db-mdb-cursor-get! data-intern->id val-data val-id-2 MDB-SET-KEY)
          (if db-mdb-status-success?
            (if (not (db-id-equal? (db-mdb-val->id val-id) (db-mdb-val->id val-id-2)))
              (begin
                (db-index-errors-data-log
                  "intern" "data from nodes differs in data-intern->id" (db-mdb-val->id val-id))
                (set result:errors? #t)
                (db-ids-add!
                  (struct-pointer-get result different-id-data) (db-mdb-val->id val-id) ids-temp)))
            (if (= MDB-NOTFOUND status.id)
              (begin
                (db-index-errors-data-log
                  "intern" "data from nodes not in data-intern->id" (db-mdb-val->id val-id-2))
                (set result:errors? #t)
                (db-ids-add!
                  (struct-pointer-get result missing-id-data) (db-mdb-val->id val-id-2) ids-temp))
              status-goto))))))
  db-status-success-if-mdb-notfound
  (label exit
    (db-mdb-cursor-close-2 nodes data-intern->id)
    (return status)))

#;(define (db-index-errors-extern txn result) (status-t db-txn-t* db-index-errors-extern-t*)
  status-init
  (set (pointer-get result) db-index-errors-extern-null)
  db-mdb-declare-val-id
  db-mdb-declare-val-data
  db-mdb-declare-val-data-2
  (declare ids-temp db-ids-t*)
  (db-mdb-cursor-declare-2 nodes data-extern->extern)
  (db-mdb-cursor-open-2 txn nodes data-extern->extern)
  ;index->main-tree comparison
  (db-mdb-cursor-each-key
    data-extern->extern
    val-data
    val-id
    (compound-statement
      (if (struct-get val-data mv-size)
        (begin
          (db-mdb-cursor-get! nodes val-id val-data-2 MDB-SET-KEY)
          (if db-mdb-status-success?
            (begin
              ;different data
              (if (db-mdb-compare-data (address-of val-data) (address-of val-data-2))
                (begin
                  (db-index-errors-data-log
                    "extern" "data from data-extern->extern differs in nodes" (db-mdb-val->id val-id))
                  (set result:errors? #t)
                  (db-ids-add!
                    (struct-pointer-get result different-data-extern)
                    (db-mdb-val->id val-id) ids-temp))))
            (if (= MDB-NOTFOUND status.id)
              (begin
                (db-index-errors-data-log
                  "extern" "data from data-extern->extern not in nodes" (db-mdb-val->id val-id))
                (set result:errors? #t)
                (db-ids-add!
                  (struct-pointer-get result excess-data-extern) (db-mdb-val->id val-id) ids-temp))
              status-goto))))))
  ;main-tree->index comparison
  (db-mdb-cursor-each-key
    nodes
    val-id
    val-data
    (compound-statement
      (if (and (db-extern? (db-mdb-val->id val-id)) (struct-get val-data mv-size))
        (begin
          (db-mdb-cursor-get! data-extern->extern val-data val-id MDB-GET-BOTH)
          (if (= MDB-NOTFOUND status.id)
            (begin
              (db-index-errors-data-log
                "extern" "data from nodes not in data-extern->extern" (db-mdb-val->id val-id))
              (set result:errors? #t)
              (db-ids-add!
                (struct-pointer-get result missing-id-data) (db-mdb-val->id val-id) ids-temp))
            status-goto)))))
  db-status-success-if-mdb-notfound
  (label exit
    (db-mdb-cursor-close-2 nodes data-extern->extern)
    (return status)))