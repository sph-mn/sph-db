(define (db-record-values->data values result) (status-t db-record-values-t db-record-t*)
  "convert a record-values array to the data format that is used as btree value for records.
  the data for unset trailing fields is not included.
  assumes that fields are in the order (fixed-size-fields variable-size-fields).
  data-size is uint64-t because its content is copied with memcpy to variable size prefixes
  which are at most 64 bit.
  assumes that value sizes are not too large and that record-values-set checks that"
  status-declare
  (declare
    data void*
    data-size uint64-t
    data-temp uint8-t*
    field-data void*
    field-size db-field-type-size-t
    fields db-field-t*
    fields-fixed-count db-fields-len-t
    i db-fields-len-t
    size size-t)
  (sc-comment "no fields set, no data stored")
  (if (not values.extent)
    (begin
      (set
        result:data 0
        result:size 0)
      (return status)))
  (set
    size 0
    fields-fixed-count values.type:fields-fixed-count
    fields values.type:fields)
  (sc-comment "calculate data size")
  (for ((set i 0) (< i values.extent) (set i (+ 1 i)))
    (set size
      (+
        (struct-get (array-get fields i) size)
        (if* (< i fields-fixed-count) 0
          (struct-get (array-get values.data i) size))
        size)))
  (sc-comment "allocate and prepare data")
  (status-require (db-helper-calloc size &data))
  (set data-temp data)
  (for ((set i 0) (< i values.extent) (set i (+ 1 i)))
    (set
      data-size (struct-get (array-get values.data i) size)
      field-size (struct-get (array-get fields i) size)
      field-data (struct-get (array-get values.data i) data))
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
    result:data data
    result:size size)
  (label exit
    (return status)))

(define (db-record-ref type record field)
  (db-record-value-t db-type-t* db-record-t db-fields-len-t)
  "from the full btree value of a record (data with all fields), return a reference
  to the data for specific field and the size.
  if a trailing field is not stored with the data, record.data and .size are 0"
  (declare
    data-temp uint8-t*
    end uint8-t*
    i db-fields-len-t
    offset size-t
    result db-record-value-t
    prefix-size uint8-t
    size size-t)
  (if (< field type:fields-fixed-count)
    (begin
      (sc-comment "fixed length field")
      (set offset (array-get type:fields-fixed-offsets field))
      (if (< offset record.size)
        (set
          result.data (+ offset (convert-type record.data uint8-t*))
          result.size (struct-get (array-get type:fields field) size))
        (set
          result.data 0
          result.size 0))
      (return result))
    (begin
      (sc-comment "variable length field")
      (set offset
        (if* type:fields-fixed-count (array-get type:fields-fixed-offsets type:fields-fixed-count)
          0))
      (if (< offset record.size)
        (begin
          (set
            data-temp (+ offset (convert-type record.data uint8-t*))
            end (+ record.size (convert-type record.data uint8-t*))
            i type:fields-fixed-count)
          (sc-comment "variable length data is prefixed by its size")
          (while (and (<= i field) (< data-temp end))
            (set
              size 0
              prefix-size (struct-get (array-get type:fields i) size))
            (memcpy &size data-temp prefix-size)
            (set data-temp (+ prefix-size data-temp))
            (if (= i field)
              (begin
                (set
                  result.data data-temp
                  result.size size)
                (return result)))
            (set
              i (+ 1 i)
              data-temp (+ size data-temp)))))
      (set
        result.data 0
        result.size 0)
      (return result))))

(define (db-record-values-new type result) (status-t db-type-t* db-record-values-t*)
  "allocate memory for a new record values array. all fields an sizes are zero.
  \"extent\" is the last field index that is set plus one, zero if no field is set"
  status-declare
  (declare data db-record-value-t*)
  (status-require (db-helper-calloc (* type:fields-len (sizeof db-record-value-t)) &data))
  (struct-set *result
    type type
    data data
    extent 0)
  (label exit
    (return status)))

(define (db-record-values-free a) (void db-record-values-t*) (free-and-set-null a:data))

(define (db-record-values-set a field data size)
  (status-t db-record-values-t* db-fields-len-t void* size-t)
  "set a value for a field in record values.
  a failure status is returned if size is too large for the field"
  status-declare
  (declare values db-record-values-t)
  (set values *a)
  (sc-comment "reject invalid sizes for fixed/variable fields")
  (if
    (if* (< field values.type:fields-fixed-count)
      (< (struct-get (array-get values.type:fields field) size) size)
      (<= (bit-shift-left 1 (* 8 (struct-get (array-get values.type:fields field) size))) size))
    (status-set-both-goto db-status-group-db db-status-id-data-length))
  (struct-set (array-get values.data field)
    data data
    size size)
  (if (or (= 0 values.extent) (>= field values.extent)) (set values.extent (+ 1 field)))
  (set *a values)
  (label exit
    (return status)))

