#include <string.h>
#define db_mdb_txn_declare(name) MDB_txn* name = 0
#define db_mdb_txn_begin(env, mdb_txn) \
  db_mdb_status_require_x(mdb_txn_begin(env, 0, MDB_RDONLY, (&mdb_txn)))
#define db_mdb_txn_write_begin(env, mdb_txn) \
  db_mdb_status_require_x(mdb_txn_begin(env, 0, 0, (&mdb_txn)))
#define db_mdb_txn_abort(a) \
  mdb_txn_abort(a); \
  a = 0
#define db_mdb_txn_commit(a) \
  db_mdb_status_require_x(mdb_txn_commit(a)); \
  a = 0
#define db_mdb_cursor_declare(name) MDB_cursor* name = 0
#define db_mdb_cursor_declare_two(name_a, name_b) \
  db_mdb_cursor_declare(name_a); \
  db_mdb_cursor_declare(name_b)
#define db_mdb_cursor_declare_three(name_a, name_b, name_c) \
  db_mdb_cursor_declare_two(name_a, name_b); \
  db_mdb_cursor_declare(name_c)
#define db_mdb_cursor_close_two(a, b) \
  mdb_cursor_close(a); \
  mdb_cursor_close(b)
#define db_mdb_cursor_close_three(a, b, c) \
  db_mdb_cursor_close_two(a, b); \
  mdb_cursor_close(c)
/** only updates status, no goto on error */
#define db_mdb_cursor_get_norequire(cursor, val_a, val_b, cursor_operation) \
  status_set_id(mdb_cursor_get(cursor, (&val_a), (&val_b), cursor_operation))
#define db_mdb_cursor_next_dup_norequire(cursor, val_a, val_b) \
  db_mdb_cursor_get_norequire(cursor, val_a, val_b, MDB_NEXT_DUP)
#define db_mdb_cursor_next_nodup_norequire(cursor, val_a, val_b) \
  db_mdb_cursor_get_norequire(cursor, val_a, val_b, MDB_NEXT_NODUP)
#define db_mdb_cursor_del_norequire(cursor, flags) \
  status_set_id(mdb_cursor_del(cursor, flags))
#define db_mdb_cursor_get(cursor, val_a, val_b, cursor_operation) \
  db_mdb_status_require_x( \
    mdb_cursor_get(cursor, (&val_a), (&val_b), cursor_operation))
#define db_mdb_cursor_put(cursor, val_a, val_b) \
  db_mdb_status_require_x(mdb_cursor_put(cursor, (&val_a), (&val_b), 0))
#define db_mdb_put(txn, dbi, val_a, val_b) \
  db_mdb_status_require_x(mdb_put(dbi, (&val_a), (&val_b), 0))
#define db_mdb_cursor_open(txn, dbi, name) \
  db_mdb_status_require_x(mdb_cursor_open(txn, dbi, (&name)))
#define db_mdb_cursor_open_two(txn, dbi_a, name_a, dbi_b, name_b) \
  db_mdb_cursor_open(txn, dbi_a, name_a); \
  db_mdb_cursor_open(txn, dbi_b, name_b)
#define db_mdb_cursor_open_three( \
  txn, dbi_a, name_a, dbi_b, name_b, dbi_c, name_c) \
  db_mdb_cursor_open_two(txn, dbi_a, name_a, dbi_b, name_b); \
  db_mdb_cursor_open(txn, dbi_c, name_c)
#define db_mdb_val_to_id(a) db_pointer_to_id((a.mv_data), 0)
#define db_mdb_declare_val(name, size) \
  MDB_val name; \
  name.mv_size = size
#define db_mdb_declare_val_id db_mdb_declare_val(val_id, db_size_id)
#define db_mdb_declare_val_id_2 db_mdb_declare_val(val_id_2, db_size_id)
#define db_mdb_declare_val_id_3 db_mdb_declare_val(val_id_3, db_size_id)
#define db_mdb_declare_val_null db_mdb_declare_val(val_null, 0)
#define db_mdb_declare_val_graph_data \
  db_mdb_declare_val(val_graph_data, db_size_graph_data)
#define db_mdb_declare_val_graph_key \
  db_mdb_declare_val(val_graph_key, db_size_graph_key)
#define db_mdb_reset_val_null val_null.mv_size = 0
#define db_mdb_cursor_each_key(cursor, val_key, val_value, body) \
  db_mdb_cursor_get_norequire(cursor, val_key, val_value, MDB_FIRST); \
  while (db_mdb_status_success_p) { \
    body; \
    db_mdb_cursor_next_nodup_norequire(cursor, val_key, val_value); \
  }; \
  db_mdb_status_require_notfound
#define db_mdb_cursor_set_first_x(cursor) \
  db_mdb_status_require_x( \
    mdb_cursor_get(cursor, (&val_null), (&val_null), MDB_FIRST))
#define db_mdb_val_to_graph_key(a) ((db_id_t*)(a.mv_data))
/** mdb comparison routines are used by lmdb for search, insert and delete */
static int db_mdb_compare_id(const MDB_val* a, const MDB_val* b) {
  return ((db_id_compare(
    (db_pointer_to_id((a->mv_data), 0)), (db_pointer_to_id((b->mv_data), 0)))));
};
static int db_mdb_compare_graph_key(const MDB_val* a, const MDB_val* b) {
  if (db_pointer_to_id((a->mv_data), 0) < db_pointer_to_id((b->mv_data), 0)) {
    return (-1);
  } else if (db_pointer_to_id((a->mv_data), 0) >
    db_pointer_to_id((b->mv_data), 0)) {
    return (1);
  } else {
    return ((db_id_compare((db_pointer_to_id((a->mv_data), 1)),
      (db_pointer_to_id((b->mv_data), 1)))));
  };
};
/** memcmp does not work here, gives -1 for 256 vs 1 */
static int db_mdb_compare_graph_data(const MDB_val* a, const MDB_val* b) {
  if (db_graph_data_to_ordinal((a->mv_data)) <
    db_graph_data_to_ordinal((b->mv_data))) {
    return (-1);
  } else if (db_graph_data_to_ordinal((a->mv_data)) >
    db_graph_data_to_ordinal((b->mv_data))) {
    return (1);
  } else {
    return ((db_id_compare((db_graph_data_to_id((a->mv_data))),
      (db_graph_data_to_id((b->mv_data))))));
  };
};
static int db_mdb_compare_data(const MDB_val* a, const MDB_val* b) {
  ssize_t length_difference =
    (((ssize_t)(a->mv_size)) - ((ssize_t)(b->mv_size)));
  return (
    (length_difference ? ((length_difference < 0) ? -1 : 1)
                       : memcmp((a->mv_data), (b->mv_data), (a->mv_size))));
};