(sc-comment "return status and error handling")
(sc-include "foreign/sph/status")

(enum
  (db-status-id-success
    db-status-id-undefined
    db-status-id-condition-unfulfilled
    db-status-id-data-length
    db-status-id-different-format
    db-status-id-duplicate
    db-status-id-input-type
    db-status-id-invalid-argument
    db-status-id-max-element-id
    db-status-id-max-type-id
    db-status-id-max-type-id-size
    db-status-id-memory
    db-status-id-missing-argument-db-root
    db-status-id-notfound
    db-status-id-not-implemented
    db-status-id-path-not-accessible-db-root
    db-status-group-db db-status-group-lmdb db-status-group-libc db-status-id-index-keysize))

(pre-define
  (db-status-set-id-goto status-id) (status-set-both-goto db-status-group-db status-id)
  (db-status-require-read expression)
  (begin
    (set status expression)
    (if (not (or status-is-success (= status.id db-status-id-notfound))) status-goto))
  db-status-success-if-notfound
  (if (= status.id db-status-id-notfound) (set status.id status-id-success)))

(define (db-status-group-id->name a) (ui8* status-id-t)
  (declare b char*)
  (case = a
    (db-status-group-db (set b "sph-db"))
    (db-status-group-lmdb (set b "lmdb"))
    (db-status-group-libc (set b "libc"))
    (else (set b "")))
  (return b))

(define (db-status-description a) (ui8* status-t)
  "get the description if available for a status"
  (declare b char*)
  (case = a.group
    (db-status-group-lmdb (set b (mdb-strerror a.id)))
    (else
      (case = a.id
        (db-status-id-invalid-argument (set b "input argument is of wrong type"))
        (db-status-id-input-type (set b "input argument is of wrong type"))
        (db-status-id-data-length (set b "data too large"))
        (db-status-id-duplicate (set b "element already exists"))
        (db-status-id-not-implemented (set b "not implemented"))
        (db-status-id-missing-argument-db-root (set b "missing argument 'db-root'"))
        (db-status-id-path-not-accessible-db-root (set b "root not accessible"))
        (db-status-id-memory (set b "not enough memory or other memory allocation error"))
        (db-status-id-max-element-id
          (set b "maximum element identifier value has been reached for the type"))
        (db-status-id-max-type-id (set b "maximum type identifier value has been reached"))
        (db-status-id-max-type-id-size
          (set b
            "type identifier size is either configured to be greater than 16 bit, which is currently not supported, or is not smaller than node id size"))
        (db-status-id-condition-unfulfilled (set b "condition unfulfilled"))
        (db-status-id-notfound (set b "no more data to read"))
        (db-status-id-different-format
          (set b "configured format differs from the format the database was created with"))
        (db-status-id-index-keysize (set b "index key to be inserted exceeds mdb maxkeysize"))
        (else (set b "")))))
  (return (convert-type b ui8*)))

(define (db-status-name a) (ui8* status-t)
  "get the name if available for a status"
  (declare b char*)
  (case = a.group
    (db-status-group-lmdb (set b (mdb-strerror a.id)))
    (else
      (case = a.id
        (db-status-id-invalid-argument (set b "invalid-argument"))
        (db-status-id-input-type (set b "input-type"))
        (db-status-id-data-length (set b "data-length"))
        (db-status-id-duplicate (set b "duplicate"))
        (db-status-id-not-implemented (set b "not-implemented"))
        (db-status-id-missing-argument-db-root (set b "missing-argument-db-root"))
        (db-status-id-path-not-accessible-db-root (set b "path-not-accessible-db-root"))
        (db-status-id-memory (set b "memory"))
        (db-status-id-max-element-id (set b "max-element-id-reached"))
        (db-status-id-max-type-id (set b "max-type-id-reached"))
        (db-status-id-max-type-id-size (set b "type-id-size-too-big"))
        (db-status-id-condition-unfulfilled (set b "condition-unfulfilled"))
        (db-status-id-notfound (set b "notfound"))
        (db-status-id-different-format (set b "differing-db-format"))
        (db-status-id-index-keysize (set b "index-key-mdb-keysize"))
        (else (set b "unknown")))))
  (return (convert-type b ui8*)))