(define (db-record-create txn values result) (status-t db-txn-t db-record-values-t db-id-t*)
  status-declare
  db-mdb-declare-val-id
  (db-mdb-cursor-declare records)
  (declare
    val-data MDB-val
    id db-id-t
    record db-record-t)
  (set
    record.data 0
    val-id.mv-data &id)
  (status-require (db-record-values->data values &record))
  (set
    val-data.mv-data record.data
    val-data.mv-size record.size)
  (db-mdb-status-require (db-mdb-env-cursor-open txn records))
  (sc-comment "sequence updated as late as possible")
  (status-require (db-sequence-next txn.env values.type:id &id))
  (db-mdb-status-require (mdb-cursor-put records &val-id &val-data 0))
  (db-mdb-cursor-close records)
  (status-require (db-indices-entry-ensure txn values id))
  (set *result id)
  (label exit
    (db-mdb-cursor-close-if-active records)
    (free record.data)
    (return status)))

(define (db-free-record-values values) (void db-record-values-t*) (free-and-set-null values:data))

(define (db-record-data->values type data result)
  (status-t db-type-t* db-record-t db-record-values-t*)
  status-declare
  (declare
    field-data db-record-value-t
    fields-len db-fields-len-t
    values db-record-values-t
    i db-fields-len-t)
  (set fields-len type:fields-len)
  (status-require (db-record-values-new type &values))
  (for ((set i 0) (< i fields-len) (set i (+ 1 i)))
    (set field-data (db-record-ref type data i))
    (if (not field-data.data) break)
    (db-record-values-set &values i field-data.data field-data.size))
  (set *result values)
  (label exit
    (if status-is-failure (db-free-record-values &values))
    (return status)))

