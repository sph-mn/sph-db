/* system btree entry format. key -> value
     type-label type-id -> uint8_t:flags db-name-len-t:name-len name db-field-len-t:field-count (uint8-t:field-type uint8-t:name-len name) ...
     index-label type-id db-field-len-t:field-offset ... -> () */
#define db_env_types_extra_count 20
/** prepare the database filesystem root path.
  create the full directory path if it does not exist */
status_t db_open_root(db_env_t* env, db_open_options_t* options, uint8_t* path) {
  status_declare;
  uint8_t* path_temp;
  path_temp = 0;
  path_temp = string_clone(path);
  if (!path_temp) {
    db_status_set_id_goto(db_status_id_memory);
  };
  if (!ensure_directory_structure(path_temp, (73 | options->file_permissions))) {
    db_status_set_id_goto(db_status_id_path_not_accessible_db_root);
  };
  env->root = path_temp;
exit:
  if (status_is_failure) {
    free(path_temp);
  };
  return (status);
};
uint32_t db_open_mdb_env_flags(db_open_options_t* options) { return ((options->env_open_flags ? options->env_open_flags : (MDB_NOSUBDIR | MDB_WRITEMAP | (options->is_read_only ? MDB_RDONLY : 0) | (options->filesystem_has_ordered_writes ? MDB_MAPASYNC : 0)))); };
status_t db_open_mdb_env(db_env_t* env, db_open_options_t* options) {
  status_declare;
  uint8_t* data_path;
  MDB_env* mdb_env;
  mdb_env = 0;
  data_path = string_append((env->root), "/data");
  if (!data_path) {
    db_status_set_id_goto(db_status_id_memory);
  };
  db_mdb_status_require((mdb_env_create((&mdb_env))));
  db_mdb_status_require((mdb_env_set_maxdbs(mdb_env, (options->maximum_db_count))));
  db_mdb_status_require((mdb_env_set_mapsize(mdb_env, (options->maximum_size))));
  db_mdb_status_require((mdb_env_set_maxreaders(mdb_env, (options->maximum_reader_count))));
  db_mdb_status_require((mdb_env_open(mdb_env, data_path, (db_open_mdb_env_flags(options)), (options->file_permissions))));
  env->maxkeysize = mdb_env_get_maxkeysize(mdb_env);
  env->mdb_env = mdb_env;
exit:
  free(data_path);
  if (status_is_failure) {
    if (mdb_env) {
      mdb_env_close(mdb_env);
    };
  };
  return (status);
};
/** check that the format the database was created with matches the current configuration.
  id, type and ordinal sizes are set at compile time and cant be changed for a database
  after data has been inserted */
status_t db_open_format(MDB_cursor* system, db_txn_t txn) {
  status_declare;
  uint8_t* data;
  uint8_t label;
  uint32_t format_data;
  uint8_t* format;
  MDB_val val_key;
  MDB_val val_data;
  MDB_stat stat_info;
  format = ((uint8_t*)(&format_data));
  format[0] = sizeof(db_id_t);
  format[1] = sizeof(db_type_id_t);
  format[2] = sizeof(db_ordinal_t);
  val_key.mv_size = 1;
  val_data.mv_size = 3;
  label = db_system_label_format;
  val_key.mv_data = &label;
  status.id = mdb_cursor_get(system, (&val_key), (&val_data), MDB_SET);
  if (db_mdb_status_is_success) {
    data = val_data.mv_data;
    if (!((data[0] == format[0]) && (data[1] == format[1]) && (data[2] == format[2]))) {
      /* differing type sizes are not a problem if there is no data yet.
             this only checks if any tables/indices exist by checking the contents of the system btree */
      db_mdb_status_require((mdb_stat((txn.mdb_txn), ((txn.env)->dbi_system), (&stat_info))));
      if (1 == stat_info.ms_entries) {
        val_data.mv_data = format;
        db_mdb_status_require((mdb_cursor_put(system, (&val_key), (&val_data), 0)));
      } else {
        fprintf(stderr, ("database sizes: (id %u) (type: %u) (ordinal %u)"), (data[0]), (data[1]), (data[2]));
        db_status_set_id_goto(db_status_id_different_format);
      };
    };
  } else {
    db_mdb_status_expect_notfound;
    /* no format entry exists yet */
    val_data.mv_data = format;
    db_mdb_status_require((mdb_cursor_put(system, (&val_key), (&val_data), 0)));
  };
  (txn.env)->format = format_data;
exit:
  return (status);
};
/** initialise the sequence for system ids like type ids in result.
  set result to the next sequence value. id zero is reserved for null */
