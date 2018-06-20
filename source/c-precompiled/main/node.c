/** add relevant field data to all indices of a type */
status_t db_node_update_indices(db_txn_t txn,
  db_type_t* type,
  db_node_value_t* values,
  db_id_t id) {
  status_init;
  db_mdb_declare_val_id;
  db_mdb_cursor_declare(node_index_cursor);
  b8* data;
  MDB_val val_data;
  size_t size;
  db_index_count_t i;
  db_index_t node_index;
  db_index_t* node_indices;
  db_index_count_t node_indices_len;
  size_t value_size;
  db_field_count_t fields_len;
  db_field_count_t fields_index;
  val_id.mv_data = &id;
  data = 0;
  node_indices_len = type->indices_len;
  node_indices = type->indices;
  for (i = 0; (i < node_indices_len); i = (1 + i)) {
    node_index = node_indices[i];
    /* calculate size */
    for (fields_index = 0; (fields_index < node_index.fields_len);
         fields_index = (1 + fields_index)) {
      size = (size + (values[(node_index.fields)[fields_index]]).size);
    };
    if ((txn.env)->maxkeysize < size) {
      status_set_both_goto(db_status_group_db, db_status_id_index_keysize);
    };
    /* prepare insert data */
    db_malloc(data, size);
    val_data.mv_data = data;
    for (fields_index = 0; (fields_index < fields_len);
         fields_index = (1 + fields_index)) {
      value_size = (values[(node_index.fields)[fields_index]]).size;
      memcpy(data, ((values[fields_index]).data), value_size);
      data = (value_size + data);
    };
    db_mdb_status_require_x(
      (mdb_cursor_open((txn.mdb_txn), (node_index.dbi), (&node_index_cursor))));
    db_mdb_cursor_put(node_index_cursor, val_data, val_id);
    db_mdb_status_require_x(
      mdb_cursor_put(node_index_cursor, (&val_data), (&val_id), 0));
    db_mdb_cursor_close(node_index_cursor);
    free_and_set_null(data);
  };
exit:
  db_mdb_cursor_close_if_active(node_index_cursor);
  if (data) {
    free(data);
  };
  return (status);
};
status_t db_node_values_to_data(db_type_t* type,
  db_node_value_t* values,
  b0** result,
  size_t* result_size) {
  status_init;
  db_field_count_t i;
  db_field_count_t field_count;
  b8 field_size;
  size_t size;
  b8* data;
  db_field_type_t field_type;
  field_count = type->fields_len;
  size = 0;
  /* prepare size information */
  for (i = 0; (i < field_count); i = (1 + i)) {
    if (i >= type->fields_fixed_count) {
      field_size = (values[i]).size;
    } else {
      field_type = ((type->fields)[i]).type;
      field_size = db_field_type_size(field_type);
      (values[i]).size = field_size;
    };
    if (field_size > db_data_len_max) {
      status_set_both_goto(db_status_group_db, db_status_id_data_length);
    };
    size = (field_size + size);
  };
  db_malloc(data, size);
  /* copy data */
  for (i = 0; (i < field_count); i = (1 + i)) {
    field_size = (values[i]).size;
    if (i >= type->fields_fixed_count) {
      *((db_data_len_t*)(data)) = field_size;
      data = (sizeof(db_data_len_t) + data);
    };
    memcpy(data, ((values[i]).data), field_size);
    data = (size + data);
  };
  *result = data;
  *result_size = size;
exit:
  return (status);
};
/** allocate memory for a new node values array */
status_t db_node_values_prepare(db_type_t* type, db_node_value_t** result) {
  status_init;
  db_node_value_t* a;
  db_malloc(a, (type->fields_len * sizeof(db_node_value_t)));
  *result = a;
exit:
  return (status);
};
/** set a value for a field in node values.
  size can be set to zero and is ignored for fixed length types */
b0 db_node_values_set(db_node_value_t* values,
  db_field_count_t field_index,
  b0* data,
  size_t size) {
  (values[field_index]).data = data;
  (values[field_index]).size = size;
};
status_t db_node_create(db_txn_t txn,
  db_type_t* type,
  db_node_value_t* values,
  db_id_t* result) {
  status_init;
  db_mdb_declare_val_id;
  db_mdb_cursor_declare(nodes);
  MDB_val val_data;
  db_id_t id;
  val_data.mv_data = 0;
  val_id.mv_data = &id;
  status_require_x((db_node_values_to_data(
    type, values, (&(val_data.mv_data)), (&(val_data.mv_size)))));
  db_mdb_cursor_open(txn, nodes);
  status_require_x((db_sequence_next((txn.env), (type->id), (&id))));
  db_mdb_status_require_x(mdb_cursor_put(nodes, (&val_id), (&val_data), 0));
  db_mdb_cursor_close(nodes);
  status_require_x(db_node_update_indices(txn, type, values, id));
  *result = id;
exit:
  db_mdb_cursor_close_if_active(nodes);
  free((val_data.mv_data));
  return (status);
};
/** declare because it is defined later */
status_t db_graph_internal_delete(db_ids_t* left,
  db_ids_t* right,
  db_ids_t* label,
  db_ordinal_condition_t* ordinal,
  MDB_cursor* graph_lr,
  MDB_cursor* graph_rl,
  MDB_cursor* graph_ll);