status_t db_record_data_to_values(db_type_t* type, db_record_t data, db_record_values_t* result);
void db_free_record_values(db_record_values_t* values);
/** create a key for an index to be used in the system btree.
   key-format: system-label-type type-id indexed-field-offset ... */
status_t db_index_system_key(db_type_id_t type_id, db_fields_len_t* fields, db_fields_len_t fields_len, void** result_data, size_t* result_size) {
  status_declare;
  uint8_t* data;
  uint8_t* data_temp;
  size_t size;
  /* system-label + type + fields */
  size = (1 + sizeof(db_type_id_t) + (sizeof(db_fields_len_t) * fields_len));
  status_require((sph_helper_malloc(size, (&data))));
  *data = db_system_label_index;
  data_temp = (1 + data);
  *((db_type_id_t*)(data_temp)) = type_id;
  data_temp = (sizeof(db_type_id_t) + data_temp);
  memcpy(data_temp, fields, (sizeof(db_fields_len_t) * fields_len));
  *result_data = data;
  *result_size = size;
exit:
  return (status);
}
/** create a string name from type-id and field offsets.
  i-{type-id}-{field-offset}-{field-offset}... */
status_t db_index_name(db_type_id_t type_id, db_fields_len_t* fields, db_fields_len_t fields_len, uint8_t** result, size_t* result_len) {
  status_declare;
  db_fields_len_t i;
  uint8_t* str;
  size_t name_len;
  size_t str_len;
  uint8_t** strings;
  int strings_len;
  uint8_t* name;
  uint8_t* prefix = "i";
  strings = 0;
  strings_len = (2 + fields_len);
  status_require((sph_helper_calloc((strings_len * sizeof(uint8_t*)), (&strings))));
  /* type id */
  str = sph_helper_uint_to_string(type_id, (&str_len));
  if (!str) {
    status_set_goto(db_status_group_db, db_status_id_memory);
  };
  strings[0] = prefix;
  strings[1] = str;
  /* field ids */
  for (i = 0; (i < fields_len); i = (1 + i)) {
    str = sph_helper_uint_to_string((fields[i]), (&str_len));
    if (!str) {
      status_set_goto(db_status_group_db, db_status_id_memory);
    };
    strings[(2 + i)] = str;
  };
  name = string_join(strings, strings_len, "-", (&name_len));
  if (!name) {
    status_set_goto(db_status_group_db, db_status_id_memory);
  };
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
}
/** create a key to be used in an index database.
  similar to db-record-values->data but only for indexed fields.
  key format: field-data ...
  the record id will be in the btree key but with MDB-DUPSORT it is treated like key/value.
  values must be written with variable size prefixes and more like for row data to avoid ambiguous keys */
status_t db_index_key(db_env_t* env, db_index_t index, db_record_values_t values, void** result_data, size_t* result_size) {
  status_declare;
  void* data;
  uint64_t data_size;
  uint8_t* data_temp;
  void* field_data;
  db_fields_len_t field_index;
  db_field_type_size_t field_size;
  db_field_t* fields;
  db_fields_len_t fields_fixed_count;
  db_fields_len_t i;
  size_t size;
  /* no fields set, no data stored */
  size = 0;
  fields_fixed_count = (values.type)->fields_fixed_count;
  fields = (values.type)->fields;
  /* calculate data size */
  for (i = 0; (i < index.fields_len); i = (1 + i)) {
    field_index = (index.fields)[i];
    size = ((fields[field_index]).size + ((field_index < fields_fixed_count) ? 0 : ((field_index < values.extent) ? ((values.data)[field_index]).size : 0)) + size);
  };
  if (env->maxkeysize < size) {
    status_set_goto(db_status_group_db, db_status_id_index_keysize);
  };
  /* allocate and prepare data */
  status_require((sph_helper_calloc(size, (&data))));
  data_temp = data;
  for (i = 0; (i < index.fields_len); i = (1 + i)) {
    field_index = (index.fields)[i];
    field_size = (fields[field_index]).size;
    if (field_index < values.extent) {
      data_size = ((values.data)[field_index]).size;
      field_data = ((values.data)[field_index]).data;
    } else {
      data_size = 0;
    };
    if (i < fields_fixed_count) {
      if (data_size) {
        memcpy(data_temp, field_data, data_size);
      };
      data_temp = (field_size + data_temp);
    } else {
      /* data size prefix and optionally data */
      memcpy(data_temp, (&data_size), field_size);
      data_temp = (field_size + data_temp);
      if (data_size) {
        memcpy(data_temp, field_data, data_size);
      };
      data_temp = (data_size + data_temp);
    };
  };
  *result_data = data;
  *result_size = size;
exit:
  return (status);
}
/** create entries in all indices of type for id and values.
  assumes that values has at least one entry set (values.extent unequal zero).
  index entry: field-value ... -> id */
