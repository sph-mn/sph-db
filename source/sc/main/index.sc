(pre-define (db-index-errors-data-log message type id)
  (db-error-log "(groups index %s) (description %s) (id %lu)" type message id))

(declare
  (db-node-data->values type data result) (status-t db-type-t* db-node-data-t db-node-values-t*)
  (db-free-node-values values) (void db-node-values-t*))

(define (db-index-get type fields fields-len)
  (db-index-t* db-type-t* db-fields-len-t* db-fields-len-t)
  (declare
    indices-len db-indices-len-t
    index db-indices-len-t
    indices db-index-t*)
  (set
    indices type:indices
    indices-len type:indices-len)
  (for ((set index 0) (< index indices-len) (set index (+ 1 index)))
    (if
      (=
        0
        (memcmp
          (struct-get (array-get indices index) fields) fields (* (sizeof db-field-t) fields-len)))
      (return (+ index indices))))
  (return 0))

(define (db-index-system-key type-id fields fields-len result-data result-size)
  (status-t db-type-id-t db-fields-len-t* db-fields-len-t void** size-t*)
  status-declare
  (declare
    data ui8*
    size size-t)
  (set size (+ db-size-type-id (* (sizeof db-fields-len-t) fields-len)))
  (db-malloc data size)
  (set
    *data db-system-label-index
    data (+ 1 data)
    (pointer-get (convert-type data db-type-id-t*)) type-id
    data (+ (sizeof db-type-id-t) data))
  (memcpy data fields fields-len)
  (label exit
    (set
      *result-data data
      *result-size size)
    (return status)))

(define (db-index-name type-id fields fields-len result result-size)
  (status-t db-type-id-t db-fields-len-t* db-fields-len-t ui8** size-t*)
  "create a string name from type-id and field offsets"
  status-declare
  (declare
    i db-fields-len-t
    str ui8*
    strings ui8**
    strings-len int
    name ui8*)
  (set
    name 0
    strings-len (+ 1 fields-len)
    strings (calloc strings-len (sizeof ui8*)))
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
  (set name (string-join strings strings-len "-" result-size))
  (db-status-memory-error-if-null name)
  (label exit
    (while i
      (free (array-get strings i))
      (set i (- i 1)))
    (free (array-get strings 0))
    (free strings)
    (set *result name)
    (return status)))

