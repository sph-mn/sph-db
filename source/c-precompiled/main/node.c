/** convert a node-values array to the data format that is used as btree value for nodes.
  unset trailing fields are not included */
status_t db_node_values_to_data(db_node_values_t values, db_node_data_t* result) {
  status_declare;
  void* data;
  ui8* data_temp;
  ui8 field_size;
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
  db_calloc(data, (type->fields_len), sizeof(db_node_value_t));
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
  db_node_data_t node_data;
  node_data.data = 0;
  val_id.mv_data = &id;
  status_require(db_node_values_to_data(values, (&node_data)));
  val_data.mv_data = node_data.data;
  val_data.mv_size = node_data.size;
  db_mdb_status_require(db_mdb_env_cursor_open(txn, nodes));
  /* sequence updated as late as possible */
  status_require((db_sequence_next((txn.env), ((values.type)->id), (&id))));
  db_mdb_status_require(mdb_cursor_put(nodes, (&val_id), (&val_data), 0));
  db_mdb_cursor_close(nodes);
  status_require(db_indices_entry_ensure(txn, values, id));
  *result = id;
exit:
  db_mdb_cursor_close_if_active(nodes);
  free((node_data.data));
  return (status);
};
/** from the full btree value a node (all fields), return a reference
  to the data for specific field and the size */