status_t db_open_system_sequence(MDB_cursor* system, db_type_id_t* result) {
  status_declare;
  db_mdb_declare_val_null;
  MDB_val val_key;
  db_type_id_t current;
  uint8_t key[db_size_system_key];
  val_key.mv_size = 1;
  current = 0;
  db_system_key_label(key) = db_system_label_type;
  db_system_key_id(key) = db_type_id_limit;
  val_key.mv_data = key;
  /* search from the last possible type or the key after */
  status.id = mdb_cursor_get(system, (&val_key), (&val_null), MDB_SET_RANGE);
  if (db_mdb_status_is_success) {
    if (db_system_label_type == db_system_key_label((val_key.mv_data))) {
      current = db_system_key_id((val_key.mv_data));
      goto exit;
    } else {
      status.id = mdb_cursor_get(system, (&val_key), (&val_null), MDB_PREV);
      if (db_mdb_status_is_success) {
        current = db_system_key_id((val_key.mv_data));
        goto exit;
      } else {
        db_mdb_status_expect_notfound;
      };
    };
  } else {
    db_mdb_status_expect_notfound;
  };
  /* search from the last key */
  status.id = mdb_cursor_get(system, (&val_key), (&val_null), MDB_LAST);
  if (db_mdb_status_is_success) {
    while ((db_mdb_status_is_success && !(db_system_label_type == db_system_key_label((val_key.mv_data))))) {
      status.id = mdb_cursor_get(system, (&val_key), (&val_null), MDB_PREV);
    };
    if (db_mdb_status_is_success) {
      current = db_system_key_id((val_key.mv_data));
      goto exit;
    } else {
      db_mdb_status_expect_notfound;
    };
  } else {
    db_mdb_status_expect_notfound;
  };
exit:
  db_mdb_status_success_if_notfound;
  *result = ((db_type_id_limit == current) ? current : (1 + current));
  return (status);
};
/** get the first data id of type and save it in result. result is set to zero if none has been found */
status_t db_type_first_id(MDB_cursor* records, db_type_id_t type_id, db_id_t* result) {
  status_declare;
  db_mdb_declare_val_null;
  db_mdb_declare_val_id;
  db_id_t id;
  *result = 0;
  id = db_id_add_type(0, type_id);
  val_id.mv_data = &id;
  status.id = mdb_cursor_get(records, (&val_id), (&val_null), MDB_SET_RANGE);
  if (db_mdb_status_is_success) {
    if (type_id == db_id_type((db_pointer_to_id((val_id.mv_data))))) {
      *result = db_pointer_to_id((val_id.mv_data));
    };
  } else {
    if (db_mdb_status_is_notfound) {
      status.id = status_id_success;
    } else {
      status_set_group_goto(db_status_group_lmdb);
    };
  };
exit:
  return (status);
};
/** sets result to the last key id if the last key is of type, otherwise sets result to zero.
  leaves cursor at last key. status is mdb-notfound if database is empty */
status_t db_type_last_key_id(MDB_cursor* records, db_type_id_t type_id, db_id_t* result) {
  status_declare;
  db_mdb_declare_val_id;
  db_mdb_declare_val_null;
  *result = 0;
  status.id = mdb_cursor_get(records, (&val_id), (&val_null), MDB_LAST);
  if (db_mdb_status_is_success && (type_id == db_id_type((db_pointer_to_id((val_id.mv_data)))))) {
    *result = db_pointer_to_id((val_id.mv_data));
  };
  return (status);
};
/** get the last existing record id for type or zero if none exist.
   algorithm: check if data of type exists, if yes then check if last key is of type or
   position next type and step back */
status_t db_type_last_id(MDB_cursor* records, db_type_id_t type_id, db_id_t* result) {
  status_declare;
  db_mdb_declare_val_id;
  db_mdb_declare_val_null;
  db_id_t id;
  /* if last key is of type then there are no greater type-ids and data of type exists.
    if there is no last key, the database is empty */
  status = db_type_last_key_id(records, type_id, (&id));
  if (db_mdb_status_is_success) {
    if (id) {
      *result = id;
      goto exit;
    };
  } else {
    if (db_mdb_status_is_notfound) {
      /* database is empty */
      *result = 0;
      status_set_id_goto(status_id_success);
    } else {
      status_goto;
    };
  };
  /* database is not empty and the last key is not of searched type.
     type-id +1 is not greater than max possible type-id */
  status_require((db_type_first_id(records, (1 + type_id), (&id))));
  if (!id) {
    /* no greater type-id found. since the searched type is not the last,
         all existing type-ids are smaller */
    *result = 0;
    goto exit;
  };
  /* greater type found, step back */
  db_mdb_status_require((mdb_cursor_get(records, (&val_id), (&val_null), MDB_PREV)));
  *result = ((type_id == db_id_type((db_pointer_to_id((val_id.mv_data))))) ? db_pointer_to_id((val_id.mv_data)) : 0);
exit:
  return (status);
};
/** initialise the sequence for a type by searching the max used id for the type.
   lowest sequence value is 1.
   algorithm:
     check if any entry for type exists, then position at max or first next type key,
     take or step back to previous key */
