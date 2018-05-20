/* system btree entry format. key -> value
     type-label id -> 8b:name-len name db-field-count-t:field-count
   (b8:field-type b8:name-len name) ... index-label db-type-id-t:type-id
   db-field-count-t:field-offset ... -> () */
/** prepare the database filesystem root path.
  create the full directory path if it does not exist.
  on success sets path and data-path to new strings */
status_t db_open_root(db_open_options_t* options, b8** path, b8** data_path) {
  status_init;
  b8* path_temp;
  b8* data_path_temp;
  path_temp = string_clone((*path));
  if (!path_temp) {
    db_status_set_id_goto(db_status_id_memory);
  };
  if (!ensure_directory_structure(
        path_temp, (384 | (*options).file_permissions))) {
    db_status_set_id_goto(db_status_id_path_not_accessible_db_root);
  };
  data_path_temp = string_append(path_temp, "/data");
  if (!data_path_temp) {
    db_status_set_id_goto(db_status_id_memory);
  };
  (*path) = path_temp;
  (*data_path) = data_path_temp;
exit:
  return (status);
};
b32 db_open_mdb_env_flags(db_open_options_t* options) {
  return (((*options).env_open_flags
      ? (*options).env_open_flags
      : (MDB_NOSUBDIR | MDB_WRITEMAP |
          ((*options).read_only_p ? MDB_RDONLY : 0) |
          ((*options).filesystem_has_ordered_writes_p ? MDB_MAPASYNC : 0))));
};
status_t
db_open_mdb_env(db_txn_t txn, b8* data_path, db_open_options_t* options) {
  status_init;
  MDB_env* mdb_env;
  db_mdb_status_require_x(mdb_env_create(&mdb_env));
  db_mdb_status_require_x(
    mdb_env_set_maxdbs(mdb_env, (*options).maximum_db_count));
  db_mdb_status_require_x(
    mdb_env_set_mapsize(mdb_env, (*options).maximum_size_octets));
  db_mdb_status_require_x(
    mdb_env_set_maxreaders(mdb_env, (*options).maximum_reader_count));
  db_mdb_status_require_x(mdb_env_open(mdb_env,
    data_path,
    db_open_mdb_env_flags(options),
    (*options).file_permissions));
  db_mdb_status_require_x(mdb_dbi_open(
    txn.mdb_txn, "id->data", MDB_CREATE, &((*txn.env).dbi_id_to_data)));
  db_mdb_status_require_x(mdb_set_compare(txn.mdb_txn,
    (*txn.env).dbi_id_to_data,
    ((MDB_cmp_func*)(db_mdb_compare_id))));
  (*txn.env).mdb_env = mdb_env;
exit:
  if (status_failure_p) {
    if (mdb_env) {
      mdb_env_close(mdb_env);
    };
  };
  return (status);
};
/** check that the format the database was created with matches the current
  configuration. id, type and ordinal sizes are set at compile time and cant be
  changed for a database after data has been inserted */
status_t db_open_format(MDB_cursor* system, db_txn_t txn) {
  status_init;
  b8 label;
  b8 format[3] = { db_size_id, db_size_type_id, db_size_ordinal };
  db_mdb_declare_val(val_key, 1);
  db_mdb_declare_val(val_data, 3);
  label = db_system_label_format;
  val_key.mv_data = &label;
  db_mdb_cursor_get_norequire(system, val_key, val_data, MDB_SET);
  if (db_mdb_status_success_p) {
    b8* data = val_data.mv_data;
    if (!((((*(data + 0)) == (*(format + 0)))) &&
          (((*(data + 1)) == (*(format + 1)))) &&
          (((*(data + 2)) == (*(format + 2)))))) {
      MDB_stat stat_info;
      db_mdb_status_require_x(
        mdb_stat(txn.mdb_txn, (*txn.env).dbi_system, &stat_info));
      if ((1 == stat_info.ms_entries)) {
        val_data.mv_data = format;
        db_mdb_cursor_put(system, val_key, val_data);
      } else {
        fprintf(stderr,
          "database sizes: (id %u) (type: %u) (ordinal %u)",
          (*(data + 0)),
          (*(data + 1)),
          (*(data + 2)));
        db_status_set_id_goto(db_status_id_different_format);
      };
    };
  } else {
    db_mdb_status_require_notfound;
    val_data.mv_data = format;
    db_mdb_cursor_put(system, val_key, val_data);
  };
exit:
  return (status);
};
/** initialise the sequence for system ids like type ids in result.
  set result to the next sequence value. id zero is reserved for null */
