status_t db_debug_display_content_left_to_right(db_txn_t txn) {
  status_init;
  db_mdb_cursor_define(txn.mdb_txn, (*txn.s).dbi_left_to_right, left_to_right);
  db_id_t id_left;
  db_id_t id_right;
  db_id_t id_label;
  db_ordinal_t ordinal;
  db_mdb_declare_val_graph_key;
  db_mdb_declare_val_graph_data;
  printf("left->right\n");
  db_mdb_cursor_each_key(left_to_right, val_graph_key, val_graph_data, {
    id_left = db_mdb_val_to_id_at(val_graph_key, 0);
    id_label = db_mdb_val_to_id_at(val_graph_key, 1);
    do {
      id_right = db_mdb_val_graph_data_to_id(val_graph_data);
      ordinal = db_mdb_val_graph_data_to_ordinal(val_graph_data);
      printf("  (%lu %lu) (%lu %lu)\n", id_left, id_label, ordinal, id_right);
      db_mdb_cursor_next_dup_x(left_to_right, val_graph_key, val_graph_data);
    } while (db_mdb_status_success_p);
  });
exit:
  mdb_cursor_close(left_to_right);
  db_status_success_if_mdb_notfound;
  return (status);
};
status_t db_debug_display_content_right_to_left(db_txn_t txn) {
  status_init;
  db_mdb_cursor_define(txn.mdb_txn, (*txn.s).dbi_right_to_left, right_to_left);
  db_id_t id_left;
  db_id_t id_right;
  db_id_t id_label;
  db_mdb_declare_val_graph_key;
  db_mdb_declare_val_id;
  printf("right->left\n");
  db_mdb_cursor_each_key(right_to_left, val_graph_key, val_id, {
    id_right = db_mdb_val_to_id_at(val_graph_key, 0);
    id_label = db_mdb_val_to_id_at(val_graph_key, 1);
    do {
      id_left = db_mdb_val_to_id(val_id);
      printf("  (%lu %lu) %lu\n", id_right, id_label, id_left);
      db_mdb_cursor_next_dup_x(right_to_left, val_graph_key, val_id);
    } while (db_mdb_status_success_p);
  });
exit:
  mdb_cursor_close(right_to_left);
  db_status_success_if_mdb_notfound;
  return (status);
};