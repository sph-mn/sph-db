#define db_type_flag_virtual 1
#define db_type_name_max_len 255
/** extend the types array if type-id is an index out of bounds */
status_t db_env_types_extend(db_env_t* env, db_type_id_t type_id) {
  status_init;
  db_type_t* types_temp;
  db_type_id_t types_len;
  db_type_t* types;
  db_type_id_t index;
  types_len = (*env).types_len;
  if ((types_len < type_id)) {
    types = (*env).types;
    index = types_len;
    types_len = (20 + type_id);
    db_realloc(types, types_temp, types_len);
    for (index = 0; (index < types_len); index = (1 + index)) {
      (*(index + types)).id = 0;
    };
    (*env).types = types;
    (*env).types_len = types_len;
  };
exit:
  return (status);
};
/** return a pointer to the type struct for the type with the given name. zero
 * if not found */
db_type_t* db_type_get(db_env_t* env, b8* name) {
  db_type_id_t index;
  db_type_id_t types_len;
  db_type_t* type;
  types_len = (*env).types_len;
  for (index = 0; (index < types_len); index = (1 + index)) {
    type = (index + (*env).types);
    if (((*type).id && ((0 == strcmp(name, (*type).name))))) {
      return (type);
    };
  };
  return (0);
};
/** key-value: type-label id -> name-len name field-count (field-type name-len
 * name) ... */
status_t db_type_new(db_env_t* env,
  b8* name,
  db_field_count_t field_count,
  db_field_t* fields,
  b8 flags,
  db_type_id_t result) {
  status_init;
  b8* data;
  b8* data_start;
  db_field_t field;
  db_field_count_t index;
  b8 key[db_size_system_key];
  b8 name_len;
  db_type_id_t type_id;
  MDB_val val_data;
  MDB_val val_key;
  db_txn_declare(env, txn);
  db_cursor_declare(system);
  /* check if type with name exists */
  if (db_type_get(txn.env, name)) {
    status_set_both_goto(db_status_group_db, db_status_id_duplicate);
  };
  /* check name length */
  if ((db_type_name_max_len < strlen(name))) {
    status_set_both_goto(db_status_group_db, db_status_id_data_length);
  };
  /* prepare insert data */
  data_start = data;
  name_len = strlen(name);
  db_system_key_label(key) = db_system_label_type;
  (*((b8*)(data))) = name_len;
  data = (1 + data);
  memcpy(data, name, name_len);
  data = (name_len + data);
  field_count = (*((db_field_count_t*)(data)));
  data = (sizeof(db_field_count_t) + data);
  index = 0;
  for (index = 0; (index < field_count); index = (1 + index)) {
    field = (*(fields + index));
    *data = field.type;
    data = (1 + data);
    name_len = strlen(field.name);
    *data = name_len;
    data = (1 + data);
    memcpy(data, field.name, name_len);
    data = (name_len + data);
  };
  status_require_x(db_sequence_next_system(txn.env, &type_id));
  db_system_key_id(key) = type_id;
  val_key.mv_data = key;
  val_key.mv_size = db_size_type_id;
  val_data.mv_size = (data - data_start);
  val_data.mv_data = data;
  /* insert data */
  db_txn_write_begin(txn);
  db_cursor_open(txn, system);
  db_mdb_cursor_put(system, val_key, val_data);
  db_txn_commit(txn);
  /* update cache */
  status_require_x(db_env_types_extend(txn.env, type_id));
  status_require_x(
    db_open_type(val_key.mv_data, val_data.mv_data, (*txn.env).types));
exit:
  mdb_cursor_close(system);
  return (status);
};
status_t db_type_delete(db_env_t* env, db_type_id_t id) {
  status_init;
  db_mdb_declare_val(val_key, db_size_type_id);
  db_txn_declare(env, txn);
  db_cursor_declare(system);
  db_txn_write_begin(txn);
  db_cursor_open(txn, system);
  db_mdb_cursor_get_norequire(system, val_key, val_null, MDB_SET);
  if (db_mdb_status_success_p) {
    db_mdb_status_require_x(mdb_cursor_del(system, 0));
  } else {
    db_mdb_status_require_notfound;
    status_set_id(status_id_success);
  };
exit:
  mdb_cursor_close(system);
  return (status);
};