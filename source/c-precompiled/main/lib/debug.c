/* development helpers */
b0 db_debug_log_ids(db_ids_t* a) {
  while (a) {
    debug_log("%lu", db_ids_first(a));
    a = db_ids_rest(a);
  };
};
b0 db_debug_log_ids_set(imht_set_t a) {
  b32 index = 0;
  while ((index < a.size)) {
    debug_log("%lu", (*(a.content + index)));
    index = (1 + index);
  };
};
b0 db_debug_display_graph_records(db_graph_records_t* records) {
  db_graph_record_t record;
  printf("graph records\n");
  while (records) {
    record = db_graph_records_first(records);
    printf("  lcor %lu %lu %lu %lu\n",
      record.left,
      record.label,
      record.ordinal,
      record.right);
    records = db_graph_records_rest(records);
  };
};
status_t db_debug_count_all_btree_entries(db_txn_t txn, b32* result) {
  status_init;
  db_statistics_t stat;
  status_require_x(db_statistics(txn, &stat));
  (*result) = (stat.system.ms_entries + stat.id_to_data.ms_entries +
    stat.left_to_right.ms_entries + stat.right_to_left.ms_entries +
    stat.label_to_left.ms_entries);
exit:
  return (status);
};
status_t db_debug_display_btree_counts(db_txn_t txn) {
  status_init;
  db_statistics_t stat;
  status_require_x(db_statistics(txn, &stat));
  printf(
    "btree entry count\n  id->data %d data-intern->id %d\n  "
    "data-extern->extern %d left->right %d\n  right->left %d label->left %d\n",
    stat.system.ms_entries,
    stat.id_to_data.ms_entries,
    stat.left_to_right.ms_entries,
    stat.right_to_left.ms_entries,
    stat.label_to_left.ms_entries);
exit:
  return (status);
};