status_t db_open_system_sequence(MDB_cursor* system, db_type_id_t* result) {
  status_init;
  db_type_id_t current;
  b8 key[db_size_system_key];
  db_mdb_declare_val(val_key, 1);
  current = 0;
  db_system_key_label(key) = db_system_label_type;
  db_system_key_id(key) = db_type_id_max;
  val_key.mv_data = key;
  /* search from the last possible type or the key after */
  db_mdb_cursor_get_norequire(system, val_key, val_null, MDB_SET_RANGE);
  if (db_mdb_status_success_p) {
    if ((db_system_label_type == db_system_key_label(val_key.mv_data))) {
      current = db_system_key_id(val_key.mv_data);
      goto exit;
    } else {
      db_mdb_cursor_get_norequire(system, val_key, val_null, MDB_PREV);
      if (db_mdb_status_success_p) {
        current = db_system_key_id(val_key.mv_data);
        goto exit;
      } else {
        db_mdb_status_require_notfound;
      };
    };
  } else {
    db_mdb_status_require_notfound;
  };
  /* search from the last key */
  db_mdb_cursor_get_norequire(system, val_key, val_null, MDB_LAST);
  if (db_mdb_status_success_p) {
    while ((db_mdb_status_success_p &&
      (!(db_system_label_type == db_system_key_label(val_key.mv_data))))) {
      db_mdb_cursor_get_norequire(system, val_key, val_null, MDB_PREV);
    };
    if (db_mdb_status_success_p) {
      current = db_system_key_id(val_key.mv_data);
      goto exit;
    } else {
      db_mdb_status_require_notfound;
    };
  } else {
    db_mdb_status_require_notfound;
  };
exit:
  db_status_success_if_mdb_notfound;
  (*result) = ((db_type_id_max == current) ? db_type_id_max : (1 + current));
  return (status);
};
/** read information for fields from system btree type data */
status_t db_open_type_read_fields(b8** data_pointer, db_type_t* type) {
  status_init;
  db_field_count_t count;
  b8* data;
  b8 field_type;
  db_field_t* field_pointer;
  db_field_t* fields;
  db_field_count_t fixed_count;
  db_field_count_t* fixed_offsets;
  db_field_count_t i;
  db_field_count_t offset;
  data = (*data_pointer);
  fixed_offsets = 0;
  fixed_count = 0;
  fields = 0;
  count = (*((db_field_count_t*)(data)));
  data = (1 + ((db_field_count_t*)(data)));
  db_calloc(fields, count, sizeof(db_field_t));
  /* field */
  for (i = 0; (i < count); i = (1 + i)) {
    /* type */
    field_pointer = (i + fields);
    field_type = (*data);
    data = (1 + data);
    (*field_pointer).type = field_type;
    /* name */
    status_require_x(
      db_read_length_prefixed_string_b8(&data, &((*field_pointer).name)));
    if (db_field_type_fixed_p(field_type)) {
      fixed_count = (1 + fixed_count);
    };
  };
  /* offsets */
  if (fixed_count) {
    db_malloc(fixed_offsets, (fixed_count * sizeof(db_field_count_t)));
    for (i = 0; (i < fixed_count); i = (1 + i)) {
      offset = (offset + db_field_type_size((*(i + fields)).type));
      (*(i + fixed_offsets)) = offset;
    };
  };
  (*type).fields = fields;
  (*type).fields_count = count;
  (*type).fields_fixed_count = fixed_count;
  (*type).fields_fixed_offsets = fixed_offsets;
  *data_pointer = data;
exit:
  if (status_failure_p) {
    db_free_env_types_fields(&fields, count);
  };
  return (status);
};
status_t db_open_type(b8* system_key, b8* system_value, db_type_t* types) {
  status_init;
  db_type_id_t id;
  db_type_t* type_pointer;
  id = db_system_key_id(system_key);
  type_pointer = (id + types);
  (*type_pointer).id = id;
  status_require_x(
    db_read_length_prefixed_string_b8(&system_value, &((*type_pointer).name)));
  status_require_x(db_open_type_read_fields(&system_value, type_pointer));
exit:
  return (status);
};
/** load type info into cache. open all dbi.
   max type id size is currently 16 bit because of using an array to cache types
   instead of a slower hash table which would be needed otherwise.
   the type array has free space at the end for possible new types.
   type id zero is the system btree */