status_t db_open_sequence(MDB_cursor* records, db_type_t* type) {
  status_declare;
  db_id_t id;
  status_require((db_type_last_id(records, (type->id), (&id))));
  id = db_id_element(id);
  type->sequence = ((id < db_element_id_limit) ? (1 + id) : id);
exit:
  return (status);
};
/** read information for fields from system btree type data */
status_t db_open_type_read_fields(uint8_t** data_pointer, db_type_t* type) {
  status_declare;
  db_fields_len_t count;
  uint8_t* data;
  uint8_t field_type;
  db_field_t* field_pointer;
  db_field_t* fields;
  db_fields_len_t fixed_count;
  size_t* fixed_offsets;
  db_fields_len_t i;
  db_fields_len_t offset;
  data = *data_pointer;
  fixed_offsets = 0;
  fixed_count = 0;
  fields = 0;
  offset = 0;
  count = *((db_fields_len_t*)(data));
  data = (sizeof(db_fields_len_t) + data);
  db_calloc(fields, count, (sizeof(db_field_t)));
  /* field */
  for (i = 0; (i < count); i = (1 + i)) {
    /* type */
    field_pointer = (i + fields);
    field_type = *data;
    data = (sizeof(db_field_type_t) + data);
    field_pointer->type = field_type;
    field_pointer->name_len = *((db_name_len_t*)(data));
    db_read_name((&data), (&(field_pointer->name)));
    if (db_field_type_is_fixed(field_type)) {
      fixed_count = (1 + fixed_count);
    };
  };
  /* offsets
example: field-sizes-in-bytes: 1 4 2. fields-fixed-offsets: 1 5 7 */
  if (fixed_count) {
    db_malloc(fixed_offsets, ((1 + fixed_count) * sizeof(size_t)));
    for (i = 0; (i < fixed_count); i = (1 + i)) {
      *(i + fixed_offsets) = offset;
      offset = (offset + db_field_type_size(((i + fields)->type)));
    };
    *(i + fixed_offsets) = offset;
  };
  type->fields = fields;
  type->fields_len = count;
  type->fields_fixed_count = fixed_count;
  type->fields_fixed_offsets = fixed_offsets;
  *data_pointer = data;
exit:
  if (status_is_failure) {
    db_free_env_types_fields((&fields), count);
  };
  return (status);
};
status_t db_open_type(uint8_t* system_key, uint8_t* system_value, db_type_t* types, MDB_cursor* records, db_type_t** result_type) {
  status_declare;
  db_type_id_t id;
  db_type_t* type_pointer;
  id = db_system_key_id(system_key);
  type_pointer = (id + types);
  type_pointer->id = id;
  type_pointer->sequence = 1;
  type_pointer->flags = *system_value;
  system_value = (1 + system_value);
  status_require((db_read_name((&system_value), (&(type_pointer->name)))));
  status_require((db_open_type_read_fields((&system_value), type_pointer)));
  *result_type = type_pointer;
exit:
  return (status);
};
/** load type info into cache. open all dbi.
   max type id size is currently 16 bit because of using an array to cache types
   instead of a slower hash table which would be needed otherwise.
   the type array has free space at the end for possible new types.
   type id zero is the system btree */
