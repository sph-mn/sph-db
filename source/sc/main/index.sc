(pre-define (db-index-errors-graph-log message left right label)
  (db-error-log
    "(groups index graph) (description \"%s\") (left %lu) (right %lu) (label %lu)"
    message left right label))

(pre-define (db-index-errors-data-log message type id)
  (db-error-log "(groups index %s) (description %s) (id %lu)" type message id))

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

(define (db-index-name type-id fields fields-len result result-len)
  (status-t db-type-id-t db-field-count-t* b8* size-t*)
  "create a string name from type-id and field offsets"
  status-init
  (declare
    result-len int
    i db-field-count-t
    strings b8**
    strings-len int
    name b8*)
  (set
    name 0
    strings-len (+ 1 fields-len)
    strings (calloc strings-len (sizeof b8*)))
  (if (not strings)
    (begin
      (status-set-both db-status-group-db db-status-id-memory)
      (return status)))
  (sc-comment "type id")
  (set str (uint->string type-id))
  (if (not str)
    (begin
      (free strings)
      (status-set-both db-status-group-db db-status-id-memory)
      (return status)))
  (set *strings str)
  (sc-comment "field ids")
  (for ((set i 0) (< i fields-len) (set i (+ 1 i)))
    (set str (uint->string (array-get fields i)))
    (if (not str) (goto exit))
    (set (array-get strings (+ 1 i)) str))
  (set name (string-join strings strings-len "-" &result-len))
  (label exit
    (while i
      (free (array-get strings i))
      (set i (- i 1)))
    (free (array-get strings 0))
    (free strings)
    (set *result name)
    (return status)))

(define (db-index-system-key type fields fields-len data size)
  (status-t db-type-t* db-field-count-t* db-field-count-t b8* size-t*)
  status-init
  (declare data b8*)
  (set *size (+ db-size-type-id (* (sizeof db-field-count-t) fields-len)))
  (db-malloc data *size)
  (set
    *data db-system-label-index
    data (+ 1 data)
    (convert-type data db-type-id-t*) type:id
    data (+ (sizeof db-type-id-t) data))
  (memcpy data fields fields-len)
  (label exit
    (return status)))

(define (db-index-create env type fields fields-len)
  (status-t db-env-t* db-type-t* db-field-count-t* db-field-count-t)
  db-mdb-declare-val-null
  (db-txn-declare env txn)
  (db-mdb-cursor-declare system)
  (declare
    val-data MDB-val
    name b8*
    name-len size-t
    indices db-index-t*
    node-index db-index-t)
  (set
    name 0
    val-data.mv-data 0)
  (sc-comment "check if already exists")
  (set indices (db-index-get type fields fields-len))
  (if indices (status-set-both-goto db-status-group-db db-status-id-duplicate))
  (sc-comment "prepare data")
  (status-require!
    (db-index-system-key type:id fields fields-len &val-data.mv-data &val-data.mv-size))
  (status-require! (db-index-name type:id fields &name &name-len))
  (sc-comment "add to system btree")
  (db-txn-write-begin txn)
  (db-mdb-cursor-open txn system)
  (db-mdb-put system val-data val-null)
  (db-mdb-cursor-close system)
  (sc-comment "add data btree")
  (db-mdb-status-require! (mdb-dbi-open txn.mdb-txn name MDB-CREATE &node-index.dbi))
  (db-txn-commit txn)
  (sc-comment "update cache")
  (db-realloc type:indices indices (+ (sizeof db-index-t) type:indices-count))
  (set node-index (array-get type:indices type:indices-count))
  (struct-set node-index
    fields fields
    fields-len fields-len
    type type)
  (set type:indices-count (+ 1 type:indices-count))
  (label exit
    (db-mdb-cursor-close-if-active system)
    (db-txn-abort-if-active txn)
    (free name)
    (free val-data.mv-data)
    (return status)))

(define (db-index-delete env index) (status-t db-env-t* db-index-t*)
  "index must be a pointer into env:types:indices"
  status-init
  (db-txn-declare env txn)
  (db-mdb-cursor-declare system)
  (declare
    name b8*
    name-len size-t)
  (set name 0)
  (status-require!
    (db-index-system-key index:type:id fields fields-len &val-data.mv-data &val-data.mv-size))
  (sc-comment "remove from system btree")
  (db-txn-write-begin txn)
  (db-mdb-cursor-open txn system)
  (db-mdb-cursor-get-norequire system val-data val-null MDB-SET)
  (if db-mdb-status-success? (db-mdb-status-require! (mdb-cursor-del system 0))
    db-mdb-status-require-notfound)
  (db-mdb-cursor-close system)
  (sc-comment "remove data btree")
  (db-mdb-status-require! (mdb-drop txn.mdb-txn index:dbi 1))
  (db-txn-commit txn)
  (sc-comment "update cache")
  (free index:fields)
  (set
    index:dbi 0
    index:fields 0
    index:fields-len 0
    index:type 0)
  (label exit
    (free name)
    (db-mdb-cursor-close-if-active system)
    (db-txn-abort-if-active txn)
    (return status)))

