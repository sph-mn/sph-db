status_t db_graph_internal_delete_graph_ll(MDB_cursor* graph_ll, db_id_t id_label, db_id_t id_left) {
  status_declare;
  db_mdb_declare_val_id;
  db_mdb_declare_val_id_2;
  val_id.mv_data = &id_label;
  val_id_2.mv_data = &id_left;
  status.id = mdb_cursor_get(graph_ll, (&val_id), (&val_id_2), MDB_GET_BOTH);
  if (db_mdb_status_is_success) {
    db_mdb_status_require(mdb_cursor_del(graph_ll, 0));
  } else {
    db_mdb_status_expect_notfound;
  };
  status.id = status_id_success;
exit:
  return (status);
};
status_t db_graph_internal_delete_graph_ll_conditional(MDB_cursor* graph_lr, MDB_cursor* graph_ll, db_id_t id_label, db_id_t id_left) {
  status_declare;
  db_mdb_declare_val_null;
  db_mdb_declare_val_graph_key;
  db_declare_graph_key(graph_key);
  graph_key[0] = id_left;
  graph_key[1] = id_label;
  val_graph_key.mv_data = graph_key;
  status.id = mdb_cursor_get(graph_lr, (&val_graph_key), (&val_null), MDB_SET);
  return ((db_mdb_status_is_notfound ? db_graph_internal_delete_graph_ll(graph_ll, id_label, id_left) : status));
};
status_t db_graph_internal_delete_graph_rl(MDB_cursor* graph_rl, db_id_t id_left, db_id_t id_right, db_id_t id_label) {
  status_declare;
  db_mdb_declare_val_id;
  db_mdb_declare_val_graph_key;
  db_declare_graph_key(graph_key);
  graph_key[0] = id_right;
  graph_key[1] = id_label;
  val_graph_key.mv_data = graph_key;
  val_id.mv_data = &id_left;
  status.id = mdb_cursor_get(graph_rl, (&val_graph_key), (&val_id), MDB_GET_BOTH);
  if (db_mdb_status_is_success) {
    db_mdb_status_require(mdb_cursor_del(graph_rl, 0));
  } else {
    db_mdb_status_expect_notfound;
  };
exit:
  return (status);
};
#define db_graph_internal_delete_0010 \
  label = *label_pointer; \
  set_key_0010: \
  id_label = i_array_get(label); \
  val_id.mv_data = &id_label; \
  status.id = mdb_cursor_get(graph_ll, (&val_id), (&val_id_2), MDB_SET_KEY); \
  if (db_mdb_status_is_success) { \
    goto each_data_0010; \
  } else { \
    db_mdb_status_expect_notfound; \
  }; \
  each_key_0010: \
  i_array_forward(label); \
  if (i_array_in_range(label)) { \
    goto set_key_0010; \
  } else { \
    goto exit; \
  }; \
  each_data_0010: \
  id_left = db_pointer_to_id((val_id_2.mv_data)); \
  graph_key[0] = id_left; \
  graph_key[1] = id_label; \
  val_graph_key.mv_data = graph_key; \
  status.id = mdb_cursor_get(graph_lr, (&val_graph_key), (&val_graph_data), MDB_SET_KEY); \
  if (db_mdb_status_is_success) { \
  each_data_2_0010: \
    status = db_graph_internal_delete_graph_rl(graph_rl, id_left, (db_graph_data_to_id((val_graph_data.mv_data))), id_label); \
    db_mdb_status_expect_read; \
    db_mdb_status_require(mdb_cursor_del(graph_lr, 0)); \
    status.id = mdb_cursor_get(graph_lr, (&val_graph_key), (&val_graph_data), MDB_NEXT_DUP); \
    if (db_mdb_status_is_success) { \
      goto each_data_2_0010; \
    } else { \
      db_mdb_status_expect_notfound; \
    }; \
  } else { \
    db_mdb_status_expect_notfound; \
  }; \
  db_mdb_status_require(mdb_cursor_del(graph_ll, 0)); \
  status.id = mdb_cursor_get(graph_ll, (&val_id), (&val_id_2), MDB_NEXT_DUP); \
  if (db_mdb_status_is_success) { \
    goto each_data_0010; \
  } else { \
    db_mdb_status_expect_notfound; \
  }; \
  goto each_key_0010;