db_node_data_t db_node_data_ref(db_type_t* type, db_node_data_t data, db_fields_len_t field) {
  ui8* data_temp;
  ui8* end;
  db_fields_len_t i;
  size_t offset;
  db_node_data_t result;
  size_t size;
  if (field < type->fields_fixed_count) {
    /* fixed length field */
    offset = (type->fields_fixed_offsets)[field];
    if (offset < data.size) {
      result.data = (offset + ((ui8*)(data.data)));
      result.size = db_field_type_size((((type->fields)[field]).type));
    } else {
      result.data = 0;
      result.size = 0;
    };
    return (result);
  } else {
    /* variable length field */
    offset = (type->fields_fixed_count ? (type->fields_fixed_offsets)[type->fields_fixed_count] : 0);
    if (offset < data.size) {
      data_temp = (offset + ((ui8*)(data.data)));
      end = (data.size + ((ui8*)(data.data)));
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
/** return a reference to the data for a field without copying */
db_node_data_t db_node_ref(db_node_selection_t* selection, db_fields_len_t field) { return ((db_node_data_ref((selection->type), (selection->current), field))); };
void db_free_node_values(db_node_values_t* values) { free_and_set_null((values->data)); };
status_t db_node_data_to_values(db_type_t* type, db_node_data_t data, db_node_values_t* result) {
  status_declare;
  db_node_data_t field_data;
  db_fields_len_t fields_len;
  db_node_values_t values;
  db_fields_len_t i;
  fields_len = type->fields_len;
  status_require(db_node_values_new(type, (&values)));
  for (i = 0; (i < fields_len); i = (1 + i)) {
    field_data = db_node_data_ref(type, data, i);
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
status_t db_node_next(db_node_selection_t* selection) {
  status_declare;
  db_mdb_declare_val_id;
  db_count_t count;
  MDB_val val_data;
  db_node_matcher_t matcher;
  void* matcher_state;
  db_node_data_t node_data;
  db_id_t id;
  db_ids_t ids;
  boolean skip;
  boolean match;
  db_type_id_t type_id;
  matcher = selection->matcher;
  matcher_state = selection->matcher_state;
  skip = (selection->options & db_selection_flag_skip);
  count = selection->count;
  ids = selection->ids;
  if (i_array_in_range(ids)) {
    /* filter by ids */
    while ((i_array_in_range(ids) && count)) {
      val_id.mv_data = ids.current;
      status.id = mdb_cursor_get((selection->cursor), (&val_id), (&val_data), MDB_SET_KEY);
      if (db_mdb_status_is_success) {
        if (matcher) {
          node_data.data = val_data.mv_data;
          node_data.size = val_data.mv_size;
          match = matcher(i_array_get(ids), node_data, matcher_state);
        } else {
          match = 1;
        };
        if (match) {
          if (!skip) {
            id = db_pointer_to_id((val_id.mv_data));
            selection->type = db_type_get_by_id((selection->env), db_id_type(id));
            selection->current.data = val_data.mv_data;
            selection->current.size = val_data.mv_size;
            selection->current_id = id;
          };
          count = (count - 1);
        };
      } else {
        db_mdb_status_expect_notfound;
      };
      i_array_forward(ids);
    };
    selection->ids = ids;
  } else {
    /* filter by type */
    type_id = selection->type->id;
    db_mdb_status_require((mdb_cursor_get((selection->cursor), (&val_id), (&val_data), MDB_GET_CURRENT)));
    while ((db_mdb_status_is_success && count && (type_id == db_id_type((db_pointer_to_id((val_id.mv_data))))))) {
      if (matcher) {
        node_data.data = val_data.mv_data;
        node_data.size = val_data.mv_size;
        match = matcher((db_pointer_to_id((val_id.mv_data))), node_data, matcher_state);
      } else {
        match = 1;
      };
      if (match) {
        if (!skip) {
          selection->current.data = val_data.mv_data;
          selection->current.size = val_data.mv_size;
          selection->current_id = db_pointer_to_id((val_id.mv_data));
        };
        count = (count - 1);
      };
      status.id = mdb_cursor_get((selection->cursor), (&val_id), (&val_data), MDB_NEXT_NODUP);
    };
  };
exit:
  db_mdb_status_notfound_if_notfound;
  return (status);
};
/** read the next count matches and position selection afterwards */
status_t db_node_skip(db_node_selection_t* selection, db_count_t count) {
  status_declare;
  selection->options = (selection->options | db_selection_flag_skip);
  selection->count = count;
  status = db_node_next(selection);
  selection->options = (selection->options ^ db_selection_flag_skip);
  selection->count = 1;
  return (status);
};
/** select nodes optionally filtered by either a list of ids or type.
  ids: zero if unused
  type: zero if unused
  offset: skip this number of matches first
  matcher: zero if unused. a function that is called for each node after filtering by ids or type
  matcher-state: zero if unused. a pointer passed to each call of matcher */
status_t db_node_select(db_txn_t txn, db_ids_t* ids, db_type_t* type, db_count_t offset, db_node_matcher_t matcher, void* matcher_state, db_node_selection_t* result_selection) {
  status_declare;
  db_mdb_declare_val_id;
  db_mdb_declare_val_null;
  db_mdb_cursor_declare(nodes);
  db_id_t id;
  db_mdb_status_require(db_mdb_env_cursor_open(txn, nodes));
  /* if ids filter set just check if database is empty */
  if (ids) {
    db_mdb_status_require(mdb_cursor_get(nodes, (&val_null), (&val_null), MDB_FIRST));
    result_selection->ids = *ids;
  } else {
    id = db_id_add_type(0, (type->id));
    val_id.mv_data = &id;
    i_array_set_null((result_selection->ids));
    db_mdb_status_require(mdb_cursor_get(nodes, (&val_id), (&val_null), MDB_SET_RANGE));
    if (!(type->id == db_id_type((db_pointer_to_id((val_id.mv_data)))))) {
      status_set_id_goto(db_status_id_notfound);
    };
  };
  result_selection->cursor = nodes;
  result_selection->count = 1;
  result_selection->env = txn.env;
  result_selection->matcher = matcher;
  result_selection->matcher_state = matcher_state;
  result_selection->options = 0;
  result_selection->type = type;
  if (offset) {
    status = db_node_skip(result_selection, offset);
  };
exit:
  if (!status_is_success) {
    mdb_cursor_close(nodes);
    db_mdb_status_notfound_if_notfound;
  };
  return (status);
};
/** used in node-get and node-index-get */
status_t db_node_get_internal(MDB_cursor* nodes, db_id_t id, db_node_data_t* result) {
  status_declare;
  db_mdb_declare_val_id;
  MDB_val val_data;
  val_id.mv_data = &id;
  status.id = mdb_cursor_get(nodes, (&val_id), (&val_data), MDB_SET_KEY);
  if (db_mdb_status_is_success) {
    result->data = val_data.mv_data;
    result->size = val_data.mv_size;
  } else {
    if (db_mdb_status_is_notfound) {
      db_mdb_status_notfound_if_notfound;
    } else {
      status.group = db_status_group_lmdb;
    };
  };
  return (status);
};
/** get a reference to data for one node identified by id.
  fields can be accessed with db-node-data-ref.
  if node could not be found, status is status-id-notfound */
status_t db_node_get(db_txn_t txn, db_id_t id, db_node_data_t* result) {
  status_declare;
  db_mdb_cursor_declare(nodes);
  db_mdb_status_require(db_mdb_env_cursor_open(txn, nodes));
  status = db_node_get_internal(nodes, id, result);
exit:
  db_mdb_cursor_close(nodes);
  return (status);
};
/** declare because it is defined later */
status_t db_graph_internal_delete(db_ids_t* left, db_ids_t* right, db_ids_t* label, db_ordinal_condition_t* ordinal, MDB_cursor* graph_lr, MDB_cursor* graph_rl, MDB_cursor* graph_ll);
/** delete a node and all its relations */
status_t db_node_delete(db_txn_t txn, db_ids_t* ids_pointer) {
  status_declare;
  db_mdb_declare_val_id;
  db_ids_t ids;
  db_id_t id;
  MDB_val val_data;
  db_node_values_t values;
  db_node_data_t node_data;
  ids = *ids_pointer;
  db_mdb_cursor_declare(nodes);
  db_mdb_cursor_declare(graph_lr);
  db_mdb_cursor_declare(graph_rl);
  db_mdb_cursor_declare(graph_ll);
  /* first delete references.
return if ids-pointer is zero because in db-graph-internal-delete it would mean non-filter and match all. */
  if (!ids_pointer) {
    return (status);
  };
  db_mdb_status_require(db_mdb_env_cursor_open(txn, graph_lr));
  db_mdb_status_require(db_mdb_env_cursor_open(txn, graph_rl));
  db_mdb_status_require(db_mdb_env_cursor_open(txn, graph_ll));
  status_require(db_graph_internal_delete(ids_pointer, 0, 0, 0, graph_lr, graph_rl, graph_ll));
  status_require(db_graph_internal_delete(0, ids_pointer, 0, 0, graph_lr, graph_rl, graph_ll));
  status_require(db_graph_internal_delete(0, 0, ids_pointer, 0, graph_lr, graph_rl, graph_ll));
  db_mdb_status_require(db_mdb_env_cursor_open(txn, nodes));
  /* delete node and index btree entries */
  while (i_array_in_range(ids)) {
    val_id.mv_data = ids.current;
    status.id = mdb_cursor_get(nodes, (&val_id), (&val_data), MDB_SET_KEY);
    if (db_mdb_status_is_success) {
      id = i_array_get(ids);
      node_data.data = val_data.mv_data;
      node_data.size = val_data.mv_size;
      status_require((db_node_data_to_values((db_type_get_by_id((txn.env), db_id_type(id))), node_data, (&values))));
      status_require(db_indices_entry_delete(txn, values, id));
      db_mdb_status_require(mdb_cursor_del(nodes, 0));
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
void db_node_selection_finish(db_node_selection_t* a) { db_mdb_cursor_close_if_active((a->cursor)); };
/** update node data. like node-delete followed by node-create but keeps the old id */
status_t db_node_update(db_txn_t txn, db_id_t id, db_node_values_t values) {
  status_declare;
  db_mdb_declare_val_id;
  db_mdb_cursor_declare(nodes);
  MDB_val val_data;
  db_node_data_t node_data;
  val_id.mv_data = &id;
  node_data.data = 0;
  status_require(db_node_values_to_data(values, (&node_data)));
  db_mdb_status_require(db_mdb_env_cursor_open(txn, nodes));
  db_mdb_status_require(mdb_cursor_get(nodes, (&val_id), (&val_data), MDB_SET));
  val_data.mv_data = node_data.data;
  val_data.mv_size = node_data.size;
  db_mdb_status_require(mdb_cursor_put(nodes, (&val_id), (&val_data), 0));
  db_mdb_cursor_close(nodes);
  status_require(db_indices_entry_ensure(txn, values, id));
exit:
  db_mdb_cursor_close_if_active(nodes);
  free((node_data.data));
  return (status);
};
/** true if all given ids are ids of existing nodes, false otherwise */
status_t db_node_exists(db_txn_t txn, db_ids_t ids, boolean* result) {
  status_declare;
  db_mdb_declare_val_id;
  db_mdb_declare_val_null;
  db_mdb_cursor_declare(nodes);
  db_mdb_status_require(db_mdb_env_cursor_open(txn, nodes));
  while (i_array_in_range(ids)) {
    val_id.mv_data = ids.current;
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
    i_array_forward(ids);
  };
  *result = 1;
exit:
  mdb_cursor_close(nodes);
  return (status);
};
status_t db_node_index_next(db_node_index_selection_t selection) {
  status_declare;
  status_require((db_index_next((selection.index_selection))));
  status_require((db_node_get_internal((selection.nodes), (selection.index_selection.current), (&(selection.current)))));
  selection.current_id = selection.index_selection.current;
exit:
  return (status);
};
void db_node_index_selection_finish(db_node_index_selection_t* selection) {
  db_index_selection_finish((&(selection->index_selection)));
  db_mdb_cursor_close_if_active((selection->nodes));
};
status_t db_node_index_select(db_txn_t txn, db_index_t index, db_node_values_t values, db_node_index_selection_t* result) {
  status_declare;
  db_mdb_cursor_declare(nodes);
  db_index_selection_t index_selection;
  status_require(db_index_select(txn, index, values, (&index_selection)));
  db_mdb_status_require(db_mdb_env_cursor_open(txn, nodes));
  result->index_selection = index_selection;
  result->nodes = nodes;
exit:
  if (status_is_failure) {
    db_mdb_cursor_close_if_active(nodes);
  };
  return (status);
};