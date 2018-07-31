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
#define db_graph_select_initialise_set(name, state) \
  imht_set_t* name##_set; \
  status_require(db_ids_to_set(name, (&name##_set))); \
  state->name = name##_set; \
  state->options = (db_graph_selection_flag_is_set_##name | state->options)
#define db_graph_reader_header(state) \
  status_declare; \
  db_mdb_declare_val_graph_key; \
  db_declare_graph_key(graph_key); \
  db_declare_graph_record(record); \
  db_graph_records_t* result_temp; \
  boolean skip; \
  skip = (db_selection_flag_skip & state->options)
#define db_graph_reader_header_0000(state) \
  status_declare; \
  db_mdb_declare_val_graph_key; \
  db_declare_graph_record(record); \
  db_graph_records_t* result_temp; \
  boolean skip; \
  skip = (db_selection_flag_skip & state->options)
#define db_graph_reader_get_ordinal_data(state) \
  db_ordinal_t ordinal_min = state->ordinal->min; \
  db_ordinal_t ordinal_max = state->ordinal->max
status_t db_graph_read_1000(db_graph_selection_t* state,
  db_count_t count,
  db_graph_records_t** result) {
  db_graph_reader_header(state);
  db_mdb_declare_val_graph_data;
  MDB_cursor* graph_lr;
  db_ids_t* left;
  graph_lr = state->cursor;
  left = state->left;
  db_mdb_status_require(mdb_cursor_get(
    graph_lr, (&val_graph_key), (&val_graph_data), MDB_GET_CURRENT));
  graph_key[0] = db_ids_first(left);
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
    left = db_ids_rest(left);
    if (left) {
      graph_key[0] = db_ids_first(left);
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
    db_graph_records_add_x((*result), record, result_temp);
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
  state->status = status;
  state->left = left;
  return (status);
};
status_t db_graph_read_1010(db_graph_selection_t* state,
  db_count_t count,
  db_graph_records_t** result) {
  db_graph_reader_header(state);
  db_mdb_declare_val_graph_data;
  MDB_cursor* graph_lr;
  db_ids_t* left;
  db_ids_t* left_first;
  db_ids_t* label;
  graph_lr = state->cursor;
  left = state->left;
  left_first = state->left_first;
  label = state->label;
  db_mdb_status_require(mdb_cursor_get(
    graph_lr, (&val_graph_key), (&val_graph_data), MDB_GET_CURRENT));
  graph_key[0] = db_ids_first(left);
  graph_key[1] = db_ids_first(label);
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
    left = db_ids_rest(left);
    if (left) {
      graph_key[0] = db_ids_first(left);
      goto set_key;
    } else {
      label = db_ids_rest(label);
      if (label) {
        left = left_first;
        graph_key[0] = db_ids_first(left);
        graph_key[1] = db_ids_first(label);
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
    db_graph_records_add_x((*result), record, result_temp);
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
  state->status = status;
  state->left = left;
  state->label = label;
  return (status);
};
status_t db_graph_read_1100(db_graph_selection_t* state,
  db_count_t count,
  db_graph_records_t** result) {
  db_mdb_declare_val_id;
  db_graph_reader_header(state);
  MDB_cursor* graph_rl;
  db_ids_t* left;
  db_ids_t* left_first;
  db_ids_t* right;
  graph_rl = state->cursor;
  left = state->left;
  left_first = state->left_first;
  right = state->right;
  db_mdb_status_require(
    mdb_cursor_get(graph_rl, (&val_graph_key), (&val_id), MDB_GET_CURRENT));
  graph_key[0] = db_ids_first(right);
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
    right = db_ids_rest(right);
    if (right) {
      graph_key[0] = db_ids_first(right);
    } else {
      notfound_exit;
    };
    goto set_range;
  };
each_left:
  stop_if_count_zero;
  val_id.mv_data = db_ids_first_address(left);
  status.id =
    mdb_cursor_get(graph_rl, (&val_graph_key), (&val_id), MDB_GET_BOTH);
  if (db_mdb_status_is_success) {
    if (!skip) {
      record.left = db_pointer_to_id((val_id.mv_data));
      record.right = db_pointer_to_id((val_graph_key.mv_data));
      record.label = db_pointer_to_id_at((val_graph_key.mv_data), 1);
      db_graph_records_add_x((*result), record, result_temp);
      reduce_count;
    };
  } else {
    db_mdb_status_expect_notfound;
  };
  left = db_ids_rest(left);
  if (left) {
    goto each_left;
  } else {
    left = left_first;
  };
  status.id =
    mdb_cursor_get(graph_rl, (&val_graph_key), (&val_id), MDB_NEXT_NODUP);
  goto each_right;
exit:
  state->status = status;
  state->left = left;
  state->right = right;
  return (status);
};
status_t db_graph_read_1110(db_graph_selection_t* state,
  db_count_t count,
  db_graph_records_t** result) {
  db_graph_reader_header(state);
  db_mdb_declare_val_id;
  MDB_cursor* graph_rl;
  db_ids_t* left;
  db_ids_t* left_first;
  db_ids_t* right;
  db_ids_t* right_first;
  db_ids_t* label;
  db_id_t id_left;
  graph_rl = state->cursor;
  left = state->left;
  left_first = state->left_first;
  right = state->right;
  right_first = state->right_first;
  label = state->label;
  graph_key[1] = db_ids_first(label);
  id_left = db_ids_first(left);
  graph_key[0] = db_ids_first(right);
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
  right = db_ids_rest(right);
  if (right) {
    stop_if_count_zero;
    graph_key[0] = db_ids_first(right);
    goto set_cursor;
  } else {
    right = right_first;
    graph_key[0] = db_ids_first(right);
    left = db_ids_rest(left);
    if (left) {
      stop_if_count_zero;
      id_left = db_ids_first(left);
      goto set_cursor;
    } else {
      left = left_first;
      id_left = db_ids_first(left);
      label = db_ids_rest(label);
      if (label) {
        stop_if_count_zero;
        graph_key[1] = db_ids_first(label);
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
    db_graph_records_add_x((*result), record, result_temp);
  };
  reduce_count;
  goto next_query;
exit:
  state->status = status;
  state->left = left;
  state->right = right;
  state->label = label;
  return (status);
};
status_t db_graph_read_1001_1101(db_graph_selection_t* state,
  db_count_t count,
  db_graph_records_t** result) {
  db_graph_reader_header(state);
  db_mdb_declare_val_graph_data;
  db_declare_graph_data(graph_data);
  MDB_cursor* graph_lr;
  db_ids_t* left;
  imht_set_t* right;
  graph_lr = state->cursor;
  left = state->left;
  right = state->right;
  db_graph_reader_get_ordinal_data(state);
  db_graph_data_set_ordinal(graph_data, ordinal_min);
  db_mdb_status_require(mdb_cursor_get(
    graph_lr, (&val_graph_key), (&val_graph_data), MDB_GET_CURRENT));
  if (left) {
    graph_key[0] = db_ids_first(left);
  } else {
    notfound_exit;
  };
  if (db_id_equal(
        (db_pointer_to_id((val_graph_key.mv_data))), (graph_key[0]))) {
    goto each_data;
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
  left = db_ids_rest(left);
  if (left) {
    graph_key[0] = db_ids_first(left);
  } else {
    notfound_exit;
  };
  goto each_left;
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
        record.label = db_pointer_to_id_at((val_graph_key.mv_data), 1);
        record.ordinal = db_graph_data_to_ordinal((val_graph_data.mv_data));
        record.right = db_graph_data_to_id((val_graph_data.mv_data));
        db_graph_records_add_x((*result), record, result_temp);
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
  state->status = status;
  state->left = left;
  return (status);
};
status_t db_graph_read_1011_1111(db_graph_selection_t* state,
  db_count_t count,
  db_graph_records_t** result) {
  db_graph_reader_header(state);
  db_declare_graph_data(graph_data);
  db_mdb_declare_val_graph_data;
  MDB_cursor* graph_lr;
  db_ids_t* left;
  db_ids_t* left_first;
  db_ids_t* label;
  imht_set_t* right;
  graph_lr = state->cursor;
  left = state->left;
  left_first = state->left_first;
  label = state->label;
  right = state->right;
  db_graph_reader_get_ordinal_data(state);
  db_graph_data_set_ordinal(graph_data, ordinal_min);
  db_mdb_status_require(mdb_cursor_get(
    graph_lr, (&val_graph_key), (&val_graph_data), MDB_GET_CURRENT));
  graph_key[0] = db_ids_first(left);
  graph_key[1] = db_ids_first(label);
  if (db_graph_key_equal(graph_key, db_mdb_val_to_graph_key(val_graph_key))) {
    goto each_data;
  } else {
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
      left = db_ids_rest(left);
      if (left) {
        graph_key[0] = db_ids_first(left);
      } else {
        label = db_ids_rest(label);
        if (label) {
          graph_key[1] = db_ids_first(label);
          left = left_first;
          graph_key[0] = db_ids_first(left);
        } else {
          notfound_exit;
        };
      };
      goto set_key;
    };
  };
each_data:
  stop_if_count_zero;
  if (!ordinal_max ||
    (db_graph_data_to_ordinal((val_graph_data.mv_data)) <= ordinal_max)) {
    if (!right ||
      imht_set_contains(
        right, (db_graph_data_to_id((val_graph_data.mv_data))))) {
      if (!skip) {
        record.left = db_pointer_to_id((val_graph_key.mv_data));
        record.right = db_graph_data_to_id((val_graph_data.mv_data));
        record.label = db_pointer_to_id_at((val_graph_key.mv_data), 1);
        record.ordinal = db_graph_data_to_ordinal((val_graph_data.mv_data));
        db_graph_records_add_x((*result), record, result_temp);
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
  state->status = status;
  state->left = left;
  state->label = label;
  return (status);
};
status_t db_graph_read_0010(db_graph_selection_t* state,
  db_count_t count,
  db_graph_records_t** result) {
  db_graph_reader_header(state);
  db_mdb_declare_val_id;
  db_mdb_declare_val_id_2;
  db_mdb_declare_val_graph_data;
  MDB_cursor* graph_ll;
  MDB_cursor* graph_lr;
  db_ids_t* label;
  db_id_t id_left;
  db_id_t id_label;
  graph_ll = state->cursor;
  graph_lr = state->cursor_2;
  label = state->label;
  db_mdb_status_require(
    mdb_cursor_get(graph_ll, (&val_id), (&val_id_2), MDB_GET_CURRENT));
  db_mdb_status_require(mdb_cursor_get(
    graph_lr, (&val_graph_key), (&val_graph_data), MDB_GET_CURRENT));
  if (label) {
    id_label = db_ids_first(label);
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
      label = db_ids_rest(label);
      if (label) {
        id_label = db_ids_first(label);
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
    db_graph_records_add_x((*result), record, result_temp);
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
    label = db_ids_rest(label);
    if (label) {
      id_label = db_ids_first(label);
    } else {
      notfound_exit;
    };
    goto set_label_key;
  };
exit:
  state->status = status;
  state->label = label;
  return (status);
};
status_t db_graph_read_0110(db_graph_selection_t* state,
  db_count_t count,
  db_graph_records_t** result) {
  db_graph_reader_header(state);
  db_mdb_declare_val_id;
  MDB_cursor* graph_rl;
  db_ids_t* label;
  db_ids_t* right;
  db_ids_t* right_first;
  graph_rl = state->cursor;
  label = state->label;
  right = state->right;
  right_first = state->right_first;
  db_mdb_status_require(
    mdb_cursor_get(graph_rl, (&val_graph_key), (&val_id), MDB_GET_CURRENT));
  graph_key[1] = db_ids_first(label);
  graph_key[0] = db_ids_first(right);
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
      right = db_ids_rest(right);
      if (right) {
        graph_key[0] = db_ids_first(right);
      } else {
        label = db_ids_rest(label);
        if (label) {
          graph_key[1] = db_ids_first(label);
          right = right_first;
          graph_key[0] = db_ids_first(right);
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
    db_graph_records_add_x((*result), record, result_temp);
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
  state->status = status;
  state->right = right;
  state->label = label;
  return (status);
};
status_t db_graph_read_0100(db_graph_selection_t* state,
  db_count_t count,
  db_graph_records_t** result) {
  db_graph_reader_header(state);
  db_mdb_declare_val_id;
  MDB_cursor* graph_rl;
  db_ids_t* right;
  graph_rl = state->cursor;
  right = state->right;
  db_mdb_status_require(
    mdb_cursor_get(graph_rl, (&val_graph_key), (&val_id), MDB_GET_CURRENT));
  graph_key[0] = db_ids_first(right);
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
    right = db_ids_rest(right);
    if (right) {
      graph_key[0] = db_ids_first(right);
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
    db_graph_records_add_x((*result), record, result_temp);
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
  right = db_ids_rest(right);
  if (right) {
    graph_key[0] = db_ids_first(right);
  } else {
    notfound_exit;
  };
  goto set_range;
exit:
  state->status = status;
  state->right = right;
  return (status);
};
status_t db_graph_read_0000(db_graph_selection_t* state,
  db_count_t count,
  db_graph_records_t** result) {
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
    db_graph_records_add_x((*result), record, result_temp);
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
  been exhausted */
status_t db_graph_select(db_txn_t txn,
  db_ids_t* left,
  db_ids_t* right,
  db_ids_t* label,
  db_ordinal_condition_t* ordinal,
  db_count_t offset,
  db_graph_selection_t* state) {
  status_declare;
  db_mdb_declare_val_null;
  db_mdb_cursor_declare(graph_lr);
  db_mdb_cursor_declare(graph_rl);
  db_mdb_cursor_declare(graph_ll);
  state->status = status;
  state->left = left;
  state->left_first = left;
  state->right = right;
  state->right_first = right;
  state->label = label;
  state->ordinal = ordinal;
  state->cursor = 0;
  state->cursor_2 = 0;
  state->options = 0;
  if (left) {
    if (ordinal) {
      if (right) {
        db_graph_select_initialise_set(right, state);
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
status_t db_graph_read(db_graph_selection_t* state,
  db_count_t count,
  db_graph_records_t** result) {
  status_declare;
  count = optional_count(count);
  status_require((state->status));
  status = ((db_graph_reader_t)(state->reader))(state, count, result);
exit:
  db_mdb_status_notfound_if_notfound;
  return (status);
};
void db_graph_selection_destroy(db_graph_selection_t* state) {
  db_mdb_cursor_close((state->cursor));
  db_mdb_cursor_close((state->cursor_2));
  if (db_graph_selection_flag_is_set_right & state->options) {
    imht_set_destroy(((imht_set_t*)(state->right)));
    state->right = 0;
  };
};