(define (db-record-read selection count result-records)
  (status-t db-record-selection-t db-count-t db-records-t*)
  status-declare
  db-mdb-declare-val-id
  (declare
    val-data MDB-val
    matcher db-record-matcher-t
    matcher-state void*
    record db-record-t
    skip boolean
    match boolean
    type-id db-type-id-t)
  (set
    matcher selection.matcher
    matcher-state selection.matcher-state
    skip (bit-and selection.options db-selection-flag-skip)
    type-id selection.type:id)
  (db-mdb-status-require (mdb-cursor-get selection.cursor &val-id &val-data MDB-GET-CURRENT))
  (while
    (and db-mdb-status-is-success count (= type-id (db-id-type (db-pointer->id val-id.mv-data))))
    (sc-comment "type is passed to matcher for record-ref")
    (if matcher
      (set
        record.id (db-pointer->id val-id.mv-data)
        record.data val-data.mv-data
        record.size val-data.mv-size
        match (matcher selection.type record matcher-state))
      (set match #t))
    (if match
      (begin
        (if (not skip)
          (begin
            (set
              record.id (db-pointer->id val-id.mv-data)
              record.data val-data.mv-data
              record.size val-data.mv-size)
            (i-array-add *result-records record)))
        (set count (- count 1))))
    (db-mdb-status-require (mdb-cursor-get selection.cursor &val-id &val-data MDB-NEXT-NODUP)))
  (label exit
    db-mdb-status-notfound-if-notfound
    (return status)))

(define (db-record-skip selection count) (status-t db-record-selection-t db-count-t)
  "skip the next count matches"
  status-declare
  (set
    selection.options (bit-or selection.options db-selection-flag-skip)
    status (db-record-read selection count 0)
    selection.options (bit-xor selection.options db-selection-flag-skip))
  (return status))

(define (db-record-select txn type matcher matcher-state result-selection)
  (status-t db-txn-t db-type-t* db-record-matcher-t void* db-record-selection-t*)
  "get records by type and optionally filtering data.
  result count is unknown on call or can be large, that is why a selection state
  for partial reading is used.
  matcher: zero if unused. a function that is called for each record of type
  matcher-state: zero if unused. a pointer passed to each call of matcher"
  status-declare
  db-mdb-declare-val-id
  db-mdb-declare-val-null
  (db-mdb-cursor-declare records)
  (declare id db-id-t)
  (db-mdb-status-require (db-mdb-env-cursor-open txn records))
  (sc-comment "position at first record of type")
  (set
    id (db-id-add-type 0 type:id)
    val-id.mv-data &id)
  (db-mdb-status-require (mdb-cursor-get records &val-id &val-null MDB-SET-RANGE))
  (if (not (= type:id (db-id-type (db-pointer->id val-id.mv-data))))
    (status-set-both-goto db-status-group-db db-status-id-notfound))
  (set
    result-selection:type type
    result-selection:cursor records
    result-selection:matcher matcher
    result-selection:matcher-state matcher-state
    result-selection:options 0)
  (label exit
    (if status-is-failure
      (begin
        (mdb-cursor-close records)
        db-mdb-status-notfound-if-notfound))
    (return status)))

(define (db-record-get-internal records-cursor ids match-all result-records)
  (status-t MDB-cursor* db-ids-t boolean db-records-t*)
  "get records by id.
  returns status notfound if any id could not be found if match-all is true.
  like record-get with a given mdb-cursor"
  status-declare
  db-mdb-declare-val-id
  (declare
    val-data MDB-val
    record db-record-t)
  (while (i-array-in-range ids)
    (set
      val-id.mv-data ids.current
      status.id (mdb-cursor-get records-cursor &val-id &val-data MDB-SET-KEY))
    (if db-mdb-status-is-success
      (begin
        (set
          record.id (i-array-get ids)
          record.data val-data.mv-data
          record.size val-data.mv-size)
        (i-array-add *result-records record))
      (if db-mdb-status-is-notfound
        (if match-all (status-set-both-goto db-status-group-db db-status-id-notfound))
        (status-set-group-goto db-status-group-lmdb)))
    (i-array-forward ids))
  (label exit
    (return status)))

(define (db-record-get txn ids match-all result-records)
  (status-t db-txn-t db-ids-t boolean db-records-t*)
  "get a reference to data for one record identified by id.
  fields can be accessed with db-record-ref.
  if a record could not be found and match-all is true, status is status-id-notfound"
  status-declare
  (db-mdb-cursor-declare records)
  (db-mdb-status-require (db-mdb-env-cursor-open txn records))
  (set status (db-record-get-internal records ids match-all result-records))
  (label exit
    (db-mdb-cursor-close records)
    (return status)))

(define (db-relation-internal-delete left right label ordinal relation-lr relation-rl relation-ll)
  (status-t
    db-ids-t* db-ids-t* db-ids-t* db-ordinal-condition-t* MDB-cursor* MDB-cursor* MDB-cursor*)
  "declare because it is defined later")

(define (db-record-delete txn ids) (status-t db-txn-t db-ids-t)
  "delete records and all their relations. status "
  status-declare
  db-mdb-declare-val-id
  (declare
    id db-id-t
    val-data MDB-val
    values db-record-values-t
    record db-record-t)
  (db-mdb-cursor-declare records)
  (db-mdb-cursor-declare relation-lr)
  (db-mdb-cursor-declare relation-rl)
  (db-mdb-cursor-declare relation-ll)
  (sc-comment "first delete references")
  (db-mdb-status-require (db-mdb-env-cursor-open txn relation-lr))
  (db-mdb-status-require (db-mdb-env-cursor-open txn relation-rl))
  (db-mdb-status-require (db-mdb-env-cursor-open txn relation-ll))
  (status-require (db-relation-internal-delete &ids 0 0 0 relation-lr relation-rl relation-ll))
  (status-require (db-relation-internal-delete 0 &ids 0 0 relation-lr relation-rl relation-ll))
  (status-require (db-relation-internal-delete 0 0 &ids 0 relation-lr relation-rl relation-ll))
  (db-mdb-status-require (db-mdb-env-cursor-open txn records))
  (sc-comment "delete record and index btree entries")
  (while (i-array-in-range ids)
    (set
      val-id.mv-data ids.current
      status.id (mdb-cursor-get records &val-id &val-data MDB-SET-KEY))
    (if db-mdb-status-is-success
      (begin
        (set
          id (i-array-get ids)
          record.data val-data.mv-data
          record.size val-data.mv-size)
        (status-require
          (db-record-data->values (db-type-get-by-id txn.env (db-id-type id)) record &values))
        (status-require (db-indices-entry-delete txn values id))
        (db-mdb-status-require (mdb-cursor-del records 0)))
      (if db-mdb-status-is-notfound (set status.id status-id-success)
        (status-set-group-goto db-status-group-lmdb)))
    (i-array-forward ids))
  (label exit
    (db-mdb-cursor-close-if-active relation-lr)
    (db-mdb-cursor-close-if-active relation-rl)
    (db-mdb-cursor-close-if-active relation-ll)
    (db-mdb-cursor-close-if-active records)
    (return status)))

(define (db-record-delete-type txn type-id) (status-t db-txn-t db-type-id-t)
  status-declare
  (sc-comment "delete all records of type and all their relations")
  db-mdb-declare-val-id
  db-mdb-declare-val-null
  (db-mdb-cursor-declare records)
  (declare id db-id-t)
  (db-mdb-status-require (db-mdb-env-cursor-open txn records))
  (set
    id (db-id-add-type (convert-type 0 db-id-t) type-id)
    val-id.mv-data &id)
  (db-mdb-status-require (mdb-cursor-get records &val-id &val-null MDB-SET-RANGE))
  (while (and db-mdb-status-is-success (= type-id (db-id-type (db-pointer->id val-id.mv-data))))
    (db-mdb-status-require (mdb-cursor-del records 0))
    (set status.id (mdb-cursor-get records &val-id &val-null MDB-NEXT-NODUP)))
  (label exit
    db-mdb-status-notfound-if-notfound
    (db-mdb-cursor-close-if-active records)
    (return status)))

(define (db-record-selection-finish a) (void db-record-selection-t*)
  (db-mdb-cursor-close-if-active a:cursor))

(define (db-record-update txn id values) (status-t db-txn-t db-id-t db-record-values-t)
  "set new data for the record with the given id"
  status-declare
  db-mdb-declare-val-id
  (db-mdb-cursor-declare records)
  (declare
    val-data MDB-val
    record db-record-t)
  (set
    val-id.mv-data &id
    record.data 0)
  (status-require (db-record-values->data values &record))
  (db-mdb-status-require (db-mdb-env-cursor-open txn records))
  (db-mdb-status-require (mdb-cursor-get records &val-id &val-data MDB-SET))
  (set
    val-data.mv-data record.data
    val-data.mv-size record.size)
  (db-mdb-status-require (mdb-cursor-put records &val-id &val-data 0))
  (db-mdb-cursor-close records)
  (status-require (db-indices-entry-ensure txn values id))
  (label exit
    (db-mdb-cursor-close-if-active records)
    (free record.data)
    (return status)))

(define (db-record-select-delete txn type matcher matcher-state)
  (status-t db-txn-t db-type-t* db-record-matcher-t void*)
  "delete records selected by type or custom matcher routine.
  collects ids in batches and calls db-record-delete"
  status-declare
  (i-array-declare ids db-ids-t)
  (i-array-declare records db-records-t)
  (db-record-selection-declare selection)
  (status-require (db-record-select txn type matcher matcher-state &selection))
  (db-records-new db-batch-len &records)
  (db-ids-new db-batch-len &ids)
  (do-while status-is-success
    (status-require-read (db-record-read selection db-batch-len &records))
    (if (not (i-array-length records)) continue)
    (db-records->ids records &ids)
    (status-require (db-record-delete txn ids))
    (i-array-clear ids)
    (i-array-clear records))
  (label exit
    (db-record-selection-finish &selection)
    (i-array-free ids)
    (i-array-free records)
    (return status)))

(define (db-record-index-read selection count result-records)
  (status-t db-record-index-selection-t db-count-t db-records-t*)
  status-declare
  (db-ids-declare ids)
  (db-ids-new count &ids)
  (status-require-read (db-index-read selection.index-selection count &ids))
  (status-require (db-record-get-internal selection.records-cursor ids #t result-records))
  (label exit
    (return status)))

(define (db-record-index-selection-finish selection) (void db-record-index-selection-t*)
  (db-index-selection-finish &selection:index-selection)
  (db-mdb-cursor-close-if-active selection:records-cursor))

(define (db-record-index-select txn index values result-selection)
  (status-t db-txn-t db-index-t db-record-values-t db-record-index-selection-t*)
  status-declare
  (db-mdb-cursor-declare records)
  (db-index-selection-declare index-selection)
  (status-require (db-index-select txn index values &index-selection))
  (db-mdb-status-require (db-mdb-env-cursor-open txn records))
  (set
    result-selection:index-selection index-selection
    result-selection:records-cursor records)
  (label exit
    (if status-is-failure (db-mdb-cursor-close-if-active records))
    (return status)))