status_t db_open_types(MDB_cursor* system, db_txn_t txn) {
  status_init;
  b8 key[db_size_system_key];
  db_type_t* types;
  db_type_id_t types_len;
  db_type_id_t system_sequence;
  db_mdb_declare_val(val_key, (1 + sizeof(db_id_t)));
  db_mdb_declare_val(val_data, 3);
  types = 0;
  if ((16 < sizeof(db_type_id_t))) {
    status_set_both_goto(db_status_group_db, db_status_id_max_type_id_size);
  };
  status_require_x(db_open_system_sequence(system, &system_sequence));
  types_len = (db_type_id_max - system_sequence);
  types_len = (system_sequence + ((20 < types_len) ? 20 : types_len));
  db_system_key_label(key) = db_system_label_type;
  db_system_key_id(key) = 0;
  val_key.mv_data = key;
  db_calloc(types, types_len, sizeof(db_type_t));
  (*types).sequence = system_sequence;
  db_mdb_cursor_get_norequire(system, val_key, val_data, MDB_SET_RANGE);
  while ((db_mdb_status_success_p &&
    ((db_system_label_type == db_system_key_label(val_key.mv_data))))) {
    status_require_x(db_open_type(val_key.mv_data, val_data.mv_data, types));
    db_mdb_cursor_get_norequire(system, val_key, val_data, MDB_NEXT);
  };
  if (!db_mdb_status_success_p) {
    db_mdb_status_require_notfound;
  };
  (*txn.env).types = types;
  (*txn.env).types_len = types_len;
exit:
  if (status_failure_p) {
    db_free_env_types(&types, types_len);
  };
  return (status);
};
/** extend type cache with index information. there can be multiple indices per
 * type */
status_t db_open_indices(MDB_cursor* system, db_txn_t txn) {
  status_init;
  db_mdb_declare_val(val_key, (1 + sizeof(db_id_t)));
  db_type_id_t current_type_id;
  db_field_t* fields;
  db_field_count_t fields_count;
  db_index_t* indices;
  db_field_count_t indices_alloc_count;
  db_field_count_t indices_count;
  db_index_t* indices_temp;
  b8 key[db_size_system_key];
  db_type_id_t type_id;
  db_type_t* types;
  db_type_id_t types_len;
  indices = 0;
  fields = 0;
  current_type_id = 0;
  indices_count = 0;
  db_system_key_label(key) = db_system_label_index;
  db_system_key_id(key) = 0;
  val_key.mv_data = key;
  types = (*txn.env).types;
  types_len = (*txn.env).types_len;
  db_mdb_cursor_get_norequire(system, val_key, val_null, MDB_SET_RANGE);
  while ((db_mdb_status_success_p &&
    ((db_system_label_index == db_system_key_label(val_key.mv_data))))) {
    type_id = db_system_key_id(val_key.mv_data);
    if ((current_type_id == type_id)) {
      indices_count = (1 + indices_count);
      if ((indices_count > indices_alloc_count)) {
        indices_alloc_count = (2 * indices_alloc_count);
        db_realloc(
          indices, indices_temp, (indices_alloc_count * sizeof(db_index_t)));
      };
    } else {
      if (indices_count) {
        /* reallocate indices from indices-alloc-count to indices-count */
        if (!(indices_alloc_count == indices_count)) {
          db_realloc(
            indices, indices_temp, (indices_count * sizeof(db_index_t)));
        };
        (*(current_type_id + types)).indices = indices;
      };
      current_type_id = type_id;
      indices_count = 1;
      indices_alloc_count = 10;
      db_calloc(indices, indices_alloc_count, sizeof(db_index_t));
    };
    fields_count = ((val_key.mv_size - sizeof(db_system_label_index) -
                      sizeof(db_type_id_t)) /
      sizeof(db_field_count_t));
    db_calloc(fields, fields_count, sizeof(db_field_count_t));
    (*((indices_count - 1) + indices)).fields = fields;
    (*((indices_count - 1) + indices)).fields_count = fields_count;
    db_mdb_cursor_get_norequire(system, val_key, val_null, MDB_NEXT);
  };
  db_status_success_if_mdb_notfound;
  if (db_mdb_status_success_p) {
    (*(current_type_id + types)).indices = indices;
  };
exit:
  if (status_failure_p) {
    db_free_env_types_indices(&indices, indices_count);
  };
  return (status);
};
/** get the first data id of type and save it in result. result is set to zero
 * if none has been found */
