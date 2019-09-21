#define notfound_exit status_set_goto(db_status_group_db, db_status_id_notfound)
#define db_relation_select_cursor_initialise(name, selection, selection_field_name) \
  db_mdb_status_require((db_mdb_env_cursor_open(txn, name))); \
  db_mdb_status_require((mdb_cursor_get(name, (&val_null), (&val_null), MDB_FIRST))); \
  if (!db_mdb_status_is_success) { \
    db_mdb_status_expect_notfound; \
    status_set_goto(db_status_group_db, db_status_id_notfound); \
  }; \
  selection->selection_field_name = name
#define db_relation_reader_header(selection) \
  status_declare; \
  db_mdb_declare_val_relation_key; \
  db_declare_relation_key(relation_key); \
  db_declare_relation(relation); \
  boolean skip; \
  skip = (db_selection_flag_skip & selection->options)
#define db_relation_reader_header_0000(selection) \
  status_declare; \
  db_mdb_declare_val_relation_key; \
  db_declare_relation(relation); \
  boolean skip; \
  skip = (db_selection_flag_skip & selection->options)
#define db_relation_reader_define_ordinal_variables(selection) \
  db_ordinal_t ordinal_min; \
  db_ordinal_t ordinal_max; \
  ordinal_min = selection->ordinal.min; \
  ordinal_max = selection->ordinal.max
