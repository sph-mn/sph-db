#define notfound_exit \
  status_set_both_goto(db_status_group_db, db_status_id_notfound)
#define db_graph_select_cursor_initialise(name, state, state_field_name) \
  db_mdb_status_require(db_mdb_env_cursor_open(txn, name)); \
  db_mdb_status_require( \
    mdb_cursor_get(name, (&val_null), (&val_null), MDB_FIRST)); \
  if (!db_mdb_status_is_success) { \
    db_mdb_status_expect_notfound; \
    status_set_both_goto(db_status_group_db, db_status_id_notfound); \
  }; \
  state->state_field_name = name
#define db_graph_reader_header(state) \
  status_declare; \
  db_mdb_declare_val_graph_key; \
  db_declare_graph_key(graph_key); \
  db_declare_graph_record(record); \
  boolean skip; \
  skip = (db_selection_flag_skip & state->options)
#define db_graph_reader_header_0000(state) \
  status_declare; \
  db_mdb_declare_val_graph_key; \
  db_declare_graph_record(record); \
  boolean skip; \
  skip = (db_selection_flag_skip & state->options)
#define db_graph_reader_define_ordinal_variables(state) \
  db_ordinal_t ordinal_min = state->ordinal->min; \
  db_ordinal_t ordinal_max = state->ordinal->max