status_t db_type_first_id(MDB_cursor* id_to_data,
  db_type_id_t type_id,
  db_id_t* result) {
  status_init;
  db_mdb_declare_val_id;
  db_id_t id;
  (*result) = 0;
  id = 0;
  val_id.mv_data = &id;
  db_id_set_type(id, type_id);
  db_mdb_cursor_get_norequire(id_to_data, val_id, val_null, MDB_SET_RANGE);
  if (db_mdb_status_success_p) {
    if ((type_id == db_type_id(db_mdb_val_to_id(val_id)))) {
      (*result) = db_mdb_val_to_id(val_id);
    };
  } else {
    if (db_mdb_status_notfound_p) {
      status_set_id(status_id_success);
    } else {
      status_set_group_goto(db_status_group_lmdb);
    };
  };
exit:
  return (status);
};
/** sets result to the last key id if the last key is of type, otherwise sets
  result to zero.
  leaves cursor at last key. status is mdb-notfound if database is empty */
status_t db_type_last_key_id(MDB_cursor* id_to_data,
  db_type_id_t type_id,
  db_id_t* result) {
  status_init;
  db_mdb_declare_val_id;
  (*result) = 0;
  db_mdb_cursor_get_norequire(id_to_data, val_id, val_null, MDB_LAST);
  if ((db_mdb_status_success_p &&
        ((type_id == db_type_id(db_mdb_val_to_id(val_id)))))) {
    (*result) = db_mdb_val_to_id(val_id);
  };
  return (status);
};
/** algorithm: check if data of type exists, if yes then check if last key is of
   type or position next type and step back */
status_t
db_type_last_id(MDB_cursor* id_to_data, db_type_id_t type_id, db_id_t* result) {
  status_init;
  db_mdb_declare_val_id;
  db_id_t id;
  /* if last key is not of type then either there are greater type-ids or no
   * data of type exists */
  status = db_type_last_key_id(id_to_data, type_id, &id);
  if (db_mdb_status_success_p) {
    if (id) {
      (*result) = id;
      goto exit;
    };
  } else {
    if (db_mdb_status_notfound_p) {
      (*result) = 0;
      goto exit;
    } else {
      status_set_group_goto(db_status_group_lmdb);
    };
  };
  /* database is not empty, and the last key is not of searched type.
       type-id +1 is not greater than max */
  status_require_x(db_type_first_id(id_to_data, (1 + type_id), &id));
  if (!id) {
    /* no greater type-id found. since the searched type is not the last,
             all existing type-ids are smaller */
    (*result) = 0;
    goto exit;
  };
  /* greater type found, step back */
  db_mdb_cursor_get(id_to_data, val_id, val_null, MDB_PREV);
  (*result) = ((type_id == db_type_id(db_mdb_val_to_id(val_id)))
      ? db_mdb_val_to_id(val_id)
      : 0);
exit:
  return (status);
};
/** initialise the sequence for each type by searching the max key for the type.
   lowest sequence value is 1.
   algorithm:
     check if any entry for type exists, then position at max or first next type
   key, take or step back to previous key */
status_t db_open_sequences(db_txn_t txn) {
  status_init;
  db_id_t id;
  db_type_t* types;
  db_type_id_t types_len;
  db_type_id_t index;
  db_cursor_declare(id_to_data);
  db_mdb_declare_val_id;
  types = (*txn.env).types;
  types_len = (*txn.env).types_len;
  index = 0;
  db_cursor_open(txn, id_to_data);
  while ((index < types_len)) {
    id = 0;
    status_require_x(db_type_last_id(id_to_data, (*(index + types)).id, &id));
    id = db_id_id(id);
    (*(index + types)).sequence = ((id < db_id_id_max) ? (1 + id) : id);
    index = (1 + index);
  };
exit:
  return (status);
};
/** ensure that the system tree exists with default values.
  check format and load cached values */
