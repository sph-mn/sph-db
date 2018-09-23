(declare
  (db-record-data->values type data result) (status-t db-type-t* db-record-t db-record-values-t*)
  (db-free-record-values values) (void db-record-values-t*))

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
  (status-require (db-helper-malloc size &data))
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
  (status-require (db-helper-calloc (* strings-len (sizeof uint8-t*)) &strings))
  (sc-comment "type id")
  (set str (uint->string type-id &str-len))
  (if (not str) (status-set-both-goto db-status-group-db db-status-id-memory))
  (set
    (array-get strings 0) prefix
    (array-get strings 1) str)
  (sc-comment "field ids")
  (for ((set i 0) (< i fields-len) (set i (+ 1 i)))
    (set str (uint->string (array-get fields i) &str-len))
    (if (not str) (status-set-both-goto db-status-group-db db-status-id-memory))
    (set (array-get strings (+ 2 i)) str))
  (set name (string-join strings strings-len "-" &name-len))
  (if (not name) (status-set-both-goto db-status-group-db db-status-id-memory))
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
  (status-t db-env-t* db-index-t db-record-values-t void** size-t*)
  "create a key to be used in an index btree.
  similar to db-record-values->data but only for indexed fields.
  values must be written with variable size prefixes and more like for row data to avoid ambiguous keys"
  status-declare
  (declare
    data void*
    data-size uint64-t
    data-temp uint8-t*
    field-data void*
    field-index db-fields-len-t
    field-size db-field-type-size-t
    fields db-field-t*
    fields-fixed-count db-fields-len-t
    i db-fields-len-t
    size size-t)
  (sc-comment "no fields set, no data stored")
  (if (not values.extent)
    (begin
      (set
        *result-data 0
        *result-size 0)
      (return status)))
  (set
    size 0
    fields-fixed-count values.type:fields-fixed-count
    fields values.type:fields)
  (sc-comment "calculate data size")
  (for ((set i 0) (< i index.fields-len) (set i (+ 1 i)))
    (set
      field-index (array-get index.fields i)
      size
      (+
        (struct-get (array-get fields field-index) size)
        (if* (< field-index fields-fixed-count) 0
          (struct-get (array-get values.data field-index) size))
        size)))
  (if (< env:maxkeysize size) (status-set-both-goto db-status-group-db db-status-id-index-keysize))
  (sc-comment "allocate and prepare data")
  (status-require (db-helper-calloc size &data))
  (set data-temp data)
  (for ((set i 0) (< i index.fields-len) (set i (+ 1 i)))
    (set
      field-index (array-get index.fields i)
      data-size (struct-get (array-get values.data field-index) size)
      field-size (struct-get (array-get fields field-index) size)
      field-data (struct-get (array-get values.data field-index) data))
    (if (< i fields-fixed-count)
      (begin
        (if data-size (memcpy data-temp field-data data-size))
        (set data-temp (+ field-size data-temp)))
      (begin
        (sc-comment "data size prefix and optionally data")
        (memcpy data-temp &data-size field-size)
        (set data-temp (+ field-size data-temp))
        (if data-size (memcpy data-temp field-data data-size))
        (set data-temp (+ data-size data-temp)))))
  (set
    *result-data data
    *result-size size)
  (label exit
    (return status)))

(define (db-indices-entry-ensure txn values id) (status-t db-txn-t db-record-values-t db-id-t)
  "create entries in all indices of type for id and values.
  assumes that values has at least one entry set (values.extent unequal zero).
  index entry: field-value ... -> id"
  status-declare
  db-mdb-declare-val-id
  (db-mdb-cursor-declare record-index-cursor)
  (declare
    data void*
    val-data MDB-val
    i db-indices-len-t
    record-index db-index-t
    record-indices db-index-t*
    record-indices-len db-indices-len-t)
  (set
    val-id.mv-data &id
    data 0
    record-indices-len values.type:indices-len
    record-indices values.type:indices)
  (for ((set i 0) (< i record-indices-len) (set i (+ 1 i)))
    (set record-index (array-get record-indices i))
    (if (not record-index.fields-len) continue)
    (status-require (db-index-key txn.env record-index values &data &val-data.mv-size))
    (set val-data.mv-data data)
    (db-mdb-status-require (mdb-cursor-open txn.mdb-txn record-index.dbi &record-index-cursor))
    (db-mdb-status-require (mdb-cursor-put record-index-cursor &val-data &val-id 0))
    (db-mdb-cursor-close record-index-cursor))
  (label exit
    (db-mdb-cursor-close-if-active record-index-cursor)
    (free data)
    (return status)))

(define (db-indices-entry-delete txn values id) (status-t db-txn-t db-record-values-t db-id-t)
  "delete all entries from all indices of type for id and values"
  status-declare
  db-mdb-declare-val-id
  (db-mdb-cursor-declare record-index-cursor)
  (declare
    data uint8-t*
    val-data MDB-val
    i db-indices-len-t
    record-index db-index-t
    record-indices db-index-t*
    record-indices-len db-indices-len-t)
  (set
    val-id.mv-data &id
    data 0
    record-indices-len values.type:indices-len
    record-indices values.type:indices)
  (for ((set i 0) (< i record-indices-len) (set i (+ 1 i)))
    (set record-index (array-get record-indices i))
    (if (not record-index.fields-len) continue)
    (status-require
      (db-index-key txn.env record-index values (convert-type &data void**) &val-data.mv-size))
    (set val-data.mv-data data)
    ; delete
    (db-mdb-status-require (mdb-cursor-open txn.mdb-txn record-index.dbi &record-index-cursor))
    (db-mdb-status-require (mdb-cursor-put record-index-cursor &val-data &val-id 0))
    (db-mdb-status-require (mdb-cursor-get record-index-cursor &val-data &val-id MDB-GET-BOTH))
    (if status-is-success (db-mdb-status-require (mdb-cursor-del record-index-cursor 0)))
    (db-mdb-cursor-close record-index-cursor))
  (label exit
    (db-mdb-cursor-close-if-active record-index-cursor)
    (free data)
    (return status)))

