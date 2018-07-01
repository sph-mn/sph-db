/** calculate size and prepare data */
status_t
db_node_index_key(db_index_t* index, db_node_values_t values, b0** result) {
  status_init;
  b8* data;
  db_field_count_t i;
  size_t size;
  b0* data_temp;
  for (i = 0; (i < index.fields_len); i = (1 + i)) {
    size = (size + ((values.data)[(index.fields)[i]]).size);
  };
  if ((txn.env)->maxkeysize < size) {
    status_set_both_goto(db_status_group_db, db_status_id_index_keysize);
  };
  db_malloc(data, size);
  data_temp = data;
  for (i = 0; (i < index.fields_len); i = (1 + i)) {
    value_size = ((values.data)[(index.fields)[i]]).size;
    memcpy(data_temp, (((values.data)[i]).data), value_size);
    data_temp = (value_size + data_temp);
  };
  *result = data;
exit:
  return (status);
};
/** create index entries in all indices for id and values.
  index: field-data ... -> id */
status_t
db_node_indices_set(db_txn_t txn, db_node_values_t values, db_id_t id) {
  status_init;
  db_mdb_declare_val_id;
  db_mdb_cursor_declare(node_index_cursor);
  b0* data;
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
  node_indices_len = (values.type)->indices_len;
  node_indices = (values.type)->indices;
  for (i = 0; (i < node_indices_len); i = (1 + i)) {
    node_index = node_indices[i];
    status_set_require(db_node_index_key(node_index, values, (&data)));
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
/** delete all index entries from all indices for id and values */
status_t
db_node_indices_delete(db_txn_t txn, db_node_values_t values, db_id_t id) {
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
  node_indices_len = (values.type)->indices_len;
  node_indices = (values.type)->indices;
  for (i = 0; (i < node_indices_len); i = (1 + i)) {
    node_index = node_indices[i];
    /* calculate size */
    for (fields_index = 0; (fields_index < node_index.fields_len);
         fields_index = (1 + fields_index)) {
      size = (size + ((values.data)[(node_index.fields)[fields_index]]).size);
    };
    if ((txn.env)->maxkeysize < size) {
      status_set_both_goto(db_status_group_db, db_status_id_index_keysize);
    };
    /* prepare insert data */
    db_malloc(data, size);
    val_data.mv_data = data;
    for (fields_index = 0; (fields_index < fields_len);
         fields_index = (1 + fields_index)) {
      value_size = ((values.data)[(node_index.fields)[fields_index]]).size;
      memcpy(data, (((values.data)[fields_index]).data), value_size);
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
status_t db_node_values_to_data(db_node_values_t values,
  b0** result,
  size_t* result_size) {
  status_init;
  db_field_count_t i;
  db_field_count_t field_count;
  b8 field_size;
  size_t size;
  b0* data;
  b8* data_temp;
  db_field_type_t field_type;
  field_count = (values.type)->fields_len;
  size = 0;
  /* prepare size information */
  for (i = 0; (i < field_count); i = (1 + i)) {
    if (i >= (values.type)->fields_fixed_count) {
      field_size = ((values.data)[i]).size;
    } else {
      field_type = (((values.type)->fields)[i]).type;
      field_size = db_field_type_size(field_type);
      ((values.data)[i]).size = field_size;
    };
    if (field_size > db_data_len_max) {
      status_set_both_goto(db_status_group_db, db_status_id_data_length);
    };
    size = (field_size + size);
  };
  db_malloc(data, size);
  data_temp = data;
  /* copy data */
  for (i = 0; (i < field_count); i = (1 + i)) {
    field_size = ((values.data)[i]).size;
    if (i >= (values.type)->fields_fixed_count) {
      *((db_data_len_t*)(data_temp)) = field_size;
      data_temp = (sizeof(db_data_len_t) + data_temp);
    };
    memcpy(data_temp, (((values.data)[i]).data), field_size);
    data_temp = (size + data_temp);
  };
  *result = data;
  *result_size = size;
exit:
  return (status);
};
/** allocate memory for a new node values array */
status_t db_node_values_new(db_type_t* type, db_node_values_t* result) {
  status_init;
  db_node_value_t* data;
  db_malloc(data, (type->fields_len * sizeof(db_node_value_t)));
  (*result).type = type;
  (*result).data = data;
exit:
  return (status);
};
/** set a value for a field in node values.
  size is ignored for fixed length types */
b0 db_node_values_set(db_node_values_t values,
  db_field_count_t field_index,
  b0* data,
  size_t size) {
  db_field_type_t field_type;
  field_type = (((values.type)->fields)[field_index]).type;
  ((values.data)[field_index]).data = data;
  ((values.data)[field_index]).size =
    (db_field_type_fixed_p(field_type) ? db_field_type_size(field_type) : size);
};
status_t
db_node_create(db_txn_t txn, db_node_values_t values, db_id_t* result) {
  status_init;
  db_mdb_declare_val_id;
  db_mdb_cursor_declare(nodes);
  MDB_val val_data;
  b0* data;
  db_id_t id;
  data = 0;
  val_id.mv_data = &id;
  status_require_x(
    (db_node_values_to_data(values, (&data), (&(val_data.mv_size)))));
  val_data.mv_data = data;
  db_mdb_cursor_open(txn, nodes);
  status_require_x((db_sequence_next((txn.env), ((values.type)->id), (&id))));
  db_mdb_status_require_x(mdb_cursor_put(nodes, (&val_id), (&val_data), 0));
  db_mdb_cursor_close(nodes);
  status_require_x(db_node_indices_set(txn, values, id));
  *result = id;
exit:
  db_mdb_cursor_close_if_active(nodes);
  free(data);
  return (status);
};
/** return a reference to the data in the database without copying */
db_node_data_t db_node_ref(db_node_read_state_t state, db_field_count_t field) {
  b8* data;
  b8* end;
  db_field_count_t field_index;
  db_data_t result;
  size_t size;
  if ((state.type)->fields_fixed_count > field) {
    /* fixed length field */
    result.data = (state.current + ((state.type)->fields_fixed_offsets)[field]);
    result.size = db_field_type_size(((((state.type)->fields)[field]).type));
    return (result);
  } else {
    /* variable length field */
    data = (state.current + ((state.type)->fields_fixed_offsets)[(i - 1)]);
    field_index = (state.type)->fields_fixed_count;
    end = (data + state.current_size);
    while (((field_index <= field) && (data < end))) {
      size = *((db_data_len_t*)(data));
      data = (sizeof(db_data_len_t) + data);
      if (field_index == field) {
        result.data = data;
        result.size = size;
        return (result);
      };
      field_index = (1 + field_index);
      data = (size + data);
    };
    result.data = 0;
    result.size = 0;
    return (result);
  };
};
status_t db_node_next(db_node_read_state_t* state) {
  status_init;
  db_mdb_declare_val_id;
  db_mdb_declare_val_null;
  db_node_matcher_t matcher;
  b0* matcher_state;
  db_id_t id;
  db_ids_t** ids;
  db_type_t type;
  status_require_x((state->status));
  matcher = state->matcher;
  matcher_state = state->matcher_state;
  type = state->type;
  ids = state->ids;
  skip = state->skip;
  count = state->count;
  if (ids) {
    /* filter ids */
    while ((ids && count)) {
      val_id.mv_data = db_ids_first_address(ids);
      db_cursor_get_norequire((state->cursor), val_id, val_data, MDB_SET_KEY);
      if (db_mdb_status_success_p) {
        if (!matcher ||
          matcher(db_ids_first(ids),
            (val_data.mv_data),
            (val_data.mv_size),
            matcher_state)) {
          if (!skip) {
            state->current = val_data.mv_data;
            state->current_size = val_data.mv_size;
            state->current_id = db_ids_first(ids);
          };
        } else {
          count = (count - 1);
        };
      } else {
        db_mdb_status_require_notfound;
      };
      ids = db_ids_rest(ids);
    };
    goto exit;
  } else {
    /* filter type */
    db_cursor_get((state->cursor), val_id, val_null, MDB_GET_CURRENT);
    if (!(type == db_id_type((pointer_to_id((val_id.mv_data)))))) {
      id = db_id_add_type(0, type);
      val_id.mv_data = &id;
      db_cursor_get((state->cursor), val_id, val_null, MDB_SET_RANGE);
    };
    while ((db_status_success_p && count &&
      (type == db_id_type((pointer_to_id((val_id.mv_data))))))) {
      if (!matcher ||
        matcher(db_ids_first(ids),
          (val_data.mv_data),
          (val_data.mv_size),
          matcher_state)) {
        if (!skip) {
          state->current = val_data.mv_data;
          state->current_size = val_data.mv_size;
          state->current_id = db_ids_first(ids);
        };
      } else {
        count = (count - 1);
      };
      db_cursor_get_norequire(
        (state->cursor), val_id, val_null, MDB_NEXT_NODUP);
    };
    if (!db_mdb_status_success_p) {
      db_mdb_status_require_notfound;
    };
  };
exit:
  db_status_no_more_data_if_mdb_notfound;
  state->ids = ids;
  return (status);
};
/** read the next count matches and position state afterwards */
status_t db_node_skip(db_node_read_state_t* state, b32 count) {
  status_init;
  state->skip = 1;
  state->count = count;
  status = db_node_next(state);
  state->skip = 0;
  state->count = 1;
  return (status);
};
/** select nodes optionally filtered by either a list of ids or type.
  ids: zero if unused
  type: zero if unused
  offset: skip this number of matches first
  matcher: zero if unused. a function that is called for each node after
  filtering by ids or type matcher-state: zero if unused. a pointer passed to
  each call of matcher */
status_t db_node_select(db_txn_t txn,
  db_ids_t* type,
  db_type_t* ids,
  b32 offset,
  db_node_matcher_t matcher,
  b0* matcher_state,
  db_node_read_state_t* result_state) {
  status_init;
  db_mdb_declare_val_null;
  db_mdb_cursor_declare(nodes);
  db_mdb_cursor_open(txn, nodes);
  db_mdb_cursor_get(nodes, val_null, val_null, MDB_FIRST);
  result_state->cursor = nodes;
  result_state->type = type;
  result_state->status = status;
  result_state->options = 0;
  result_state->count = 1;
  result_state->skip = 0;
  result_state->matcher = matcher;
  result_state->matcher_state = matcher_state;
  if (offset) {
    status = db_node_skip(result_state, offset);
  };
exit:
  if (!db_mdb_status_success_p) {
    mdb_cursor_close(nodes);
    db_status_no_more_data_if_mdb_notfound;
  };
  result_state->status = status;
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
/** delete a node and all its relations.
  check if ids is zero because in db-graph-internal-delete it would mean
  non-filter and match all */
status_t db_node_delete(db_txn_t txn, db_ids_t* ids) {
  status_init;
  db_mdb_declare_val_id;
  db_mdb_declare_val_null;
  db_mdb_cursor_declare(nodes);
  db_mdb_cursor_declare_three(graph_lr, graph_rl, graph_ll);
  if (!ids) {
    return (status);
  };
  db_mdb_cursor_open(txn, graph_lr);
  db_mdb_cursor_open(txn, graph_rl);
  db_mdb_cursor_open(txn, graph_ll);
  status_require_x(
    db_graph_internal_delete(ids, 0, 0, 0, graph_lr, graph_rl, graph_ll));
  status_require_x(
    db_graph_internal_delete(0, ids, 0, 0, graph_lr, graph_rl, graph_ll));
  status_require_x(
    db_graph_internal_delete(0, 0, ids, 0, graph_lr, graph_rl, graph_ll));
  db_mdb_cursor_open(txn, nodes);
  while (ids) {
    val_id.mv_data = db_ids_first_address(ids);
    db_mdb_cursor_get_norequire(nodes, val_id, val_null, MDB_SET);
    if (db_mdb_status_success_p) {
      db_mdb_cursor_del(nodes, 0);
    } else {
      if (db_mdb_status_notfound_p) {
        status_set_id(status_id_success);
      } else {
        status_set_group_goto(db_status_group_lmdb);
      };
    };
    ids = db_ids_rest(ids);
  };
exit:
  db_mdb_cursor_close(graph_lr);
  db_mdb_cursor_close(graph_rl);
  db_mdb_cursor_close(graph_ll);
  db_mdb_cursor_close(nodes);
  return (status);
};
b0 db_node_selection_destroy(db_node_read_state_t* state) {
  if (state->cursor) {
    mdb_cursor_close((state->cursor));
  };
};
/** add the existing ids from the given ids to "result" */
status_t db_node_identify(db_txn_t txn, db_ids_t* ids, db_ids_t** result) {
  status_init;
  db_mdb_declare_val_id;
  db_mdb_declare_val_null;
  db_mdb_cursor_declare(nodes);
  dg_ids_t* result_ids;
  result_ids = 0;
  db_mdb_cursor_open(txn, nodes);
  while (ids) {
    val_id.mv_data = db_ids_first_address(ids);
    status.id = mdb_cursor_get(nodes, (&val_id), (&val_null), MDB_SET);
    if (db_mdb_status_success_p) {
      result_ids = db_ids_add(result_ids, db_ids_first(ids));
    } else {
      db_mdb_status_require_notfound;
    };
    ids = db_ids_rest(ids);
  };
exit:
  mdb_cursor_close(nodes);
  db_status_success_if_mdb_notfound;
  if (status_failure_p) {
    db_ids_destroy(result_ids);
  } else {
    *result = result_ids;
  };
  return (status);
};
/** true if all given ids are ids of existing nodes, false otherwise */
status_t db_node_exists_p(db_txn_t* txn, db_ids_t* ids, boolean* result) {
  status_init;
  db_mdb_declare_val_id;
  db_mdb_declare_val_null;
  db_mdb_cursor_declare(nodes);
  db_mdb_cursor_open(txn, nodes);
  while (ids) {
    val_id.mv_data = db_ids_first_address(ids);
    status.id = mdb_cursor_get(nodes, (&val_id), (&val_null), MDB_SET);
    if (db_mdb_status_notfound_p) {
      *result = 0;
      status.id = status_id_success;
      goto exit;
    } else {
      status_require;
    };
    ids = db_ids_rest(ids);
  };
  *result = 1;
exit:
  mdb_cursor_close(nodes);
  return (status);
};