status_t db_indices_entry_ensure(db_txn_t txn, db_record_values_t values, db_id_t id) {
  status_declare;
  db_mdb_declare_val_id;
  db_mdb_cursor_declare(record_index_cursor);
  void* data;
  MDB_val val_data;
  db_indices_len_t i;
  db_index_t record_index;
  db_index_t* record_indices;
  db_indices_len_t record_indices_len;
  val_id.mv_data = &id;
  data = 0;
  record_indices_len = (values.type)->indices_len;
  record_indices = (values.type)->indices;
  for (i = 0; (i < record_indices_len); i = (1 + i)) {
    record_index = record_indices[i];
    if (!record_index.fields_len) {
      continue;
    };
    status_require((db_index_key((txn.env), record_index, values, (&data), (&(val_data.mv_size)))));
    val_data.mv_data = data;
    db_mdb_status_require((mdb_cursor_open((txn.mdb_txn), (record_index.dbi), (&record_index_cursor))));
    db_mdb_status_require((mdb_cursor_put(record_index_cursor, (&val_data), (&val_id), 0)));
    db_mdb_cursor_close(record_index_cursor);
  };
exit:
  db_mdb_cursor_close_if_active(record_index_cursor);
  free(data);
  return (status);
}
/** delete all entries from all indices of type for id and values */
status_t db_indices_entry_delete(db_txn_t txn, db_record_values_t values, db_id_t id) {
  status_declare;
  db_mdb_declare_val_id;
  db_mdb_cursor_declare(record_index_cursor);
  uint8_t* data;
  MDB_val val_data;
  db_indices_len_t i;
  db_index_t record_index;
  db_index_t* record_indices;
  db_indices_len_t record_indices_len;
  val_id.mv_data = &id;
  data = 0;
  record_indices_len = (values.type)->indices_len;
  record_indices = (values.type)->indices;
  for (i = 0; (i < record_indices_len); i = (1 + i)) {
    record_index = record_indices[i];
    if (!record_index.fields_len) {
      continue;
    };
    status_require((db_index_key((txn.env), record_index, values, ((void**)(&data)), (&(val_data.mv_size)))));
    val_data.mv_data = data;
    db_mdb_status_require((mdb_cursor_open((txn.mdb_txn), (record_index.dbi), (&record_index_cursor))));
    db_mdb_status_require((mdb_cursor_put(record_index_cursor, (&val_data), (&val_id), 0)));
    /* assumes that indices are valid/complete and contain the entry */
    db_mdb_status_require((mdb_cursor_get(record_index_cursor, (&val_data), (&val_id), MDB_GET_BOTH)));
    if (status_is_success) {
      db_mdb_status_require((mdb_cursor_del(record_index_cursor, 0)));
    };
    db_mdb_cursor_close(record_index_cursor);
  };
exit:
  db_mdb_cursor_close_if_active(record_index_cursor);
  free(data);
  return (status);
}
/** fill one index from existing data */
status_t db_index_build(db_env_t* env, db_index_t index) {
  status_declare;
  db_mdb_declare_val_id;
  db_txn_declare(env, txn);
  db_mdb_cursor_declare(records);
  db_mdb_cursor_declare(index_cursor);
  MDB_val val_data;
  void* data;
  db_id_t id;
  db_type_t type;
  db_record_t record;
  db_record_values_t values;
  values.data = 0;
  data = 0;
  type = *(index.type);
  id = db_id_add_type(0, (type.id));
  val_id.mv_data = &id;
  status_require((db_txn_write_begin((&txn))));
  db_mdb_status_require((mdb_cursor_open((txn.mdb_txn), (index.dbi), (&index_cursor))));
  db_mdb_status_require((db_mdb_env_cursor_open(txn, records)));
  db_mdb_status_require((mdb_cursor_get(records, (&val_id), (&val_data), MDB_SET_RANGE)));
  /* for each record of type */
  while ((db_mdb_status_is_success && (type.id == db_id_type((db_pointer_to_id((val_id.mv_data))))))) {
    record.data = val_data.mv_data;
    record.size = val_data.mv_size;
    status_require((db_record_data_to_values((&type), record, (&values))));
    status_require((db_index_key(env, index, values, (&data), (&(val_data.mv_size)))));
    db_free_record_values((&values));
    val_data.mv_data = data;
    db_mdb_status_require((mdb_cursor_put(index_cursor, (&val_data), (&val_id), 0)));
    status.id = mdb_cursor_get(records, (&val_id), (&val_data), MDB_NEXT_NODUP);
  };
  db_mdb_status_expect_read;
  db_mdb_cursor_close(index_cursor);
  db_mdb_cursor_close(records);
  status_require((db_txn_commit((&txn))));
exit:
  db_mdb_cursor_close_if_active(index_cursor);
  db_mdb_cursor_close_if_active(records);
  db_txn_abort_if_active(txn);
  db_free_record_values((&values));
  free(data);
  db_mdb_status_success_if_notfound;
  return (status);
}
/** if found returns a pointer to an index struct in the cache array, zero otherwise */
db_index_t* db_index_get(db_type_t* type, db_fields_len_t* fields, db_fields_len_t fields_len) {
  db_indices_len_t indices_len;
  db_indices_len_t index;
  db_index_t* indices;
  indices = type->indices;
  indices_len = type->indices_len;
  for (index = 0; (index < indices_len); index = (1 + index)) {
    if ((indices[index]).fields_len && (0 == memcmp(((indices[index]).fields), fields, (fields_len * sizeof(db_fields_len_t))))) {
      return ((index + indices));
    };
  };
  return (0);
}
/** eventually resize type:indices and add index to type:indices.
  indices is extended and elements are set to zero on deletion.
  indices is currently never downsized, but a re-open of the db-env
  reallocates it in appropriate size (and invalidates all db-index-t pointers) */
