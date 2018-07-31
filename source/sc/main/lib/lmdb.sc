(pre-include "string.h")
(sc-comment "lmdb helpers")

(pre-define
  ; status
  db-mdb-status-is-notfound (= MDB-NOTFOUND status.id)
  db-mdb-status-is-success (= MDB-SUCCESS status.id)
  db-mdb-status-is-failure (not db-mdb-status-is-success)
  db-mdb-status-notfound-if-notfound
  (if db-mdb-status-is-notfound
    (set
      status.group db-status-group-db
      status.id db-status-id-notfound))
  db-mdb-status-success-if-notfound (if db-mdb-status-is-notfound (set status.id status-id-success))
  (db-mdb-status-set-id-goto id)
  (set
    status.group db-status-group-lmdb
    status.id id)
  (db-mdb-status-require expression)
  (begin
    (set status.id expression)
    (if db-mdb-status-is-failure (status-set-group-goto db-status-group-lmdb)))
  (db-mdb-status-require-read expression)
  (begin
    (set status.id expression)
    (if (not (or db-mdb-status-is-success db-mdb-status-is-notfound))
      (status-set-group-goto db-status-group-lmdb)))
  db-mdb-status-expect-notfound
  (if (not db-mdb-status-is-notfound) (status-set-group-goto db-status-group-lmdb))
  db-mdb-status-expect-read
  (if (not (or db-mdb-status-is-success db-mdb-status-is-notfound))
    (status-set-group-goto db-status-group-lmdb))
  ; cursor
  (db-mdb-cursor-declare name) (define name MDB-cursor* 0)
  (db-mdb-env-cursor-open txn name)
  (mdb-cursor-open txn.mdb-txn (: txn.env (pre-concat dbi- name)) &name) (db-mdb-cursor-close name)
  (begin
    (mdb-cursor-close name)
    (set name 0))
  (db-mdb-cursor-close-if-active a) (if a (db-mdb-cursor-close a))
  (db-mdb-cursor-each-key cursor val-key val-value body)
  (begin
    (set status.id (mdb-cursor-get cursor &val-key &val-value MDB-FIRST))
    (while db-mdb-status-is-success
      body
      (set status.id (mdb-cursor-get cursor &val-key &val-value MDB-NEXT-NODUP)))
    db-mdb-status-expect-notfound)
  ; other
  (db-mdb-val->graph-key a) (convert-type a.mv-data db-id-t*)
  db-mdb-declare-val-id
  (begin
    (declare val-id MDB-val)
    (set val-id.mv-size db-size-id))
  db-mdb-declare-val-id-2
  (begin
    (declare val-id-2 MDB-val)
    (set val-id-2.mv-size db-size-id))
  db-mdb-declare-val-null
  (begin
    (declare val-null MDB-val)
    (set val-null.mv-size 0))
  db-mdb-declare-val-graph-data
  (begin
    (declare val-graph-data MDB-val)
    (set val-graph-data.mv-size db-size-graph-data))
  db-mdb-declare-val-graph-key
  (begin
    (declare val-graph-key MDB-val)
    (set val-graph-key.mv-size db-size-graph-key))
  db-mdb-reset-val-null (set val-null.mv-size 0))

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