(define (db-index-rebuild env index) (status-t db-env-t* db-index-t*)
  status-init
  db-mdb-declare-val-id
  (declare
    val-data MDB-val
    id db-id-t
    type-id db-type-id-t)
  (db-txn-declare env txn)
  (db-mdb-cursor-declare nodes)
  (set type-id index:type:id)
  (db-txn-write-begin txn)
  (db-mdb-cursor-open txn nodes)
  (set
    id (db-id-add-type 0 type-id)
    val-id.mv-data &id)
  (sc-comment "clear data btree")
  (db-mdb-status-require! (mdb-drop txn.mdb-txn index:dbi 0))
  (sc-comment "get every node of type")
  (db-mdb-cursor-get-norequire val-id val-data MDB-SET-KEY)
  (while (and db-mdb-status-success? (= type-id (db-id-type (db-pointer->id val-id.mv-data))))
    (db-mdb-put val-data-2 val-id)
    (db-mdb-cursor-next-nodup-norequire val-id val-data))
  (if (not db-mdb-status-success?) db-mdb-status-require-notfound)
  (db-mdb-cursor-close nodes)
  (db-txn-commit txn)
  (label exit
    (free name)
    (db-mdb-cursor-close-if-active nodes)
    (db-txn-abort-if-active txn)
    (return status)))

(define (db-index-errors txn index result) (status-t db-txn-t db-index-t db-index-errors-t*)
  status-init
  (set *result db-index-errors-null)
  db-mdb-declare-val-id
  db-mdb-declare-val-data
  db-mdb-declare-val-data-2
  (db-mdb-cursor-define-2 txn data-intern->id nodes)
  (declare ids-temp db-ids-t*)
  (sc-comment "index->main-tree comparison")
  (db-mdb-cursor-each-key
    data-intern->id
    val-data
    val-id
    (compound-statement
      (db-mdb-cursor-get! nodes val-id val-data-2 MDB-SET-KEY)
      (if db-mdb-status-success?
        (begin
          (sc-comment "compare data")
          (if (db-mdb-compare-data &val-data &val-data-2)
            (begin
              (db-index-errors-data-log
                "intern" "data from data-intern->id differs in nodes" (db-mdb-val->id val-id))
              (set result:errors? #t)
              (db-ids-add! result:different-data-id (db-mdb-val->id val-id) ids-temp))))
        (if (= MDB-NOTFOUND status.id)
          (begin
            (db-index-errors-data-log
              "intern" "data from data-intern->id not in nodes" (db-mdb-val->id val-id))
            (set result:errors? #t)
            (db-ids-add! result:excess-data-id (db-mdb-val->id val-id) ids-temp))
          status-goto))))
  (sc-comment "main-tree->index comparison")
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
                (db-ids-add! result:different-id-data (db-mdb-val->id val-id) ids-temp)))
            (if (= MDB-NOTFOUND status.id)
              (begin
                (db-index-errors-data-log
                  "intern" "data from nodes not in data-intern->id" (db-mdb-val->id val-id-2))
                (set result:errors? #t)
                (db-ids-add! result:missing-id-data (db-mdb-val->id val-id-2) ids-temp))
              status-goto))))))
  db-status-success-if-mdb-notfound
  (label exit
    (db-mdb-cursor-close nodes)
    (db-mdb-cursor-close node-index)
    (return status)))

(define (db-graph-index-rebuild env) (status-t db-env-t*)
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
        (set
          (array-get graph-key 0) id-right
          (array-get graph-key 1) id-label)
        (set val-graph-key.mv-data graph-key)
        (struct-set val-id.mv-data *id-left)
        (db-mdb-status-require! (mdb-cursor-put graph-rl &val-graph-key &val-id 0))
        ;create graph-ll
        (struct-set val-id-2
          mv-data &id-label)
        (db-mdb-status-require! (mdb-cursor-put graph-ll &val-id-2 &val-id 0))
        (db-mdb-cursor-next-dup! graph-lr val-graph-key val-graph-data))))
  db-txn-commit
  (label exit
    (if db-txn db-txn-abort)
    (return status)))

(define (db-graph-index-errors db-txn result) (status-t db-txn-t db-index-errors-graph-t*)
  status-init
  (set *result db-index-errors-graph-null)
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
  (sc-comment "graph-lr")
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
        (sc-comment "-> graph-rl")
        (array-set graph-key 0 id-right 1 id-label)
        (struct-set val-graph-key
          mv-data graph-key)
        (struct-set val-id
          mv-data &id-left)
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
              (db-graph-records-add! result:missing-right-left record records-temp))
            status-goto))
        (sc-comment "-> graph-ll")
        (struct-set val-id-2
          mv-data &id-label)
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
              (db-graph-records-add! result:missing-label-left record records-temp))
            status-goto))
        (db-mdb-cursor-next-dup! graph-lr val-graph-key val-graph-data))))
  (sc-comment "graph-rl -> graph-lr")
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
              (db-graph-records-add! result:excess-right-left record records-temp))
            status-goto))
        (db-mdb-cursor-next-dup! graph-rl val-graph-key val-id))))
  (sc-comment "graph-ll -> graph-lr")
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
              (db-graph-records-add! result:excess-label-left record records-temp))
            status-goto))
        (db-mdb-cursor-next-dup! graph-ll val-id val-id-2))))
  db-status-success-if-mdb-notfound
  (label exit
    (db-mdb-cursor-close-3 graph-lr graph-rl graph-ll)
    (return status)))