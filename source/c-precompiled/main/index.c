status_t db_node_data_to_values(db_type_t* type,
  db_node_data_t data,
  db_node_values_t* result);
void db_free_node_values(db_node_values_t* values);
/** label-type type-id field ... */
status_t db_index_system_key(db_type_id_t type_id,
  db_fields_len_t* fields,
  db_fields_len_t fields_len,
  void** result_data,
  size_t* result_size) {
  status_declare;
  ui8* data;
  ui8* data_temp;
  size_t size;
  /* system-label + type + fields */
  size = (1 + sizeof(db_type_id_t) + (sizeof(db_fields_len_t) * fields_len));
  db_malloc(data, size);
  *data = db_system_label_index;
  data_temp = (1 + data);
  *((db_type_id_t*)(data_temp)) = type_id;
  data_temp = (sizeof(db_type_id_t) + data_temp);
  memcpy(data_temp, fields, (sizeof(db_fields_len_t) * fields_len));
  *result_data = data;
  *result_size = size;
exit:
  return (status);
};
/** create a string name from type-id and field offsets.
  i-{type-id}-{field-offset}-{field-offset}... */
status_t db_index_name(db_type_id_t type_id,
  db_fields_len_t* fields,
  db_fields_len_t fields_len,
  ui8** result,
  size_t* result_len) {
  status_declare;
  db_fields_len_t i;
  ui8* str;
  size_t name_len;
  size_t str_len;
  ui8** strings;
  int strings_len;
  ui8* name;
  ui8* prefix = "i";
  strings = 0;
  strings_len = (2 + fields_len);
  db_calloc(strings, strings_len, sizeof(ui8*));
  /* type id */
  str = uint_to_string(type_id, (&str_len));
  db_status_memory_error_if_null(str);
  strings[0] = prefix;
  strings[1] = str;
  /* field ids */
  for (i = 0; (i < fields_len); i = (1 + i)) {
    str = uint_to_string((fields[i]), (&str_len));
    db_status_memory_error_if_null(str);
    strings[(2 + i)] = str;
  };
  name = string_join(strings, strings_len, "-", (&name_len));
  db_status_memory_error_if_null(name);
  *result = name;
  *result_len = name_len;
exit:
  if (strings) {
    /* dont free string[0] because it is the stack allocated prefix */
    for (i = 1; (i < strings_len); i = (1 + i)) {
      free((strings[i]));
    };
    free(strings);
  };
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
  void* data;
  db_fields_len_t i;
  size_t size;
  ui8* data_temp;
  size = 0;
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
    memcpy(data_temp, (((values.data)[(index.fields)[i]]).data), value_size);
    data_temp = (value_size + data_temp);
  };
  *result_data = data;
  *result_size = size;
exit:
  return (status);
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
    if (!node_index.fields_len) {
      continue;
    };
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
  free(data);
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
    if (!node_index.fields_len) {
      continue;
    };
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
  free(data);
  return (status);
};
/** fill one index from existing data */
status_t db_index_build(db_env_t* env, db_index_t index) {
  status_declare;
  db_mdb_declare_val_id;
  db_txn_declare(env, txn);
  db_mdb_cursor_declare(nodes);
  db_mdb_cursor_declare(index_cursor);
  MDB_val val_data;
  void* data;
  db_id_t id;
  db_type_t type;
  db_node_data_t node_data;
  db_node_values_t values;
  values.data = 0;
  data = 0;
  type = *(index.type);
  id = db_id_add_type(0, (type.id));
  val_id.mv_data = &id;
  status_require(db_txn_write_begin((&txn)));
  db_mdb_status_require(
    (mdb_cursor_open((txn.mdb_txn), (index.dbi), (&index_cursor))));
  db_mdb_status_require(db_mdb_env_cursor_open(txn, nodes));
  db_mdb_status_require(
    mdb_cursor_get(nodes, (&val_id), (&val_data), MDB_SET_RANGE));
  /* for each node of type */
  while ((db_mdb_status_is_success &&
    (type.id == db_id_type((db_pointer_to_id((val_id.mv_data))))))) {
    node_data.data = val_data.mv_data;
    node_data.size = val_data.mv_size;
    status_require(db_node_data_to_values((&type), node_data, (&values)));
    status_require(
      (db_index_key(env, index, values, (&data), (&(val_data.mv_size)))));
    db_free_node_values((&values));
    val_data.mv_data = data;
    db_mdb_status_require(
      mdb_cursor_put(index_cursor, (&val_data), (&val_id), 0));
    status.id = mdb_cursor_get(nodes, (&val_id), (&val_data), MDB_NEXT_NODUP);
  };
  db_mdb_status_expect_read;
  status_require(db_txn_commit((&txn)));
exit:
  db_mdb_cursor_close_if_active(index_cursor);
  db_mdb_cursor_close_if_active(nodes);
  db_txn_abort_if_active(txn);
  db_free_node_values((&values));
  free(data);
  db_mdb_status_success_if_notfound;
  return (status);
};
/** if found returns a pointer to an index struct in the cache array, zero
 * otherwise */