(define (db-index-key env index values result-data result-size)
  (status-t db-env-t* db-index-t db-node-values-t void** size-t*)
  "calculate size and prepare data"
  status-declare
  (declare
    value-size size-t
    data ui8*
    i db-fields-len-t
    size size-t
    data-temp void*)
  (for ((set i 0) (< i index.fields-len) (set i (+ 1 i)))
    (set size (+ size (struct-get (array-get values.data (array-get index.fields i)) size))))
  (if (< env:maxkeysize size) (status-set-both-goto db-status-group-db db-status-id-index-keysize))
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

(define (db-index-build env index) (status-t db-env-t* db-index-t*)
  "fill one index from existing data"
  status-declare
  db-mdb-declare-val-id
  (db-txn-declare env txn)
  (db-mdb-cursor-declare nodes)
  (db-mdb-cursor-declare index-cursor)
  (declare
    val-data MDB-val
    data void*
    id db-id-t
    type db-type-t
    name ui8*
    node-data db-node-data-t
    values db-node-values-t)
  (set
    type *index:type
    id (db-id-add-type 0 type.id)
    val-id.mv-data &id)
  (db-txn-write-begin txn)
  (db-mdb-status-require (mdb-cursor-open txn.mdb-txn index:dbi &index-cursor))
  (db-mdb-status-require (db-mdb-env-cursor-open txn nodes))
  (db-mdb-status-require (mdb-cursor-get nodes &val-id &val-data MDB-SET-KEY))
  (sc-comment "for each node of type")
  (while (and db-mdb-status-is-success (= type.id (db-id-type (db-pointer->id val-id.mv-data))))
    (set
      node-data.data val-data.mv-data
      node-data.size val-data.mv-size)
    (status-require (db-node-data->values &type node-data &values))
    (status-require (db-index-key env *index values &data &val-data.mv-size))
    (set val-data.mv-data data)
    (db-mdb-status-require (mdb-cursor-put index-cursor &val-data &val-id 0))
    (free data)
    (db-free-node-values &values)
    (db-mdb-status-require (mdb-cursor-get nodes &val-id &val-data MDB-NEXT-NODUP)))
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
  (status-t db-env-t* db-type-t* db-fields-len-t* db-fields-len-t)
  status-declare
  db-mdb-declare-val-null
  (db-txn-declare env txn)
  (db-mdb-cursor-declare system)
  (declare
    val-data MDB-val
    name ui8*
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
  (status-require (db-index-name type:id fields fields-len &name &name-len))
  (sc-comment "add to system btree")
  (db-txn-write-begin txn)
  (db-mdb-status-require (db-mdb-env-cursor-open txn system))
  (db-mdb-status-require (mdb-cursor-put system &val-data &val-null 0))
  (db-mdb-cursor-close system)
  (sc-comment "add data btree")
  (db-mdb-status-require (mdb-dbi-open txn.mdb-txn name MDB-CREATE &node-index.dbi))
  (db-txn-commit txn)
  (sc-comment "update cache")
  (db-realloc type:indices indices (+ (sizeof db-index-t) type:indices-len))
  (set node-index (array-get type:indices type:indices-len))
  (struct-set node-index
    fields fields
    fields-len fields-len
    type type)
  (set type:indices-len (+ 1 type:indices-len))
  (status-require (db-index-build env &node-index))
  (label exit
    (db-mdb-cursor-close-if-active system)
    (db-txn-abort-if-active txn)
    (free name)
    (free val-data.mv-data)
    (return status)))

(define (db-index-delete env index) (status-t db-env-t* db-index-t*)
  "index must be a pointer into env:types:indices"
  status-declare
  db-mdb-declare-val-null
  (db-txn-declare env txn)
  (db-mdb-cursor-declare system)
  (declare val-data MDB-val)
  (status-require
    (db-index-system-key
      index:type:id index:fields index:fields-len &val-data.mv-data &val-data.mv-size))
  (db-txn-write-begin txn)
  (sc-comment "remove data btree")
  (db-mdb-status-require (mdb-drop txn.mdb-txn index:dbi 1))
  (sc-comment "remove from system btree")
  (db-mdb-status-require (db-mdb-env-cursor-open txn system))
  (db-mdb-status-require (mdb-cursor-get system &val-data &val-null MDB-SET))
  (if db-mdb-status-is-success (db-mdb-status-require (mdb-cursor-del system 0))
    db-mdb-status-expect-notfound)
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
    (db-mdb-cursor-close-if-active system)
    (db-txn-abort-if-active txn)
    (return status)))

(define (db-index-rebuild env index) (status-t db-env-t* db-index-t*)
  "clear index and fill with relevant data from existing nodes"
  status-declare
  (db-txn-declare env txn)
  (declare
    name ui8*
    name-len size-t)
  (set name 0)
  (status-require (db-index-name index:type:id index:fields index:fields-len &name &name-len))
  (db-txn-write-begin txn)
  (db-mdb-status-require (mdb-drop txn.mdb-txn index:dbi 0))
  (db-mdb-status-require (mdb-dbi-open txn.mdb-txn name MDB-CREATE &index:dbi))
  (db-txn-commit txn)
  (label exit
    (free name)
    (return (db-index-build env index))))

(define (db-indices-entry-ensure txn values id) (status-t db-txn-t db-node-values-t db-id-t)
  "create entries in all indices of type for id and values.
  index: field-data ... -> id"
  status-declare
  db-mdb-declare-val-id
  (db-mdb-cursor-declare node-index-cursor)
  (declare
    data void*
    val-data MDB-val
    i db-indices-len-t
    node-index db-index-t
    node-indices db-index-t*
    node-indices-len db-indices-len-t)
  (set
    val-id.mv-data &id
    data 0
    node-indices-len values.type:indices-len
    node-indices values.type:indices)
  (for ((set i 0) (< i node-indices-len) (set i (+ 1 i)))
    (set node-index (array-get node-indices i))
    (status-require (db-index-key txn.env node-index values &data &val-data.mv-size))
    (set val-data.mv-data data)
    (db-mdb-status-require (mdb-cursor-open txn.mdb-txn node-index.dbi &node-index-cursor))
    (db-mdb-status-require (mdb-cursor-put node-index-cursor &val-data &val-id 0))
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
    data ui8*
    val-data MDB-val
    i db-indices-len-t
    node-index db-index-t
    node-indices db-index-t*
    node-indices-len db-indices-len-t)
  (set
    val-id.mv-data &id
    data 0
    node-indices-len values.type:indices-len
    node-indices values.type:indices)
  (for ((set i 0) (< i node-indices-len) (set i (+ 1 i)))
    (set node-index (array-get node-indices i))
    (status-require
      (db-index-key txn.env node-index values (convert-type &data void**) &val-data.mv-size))
    (set val-data.mv-data data)
    ; delete
    (db-mdb-status-require (mdb-cursor-open txn.mdb-txn node-index.dbi &node-index-cursor))
    (db-mdb-status-require (mdb-cursor-put node-index-cursor &val-data &val-id 0))
    (db-mdb-status-require (mdb-cursor-get node-index-cursor &val-data &val-id MDB-GET-BOTH))
    (if status-is-success (db-mdb-status-require (mdb-cursor-del node-index-cursor 0)))
    (db-mdb-cursor-close node-index-cursor))
  (label exit
    (db-mdb-cursor-close-if-active node-index-cursor)
    (if data (free data))
    (return status)))

(define (db-indices-build env index) (status-t db-env-t* db-index-t*)
  "fill index with relevant data from existing nodes"
  status-declare
  db-mdb-declare-val-id
  (db-txn-declare env txn)
  (db-mdb-cursor-declare nodes)
  (declare
    node-data db-node-data-t
    val-data MDB-val
    id db-id-t
    type db-type-t
    values db-node-values-t)
  (set
    type *index:type
    id (db-id-add-type 0 type.id)
    val-id.mv-data &id)
  (db-txn-write-begin txn)
  (db-mdb-status-require (db-mdb-env-cursor-open txn nodes))
  (set status.id (mdb-cursor-get nodes &val-id &val-data MDB-SET-KEY))
  (while (and db-mdb-status-is-success (= type.id (db-id-type (db-pointer->id val-id.mv-data))))
    (set
      node-data.data val-data.mv-data
      node-data.size val-data.mv-size)
    (status-require (db-node-data->values &type node-data &values))
    (status-require (db-indices-entry-ensure txn values (db-pointer->id val-id.mv-data)))
    (db-free-node-values &values)
    (set status.id (mdb-cursor-get nodes &val-id &val-data MDB-NEXT-NODUP)))
  (if (not db-mdb-status-is-success) db-mdb-status-expect-notfound)
  (db-txn-commit txn)
  (label exit
    (db-mdb-cursor-close-if-active nodes)
    (db-txn-abort-if-active txn)
    (db-free-node-values &values)
    (return status)))

(define (db-index-next state) (status-t db-index-selection-t*)
  "assumes that state is positioned at a matching key"
  status-declare
  db-mdb-declare-val-null
  db-mdb-declare-val-id
  (db-mdb-status-require (mdb-cursor-get state:cursor &val-null &val-id MDB-NEXT-DUP))
  (set state:current (db-pointer->id val-id.mv-data))
  (label exit
    db-mdb-status-no-more-data-if-notfound
    (return status)))

(define (db-index-selection-destroy state) (void db-index-selection-t*)
  (if state:cursor (mdb-cursor-close state:cursor)))

(define (db-index-select txn index values result)
  (status-t db-txn-t db-index-t* db-node-values-t db-index-selection-t*)
  "prepare the read state and get the first matching element or set status to no-more-data"
  status-declare
  db-mdb-declare-val-id
  (db-mdb-cursor-declare cursor)
  (declare
    data void*
    val-data MDB-val)
  (set data 0)
  (status-require (db-index-key txn.env *index values &data &val-data.mv-size))
  (set val-data.mv-data data)
  (db-mdb-status-require (mdb-cursor-open txn.mdb-txn index:dbi &cursor))
  (db-mdb-status-require (mdb-cursor-get cursor &val-data &val-id MDB-SET-KEY))
  (set
    result:current (db-pointer->id val-id.mv-data)
    result:cursor cursor)
  (label exit
    (free data)
    (if status-is-failure
      (begin
        (db-mdb-cursor-close-if-active cursor)
        db-mdb-status-no-more-data-if-notfound))
    (return status)))