status_t db_graph_read_1000(db_graph_selection_t* state,
  db_count_t count,
  db_graph_records_t* result) {
  db_graph_reader_header(state);
  db_mdb_declare_val_graph_data;
  MDB_cursor* graph_lr;
  db_ids_t left;
  graph_lr = state->cursor;
  left = state->left;
  db_mdb_status_require(mdb_cursor_get(
    graph_lr, (&val_graph_key), (&val_graph_data), MDB_GET_CURRENT));
  graph_key[0] = i_array_get(left);
  if (db_id_equal(
        (db_pointer_to_id((val_graph_key.mv_data))), (graph_key[0]))) {
    goto each_data;
  } else {
  set_range:
    val_graph_key.mv_data = graph_key;
    status.id = mdb_cursor_get(
      graph_lr, (&val_graph_key), (&val_graph_data), MDB_SET_RANGE);
  each_key:
    if (db_mdb_status_is_success) {
      if (db_id_equal(
            (db_pointer_to_id((val_graph_key.mv_data))), (graph_key[0]))) {
        goto each_data;
      };
    } else {
      db_mdb_status_expect_notfound;
    };
    i_array_forward(left);
    if (i_array_in_range(left)) {
      graph_key[0] = i_array_get(left);
      goto set_range;
    } else {
      notfound_exit;
    };
  };
each_data:
  stop_if_count_zero;
  if (!skip) {
    record.left = db_pointer_to_id((val_graph_key.mv_data));
    record.right = db_graph_data_to_id((val_graph_data.mv_data));
    record.label = db_pointer_to_id_at((val_graph_key.mv_data), 1);
    record.ordinal = db_graph_data_to_ordinal((val_graph_data.mv_data));
    i_array_add((*result), record);
  };
  reduce_count;
  status.id =
    mdb_cursor_get(graph_lr, (&val_graph_key), (&val_graph_data), MDB_NEXT_DUP);
  if (db_mdb_status_is_success) {
    goto each_data;
  } else {
    db_mdb_status_expect_notfound;
  };
  status.id = mdb_cursor_get(
    graph_lr, (&val_graph_key), (&val_graph_data), MDB_NEXT_NODUP);
  goto each_key;
exit:
  state->left.current = left.current;
  state->status = status;
  return (status);
};
status_t db_graph_read_1010(db_graph_selection_t* state,
  db_count_t count,
  db_graph_records_t* result) {
  db_graph_reader_header(state);
  db_mdb_declare_val_graph_data;
  MDB_cursor* graph_lr;
  db_ids_t left;
  db_ids_t label;
  graph_lr = state->cursor;
  left = state->left;
  label = state->label;
  db_mdb_status_require(mdb_cursor_get(
    graph_lr, (&val_graph_key), (&val_graph_data), MDB_GET_CURRENT));
  graph_key[0] = i_array_get(left);
  graph_key[1] = i_array_get(label);
  if (db_graph_key_equal(graph_key, db_mdb_val_to_graph_key(val_graph_key))) {
    goto each_data;
  } else {
  set_key:
    val_graph_key.mv_data = graph_key;
    status.id = mdb_cursor_get(
      graph_lr, (&val_graph_key), (&val_graph_data), MDB_SET_KEY);
    if (db_mdb_status_is_success) {
      goto each_data;
    } else {
      db_mdb_status_expect_notfound;
    };
  next_key:
    i_array_forward(left);
    if (i_array_in_range(left)) {
      graph_key[0] = i_array_get(left);
      goto set_key;
    } else {
      i_array_forward(label);
      if (i_array_in_range(label)) {
        i_array_rewind(left);
        graph_key[0] = i_array_get(left);
        graph_key[1] = i_array_get(label);
        goto set_key;
      } else {
        notfound_exit;
      };
    };
  };
each_data:
  stop_if_count_zero;
  if (!skip) {
    record.left = db_pointer_to_id((val_graph_key.mv_data));
    record.right = db_graph_data_to_id((val_graph_data.mv_data));
    record.label = db_pointer_to_id_at((val_graph_key.mv_data), 1);
    record.ordinal = db_graph_data_to_ordinal((val_graph_data.mv_data));
    i_array_add((*result), record);
  };
  reduce_count;
  status.id =
    mdb_cursor_get(graph_lr, (&val_graph_key), (&val_graph_data), MDB_NEXT_DUP);
  if (db_mdb_status_is_success) {
    goto each_data;
  } else {
    goto next_key;
  };
exit:
  state->left.current = left.current;
  state->label.current = label.current;
  state->status = status;
  return (status);
};
status_t db_graph_read_1100(db_graph_selection_t* state,
  db_count_t count,
  db_graph_records_t* result) {
  db_mdb_declare_val_id;
  db_graph_reader_header(state);
  MDB_cursor* graph_rl;
  db_ids_t left;
  db_ids_t right;
  graph_rl = state->cursor;
  left = state->left;
  right = state->right;
  db_mdb_status_require(
    mdb_cursor_get(graph_rl, (&val_graph_key), (&val_id), MDB_GET_CURRENT));
  graph_key[0] = i_array_get(right);
  if (db_id_equal(
        (db_pointer_to_id((val_graph_key.mv_data))), (graph_key[0]))) {
    goto each_left;
  } else {
  set_range:
    val_graph_key.mv_data = graph_key;
    status.id =
      mdb_cursor_get(graph_rl, (&val_graph_key), (&val_id), MDB_SET_RANGE);
  each_right:
    if (db_mdb_status_is_success) {
      if (db_id_equal(
            (db_pointer_to_id((val_graph_key.mv_data))), (graph_key[0]))) {
        goto each_left;
      };
    } else {
      db_mdb_status_expect_notfound;
    };
    i_array_forward(right);
    if (i_array_in_range(right)) {
      graph_key[0] = i_array_get(right);
    } else {
      notfound_exit;
    };
    goto set_range;
  };
each_left:
  stop_if_count_zero;
  val_id.mv_data = left.current;
  status.id =
    mdb_cursor_get(graph_rl, (&val_graph_key), (&val_id), MDB_GET_BOTH);
  if (db_mdb_status_is_success) {
    if (!skip) {
      record.left = db_pointer_to_id((val_id.mv_data));
      record.right = db_pointer_to_id((val_graph_key.mv_data));
      record.label = db_pointer_to_id_at((val_graph_key.mv_data), 1);
      i_array_add((*result), record);
      reduce_count;
    };
  } else {
    db_mdb_status_expect_notfound;
  };
  i_array_forward(left);
  if (i_array_in_range(left)) {
    goto each_left;
  } else {
    i_array_rewind(left);
  };
  status.id =
    mdb_cursor_get(graph_rl, (&val_graph_key), (&val_id), MDB_NEXT_NODUP);
  goto each_right;
exit:
  state->left.current = left.current;
  state->right.current = right.current;
  state->status = status;
  return (status);
};
status_t db_graph_read_1110(db_graph_selection_t* state,
  db_count_t count,
  db_graph_records_t* result) {
  db_graph_reader_header(state);
  db_mdb_declare_val_id;
  MDB_cursor* graph_rl;
  db_ids_t left;
  db_ids_t right;
  db_ids_t label;
  db_id_t id_left;
  graph_rl = state->cursor;
  left = state->left;
  right = state->right;
  label = state->label;
  graph_key[1] = i_array_get(label);
  id_left = i_array_get(left);
  graph_key[0] = i_array_get(right);
set_cursor:
  val_graph_key.mv_data = graph_key;
  val_id.mv_data = &id_left;
  status.id =
    mdb_cursor_get(graph_rl, (&val_graph_key), (&val_id), MDB_GET_BOTH);
  if (db_mdb_status_is_success) {
    goto match;
  } else {
    db_mdb_status_expect_notfound;
  };
next_query:
  i_array_forward(right);
  if (i_array_in_range(right)) {
    stop_if_count_zero;
    graph_key[0] = i_array_get(right);
    goto set_cursor;
  } else {
    i_array_rewind(right);
    graph_key[0] = i_array_get(right);
    i_array_forward(left);
    if (i_array_in_range(left)) {
      stop_if_count_zero;
      id_left = i_array_get(left);
      goto set_cursor;
    } else {
      i_array_rewind(left);
      id_left = i_array_get(left);
      i_array_forward(label);
      if (i_array_in_range(label)) {
        stop_if_count_zero;
        graph_key[1] = i_array_get(label);
        goto set_cursor;
      } else {
        notfound_exit;
      };
    };
  };
match:
  if (!skip) {
    record.left = db_pointer_to_id((val_id.mv_data));
    record.right = db_pointer_to_id((val_graph_key.mv_data));
    record.label = db_pointer_to_id_at((val_graph_key.mv_data), 1);
    i_array_add((*result), record);
  };
  reduce_count;
  goto next_query;
exit:
  state->left.current = left.current;
  state->right.current = right.current;
  state->label.current = label.current;
  state->status = status;
  return (status);
};
status_t db_graph_read_1001_1101(db_graph_selection_t* state,
  db_count_t count,
  db_graph_records_t* result) {
  db_graph_reader_header(state);
  db_mdb_declare_val_graph_data;
  db_declare_graph_data(graph_data);
  MDB_cursor* graph_lr;
  db_ids_t left;
  imht_set_t* right;
  graph_lr = state->cursor;
  left = state->left;
  right = state->ids_set;
  db_graph_reader_define_ordinal_variables(state);
  db_graph_data_set_ordinal(graph_data, ordinal_min);
  db_mdb_status_require(mdb_cursor_get(
    graph_lr, (&val_graph_key), (&val_graph_data), MDB_GET_CURRENT));
  if (i_array_in_range(left)) {
    graph_key[0] = i_array_get(left);
  } else {
    notfound_exit;
  };
  if (db_id_equal(
        (db_pointer_to_id((val_graph_key.mv_data))), (graph_key[0]))) {
    goto each_key;
  };
each_left:
  val_graph_key.mv_data = graph_key;
  status.id = mdb_cursor_get(
    graph_lr, (&val_graph_key), (&val_graph_data), MDB_SET_RANGE);
each_key:
  if (db_mdb_status_is_success) {
    if (db_id_equal(
          (db_pointer_to_id((val_graph_key.mv_data))), (graph_key[0]))) {
      val_graph_data.mv_data = graph_data;
      status.id = mdb_cursor_get(
        graph_lr, (&val_graph_key), (&val_graph_data), MDB_GET_BOTH_RANGE);
      if (db_mdb_status_is_success) {
        goto each_data;
      } else {
        db_mdb_status_expect_notfound;
      };
      status.id = mdb_cursor_get(
        graph_lr, (&val_graph_key), (&val_graph_data), MDB_NEXT_NODUP);
      goto each_key;
    };
  } else {
    db_mdb_status_expect_notfound;
  };
  i_array_forward(left);
  if (i_array_in_range(left)) {
    graph_key[0] = i_array_get(left);
  } else {
    notfound_exit;
  };
  goto each_left;
each_data:
  stop_if_count_zero;
  /* check ordinal-min for if the first element, which graph-select initialises
   * to, matches */
  if ((!ordinal_min ||
        (db_graph_data_to_ordinal((val_graph_data.mv_data)) >= ordinal_min)) &&
    (!ordinal_max ||
      (db_graph_data_to_ordinal((val_graph_data.mv_data)) <= ordinal_max))) {
    if (!right ||
      imht_set_contains(
        right, (db_graph_data_to_id((val_graph_data.mv_data))))) {
      if (!skip) {
        record.left = db_pointer_to_id((val_graph_key.mv_data));
        record.label = db_pointer_to_id_at((val_graph_key.mv_data), 1);
        record.ordinal = db_graph_data_to_ordinal((val_graph_data.mv_data));
        record.right = db_graph_data_to_id((val_graph_data.mv_data));
        i_array_add((*result), record);
      };
      reduce_count;
    };
    status.id = mdb_cursor_get(
      graph_lr, (&val_graph_key), (&val_graph_data), MDB_NEXT_DUP);
    if (db_mdb_status_is_success) {
      goto each_data;
    } else {
      db_mdb_status_expect_notfound;
    };
  };
  status.id = mdb_cursor_get(
    graph_lr, (&val_graph_key), (&val_graph_data), MDB_NEXT_NODUP);
  goto each_key;
exit:
  state->left.current = left.current;
  state->status = status;
  return (status);
};
status_t db_graph_read_1011_1111(db_graph_selection_t* state,
  db_count_t count,
  db_graph_records_t* result) {
  db_graph_reader_header(state);
  db_declare_graph_data(graph_data);
  db_mdb_declare_val_graph_data;
  MDB_cursor* graph_lr;
  db_ids_t left;
  db_ids_t label;
  imht_set_t* right;
  graph_lr = state->cursor;
  left = state->left;
  label = state->label;
  right = state->ids_set;
  db_graph_reader_define_ordinal_variables(state);
  db_graph_data_set_ordinal(graph_data, ordinal_min);
  db_mdb_status_require(mdb_cursor_get(
    graph_lr, (&val_graph_key), (&val_graph_data), MDB_GET_CURRENT));
  graph_key[0] = i_array_get(left);
  graph_key[1] = i_array_get(label);
set_key:
  val_graph_key.mv_data = graph_key;
  val_graph_data.mv_data = graph_data;
  status.id = mdb_cursor_get(
    graph_lr, (&val_graph_key), (&val_graph_data), MDB_GET_BOTH_RANGE);
  if (db_mdb_status_is_success) {
    goto each_data;
  } else {
    db_mdb_status_expect_notfound;
  each_key:
    i_array_forward(left);
    if (i_array_in_range(left)) {
      graph_key[0] = i_array_get(left);
    } else {
      i_array_forward(label);
      if (i_array_in_range(label)) {
        graph_key[1] = i_array_get(label);
        i_array_rewind(left);
        graph_key[0] = i_array_get(left);
      } else {
        notfound_exit;
      };
    };
    goto set_key;
  };
each_data:
  stop_if_count_zero;
  if ((!ordinal_min ||
        (db_graph_data_to_ordinal((val_graph_data.mv_data)) >= ordinal_min)) &&
    (!ordinal_max ||
      (db_graph_data_to_ordinal((val_graph_data.mv_data)) <= ordinal_max))) {
    if (!right ||
      imht_set_contains(
        right, (db_graph_data_to_id((val_graph_data.mv_data))))) {
      if (!skip) {
        record.left = db_pointer_to_id((val_graph_key.mv_data));
        record.right = db_graph_data_to_id((val_graph_data.mv_data));
        record.label = db_pointer_to_id_at((val_graph_key.mv_data), 1);
        record.ordinal = db_graph_data_to_ordinal((val_graph_data.mv_data));
        i_array_add((*result), record);
      };
      reduce_count;
    };
    status.id = mdb_cursor_get(
      graph_lr, (&val_graph_key), (&val_graph_data), MDB_NEXT_DUP);
    if (db_mdb_status_is_success) {
      goto each_data;
    } else {
      goto each_key;
    };
  } else {
    goto each_key;
  };
exit:
  state->left.current = left.current;
  state->label.current = label.current;
  state->status = status;
  return (status);
};
status_t db_graph_read_0010(db_graph_selection_t* state,
  db_count_t count,
  db_graph_records_t* result) {
  db_graph_reader_header(state);
  db_mdb_declare_val_id;
  db_mdb_declare_val_id_2;
  db_mdb_declare_val_graph_data;
  MDB_cursor* graph_ll;
  MDB_cursor* graph_lr;
  db_ids_t label;
  db_id_t id_left;
  db_id_t id_label;
  graph_ll = state->cursor;
  graph_lr = state->cursor_2;
  label = state->label;
  db_mdb_status_require(
    mdb_cursor_get(graph_ll, (&val_id), (&val_id_2), MDB_GET_CURRENT));
  db_mdb_status_require(mdb_cursor_get(
    graph_lr, (&val_graph_key), (&val_graph_data), MDB_GET_CURRENT));
  if (i_array_in_range(label)) {
    id_label = i_array_get(label);
  } else {
    notfound_exit;
  };
  if (db_id_equal(id_label, (db_pointer_to_id((val_id.mv_data))))) {
    graph_key[1] = id_label;
    goto each_label_data;
  } else {
  set_label_key:
    val_id.mv_data = &id_label;
    status.id = mdb_cursor_get(graph_ll, (&val_id), (&val_id_2), MDB_SET_KEY);
    if (db_mdb_status_is_success) {
      graph_key[1] = id_label;
      goto each_label_data;
    } else {
      db_mdb_status_expect_notfound;
      i_array_forward(label);
      if (i_array_in_range(label)) {
        id_label = i_array_get(label);
      } else {
        notfound_exit;
      };
      goto set_label_key;
    };
  };
each_label_data:
  id_left = db_pointer_to_id((val_id_2.mv_data));
  if (db_id_equal(id_left, (db_pointer_to_id((val_graph_key.mv_data))))) {
    goto each_left_data;
  } else {
    graph_key[0] = id_left;
    val_graph_key.mv_data = graph_key;
    status.id = mdb_cursor_get(
      graph_lr, (&val_graph_key), (&val_graph_data), MDB_SET_KEY);
    if (db_mdb_status_is_success) {
      goto each_left_data;
    } else {
      goto exit;
    };
  };
each_left_data:
  stop_if_count_zero;
  if (!skip) {
    record.left = id_left;
    record.right = db_graph_data_to_id((val_graph_data.mv_data));
    record.label = id_label;
    i_array_add((*result), record);
  };
  reduce_count;
  status.id =
    mdb_cursor_get(graph_lr, (&val_graph_key), (&val_graph_data), MDB_NEXT_DUP);
  if (db_mdb_status_is_success) {
    goto each_left_data;
  } else {
    db_mdb_status_expect_notfound;
  };
  status.id = mdb_cursor_get(graph_ll, (&val_id), (&val_id_2), MDB_NEXT_DUP);
  if (db_mdb_status_is_success) {
    goto each_label_data;
  } else {
    i_array_forward(label);
    if (i_array_in_range(label)) {
      id_label = i_array_get(label);
    } else {
      notfound_exit;
    };
    goto set_label_key;
  };
exit:
  state->status = status;
  state->label.current = label.current;
  return (status);
};
status_t db_graph_read_0110(db_graph_selection_t* state,
  db_count_t count,
  db_graph_records_t* result) {
  db_graph_reader_header(state);
  db_mdb_declare_val_id;
  MDB_cursor* graph_rl;
  db_ids_t label;
  db_ids_t right;
  graph_rl = state->cursor;
  label = state->label;
  right = state->right;
  db_mdb_status_require(
    mdb_cursor_get(graph_rl, (&val_graph_key), (&val_id), MDB_GET_CURRENT));
  graph_key[1] = i_array_get(label);
  graph_key[0] = i_array_get(right);
  if (db_graph_key_equal(graph_key, db_mdb_val_to_graph_key(val_graph_key))) {
    goto each_data;
  } else {
  set_key:
    val_graph_key.mv_data = graph_key;
    status.id =
      mdb_cursor_get(graph_rl, (&val_graph_key), (&val_id), MDB_SET_KEY);
    if (db_mdb_status_is_success) {
      goto each_data;
    } else {
    each_key:
      db_mdb_status_expect_notfound;
      i_array_forward(right);
      if (i_array_in_range(right)) {
        graph_key[0] = i_array_get(right);
      } else {
        i_array_forward(label);
        if (i_array_in_range(label)) {
          graph_key[1] = i_array_get(label);
          i_array_rewind(right);
          graph_key[0] = i_array_get(right);
        } else {
          notfound_exit;
        };
      };
      goto set_key;
    };
  };
each_data:
  stop_if_count_zero;
  if (!skip) {
    record.left = db_pointer_to_id((val_id.mv_data));
    record.right = graph_key[0];
    record.label = graph_key[1];
    i_array_add((*result), record);
  };
  reduce_count;
  status.id =
    mdb_cursor_get(graph_rl, (&val_graph_key), (&val_id), MDB_NEXT_DUP);
  if (db_mdb_status_is_success) {
    goto each_data;
  } else {
    goto each_key;
  };
exit:
  state->right.current = right.current;
  state->label.current = label.current;
  state->status = status;
  return (status);
};
status_t db_graph_read_0100(db_graph_selection_t* state,
  db_count_t count,
  db_graph_records_t* result) {
  db_graph_reader_header(state);
  db_mdb_declare_val_id;
  MDB_cursor* graph_rl;
  db_ids_t right;
  graph_rl = state->cursor;
  right = state->right;
  db_mdb_status_require(
    mdb_cursor_get(graph_rl, (&val_graph_key), (&val_id), MDB_GET_CURRENT));
  graph_key[0] = i_array_get(right);
  if (db_id_equal(
        (graph_key[0]), (db_pointer_to_id((val_graph_key.mv_data))))) {
    goto each_key;
  } else {
  set_range:
    val_graph_key.mv_data = graph_key;
    status.id =
      mdb_cursor_get(graph_rl, (&val_graph_key), (&val_id), MDB_SET_RANGE);
    if (db_mdb_status_is_success) {
      if (db_id_equal(
            (graph_key[0]), (db_pointer_to_id((val_graph_key.mv_data))))) {
        goto each_key;
      };
    } else {
      db_mdb_status_expect_notfound;
    };
    i_array_forward(right);
    if (i_array_in_range(right)) {
      graph_key[0] = i_array_get(right);
    } else {
      notfound_exit;
    };
    goto set_range;
  };
each_key:
each_data:
  stop_if_count_zero;
  if (!skip) {
    record.left = db_pointer_to_id((val_id.mv_data));
    record.right = db_pointer_to_id((val_graph_key.mv_data));
    record.label = db_pointer_to_id_at((val_graph_key.mv_data), 1);
    i_array_add((*result), record);
  };
  reduce_count;
  status.id =
    mdb_cursor_get(graph_rl, (&val_graph_key), (&val_id), MDB_NEXT_DUP);
  if (db_mdb_status_is_success) {
    goto each_data;
  } else {
    db_mdb_status_expect_notfound;
  };
  status.id =
    mdb_cursor_get(graph_rl, (&val_graph_key), (&val_id), MDB_NEXT_NODUP);
  if (db_mdb_status_is_success) {
    if (db_id_equal(
          (graph_key[0]), (db_pointer_to_id((val_graph_key.mv_data))))) {
      goto each_key;
    };
  } else {
    db_mdb_status_expect_notfound;
  };
  i_array_forward(right);
  if (i_array_in_range(right)) {
    graph_key[0] = i_array_get(right);
  } else {
    notfound_exit;
  };
  goto set_range;
exit:
  state->status = status;
  state->right.current = right.current;
  return (status);
};
status_t db_graph_read_0000(db_graph_selection_t* state,
  db_count_t count,
  db_graph_records_t* result) {
  db_graph_reader_header_0000(state);
  db_mdb_declare_val_graph_data;
  MDB_cursor* graph_lr;
  graph_lr = state->cursor;
  db_mdb_status_require(mdb_cursor_get(
    graph_lr, (&val_graph_key), (&val_graph_data), MDB_GET_CURRENT));
each_key:
each_data:
  stop_if_count_zero;
  if (!skip) {
    record.left = db_pointer_to_id((val_graph_key.mv_data));
    record.right = db_graph_data_to_id((val_graph_data.mv_data));
    record.label = db_pointer_to_id_at((val_graph_key.mv_data), 1);
    record.ordinal = db_graph_data_to_ordinal((val_graph_data.mv_data));
    i_array_add((*result), record);
  };
  reduce_count;
  status.id =
    mdb_cursor_get(graph_lr, (&val_graph_key), (&val_graph_data), MDB_NEXT_DUP);
  if (db_mdb_status_is_success) {
    goto each_data;
  } else {
    db_mdb_status_expect_notfound;
  };
  status.id = mdb_cursor_get(
    graph_lr, (&val_graph_key), (&val_graph_data), MDB_NEXT_NODUP);
  if (db_mdb_status_is_success) {
    goto each_key;
  } else {
    db_mdb_status_expect_notfound;
  };
exit:
  state->status = status;
  return (status);
};
/** prepare the state and select the reader.
  readers are specialised for filter combinations.
  the 1/0 pattern at the end of reader names corresponds to the filter
  combination the reader is supposed to handle. 1 stands for filter given, 0
  stands for not given. order is left, right, label, ordinal. readers always
  leave cursors at a valid entry, usually the next entry unless the results have
  been exhausted. left/right/label ids pointer can be zero which means they are
  unused. internally in the selection if unset i-array-in-range and
  i-array-length is zero */
