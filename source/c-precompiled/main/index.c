#define db_index_errors_data_log(message, type, id) \
  db_error_log("(groups index %s) (description %s) (id %lu)", type, message, id)
status_t db_node_data_to_values(db_type_t* type,
  void* data,
  size_t data_size,
  db_node_values_t* result);
void db_free_node_values(db_node_values_t* values);
db_index_t* db_index_get(db_type_t* type,
  db_fields_len_t* fields,
  db_fields_len_t fields_len) {
  db_indices_len_t indices_len;
  db_indices_len_t index;
  db_index_t* indices;
  indices = type->indices;
  indices_len = type->indices_len;
  for (index = 0; (index < indices_len); index = (1 + index)) {
    if (0 ==
      memcmp(
        ((indices[index]).fields), fields, (sizeof(db_field_t) * fields_len))) {
      return ((index + indices));
    };
  };
  return (0);
};
status_t db_index_system_key(db_type_id_t type_id,
  db_fields_len_t* fields,
  db_fields_len_t fields_len,
  void** result_data,
  size_t* result_size) {
  status_declare;
  ui8* data;
  size_t size;
  size = (db_size_type_id + (sizeof(db_fields_len_t) * fields_len));
  db_malloc(data, size);
  *data = db_system_label_index;
  data = (1 + data);
  *((db_type_id_t*)(data)) = type_id;
  data = (sizeof(db_type_id_t) + data);
  memcpy(data, fields, fields_len);
exit:
  *result_data = data;
  *result_size = size;
  return (status);
};
/** create a string name from type-id and field offsets */
status_t db_index_name(db_type_id_t type_id,
  db_fields_len_t* fields,
  db_fields_len_t fields_len,
  ui8** result,
  size_t* result_size) {
  status_declare;
  db_fields_len_t i;
  ui8* str;
  ui8** strings;
  int strings_len;
  ui8* name;
  name = 0;
  strings_len = (1 + fields_len);
  strings = calloc(strings_len, sizeof(ui8*));
  if (!strings) {
    status_set_both(db_status_group_db, db_status_id_memory);
    return (status);
  };
  /* type id */
  str = uint_to_string(type_id);
  if (!str) {
    free(strings);
    status_set_both(db_status_group_db, db_status_id_memory);
    return (status);
  };
  *strings = str;
  /* field ids */
  for (i = 0; (i < fields_len); i = (1 + i)) {
    str = uint_to_string((fields[i]));
    if (!str) {
      goto exit;
    };
    strings[(1 + i)] = str;
  };
  name = string_join(strings, strings_len, "-", result_size);
  db_status_memory_error_if_null(name);
exit:
  while (i) {
    free((strings[i]));
    i = (i - 1);
  };
  free((strings[0]));
  free(strings);
  *result = name;
  return (status);
};
/** calculate size and prepare data */
status_t db_index_key(db_env_t* env,
  db_index_t index,
  db_node_values_t values,
  void** result_data,
  size_t* result_size) {
  status_declare;
  size_t value_size;
  ui8* data;
  db_fields_len_t i;
  size_t size;
  void* data_temp;
  for (i = 0; (i < index.fields_len); i = (1 + i)) {
    size = (size + ((values.data)[(index.fields)[i]]).size);
  };
  if (env->maxkeysize < size) {
    status_set_both_goto(db_status_group_db, db_status_id_index_keysize);
  };
  db_malloc(data, size);
  data_temp = data;
  for (i = 0; (i < index.fields_len); i = (1 + i)) {
    value_size = ((values.data)[(index.fields)[i]]).size;
    memcpy(data_temp, (((values.data)[i]).data), value_size);
    data_temp = (value_size + data_temp);
  };
  *result_data = data;
  *result_size = size;
exit:
  return (status);
};
/** fill one index from existing data */
status_t db_index_build(db_env_t* env, db_index_t* index) {
  status_declare;
  db_mdb_declare_val_id;
  db_txn_declare(env, txn);
  db_mdb_cursor_declare(nodes);
  db_mdb_cursor_declare(index_cursor);
  MDB_val val_data;
  void* data;
  db_id_t id;
  db_type_t type;
  ui8* name;
  db_node_values_t values;
  type = *(index->type);
  id = db_id_add_type(0, (type.id));
  val_id.mv_data = &id;
  db_txn_write_begin(txn);
  db_mdb_status_require(
    (mdb_cursor_open((txn.mdb_txn), (index->dbi), (&index_cursor))));
  db_mdb_status_require(db_mdb_env_cursor_open(txn, nodes));
  db_mdb_status_require(
    mdb_cursor_get(nodes, (&val_id), (&val_data), MDB_SET_KEY));
  /* for each node of type */
  while ((db_mdb_status_is_success &&
    (type.id == db_id_type((db_pointer_to_id((val_id.mv_data))))))) {
    status_require((db_node_data_to_values(
      (&type), (val_data.mv_data), (val_data.mv_size), (&values))));
    status_require(
      (db_index_key(env, (*index), values, (&data), (&(val_data.mv_size)))));
    val_data.mv_data = data;
    db_mdb_status_require(
      mdb_cursor_put(index_cursor, (&val_data), (&val_id), 0));
    free(data);
    db_free_node_values((&values));
    db_mdb_status_require(
      mdb_cursor_get(nodes, (&val_id), (&val_data), MDB_NEXT_NODUP));
  };
  if (!(db_mdb_status_is_success || db_mdb_status_is_notfound)) {
    goto exit;
  };
  db_txn_commit(txn);
exit:
  db_mdb_cursor_close_if_active(index_cursor);
  db_mdb_cursor_close_if_active(nodes);
  if (data) {
    free(data);
  };
  free(name);
  db_free_node_values((&values));
  db_txn_abort_if_active(txn);
  return (status);
};
status_t db_index_create(db_env_t* env,
  db_type_t* type,
  db_fields_len_t* fields,
  db_fields_len_t fields_len) {
  status_declare;
  db_mdb_declare_val_null;
  db_txn_declare(env, txn);
  db_mdb_cursor_declare(system);
  MDB_val val_data;
  ui8* name;
  size_t name_len;
  db_index_t* indices;
  db_index_t node_index;
  name = 0;
  val_data.mv_data = 0;
  /* check if already exists */
  indices = db_index_get(type, fields, fields_len);
  if (indices) {
    status_set_both_goto(db_status_group_db, db_status_id_duplicate);
  };
  /* prepare data */
  status_require((db_index_system_key((type->id),
    fields,
    fields_len,
    (&(val_data.mv_data)),
    (&(val_data.mv_size)))));
  status_require(
    (db_index_name((type->id), fields, fields_len, (&name), (&name_len))));
  /* add to system btree */
  db_txn_write_begin(txn);
  db_mdb_status_require(db_mdb_env_cursor_open(txn, system));
  db_mdb_status_require(mdb_cursor_put(system, (&val_data), (&val_null), 0));
  db_mdb_cursor_close(system);
  /* add data btree */
  db_mdb_status_require(
    (mdb_dbi_open((txn.mdb_txn), name, MDB_CREATE, (&(node_index.dbi)))));
  db_txn_commit(txn);
  /* update cache */
  db_realloc(
    (type->indices), indices, (sizeof(db_index_t) + type->indices_len));
  node_index = (type->indices)[type->indices_len];
  node_index.fields = fields;
  node_index.fields_len = fields_len;
  node_index.type = type;
  type->indices_len = (1 + type->indices_len);
  status_require(db_index_build(env, (&node_index)));
exit:
  db_mdb_cursor_close_if_active(system);
  db_txn_abort_if_active(txn);
  free(name);
  free((val_data.mv_data));
  return (status);
};
/** index must be a pointer into env:types:indices */
status_t db_index_delete(db_env_t* env, db_index_t* index) {
  status_declare;
  db_mdb_declare_val_null;
  db_txn_declare(env, txn);
  db_mdb_cursor_declare(system);
  MDB_val val_data;
  status_require((db_index_system_key((index->type->id),
    (index->fields),
    (index->fields_len),
    (&(val_data.mv_data)),
    (&(val_data.mv_size)))));
  db_txn_write_begin(txn);
  /* remove data btree */
  db_mdb_status_require((mdb_drop((txn.mdb_txn), (index->dbi), 1)));
  /* remove from system btree */
  db_mdb_status_require(db_mdb_env_cursor_open(txn, system));
  db_mdb_status_require(
    mdb_cursor_get(system, (&val_data), (&val_null), MDB_SET));
  if (db_mdb_status_is_success) {
    db_mdb_status_require(mdb_cursor_del(system, 0));
  } else {
    db_mdb_status_expect_notfound;
  };
  db_mdb_cursor_close(system);
  db_txn_commit(txn);
  /* update cache */
  free((index->fields));
  index->dbi = 0;
  index->fields = 0;
  index->fields_len = 0;
  index->type = 0;
exit:
  db_mdb_cursor_close_if_active(system);
  db_txn_abort_if_active(txn);
  return (status);
};
/** clear index and fill with relevant data from existing nodes */
status_t db_index_rebuild(db_env_t* env, db_index_t* index) {
  status_declare;
  db_txn_declare(env, txn);
  ui8* name;
  size_t name_len;
  name = 0;
  status_require((db_index_name((index->type->id),
    (index->fields),
    (index->fields_len),
    (&name),
    (&name_len))));
  db_txn_write_begin(txn);
  db_mdb_status_require((mdb_drop((txn.mdb_txn), (index->dbi), 0)));
  db_mdb_status_require(
    (mdb_dbi_open((txn.mdb_txn), name, MDB_CREATE, (&(index->dbi)))));
  db_txn_commit(txn);
exit:
  free(name);
  return (db_index_build(env, index));
};
/** create entries in all indices of type for id and values.
  index: field-data ... -> id */