status_t db_open_types(MDB_cursor* system, MDB_cursor* records, db_txn_t txn) {
  status_declare;
  MDB_val val_key;
  MDB_val val_data;
  uint8_t key[db_size_system_key];
  db_type_t* type_pointer;
  db_type_t* types;
  db_type_id_t types_len;
  db_type_id_t system_sequence;
  val_key.mv_size = (1 + sizeof(db_id_t));
  val_data.mv_size = 3;
  types = 0;
  if (db_size_type_id_max < sizeof(db_type_id_t)) {
    status_set_both_goto(db_status_group_db, db_status_id_max_type_id_size);
  };
  /* initialise system sequence (type 0) */
  status_require((db_open_system_sequence(system, (&system_sequence))));
  types_len = (db_type_id_limit - system_sequence);
  types_len = (system_sequence + ((db_env_types_extra_count < types_len) ? db_env_types_extra_count : types_len));
  db_system_key_label(key) = db_system_label_type;
  db_system_key_id(key) = 0;
  val_key.mv_data = key;
  db_calloc(types, types_len, (sizeof(db_type_t)));
  types->sequence = system_sequence;
  /* record types */
  status.id = mdb_cursor_get(system, (&val_key), (&val_data), MDB_SET_RANGE);
  while ((db_mdb_status_is_success && (db_system_label_type == db_system_key_label((val_key.mv_data))))) {
    status_require((db_open_type((val_key.mv_data), (val_data.mv_data), types, records, (&type_pointer))));
    status_require((db_open_sequence(records, type_pointer)));
    status.id = mdb_cursor_get(system, (&val_key), (&val_data), MDB_NEXT);
  };
  if (db_mdb_status_is_notfound) {
    status.id = status_id_success;
  } else {
    status_goto;
  };
  (txn.env)->types = types;
  (txn.env)->types_len = types_len;
exit:
  if (status_is_failure) {
    db_free_env_types((&types), types_len);
  };
  return (status);
};
/** extend type cache with index information. there can be multiple indices per type */
status_t db_open_indices(MDB_cursor* system, db_txn_t txn) {
  status_declare;
  db_mdb_declare_val_null;
  MDB_val val_key;
  db_type_id_t current_type_id;
  db_fields_len_t* fields;
  db_fields_len_t fields_len;
  db_index_t* indices;
  db_fields_len_t indices_alloc_len;
  db_fields_len_t indices_len;
  db_index_t* indices_temp;
  uint8_t key[db_size_system_key];
  db_type_id_t type_id;
  db_type_t* types;
  db_type_id_t types_len;
  val_key.mv_size = (1 + sizeof(db_id_t));
  indices = 0;
  fields = 0;
  current_type_id = 0;
  indices_len = 0;
  db_system_key_label(key) = db_system_label_index;
  db_system_key_id(key) = 0;
  val_key.mv_data = key;
  types = (txn.env)->types;
  types_len = (txn.env)->types_len;
  status.id = mdb_cursor_get(system, (&val_key), (&val_null), MDB_SET_RANGE);
  while ((db_mdb_status_is_success && (db_system_label_index == db_system_key_label((val_key.mv_data))))) {
    type_id = db_system_key_id((val_key.mv_data));
    if (current_type_id == type_id) {
      indices_len = (1 + indices_len);
      if (indices_len > indices_alloc_len) {
        indices_alloc_len = (2 * indices_alloc_len);
        db_realloc(indices, indices_temp, (indices_alloc_len * sizeof(db_index_t)));
      };
    } else {
      if (indices_len) {
        /* reallocate indices from indices-alloc-len to indices-len */
        if (!(indices_alloc_len == indices_len)) {
          db_realloc(indices, indices_temp, (indices_len * sizeof(db_index_t)));
        };
        (current_type_id + types)->indices = indices;
      };
      current_type_id = type_id;
      indices_len = 1;
      indices_alloc_len = 10;
      db_calloc(indices, indices_alloc_len, (sizeof(db_index_t)));
    };
    fields_len = ((val_key.mv_size - sizeof(db_system_label_index) - sizeof(db_type_id_t)) / sizeof(db_fields_len_t));
    db_calloc(fields, fields_len, (sizeof(db_fields_len_t)));
    (indices[(indices_len - 1)]).fields = fields;
    (indices[(indices_len - 1)]).fields_len = fields_len;
    status.id = mdb_cursor_get(system, (&val_key), (&val_null), MDB_NEXT);
  };
  if (db_mdb_status_is_notfound) {
    status.id = status_id_success;
  } else {
    status_goto;
  };
  if (current_type_id) {
    (current_type_id + types)->indices = indices;
  };
exit:
  if (status_is_failure) {
    db_free_env_types_indices((&indices), indices_len);
  };
  return (status);
};
/** ensure that the system tree exists with default values.
  check format and load cached values */
