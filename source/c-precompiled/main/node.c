status_t db_node_values_to_data(db_node_values_t values,
  void** result,
  size_t* result_size) {
  status_declare;
  db_fields_len_t i;
  db_fields_len_t fields_len;
  ui8 field_size;
  size_t size;
  void* data;
  ui8* data_temp;
  db_field_type_t field_type;
  fields_len = (values.type)->fields_len;
  size = 0;
  /* prepare size information */
  for (i = 0; (i < fields_len); i = (1 + i)) {
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
  for (i = 0; (i < fields_len); i = (1 + i)) {
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
  status_declare;
  db_node_value_t* data;
  db_malloc(data, (type->fields_len * sizeof(db_node_value_t)));
  (*result).type = type;
  (*result).data = data;
exit:
  return (status);
};
/** set a value for a field in node values.
  size is ignored for fixed length types */
void db_node_values_set(db_node_values_t values,
  db_fields_len_t field_index,
  void* data,
  size_t size) {
  db_field_type_t field_type;
  field_type = (((values.type)->fields)[field_index]).type;
  ((values.data)[field_index]).data = data;
  ((values.data)[field_index]).size =
    (db_field_type_is_fixed(field_type) ? db_field_type_size(field_type)
                                        : size);
};
status_t
db_node_create(db_txn_t txn, db_node_values_t values, db_id_t* result) {
  status_declare;
  db_mdb_declare_val_id;
  db_mdb_cursor_declare(nodes);
  MDB_val val_data;
  void* data;
  db_id_t id;
  data = 0;
  val_id.mv_data = &id;
  status_require(
    (db_node_values_to_data(values, (&data), (&(val_data.mv_size)))));
  val_data.mv_data = data;
  db_mdb_status_require(db_mdb_env_cursor_open(txn, nodes));
  /* sequence updated as late as possible */
  status_require((db_sequence_next((txn.env), ((values.type)->id), (&id))));
  db_mdb_status_require(mdb_cursor_put(nodes, (&val_id), (&val_data), 0));
  db_mdb_cursor_close(nodes);
  status_require(db_indices_entry_ensure(txn, values, id));
  *result = id;
exit:
  db_mdb_cursor_close_if_active(nodes);
  free(data);
  return (status);
};
/** get a reference to field data from node data (btree value) */
db_node_data_t db_node_data_ref(db_type_t* type,
  void* data,
  size_t data_size,
  db_fields_len_t field) {
  ui8* result_data;
  ui8* end;
  db_fields_len_t field_index;
  db_node_data_t result;
  size_t size;
  if (type->fields_fixed_count > field) {
    /* fixed length field */
    result.data = (data + (type->fields_fixed_offsets)[field]);
    result.size = db_field_type_size((((type->fields)[field]).type));
    return (result);
  } else {
    /* variable length field */
    result_data =
      (data + (type->fields_fixed_offsets)[(type->fields_fixed_count - 1)]);
    field_index = type->fields_fixed_count;
    end = (result_data + data_size);
    while (((field_index <= field) && (result_data < end))) {
      size = *((db_data_len_t*)(result_data));
      result_data = (sizeof(db_data_len_t) + result_data);
      if (field_index == field) {
        result.data = result_data;
        result.size = size;
        return (result);
      };
      field_index = (1 + field_index);
      result_data = (size + result_data);
    };
    result.data = 0;
    result.size = 0;
    return (result);
  };
};
/** return a reference to the data in the database without copying */
db_node_data_t db_node_ref(db_node_selection_t* state, db_fields_len_t field) {
  return ((db_node_data_ref(
    (state->type), (state->current), (state->current_size), field)));
};
void db_free_node_values(db_node_values_t* values) {
  free_and_set_null((values->data));
};
status_t db_node_data_to_values(db_type_t* type,
  void* data,
  size_t data_size,
  db_node_values_t* result) {
  status_declare;
  db_fields_len_t i;
  db_fields_len_t fields_len;
  db_node_data_t node_data;
  db_node_values_t values;
  size_t size;
  fields_len = type->fields_len;
  size = 0;
  status_require(db_node_values_new(type, (&values)));
  for (i = 0; (i < fields_len); i = (1 + i)) {
    node_data = db_node_data_ref(type, data, data_size, i);
    db_node_values_set(values, i, data, size);
  };
  *result = values;
exit:
  if (status_is_failure) {
    db_free_node_values((&values));
  };
  return (status);
};
status_t db_node_next(db_node_selection_t* state) {
  status_declare;
  db_mdb_declare_val_id;
  db_mdb_declare_val_null;
  db_count_t count;
  MDB_val val_data;
  db_node_matcher_t matcher;
  void* matcher_state;
  db_id_t id;
  db_ids_t* ids;
  boolean skip;
  db_type_id_t type_id;
  matcher = state->matcher;
  matcher_state = state->matcher_state;
  type_id = state->type->id;
  ids = state->ids;
  skip = (state->options & db_selection_flag_skip);
  count = state->count;
  if (ids) {
    /* filter ids */
    while ((ids && count)) {
      val_id.mv_data = db_ids_first_address(ids);
      status.id =
        mdb_cursor_get((state->cursor), (&val_id), (&val_data), MDB_SET_KEY);
      if (db_mdb_status_is_success) {
        if (!matcher ||
          matcher(db_ids_first(ids), (val_data.mv_data), (val_data.mv_size))) {
          if (!skip) {
            state->current = val_data.mv_data;
            state->current_size = val_data.mv_size;
            state->current_id = db_ids_first(ids);
          };
        } else {
          count = (count - 1);
        };
      } else {
        db_mdb_status_expect_notfound;
      };
      ids = db_ids_rest(ids);
    };
    goto exit;
  } else {
    /* filter type */
    db_mdb_status_require((mdb_cursor_get(
      (state->cursor), (&val_id), (&val_null), MDB_GET_CURRENT)));
    if (!(type_id == db_id_type((db_pointer_to_id((val_id.mv_data)))))) {
      id = db_id_add_type(0, type_id);
      val_id.mv_data = &id;
      status.id =
        mdb_cursor_get((state->cursor), (&val_id), (&val_null), MDB_SET_RANGE);
    };
    while ((db_mdb_status_is_success && count &&
      (type_id == db_id_type((db_pointer_to_id((val_id.mv_data))))))) {
      if (!matcher ||
        matcher(db_ids_first(ids), (val_data.mv_data), (val_data.mv_size))) {
        if (!skip) {
          state->current = val_data.mv_data;
          state->current_size = val_data.mv_size;
          state->current_id = db_ids_first(ids);
        };
      } else {
        count = (count - 1);
      };
      status.id =
        mdb_cursor_get((state->cursor), (&val_id), (&val_null), MDB_NEXT_NODUP);
    };
    if (!db_mdb_status_is_success) {
      db_mdb_status_expect_notfound;
    };
  };
exit:
  db_mdb_status_no_more_data_if_notfound;
  state->ids = ids;
  return (status);
};
/** read the next count matches and position state afterwards */
status_t db_node_skip(db_node_selection_t* state, db_count_t count) {
  status_declare;
  state->options = (state->options | db_selection_flag_skip);
  state->count = count;
  status = db_node_next(state);
  state->options = (state->options ^ db_selection_flag_skip);
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
  db_ids_t* ids,
  db_type_t* type,
  db_count_t offset,
  db_node_matcher_t matcher,
  void* matcher_state,
  db_node_selection_t* result_state) {
  status_declare;
  db_mdb_declare_val_null;
  db_mdb_cursor_declare(nodes);
  db_mdb_status_require(db_mdb_env_cursor_open(txn, nodes));
  db_mdb_status_require(
    mdb_cursor_get(nodes, (&val_null), (&val_null), MDB_FIRST));
  result_state->cursor = nodes;
  result_state->count = 1;
  result_state->ids = ids;
  result_state->matcher = matcher;
  result_state->matcher_state = matcher_state;
  result_state->options = 0;
  result_state->type = type;
  if (offset) {
    status = db_node_skip(result_state, offset);
  };
exit:
  if (!db_mdb_status_is_success) {
    mdb_cursor_close(nodes);
    db_mdb_status_no_more_data_if_notfound;
  };
  return (status);
};
/** get a reference to data for one node identified by id.
  if node could not be found, status is status-id-no-more-data */
status_t
db_node_get(db_txn_t txn, db_id_t id, void** result_data, size_t* result_size) {
  status_declare;
  db_mdb_declare_val_id;
  db_mdb_cursor_declare(nodes);
  MDB_val val_data;
  val_id.mv_data = &id;
  db_mdb_status_require(db_mdb_env_cursor_open(txn, nodes));
  status.id = mdb_cursor_get(nodes, (&val_id), (&val_data), MDB_SET_KEY);
  if (db_mdb_status_is_success) {
    *result_data = val_data.mv_data;
    *result_size = val_data.mv_size;
  } else {
    if (db_mdb_status_is_notfound) {
      status.id = db_status_id_no_more_data;
      status.group = db_status_group_db;
    };
  };
exit:
  db_mdb_cursor_close_if_active(nodes);
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
/** delete a node and all its relations */
status_t db_node_delete(db_txn_t txn, db_ids_t* ids) {
  status_declare;
  db_mdb_declare_val_id;
  db_id_t id;
  MDB_val val_data;
  db_node_values_t values;
  db_mdb_cursor_declare(nodes);
  db_mdb_cursor_declare(graph_lr);
  db_mdb_cursor_declare(graph_rl);
  db_mdb_cursor_declare(graph_ll);
  /* return if ids is zero because in db-graph-internal-delete it would mean
   * non-filter and match all */
  if (!ids) {
    return (status);
  };
  db_mdb_status_require(db_mdb_env_cursor_open(txn, graph_lr));
  db_mdb_status_require(db_mdb_env_cursor_open(txn, graph_rl));
  db_mdb_status_require(db_mdb_env_cursor_open(txn, graph_ll));
  status_require(
    db_graph_internal_delete(ids, 0, 0, 0, graph_lr, graph_rl, graph_ll));
  status_require(
    db_graph_internal_delete(0, ids, 0, 0, graph_lr, graph_rl, graph_ll));
  status_require(
    db_graph_internal_delete(0, 0, ids, 0, graph_lr, graph_rl, graph_ll));
  db_mdb_status_require(db_mdb_env_cursor_open(txn, nodes));
  while (ids) {
    val_id.mv_data = db_ids_first_address(ids);
    status.id = mdb_cursor_get(nodes, (&val_id), (&val_data), MDB_SET_KEY);
    if (db_mdb_status_is_success) {
      id = db_ids_first(ids);
      status_require(
        (db_node_data_to_values((db_type_get_by_id((txn.env), db_id_type(id))),
          (val_data.mv_data),
          (val_data.mv_size),
          (&values))));
      status_require(db_indices_entry_delete(txn, values, id));
      db_mdb_status_require(mdb_cursor_del(nodes, 0));
    } else {
      if (db_mdb_status_is_notfound) {
        status.id = status_id_success;
      } else {
        status_set_group_goto(db_status_group_lmdb);
      };
    };
    ids = db_ids_rest(ids);
  };
exit:
  db_mdb_cursor_close_if_active(graph_lr);
  db_mdb_cursor_close_if_active(graph_rl);
  db_mdb_cursor_close_if_active(graph_ll);
  db_mdb_cursor_close_if_active(nodes);
  return (status);
};
void db_node_selection_destroy(db_node_selection_t* state) {
  if (state->cursor) {
    mdb_cursor_close((state->cursor));
  };
};
/** update node data. like node-delete followed by node-create but keeps the old
 * id */
status_t db_node_update(db_txn_t txn, db_id_t id, db_node_values_t values) {
  status_declare;
  db_mdb_declare_val_id;
  db_mdb_cursor_declare(nodes);
  MDB_val val_data;
  void* data;
  db_ids_t* ids;
  ids = db_ids_add(0, id);
  data = 0;
  val_id.mv_data = &id;
  status_require(
    (db_node_values_to_data(values, (&data), (&(val_data.mv_size)))));
  status_require(db_node_delete(txn, ids));
  val_data.mv_data = data;
  val_id.mv_data = &id;
  db_mdb_status_require(db_mdb_env_cursor_open(txn, nodes));
  db_mdb_status_require(mdb_cursor_put(nodes, (&val_id), (&val_data), 0));
  mdb_cursor_close(nodes);
  status_require(db_indices_entry_ensure(txn, values, id));
exit:
  db_mdb_cursor_close_if_active(nodes);
  free(data);
  return (status);
};
/** true if all given ids are ids of existing nodes, false otherwise */
status_t db_node_exists(db_txn_t txn, db_ids_t* ids, boolean* result) {
  status_declare;
  db_mdb_declare_val_id;
  db_mdb_declare_val_null;
  db_mdb_cursor_declare(nodes);
  db_mdb_status_require(db_mdb_env_cursor_open(txn, nodes));
  while (ids) {
    val_id.mv_data = db_ids_first_address(ids);
    status.id = mdb_cursor_get(nodes, (&val_id), (&val_null), MDB_SET);
    if (db_mdb_status_is_notfound) {
      *result = 0;
      status.id = status_id_success;
      goto exit;
    } else {
      if (!status_is_success) {
        status_goto;
      };
    };
    ids = db_ids_rest(ids);
  };
  *result = 1;
exit:
  mdb_cursor_close(nodes);
  return (status);
};