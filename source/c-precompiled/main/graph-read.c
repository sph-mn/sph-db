#define no_more_data_exit \
  status_set_both_goto(db_status_group_db, db_status_id_no_more_data)
#define db_graph_select_cursor_initialise(name, state, state_field_name) \
  db_mdb_cursor_open(txn.mdb_txn, (*txn.s).dbi_##name, name); \
  status_set_id(mdb_cursor_get(name, &val_null, &val_null, MDB_FIRST)); \
  if (!db_mdb_status_success_p) { \
    db_mdb_status_require_notfound; \
    status_set_both_goto(db_status_group_db, db_status_id_no_more_data); \
  }; \
  (*state).state_field_name = name
#define db_graph_select_initialise_set(name, state) \
  imht_set_t* name##_set; \
  status_require_x(db_ids_to_set(name, &name##_set)); \
  (*state).name = name##_set; \
  (*state).options = (db_read_option_is_set_##name | (*state).options)
#define db_graph_reader_header(state) \
  status_init; \
  db_mdb_declare_val_graph_key; \
  db_define_graph_key(graph_key); \
  db_define_graph_record(record); \
  db_graph_records_t* result_temp; \
  boolean skip_p = (db_read_option_skip & (*state).options)
#define db_graph_reader_header_0000(state) \
  status_init; \
  db_mdb_declare_val_graph_key; \
  db_define_graph_record(record); \
  db_graph_records_t* result_temp; \
  boolean skip_p = (db_read_option_skip & (*state).options)
#define db_graph_reader_get_ordinal_data(state) \
  db_ordinal_t ordinal_min = (*(*state).ordinal).min; \
  db_ordinal_t ordinal_max = (*(*state).ordinal).max
status_t db_graph_read_1000(db_graph_read_state_t* state,
  b32 count,
  db_graph_records_t** result) {
  db_graph_reader_header(state);
  db_mdb_declare_val_graph_data;
  MDB_cursor* graph_lr = (*state).cursor;
  db_ids_t* left = (*state).left;
  db_mdb_cursor_get_x(graph_lr, val_graph_key, val_graph_data, MDB_GET_CURRENT);
  db_mdb_status_require;
  (*(graph_key + 0)) = db_ids_first(left);
  if (db_id_equal_p(
        db_mdb_val_to_id_at(val_graph_key, 0), (*(graph_key + 0)))) {
    goto each_data;
  } else {
  set_range:
    val_graph_key.mv_data = graph_key;
    db_mdb_cursor_get_x(graph_lr, val_graph_key, val_graph_data, MDB_SET_RANGE);
  each_key:
    if (db_mdb_status_success_p) {
      if (db_id_equal_p(
            db_mdb_val_to_id_at(val_graph_key, 0), (*(graph_key + 0)))) {
        goto each_data;
      };
    } else {
      db_mdb_status_require_notfound;
    };
    left = db_ids_rest(left);
    if (left) {
      (*(graph_key + 0)) = db_ids_first(left);
      goto set_range;
    } else {
      no_more_data_exit;
    };
  };
each_data:
  stop_if_count_zero;
  if (!skip_p) {
    record.left = db_mdb_val_to_id_at(val_graph_key, 0);
    record.right = db_mdb_val_graph_data_to_id(val_graph_data);
    record.label = db_mdb_val_to_id_at(val_graph_key, 1);
    record.ordinal = db_mdb_val_graph_data_to_ordinal(val_graph_data);
    db_graph_records_add_x((*result), record, result_temp);
  };
  reduce_count;
  db_mdb_cursor_next_dup_x(graph_lr, val_graph_key, val_graph_data);
  if (db_mdb_status_success_p) {
    goto each_data;
  } else {
    db_mdb_status_require_notfound;
  };
  db_mdb_cursor_next_nodup_x(graph_lr, val_graph_key, val_graph_data);
  goto each_key;
exit:
  (*state).status = status;
  (*state).left = left;
  return (status);
};
status_t db_graph_read_1010(db_graph_read_state_t* state,
  b32 count,
  db_graph_records_t** result) {
  db_graph_reader_header(state);
  db_mdb_declare_val_graph_data;
  MDB_cursor* graph_lr = (*state).cursor;
  db_ids_t* left = (*state).left;
  db_ids_t* left_first = (*state).left_first;
  db_ids_t* label = (*state).label;
  db_mdb_cursor_get_x(graph_lr, val_graph_key, val_graph_data, MDB_GET_CURRENT);
  db_mdb_status_require;
  (*(graph_key + 0)) = db_ids_first(left);
  (*(graph_key + 1)) = db_ids_first(label);
  if (db_graph_key_equal_p(graph_key, db_mdb_val_to_graph_key(val_graph_key))) {
    goto each_data;
  } else {
  set_key:
    val_graph_key.mv_data = graph_key;
    db_mdb_cursor_get_x(graph_lr, val_graph_key, val_graph_data, MDB_SET_KEY);
    if (db_mdb_status_success_p) {
      goto each_data;
    } else {
      db_mdb_status_require_notfound;
    };
  next_key:
    left = db_ids_rest(left);
    if (left) {
      (*(graph_key + 0)) = db_ids_first(left);
      goto set_key;
    } else {
      label = db_ids_rest(label);
      if (label) {
        left = left_first;
        (*(graph_key + 0)) = db_ids_first(left);
        (*(graph_key + 1)) = db_ids_first(label);
        goto set_key;
      } else {
        no_more_data_exit;
      };
    };
  };
each_data:
  stop_if_count_zero;
  if (!skip_p) {
    record.left = db_mdb_val_to_id_at(val_graph_key, 0);
    record.right = db_mdb_val_graph_data_to_id(val_graph_data);
    record.label = db_mdb_val_to_id_at(val_graph_key, 1);
    record.ordinal = db_mdb_val_graph_data_to_ordinal(val_graph_data);
    db_graph_records_add_x((*result), record, result_temp);
  };
  reduce_count;
  db_mdb_cursor_next_dup_x(graph_lr, val_graph_key, val_graph_data);
  if (db_mdb_status_success_p) {
    goto each_data;
  } else {
    goto next_key;
  };
exit:
  (*state).status = status;
  (*state).left = left;
  (*state).label = label;
  return (status);
};
status_t db_graph_read_1100(db_graph_read_state_t* state,
  b32 count,
  db_graph_records_t** result) {
  db_graph_reader_header(state);
  db_mdb_declare_val_id;
  MDB_cursor* graph_rl = (*state).cursor;
  db_ids_t* left = (*state).left;
  db_ids_t* left_first = (*state).left_first;
  db_ids_t* right = (*state).right;
  db_mdb_cursor_get_x(graph_rl, val_graph_key, val_id, MDB_GET_CURRENT);
  db_mdb_status_require;
  (*(graph_key + 0)) = db_ids_first(right);
  if (db_id_equal_p(
        db_mdb_val_to_id_at(val_graph_key, 0), (*(graph_key + 0)))) {
    goto each_left;
  } else {
  set_range:
    val_graph_key.mv_data = graph_key;
    db_mdb_cursor_get_x(graph_rl, val_graph_key, val_id, MDB_SET_RANGE);
  each_right:
    if (db_mdb_status_success_p) {
      if (db_id_equal_p(
            db_mdb_val_to_id_at(val_graph_key, 0), (*(graph_key + 0)))) {
        goto each_left;
      };
    } else {
      db_mdb_status_require_notfound;
    };
    right = db_ids_rest(right);
    if (right) {
      (*(graph_key + 0)) = db_ids_first(right);
    } else {
      no_more_data_exit;
    };
    goto set_range;
  };
each_left:
  stop_if_count_zero;
  val_id.mv_data = db_ids_first_address(left);
  db_mdb_cursor_get_x(graph_rl, val_graph_key, val_id, MDB_GET_BOTH);
  if (db_mdb_status_success_p) {
    if (!skip_p) {
      record.left = db_mdb_val_to_id(val_id);
      record.right = db_mdb_val_to_id_at(val_graph_key, 0);
      record.label = db_mdb_val_to_id_at(val_graph_key, 1);
      db_graph_records_add_x((*result), record, result_temp);
      reduce_count;
    };
  } else {
    db_mdb_status_require_notfound;
  };
  left = db_ids_rest(left);
  if (left) {
    goto each_left;
  } else {
    left = left_first;
  };
  db_mdb_cursor_next_nodup_x(graph_rl, val_graph_key, val_id);
  goto each_right;
exit:
  (*state).status = status;
  (*state).left = left;
  (*state).right = right;
  return (status);
};
status_t db_graph_read_1110(db_graph_read_state_t* state,
  b32 count,
  db_graph_records_t** result) {
  db_graph_reader_header(state);
  db_mdb_declare_val_id;
  MDB_cursor* graph_rl = (*state).cursor;
  db_ids_t* left = (*state).left;
  db_ids_t* left_first = (*state).left_first;
  db_ids_t* right = (*state).right;
  db_ids_t* right_first = (*state).right_first;
  db_ids_t* label = (*state).label;
  db_id_t id_left;
  (*(graph_key + 1)) = db_ids_first(label);
  id_left = db_ids_first(left);
  (*(graph_key + 0)) = db_ids_first(right);
set_cursor:
  val_graph_key.mv_data = graph_key;
  val_id.mv_data = &id_left;
  db_mdb_cursor_get_x(graph_rl, val_graph_key, val_id, MDB_GET_BOTH);
  if (db_mdb_status_success_p) {
    goto match;
  } else {
    db_mdb_status_require_notfound;
  };
next_query:
  right = db_ids_rest(right);
  if (right) {
    stop_if_count_zero;
    (*(graph_key + 0)) = db_ids_first(right);
    goto set_cursor;
  } else {
    right = right_first;
    (*(graph_key + 0)) = db_ids_first(right);
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
        (*(graph_key + 1)) = db_ids_first(label);
        goto set_cursor;
      } else {
        no_more_data_exit;
      };
    };
  };
match:
  if (!skip_p) {
    record.left = db_mdb_val_to_id(val_id);
    record.right = db_mdb_val_to_id_at(val_graph_key, 0);
    record.label = db_mdb_val_to_id_at(val_graph_key, 1);
    db_graph_records_add_x((*result), record, result_temp);
  };
  reduce_count;
  goto next_query;
exit:
  (*state).status = status;
  (*state).left = left;
  (*state).right = right;
  (*state).label = label;
  return (status);
};
status_t db_graph_read_1001_1101(db_graph_read_state_t* state,
  b32 count,
  db_graph_records_t** result) {
  db_graph_reader_header(state);
  db_mdb_declare_val_graph_data;
  MDB_cursor* graph_lr = (*state).cursor;
  db_ids_t* left = (*state).left;
  imht_set_t* right = (*state).right;
  db_graph_reader_get_ordinal_data(state);
  db_define_graph_data(graph_data);
  db_graph_data_set_ordinal(graph_data, ordinal_min);
  db_mdb_cursor_get_x(graph_lr, val_graph_key, val_graph_data, MDB_GET_CURRENT);
  db_mdb_status_require;
  if (left) {
    (*(graph_key + 0)) = db_ids_first(left);
  } else {
    no_more_data_exit;
  };
  if ((db_id_equal_p(
         db_mdb_val_to_id_at(val_graph_key, 0), (*(graph_key + 0))) &&
        ((!ordinal_min ||
          ((db_mdb_val_graph_data_to_ordinal(val_graph_data) >=
            ordinal_min)))) &&
        ((!ordinal_max ||
          ((db_mdb_val_graph_data_to_ordinal(val_graph_data) <=
            ordinal_max)))))) {
    goto each_data;
  } else {
  each_left:
    val_graph_key.mv_data = graph_key;
    db_mdb_cursor_get_x(graph_lr, val_graph_key, val_graph_data, MDB_SET_RANGE);
  each_key:
    if (db_mdb_status_success_p) {
      if (db_id_equal_p(
            db_mdb_val_to_id_at(val_graph_key, 0), (*(graph_key + 0)))) {
        val_graph_data.mv_data = graph_data;
        db_mdb_cursor_get_x(
          graph_lr, val_graph_key, val_graph_data, MDB_GET_BOTH_RANGE);
        if (db_mdb_status_success_p) {
          goto each_data;
        } else {
          db_mdb_status_require_notfound;
        };
        db_mdb_cursor_next_nodup_x(graph_lr, val_graph_key, val_graph_data);
        goto each_key;
      };
    } else {
      db_mdb_status_require_notfound;
    };
    left = db_ids_rest(left);
    if (left) {
      (*(graph_key + 0)) = db_ids_first(left);
    } else {
      no_more_data_exit;
    };
    goto each_left;
  };
each_data:
  stop_if_count_zero;
  if ((!ordinal_max ||
        ((db_mdb_val_graph_data_to_ordinal(val_graph_data) <= ordinal_max)))) {
    if ((!right ||
          imht_set_contains_p(
            right, db_mdb_val_graph_data_to_id(val_graph_data)))) {
      if (!skip_p) {
        record.left = db_mdb_val_to_id_at(val_graph_key, 0);
        record.label = db_mdb_val_to_id_at(val_graph_key, 1);
        record.ordinal = db_mdb_val_graph_data_to_ordinal(val_graph_data);
        record.right = db_mdb_val_graph_data_to_id(val_graph_data);
        db_graph_records_add_x((*result), record, result_temp);
      };
      reduce_count;
    };
    db_mdb_cursor_next_dup_x(graph_lr, val_graph_key, val_graph_data);
    if (db_mdb_status_success_p) {
      goto each_data;
    } else {
      db_mdb_status_require_notfound;
    };
  };
  db_mdb_cursor_next_nodup_x(graph_lr, val_graph_key, val_graph_data);
  goto each_key;
exit:
  (*state).status = status;
  (*state).left = left;
  return (status);
};
status_t db_graph_read_1011_1111(db_graph_read_state_t* state,
  b32 count,
  db_graph_records_t** result) {
  db_graph_reader_header(state);
  db_mdb_declare_val_graph_data;
  db_define_graph_data(graph_data);
  MDB_cursor* graph_lr = (*state).cursor;
  db_ids_t* left = (*state).left;
  db_ids_t* left_first = (*state).left_first;
  db_ids_t* label = (*state).label;
  imht_set_t* right = (*state).right;
  db_graph_reader_get_ordinal_data(state);
  db_graph_data_set_ordinal(graph_data, ordinal_min);
  db_mdb_cursor_get_x(graph_lr, val_graph_key, val_graph_data, MDB_GET_CURRENT);
  db_mdb_status_require;
  (*(graph_key + 0)) = db_ids_first(left);
  (*(graph_key + 1)) = db_ids_first(label);
  if (db_graph_key_equal_p(graph_key, db_mdb_val_to_graph_key(val_graph_key))) {
    goto each_data;
  } else {
  set_key:
    val_graph_key.mv_data = graph_key;
    val_graph_data.mv_data = graph_data;
    db_mdb_cursor_get_x(
      graph_lr, val_graph_key, val_graph_data, MDB_GET_BOTH_RANGE);
    if (db_mdb_status_success_p) {
      goto each_data;
    } else {
      db_mdb_status_require_notfound;
    each_key:
      left = db_ids_rest(left);
      if (left) {
        (*(graph_key + 0)) = db_ids_first(left);
      } else {
        label = db_ids_rest(label);
        if (label) {
          (*(graph_key + 1)) = db_ids_first(label);
          left = left_first;
          (*(graph_key + 0)) = db_ids_first(left);
        } else {
          no_more_data_exit;
        };
      };
      goto set_key;
    };
  };
each_data:
  stop_if_count_zero;
  if ((!ordinal_max ||
        ((db_mdb_val_graph_data_to_ordinal(val_graph_data) <= ordinal_max)))) {
    if ((!right ||
          imht_set_contains_p(
            right, db_mdb_val_graph_data_to_id(val_graph_data)))) {
      if (!skip_p) {
        record.left = db_mdb_val_to_id_at(val_graph_key, 0);
        record.right = db_mdb_val_graph_data_to_id(val_graph_data);
        record.label = db_mdb_val_to_id_at(val_graph_key, 1);
        record.ordinal = db_mdb_val_graph_data_to_ordinal(val_graph_data);
        db_graph_records_add_x((*result), record, result_temp);
      };
      reduce_count;
    };
    db_mdb_cursor_next_dup_x(graph_lr, val_graph_key, val_graph_data);
    if (db_mdb_status_success_p) {
      goto each_data;
    } else {
      goto each_key;
    };
  } else {
    goto each_key;
  };
exit:
  (*state).status = status;
  (*state).left = left;
  (*state).label = label;
  return (status);
};
status_t db_graph_read_0010(db_graph_read_state_t* state,
  b32 count,
  db_graph_records_t** result) {
  db_graph_reader_header(state);
  db_mdb_declare_val_id;
  db_mdb_declare_val_id_2;
  db_mdb_declare_val_graph_data;
  MDB_cursor* graph_ll = (*state).cursor;
  MDB_cursor* graph_lr = (*state).cursor_2;
  db_ids_t* label = (*state).label;
  db_id_t id_left;
  db_id_t id_label;
  db_mdb_cursor_get_x(graph_ll, val_id, val_id_2, MDB_GET_CURRENT);
  db_mdb_status_require;
  db_mdb_cursor_get_x(graph_lr, val_graph_key, val_graph_data, MDB_GET_CURRENT);
  db_mdb_status_require;
  if (label) {
    id_label = db_ids_first(label);
  } else {
    no_more_data_exit;
  };
  if (db_id_equal_p(id_label, db_mdb_val_to_id(val_id))) {
    (*(graph_key + 1)) = id_label;
    goto each_label_data;
  } else {
  set_label_key:
    val_id.mv_data = &id_label;
    db_mdb_cursor_get_x(graph_ll, val_id, val_id_2, MDB_SET_KEY);
    if (db_mdb_status_success_p) {
      (*(graph_key + 1)) = id_label;
      goto each_label_data;
    } else {
      db_mdb_status_require_notfound;
      label = db_ids_rest(label);
      if (label) {
        id_label = db_ids_first(label);
      } else {
        no_more_data_exit;
      };
      goto set_label_key;
    };
  };
each_label_data:
  id_left = db_mdb_val_to_id(val_id_2);
  if (db_id_equal_p(id_left, db_mdb_val_to_id_at(val_graph_key, 0))) {
    goto each_left_data;
  } else {
    (*(graph_key + 0)) = id_left;
    val_graph_key.mv_data = graph_key;
    db_mdb_cursor_get_x(graph_lr, val_graph_key, val_graph_data, MDB_SET_KEY);
    if (db_mdb_status_success_p) {
      goto each_left_data;
    } else {
      goto exit;
    };
  };
each_left_data:
  stop_if_count_zero;
  if (!skip_p) {
    record.left = id_left;
    record.right = db_mdb_val_graph_data_to_id(val_graph_data);
    record.label = id_label;
    db_graph_records_add_x((*result), record, result_temp);
  };
  reduce_count;
  db_mdb_cursor_next_dup_x(graph_lr, val_graph_key, val_graph_data);
  if (db_mdb_status_success_p) {
    goto each_left_data;
  } else {
    db_mdb_status_require_notfound;
  };
  db_mdb_cursor_next_dup_x(graph_ll, val_id, val_id_2);
  if (db_mdb_status_success_p) {
    goto each_label_data;
  } else {
    label = db_ids_rest(label);
    if (label) {
      id_label = db_ids_first(label);
    } else {
      no_more_data_exit;
    };
    goto set_label_key;
  };
exit:
  (*state).status = status;
  (*state).label = label;
  return (status);
};
status_t db_graph_read_0110(db_graph_read_state_t* state,
  b32 count,
  db_graph_records_t** result) {
  db_graph_reader_header(state);
  db_mdb_declare_val_id;
  MDB_cursor* graph_rl = (*state).cursor;
  db_ids_t* label = (*state).label;
  db_ids_t* right = (*state).right;
  db_ids_t* right_first = (*state).right_first;
  db_mdb_cursor_get_x(graph_rl, val_graph_key, val_id, MDB_GET_CURRENT);
  db_mdb_status_require;
  (*(graph_key + 1)) = db_ids_first(label);
  (*(graph_key + 0)) = db_ids_first(right);
  if (db_graph_key_equal_p(graph_key, db_mdb_val_to_graph_key(val_graph_key))) {
    goto each_data;
  } else {
  set_key:
    val_graph_key.mv_data = graph_key;
    db_mdb_cursor_get_x(graph_rl, val_graph_key, val_id, MDB_SET_KEY);
    if (db_mdb_status_success_p) {
      goto each_data;
    } else {
    each_key:
      db_mdb_status_require_notfound;
      right = db_ids_rest(right);
      if (right) {
        (*(graph_key + 0)) = db_ids_first(right);
      } else {
        label = db_ids_rest(label);
        if (label) {
          (*(graph_key + 1)) = db_ids_first(label);
          right = right_first;
          (*(graph_key + 0)) = db_ids_first(right);
        } else {
          no_more_data_exit;
        };
      };
      goto set_key;
    };
  };
each_data:
  stop_if_count_zero;
  if (!skip_p) {
    record.left = db_mdb_val_to_id(val_id);
    record.right = (*(graph_key + 0));
    record.label = (*(graph_key + 1));
    db_graph_records_add_x((*result), record, result_temp);
  };
  reduce_count;
  db_mdb_cursor_next_dup_x(graph_rl, val_graph_key, val_id);
  if (db_mdb_status_success_p) {
    goto each_data;
  } else {
    goto each_key;
  };
exit:
  (*state).status = status;
  (*state).right = right;
  (*state).label = label;
  return (status);
};
status_t db_graph_read_0100(db_graph_read_state_t* state,
  b32 count,
  db_graph_records_t** result) {
  db_graph_reader_header(state);
  db_mdb_declare_val_id;
  MDB_cursor* graph_rl = (*state).cursor;
  db_ids_t* right = (*state).right;
  db_mdb_cursor_get_x(graph_rl, val_graph_key, val_id, MDB_GET_CURRENT);
  db_mdb_status_require;
  (*(graph_key + 0)) = db_ids_first(right);
  if (db_id_equal_p(
        (*(graph_key + 0)), db_mdb_val_to_id_at(val_graph_key, 0))) {
    goto each_key;
  } else {
  set_range:
    val_graph_key.mv_data = graph_key;
    db_mdb_cursor_get_x(graph_rl, val_graph_key, val_id, MDB_SET_RANGE);
    if (db_mdb_status_success_p) {
      if (db_id_equal_p(
            (*(graph_key + 0)), db_mdb_val_to_id_at(val_graph_key, 0))) {
        goto each_key;
      };
    } else {
      db_mdb_status_require_notfound;
    };
    right = db_ids_rest(right);
    if (right) {
      (*(graph_key + 0)) = db_ids_first(right);
    } else {
      no_more_data_exit;
    };
    goto set_range;
  };
each_key:
each_data:
  stop_if_count_zero;
  if (!skip_p) {
    record.left = db_mdb_val_to_id(val_id);
    record.right = db_mdb_val_to_id_at(val_graph_key, 0);
    record.label = db_mdb_val_to_id_at(val_graph_key, 1);
    db_graph_records_add_x((*result), record, result_temp);
  };
  reduce_count;
  db_mdb_cursor_next_dup_x(graph_rl, val_graph_key, val_id);
  if (db_mdb_status_success_p) {
    goto each_data;
  } else {
    db_mdb_status_require_notfound;
  };
  db_mdb_cursor_next_nodup_x(graph_rl, val_graph_key, val_id);
  if (db_mdb_status_success_p) {
    if (db_id_equal_p(
          (*(graph_key + 0)), db_mdb_val_to_id_at(val_graph_key, 0))) {
      goto each_key;
    };
  } else {
    db_mdb_status_require_notfound;
  };
  right = db_ids_rest(right);
  if (right) {
    (*(graph_key + 0)) = db_ids_first(right);
  } else {
    no_more_data_exit;
  };
  goto set_range;
exit:
  (*state).status = status;
  (*state).right = right;
  return (status);
};
status_t db_graph_read_0000(db_graph_read_state_t* state,
  b32 count,
  db_graph_records_t** result) {
  db_graph_reader_header_0000(state);
  db_mdb_declare_val_graph_data;
  MDB_cursor* graph_lr = (*state).cursor;
  db_mdb_cursor_get_x(graph_lr, val_graph_key, val_graph_data, MDB_GET_CURRENT);
  db_mdb_status_require;
each_key:
each_data:
  stop_if_count_zero;
  if (!skip_p) {
    record.left = db_mdb_val_to_id_at(val_graph_key, 0);
    record.right = db_mdb_val_graph_data_to_id(val_graph_data);
    record.label = db_mdb_val_to_id_at(val_graph_key, 1);
    record.ordinal = db_mdb_val_graph_data_to_ordinal(val_graph_data);
    db_graph_records_add_x((*result), record, result_temp);
  };
  reduce_count;
  db_mdb_cursor_next_dup_x(graph_lr, val_graph_key, val_graph_data);
  if (db_mdb_status_success_p) {
    goto each_data;
  } else {
    db_mdb_status_require_notfound;
  };
  db_mdb_cursor_next_nodup_x(graph_lr, val_graph_key, val_graph_data);
  if (db_mdb_status_success_p) {
    goto each_key;
  } else {
    db_mdb_status_require_notfound;
  };
exit:
  (*state).status = status;
  return (status);
};
/** prepare the state and select the reader.
  readers are specialised for filter combinations.
  the 1/0 pattern at the end of reader names corresponds to the filter
  combination the reader is supposed to handle. 1 stands for filter given, 0
  stands for not given. the order is left-right-label-ordinal. readers always
  leave cursors at a valid entry, usually the next entry unless the results have
  been exhausted */
status_t db_graph_select(db_txn_t txn,
  db_ids_t* left,
  db_ids_t* right,
  db_ids_t* label,
  db_ordinal_condition_t* ordinal,
  b32 offset,
  db_graph_read_state_t* result) {
  status_init;
  db_mdb_cursor_declare_3(graph_lr, graph_rl, graph_ll);
  (*result).status = status;
  (*result).left = left;
  (*result).left_first = left;
  (*result).right = right;
  (*result).right_first = right;
  (*result).label = label;
  (*result).ordinal = ordinal;
  (*result).cursor = 0;
  (*result).cursor_2 = 0;
  (*result).options = 0;
  if (left) {
    if (ordinal) {
      if (right) {
        db_graph_select_initialise_set(right, result);
      };
      db_graph_select_cursor_initialise(graph_lr, result, cursor);
      if (label) {
        (*result).reader = db_graph_read_1011_1111;
      } else {
        (*result).reader = db_graph_read_1001_1101;
      };
    } else {
      if (right) {
        db_graph_select_cursor_initialise(graph_rl, result, cursor);
        if (label) {
          (*result).reader = db_graph_read_1110;
        } else {
          (*result).reader = db_graph_read_1100;
        };
      } else {
        db_graph_select_cursor_initialise(graph_lr, result, cursor);
        if (label) {
          (*result).reader = db_graph_read_1010;
        } else {
          (*result).reader = db_graph_read_1000;
        };
      };
    };
  } else {
    if (right) {
      db_graph_select_cursor_initialise(graph_rl, result, cursor);
      (*result).reader = (label ? db_graph_read_0110 : db_graph_read_0100);
    } else {
      if (label) {
        db_graph_select_cursor_initialise(graph_ll, result, cursor);
        db_graph_select_cursor_initialise(graph_lr, result, cursor_2);
        (*result).reader = db_graph_read_0010;
      } else {
        db_graph_select_cursor_initialise(graph_lr, result, cursor);
        (*result).reader = db_graph_read_0000;
      };
    };
  };
  db_graph_reader_t reader = (*result).reader;
  db_select_ensure_offset(result, offset, reader);
exit:
  (*result).status = status;
  return (status);
};
status_t db_graph_read(db_graph_read_state_t* state,
  b32 count,
  db_graph_records_t** result) {
  status_init;
  count = optional_count(count);
  status_require_x((*state).status);
  status = ((db_graph_reader_t)((*state).reader))(state, count, result);
exit:
  db_status_no_more_data_if_mdb_notfound;
  return (status);
};
b0 db_graph_selection_destroy(db_graph_read_state_t* state) {
  db_mdb_cursor_close_2((*state).cursor, (*state).cursor_2);
  if ((db_read_option_is_set_right & (*state).options)) {
    imht_set_destroy(((imht_set_t*)((*state).right)));
    (*state).right = 0;
  };
};