#define db_type_get_by_id(env, type_id) (type_id + env->types)
/** extend the size of the types array if type-id is an index out of bounds */
status_t db_env_types_extend(db_env_t* env, db_type_id_t type_id) {
  status_declare;
  db_type_t* types_temp;
  db_type_id_t types_len;
  db_type_t* types;
  db_type_id_t i;
  types_len = env->types_len;
  if (types_len > type_id) {
    goto exit;
  };
  /* resize */
  types = env->types;
  types_len = (db_env_types_extra_count + type_id);
  db_realloc(types, types_temp, (types_len * sizeof(db_type_t)));
  /* set new type struct ids to zero */
  for (i = type_id; (i < types_len); i = (1 + i)) {
    (i + types)->id = 0;
  };
  env->types = types;
  env->types_len = types_len;
exit:
  return (status);
};
/** return a pointer to the type struct for the type with the given name. zero if not found */
db_type_t* db_type_get(db_env_t* env, uint8_t* name) {
  db_type_id_t i;
  db_type_id_t types_len;
  db_type_t* type;
  types_len = env->types_len;
  for (i = 0; (i < types_len); i = (1 + i)) {
    type = (i + env->types);
    if (type->id && type->name && (0 == strcmp(name, (type->name)))) {
      return (type);
    };
  };
  return (0);
};
db_field_t* db_type_field_get(db_type_t* type, uint8_t* name) {
  db_fields_len_t index;
  db_fields_len_t fields_len;
  db_field_t* fields;
  fields_len = type->fields_len;
  fields = type->fields;
  for (index = 0; (index < fields_len); index = (1 + index)) {
    if (0 == strncmp(name, ((fields[index]).name), ((fields[index]).name_len))) {
      return ((fields + index));
    };
  };
  return (0);
};
/** the data format is documented in main/open.c */
status_t db_type_create(db_env_t* env, uint8_t* name, db_field_t* fields, db_fields_len_t fields_len, uint8_t flags, db_type_t** result) {
  status_declare;
  db_mdb_cursor_declare(system);
  db_mdb_cursor_declare(nodes);
  db_txn_declare(env, txn);
  uint8_t* data;
  uint8_t* data_start;
  db_field_t field;
  db_fields_len_t i;
  db_type_t* type_pointer;
  uint8_t key[db_size_system_key];
  uint8_t name_len;
  size_t data_size;
  db_type_id_t type_id;
  MDB_val val_data;
  MDB_val val_key;
  /* check if type with name exists */
  if (db_type_get((txn.env), name)) {
    status_set_both_goto(db_status_group_db, db_status_id_duplicate);
  };
  /* check name length */
  name_len = strlen(name);
  if (db_name_len_max < name_len) {
    status_set_both_goto(db_status_group_db, db_status_id_data_length);
  };
  /* allocate insert data */
  data_size = (sizeof(db_name_len_t) + name_len + sizeof(db_fields_len_t));
  for (i = 0; (i < fields_len); i = (1 + i)) {
    data_size = (data_size + sizeof(db_field_type_t) + sizeof(db_name_len_t) + (i + fields)->name_len);
  };
  db_malloc(data, data_size);
  /* set insert data */
  data_start = data;
  *((db_name_len_t*)(data)) = name_len;
  data = (sizeof(db_name_len_t) + data);
  memcpy(data, name, name_len);
  data = (name_len + data);
  *((db_fields_len_t*)(data)) = fields_len;
  data = (sizeof(db_fields_len_t) + data);
  for (i = 0; (i < fields_len); i = (1 + i)) {
    field = fields[i];
    *((db_field_type_t*)(data)) = field.type;
    data = (1 + ((db_field_type_t*)(data)));
    *((db_name_len_t*)(data)) = field.name_len;
    data = (1 + ((db_name_len_t*)(data)));
    memcpy(data, (field.name), (field.name_len));
    data = (field.name_len + data);
  };
  status_require((db_sequence_next_system((txn.env), (&type_id))));
  db_system_key_label(key) = db_system_label_type;
  db_system_key_id(key) = type_id;
  val_key.mv_data = key;
  val_key.mv_size = db_size_system_key;
  val_data.mv_data = data_start;
  val_data.mv_size = data_size;
  /* insert data */
  status_require(db_txn_write_begin((&txn)));
  db_mdb_status_require(db_mdb_env_cursor_open(txn, system));
  db_mdb_status_require(mdb_cursor_put(system, (&val_key), (&val_data), 0));
  db_mdb_cursor_close(system);
  /* update cache */
  status_require((db_env_types_extend((txn.env), type_id)));
  db_mdb_status_require(db_mdb_env_cursor_open(txn, nodes));
  status_require((db_open_type((val_key.mv_data), (val_data.mv_data), ((txn.env)->types), nodes, (&type_pointer))));
  db_mdb_cursor_close(nodes);
  status_require(db_txn_commit((&txn)));
  *result = type_pointer;
exit:
  if (db_txn_is_active(txn)) {
    db_mdb_cursor_close_if_active(system);
    db_mdb_cursor_close_if_active(nodes);
    db_txn_abort((&txn));
  };
  return (status);
};
/** delete system entry and/or all nodes and cache entries */
status_t db_type_delete(db_env_t* env, db_type_id_t type_id) {
  status_declare;
  db_mdb_declare_val_null;
  db_mdb_cursor_declare(system);
  db_mdb_cursor_declare(nodes);
  db_txn_declare(env, txn);
  MDB_val val_key;
  db_id_t id;
  uint8_t key[db_size_system_key];
  val_key.mv_size = db_size_system_key;
  db_system_key_label(key) = db_system_label_type;
  db_system_key_id(key) = type_id;
  val_key.mv_data = key;
  status_require(db_txn_write_begin((&txn)));
  /* system. continue even if not found */
  db_mdb_status_require(db_mdb_env_cursor_open(txn, system));
  status.id = mdb_cursor_get(system, (&val_key), (&val_null), MDB_SET);
  if (db_mdb_status_is_success) {
    db_mdb_status_require(mdb_cursor_del(system, 0));
  } else {
    db_mdb_status_expect_notfound;
    status.id = status_id_success;
  };
  db_mdb_cursor_close(system);
  /* nodes */
  db_mdb_status_require(db_mdb_env_cursor_open(txn, nodes));
  val_key.mv_size = sizeof(db_id_t);
  id = db_id_add_type(0, type_id);
  val_key.mv_data = &id;
  status.id = mdb_cursor_get(nodes, (&val_key), (&val_null), MDB_SET_RANGE);
  while ((db_mdb_status_is_success && (type_id == db_id_type((db_pointer_to_id((val_key.mv_data))))))) {
    db_mdb_status_require(mdb_cursor_del(nodes, 0));
    status.id = mdb_cursor_get(nodes, (&val_key), (&val_null), MDB_NEXT_NODUP);
  };
  if (status_is_failure) {
    if (db_mdb_status_is_notfound) {
      status.id = status_id_success;
    } else {
      status_set_group_goto(db_status_group_lmdb);
    };
  };
  /* cache */
  db_free_env_type((type_id + env->types));
exit:
  db_mdb_cursor_close_if_active(system);
  db_mdb_cursor_close_if_active(nodes);
  if (status_is_success) {
    status_require(db_txn_commit((&txn)));
  } else {
    db_txn_abort((&txn));
  };
  return (status);
};