status_t db_open_system(db_txn_t txn) {
  status_declare;
  db_mdb_cursor_declare(system);
  db_mdb_cursor_declare(records);
  db_mdb_status_require((mdb_dbi_open((txn.mdb_txn), "system", MDB_CREATE, (&((txn.env)->dbi_system)))));
  db_mdb_env_cursor_open(txn, system);
  status_require((db_open_format(system, txn)));
  db_mdb_env_cursor_open(txn, records);
  status_require((db_open_types(system, records, txn)));
  status_require((db_open_indices(system, txn)));
exit:
  db_mdb_cursor_close_if_active(system);
  db_mdb_cursor_close_if_active(records);
  return (status);
};
/** ensure that the trees used for the relation exist, configure and open dbi */
status_t db_open_relation(db_txn_t txn) {
  status_declare;
  uint32_t db_options;
  MDB_dbi dbi_relation_lr;
  MDB_dbi dbi_relation_rl;
  MDB_dbi dbi_relation_ll;
  db_options = (MDB_CREATE | MDB_DUPSORT | MDB_DUPFIXED);
  db_mdb_status_require((mdb_dbi_open((txn.mdb_txn), "relation-lr", db_options, (&dbi_relation_lr))));
  db_mdb_status_require((mdb_dbi_open((txn.mdb_txn), "relation-rl", db_options, (&dbi_relation_rl))));
  db_mdb_status_require((mdb_dbi_open((txn.mdb_txn), "relation-ll", db_options, (&dbi_relation_ll))));
  db_mdb_status_require((mdb_set_compare((txn.mdb_txn), dbi_relation_lr, ((MDB_cmp_func*)(db_mdb_compare_relation_key)))));
  db_mdb_status_require((mdb_set_compare((txn.mdb_txn), dbi_relation_rl, ((MDB_cmp_func*)(db_mdb_compare_relation_key)))));
  db_mdb_status_require((mdb_set_compare((txn.mdb_txn), dbi_relation_ll, ((MDB_cmp_func*)(db_mdb_compare_id)))));
  db_mdb_status_require((mdb_set_dupsort((txn.mdb_txn), dbi_relation_lr, ((MDB_cmp_func*)(db_mdb_compare_relation_data)))));
  db_mdb_status_require((mdb_set_dupsort((txn.mdb_txn), dbi_relation_rl, ((MDB_cmp_func*)(db_mdb_compare_id)))));
  db_mdb_status_require((mdb_set_dupsort((txn.mdb_txn), dbi_relation_ll, ((MDB_cmp_func*)(db_mdb_compare_id)))));
  (txn.env)->dbi_relation_lr = dbi_relation_lr;
  (txn.env)->dbi_relation_rl = dbi_relation_rl;
  (txn.env)->dbi_relation_ll = dbi_relation_ll;
exit:
  return (status);
};
void db_open_options_set_defaults(db_open_options_t* a) {
  a->is_read_only = 0;
  a->maximum_size = 17179869183;
  a->maximum_reader_count = 65535;
  a->maximum_db_count = 255;
  a->env_open_flags = 0;
  a->filesystem_has_ordered_writes = 1;
  a->file_permissions = 384;
};
status_t db_open_records(db_txn_t txn) {
  status_declare;
  db_mdb_status_require((mdb_dbi_open((txn.mdb_txn), "records", MDB_CREATE, (&((txn.env)->dbi_records)))));
  db_mdb_status_require((mdb_set_compare((txn.mdb_txn), ((txn.env)->dbi_records), ((MDB_cmp_func*)(db_mdb_compare_id)))));
exit:
  return (status);
};
status_t db_open(uint8_t* path, db_open_options_t* options_pointer, db_env_t* env) {
  status_declare;
  db_open_options_t options;
  if (!(sizeof(db_id_t) > sizeof(db_type_id_t))) {
    status_set_both_goto(db_status_group_db, db_status_id_max_type_id_size);
  };
  db_txn_declare(env, txn);
  if (env->is_open) {
    return (status);
  };
  if (!path) {
    db_status_set_id_goto(db_status_id_missing_argument_db_root);
  };
  if (options_pointer) {
    options = *options_pointer;
  } else {
    db_open_options_set_defaults((&options));
  };
  status_require((db_open_root(env, (&options), path)));
  status_require((db_open_mdb_env(env, (&options))));
  status_require((db_txn_write_begin((&txn))));
  status_require((db_open_records(txn)));
  status_require((db_open_system(txn)));
  status_require((db_open_relation(txn)));
  status_require((db_txn_commit((&txn))));
  pthread_mutex_init((&(env->mutex)), 0);
  env->is_open = 1;
exit:
  if (status_is_failure) {
    db_txn_abort_if_active(txn);
    db_close(env);
  };
  return (status);
};