#define db_graph_internal_delete_0110 \
  label = *label_pointer; \
  right = *right_pointer; \
  set_key_0110: \
  id_right = i_array_get(right); \
  id_label = i_array_get(label); \
  graph_key[0] = id_right; \
  graph_key[1] = id_label; \
  val_graph_key.mv_data = graph_key; \
  status.id = mdb_cursor_get(graph_rl, (&val_graph_key), (&val_id), MDB_SET_KEY); \
  if (db_mdb_status_is_success) { \
    goto each_data_0110; \
  } else { \
    db_mdb_status_expect_notfound; \
  }; \
  each_key_0110: \
  i_array_forward(right); \
  if (i_array_in_range(right)) { \
    goto set_key_0110; \
  } else { \
    i_array_forward(label); \
    if (i_array_in_range(label)) { \
      i_array_rewind(right); \
      goto set_key_0110; \
    } else { \
      goto exit; \
    }; \
  }; \
  each_data_0110: \
  id_left = db_pointer_to_id((val_id.mv_data)); \
  graph_key[0] = id_left; \
  graph_key[1] = id_label; \
  val_graph_key.mv_data = graph_key; \
  status.id = mdb_cursor_get(graph_lr, (&val_graph_key), (&val_graph_data), MDB_SET_KEY); \
  if (db_mdb_status_is_success) { \
    status = db_mdb_graph_lr_seek_right(graph_lr, id_right); \
    if (db_mdb_status_is_success) { \
      db_mdb_status_require(mdb_cursor_del(graph_lr, 0)); \
    } else { \
      db_mdb_status_expect_notfound; \
    }; \
  } else { \
    db_mdb_status_expect_notfound; \
  }; \
  status = db_graph_internal_delete_graph_ll(graph_ll, id_label, id_left); \
  db_mdb_status_expect_read; \
  db_mdb_status_require(mdb_cursor_del(graph_rl, 0)); \
  status.id = mdb_cursor_get(graph_rl, (&val_graph_key), (&val_id), MDB_NEXT_DUP); \
  if (db_mdb_status_is_success) { \
    goto each_data_0110; \
  } else { \
    db_mdb_status_expect_notfound; \
  }; \
  goto each_key_0110;
#define db_graph_internal_delete_1010 \
  left = *left_pointer; \
  label = *label_pointer; \
  while (i_array_in_range(left)) { \
    id_left = i_array_get(left); \
    while (i_array_in_range(label)) { \
      id_label = i_array_get(label); \
      graph_key[0] = id_left; \
      graph_key[1] = id_label; \
      val_graph_key.mv_data = graph_key; \
      status.id = mdb_cursor_get(graph_lr, (&val_graph_key), (&val_graph_data), MDB_SET_KEY); \
      if (db_mdb_status_is_success) { \
        do { \
          status = db_graph_internal_delete_graph_rl(graph_rl, id_left, (db_graph_data_to_id((val_graph_data.mv_data))), id_label); \
          db_mdb_status_expect_read; \
          status = db_graph_internal_delete_graph_ll(graph_ll, id_label, id_left); \
          db_mdb_status_expect_read; \
          status.id = mdb_cursor_get(graph_lr, (&val_graph_key), (&val_graph_data), MDB_NEXT_DUP); \
        } while (db_mdb_status_is_success); \
        db_mdb_status_expect_notfound; \
        graph_key[0] = id_left; \
        graph_key[1] = id_label; \
        val_graph_key.mv_data = graph_key; \
        db_mdb_status_require(mdb_cursor_get(graph_lr, (&val_graph_key), (&val_graph_data), MDB_SET_KEY)); \
        db_mdb_status_require(mdb_cursor_del(graph_lr, MDB_NODUPDATA)); \
      } else { \
        db_mdb_status_expect_notfound; \
      }; \
      i_array_forward(label); \
    }; \
    i_array_rewind(label); \
    i_array_forward(left); \
  };
