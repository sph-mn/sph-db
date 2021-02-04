
/** convert a record-values array to the data format that is used as btree value for records.
  the data for unset trailing fields is not included.
  assumes that fields are in the order (fixed-size-fields variable-size-fields).
  data-size is uint64-t because its content is copied with memcpy to variable size prefixes
  which are at most 64 bit.
  assumes that value sizes are not too large and that record-values-set checks that */
status_t db_record_values_to_data(db_record_values_t values, db_record_t* result) {
  status_declare;
  void* data;
  uint64_t data_size;
  uint8_t* data_temp;
  void* field_data;
  db_field_type_size_t field_size;
  db_field_t* fields;
  db_fields_len_t fields_fixed_count;
  db_fields_len_t i;
  size_t size;
  /* no fields set, no data stored */
  if (!values.extent) {
    result->data = 0;
    result->size = 0;
    return (status);
  };
  size = 0;
  fields_fixed_count = (values.type)->fields_fixed_count;
  fields = (values.type)->fields;
  /* calculate data size */
  for (i = 0; (i < values.extent); i = (1 + i)) {
    size = ((fields[i]).size + ((i < fields_fixed_count) ? 0 : ((values.data)[i]).size) + size);
  };
  /* allocate and prepare data */
  status_require((sph_helper_calloc(size, (&data))));
  data_temp = data;
  for (i = 0; (i < values.extent); i = (1 + i)) {
    data_size = ((values.data)[i]).size;
    field_size = (fields[i]).size;
    field_data = ((values.data)[i]).data;
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
  result->data = data;
  result->size = size;
exit:
  return (status);
}

/** from the full btree value of a record (data with all fields), return a reference
  to the data for specific field and the size.
  if a trailing field is not stored with the data, record.data and .size are 0 */
db_record_value_t db_record_ref(db_type_t* type, db_record_t record, db_fields_len_t field) {
  uint8_t* data_temp;
  uint8_t* end;
  db_fields_len_t i;
  size_t offset;
  db_record_value_t result;
  uint8_t prefix_size;
  size_t size;
  if (field < type->fields_fixed_count) {
    /* fixed length field */
    offset = (type->fields_fixed_offsets)[field];
    if (offset < record.size) {
      result.data = (offset + ((uint8_t*)(record.data)));
      result.size = ((type->fields)[field]).size;
    } else {
      result.data = 0;
      result.size = 0;
    };
    return (result);
  } else {
    /* variable length field */
    offset = (type->fields_fixed_count ? (type->fields_fixed_offsets)[type->fields_fixed_count] : 0);
    if (offset < record.size) {
      data_temp = (offset + ((uint8_t*)(record.data)));
      end = (record.size + ((uint8_t*)(record.data)));
      i = type->fields_fixed_count;
      /* variable length data is prefixed by its size */
      while (((i <= field) && (data_temp < end))) {
        size = 0;
        prefix_size = ((type->fields)[i]).size;
        memcpy((&size), data_temp, prefix_size);
        data_temp = (prefix_size + data_temp);
        if (i == field) {
          result.data = data_temp;
          result.size = size;
          return (result);
        };
        i = (1 + i);
        data_temp = (size + data_temp);
      };
    };
    result.data = 0;
    result.size = 0;
    return (result);
  };
}

/** allocate memory for a new record values array. all fields an sizes are zero.
  "extent" is the last field index that is set plus one, zero if no field is set */
status_t db_record_values_new(db_type_t* type, db_record_values_t* result) {
  status_declare;
  db_record_value_t* data;
  status_require((sph_helper_calloc((type->fields_len * sizeof(db_record_value_t)), (&data))));
  (*result).type = type;
  (*result).data = data;
  (*result).extent = 0;
exit:
  return (status);
}
void db_record_values_free(db_record_values_t* a) { free_and_set_null((a->data)); }

/** set a value for a field in record values.
  a failure status is returned if size is too large for the field */
status_t db_record_values_set(db_record_values_t* a, db_fields_len_t field, void* data, size_t size) {
  status_declare;
  db_record_values_t values;
  values = *a;
  /* reject invalid sizes for fixed/variable fields */
  if ((field < (values.type)->fields_fixed_count) ? ((((values.type)->fields)[field]).size < size) : ((1 << (8 * (((values.type)->fields)[field]).size)) <= size)) {
    status_set_goto(db_status_group_db, db_status_id_data_length);
  };
  ((values.data)[field]).data = data;
  ((values.data)[field]).size = size;
  if ((0 == values.extent) || (field >= values.extent)) {
    values.extent = (1 + field);
  };
  *a = values;
exit:
  return (status);
}
status_t db_record_create(db_txn_t txn, db_record_values_t values, db_id_t* result) {
  status_declare;
  db_mdb_declare_val_id;
  db_mdb_cursor_declare(records);
  MDB_val val_data;
  db_id_t id;
  db_record_t record;
  record.data = 0;
  val_id.mv_data = &id;
  status_require((db_record_values_to_data(values, (&record))));
  val_data.mv_data = record.data;
  val_data.mv_size = record.size;
  db_mdb_status_require((db_mdb_env_cursor_open(txn, records)));
  /* sequence updated as late as possible */
  status_require((db_sequence_next((txn.env), ((values.type)->id), (&id))));
  db_mdb_status_require((mdb_cursor_put(records, (&val_id), (&val_data), 0)));
  db_mdb_cursor_close(records);
  status_require((db_indices_entry_ensure(txn, values, id)));
  *result = id;
exit:
  db_mdb_cursor_close_if_active(records);
  free((record.data));
  return (status);
}
void db_free_record_values(db_record_values_t* values) { free_and_set_null((values->data)); }
status_t db_record_data_to_values(db_type_t* type, db_record_t data, db_record_values_t* result) {
  status_declare;
  db_record_value_t field_data;
  db_fields_len_t fields_len;
  db_record_values_t values;
  db_fields_len_t i;
  fields_len = type->fields_len;
  status_require((db_record_values_new(type, (&values))));
  for (i = 0; (i < fields_len); i = (1 + i)) {
    field_data = db_record_ref(type, data, i);
    if (!field_data.data) {
      break;
    };
    db_record_values_set((&values), i, (field_data.data), (field_data.size));
  };
  *result = values;
exit:
  if (status_is_failure) {
    db_free_record_values((&values));
  };
  return (status);
}
status_t db_record_read(db_record_selection_t selection, db_count_t count, db_records_t* result_records) {
  status_declare;
  db_mdb_declare_val_id;
  MDB_val val_data;
  db_record_matcher_t matcher;
  void* matcher_state;
  db_record_t record;
  boolean skip;
  boolean match;
  db_type_id_t type_id;
  matcher = selection.matcher;
  matcher_state = selection.matcher_state;
  skip = (selection.options & db_selection_flag_skip);
  type_id = (selection.type)->id;
  db_mdb_status_require((mdb_cursor_get((selection.cursor), (&val_id), (&val_data), MDB_GET_CURRENT)));
  while ((db_mdb_status_is_success && count && (type_id == db_id_type((db_pointer_to_id((val_id.mv_data))))))) {
    /* type is passed to matcher for record-ref */
    if (matcher) {
      record.id = db_pointer_to_id((val_id.mv_data));
      record.data = val_data.mv_data;
      record.size = val_data.mv_size;
      match = matcher((selection.type), record, matcher_state);
    } else {
      match = 1;
    };
    if (match) {
      if (!skip) {
        record.id = db_pointer_to_id((val_id.mv_data));
        record.data = val_data.mv_data;
        record.size = val_data.mv_size;
        i_array_add((*result_records), record);
      };
      count = (count - 1);
    };
    db_mdb_status_require((mdb_cursor_get((selection.cursor), (&val_id), (&val_data), MDB_NEXT_NODUP)));
  };
exit:
  db_mdb_status_notfound_if_notfound;
  return (status);
}

/** skip the next count matches */
status_t db_record_skip(db_record_selection_t selection, db_count_t count) {
  status_declare;
  selection.options = (selection.options | db_selection_flag_skip);
  status = db_record_read(selection, count, 0);
  selection.options = (selection.options ^ db_selection_flag_skip);
  return (status);
}

/** get records by type and optionally filtering data.
  result count is unknown on call or can be large, that is why a selection state
  for partial reading is used.
  matcher: zero if unused. a function that is called for each record of type
  matcher-state: zero if unused. a pointer passed to each call of matcher */
status_t db_record_select(db_txn_t txn, db_type_t* type, db_record_matcher_t matcher, void* matcher_state, db_record_selection_t* result_selection) {
  status_declare;
  db_mdb_declare_val_id;
  db_mdb_declare_val_null;
  db_mdb_cursor_declare(records);
  db_id_t id;
  db_mdb_status_require((db_mdb_env_cursor_open(txn, records)));
  /* position at first record of type */
  id = db_id_add_type(0, (type->id));
  val_id.mv_data = &id;
  db_mdb_status_require((mdb_cursor_get(records, (&val_id), (&val_null), MDB_SET_RANGE)));
  if (!(type->id == db_id_type((db_pointer_to_id((val_id.mv_data)))))) {
    status_set_goto(db_status_group_db, db_status_id_notfound);
  };
  result_selection->type = type;
  result_selection->cursor = records;
  result_selection->matcher = matcher;
  result_selection->matcher_state = matcher_state;
  result_selection->options = 0;
exit:
  if (status_is_failure) {
    mdb_cursor_close(records);
    db_mdb_status_notfound_if_notfound;
  };
  return (status);
}

/** get records by id.
  returns status notfound if any id could not be found if match-all is true.
  like record-get with a given mdb-cursor */
status_t db_record_get_internal(MDB_cursor* records_cursor, db_ids_t ids, boolean match_all, db_records_t* result_records) {
  status_declare;
  db_mdb_declare_val_id;
  MDB_val val_data;
  db_record_t record;
  while (i_array_in_range(ids)) {
    val_id.mv_data = ids.current;
    status.id = mdb_cursor_get(records_cursor, (&val_id), (&val_data), MDB_SET_KEY);
    if (db_mdb_status_is_success) {
      record.id = i_array_get(ids);
      record.data = val_data.mv_data;
      record.size = val_data.mv_size;
      i_array_add((*result_records), record);
    } else {
      if (db_mdb_status_is_notfound) {
        if (match_all) {
          status_set_goto(db_status_group_db, db_status_id_notfound);
        };
      } else {
        status.group = db_status_group_lmdb;
        goto exit;
      };
    };
    i_array_forward(ids);
  };
exit:
  /* only acts on a status-group-lmdb status */
  db_mdb_status_success_if_notfound;
  return (status);
}

/** get a reference to data for one record identified by id.
  fields can be accessed with db-record-ref.
  if a record could not be found and match-all is true, status is status-id-notfound */
status_t db_record_get(db_txn_t txn, db_ids_t ids, boolean match_all, db_records_t* result_records) {
  status_declare;
  db_mdb_cursor_declare(records);
  db_mdb_status_require((db_mdb_env_cursor_open(txn, records)));
  status = db_record_get_internal(records, ids, match_all, result_records);
exit:
  db_mdb_cursor_close(records);
  return (status);
}

/** declare because it is defined later */
status_t db_relation_internal_delete(db_ids_t* left, db_ids_t* right, db_ids_t* label, db_ordinal_condition_t* ordinal, MDB_cursor* relation_lr, MDB_cursor* relation_rl, MDB_cursor* relation_ll);
/** delete records and all their relations. status  */
status_t db_record_delete(db_txn_t txn, db_ids_t ids) {
  status_declare;
  db_mdb_declare_val_id;
  db_id_t id;
  MDB_val val_data;
  db_record_values_t values;
  db_record_t record;
  db_mdb_cursor_declare(records);
  db_mdb_cursor_declare(relation_lr);
  db_mdb_cursor_declare(relation_rl);
  db_mdb_cursor_declare(relation_ll);
  /* first delete references */
  db_mdb_status_require((db_mdb_env_cursor_open(txn, relation_lr)));
  db_mdb_status_require((db_mdb_env_cursor_open(txn, relation_rl)));
  db_mdb_status_require((db_mdb_env_cursor_open(txn, relation_ll)));
  status_require((db_relation_internal_delete((&ids), 0, 0, 0, relation_lr, relation_rl, relation_ll)));
  status_require((db_relation_internal_delete(0, (&ids), 0, 0, relation_lr, relation_rl, relation_ll)));
  status_require((db_relation_internal_delete(0, 0, (&ids), 0, relation_lr, relation_rl, relation_ll)));
  db_mdb_status_require((db_mdb_env_cursor_open(txn, records)));
  /* delete record and index btree entries */
  while (i_array_in_range(ids)) {
    val_id.mv_data = ids.current;
    status.id = mdb_cursor_get(records, (&val_id), (&val_data), MDB_SET_KEY);
    if (db_mdb_status_is_success) {
      id = i_array_get(ids);
      record.data = val_data.mv_data;
      record.size = val_data.mv_size;
      status_require((db_record_data_to_values((db_type_get_by_id((txn.env), (db_id_type(id)))), record, (&values))));
      status_require((db_indices_entry_delete(txn, values, id)));
      db_mdb_status_require((mdb_cursor_del(records, 0)));
    } else {
      if (db_mdb_status_is_notfound) {
        status.id = status_id_success;
      } else {
        status.group = db_status_group_lmdb;
        goto exit;
      };
    };
    i_array_forward(ids);
  };
exit:
  db_mdb_cursor_close_if_active(relation_lr);
  db_mdb_cursor_close_if_active(relation_rl);
  db_mdb_cursor_close_if_active(relation_ll);
  db_mdb_cursor_close_if_active(records);
  return (status);
}

/** delete any records of type and all their relations */
status_t db_record_delete_type(db_txn_t txn, db_type_id_t type_id) {
  status_declare;
  db_mdb_declare_val_id;
  db_mdb_declare_val_null;
  db_mdb_cursor_declare(records);
  db_id_t id;
  db_mdb_status_require((db_mdb_env_cursor_open(txn, records)));
  id = db_id_add_type(((db_id_t)(0)), type_id);
  val_id.mv_data = &id;
  db_mdb_status_require((mdb_cursor_get(records, (&val_id), (&val_null), MDB_SET_RANGE)));
  while ((db_mdb_status_is_success && (type_id == db_id_type((db_pointer_to_id((val_id.mv_data))))))) {
    db_mdb_status_require((mdb_cursor_del(records, 0)));
    status.id = mdb_cursor_get(records, (&val_id), (&val_null), MDB_NEXT_NODUP);
  };
exit:
  db_mdb_status_success_if_notfound;
  db_mdb_cursor_close_if_active(records);
  return (status);
}
void db_record_selection_finish(db_record_selection_t* a) { db_mdb_cursor_close_if_active((a->cursor)); }

/** set new data for the record with the given id */
status_t db_record_update(db_txn_t txn, db_id_t id, db_record_values_t values) {
  status_declare;
  db_mdb_declare_val_id;
  db_mdb_cursor_declare(records);
  MDB_val val_data;
  db_record_t record;
  val_id.mv_data = &id;
  record.data = 0;
  status_require((db_record_values_to_data(values, (&record))));
  db_mdb_status_require((db_mdb_env_cursor_open(txn, records)));
  db_mdb_status_require((mdb_cursor_get(records, (&val_id), (&val_data), MDB_SET)));
  val_data.mv_data = record.data;
  val_data.mv_size = record.size;
  db_mdb_status_require((mdb_cursor_put(records, (&val_id), (&val_data), 0)));
  db_mdb_cursor_close(records);
  status_require((db_indices_entry_ensure(txn, values, id)));
exit:
  db_mdb_cursor_close_if_active(records);
  free((record.data));
  return (status);
}

/** delete records selected by type or custom matcher routine.
  collects ids in batches and calls db-record-delete */
status_t db_record_select_delete(db_txn_t txn, db_type_t* type, db_record_matcher_t matcher, void* matcher_state) {
  status_declare;
  i_array_declare(ids, db_ids_t);
  i_array_declare(records, db_records_t);
  db_record_selection_declare(selection);
  status_require((db_record_select(txn, type, matcher, matcher_state, (&selection))));
  db_records_new(db_batch_len, (&records));
  db_ids_new(db_batch_len, (&ids));
  do {
    status_require_read((db_record_read(selection, db_batch_len, (&records))));
    if (!i_array_length(records)) {
      continue;
    };
    db_records_to_ids(records, (&ids));
    status_require((db_record_delete(txn, ids)));
    i_array_clear(ids);
    i_array_clear(records);
  } while (status_is_success);
exit:
  db_record_selection_finish((&selection));
  i_array_free(ids);
  i_array_free(records);
  return (status);
}
status_t db_record_index_read(db_record_index_selection_t selection, db_count_t count, db_records_t* result_records) {
  status_declare;
  db_ids_declare(ids);
  db_ids_new(count, (&ids));
  status_require_read((db_index_read((selection.index_selection), count, (&ids))));
  status_require((db_record_get_internal((selection.records_cursor), ids, 1, result_records)));
exit:
  return (status);
}
void db_record_index_selection_finish(db_record_index_selection_t* selection) {
  db_index_selection_finish((&(selection->index_selection)));
  db_mdb_cursor_close_if_active((selection->records_cursor));
}
status_t db_record_index_select(db_txn_t txn, db_index_t index, db_record_values_t values, db_record_index_selection_t* result_selection) {
  status_declare;
  db_mdb_cursor_declare(records);
  db_index_selection_declare(index_selection);
  status_require((db_index_select(txn, index, values, (&index_selection))));
  db_mdb_status_require((db_mdb_env_cursor_open(txn, records)));
  result_selection->index_selection = index_selection;
  result_selection->records_cursor = records;
exit:
  if (status_is_failure) {
    db_mdb_cursor_close_if_active(records);
  };
  return (status);
}