status_t db_type_indices_add(db_type_t* type, db_index_t index, db_index_t** result) {
  status_declare;
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
  status_require((sph_helper_realloc((indices_len * sizeof(db_index_t)), (&indices))));
  indices[(indices_len - 1)] = index;
  type->indices = indices;
  type->indices_len = indices_len;
  *result = ((indices_len - 1) + indices);
exit:
  return (status);
}
status_t db_index_create(db_env_t* env, db_type_t* type, db_fields_len_t* fields, db_fields_len_t fields_len, db_index_t** result_index) {
  status_declare;
  db_mdb_declare_val_null;
  db_txn_declare(env, txn);
  db_mdb_cursor_declare(system);
  MDB_val val_data;
  db_fields_len_t* fields_copy;
  void* data;
  size_t size;
  uint8_t* name;
  size_t name_len;
  db_index_t* index_temp;
  db_index_t record_index;
  if (!fields_len) {
    status.id = db_status_id_invalid_argument;
    return (status);
  };
  fields_copy = 0;
  name = 0;
  data = 0;
  size = 0;
  /* check if already exists */
  index_temp = db_index_get(type, fields, fields_len);
  if (index_temp) {
    status_set_goto(db_status_group_db, db_status_id_duplicate);
  };
  /* prepare data */
  status_require((db_index_system_key((type->id), fields, fields_len, (&data), (&size))));
  status_require((db_index_name((type->id), fields, fields_len, (&name), (&name_len))));
  /* add to system btree */
  val_data.mv_data = data;
  val_data.mv_size = size;
  status_require((db_txn_write_begin((&txn))));
  db_mdb_status_require((db_mdb_env_cursor_open(txn, system)));
  db_mdb_status_require((mdb_cursor_put(system, (&val_data), (&val_null), 0)));
  db_mdb_cursor_close(system);
  /* add data btree */
  db_mdb_status_require((mdb_dbi_open((txn.mdb_txn), name, (MDB_CREATE | MDB_DUPSORT), (&(record_index.dbi)))));
  /* update cache. fields might be stack allocated */
  status_require((sph_helper_malloc((fields_len * sizeof(db_fields_len_t)), (&fields_copy))));
  memcpy(fields_copy, fields, (fields_len * sizeof(db_fields_len_t)));
  record_index.fields = fields_copy;
  record_index.fields_len = fields_len;
  record_index.type = type;
  status_require((db_type_indices_add(type, record_index, (&index_temp))));
  status_require((db_txn_commit((&txn))));
  status_require((db_index_build(env, record_index)));
  *result_index = index_temp;
exit:
  db_mdb_cursor_close_if_active(system);
  db_txn_abort_if_active(txn);
  if (status_is_failure) {
    free(fields_copy);
  };
  free(name);
  free(data);
  return (status);
}
/** index must be a pointer into env:types:indices.
  the cache entry struct has at least its type field set to zero */
