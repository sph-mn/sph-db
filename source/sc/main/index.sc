(declare
  (db-node-data->values type data result) (status-t db-type-t* db-node-data-t db-node-values-t*)
  (db-free-node-values values) (void db-node-values-t*))

(define (db-index-system-key type-id fields fields-len result-data result-size)
  (status-t db-type-id-t db-fields-len-t* db-fields-len-t void** size-t*)
  "create a key for an index to be used in the system btree.
   key-format: system-label-type type-id indexed-field-offset ..."
  status-declare
  (declare
    data uint8-t*
    data-temp uint8-t*
    size size-t)
  (sc-comment "system-label + type + fields")
  (set size (+ 1 (sizeof db-type-id-t) (* (sizeof db-fields-len-t) fields-len)))
  (db-malloc data size)
  (set
    *data db-system-label-index
    data-temp (+ 1 data)
    (pointer-get (convert-type data-temp db-type-id-t*)) type-id
    data-temp (+ (sizeof db-type-id-t) data-temp))
  (memcpy data-temp fields (* (sizeof db-fields-len-t) fields-len))
  (set
    *result-data data
    *result-size size)
  (label exit
    (return status)))

(define (db-index-name type-id fields fields-len result result-len)
  (status-t db-type-id-t db-fields-len-t* db-fields-len-t uint8-t** size-t*)
  "create a string name from type-id and field offsets.
  i-{type-id}-{field-offset}-{field-offset}..."
  status-declare
  (declare
    i db-fields-len-t
    str uint8-t*
    name-len size-t
    str-len size-t
    strings uint8-t**
    strings-len int
    name uint8-t*)
  (define prefix uint8-t* "i")
  (set
    strings 0
    strings-len (+ 2 fields-len))
  (db-calloc strings strings-len (sizeof uint8-t*))
  (sc-comment "type id")
  (set str (uint->string type-id &str-len))
  (db-status-memory-error-if-null str)
  (set
    (array-get strings 0) prefix
    (array-get strings 1) str)
  (sc-comment "field ids")
  (for ((set i 0) (< i fields-len) (set i (+ 1 i)))
    (set str (uint->string (array-get fields i) &str-len))
    (db-status-memory-error-if-null str)
    (set (array-get strings (+ 2 i)) str))
  (set name (string-join strings strings-len "-" &name-len))
  (db-status-memory-error-if-null name)
  (set
    *result name
    *result-len name-len)
  (label exit
    (if strings
      (begin
        (sc-comment "dont free string[0] because it is the stack allocated prefix")
        (for ((set i 1) (< i strings-len) (set i (+ 1 i)))
          (free (array-get strings i)))
        (free strings)))
    (return status)))

(define (db-index-key env index values result-data result-size)
  (status-t db-env-t* db-index-t db-node-values-t void** size-t*)
  "create a key to be used in an index btree.
  key-format: field-value ..."
  status-declare
  (declare
    value-size size-t
    data void*
    i db-fields-len-t
    size size-t
    data-temp uint8-t*)
  (set size 0)
  (for ((set i 0) (< i index.fields-len) (set i (+ 1 i)))
    (set size (+ size (struct-get (array-get values.data (array-get index.fields i)) size))))
  (if (< env:maxkeysize size) (status-set-both-goto db-status-group-db db-status-id-index-keysize))
  (db-malloc data size)
  (set data-temp data)
  (for ((set i 0) (< i index.fields-len) (set i (+ 1 i)))
    (set value-size (struct-get (array-get values.data (array-get index.fields i)) size))
    (memcpy
      data-temp (struct-get (array-get values.data (array-get index.fields i)) data) value-size)
    (set data-temp (+ value-size data-temp)))
  (set
    *result-data data
    *result-size size)
  (label exit
    (return status)))

(define (db-indices-entry-ensure txn values id) (status-t db-txn-t db-node-values-t db-id-t)
  "create entries in all indices of type for id and values.
  index entry: field-value ... -> id"
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
    (if (not node-index.fields-len) continue)
    (status-require (db-index-key txn.env node-index values &data &val-data.mv-size))
    (set val-data.mv-data data)
    (db-mdb-status-require (mdb-cursor-open txn.mdb-txn node-index.dbi &node-index-cursor))
    (db-mdb-status-require (mdb-cursor-put node-index-cursor &val-data &val-id 0))
    (db-mdb-cursor-close node-index-cursor))
  (label exit
    (db-mdb-cursor-close-if-active node-index-cursor)
    (free data)
    (return status)))

(define (db-indices-entry-delete txn values id) (status-t db-txn-t db-node-values-t db-id-t)
  "delete all entries from all indices of type for id and values"
  status-declare
  db-mdb-declare-val-id
  (db-mdb-cursor-declare node-index-cursor)
  (declare
    data uint8-t*
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
    (if (not node-index.fields-len) continue)
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
    (free data)
    (return status)))

