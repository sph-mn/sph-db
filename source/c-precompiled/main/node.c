/** convert a node-values array to the data format that is used as btree value for nodes.
  the data for unset trailing fields is not included */
status_t db_node_values_to_data(db_node_values_t values, db_node_t* result) {
  status_declare;
  void* data;
  uint8_t* data_temp;
  uint8_t field_size;
  db_field_type_t field_type;
  db_fields_len_t i;
  size_t size;
  size = 0;
  /* prepare size information */
  for (i = 0; (i <= values.last); i = (1 + i)) {
    if (i < (values.type)->fields_fixed_count) {
      /* fixed length field */
      field_type = (((values.type)->fields)[i]).type;
      field_size = db_field_type_size(field_type);
      ((values.data)[i]).size = field_size;
      size = (field_size + size);
    } else {
      field_size = ((values.data)[i]).size;
      size = (sizeof(db_data_len_t) + field_size + size);
      /* check if data is larger than the size prefix can specify */
      if (field_size > db_data_len_max) {
        status_set_both_goto(db_status_group_db, db_status_id_data_length);
      };
    };
  };
  db_malloc(data, size);
  data_temp = data;
  /* copy data */
  for (i = 0; (i <= values.last); i = (1 + i)) {
    field_size = ((values.data)[i]).size;
    if (i >= (values.type)->fields_fixed_count) {
      *((db_data_len_t*)(data_temp)) = field_size;
      data_temp = (sizeof(db_data_len_t) + data_temp);
    };
    memcpy(data_temp, (((values.data)[i]).data), field_size);
    data_temp = (field_size + data_temp);
  };
  result->data = data;
  result->size = size;
exit:
  return (status);
};
/** allocate memory for a new node values array */
status_t db_node_values_new(db_type_t* type, db_node_values_t* result) {
  status_declare;
  db_node_value_t* data;
  db_calloc(data, (type->fields_len), (sizeof(db_node_value_t)));
  (*result).type = type;
  (*result).data = data;
  (*result).last = 0;
exit:
  return (status);
};
void db_node_values_free(db_node_values_t* a) { free_and_set_null((a->data)); };
/** set a value for a field in node values.
  size is ignored for fixed length types */
void db_node_values_set(db_node_values_t* values, db_fields_len_t field, void* data, size_t size) {
  db_field_type_t field_type;
  db_node_values_t values_temp;
  values_temp = *values;
  field_type = (((values_temp.type)->fields)[field]).type;
  ((values_temp.data)[field]).data = data;
  ((values_temp.data)[field]).size = (db_field_type_is_fixed(field_type) ? db_field_type_size(field_type) : size);
  if (!values_temp.last || (field > values_temp.last)) {
    values_temp.last = field;
  };
  *values = values_temp;
};
status_t db_node_create(db_txn_t txn, db_node_values_t values, db_id_t* result) {
  status_declare;
  db_mdb_declare_val_id;
  db_mdb_cursor_declare(nodes);
  MDB_val val_data;
  db_id_t id;
  db_node_t node;
  node.data = 0;
  val_id.mv_data = &id;
  status_require((db_node_values_to_data(values, (&node))));
  val_data.mv_data = node.data;
  val_data.mv_size = node.size;
  db_mdb_status_require((db_mdb_env_cursor_open(txn, nodes)));
  /* sequence updated as late as possible */
  status_require((db_sequence_next((txn.env), ((values.type)->id), (&id))));
  db_mdb_status_require((mdb_cursor_put(nodes, (&val_id), (&val_data), 0)));
  db_mdb_cursor_close(nodes);
  status_require((db_indices_entry_ensure(txn, values, id)));
  *result = id;
exit:
  db_mdb_cursor_close_if_active(nodes);
  free((node.data));
  return (status);
};
/** from the full btree value a node (all fields), return a reference
  to the data for specific field and the size */
