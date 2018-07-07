(pre-define (db-index-errors-data-log message type id)
  (db-error-log "(groups index %s) (description %s) (id %lu)" type message id))

(define (db-index-get type fields fields-len)
  (db-index-t* db-type-t* db-field-count-t* db-field-count-t)
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

(define (db-index-system-key type fields fields-len data size)
  (status-t db-type-t* db-field-count-t* db-field-count-t b8* size-t*)
  status-declare
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

(define (db-index-name type-id fields fields-len result result-len)
  (status-t db-type-id-t db-field-count-t* db-field-count-t b8** size-t*)
  "create a string name from type-id and field offsets"
  status-declare
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

(define (db-index-build env index) (status-t db-env-t* db-index-t*)
  "fill one index from existing data"
  status-declare
  db-mdb-declare-val-id
  (db-txn-declare env txn)
  (db-mdb-cursor-declare nodes)
  (db-mdb-cursor-declare index-cursor)
  (declare
    val-data MDB-val
    data b0*
    id db-id-t
    type db-type-t
    name b8*
    values db-node-values-t
    name-len size-t)
  (set
    type &index:type
    id (db-id-add-type 0 type.id)
    val-id.mv-data &id)
  (db-txn-write-begin txn)
  (db-mdb-status-require (mdb-cursor-open txn.mdb-txn index:dbi &index-cursor))
  (db-mdb-cursor-open txn nodes)
  (db-mdb-cursor-get-norequire val-id val-data MDB-SET-KEY)
  (sc-comment "for each node of type")
  (while (and db-mdb-status-is-success (= type.id (db-id-type (db-pointer->id val-id.mv-data))))
    (status-require (db-node-data->values &type val-data.mv-data val-data.mv-size &values))
    (status-require (db-index-key index values &data &val-data.mv-size))
    (set val-data.mv-data data)
    (db-mdb-cursor-put index-cursor val-data val-id)
    (free data)
    (db-free-node-values &values)
    (db-mdb-cursor-next-nodup-norequire val-id val-data))
  (if (not (or db-mdb-status-is-success db-mdb-status-is-notfound)) (goto exit))
  (db-txn-commit txn)
  (label exit
    (db-mdb-cursor-close-if-active index-cursor)
    (db-mdb-cursor-close-if-active nodes)
    (if data (free data))
    (free name)
    (db-free-node-values &values)
    (db-txn-abort-if-active txn)
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
  (status-require
    (db-index-system-key type:id fields fields-len &val-data.mv-data &val-data.mv-size))
  (status-require (db-index-name type:id fields &name &name-len))
  (sc-comment "add to system btree")
  (db-txn-write-begin txn)
  (db-mdb-cursor-open txn system)
  (db-mdb-put system val-data val-null)
  (db-mdb-cursor-close system)
  (sc-comment "add data btree")
  (db-mdb-status-require (mdb-dbi-open txn.mdb-txn name MDB-CREATE &node-index.dbi))
  (db-txn-commit txn)
  (sc-comment "update cache")
  (db-realloc type:indices indices (+ (sizeof db-index-t) type:indices-count))
  (set node-index (array-get type:indices type:indices-count))
  (struct-set node-index
    fields fields
    fields-len fields-len
    type type)
  (set type:indices-count (+ 1 type:indices-count))
  (status-require (db-index-build env node-index))
  (label exit
    (db-mdb-cursor-close-if-active system)
    (db-txn-abort-if-active txn)
    (free name)
    (free val-data.mv-data)
    (return status)))

(define (db-index-delete env index) (status-t db-env-t* db-index-t*)
  "index must be a pointer into env:types:indices"
  status-declare
  (db-txn-declare env txn)
  (db-mdb-cursor-declare system)
  (declare
    name b8*
    name-len size-t)
  (set name 0)
  (status-require
    (db-index-system-key index:type:id fields fields-len &val-data.mv-data &val-data.mv-size))
  (db-txn-write-begin txn)
  (sc-comment "remove data btree")
  (db-mdb-status-require (mdb-drop txn.mdb-txn index:dbi 1))
  (sc-comment "remove from system btree")
  (db-mdb-cursor-open txn system)
  (db-mdb-cursor-get-norequire system val-data val-null MDB-SET)
  (if db-mdb-status-is-success (db-mdb-status-require (mdb-cursor-del system 0))
    db-mdb-status-require-notfound)
  (db-mdb-cursor-close system)
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

(define (db-index-key index values result-data result-size)
  (status-t db-index-t db-node-values-t b0** size-t*)
  "calculate size and prepare data"
  status-declare
  (declare
    value-size size-t
    data b8*
    i db-field-count-t
    size size-t
    data-temp b0*)
  (for ((set i 0) (< i index.fields-len) (set i (+ 1 i)))
    (set size (+ size (struct-get (array-get values.data (array-get index.fields i)) size))))
  (if (< txn.env:maxkeysize size)
    (status-set-both-goto db-status-group-db db-status-id-index-keysize))
  (db-malloc data size)
  (set data-temp data)
  (for ((set i 0) (< i index.fields-len) (set i (+ 1 i)))
    (set value-size (struct-get (array-get values.data (array-get index.fields i)) size))
    (memcpy data-temp (struct-get (array-get values.data i) data) value-size)
    (set data-temp (+ value-size data-temp)))
  (set
    *result-data data
    *result-size size)
  (label exit
    (return status)))

(define (db-indices-entry-ensure txn values id) (status-t db-txn-t db-node-values-t db-id-t)
  "create entries in all indices of type for id and values.
  index: field-data ... -> id"
  status-declare
  db-mdb-declare-val-id
  (db-mdb-cursor-declare node-index-cursor)
  (declare
    data b0*
    val-data MDB-val
    size size-t
    i db-index-count-t
    node-index db-index-t
    node-indices db-index-t*
    node-indices-len db-index-count-t)
  (set
    val-id.mv-data &id
    data 0
    node-indices-len values.type:indices-len
    node-indices values.type:indices)
  (for ((set i 0) (< i node-indices-len) (set i (+ 1 i)))
    (set node-index (array-get node-indices i))
    (status-require (db-index-key node-index values &data &val-data.mv-size))
    (set val-data.mv-data data)
    (db-mdb-status-require (mdb-cursor-open txn.mdb-txn node-index.dbi &node-index-cursor))
    (db-mdb-cursor-put node-index-cursor val-data val-id)
    (db-mdb-cursor-close node-index-cursor))
  (label exit
    (db-mdb-cursor-close-if-active node-index-cursor)
    (if data (free data))
    (return status)))

(define (db-indices-entry-delete txn values id) (status-t db-txn-t db-node-values-t db-id-t)
  "delete all entries from all indices of type for id and values"
  status-declare
  db-mdb-declare-val-id
  (db-mdb-cursor-declare node-index-cursor)
  (declare
    data b8*
    val-data MDB-val
    i db-index-count-t
    node-index db-index-t
    node-indices db-index-t*
    node-indices-len db-index-count-t)
  (set
    val-id.mv-data &id
    data 0
    node-indices-len values.type:indices-len
    node-indices values.type:indices)
  (for ((set i 0) (< i node-indices-len) (set i (+ 1 i)))
    (set node-index (array-get node-indices i))
    (status-require (db-index-key node-index values &data &val-data.mv-size))
    (set val-data.mv-data data)
    ; delete
    (db-mdb-status-require (mdb-cursor-open txn.mdb-txn node-index.dbi &node-index-cursor))
    (db-mdb-cursor-put node-index-cursor val-data val-id)
    (db-mdb-cursor-get-norequire node-index-cursor val-data val-id MDB-GET-BOTH)
    (if db-status-is-success (db-mdb-cursor-del node-index-cursor))
    (db-mdb-cursor-close node-index-cursor))
  (label exit
    (db-mdb-cursor-close-if-active node-index-cursor)
    (if data (free data))
    (return status)))

(define (db-index-rebuild env index) (mdb-drop index:dbi 0)
  (status-require (db-index-name type-id index:fields index:fields-len &name &name-len))
  (db-mdb-status-require (mdb-dbi-open txn.mdb-txn name MDB-CREATE &index:dbi))
  (db-index-build env index))

(define (db-indices-build env index) (status-t db-env-t* db-index-t*)
  "clear data btree if exists or create it. then update index from existing nodes"
  status-declare
  db-mdb-declare-val-id
  (db-txn-declare env txn)
  (db-mdb-cursor-declare nodes)
  (declare
    val-data MDB-val
    id db-id-t
    type db-type-t
    name b8*
    values db-node-values-t
    name-len size-t)
  (set
    type *index:type
    id (db-id-add-type 0 type.id)
    val-id.mv-data &id)
  (db-txn-write-begin txn)
  (db-mdb-cursor-open txn nodes)
  (db-mdb-cursor-get-norequire val-id val-data MDB-SET-KEY)
  (while (and db-mdb-status-is-success (= type.id (db-id-type (db-pointer->id val-id.mv-data))))
    (status-require (db-node-data->values &type val-data.mv-data val-data.mv-size &values))
    (status-require (db-indices-ensure txn values))
    (db-free-node-values &values)
    (db-mdb-cursor-next-nodup-norequire val-id val-data))
  (if (not db-mdb-status-is-success) db-mdb-status-require-notfound)
  (db-txn-commit txn)
  (label exit
    (db-mdb-cursor-close-if-active nodes)
    (db-txn-abort-if-active txn)
    (db-free-node-values &values)
    (return status)))

(declare db-index-read-state-t
  (type
    (struct
      (current db-id-t)
      (cursor MDB-cursor*)
      (status status-t))))

(define (db-index-next state) (status-t db-index-read-state-t*)
  status-declare
  (status-require state:status)
  ; check if key already set
  ; get next target
  (db-mdb-cursor-get-norequire val-data val-id MDB-SET-KEY)
  (db-mdb-cursor-get-norequire val-data val-id MDB-SET-KEY)
  (set state:current (pointer->id val-id.mv-data)))

(define (db-index-select txn index values result) "prepare the read state"
  (status-t db-txn-t db-index-t* db-node-values-t db-index-read-state-t*)
  status-declare
  (db-txn-declare env txn)
  (db-mdb-cursor-declare nodes)
  (db-mdb-cursor-declare index-cursor)
  (declare
    data b0*
    val-data MDB-val)
  (status-require (db-index-key index values &data &val-data.mv-size))
  (set val-data.mv-data data)
  (db-mdb-status-require (mdb-cursor-open txn.mdb-txn index:dbi &index-cursor))
  (set
    result:cursor index-cursor
    result:status status
    result:current 0)
  (db-mdb-cursor-get-norequire index-cursor val-data val-id MDB-SET-KEY)
  (label exit
    (db-mdb-cursor-close-if-active nodes)
    (db-txn-abort-if-active txn)
    (return status)))

(define (db-node-get-internal cursor id) "get data for one node by id")

(define (db-node-get txn id) "get data for one node by id"
  status-declare
  (db-mdb-cursor-declare nodes))

(define (db-node-index-select txn index values result)
  (status-t db-txn-t db-index-t* db-node-values-t db-node-index-read-state-t*)
  status-declare
  (db-mdb-cursor-declare nodes)
  (declare index-state db-index-read-state-t)
  (status-require (db-index-select txn index values index-state))
  (do-while db-status-is-success
    (db-index-next index-state)
    (db-node-get-internal cursor id)))