status_t db_relation_read_1000(db_relation_selection_t* selection, db_count_t count, db_relations_t* result) {
  db_relation_reader_header(selection);
  db_mdb_declare_val_relation_data;
  MDB_cursor* relation_lr;
  db_ids_t left;
  relation_lr = selection->cursor;
  left = selection->left;
  db_mdb_status_require((mdb_cursor_get(relation_lr, (&val_relation_key), (&val_relation_data), MDB_GET_CURRENT)));
  relation_key[0] = i_array_get(left);
  if (db_pointer_to_id((val_relation_key.mv_data)) == relation_key[0]) {
    goto each_data;
  } else {
  set_range:
    val_relation_key.mv_data = relation_key;
    status.id = mdb_cursor_get(relation_lr, (&val_relation_key), (&val_relation_data), MDB_SET_RANGE);
  each_key:
    if (db_mdb_status_is_success) {
      if (db_pointer_to_id((val_relation_key.mv_data)) == relation_key[0]) {
        goto each_data;
      };
    } else {
      db_mdb_status_expect_notfound;
    };
    i_array_forward(left);
    if (i_array_in_range(left)) {
      relation_key[0] = i_array_get(left);
      goto set_range;
    } else {
      notfound_exit;
    };
  };
each_data:
  stop_if_count_zero;
  if (!skip) {
    relation.left = db_pointer_to_id((val_relation_key.mv_data));
    relation.right = db_relation_data_to_id((val_relation_data.mv_data));
    relation.label = db_pointer_to_id_at((val_relation_key.mv_data), 1);
    relation.ordinal = db_relation_data_to_ordinal((val_relation_data.mv_data));
    i_array_add((*result), relation);
  };
  reduce_count;
  status.id = mdb_cursor_get(relation_lr, (&val_relation_key), (&val_relation_data), MDB_NEXT_DUP);
  if (db_mdb_status_is_success) {
    goto each_data;
  } else {
    db_mdb_status_expect_notfound;
  };
  status.id = mdb_cursor_get(relation_lr, (&val_relation_key), (&val_relation_data), MDB_NEXT_NODUP);
  goto each_key;
exit:
  selection->left.current = left.current;
  return (status);
}
status_t db_relation_read_1010(db_relation_selection_t* selection, db_count_t count, db_relations_t* result) {
  db_relation_reader_header(selection);
  db_mdb_declare_val_relation_data;
  MDB_cursor* relation_lr;
  db_ids_t left;
  db_ids_t label;
  relation_lr = selection->cursor;
  left = selection->left;
  label = selection->label;
  db_mdb_status_require((mdb_cursor_get(relation_lr, (&val_relation_key), (&val_relation_data), MDB_GET_CURRENT)));
  relation_key[0] = i_array_get(left);
  relation_key[1] = i_array_get(label);
  if (db_relation_key_equal(relation_key, (db_mdb_val_to_relation_key(val_relation_key)))) {
    goto each_data;
  } else {
  set_key:
    val_relation_key.mv_data = relation_key;
    status.id = mdb_cursor_get(relation_lr, (&val_relation_key), (&val_relation_data), MDB_SET_KEY);
    if (db_mdb_status_is_success) {
      goto each_data;
    } else {
      db_mdb_status_expect_notfound;
    };
  next_key:
    i_array_forward(left);
    if (i_array_in_range(left)) {
      relation_key[0] = i_array_get(left);
      goto set_key;
    } else {
      i_array_forward(label);
      if (i_array_in_range(label)) {
        i_array_rewind(left);
        relation_key[0] = i_array_get(left);
        relation_key[1] = i_array_get(label);
        goto set_key;
      } else {
        notfound_exit;
      };
    };
  };
each_data:
  stop_if_count_zero;
  if (!skip) {
    relation.left = db_pointer_to_id((val_relation_key.mv_data));
    relation.right = db_relation_data_to_id((val_relation_data.mv_data));
    relation.label = db_pointer_to_id_at((val_relation_key.mv_data), 1);
    relation.ordinal = db_relation_data_to_ordinal((val_relation_data.mv_data));
    i_array_add((*result), relation);
  };
  reduce_count;
  status.id = mdb_cursor_get(relation_lr, (&val_relation_key), (&val_relation_data), MDB_NEXT_DUP);
  if (db_mdb_status_is_success) {
    goto each_data;
  } else {
    goto next_key;
  };
exit:
  selection->left.current = left.current;
  selection->label.current = label.current;
  return (status);
}
status_t db_relation_read_1100(db_relation_selection_t* selection, db_count_t count, db_relations_t* result) {
  db_mdb_declare_val_id;
  db_relation_reader_header(selection);
  MDB_cursor* relation_rl;
  db_ids_t left;
  db_ids_t right;
  relation_rl = selection->cursor;
  left = selection->left;
  right = selection->right;
  db_mdb_status_require((mdb_cursor_get(relation_rl, (&val_relation_key), (&val_id), MDB_GET_CURRENT)));
  relation_key[0] = i_array_get(right);
  if (db_pointer_to_id((val_relation_key.mv_data)) == relation_key[0]) {
    goto each_left;
  } else {
  set_range:
    val_relation_key.mv_data = relation_key;
    status.id = mdb_cursor_get(relation_rl, (&val_relation_key), (&val_id), MDB_SET_RANGE);
  each_right:
    if (db_mdb_status_is_success) {
      if (db_pointer_to_id((val_relation_key.mv_data)) == relation_key[0]) {
        goto each_left;
      };
    } else {
      db_mdb_status_expect_notfound;
    };
    i_array_forward(right);
    if (i_array_in_range(right)) {
      relation_key[0] = i_array_get(right);
    } else {
      notfound_exit;
    };
    goto set_range;
  };
each_left:
  stop_if_count_zero;
  val_id.mv_data = left.current;
  status.id = mdb_cursor_get(relation_rl, (&val_relation_key), (&val_id), MDB_GET_BOTH);
  if (db_mdb_status_is_success) {
    if (!skip) {
      relation.left = db_pointer_to_id((val_id.mv_data));
      relation.right = db_pointer_to_id((val_relation_key.mv_data));
      relation.label = db_pointer_to_id_at((val_relation_key.mv_data), 1);
      i_array_add((*result), relation);
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
  status.id = mdb_cursor_get(relation_rl, (&val_relation_key), (&val_id), MDB_NEXT_NODUP);
  goto each_right;
exit:
  selection->left.current = left.current;
  selection->right.current = right.current;
  return (status);
}
status_t db_relation_read_1110(db_relation_selection_t* selection, db_count_t count, db_relations_t* result) {
  db_relation_reader_header(selection);
  db_mdb_declare_val_id;
  MDB_cursor* relation_rl;
  db_ids_t left;
  db_ids_t right;
  db_ids_t label;
  db_id_t id_left;
  relation_rl = selection->cursor;
  left = selection->left;
  right = selection->right;
  label = selection->label;
  relation_key[1] = i_array_get(label);
  id_left = i_array_get(left);
  relation_key[0] = i_array_get(right);
set_cursor:
  val_relation_key.mv_data = relation_key;
  val_id.mv_data = &id_left;
  status.id = mdb_cursor_get(relation_rl, (&val_relation_key), (&val_id), MDB_GET_BOTH);
  if (db_mdb_status_is_success) {
    goto match;
  } else {
    db_mdb_status_expect_notfound;
  };
next_query:
  i_array_forward(right);
  if (i_array_in_range(right)) {
    stop_if_count_zero;
    relation_key[0] = i_array_get(right);
    goto set_cursor;
  } else {
    i_array_rewind(right);
    relation_key[0] = i_array_get(right);
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
        relation_key[1] = i_array_get(label);
        goto set_cursor;
      } else {
        notfound_exit;
      };
    };
  };
match:
  if (!skip) {
    relation.left = db_pointer_to_id((val_id.mv_data));
    relation.right = db_pointer_to_id((val_relation_key.mv_data));
    relation.label = db_pointer_to_id_at((val_relation_key.mv_data), 1);
    i_array_add((*result), relation);
  };
  reduce_count;
  goto next_query;
exit:
  selection->left.current = left.current;
  selection->right.current = right.current;
  selection->label.current = label.current;
  return (status);
}
status_t db_relation_read_1001_1101(db_relation_selection_t* selection, db_count_t count, db_relations_t* result) {
  db_relation_reader_header(selection);
  relation_key[1] = 0;
  db_mdb_declare_val_relation_data;
  db_declare_relation_data(relation_data);
  MDB_cursor* relation_lr;
  db_ids_t left;
  imht_set_t* right;
  relation_lr = selection->cursor;
  left = selection->left;
  right = selection->ids_set;
  relation_key[0] = i_array_get(left);
  db_relation_reader_define_ordinal_variables(selection);
  db_relation_data_set_ordinal(relation_data, ordinal_min);
  db_mdb_status_require((mdb_cursor_get(relation_lr, (&val_relation_key), (&val_relation_data), MDB_GET_CURRENT)));
  /* already set from select or previous call */
  if (db_pointer_to_id((val_relation_key.mv_data)) == relation_key[0]) {
    goto each_data;
  };
each_left:
  val_relation_key.mv_data = relation_key;
  status.id = mdb_cursor_get(relation_lr, (&val_relation_key), (&val_relation_data), MDB_SET_RANGE);
each_key:
  if (db_mdb_status_is_success) {
    if (db_pointer_to_id((val_relation_key.mv_data)) == relation_key[0]) {
      val_relation_data.mv_data = relation_data;
      status.id = mdb_cursor_get(relation_lr, (&val_relation_key), (&val_relation_data), MDB_GET_BOTH_RANGE);
      if (db_mdb_status_is_success) {
        goto each_data;
      } else {
        db_mdb_status_expect_notfound;
      };
      status.id = mdb_cursor_get(relation_lr, (&val_relation_key), (&val_relation_data), MDB_NEXT_NODUP);
      goto each_key;
    };
  } else {
    db_mdb_status_expect_notfound;
  };
  i_array_forward(left);
  if (i_array_in_range(left)) {
    relation_key[0] = i_array_get(left);
  } else {
    notfound_exit;
  };
  goto each_left;
each_data:
  stop_if_count_zero;
  if (!ordinal_max || (db_relation_data_to_ordinal((val_relation_data.mv_data)) <= ordinal_max)) {
    /* ordinal-min is checked because the set-range can be skipped */
    if ((!ordinal_min || (db_relation_data_to_ordinal((val_relation_data.mv_data)) >= ordinal_min)) && (!right || imht_set_contains(right, (db_relation_data_to_id((val_relation_data.mv_data)))))) {
      if (!skip) {
        relation.left = db_pointer_to_id((val_relation_key.mv_data));
        relation.label = db_pointer_to_id_at((val_relation_key.mv_data), 1);
        relation.ordinal = db_relation_data_to_ordinal((val_relation_data.mv_data));
        relation.right = db_relation_data_to_id((val_relation_data.mv_data));
        i_array_add((*result), relation);
      };
      reduce_count;
    };
    status.id = mdb_cursor_get(relation_lr, (&val_relation_key), (&val_relation_data), MDB_NEXT_DUP);
    if (db_mdb_status_is_success) {
      goto each_data;
    } else {
      db_mdb_status_expect_notfound;
    };
  };
  status.id = mdb_cursor_get(relation_lr, (&val_relation_key), (&val_relation_data), MDB_NEXT_NODUP);
  goto each_key;
exit:
  selection->left.current = left.current;
  return (status);
}
status_t db_relation_read_1011_1111(db_relation_selection_t* selection, db_count_t count, db_relations_t* result) {
  db_relation_reader_header(selection);
  db_declare_relation_data(relation_data);
  db_mdb_declare_val_relation_data;
  MDB_cursor* relation_lr;
  db_ids_t left;
  db_ids_t label;
  imht_set_t* right;
  relation_lr = selection->cursor;
  left = selection->left;
  label = selection->label;
  right = selection->ids_set;
  relation_key[0] = i_array_get(left);
  relation_key[1] = i_array_get(label);
  db_relation_reader_define_ordinal_variables(selection);
  db_relation_data_set_ordinal(relation_data, ordinal_min);
  db_mdb_status_require((mdb_cursor_get(relation_lr, (&val_relation_key), (&val_relation_data), MDB_GET_CURRENT)));
  if ((db_pointer_to_id((val_relation_key.mv_data)) == relation_key[0]) && (db_pointer_to_id_at((val_relation_key.mv_data), 1) == relation_key[1])) {
    goto each_data;
  };
set_key:
  val_relation_key.mv_data = relation_key;
  val_relation_data.mv_data = relation_data;
  status.id = mdb_cursor_get(relation_lr, (&val_relation_key), (&val_relation_data), MDB_GET_BOTH_RANGE);
  if (db_mdb_status_is_success) {
    goto each_data;
  } else {
    db_mdb_status_expect_notfound;
  each_key:
    i_array_forward(left);
    if (i_array_in_range(left)) {
      relation_key[0] = i_array_get(left);
    } else {
      i_array_forward(label);
      if (i_array_in_range(label)) {
        relation_key[1] = i_array_get(label);
        i_array_rewind(left);
        relation_key[0] = i_array_get(left);
      } else {
        notfound_exit;
      };
    };
    goto set_key;
  };
each_data:
  stop_if_count_zero;
  if (!ordinal_max || (db_relation_data_to_ordinal((val_relation_data.mv_data)) <= ordinal_max)) {
    /* ordinal-min is checked because the get-both-range can be skipped */
    if ((!ordinal_min || (db_relation_data_to_ordinal((val_relation_data.mv_data)) >= ordinal_min)) && (!right || imht_set_contains(right, (db_relation_data_to_id((val_relation_data.mv_data)))))) {
      if (!skip) {
        relation.left = db_pointer_to_id((val_relation_key.mv_data));
        relation.label = db_pointer_to_id_at((val_relation_key.mv_data), 1);
        relation.ordinal = db_relation_data_to_ordinal((val_relation_data.mv_data));
        relation.right = db_relation_data_to_id((val_relation_data.mv_data));
        i_array_add((*result), relation);
      };
      reduce_count;
    };
    status.id = mdb_cursor_get(relation_lr, (&val_relation_key), (&val_relation_data), MDB_NEXT_DUP);
    if (db_mdb_status_is_success) {
      goto each_data;
    } else {
      db_mdb_status_expect_notfound;
    };
  };
  goto each_key;
exit:
  selection->left.current = left.current;
  selection->label.current = label.current;
  return (status);
}
status_t db_relation_read_0010(db_relation_selection_t* selection, db_count_t count, db_relations_t* result) {
  db_relation_reader_header(selection);
  db_mdb_declare_val_id;
  db_mdb_declare_val_id_2;
  db_mdb_declare_val_relation_data;
  MDB_cursor* relation_ll;
  MDB_cursor* relation_lr;
  db_ids_t label;
  db_id_t id_left;
  db_id_t id_label;
  relation_ll = selection->cursor;
  relation_lr = selection->cursor_2;
  label = selection->label;
  db_mdb_status_require((mdb_cursor_get(relation_ll, (&val_id), (&val_id_2), MDB_GET_CURRENT)));
  db_mdb_status_require((mdb_cursor_get(relation_lr, (&val_relation_key), (&val_relation_data), MDB_GET_CURRENT)));
  if (i_array_in_range(label)) {
    id_label = i_array_get(label);
  } else {
    notfound_exit;
  };
  if (id_label == db_pointer_to_id((val_id.mv_data))) {
    relation_key[1] = id_label;
    goto each_label_data;
  } else {
  set_label_key:
    val_id.mv_data = &id_label;
    status.id = mdb_cursor_get(relation_ll, (&val_id), (&val_id_2), MDB_SET_KEY);
    if (db_mdb_status_is_success) {
      relation_key[1] = id_label;
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
  if (id_left == db_pointer_to_id((val_relation_key.mv_data))) {
    goto each_left_data;
  } else {
    relation_key[0] = id_left;
    val_relation_key.mv_data = relation_key;
    status.id = mdb_cursor_get(relation_lr, (&val_relation_key), (&val_relation_data), MDB_SET_KEY);
    if (db_mdb_status_is_success) {
      goto each_left_data;
    } else {
      goto exit;
    };
  };
each_left_data:
  stop_if_count_zero;
  if (!skip) {
    relation.left = id_left;
    relation.right = db_relation_data_to_id((val_relation_data.mv_data));
    relation.label = id_label;
    i_array_add((*result), relation);
  };
  reduce_count;
  status.id = mdb_cursor_get(relation_lr, (&val_relation_key), (&val_relation_data), MDB_NEXT_DUP);
  if (db_mdb_status_is_success) {
    goto each_left_data;
  } else {
    db_mdb_status_expect_notfound;
  };
  status.id = mdb_cursor_get(relation_ll, (&val_id), (&val_id_2), MDB_NEXT_DUP);
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
  selection->label.current = label.current;
  return (status);
}
status_t db_relation_read_0110(db_relation_selection_t* selection, db_count_t count, db_relations_t* result) {
  db_relation_reader_header(selection);
  db_mdb_declare_val_id;
  MDB_cursor* relation_rl;
  db_ids_t label;
  db_ids_t right;
  relation_rl = selection->cursor;
  label = selection->label;
  right = selection->right;
  db_mdb_status_require((mdb_cursor_get(relation_rl, (&val_relation_key), (&val_id), MDB_GET_CURRENT)));
  relation_key[1] = i_array_get(label);
  relation_key[0] = i_array_get(right);
  if (db_relation_key_equal(relation_key, (db_mdb_val_to_relation_key(val_relation_key)))) {
    goto each_data;
  } else {
  set_key:
    val_relation_key.mv_data = relation_key;
    status.id = mdb_cursor_get(relation_rl, (&val_relation_key), (&val_id), MDB_SET_KEY);
    if (db_mdb_status_is_success) {
      goto each_data;
    } else {
    each_key:
      db_mdb_status_expect_notfound;
      i_array_forward(right);
      if (i_array_in_range(right)) {
        relation_key[0] = i_array_get(right);
      } else {
        i_array_forward(label);
        if (i_array_in_range(label)) {
          relation_key[1] = i_array_get(label);
          i_array_rewind(right);
          relation_key[0] = i_array_get(right);
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
    relation.left = db_pointer_to_id((val_id.mv_data));
    relation.right = relation_key[0];
    relation.label = relation_key[1];
    i_array_add((*result), relation);
  };
  reduce_count;
  status.id = mdb_cursor_get(relation_rl, (&val_relation_key), (&val_id), MDB_NEXT_DUP);
  if (db_mdb_status_is_success) {
    goto each_data;
  } else {
    goto each_key;
  };
exit:
  selection->right.current = right.current;
  selection->label.current = label.current;
  return (status);
}
status_t db_relation_read_0100(db_relation_selection_t* selection, db_count_t count, db_relations_t* result) {
  db_relation_reader_header(selection);
  db_mdb_declare_val_id;
  MDB_cursor* relation_rl;
  db_ids_t right;
  relation_rl = selection->cursor;
  right = selection->right;
  db_mdb_status_require((mdb_cursor_get(relation_rl, (&val_relation_key), (&val_id), MDB_GET_CURRENT)));
  relation_key[0] = i_array_get(right);
  if (relation_key[0] == db_pointer_to_id((val_relation_key.mv_data))) {
    goto each_key;
  } else {
  set_range:
    val_relation_key.mv_data = relation_key;
    status.id = mdb_cursor_get(relation_rl, (&val_relation_key), (&val_id), MDB_SET_RANGE);
    if (db_mdb_status_is_success) {
      if (relation_key[0] == db_pointer_to_id((val_relation_key.mv_data))) {
        goto each_key;
      };
    } else {
      db_mdb_status_expect_notfound;
    };
    i_array_forward(right);
    if (i_array_in_range(right)) {
      relation_key[0] = i_array_get(right);
    } else {
      notfound_exit;
    };
    goto set_range;
  };
each_key:
each_data:
  stop_if_count_zero;
  if (!skip) {
    relation.left = db_pointer_to_id((val_id.mv_data));
    relation.right = db_pointer_to_id((val_relation_key.mv_data));
    relation.label = db_pointer_to_id_at((val_relation_key.mv_data), 1);
    i_array_add((*result), relation);
  };
  reduce_count;
  status.id = mdb_cursor_get(relation_rl, (&val_relation_key), (&val_id), MDB_NEXT_DUP);
  if (db_mdb_status_is_success) {
    goto each_data;
  } else {
    db_mdb_status_expect_notfound;
  };
  status.id = mdb_cursor_get(relation_rl, (&val_relation_key), (&val_id), MDB_NEXT_NODUP);
  if (db_mdb_status_is_success) {
    if (relation_key[0] == db_pointer_to_id((val_relation_key.mv_data))) {
      goto each_key;
    };
  } else {
    db_mdb_status_expect_notfound;
  };
  i_array_forward(right);
  if (i_array_in_range(right)) {
    relation_key[0] = i_array_get(right);
  } else {
    notfound_exit;
  };
  goto set_range;
exit:
  selection->right.current = right.current;
  return (status);
}
status_t db_relation_read_0000(db_relation_selection_t* selection, db_count_t count, db_relations_t* result) {
  db_relation_reader_header_0000(selection);
  db_mdb_declare_val_relation_data;
  MDB_cursor* relation_lr;
  relation_lr = selection->cursor;
  db_mdb_status_require((mdb_cursor_get(relation_lr, (&val_relation_key), (&val_relation_data), MDB_GET_CURRENT)));
each_key:
each_data:
  stop_if_count_zero;
  if (!skip) {
    relation.left = db_pointer_to_id((val_relation_key.mv_data));
    relation.right = db_relation_data_to_id((val_relation_data.mv_data));
    relation.label = db_pointer_to_id_at((val_relation_key.mv_data), 1);
    relation.ordinal = db_relation_data_to_ordinal((val_relation_data.mv_data));
    i_array_add((*result), relation);
  };
  reduce_count;
  status.id = mdb_cursor_get(relation_lr, (&val_relation_key), (&val_relation_data), MDB_NEXT_DUP);
  if (db_mdb_status_is_success) {
    goto each_data;
  } else {
    db_mdb_status_expect_notfound;
  };
  status.id = mdb_cursor_get(relation_lr, (&val_relation_key), (&val_relation_data), MDB_NEXT_NODUP);
  if (db_mdb_status_is_success) {
    goto each_key;
  } else {
    db_mdb_status_expect_notfound;
  };
exit:
  return (status);
}
/** prepare the selection and select the reader.
  readers are specialised for filter combinations.
  the 1/0 pattern at the end of reader names corresponds to the filter combination the reader is supposed to handle.
  1 stands for filter given, 0 stands for not given. order is left, right, label, ordinal.
  readers always leave cursors at a valid entry, usually the next entry unless the results have been exhausted.
  left/right/label ids pointer can be zero which means they are unused.
  internally in the selection if unset i-array-in-range and i-array-length is zero */
status_t db_relation_select(db_txn_t txn, db_ids_t* left, db_ids_t* right, db_ids_t* label, db_ordinal_condition_t* ordinal, db_relation_selection_t* selection) {
  status_declare;
  db_mdb_declare_val_null;
  imht_set_t* right_set;
  db_mdb_cursor_declare(relation_lr);
  db_mdb_cursor_declare(relation_rl);
  db_mdb_cursor_declare(relation_ll);
  if (left) {
    selection->left = *left;
  } else {
    i_array_set_null((selection->left));
  };
  if (right) {
    selection->right = *right;
  } else {
    i_array_set_null((selection->right));
  };
  if (label) {
    selection->label = *label;
  } else {
    i_array_set_null((selection->label));
  };
  if (ordinal) {
    selection->ordinal = *ordinal;
  };
  selection->cursor = 0;
  selection->cursor_2 = 0;
  selection->options = 0;
  selection->ids_set = 0;
  if (left) {
    if (ordinal) {
      if (right) {
        status_require((db_ids_to_set((*right), (&right_set))));
        selection->ids_set = right_set;
        selection->options = (db_relation_selection_flag_is_set_right | selection->options);
      };
      db_relation_select_cursor_initialise(relation_lr, selection, cursor);
      if (label) {
        selection->reader = db_relation_read_1011_1111;
      } else {
        selection->reader = db_relation_read_1001_1101;
      };
    } else {
      if (right) {
        db_relation_select_cursor_initialise(relation_rl, selection, cursor);
        if (label) {
          selection->reader = db_relation_read_1110;
        } else {
          selection->reader = db_relation_read_1100;
        };
      } else {
        db_relation_select_cursor_initialise(relation_lr, selection, cursor);
        if (label) {
          selection->reader = db_relation_read_1010;
        } else {
          selection->reader = db_relation_read_1000;
        };
      };
    };
  } else {
    if (right) {
      db_relation_select_cursor_initialise(relation_rl, selection, cursor);
      selection->reader = (label ? db_relation_read_0110 : db_relation_read_0100);
    } else {
      if (label) {
        db_relation_select_cursor_initialise(relation_ll, selection, cursor);
        db_relation_select_cursor_initialise(relation_lr, selection, cursor_2);
        selection->reader = db_relation_read_0010;
      } else {
        db_relation_select_cursor_initialise(relation_lr, selection, cursor);
        selection->reader = db_relation_read_0000;
      };
    };
  };
exit:
  db_mdb_status_notfound_if_notfound;
  return (status);
}
/** skip the next count result matches */
status_t db_relation_skip(db_relation_selection_t* selection, db_count_t count) {
  status_declare;
  selection->options = (db_selection_flag_skip | selection->options);
  status = ((db_relation_reader_t)(selection->reader))(selection, count, 0);
  selection->options = (db_selection_flag_skip ^ selection->options);
  return (status);
}
/** result memory is to be allocated by the caller */
status_t db_relation_read(db_relation_selection_t* selection, db_count_t count, db_relations_t* result) {
  status_declare;
  status = ((db_relation_reader_t)(selection->reader))(selection, count, result);
  db_mdb_status_notfound_if_notfound;
  return (status);
}
void db_relation_selection_finish(db_relation_selection_t* selection) {
  db_mdb_cursor_close_if_active((selection->cursor));
  db_mdb_cursor_close_if_active((selection->cursor_2));
  if (db_relation_selection_flag_is_set_right & selection->options) {
    imht_set_destroy((selection->ids_set));
    selection->ids_set = 0;
  };
}
