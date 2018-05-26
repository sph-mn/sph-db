#define db_graph_key_equal_p(a, b) \
  (db_id_equal_p((*(a + 0)), (*(b + 0))) && \
    db_id_equal_p((*(a + 1)), (*(b + 1))))
#define db_graph_data_ordinal_set(graph_data, value) \
  (*(((db_ordinal_t*)(graph_data)) + 0)) = value
#define db_graph_data_id_set(graph_data, value) \
  (*(((db_id_t*)((1 + ((db_ordinal_t*)(graph_data))))) + 0)) = value
#define db_define_graph_key(name) db_id_t name[2] = { 0, 0 }
#define db_define_graph_data(name) \
  b8 name[(db_size_ordinal + db_size_id)]; \
  memset(name, 0, (db_size_ordinal + db_size_id))
#define db_define_graph_record(name) db_graph_record_t name = { 0, 0, 0, 0 }
#define db_graph_records_add_x(target, record, target_temp) \
  db_pointer_allocation_set( \
    target, db_graph_records_add(target, record), target_temp)
/** search data until the given id-right has been found */
status_t db_mdb_graph_lr_seek_right(MDB_cursor* graph_lr, db_id_t id_right) {
  status_init;
  db_mdb_declare_val_graph_key;
  db_mdb_declare_val_graph_data;
  db_mdb_cursor_get_norequire(
    graph_lr, val_graph_key, val_graph_data, MDB_GET_CURRENT);
each_data:
  if (db_mdb_status_success_p) {
    if ((id_right == db_mdb_val_graph_data_to_id(val_graph_data))) {
      return (status);
    } else {
      db_mdb_cursor_next_dup_x(graph_lr, val_graph_key, val_graph_data);
      goto each_data;
    };
  } else {
    db_mdb_status_require_notfound;
  };
exit:
  return (status);
};
status_t db_graph_ensure(db_txn_t txn,
  db_ids_t* left,
  db_ids_t* right,
  db_ids_t* label,
  db_graph_ordinal_generator_t ordinal_generator,
  b0* ordinal_generator_state) {
  status_init;
  db_ids_t* right_pointer;
  db_ids_t* label_pointer;
  db_id_t id_left;
  db_id_t id_right;
  db_id_t id_label;
  db_ordinal_t ordinal = ((!ordinal_generator && ordinal_generator_state)
      ? (ordinal = (*((db_ordinal_t*)(ordinal_generator_state))))
      : 0);
  db_define_graph_key(graph_key);
  db_define_graph_data(graph_data);
  db_mdb_declare_val_id;
  db_mdb_declare_val_id_2;
  db_mdb_declare_val_graph_key;
  db_mdb_declare_val_graph_data;
  db_mdb_cursor_define_3(txn.mdb_txn,
    (*txn.s).dbi_graph_lr,
    graph_lr,
    (*txn.s).dbi_graph_rl,
    graph_rl,
    (*txn.s).dbi_graph_ll,
    graph_ll);
  while (left) {
    id_left = db_ids_first(left);
    label_pointer = label;
    while (label_pointer) {
      id_label = db_ids_first(label_pointer);
      right_pointer = right;
      val_id_2.mv_data = &id_label;
      while (right_pointer) {
        id_right = db_ids_first(right_pointer);
        (*(graph_key + 0)) = id_right;
        (*(graph_key + 1)) = id_label;
        val_graph_key.mv_data = graph_key;
        val_id.mv_data = &id_left;
        db_mdb_cursor_get_norequire(
          graph_rl, val_graph_key, val_id, MDB_GET_BOTH);
        if ((MDB_NOTFOUND == status.id)) {
          db_mdb_status_require_x(
            mdb_cursor_put(graph_rl, &val_graph_key, &val_id, 0));
          db_mdb_status_require_x(
            mdb_cursor_put(graph_ll, &val_id_2, &val_id, 0));
          (*(graph_key + 0)) = id_left;
          (*(graph_key + 1)) = id_label;
          if (ordinal_generator) {
            ordinal = (*ordinal_generator)(ordinal_generator_state);
          };
          db_graph_data_ordinal_set(graph_data, ordinal);
          db_graph_data_id_set(graph_data, id_right);
          val_graph_data.mv_data = graph_data;
          db_mdb_status_require_x(
            mdb_cursor_put(graph_lr, &val_graph_key, &val_graph_data, 0));
        } else {
          if (!db_mdb_status_success_p) {
            status_set_group_goto(db_status_group_lmdb);
          };
        };
        right_pointer = db_ids_rest(right_pointer);
      };
      label_pointer = db_ids_rest(label_pointer);
    };
    left = db_ids_rest(left);
  };
exit:
  db_mdb_cursor_close_3(graph_lr, graph_rl, graph_ll);
  return (status);
};
#include <graph-delete>
#include <graph-read>
#include <lib/debug-graph>