(define (db-index-build env index) (status-t db-env-t* db-index-t)
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
    node-data db-node-data-t
    values db-node-values-t)
  (set
    values.data 0
    data 0
    type *index.type
    id (db-id-add-type 0 type.id)
    val-id.mv-data &id)
  (status-require (db-txn-write-begin &txn))
  (db-mdb-status-require (mdb-cursor-open txn.mdb-txn index.dbi &index-cursor))
  (db-mdb-status-require (db-mdb-env-cursor-open txn nodes))
  (db-mdb-status-require (mdb-cursor-get nodes &val-id &val-data MDB-SET-RANGE))
  (sc-comment "for each node of type")
  (while (and db-mdb-status-is-success (= type.id (db-id-type (db-pointer->id val-id.mv-data))))
    (set
      node-data.data val-data.mv-data
      node-data.size val-data.mv-size)
    (status-require (db-node-data->values &type node-data &values))
    (status-require (db-index-key env index values &data &val-data.mv-size))
    (db-free-node-values &values)
    (set val-data.mv-data data)
    (db-mdb-status-require (mdb-cursor-put index-cursor &val-data &val-id 0))
    (set status.id (mdb-cursor-get nodes &val-id &val-data MDB-NEXT-NODUP)))
  db-mdb-status-expect-read
  (status-require (db-txn-commit &txn))
  (label exit
    (db-mdb-cursor-close-if-active index-cursor)
    (db-mdb-cursor-close-if-active nodes)
    (db-txn-abort-if-active txn)
    (db-free-node-values &values)
    (free data)
    db-mdb-status-success-if-notfound
    (return status)))

(define (db-index-get type fields fields-len)
  (db-index-t* db-type-t* db-fields-len-t* db-fields-len-t)
  "if found returns a pointer to an index struct in the cache array, zero otherwise"
  (declare
    indices-len db-indices-len-t
    index db-indices-len-t
    indices db-index-t*)
  (set
    indices type:indices
    indices-len type:indices-len)
  (for ((set index 0) (< index indices-len) (set index (+ 1 index)))
    (if
      (and
        (struct-get (array-get indices index) fields-len)
        (=
          0
          (memcmp
            (struct-get (array-get indices index) fields)
            fields (* fields-len (sizeof db-fields-len-t)))))
      (return (+ index indices))))
  (return 0))

(define (db-type-indices-add type index) (status-t db-type-t* db-index-t)
  "eventually resize type:indices and add index to type:indices.
  indices is extended and elements are set to zero on deletion.
  indices is currently never downsized, but a re-open of the db-env
  reallocates it in appropriate size (and invalidates all db-index-t pointers)"
  status-declare
  (declare
    indices-temp db-index-t*
    indices-len db-indices-len-t
    indices db-index-t*
    i db-indices-len-t)
  (set
    indices type:indices
    indices-len type:indices-len)
  (sc-comment "search unset index")
  (for ((set i 0) (< i indices-len) (set i (+ 1 i)))
    (if (not (struct-get (array-get indices i) fields-len)) break))
  (if (< i indices-len)
    (begin
      (set (array-get indices i) index)
      (goto exit)))
  (sc-comment "reallocate")
  (set indices-len (+ 1 indices-len))
  (db-realloc indices indices-temp (* indices-len (sizeof db-index-t)))
  (set
    (array-get indices (- indices-len 1)) index
    type:indices indices
    type:indices-len indices-len)
  (label exit
    (return status)))

(define (db-index-create env type fields fields-len)
  (status-t db-env-t* db-type-t* db-fields-len-t* db-fields-len-t)
  status-declare
  db-mdb-declare-val-null
  (db-txn-declare env txn)
  (db-mdb-cursor-declare system)
  (declare
    val-data MDB-val
    fields-copy db-fields-len-t*
    data void*
    size size-t
    name uint8-t*
    name-len size-t
    indices-temp db-index-t*
    node-index db-index-t)
  (if (not fields-len)
    (begin
      (set status.id db-status-id-invalid-argument)
      (return status)))
  (set
    fields-copy 0
    name 0
    data 0
    size 0)
  (sc-comment "check if already exists")
  (set indices-temp (db-index-get type fields fields-len))
  (if indices-temp (status-set-both-goto db-status-group-db db-status-id-duplicate))
  (sc-comment "prepare data")
  (status-require (db-index-system-key type:id fields fields-len &data &size))
  (status-require (db-index-name type:id fields fields-len &name &name-len))
  (sc-comment "add to system btree")
  (set
    val-data.mv-data data
    val-data.mv-size size)
  (status-require (db-txn-write-begin &txn))
  (db-mdb-status-require (db-mdb-env-cursor-open txn system))
  (db-mdb-status-require (mdb-cursor-put system &val-data &val-null 0))
  (db-mdb-cursor-close system)
  (sc-comment "add data btree")
  (db-mdb-status-require (mdb-dbi-open txn.mdb-txn name MDB-CREATE &node-index.dbi))
  (sc-comment "update cache. fields might be stack allocated")
  (db-malloc fields-copy (* fields-len (sizeof db-fields-len-t)))
  (memcpy fields-copy fields (* fields-len (sizeof db-fields-len-t)))
  (struct-set node-index
    fields fields-copy
    fields-len fields-len
    type type)
  (status-require (db-type-indices-add type node-index))
  (status-require (db-txn-commit &txn))
  (status-require (db-index-build env node-index))
  (label exit
    (db-mdb-cursor-close-if-active system)
    (db-txn-abort-if-active txn)
    (if status-is-failure (free fields-copy))
    (free name)
    (free data)
    (return status)))

