#include <string.h>
/* lmdb helpers */
#define db_mdb_status_is_notfound (MDB_NOTFOUND == status.id)
#define db_mdb_status_is_success (MDB_SUCCESS == status.id)
#define db_mdb_status_is_failure !db_mdb_status_is_success
#define db_mdb_status_notfound_if_notfound \
  if (db_mdb_status_is_notfound) { \
    status.group = db_status_group_db; \
    status.id = db_status_id_notfound; \
  }
#define db_mdb_status_success_if_notfound \
  if (db_mdb_status_is_notfound) { \
    status.id = status_id_success; \
  }
#define db_mdb_status_set_id_goto(id) \
  status.group = db_status_group_lmdb; \
  status.id = id
#define db_mdb_status_require(expression) \
  status.id = expression; \
  if (db_mdb_status_is_failure) { \
    status_set_group_goto(db_status_group_lmdb); \
  }
#define db_mdb_status_require_read(expression) \
  status.id = expression; \
  if (!(db_mdb_status_is_success || db_mdb_status_is_notfound)) { \
    status_set_group_goto(db_status_group_lmdb); \
  }
#define db_mdb_status_expect_notfound \
  if (!db_mdb_status_is_notfound) { \
    status_set_group_goto(db_status_group_lmdb); \
  }
#define db_mdb_status_expect_read \
  if (!(db_mdb_status_is_success || db_mdb_status_is_notfound)) { \
    status_set_group_goto(db_status_group_lmdb); \
  }
#define db_mdb_cursor_declare(name) MDB_cursor* name = 0
#define db_mdb_env_cursor_open(txn, name) \
  mdb_cursor_open((txn.mdb_txn), ((txn.env)->dbi_##name), (&name))
#define db_mdb_cursor_close(name) \
  mdb_cursor_close(name); \
  name = 0
#define db_mdb_cursor_close_if_active(a) \
  if (a) { \
    db_mdb_cursor_close(a); \
  }
#define db_mdb_cursor_each_key(cursor, val_key, val_value, body) \
  status.id = mdb_cursor_get(cursor, (&val_key), (&val_value), MDB_FIRST); \
  while (db_mdb_status_is_success) { \
    body; \
    status.id = \
      mdb_cursor_get(cursor, (&val_key), (&val_value), MDB_NEXT_NODUP); \
  }; \
  db_mdb_status_expect_notfound
#define db_mdb_val_to_graph_key(a) ((db_id_t*)(a.mv_data))
#define db_mdb_declare_val_id \
  MDB_val val_id; \
  val_id.mv_size = db_size_id;
#define db_mdb_declare_val_id_2 \
  MDB_val val_id_2; \
  val_id_2.mv_size = db_size_id;
#define db_mdb_declare_val_null \
  MDB_val val_null; \
  val_null.mv_size = 0;
#define db_mdb_declare_val_graph_data \
  MDB_val val_graph_data; \
  val_graph_data.mv_size = db_size_graph_data;
#define db_mdb_declare_val_graph_key \
  MDB_val val_graph_key; \
  val_graph_key.mv_size = db_size_graph_key;
#define db_mdb_reset_val_null val_null.mv_size = 0
/** mdb comparison routines are used by lmdb for search, insert and delete */
static int db_mdb_compare_id(const MDB_val* a, const MDB_val* b) {
  return ((db_id_compare(
    (db_pointer_to_id((a->mv_data))), (db_pointer_to_id((b->mv_data))))));
};
static int db_mdb_compare_graph_key(const MDB_val* a, const MDB_val* b) {
  if (db_pointer_to_id((a->mv_data)) < db_pointer_to_id((b->mv_data))) {
    return (-1);
  } else if (db_pointer_to_id((a->mv_data)) > db_pointer_to_id((b->mv_data))) {
    return (1);
  } else {
    return ((db_id_compare((db_pointer_to_id_at((a->mv_data), 1)),
      (db_pointer_to_id_at((b->mv_data), 1)))));
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