status_t
db_indices_entry_ensure(db_txn_t txn, db_node_values_t values, db_id_t id) {
  status_declare;
  db_mdb_declare_val_id;
  db_mdb_cursor_declare(node_index_cursor);
  void* data;
  MDB_val val_data;
  db_indices_len_t i;
  db_index_t node_index;
  db_index_t* node_indices;
  db_indices_len_t node_indices_len;
  val_id.mv_data = &id;
  data = 0;
  node_indices_len = (values.type)->indices_len;
  node_indices = (values.type)->indices;
  for (i = 0; (i < node_indices_len); i = (1 + i)) {
    node_index = node_indices[i];
    status_require((db_index_key(
      (txn.env), node_index, values, (&data), (&(val_data.mv_size)))));
    val_data.mv_data = data;
    db_mdb_status_require(
      (mdb_cursor_open((txn.mdb_txn), (node_index.dbi), (&node_index_cursor))));
    db_mdb_status_require(
      mdb_cursor_put(node_index_cursor, (&val_data), (&val_id), 0));
    db_mdb_cursor_close(node_index_cursor);
  };
exit:
  db_mdb_cursor_close_if_active(node_index_cursor);
  if (data) {
    free(data);
  };
  return (status);
};
/** delete all entries from all indices of type for id and values */
status_t
db_indices_entry_delete(db_txn_t txn, db_node_values_t values, db_id_t id) {
  status_declare;
  db_mdb_declare_val_id;
  db_mdb_cursor_declare(node_index_cursor);
  ui8* data;
  MDB_val val_data;
  db_indices_len_t i;
  db_index_t node_index;
  db_index_t* node_indices;
  db_indices_len_t node_indices_len;
  val_id.mv_data = &id;
  data = 0;
  node_indices_len = (values.type)->indices_len;
  node_indices = (values.type)->indices;
  for (i = 0; (i < node_indices_len); i = (1 + i)) {
    node_index = node_indices[i];
    status_require((db_index_key((txn.env),
      node_index,
      values,
      ((void**)(&data)),
      (&(val_data.mv_size)))));
    val_data.mv_data = data;
    db_mdb_status_require(
      (mdb_cursor_open((txn.mdb_txn), (node_index.dbi), (&node_index_cursor))));
    db_mdb_status_require(
      mdb_cursor_put(node_index_cursor, (&val_data), (&val_id), 0));
    db_mdb_status_require(
      mdb_cursor_get(node_index_cursor, (&val_data), (&val_id), MDB_GET_BOTH));
    if (status_is_success) {
      db_mdb_status_require(mdb_cursor_del(node_index_cursor, 0));
    };
    db_mdb_cursor_close(node_index_cursor);
  };
exit:
  db_mdb_cursor_close_if_active(node_index_cursor);
  if (data) {
    free(data);
  };
  return (status);
};
/** fill index with relevant data from existing nodes */
status_t db_indices_build(db_env_t* env, db_index_t* index) {
  status_declare;
  db_mdb_declare_val_id;
  db_txn_declare(env, txn);
  db_mdb_cursor_declare(nodes);
  MDB_val val_data;
  db_id_t id;
  db_type_t type;
  db_node_values_t values;
  type = *(index->type);
  id = db_id_add_type(0, (type.id));
  val_id.mv_data = &id;
  db_txn_write_begin(txn);
  db_mdb_status_require(db_mdb_env_cursor_open(txn, nodes));
  status.id = mdb_cursor_get(nodes, (&val_id), (&val_data), MDB_SET_KEY);
  while ((db_mdb_status_is_success &&
    (type.id == db_id_type((db_pointer_to_id((val_id.mv_data))))))) {
    status_require((db_node_data_to_values(
      (&type), (val_data.mv_data), (val_data.mv_size), (&values))));
    status_require((db_indices_entry_ensure(
      txn, values, (db_pointer_to_id((val_id.mv_data))))));
    db_free_node_values((&values));
    status.id = mdb_cursor_get(nodes, (&val_id), (&val_data), MDB_NEXT_NODUP);
  };
  if (!db_mdb_status_is_success) {
    db_mdb_status_expect_notfound;
  };
  db_txn_commit(txn);
exit:
  db_mdb_cursor_close_if_active(nodes);
  db_txn_abort_if_active(txn);
  db_free_node_values((&values));
  return (status);
};
/** assumes that state is positioned at a matching key */
status_t db_index_next(db_index_selection_t* state) {
  status_declare;
  db_mdb_declare_val_null;
  db_mdb_declare_val_id;
  db_mdb_status_require(
    (mdb_cursor_get((state->cursor), (&val_null), (&val_id), MDB_NEXT_DUP)));
  state->current = db_pointer_to_id((val_id.mv_data));
exit:
  db_mdb_status_no_more_data_if_notfound;
  return (status);
};
void db_index_selection_destroy(db_index_selection_t* state) {
  if (state->cursor) {
    mdb_cursor_close((state->cursor));
  };
};
/** prepare the read state and get the first matching element or set status to
 * no-more-data */
status_t db_index_select(db_txn_t txn,
  db_index_t* index,
  db_node_values_t values,
  db_index_selection_t* result) {
  status_declare;
  db_mdb_declare_val_id;
  db_mdb_cursor_declare(cursor);
  void* data;
  MDB_val val_data;
  data = 0;
  status_require((
    db_index_key((txn.env), (*index), values, (&data), (&(val_data.mv_size)))));
  val_data.mv_data = data;
  db_mdb_status_require(
    (mdb_cursor_open((txn.mdb_txn), (index->dbi), (&cursor))));
  db_mdb_status_require(
    mdb_cursor_get(cursor, (&val_data), (&val_id), MDB_SET_KEY));
  result->current = db_pointer_to_id((val_id.mv_data));
  result->cursor = cursor;
exit:
  free(data);
  if (status_is_failure) {
    db_mdb_cursor_close_if_active(cursor);
    db_mdb_status_no_more_data_if_notfound;
  };
  return (status);
};