(define (db-index-delete env index) (status-t db-env-t* db-index-t*)
  "index must be a pointer into env:types:indices.
  the cache entry struct has its fields set to zero"
  status-declare
  db-mdb-declare-val-null
  (db-txn-declare env txn)
  (db-mdb-cursor-declare system)
  (declare
    key-data void*
    key-size size-t
    val-data MDB-val)
  (status-require
    (db-index-system-key index:type:id index:fields index:fields-len &key-data &key-size))
  (set
    val-data.mv-data key-data
    val-data.mv-size key-size)
  (status-require (db-txn-write-begin &txn))
  (sc-comment "remove data btree. closes the handle")
  (db-mdb-status-require (mdb-drop txn.mdb-txn index:dbi 1))
  (sc-comment "remove from system btree")
  (db-mdb-status-require (db-mdb-env-cursor-open txn system))
  (db-mdb-status-require (mdb-cursor-get system &val-data &val-null MDB-SET))
  (if db-mdb-status-is-success (db-mdb-status-require (mdb-cursor-del system 0))
    db-mdb-status-expect-notfound)
  (db-mdb-cursor-close system)
  (status-require (db-txn-commit &txn))
  (sc-comment "update cache")
  (free-and-set-null index:fields)
  (set
    index:fields-len 0
    index:type 0)
  (label exit
    (db-mdb-cursor-close-if-active system)
    (db-txn-abort-if-active txn)
    (return status)))

(define (db-index-rebuild env index) (status-t db-env-t* db-index-t*)
  "clear index and fill with data from existing nodes"
  status-declare
  (db-txn-declare env txn)
  (declare
    name uint8-t*
    name-len size-t)
  (set name 0)
  (status-require (db-index-name index:type:id index:fields index:fields-len &name &name-len))
  (status-require (db-txn-write-begin &txn))
  (db-mdb-status-require (mdb-drop txn.mdb-txn index:dbi 0))
  (db-mdb-status-require (mdb-dbi-open txn.mdb-txn name MDB-CREATE &index:dbi))
  (status-require (db-txn-commit &txn))
  (label exit
    (free name)
    (if status-is-success (return (db-index-build env *index))
      (db-txn-abort-if-active txn))))

(define (db-index-next selection) (status-t db-index-selection-t)
  "position at the next index value.
  if no value is found, status is db-notfound.
  before call, selection must be positioned at a matching key"
  status-declare
  db-mdb-declare-val-null
  db-mdb-declare-val-id
  (db-mdb-status-require (mdb-cursor-get selection.cursor &val-null &val-id MDB-NEXT-DUP))
  (set selection.current (db-pointer->id val-id.mv-data))
  (label exit
    db-mdb-status-notfound-if-notfound
    (return status)))

(define (db-index-selection-finish selection) (void db-index-selection-t*)
  (db-mdb-cursor-close-if-active selection:cursor))

(define (db-index-select txn index values result)
  (status-t db-txn-t db-index-t db-node-values-t db-index-selection-t*)
  "open the cursor and set to the index key matching values.
  selection is set to the first match.
  if no match found status is db-notfound"
  status-declare
  db-mdb-declare-val-id
  (db-mdb-cursor-declare cursor)
  (declare
    data void*
    val-data MDB-val)
  (set data 0)
  (status-require (db-index-key txn.env index values &data &val-data.mv-size))
  (set val-data.mv-data data)
  (db-mdb-status-require (mdb-cursor-open txn.mdb-txn index.dbi &cursor))
  (db-mdb-status-require (mdb-cursor-get cursor &val-data &val-id MDB-SET-KEY))
  (set
    result:current (db-pointer->id val-id.mv-data)
    result:cursor cursor)
  (label exit
    (free data)
    (if status-is-failure
      (begin
        (db-mdb-cursor-close-if-active cursor)
        db-mdb-status-notfound-if-notfound))
    (return status)))