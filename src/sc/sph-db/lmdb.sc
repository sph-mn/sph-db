(pre-include "string.h")
(sc-comment "lmdb helpers")

(pre-define
  (db-id-compare a b) (if* (< a b) -1 (> a b))
  db-mdb-status-is-notfound (= MDB-NOTFOUND status.id)
  db-mdb-status-is-success (= MDB-SUCCESS status.id)
  db-mdb-status-is-failure (not db-mdb-status-is-success)
  db-mdb-status-notfound-if-notfound
  (if db-mdb-status-is-notfound
    (set status.group db-status-group-db status.id db-status-id-notfound))
  db-mdb-status-success-if-notfound (if db-mdb-status-is-notfound (set status.id status-id-success))
  (db-mdb-status-set-id-goto id) (status-set-goto db-status-group-lmdb id)
  (db-mdb-status-require expression)
  (begin
    (set status.id expression)
    (if db-mdb-status-is-failure (begin (set status.group db-status-group-lmdb) (goto exit))))
  (db-mdb-status-require-read expression)
  (begin
    (set status.id expression)
    (if (not (or db-mdb-status-is-success db-mdb-status-is-notfound))
      (begin (set status-group db-status-group-lmdb) (goto exit))))
  db-mdb-status-expect-notfound
  (if (not db-mdb-status-is-notfound) (begin (set status.group db-status-group-lmdb) (goto exit)))
  db-mdb-status-expect-read
  (if (not (or db-mdb-status-is-success db-mdb-status-is-notfound))
    (begin (set status.group db-status-group-lmdb) (goto exit)))
  (db-mdb-cursor-declare name) (define name MDB-cursor* 0)
  (db-mdb-env-cursor-open txn name)
  (mdb-cursor-open txn.mdb-txn (: txn.env (pre-concat dbi- name)) &name)
  (db-mdb-cursor-close name) (begin (mdb-cursor-close name) (set name 0))
  (db-mdb-cursor-close-if-active a) (if a (db-mdb-cursor-close a))
  (db-mdb-val->relation-key a) (convert-type a.mv-data db-id-t*)
  db-mdb-declare-val-id (begin (declare val-id MDB-val) (set val-id.mv-size (sizeof db-id-t)))
  db-mdb-declare-val-id-2 (begin (declare val-id-2 MDB-val) (set val-id-2.mv-size (sizeof db-id-t)))
  db-mdb-declare-val-null (begin (declare val-null MDB-val) (set val-null.mv-size 0))
  db-mdb-declare-val-relation-data
  (begin (declare val-relation-data MDB-val) (set val-relation-data.mv-size db-size-relation-data))
  db-mdb-declare-val-relation-key
  (begin (declare val-relation-key MDB-val) (set val-relation-key.mv-size db-size-relation-key))
  db-mdb-reset-val-null (set val-null.mv-size 0))

(define (db-mdb-compare-id a b) (int (const MDB-val*) (const MDB-val*))
  "mdb comparison routines are used by lmdb for search, insert and delete"
  (return (db-id-compare (db-pointer->id a:mv-data) (db-pointer->id b:mv-data))))

(define (db-mdb-compare-relation-key a b) (int (const MDB-val*) (const MDB-val*))
  (cond
    ((< (db-pointer->id a:mv-data) (db-pointer->id b:mv-data)) (return -1))
    ((> (db-pointer->id a:mv-data) (db-pointer->id b:mv-data)) (return 1))
    (else (return (db-id-compare (db-pointer->id-at a:mv-data 1) (db-pointer->id-at b:mv-data 1))))))

(define (db-mdb-compare-relation-data a b) (int (const MDB-val*) (const MDB-val*))
  "memcmp does not work here, gives -1 for 256 vs 1"
  (cond
    ((< (db-relation-data->ordinal a:mv-data) (db-relation-data->ordinal b:mv-data)) (return -1))
    ((> (db-relation-data->ordinal a:mv-data) (db-relation-data->ordinal b:mv-data)) (return 1))
    (else
      (return (db-id-compare (db-relation-data->id a:mv-data) (db-relation-data->id b:mv-data))))))