#define db_graph_internal_delete_0100 \
  db_id_t id_left; \
  db_id_t id_right; \
  db_id_t id_label; \
  right = *right_pointer; \
  set_range_0100: \
  id_right = i_array_get(right); \
  graph_key[0] = id_right; \
  graph_key[1] = 0; \
  val_graph_key.mv_data = graph_key; \
  status.id = mdb_cursor_get(graph_rl, (&val_graph_key), (&val_id), MDB_SET_RANGE); \
  if (db_mdb_status_is_success) { \
    if (id_right == db_pointer_to_id((val_graph_key.mv_data))) { \
      id_label = db_pointer_to_id_at((val_graph_key.mv_data), 1); \
      goto each_data_0100; \
    }; \
  } else { \
    db_mdb_status_expect_notfound; \
  }; \
  i_array_forward(right); \
  if (i_array_in_range(right)) { \
    goto set_range_0100; \
  } else { \
    goto exit; \
  }; \
  each_data_0100: \
  id_left = db_pointer_to_id((val_id.mv_data)); \
  graph_key[0] = id_left; \
  graph_key[1] = id_label; \
  val_graph_key.mv_data = graph_key; \
  status.id = mdb_cursor_get(graph_lr, (&val_graph_key), (&val_graph_data), MDB_SET_KEY); \
  if (db_mdb_status_is_success) { \
    status = db_mdb_graph_lr_seek_right(graph_lr, id_right); \
    if (db_mdb_status_is_success) { \
      db_mdb_status_require(mdb_cursor_del(graph_lr, 0)); \
    } else { \
      db_mdb_status_expect_notfound; \
    }; \
  } else { \
    db_mdb_status_expect_notfound; \
  }; \
  status = db_graph_internal_delete_graph_ll_conditional(graph_lr, graph_ll, id_label, id_left); \
  db_mdb_status_expect_read; \
  db_mdb_status_require(mdb_cursor_del(graph_rl, 0)); \
  status.id = mdb_cursor_get(graph_rl, (&val_graph_key), (&val_id), MDB_NEXT_DUP); \
  if (db_mdb_status_is_success) { \
    goto each_data_0100; \
  } else { \
    db_mdb_status_expect_notfound; \
  }; \
  goto set_range_0100;
#define db_graph_internal_delete_1000 \
  left = *left_pointer; \
  set_range_1000: \
  id_left = i_array_get(left); \
  graph_key[0] = id_left; \
  graph_key[1] = 0; \
  val_graph_key.mv_data = graph_key; \
  status.id = mdb_cursor_get(graph_lr, (&val_graph_key), (&val_graph_data), MDB_SET_RANGE); \
  each_key_1000: \
  if (db_mdb_status_is_success) { \
    if (id_left == db_pointer_to_id((val_graph_key.mv_data))) { \
      id_label = db_pointer_to_id_at((val_graph_key.mv_data), 1); \
      goto each_data_1000; \
    }; \
  } else { \
    db_mdb_status_expect_notfound; \
  }; \
  i_array_forward(left); \
  if (i_array_in_range(left)) { \
    goto set_range_1000; \
  } else { \
    goto exit; \
  }; \
  each_data_1000: \
  id_right = db_graph_data_to_id((val_graph_data.mv_data)); \
  status = db_graph_internal_delete_graph_rl(graph_rl, id_left, id_right, id_label); \
  db_mdb_status_expect_read; \
  status = db_graph_internal_delete_graph_ll(graph_ll, id_label, id_left); \
  db_mdb_status_expect_read; \
  status.id = mdb_cursor_get(graph_lr, (&val_graph_key), (&val_graph_data), MDB_NEXT_DUP); \
  if (db_mdb_status_is_success) { \
    goto each_data_1000; \
  } else { \
    db_mdb_status_expect_notfound; \
  }; \
  graph_key[0] = id_left; \
  graph_key[1] = id_label; \
  db_mdb_status_require(mdb_cursor_get(graph_lr, (&val_graph_key), (&val_graph_data), MDB_SET_KEY)); \
  db_mdb_status_require(mdb_cursor_del(graph_lr, MDB_NODUPDATA)); \
  status.id = mdb_cursor_get(graph_lr, (&val_graph_key), (&val_graph_data), MDB_NEXT_NODUP); \
  goto each_key_1000;
