status_t db_debug_display_content_graph_lr(db_txn_t txn) {
  status_init;
  db_mdb_cursor_define(txn.mdb_txn, txn.env->dbi_graph_lr, graph_lr);
  db_id_t id_left;
  db_id_t id_right;
  db_id_t id_label;
  db_ordinal_t ordinal;
  db_mdb_declare_val_graph_key;
  db_mdb_declare_val_graph_data;
  printf("graph-lr\n");
  db_mdb_cursor_each_key(graph_lr, val_graph_key, val_graph_data, {
    id_left = db_mdb_val_to_id_at(val_graph_key, 0);
    id_label = db_mdb_val_to_id_at(val_graph_key, 1);
    do {
      id_right = db_mdb_val_graph_data_to_id(val_graph_data);
      ordinal = db_mdb_val_graph_data_to_ordinal(val_graph_data);
      printf("  (%lu %lu) (%lu %lu)\n", id_left, id_label, ordinal, id_right);
      db_mdb_cursor_next_dup_x(graph_lr, val_graph_key, val_graph_data);
    } while (db_mdb_status_success_p);
  });
exit:
  mdb_cursor_close(graph_lr);
  db_status_success_if_mdb_notfound;
  return (status);
};
status_t db_debug_display_content_graph_rl(db_txn_t txn) {
  status_init;
  db_id_t id_left;
  db_id_t id_right;
  db_id_t id_label;
  db_mdb_declare_val_graph_key;
  db_mdb_declare_val_id;
  db_mdb_cursor_define(txn.mdb_txn, txn.env->dbi_graph_rl, graph_rl);
  printf("graph-rl\n");
  db_mdb_cursor_each_key(graph_rl, val_graph_key, val_id, {
    id_right = db_mdb_val_to_id_at(val_graph_key, 0);
    id_label = db_mdb_val_to_id_at(val_graph_key, 1);
    do {
      id_left = db_mdb_val_to_id(val_id);
      printf("  (%lu %lu) %lu\n", id_right, id_label, id_left);
      db_mdb_cursor_next_dup_x(graph_rl, val_graph_key, val_id);
    } while (db_mdb_status_success_p);
  });
exit:
  mdb_cursor_close(graph_rl);
  db_status_success_if_mdb_notfound;
  return (status);
};