status_t db_open_system(db_txn_t txn) {
  status_init;
  db_mdb_cursor_declare(system);
  db_mdb_status_require_x(
    mdb_dbi_open(txn.mdb_txn, "system", MDB_CREATE, &((*txn.env).dbi_system)));
  db_cursor_open(txn, system);
  status_require_x(db_open_format(system, txn));
  status_require_x(db_open_types(system, txn));
  status_require_x(db_open_indices(system, txn));
  status_require_x(db_open_sequences(txn));
exit:
  mdb_cursor_close(system);
  return (status);
};
/** ensure that the trees used for the graph exist, configure and open dbi */
status_t db_open_graph(db_txn_t txn) {
  status_init;
  b32 db_options;
  MDB_dbi dbi_left_to_right;
  MDB_dbi dbi_right_to_left;
  MDB_dbi dbi_label_to_left;
  db_options = (MDB_CREATE | MDB_DUPSORT | MDB_DUPFIXED);
  db_mdb_status_require_x(
    mdb_dbi_open(txn.mdb_txn, "left->right", db_options, &dbi_left_to_right));
  db_mdb_status_require_x(
    mdb_dbi_open(txn.mdb_txn, "right->left", db_options, &dbi_right_to_left));
  db_mdb_status_require_x(
    mdb_dbi_open(txn.mdb_txn, "label->left", db_options, &dbi_label_to_left));
  db_mdb_status_require_x(mdb_set_compare(txn.mdb_txn,
    dbi_left_to_right,
    ((MDB_cmp_func*)(db_mdb_compare_graph_key))));
  db_mdb_status_require_x(mdb_set_compare(txn.mdb_txn,
    dbi_right_to_left,
    ((MDB_cmp_func*)(db_mdb_compare_graph_key))));
  db_mdb_status_require_x(mdb_set_compare(
    txn.mdb_txn, dbi_label_to_left, ((MDB_cmp_func*)(db_mdb_compare_id))));
  db_mdb_status_require_x(mdb_set_dupsort(txn.mdb_txn,
    dbi_left_to_right,
    ((MDB_cmp_func*)(db_mdb_compare_graph_data))));
  db_mdb_status_require_x(mdb_set_dupsort(
    txn.mdb_txn, dbi_right_to_left, ((MDB_cmp_func*)(db_mdb_compare_id))));
  db_mdb_status_require_x(mdb_set_dupsort(
    txn.mdb_txn, dbi_label_to_left, ((MDB_cmp_func*)(db_mdb_compare_id))));
  (*txn.env).dbi_left_to_right = dbi_left_to_right;
  (*txn.env).dbi_right_to_left = dbi_right_to_left;
  (*txn.env).dbi_label_to_left = dbi_label_to_left;
exit:
  return (status);
};
db_open_options_t db_open_options_set_defaults(db_open_options_t* a) {
  (*a).read_only_p = 0;
  (*a).maximum_size_octets = 17179869183;
  (*a).maximum_reader_count = 65535;
  (*a).maximum_db_count = 255;
  (*a).env_open_flags = 0;
  (*a).filesystem_has_ordered_writes_p = 1;
  (*a).file_permissions = 384;
};
status_t
db_open(b8* path_argument, db_open_options_t* options_pointer, db_env_t* env) {
  status_init;
  db_open_options_t options;
  b8* path;
  b8* data_path;
  db_txn_declare(env, txn);
  data_path = 0;
  if ((*env).open) {
    return (status);
  };
  if (!path) {
    db_status_set_id_goto(db_status_id_missing_argument_db_root);
  };
  db_mdb_reset_val_null;
  db_type_id_max = (pow(2, (8 * sizeof(db_type_id_t))) - 1);
  db_type_id_mask = db_type_id_max;
  db_id_id_max = ~(~db_id_max & db_type_id_mask);
  if (options_pointer) {
    options = (*options_pointer);
  } else {
    db_open_options_set_defaults(&options);
  };
  status_require_x(db_open_root(&options, &path_argument, &data_path));
  db_txn_write_begin(txn);
  status_require_x(db_open_mdb_env(txn, data_path, &options));
  status_require_x(db_open_system(txn));
  status_require_x(db_open_graph(txn));
  db_txn_commit(txn);
  pthread_mutex_init(&((*env).mutex), 0);
  (*env).open = 1;
exit:
  free(data_path);
  if (status_failure_p) {
    if (txn.mdb_txn) {
      db_txn_abort(txn);
    };
    db_close(env);
  };
  return (status);
};