status_t db_index_delete(db_env_t* env, db_index_t* index) {
  status_declare;
  db_mdb_declare_val_null;
  db_txn_declare(env, txn);
  db_mdb_cursor_declare(system);
  void* key_data;
  size_t key_size;
  MDB_val val_data;
  status_require((db_index_system_key((index->type->id), (index->fields), (index->fields_len), (&key_data), (&key_size))));
  val_data.mv_data = key_data;
  val_data.mv_size = key_size;
  status_require((db_txn_write_begin((&txn))));
  /* remove data btree. closes the handle */
  db_mdb_status_require((mdb_drop((txn.mdb_txn), (index->dbi), 1)));
  /* remove from system btree */
  db_mdb_status_require((db_mdb_env_cursor_open(txn, system)));
  db_mdb_status_require((mdb_cursor_get(system, (&val_data), (&val_null), MDB_SET)));
  if (db_mdb_status_is_success) {
    db_mdb_status_require((mdb_cursor_del(system, 0)));
  } else {
    db_mdb_status_expect_notfound;
  };
  db_mdb_cursor_close(system);
  status_require((db_txn_commit((&txn))));
  /* update cache */
  free_and_set_null((index->fields));
  index->fields_len = 0;
  index->type = 0;
exit:
  db_mdb_cursor_close_if_active(system);
  db_txn_abort_if_active(txn);
  return (status);
}
/** clear index and fill with data from existing records */
status_t db_index_rebuild(db_env_t* env, db_index_t* index) {
  status_declare;
  db_txn_declare(env, txn);
  uint8_t* name;
  size_t name_len;
  name = 0;
  status_require((db_index_name((index->type->id), (index->fields), (index->fields_len), (&name), (&name_len))));
  status_require((db_txn_write_begin((&txn))));
  db_mdb_status_require((mdb_drop((txn.mdb_txn), (index->dbi), 0)));
  db_mdb_status_require((mdb_dbi_open((txn.mdb_txn), name, MDB_CREATE, (&(index->dbi)))));
  status_require((db_txn_commit((&txn))));
  status = db_index_build(env, (*index));
exit:
  free(name);
  db_txn_abort_if_active(txn);
  return (status);
}
/** read index values (record ids).
  count must be positive.
  if no more value is found, status is db-notfound.
  status must be success on call */
status_t db_index_read(db_index_selection_t selection, db_count_t count, db_ids_t* result_ids) {
  status_declare;
  db_mdb_declare_val_null;
  db_mdb_declare_val_id;
  db_mdb_status_require((mdb_cursor_get((selection.cursor), (&val_null), (&val_id), MDB_GET_CURRENT)));
  do {
    i_array_add((*result_ids), (db_pointer_to_id((val_id.mv_data))));
    count = (count - 1);
    db_mdb_status_require((mdb_cursor_get((selection.cursor), (&val_null), (&val_id), MDB_NEXT_DUP)));
  } while (count);
exit:
  db_mdb_status_notfound_if_notfound;
  return (status);
}
void db_index_selection_finish(db_index_selection_t* selection) { db_mdb_cursor_close_if_active((selection->cursor)); }
/** open the cursor and set to the index key matching values.
  selection is positioned at the first match.
  if no match found then status is db-notfound */
status_t db_index_select(db_txn_t txn, db_index_t index, db_record_values_t values, db_index_selection_t* result) {
  status_declare;
  db_mdb_declare_val_id;
  db_mdb_cursor_declare(cursor);
  void* data;
  MDB_val val_data;
  data = 0;
  status_require((db_index_key((txn.env), index, values, (&data), (&(val_data.mv_size)))));
  val_data.mv_data = data;
  db_mdb_status_require((mdb_cursor_open((txn.mdb_txn), (index.dbi), (&cursor))));
  db_mdb_status_require((mdb_cursor_get(cursor, (&val_data), (&val_id), MDB_SET_KEY)));
  result->cursor = cursor;
exit:
  free(data);
  if (status_is_failure) {
    db_mdb_cursor_close_if_active(cursor);
    db_mdb_status_notfound_if_notfound;
  };
  return (status);
}
