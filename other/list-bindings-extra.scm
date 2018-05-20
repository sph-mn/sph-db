; config to be used by exe/list-bindings.
; for generated bindings

(define-as generated list-q
  ; mostly from mi-lists
  (declare
    db-ids-t (type (struct (link (struct db-ids-struct*)) (data db-id-t)))
    db-data-list-t (type (struct (link (struct db-data-list-struct*)) (data db-data-t)))
    db-data-records-t (type (struct (link (struct db-data-records-struct*)) (data db-data-record-t)))
    db-relation-records-t (type (struct (link (struct db-relation-records-struct*)) (data db-relation-record-t)))
    (db-data-list-add a value) (db-data-list-t* db-data-list-t* db-data-t)
    (db-data-records-add a value) (db-data-records-t* db-data-records-t* db-data-record-t)
    (db-ids-add a value) (db-ids-t* db-ids-t* db-id-t)
    (db-relation-records-add a value) (db-relation-records-t* db-relation-records-t* db-relation-record-t)
    (db-data-list-drop a) (db-data-list-t* db-data-list-t*)
    (db-data-records-drop a) (db-data-records-t* db-data-records-t*)
    (db-ids-drop a) (db-ids-t* db-ids-t*)
    (db-relation-records-drop a) (db-relation-records-t* db-relation-records-t*)
    (db-data-list-length a) (size-t db-data-list-t*)
    (db-data-records-length a) (size-t db-data-records-t*)
    (db-ids-length a) (size-t db-ids-t*)
    (db-relation-records-length a) (size-t db-relation-records-t*)))

(define-as excluded list-q
  ; exported but not really supposed to be used
  "db-mdb-.*" "mi-list-.*" "db-status-no-more-data-if-mdb-notfound" "imht-set-.*")

(define-as identifier-replacements list-q
  ; replace short type aliases to make it clear that they do not have to be used
  "^b0" "void"
  "^b8" "uint8_t"
  "^b16" "uint16_t"
  "^b32" "uint32_t"
  "^b64" "uint64_t"
  "^b8-s" "int8_t"
  "^b16-s" "int16_t" "^b32-s" "int32_t" "^b64-s" "int64_t" "^f32-s" "double" "boolean" "uint8_t")
