(sc-comment "return status and error handling")
(sc-include "foreign/sph/status")

(enum
  (db-status-id-condition-unfulfilled
    db-status-id-data-length
    db-status-id-different-format
    db-status-id-duplicate
    db-status-id-input-type
    db-status-id-max-id
    db-status-id-max-type-id
    db-status-id-max-type-id-size
    db-status-id-memory
    db-status-id-missing-argument-db-root
    db-status-id-no-more-data
    db-status-id-not-implemented
    db-status-id-path-not-accessible-db-root
    db-status-id-undefined db-status-group-db db-status-group-lmdb db-status-group-libc))

(pre-define
  (db-status-set-id-goto status-id) (status-set-both-goto db-status-group-db status-id)
  (db-status-require-read! expression)
  (begin
    (set status expression)
    (if (not (or status-success? (status-id-is? db-status-id-no-more-data))) status-goto))
  db-status-no-more-data-if-mdb-notfound
  (if db-mdb-status-notfound? (status-set-both db-status-group-db db-status-id-no-more-data))
  db-status-success-if-mdb-notfound (if db-mdb-status-notfound? (status-set-id status-id-success))
  db-status-success-if-no-more-data
  (if (status-id-is? db-status-id-no-more-data) (struct-set status id status-id-success))
  db-mdb-status-success? (status-id-is? MDB-SUCCESS)
  db-mdb-status-failure? (not db-mdb-status-success?)
  db-mdb-status-notfound? (status-id-is? MDB-NOTFOUND)
  (db-mdb-status-set-id-goto id) (status-set-both-goto db-status-group-lmdb id)
  (db-mdb-status-require! expression)
  (begin
    (status-set-id expression)
    (if db-mdb-status-failure? (status-set-group-goto db-status-group-lmdb)))
  db-mdb-status-require (if db-mdb-status-failure? (status-set-group-goto db-status-group-lmdb))
  db-mdb-status-require-read
  (if (not (or db-mdb-status-success? db-mdb-status-notfound?))
    (status-set-group-goto db-status-group-lmdb))
  (db-mdb-status-require-read! expression)
  (begin
    (status-set-id expression)
    db-mdb-status-require-read)
  db-mdb-status-require-notfound
  (if (not db-mdb-status-notfound?) (status-set-group-goto db-status-group-lmdb)))

(define (db-status-group-id->name a) (b8* status-i-t)
  (declare b char*)
  (case = a
    (db-status-group-db (set b "sph-db"))
    (db-status-group-lmdb (set b "lmdb"))
    (db-status-group-libc (set b "libc"))
    (else (set b "")))
  (return b))

(define (db-status-description a) (b8* status-t)
  "get the description if available for a status"
  (declare b char*)
  (case = a.group
    (db-status-group-db
      (case = a.id
        (db-status-id-input-type (set b "input argument is of wrong type"))
        (db-status-id-data-length (set b "data too large"))
        (db-status-id-duplicate (set b "element already exists"))
        (db-status-id-not-implemented (set b "not implemented"))
        (db-status-id-missing-argument-db-root (set b "missing argument 'db-root'"))
        (db-status-id-path-not-accessible-db-root (set b "root not accessible"))
        (db-status-id-memory (set b "not enough memory or other memory allocation error"))
        (db-status-id-max-id (set b "maximum identifier value for the type has been reached"))
        (db-status-id-max-type-id (set b "maximum type identifier value has been reached"))
        (db-status-id-max-type-id-size
          (set b
            "type identifier size is configured to be greater than 16 bit, which is currently not supported"))
        (db-status-id-condition-unfulfilled (set b "condition unfulfilled"))
        (db-status-id-no-more-data (set b "no more data to read"))
        (db-status-id-different-format
          (set b "configured format differs from the format the database was created with"))
        (else (set b ""))))
    (db-status-group-lmdb (set b (mdb-strerror a.id)))
    (else (set b "")))
  (return (convert-type b b8*)))

(define (db-status-name a) (b8* status-t)
  "get the name if available for a status"
  (declare b char*)
  (case = a.group
    (db-status-group-db
      (case = a.id
        (db-status-id-input-type (set b "input-type"))
        (db-status-id-data-length (set b "data-length"))
        (db-status-id-duplicate (set b "duplicate"))
        (db-status-id-not-implemented (set b "not-implemented"))
        (db-status-id-missing-argument-db-root (set b "missing-argument-db-root"))
        (db-status-id-path-not-accessible-db-root (set b "path-not-accessible-db-root"))
        (db-status-id-memory (set b "memory"))
        (db-status-id-max-id (set b "max-id-reached"))
        (db-status-id-max-type-id (set b "max-type-id-reached"))
        (db-status-id-max-type-id-size (set b "type-id-size-too-big"))
        (db-status-id-condition-unfulfilled (set b "condition-unfulfilled"))
        (db-status-id-no-more-data (set b "no-more-data"))
        (db-status-id-different-format (set b "different-format"))
        (else (set b "unknown"))))
    (db-status-group-lmdb (set b (mdb-strerror a.id)))
    (else (set b "unknown")))
  (return (convert-type b b8*)))