status_t db_graph_select(db_txn_t txn,
  db_ids_t* left,
  db_ids_t* right,
  db_ids_t* label,
  db_ordinal_condition_t* ordinal,
  db_count_t offset,
  db_graph_selection_t* state) {
  status_declare;
  db_mdb_declare_val_null;
  imht_set_t* right_set;
  db_mdb_cursor_declare(graph_lr);
  db_mdb_cursor_declare(graph_rl);
  db_mdb_cursor_declare(graph_ll);
  if (left) {
    state->left = *left;
  } else {
    i_array_set_null((state->left));
  };
  if (right) {
    state->right = *right;
  } else {
    i_array_set_null((state->right));
  };
  if (label) {
    state->label = *label;
  } else {
    i_array_set_null((state->label));
  };
  state->ids_set = 0;
  state->status = status;
  state->ordinal = ordinal;
  state->cursor = 0;
  state->cursor_2 = 0;
  state->options = 0;
  if (left) {
    if (ordinal) {
      if (right) {
        status_require(db_ids_to_set((*right), (&right_set)));
        state->ids_set = right_set;
        state->options =
          (db_graph_selection_flag_is_set_right | state->options);
      };
      db_graph_select_cursor_initialise(graph_lr, state, cursor);
      if (label) {
        state->reader = db_graph_read_1011_1111;
      } else {
        state->reader = db_graph_read_1001_1101;
      };
    } else {
      if (right) {
        db_graph_select_cursor_initialise(graph_rl, state, cursor);
        if (label) {
          state->reader = db_graph_read_1110;
        } else {
          state->reader = db_graph_read_1100;
        };
      } else {
        db_graph_select_cursor_initialise(graph_lr, state, cursor);
        if (label) {
          state->reader = db_graph_read_1010;
        } else {
          state->reader = db_graph_read_1000;
        };
      };
    };
  } else {
    if (right) {
      db_graph_select_cursor_initialise(graph_rl, state, cursor);
      state->reader = (label ? db_graph_read_0110 : db_graph_read_0100);
    } else {
      if (label) {
        db_graph_select_cursor_initialise(graph_ll, state, cursor);
        db_graph_select_cursor_initialise(graph_lr, state, cursor_2);
        state->reader = db_graph_read_0010;
      } else {
        db_graph_select_cursor_initialise(graph_lr, state, cursor);
        state->reader = db_graph_read_0000;
      };
    };
  };
  db_graph_reader_t reader = state->reader;
  if (offset) {
    state->options = (db_selection_flag_skip | state->options);
    status = reader(state, offset, 0);
    if (!db_mdb_status_is_success) {
      db_mdb_status_expect_notfound;
    };
    state->options = (db_selection_flag_skip ^ state->options);
  };
exit:
  db_mdb_status_notfound_if_notfound;
  state->status = status;
  return (status);
};
/** result memory is to be allocated by the caller */
status_t db_graph_read(db_graph_selection_t* state,
  db_count_t count,
  db_graph_records_t* result) {
  status_declare;
  status_require((state->status));
  status = ((db_graph_reader_t)(state->reader))(
    state, (!count ? i_array_max_length((*result)) : count), result);
exit:
  db_mdb_status_notfound_if_notfound;
  return (status);
};
void db_graph_selection_destroy(db_graph_selection_t* state) {
  db_mdb_cursor_close((state->cursor));
  db_mdb_cursor_close((state->cursor_2));
  if (db_graph_selection_flag_is_set_right & state->options) {
    imht_set_destroy((state->ids_set));
    state->ids_set = 0;
  };
};