db_index_t* db_index_get(db_type_t* type,
  db_fields_len_t* fields,
  db_fields_len_t fields_len) {
  db_indices_len_t indices_len;
  db_indices_len_t index;
  db_index_t* indices;
  indices = type->indices;
  indices_len = type->indices_len;
  for (index = 0; (index < indices_len); index = (1 + index)) {
    if ((indices[index]).fields_len &&
      (0 ==
        memcmp(((indices[index]).fields),
          fields,
          (fields_len * sizeof(db_fields_len_t))))) {
      return ((index + indices));
    };
  };
  return (0);
};
/** eventually resize type:indices and add index to type:indices.
  indices is extended and elements are set to zero on deletion.
  indices is currently never downsized, but a re-open of the db-env
  reallocates it in appropriate size (and invalidates all db-index-t pointers)
*/
status_t db_type_indices_add(db_type_t* type, db_index_t index) {
  status_declare;
  db_index_t* indices_temp;
  db_indices_len_t indices_len;
  db_index_t* indices;
  db_indices_len_t i;
  indices = type->indices;
  indices_len = type->indices_len;
  /* search unset index */
  for (i = 0; (i < indices_len); i = (1 + i)) {
    if (!(indices[i]).fields_len) {
      break;
    };
  };
  if (i < indices_len) {
    indices[i] = index;
    goto exit;
  };
  /* reallocate */
  indices_len = (1 + indices_len);
  db_realloc(indices, indices_temp, (indices_len * sizeof(db_index_t)));
  indices[(indices_len - 1)] = index;
  type->indices = indices;
  type->indices_len = indices_len;
exit:
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
  db_fields_len_t* fields_copy;
  void* data;
  size_t size;
  ui8* name;
  size_t name_len;
  db_index_t* indices_temp;
  db_index_t node_index;
  if (!fields_len) {
    status.id = db_status_id_invalid_argument;
    return (status);
  };
  fields_copy = 0;
  name = 0;
  data = 0;
  size = 0;
  /* check if already exists */
  indices_temp = db_index_get(type, fields, fields_len);
  if (indices_temp) {
    status_set_both_goto(db_status_group_db, db_status_id_duplicate);
  };
  /* prepare data */
  status_require(
    (db_index_system_key((type->id), fields, fields_len, (&data), (&size))));
  status_require(
    (db_index_name((type->id), fields, fields_len, (&name), (&name_len))));
  /* add to system btree */
  val_data.mv_data = data;
  val_data.mv_size = size;
  status_require(db_txn_write_begin((&txn)));
  db_mdb_status_require(db_mdb_env_cursor_open(txn, system));
  db_mdb_status_require(mdb_cursor_put(system, (&val_data), (&val_null), 0));
  db_mdb_cursor_close(system);
  /* add data btree */
  db_mdb_status_require(
    (mdb_dbi_open((txn.mdb_txn), name, MDB_CREATE, (&(node_index.dbi)))));
  /* update cache. fields might be stack allocated */
  db_malloc(fields_copy, (fields_len * sizeof(db_fields_len_t)));
  memcpy(fields_copy, fields, (fields_len * sizeof(db_fields_len_t)));
  node_index.fields = fields_copy;
  node_index.fields_len = fields_len;
  node_index.type = type;
  status_require(db_type_indices_add(type, node_index));
  status_require(db_txn_commit((&txn)));
  status_require(db_index_build(env, node_index));
exit:
  db_mdb_cursor_close_if_active(system);
  db_txn_abort_if_active(txn);
  if (status_is_failure) {
    free(fields_copy);
  };
  free(name);
  free(data);
  return (status);
};
/** index must be a pointer into env:types:indices.
  the cache entry struct has its fields set to zero */
