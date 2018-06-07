(pre-include "string.h")

(pre-define
  (db-mdb-txn-declare name) (define name MDB-txn* 0)
  (db-mdb-txn-begin env mdb-txn)
  (db-mdb-status-require! (mdb-txn-begin env 0 MDB-RDONLY (address-of mdb-txn)))
  (db-mdb-txn-write-begin env mdb-txn)
  (db-mdb-status-require! (mdb-txn-begin env 0 0 (address-of mdb-txn))) (db-mdb-txn-abort a)
  (begin
    (mdb-txn-abort a)
    (set a 0))
  (db-mdb-txn-commit a)
  (begin
    (db-mdb-status-require! (mdb-txn-commit a))
    (set a 0))
  (db-mdb-cursor-declare name) (define name MDB-cursor* 0)
  (db-mdb-cursor-declare-two name-a name-b)
  (begin
    (db-mdb-cursor-declare name-a)
    (db-mdb-cursor-declare name-b))
  (db-mdb-cursor-declare-three name-a name-b name-c)
  (begin
    (db-mdb-cursor-declare-two name-a name-b)
    (db-mdb-cursor-declare name-c))
  (db-mdb-cursor-close-two a b)
  (begin
    (mdb-cursor-close a)
    (mdb-cursor-close b))
  (db-mdb-cursor-close-three a b c)
  (begin
    (db-mdb-cursor-close-two a b)
    (mdb-cursor-close c))
  (db-mdb-cursor-get-norequire cursor val-a val-b cursor-operation)
  (begin
    "only updates status, no goto on error"
    (status-set-id (mdb-cursor-get cursor &val-a &val-b cursor-operation)))
  (db-mdb-cursor-next-dup-norequire cursor val-a val-b)
  (db-mdb-cursor-get-norequire cursor val-a val-b MDB-NEXT-DUP)
  (db-mdb-cursor-next-nodup-norequire cursor val-a val-b)
  (db-mdb-cursor-get-norequire cursor val-a val-b MDB-NEXT-NODUP)
  (db-mdb-cursor-del-norequire cursor flags) (status-set-id (mdb-cursor-del cursor flags))
  (db-mdb-cursor-get cursor val-a val-b cursor-operation)
  (db-mdb-status-require!
    (mdb-cursor-get cursor (address-of val-a) (address-of val-b) cursor-operation))
  (db-mdb-cursor-put cursor val-a val-b)
  (db-mdb-status-require! (mdb-cursor-put cursor (address-of val-a) (address-of val-b) 0))
  (db-mdb-put txn dbi val-a val-b)
  (db-mdb-status-require! (mdb-put dbi (address-of val-a) (address-of val-b) 0))
  (db-mdb-cursor-open txn dbi name) (db-mdb-status-require! (mdb-cursor-open txn dbi &name))
  (db-mdb-cursor-open-two txn dbi-a name-a dbi-b name-b)
  (begin
    (db-mdb-cursor-open txn dbi-a name-a)
    (db-mdb-cursor-open txn dbi-b name-b))
  (db-mdb-cursor-open-three txn dbi-a name-a dbi-b name-b dbi-c name-c)
  (begin
    (db-mdb-cursor-open-two txn dbi-a name-a dbi-b name-b)
    (db-mdb-cursor-open txn dbi-c name-c))
  (db-mdb-declare-val name size)
  (begin
    (declare name MDB-val)
    (set name.mv-size size))
  db-mdb-declare-val-id (db-mdb-declare-val val-id db-size-id)
  db-mdb-declare-val-id-2 (db-mdb-declare-val val-id-2 db-size-id)
  db-mdb-declare-val-id-3 (db-mdb-declare-val val-id-3 db-size-id)
  db-mdb-declare-val-null (db-mdb-declare-val val-null 0)
  db-mdb-declare-val-graph-data (db-mdb-declare-val val-graph-data db-size-graph-data)
  db-mdb-declare-val-graph-key (db-mdb-declare-val val-graph-key db-size-graph-key)
  db-mdb-reset-val-null (set val-null.mv-size 0)
  (db-mdb-cursor-each-key cursor val-key val-value body)
  (begin
    (db-mdb-cursor-get-norequire cursor val-key val-value MDB-FIRST)
    (while db-mdb-status-success?
      body
      (db-mdb-cursor-next-nodup-norequire cursor val-key val-value))
    db-mdb-status-require-notfound)
  (db-mdb-cursor-set-first! cursor)
  (db-mdb-status-require! (mdb-cursor-get cursor &val-null &val-null MDB-FIRST))
  (db-mdb-val->graph-key a) (convert-type a.mv-data db-id-t*))

(define (db-mdb-compare-id a b) ((static int) (const MDB-val*) (const MDB-val*))
  "mdb comparison routines are used by lmdb for search, insert and delete"
  (return (db-id-compare (db-pointer->id a:mv-data) (db-pointer->id b:mv-data))))

(define (db-mdb-compare-graph-key a b) ((static int) (const MDB-val*) (const MDB-val*))
  (cond
    ((< (db-pointer->id a:mv-data) (db-pointer->id b:mv-data)) (return -1))
    ((> (db-pointer->id a:mv-data) (db-pointer->id b:mv-data)) (return 1))
    (else (return (db-id-compare (db-pointer->id-at a:mv-data 1) (db-pointer->id-at b:mv-data 1))))))

(define (db-mdb-compare-graph-data a b) ((static int) (const MDB-val*) (const MDB-val*))
  "memcmp does not work here, gives -1 for 256 vs 1"
  (cond
    ((< (db-graph-data->ordinal a:mv-data) (db-graph-data->ordinal b:mv-data)) (return -1))
    ((> (db-graph-data->ordinal a:mv-data) (db-graph-data->ordinal b:mv-data)) (return 1))
    (else (return (db-id-compare (db-graph-data->id a:mv-data) (db-graph-data->id b:mv-data))))))

(define (db-mdb-compare-data a b) ((static int) (const MDB-val*) (const MDB-val*))
  (define length-difference ssize-t
    (- (convert-type a:mv-size ssize-t) (convert-type b:mv-size ssize-t)))
  (return
    (if* length-difference
      (if* (< length-difference 0) -1
        1)
      (memcmp a:mv-data b:mv-data a:mv-size))))