#define db_graph_internal_delete_1100 \
  status_require(db_ids_to_set((*right_pointer), (&right_set))); \
  left = *left_pointer; \
  graph_key[1] = 0; \
  set_range_1100: \
  id_left = i_array_get(left); \
  graph_key[0] = id_left; \
  val_graph_key.mv_data = graph_key; \
  status.id = mdb_cursor_get(graph_lr, (&val_graph_key), (&val_graph_data), MDB_SET_RANGE); \
  each_key_1100: \
  if (db_mdb_status_is_success) { \
    if (id_left == db_pointer_to_id((val_graph_key.mv_data))) { \
      id_label = db_pointer_to_id_at((val_graph_key.mv_data), 1); \
      goto each_data_1100; \
    }; \
  } else { \
    db_mdb_status_expect_notfound; \
  }; \
  i_array_forward(left); \
  if (i_array_in_range(left)) { \
    graph_key[1] = 0; \
    goto set_range_1100; \
  } else { \
    goto exit; \
  }; \
  each_data_1100: \
  id_right = db_graph_data_to_id((val_graph_data.mv_data)); \
  if (imht_set_contains(right_set, id_right)) { \
    status = db_graph_internal_delete_graph_rl(graph_rl, id_left, id_right, id_label); \
    db_mdb_status_expect_read; \
    db_mdb_status_require(mdb_cursor_del(graph_lr, 0)); \
  }; \
  status.id = mdb_cursor_get(graph_lr, (&val_graph_key), (&val_graph_data), MDB_NEXT_DUP); \
  if (db_mdb_status_is_success) { \
    goto each_data_1100; \
  } else { \
    db_mdb_status_expect_notfound; \
  }; \
  status = db_graph_internal_delete_graph_ll_conditional(graph_lr, graph_ll, id_label, id_left); \
  db_mdb_status_expect_read; \
  graph_key[0] = id_left; \
  graph_key[1] = id_label; \
  val_graph_key.mv_data = graph_key; \
  status.id = mdb_cursor_get(graph_lr, (&val_graph_key), (&val_graph_data), MDB_SET_KEY); \
  if (db_mdb_status_is_success) { \
    status.id = mdb_cursor_get(graph_lr, (&val_graph_key), (&val_graph_data), MDB_NEXT_NODUP); \
    goto each_key_1100; \
  } else if (status.id == MDB_NOTFOUND) { \
    goto set_range_1100; \
  } else { \
    status_set_group_goto(db_status_group_lmdb); \
  };
#define db_graph_internal_delete_1110 \
  status_require(db_ids_to_set((*right_pointer), (&right_set))); \
  left = *left_pointer; \
  label = *label_pointer; \
  while (i_array_in_range(left)) { \
    id_left = i_array_get(left); \
    while (i_array_in_range(label)) { \
      id_label = i_array_get(label); \
      graph_key[0] = id_left; \
      graph_key[1] = id_label; \
      val_graph_key.mv_data = graph_key; \
      status.id = mdb_cursor_get(graph_lr, (&val_graph_key), (&val_graph_data), MDB_SET_KEY); \
      while (db_mdb_status_is_success) { \
        if (imht_set_contains(right_set, (db_graph_data_to_id((val_graph_data.mv_data))))) { \
          id_right = db_graph_data_to_id((val_graph_data.mv_data)); \
          status = db_graph_internal_delete_graph_rl(graph_rl, id_left, id_right, id_label); \
          db_mdb_status_expect_read; \
          db_mdb_status_require(mdb_cursor_del(graph_lr, 0)); \
        }; \
        status.id = mdb_cursor_get(graph_lr, (&val_graph_key), (&val_graph_data), MDB_NEXT_DUP); \
      }; \
      status = db_graph_internal_delete_graph_ll_conditional(graph_lr, graph_ll, id_label, id_left); \
      db_mdb_status_expect_read; \
      i_array_forward(label); \
    }; \
    i_array_rewind(label); \
    i_array_forward(left); \
  };