db_node_value_t db_node_ref(db_type_t* type, db_node_t node, db_fields_len_t field) {
  uint8_t* data_temp;
  uint8_t* end;
  db_fields_len_t i;
  size_t offset;
  db_node_value_t result;
  size_t size;
  if (field < type->fields_fixed_count) {
    /* fixed length field */
    offset = (type->fields_fixed_offsets)[field];
    if (offset < node.size) {
      result.data = (offset + ((uint8_t*)(node.data)));
      result.size = db_field_type_size((((type->fields)[field]).type));
    } else {
      result.data = 0;
      result.size = 0;
    };
    return (result);
  } else {
    /* variable length field */
    offset = (type->fields_fixed_count ? (type->fields_fixed_offsets)[type->fields_fixed_count] : 0);
    if (offset < node.size) {
      data_temp = (offset + ((uint8_t*)(node.data)));
      end = (node.size + ((uint8_t*)(node.data)));
      i = type->fields_fixed_count;
      /* variable length data is prefixed by its size */
      while (((i <= field) && (data_temp < end))) {
        size = *((db_data_len_t*)(data_temp));
        data_temp = (sizeof(db_data_len_t) + data_temp);
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
};
void db_free_node_values(db_node_values_t* values) { free_and_set_null((values->data)); };
status_t db_node_data_to_values(db_type_t* type, db_node_t data, db_node_values_t* result) {
  status_declare;
  db_node_value_t field_data;
  db_fields_len_t fields_len;
  db_node_values_t values;
  db_fields_len_t i;
  fields_len = type->fields_len;
  status_require((db_node_values_new(type, (&values))));
  for (i = 0; (i < fields_len); i = (1 + i)) {
    field_data = db_node_ref(type, data, i);
    if (!field_data.data) {
      break;
    };
    db_node_values_set((&values), i, (field_data.data), (field_data.size));
  };
  *result = values;
exit:
  if (status_is_failure) {
    db_free_node_values((&values));
  };
  return (status);
};
status_t db_node_read(db_node_selection_t selection, db_count_t count, db_nodes_t* result_nodes) {
  status_declare;
  db_mdb_declare_val_id;
  MDB_val val_data;
  db_node_matcher_t matcher;
  void* matcher_state;
  db_node_t node;
  db_id_t id;
  boolean skip;
  boolean match;
  db_type_id_t type_id;
  matcher = selection.matcher;
  matcher_state = selection.matcher_state;
  skip = (selection.options & db_selection_flag_skip);
  type_id = (selection.type)->id;
  db_mdb_status_require((mdb_cursor_get((selection.cursor), (&val_id), (&val_data), MDB_GET_CURRENT)));
  while ((db_mdb_status_is_success && count && (type_id == db_id_type((db_pointer_to_id((val_id.mv_data))))))) {
    if (matcher) {
      node.id = db_pointer_to_id((val_id.mv_data));
      node.data = val_data.mv_data;
      node.size = val_data.mv_size;
      match = matcher(node, matcher_state);
    } else {
      match = 1;
    };
    if (match) {
      if (!skip) {
        node.id = db_pointer_to_id((val_id.mv_data));
        node.data = val_data.mv_data;
        node.size = val_data.mv_size;
        i_array_add((*result_nodes), node);
      };
      count = (count - 1);
    };
    db_mdb_status_require((mdb_cursor_get((selection.cursor), (&val_id), (&val_data), MDB_NEXT_NODUP)));
  };
exit:
  db_mdb_status_notfound_if_notfound;
  return (status);
};
/** skip the next count matches */
status_t db_node_skip(db_node_selection_t selection, db_count_t count) {
  status_declare;
  selection.options = (selection.options | db_selection_flag_skip);
  status = db_node_read(selection, count, 0);
  selection.options = (selection.options ^ db_selection_flag_skip);
  return (status);
};
/** get nodes by type and optionally filtering data.
  result count is unknown on call or can be large, that is why a selection state
  for partial reading is used.
  offset: skip this number of matches first.
  matcher: zero if unused. a function that is called for each node of type
  matcher-state: zero if unused. a pointer passed to each call of matcher */
status_t db_node_select(db_txn_t txn, db_type_t* type, db_count_t offset, db_node_matcher_t matcher, void* matcher_state, db_node_selection_t* result_selection) {
  status_declare;
  db_mdb_declare_val_id;
  db_mdb_declare_val_null;
  db_mdb_cursor_declare(nodes);
  db_id_t id;
  db_mdb_status_require((db_mdb_env_cursor_open(txn, nodes)));
  /* position at first node of type */
  id = db_id_add_type(0, (type->id));
  val_id.mv_data = &id;
  db_mdb_status_require((mdb_cursor_get(nodes, (&val_id), (&val_null), MDB_SET_RANGE)));
  if (!(type->id == db_id_type((db_pointer_to_id((val_id.mv_data)))))) {
    status_set_id_goto(db_status_id_notfound);
  };
  result_selection->type = type;
  result_selection->cursor = nodes;
  result_selection->matcher = matcher;
  result_selection->matcher_state = matcher_state;
  result_selection->options = 0;
  if (offset) {
    status = db_node_skip((*result_selection), offset);
  };
exit:
  if (status_is_failure) {
    mdb_cursor_close(nodes);
    db_mdb_status_notfound_if_notfound;
  };
  return (status);
};
/** get nodes by id.
  returns and status is notfound if any id could not be found.
  like node-get with a given mdb-cursor */
status_t db_node_get_internal(MDB_cursor* nodes_cursor, db_ids_t ids, db_nodes_t* result_nodes) {
  status_declare;
  db_mdb_declare_val_id;
  MDB_val val_data;
  db_node_t node;
  while (i_array_in_range(ids)) {
    val_id.mv_data = ids.current;
    status.id = mdb_cursor_get(nodes_cursor, (&val_id), (&val_data), MDB_SET_KEY);
    if (db_mdb_status_is_success) {
      node.id = i_array_get(ids);
      node.data = val_data.mv_data;
      node.size = val_data.mv_size;
      i_array_add((*result_nodes), node);
    } else {
      if (db_mdb_status_is_notfound) {
        status_set_id_goto(db_status_id_notfound);
      } else {
        status_set_group_goto(db_status_group_lmdb);
      };
    };
    i_array_forward(ids);
  };
exit:
  return (status);
};
/** get a reference to data for one node identified by id.
  fields can be accessed with db-node-ref.
  if node could not be found, status is status-id-notfound */
status_t db_node_get(db_txn_t txn, db_ids_t ids, db_nodes_t* result_nodes) {
  status_declare;
  db_mdb_cursor_declare(nodes);
  db_mdb_status_require((db_mdb_env_cursor_open(txn, nodes)));
  status = db_node_get_internal(nodes, ids, result_nodes);
exit:
  db_mdb_cursor_close(nodes);
  return (status);
};
/** declare because it is defined later */
status_t db_graph_internal_delete(db_ids_t* left, db_ids_t* right, db_ids_t* label, db_ordinal_condition_t* ordinal, MDB_cursor* graph_lr, MDB_cursor* graph_rl, MDB_cursor* graph_ll);
/** delete nodes and all their relations */
status_t db_node_delete(db_txn_t txn, db_ids_t ids) {
  status_declare;
  db_mdb_declare_val_id;
  db_id_t id;
  MDB_val val_data;
  db_node_values_t values;
  db_node_t node;
  db_mdb_cursor_declare(nodes);
  db_mdb_cursor_declare(graph_lr);
  db_mdb_cursor_declare(graph_rl);
  db_mdb_cursor_declare(graph_ll);
  /* first delete references */
  db_mdb_status_require((db_mdb_env_cursor_open(txn, graph_lr)));
  db_mdb_status_require((db_mdb_env_cursor_open(txn, graph_rl)));
  db_mdb_status_require((db_mdb_env_cursor_open(txn, graph_ll)));
  status_require((db_graph_internal_delete((&ids), 0, 0, 0, graph_lr, graph_rl, graph_ll)));
  status_require((db_graph_internal_delete(0, (&ids), 0, 0, graph_lr, graph_rl, graph_ll)));
  status_require((db_graph_internal_delete(0, 0, (&ids), 0, graph_lr, graph_rl, graph_ll)));
  db_mdb_status_require((db_mdb_env_cursor_open(txn, nodes)));
  /* delete node and index btree entries */
  while (i_array_in_range(ids)) {
    val_id.mv_data = ids.current;
    status.id = mdb_cursor_get(nodes, (&val_id), (&val_data), MDB_SET_KEY);
    if (db_mdb_status_is_success) {
      id = i_array_get(ids);
      node.data = val_data.mv_data;
      node.size = val_data.mv_size;
      status_require((db_node_data_to_values((db_type_get_by_id((txn.env), (db_id_type(id)))), node, (&values))));
      status_require((db_indices_entry_delete(txn, values, id)));
      db_mdb_status_require((mdb_cursor_del(nodes, 0)));
    } else {
      if (db_mdb_status_is_notfound) {
        status.id = status_id_success;
      } else {
        status_set_group_goto(db_status_group_lmdb);
      };
    };
    i_array_forward(ids);
  };
exit:
  db_mdb_cursor_close_if_active(graph_lr);
  db_mdb_cursor_close_if_active(graph_rl);
  db_mdb_cursor_close_if_active(graph_ll);
  db_mdb_cursor_close_if_active(nodes);
  return (status);
};
status_t db_node_delete_type(db_txn_t txn, db_type_id_t type_id) {
  status_declare;
  /* delete all nodes of type and all their relations */
  db_mdb_declare_val_id;
  db_mdb_declare_val_null;
  db_mdb_cursor_declare(nodes);
  db_id_t id;
  db_mdb_status_require((db_mdb_env_cursor_open(txn, nodes)));
  id = db_id_add_type(((db_id_t)(0)), type_id);
  val_id.mv_data = &id;
  db_mdb_status_require((mdb_cursor_get(nodes, (&val_id), (&val_null), MDB_SET_RANGE)));
  while ((db_mdb_status_is_success && (type_id == db_id_type((db_pointer_to_id((val_id.mv_data))))))) {
    db_mdb_status_require((mdb_cursor_del(nodes, 0)));
    status.id = mdb_cursor_get(nodes, (&val_id), (&val_null), MDB_NEXT_NODUP);
  };
exit:
  db_mdb_status_notfound_if_notfound;
  db_mdb_cursor_close_if_active(nodes);
  return (status);
};
void db_node_selection_finish(db_node_selection_t* a) { db_mdb_cursor_close_if_active((a->cursor)); };
/** set new data for the node with the given id */
status_t db_node_update(db_txn_t txn, db_id_t id, db_node_values_t values) {
  status_declare;
  db_mdb_declare_val_id;
  db_mdb_cursor_declare(nodes);
  MDB_val val_data;
  db_node_t node;
  val_id.mv_data = &id;
  node.data = 0;
  status_require((db_node_values_to_data(values, (&node))));
  db_mdb_status_require((db_mdb_env_cursor_open(txn, nodes)));
  db_mdb_status_require((mdb_cursor_get(nodes, (&val_id), (&val_data), MDB_SET)));
  val_data.mv_data = node.data;
  val_data.mv_size = node.size;
  db_mdb_status_require((mdb_cursor_put(nodes, (&val_id), (&val_data), 0)));
  db_mdb_cursor_close(nodes);
  status_require((db_indices_entry_ensure(txn, values, id)));
exit:
  db_mdb_cursor_close_if_active(nodes);
  free((node.data));
  return (status);
};
/** delete nodes selected by type or custom matcher routine.
  collects ids in batches and calls db-node-delete */
status_t db_node_select_delete(db_txn_t txn, db_type_t* type, db_node_matcher_t matcher, void* matcher_state) {
  status_declare;
  i_array_declare(ids, db_ids_t);
  i_array_declare(nodes, db_nodes_t);
  db_node_selection_declare(selection);
  status_require((db_node_select(txn, type, 0, matcher, matcher_state, (&selection))));
  db_nodes_new(db_batch_len, (&nodes));
  db_ids_new(db_batch_len, (&ids));
  do {
    status_require_read((db_node_read(selection, db_batch_len, (&nodes))));
    if (!i_array_length(nodes)) {
      continue;
    };
    db_nodes_to_ids(nodes, (&ids));
    status_require((db_node_delete(txn, ids)));
    i_array_clear(ids);
    i_array_clear(nodes);
  } while (status_is_success);
exit:
  db_node_selection_finish((&selection));
  i_array_free(ids);
  i_array_free(nodes);
  return (status);
};
status_t db_node_index_read(db_node_index_selection_t selection, db_count_t count, db_ids_t temp_ids, db_nodes_t* result_nodes) {
  status_declare;
  status_require_read((db_index_read((selection.index_selection), count, (&temp_ids))));
  status_require((db_node_get_internal((selection.nodes_cursor), temp_ids, result_nodes)));
exit:
  return (status);
};
void db_node_index_selection_finish(db_node_index_selection_t* selection) {
  db_index_selection_finish((&(selection->index_selection)));
  db_mdb_cursor_close_if_active((selection->nodes_cursor));
};
status_t db_node_index_select(db_txn_t txn, db_index_t index, db_node_values_t values, db_node_index_selection_t* result_selection) {
  status_declare;
  db_mdb_cursor_declare(nodes);
  db_index_selection_declare(index_selection);
  status_require((db_index_select(txn, index, values, (&index_selection))));
  db_mdb_status_require((db_mdb_env_cursor_open(txn, nodes)));
  result_selection->index_selection = index_selection;
  result_selection->nodes_cursor = nodes;
exit:
  if (status_is_failure) {
    db_mdb_cursor_close_if_active(nodes);
  };
  return (status);
};