status_t db_index_delete(db_env_t* env, db_index_t* index) {
  status_declare;
  db_mdb_declare_val_null;
  db_txn_declare(env, txn);
  db_mdb_cursor_declare(system);
  db_index_t* indices_temp;
  void* key_data;
  size_t key_size;
  MDB_val val_data;
  status_require((db_index_system_key((index->type->id),
    (index->fields),
    (index->fields_len),
    (&key_data),
    (&key_size))));
  val_data.mv_data = key_data;
  val_data.mv_size = key_size;
  status_require(db_txn_write_begin((&txn)));
  /* remove data btree. closes the handle */
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
  status_require(db_txn_commit((&txn)));
  /* update cache */
  free_and_set_null((index->fields));
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
  status_require(db_txn_write_begin((&txn)));
  db_mdb_status_require((mdb_drop((txn.mdb_txn), (index->dbi), 0)));
  db_mdb_status_require(
    (mdb_dbi_open((txn.mdb_txn), name, MDB_CREATE, (&(index->dbi)))));
  status_require(db_txn_commit((&txn)));
exit:
  free(name);
  if (status_is_success) {
    return (db_index_build(env, (*index)));
  } else {
    db_txn_abort_if_active(txn);
  };
};
/** position at the next index value.
  if no value is found, status is db-notfound.
  before call, state must be positioned at a matching key */
status_t db_index_next(db_index_selection_t* state) {
  status_declare;
  db_mdb_declare_val_null;
  db_mdb_declare_val_id;
  db_mdb_status_require(
    (mdb_cursor_get((state->cursor), (&val_null), (&val_id), MDB_NEXT_DUP)));
  state->current = db_pointer_to_id((val_id.mv_data));
exit:
  db_mdb_status_notfound_if_notfound;
  return (status);
};
void db_index_selection_destroy(db_index_selection_t* state) {
  db_mdb_cursor_close_if_active((state->cursor));
};
/** open the cursor and set to the index key matching values.
  if no match found status is db-notfound */
status_t db_index_select(db_txn_t txn,
  db_index_t index,
  db_node_values_t values,
  db_index_selection_t* result) {
  status_declare;
  db_mdb_declare_val_id;
  db_mdb_cursor_declare(cursor);
  void* data;
  MDB_val val_data;
  data = 0;
  status_require(
    (db_index_key((txn.env), index, values, (&data), (&(val_data.mv_size)))));
  val_data.mv_data = data;
  db_mdb_status_require(
    (mdb_cursor_open((txn.mdb_txn), (index.dbi), (&cursor))));
  db_mdb_status_require(
    mdb_cursor_get(cursor, (&val_data), (&val_id), MDB_SET_KEY));
  result->current = db_pointer_to_id((val_id.mv_data));
  result->cursor = cursor;
exit:
  free(data);
  if (status_is_failure) {
    db_mdb_cursor_close_if_active(cursor);
    db_mdb_status_notfound_if_notfound;
  };
  return (status);
};