#define db_graph_internal_delete_1001_1101 \
  ordinal_min = ordinal->min; \
  ordinal_max = ordinal->max; \
  left = *left_pointer; \
  graph_data[0] = ordinal_min; \
  graph_key[1] = 0; \
  if (right_pointer) { \
    status_require(db_ids_to_set((*right_pointer), (&right_set))); \
  }; \
  set_range_1001_1101: \
  id_left = i_array_get(left); \
  graph_key[0] = id_left; \
  val_graph_key.mv_data = graph_key; \
  status.id = mdb_cursor_get(graph_lr, (&val_graph_key), (&val_graph_data), MDB_SET_RANGE); \
  each_key_1001_1101: \
  if (db_mdb_status_is_success) { \
    if (id_left == db_pointer_to_id((val_graph_key.mv_data))) { \
      val_graph_data.mv_data = graph_data; \
      status.id = mdb_cursor_get(graph_lr, (&val_graph_key), (&val_graph_data), MDB_GET_BOTH_RANGE); \
      if (db_mdb_status_is_success) { \
        id_label = db_pointer_to_id_at((val_graph_key.mv_data), 1); \
        goto each_data_1001_1101; \
      } else { \
        db_mdb_status_expect_notfound; \
      }; \
      status.id = mdb_cursor_get(graph_lr, (&val_graph_key), (&val_graph_data), MDB_NEXT_NODUP); \
      goto each_key_1001_1101; \
    }; \
  } else { \
    db_mdb_status_expect_notfound; \
  }; \
  i_array_forward(left); \
  if (i_array_in_range(left)) { \
    graph_key[1] = 0; \
    goto set_range_1001_1101; \
  } else { \
    goto exit; \
  }; \
  each_data_1001_1101: \
  /* get-both-range should have positioned cursor at >= ordinal-min */ \
  if (!ordinal_max || (db_graph_data_to_ordinal((val_graph_data.mv_data)) <= ordinal_max)) { \
    id_right = db_graph_data_to_id((val_graph_data.mv_data)); \
    if (!right_pointer || imht_set_contains(right_set, id_right)) { \
      status = db_graph_internal_delete_graph_rl(graph_rl, id_left, id_right, id_label); \
      db_mdb_status_expect_read; \
      db_mdb_status_require(mdb_cursor_del(graph_lr, 0)); \
    }; \
  } else { \
    goto next_label_1001_1101; \
  }; \
  status.id = mdb_cursor_get(graph_lr, (&val_graph_key), (&val_graph_data), MDB_NEXT_DUP); \
  if (db_mdb_status_is_success) { \
    goto each_data_1001_1101; \
  } else { \
    db_mdb_status_expect_notfound; \
  }; \
  status = db_graph_internal_delete_graph_ll_conditional(graph_lr, graph_ll, id_label, id_left); \
  db_mdb_status_expect_read; \
  next_label_1001_1101: \
  graph_key[0] = id_left; \
  graph_key[1] = id_label; \
  val_graph_key.mv_data = graph_key; \
  status.id = mdb_cursor_get(graph_lr, (&val_graph_key), (&val_graph_data), MDB_SET_KEY); \
  if (db_mdb_status_is_success) { \
    status.id = mdb_cursor_get(graph_lr, (&val_graph_key), (&val_graph_data), MDB_NEXT_NODUP); \
    goto each_key_1001_1101; \
  } else if (status.id == MDB_NOTFOUND) { \
    goto set_range_1001_1101; \
  } else { \
    status_set_group_goto(db_status_group_lmdb); \
  };
#define db_graph_internal_delete_1011_1111 \
  if (right_pointer) { \
    status_require(db_ids_to_set((*right_pointer), (&right_set))); \
  }; \
  ordinal_min = ordinal->min; \
  ordinal_max = ordinal->max; \
  left = *left_pointer; \
  label = *label_pointer; \
  graph_data[0] = ordinal_min; \
  id_label = i_array_get(label); \
  set_key_1011_1111: \
  id_left = i_array_get(left); \
  graph_key[0] = id_left; \
  graph_key[1] = id_label; \
  val_graph_key.mv_data = graph_key; \
  val_graph_data.mv_data = graph_data; \
  status.id = mdb_cursor_get(graph_lr, (&val_graph_key), (&val_graph_data), MDB_GET_BOTH_RANGE); \
  if (db_mdb_status_is_success) { \
    goto each_data_1011_1111; \
  } else { \
  each_key_1011_1111: \
    i_array_forward(left); \
    if (i_array_in_range(left)) { \
      goto set_key_1011_1111; \
    } else { \
      i_array_forward(label); \
      if (i_array_in_range(label)) { \
        id_label = i_array_get(label); \
        i_array_rewind(left); \
        goto set_key_1011_1111; \
      } else { \
        goto exit; \
      }; \
    }; \
  }; \
  each_data_1011_1111: \
  if (!ordinal_max || (db_graph_data_to_ordinal((val_graph_data.mv_data)) <= ordinal_max)) { \
    if (!right_pointer || imht_set_contains(right_set, (db_graph_data_to_id((val_graph_data.mv_data))))) { \
      /* delete graph-rl */ \
      id_right = db_graph_data_to_id((val_graph_data.mv_data)); \
      status = db_graph_internal_delete_graph_rl(graph_rl, id_left, id_right, id_label); \
      db_mdb_status_expect_read; \
      db_mdb_status_require(mdb_cursor_del(graph_lr, 0)); \
    }; \
    status.id = mdb_cursor_get(graph_lr, (&val_graph_key), (&val_graph_data), MDB_NEXT_DUP); \
    if (db_mdb_status_is_success) { \
      goto each_data_1011_1111; \
    } else { \
      db_mdb_status_expect_notfound; \
    }; \
  }; \
  status = db_graph_internal_delete_graph_ll_conditional(graph_lr, graph_ll, id_label, id_left); \
  db_mdb_status_expect_read; \
  goto each_key_1011_1111;
