#define db_index_errors_graph_log(message, left, right, label) \
  db_error_log("(groups index graph) (description \"%s\") (left %lu) (right " \
               "%lu) (label %lu)", \
    message, \
    left, \
    right, \
    label)
#define db_index_errors_data_log(message, type, id) \
  db_error_log("(groups index %s) (description %s) (id %lu)", type, message, id)
status_t db_index_recreate_extern() {
  status_init;
  db_mdb_declare_val_id;
  db_mdb_declare_val_data;
  db_mdb_cursor_declare(nodes);
  db_mdb_cursor_declare(data_intern_to_id);
  db_txn_introduce;
  db_txn_write_begin;
  mdb_drop(db_txn, dbi_data_intern_to_id, 0);
  db_txn_commit;
  db_txn_write_begin;
  db_mdb_cursor_open(db_txn, nodes);
  db_mdb_cursor_open(db_txn, data_intern_to_id);
  db_mdb_cursor_each_key(nodes, val_id, val_data, ({
    if (val_data.mv_size && db_intern_p(db_mdb_val_to_id(val_id))) {
      db_mdb_status_require_x(
        mdb_cursor_put(data_intern_to_id, (&val_data), (&val_id), 0));
    };
  }));
  db_txn_commit;
exit:
  if (db_txn) {
    db_txn_abort;
  };
  return (status);
};