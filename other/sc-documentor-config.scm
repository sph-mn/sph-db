; config to be used by exe/list-bindings.
; for generated bindings

(define-as generated list-q
  ; mostly from macro-generated mi-lists
  (declare db-ids-t (type (struct (link (struct db-ids-struct*)) (data db-id-t)))
    db-data-list-t (type (struct (link (struct db-data-list-struct*)) (data db-data-t)))
    db-data-records-t (type (struct (link (struct db-data-records-struct*)) (data db-data-record-t)))
    db-graph-records-t
    (type (struct (link (struct db-graph-records-struct*)) (data db-graph-record-t)))
    (db-data-list-add a value) (db-data-list-t* db-data-list-t* db-data-t)
    (db-data-records-add a value) (db-data-records-t* db-data-records-t* db-data-record-t)
    (db-ids-add a value) (db-ids-t* db-ids-t* db-id-t)
    (db-graph-records-add a value)
    (db-graph-records-t* db-graph-records-t* db-graph-record-t) (db-data-list-drop a)
    (db-data-list-t* db-data-list-t*) (db-data-records-drop a)
    (db-data-records-t* db-data-records-t*) (db-ids-drop a)
    (db-ids-t* db-ids-t*) (db-graph-records-drop a)
    (db-graph-records-t* db-graph-records-t*) (db-data-list-length a)
    (size-t db-data-list-t*) (db-data-records-length a)
    (size-t db-data-records-t*) (db-ids-length a)
    (size-t db-ids-t*) (db-graph-records-length a) (size-t db-graph-records-t*)))

(define-as excluded list-q
  ; involuntarily exported
  "db-mdb-.*" "mi-list-.*" "db-status-no-more-data-if-mdb-notfound" "imht-set-.*")

(define-as identifier-replacements list-q
  ; replace short type aliases to make clear that they do not have to be used
  "^ui8" "uint8_t"
  "^ui16" "uint16_t"
  "^ui32" "uint32_t"
  "^ui64" "uint64_t"
  "^i8" "int8_t"
  "^i16" "int16_t" "^i32" "int32_t" "^i64" "int64_t" "^f32" "double" "boolean" "uint8_t")