/** db-graph-internal-delete does not open/close cursors.
   1111 / left-right-label-ordinal.
   tip: the code is nice to debug if current state information is displayed near the
     beginning of goto labels before cursor operations.
     example: (debug-log "each-key-1100 %lu %lu" id-left id-right)
   db-graph-internal-delete-* macros are allowed to leave status on MDB-NOTFOUND.
  the inner internal-delete macros should probably be converted to functions */
status_t db_graph_internal_delete(db_ids_t* left_pointer, db_ids_t* right_pointer, db_ids_t* label_pointer, db_ordinal_condition_t* ordinal, MDB_cursor* graph_lr, MDB_cursor* graph_rl, MDB_cursor* graph_ll) {
  status_declare;
  db_ordinal_t ordinal_min;
  db_ordinal_t ordinal_max;
  db_id_t id_left;
  db_id_t id_right;
  db_id_t id_label;
  imht_set_t* right_set;
  i_array_declare(left, db_ids_t);
  i_array_declare(right, db_ids_t);
  i_array_declare(label, db_ids_t);
  db_mdb_declare_val_graph_key;
  db_mdb_declare_val_graph_data;
  db_mdb_declare_val_id;
  db_mdb_declare_val_id_2;
  db_declare_graph_key(graph_key);
  db_declare_graph_data(graph_data);
  if (left_pointer) {
    if (ordinal) {
      if (label_pointer) {
        db_graph_internal_delete_1011_1111;
      } else {
        db_graph_internal_delete_1001_1101;
      };
    } else {
      if (label_pointer) {
        if (right_pointer) {
          db_graph_internal_delete_1110;
        } else {
          db_graph_internal_delete_1010;
        };
      } else {
        if (right_pointer) {
          db_graph_internal_delete_1100;
        } else {
          db_graph_internal_delete_1000;
        };
      };
    };
  } else {
    if (right_pointer) {
      if (label_pointer) {
        db_graph_internal_delete_0110;
      } else {
        db_graph_internal_delete_0100;
      };
    } else {
      if (label_pointer) {
        db_graph_internal_delete_0010;
      } else {
        db_status_set_id_goto(db_status_id_not_implemented);
      };
    };
  };
exit:
  db_mdb_status_success_if_notfound;
  return (status);
};
/** db-relation-delete differs from db-relation-read in that it does not support
  partial processing and therefore does not need a state for repeated calls.
   it also differs in that it always needs all relation dbi
   to complete the deletion instead of just any dbi necessary to find relations.
  algorithm: delete all relations with any of the given ids at the corresponding position */
status_t db_graph_delete(db_txn_t txn, db_ids_t* left, db_ids_t* right, db_ids_t* label, db_ordinal_condition_t* ordinal) {
  status_declare;
  db_mdb_cursor_declare(graph_lr);
  db_mdb_cursor_declare(graph_rl);
  db_mdb_cursor_declare(graph_ll);
  db_mdb_status_require(db_mdb_env_cursor_open(txn, graph_lr));
  db_mdb_status_require(db_mdb_env_cursor_open(txn, graph_rl));
  db_mdb_status_require(db_mdb_env_cursor_open(txn, graph_ll));
  status = db_graph_internal_delete(left, right, label, ordinal, graph_lr, graph_rl, graph_ll);
exit:
  db_mdb_cursor_close_if_active(graph_lr);
  db_mdb_cursor_close_if_active(graph_rl);
  db_mdb_cursor_close_if_active(graph_ll);
  return (status);
};