(define (db-index-build env index) (status-t db-env-t* db-index-t)
  "fill one index from existing data"
  status-declare
  db-mdb-declare-val-id
  (db-txn-declare env txn)
  (db-mdb-cursor-declare records)
  (db-mdb-cursor-declare index-cursor)
  (declare
    val-data MDB-val
    data void*
    id db-id-t
    type db-type-t
    record db-record-t
    values db-record-values-t)
  (set
    values.data 0
    data 0
    type *index.type
    id (db-id-add-type 0 type.id)
    val-id.mv-data &id)
  (status-require (db-txn-write-begin &txn))
  (db-mdb-status-require (mdb-cursor-open txn.mdb-txn index.dbi &index-cursor))
  (db-mdb-status-require (db-mdb-env-cursor-open txn records))
  (db-mdb-status-require (mdb-cursor-get records &val-id &val-data MDB-SET-RANGE))
  (sc-comment "for each record of type")
  (while (and db-mdb-status-is-success (= type.id (db-id-type (db-pointer->id val-id.mv-data))))
    (set
      record.data val-data.mv-data
      record.size val-data.mv-size)
    (status-require (db-record-data->values &type record &values))
    (status-require (db-index-key env index values &data &val-data.mv-size))
    (db-free-record-values &values)
    (set val-data.mv-data data)
    (db-mdb-status-require (mdb-cursor-put index-cursor &val-data &val-id 0))
    (set status.id (mdb-cursor-get records &val-id &val-data MDB-NEXT-NODUP)))
  db-mdb-status-expect-read
  (status-require (db-txn-commit &txn))
  (label exit
    (db-mdb-cursor-close-if-active index-cursor)
    (db-mdb-cursor-close-if-active records)
    (db-txn-abort-if-active txn)
    (db-free-record-values &values)
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

(define (db-type-indices-add type index result) (status-t db-type-t* db-index-t db-index-t**)
  "eventually resize type:indices and add index to type:indices.
  indices is extended and elements are set to zero on deletion.
  indices is currently never downsized, but a re-open of the db-env
  reallocates it in appropriate size (and invalidates all db-index-t pointers)"
  status-declare
  (declare
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
  (status-require (db-helper-realloc (* indices-len (sizeof db-index-t)) &indices))
  (set
    (array-get indices (- indices-len 1)) index
    type:indices indices
    type:indices-len indices-len
    *result (+ (- indices-len 1) indices))
  (label exit
    (return status)))

(define (db-index-create env type fields fields-len result-index)
  (status-t db-env-t* db-type-t* db-fields-len-t* db-fields-len-t db-index-t**)
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
    index-temp db-index-t*
    record-index db-index-t)
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
  (set index-temp (db-index-get type fields fields-len))
  (if index-temp (status-set-both-goto db-status-group-db db-status-id-duplicate))
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
  (db-mdb-status-require (mdb-dbi-open txn.mdb-txn name MDB-CREATE &record-index.dbi))
  (sc-comment "update cache. fields might be stack allocated")
  (status-require (db-helper-malloc (* fields-len (sizeof db-fields-len-t)) &fields-copy))
  (memcpy fields-copy fields (* fields-len (sizeof db-fields-len-t)))
  (struct-set record-index
    fields fields-copy
    fields-len fields-len
    type type)
  (status-require (db-type-indices-add type record-index &index-temp))
  (status-require (db-txn-commit &txn))
  (status-require (db-index-build env record-index))
  (set *result-index index-temp)
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
  "clear index and fill with data from existing records"
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
  (set status (db-index-build env *index))
  (label exit
    (free name)
    (db-txn-abort-if-active txn)
    (return status)))

(define (db-index-read selection count result-ids)
  (status-t db-index-selection-t db-count-t db-ids-t*)
  "read index values (record ids).
  count must be positive.
  if no more value is found, status is db-notfound.
  status must be success on call"
  status-declare
  db-mdb-declare-val-null
  db-mdb-declare-val-id
  (db-mdb-status-require (mdb-cursor-get selection.cursor &val-null &val-id MDB-GET-CURRENT))
  (do-while count
    (i-array-add *result-ids (db-pointer->id val-id.mv-data))
    (set count (- count 1))
    (db-mdb-status-require (mdb-cursor-get selection.cursor &val-null &val-id MDB-NEXT-DUP)))
  (label exit
    db-mdb-status-notfound-if-notfound
    (return status)))

(define (db-index-selection-finish selection) (void db-index-selection-t*)
  (db-mdb-cursor-close-if-active selection:cursor))

(define (db-index-select txn index values result)
  (status-t db-txn-t db-index-t db-record-values-t db-index-selection-t*)
  "open the cursor and set to the index key matching values.
  selection is positioned at the first match.
  if no match found then status is db-notfound"
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
  (set result:cursor cursor)
  (label exit
    (free data)
    (if status-is-failure
      (begin
        (db-mdb-cursor-close-if-active cursor)
        db-mdb-status-notfound-if-notfound))
    (return status)))