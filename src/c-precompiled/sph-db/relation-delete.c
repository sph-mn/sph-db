status_t db_relation_internal_delete_relation_ll(MDB_cursor* relation_ll, db_id_t id_label, db_id_t id_left) {
  status_declare;
  db_mdb_declare_val_id;
  db_mdb_declare_val_id_2;
  val_id.mv_data = &id_label;
  val_id_2.mv_data = &id_left;
  status.id = mdb_cursor_get(relation_ll, (&val_id), (&val_id_2), MDB_GET_BOTH);
  if (db_mdb_status_is_success) {
    db_mdb_status_require((mdb_cursor_del(relation_ll, 0)));
  } else {
    db_mdb_status_expect_notfound;
  };
  status.id = status_id_success;
exit:
  status_return;
}
status_t db_relation_internal_delete_relation_ll_conditional(MDB_cursor* relation_lr, MDB_cursor* relation_ll, db_id_t id_label, db_id_t id_left) {
  status_declare;
  db_mdb_declare_val_null;
  db_mdb_declare_val_relation_key;
  db_declare_relation_key(relation_key);
  relation_key[0] = id_left;
  relation_key[1] = id_label;
  val_relation_key.mv_data = relation_key;
  status.id = mdb_cursor_get(relation_lr, (&val_relation_key), (&val_null), MDB_SET);
  return ((db_mdb_status_is_notfound ? db_relation_internal_delete_relation_ll(relation_ll, id_label, id_left) : status));
}
status_t db_relation_internal_delete_relation_rl(MDB_cursor* relation_rl, db_id_t id_left, db_id_t id_right, db_id_t id_label) {
  status_declare;
  db_mdb_declare_val_id;
  db_mdb_declare_val_relation_key;
  db_declare_relation_key(relation_key);
  relation_key[0] = id_right;
  relation_key[1] = id_label;
  val_relation_key.mv_data = relation_key;
  val_id.mv_data = &id_left;
  status.id = mdb_cursor_get(relation_rl, (&val_relation_key), (&val_id), MDB_GET_BOTH);
  if (db_mdb_status_is_success) {
    db_mdb_status_require((mdb_cursor_del(relation_rl, 0)));
  } else {
    db_mdb_status_expect_notfound;
  };
exit:
  status_return;
}
#define db_relation_internal_delete_0010 \
  label = *label_pointer; \
  set_key_0010: \
  id_label = sph_array_current_get(label); \
  val_id.mv_data = &id_label; \
  status.id = mdb_cursor_get(relation_ll, (&val_id), (&val_id_2), MDB_SET_KEY); \
  if (db_mdb_status_is_success) { \
    goto each_data_0010; \
  } else { \
    db_mdb_status_expect_notfound; \
  }; \
  each_key_0010: \
  sph_array_current_forward(label); \
  if (sph_array_current_in_range(label)) { \
    goto set_key_0010; \
  } else { \
    goto exit; \
  }; \
  each_data_0010: \
  id_left = db_pointer_to_id((val_id_2.mv_data)); \
  relation_key[0] = id_left; \
  relation_key[1] = id_label; \
  val_relation_key.mv_data = relation_key; \
  status.id = mdb_cursor_get(relation_lr, (&val_relation_key), (&val_relation_data), MDB_SET_KEY); \
  if (db_mdb_status_is_success) { \
  each_data_2_0010: \
    status = db_relation_internal_delete_relation_rl(relation_rl, id_left, (db_relation_data_to_id((val_relation_data.mv_data))), id_label); \
    db_mdb_status_expect_read; \
    db_mdb_status_require((mdb_cursor_del(relation_lr, 0))); \
    status.id = mdb_cursor_get(relation_lr, (&val_relation_key), (&val_relation_data), MDB_NEXT_DUP); \
    if (db_mdb_status_is_success) { \
      goto each_data_2_0010; \
    } else { \
      db_mdb_status_expect_notfound; \
    }; \
  } else { \
    db_mdb_status_expect_notfound; \
  }; \
  db_mdb_status_require((mdb_cursor_del(relation_ll, 0))); \
  status.id = mdb_cursor_get(relation_ll, (&val_id), (&val_id_2), MDB_NEXT_DUP); \
  if (db_mdb_status_is_success) { \
    goto each_data_0010; \
  } else { \
    db_mdb_status_expect_notfound; \
  }; \
  goto each_key_0010;
#define db_relation_internal_delete_0110 \
  label = *label_pointer; \
  right = *right_pointer; \
  set_key_0110: \
  id_right = sph_array_current_get(right); \
  id_label = sph_array_current_get(label); \
  relation_key[0] = id_right; \
  relation_key[1] = id_label; \
  val_relation_key.mv_data = relation_key; \
  status.id = mdb_cursor_get(relation_rl, (&val_relation_key), (&val_id), MDB_SET_KEY); \
  if (db_mdb_status_is_success) { \
    goto each_data_0110; \
  } else { \
    db_mdb_status_expect_notfound; \
  }; \
  each_key_0110: \
  sph_array_current_forward(right); \
  if (sph_array_current_in_range(right)) { \
    goto set_key_0110; \
  } else { \
    sph_array_current_forward(label); \
    if (sph_array_current_in_range(label)) { \
      sph_array_current_rewind(right); \
      goto set_key_0110; \
    } else { \
      goto exit; \
    }; \
  }; \
  each_data_0110: \
  id_left = db_pointer_to_id((val_id.mv_data)); \
  relation_key[0] = id_left; \
  relation_key[1] = id_label; \
  val_relation_key.mv_data = relation_key; \
  status.id = mdb_cursor_get(relation_lr, (&val_relation_key), (&val_relation_data), MDB_SET_KEY); \
  if (db_mdb_status_is_success) { \
    status = db_mdb_relation_lr_seek_right(relation_lr, id_right); \
    if (db_mdb_status_is_success) { \
      db_mdb_status_require((mdb_cursor_del(relation_lr, 0))); \
    } else { \
      db_mdb_status_expect_notfound; \
    }; \
  } else { \
    db_mdb_status_expect_notfound; \
  }; \
  status = db_relation_internal_delete_relation_ll(relation_ll, id_label, id_left); \
  db_mdb_status_expect_read; \
  db_mdb_status_require((mdb_cursor_del(relation_rl, 0))); \
  status.id = mdb_cursor_get(relation_rl, (&val_relation_key), (&val_id), MDB_NEXT_DUP); \
  if (db_mdb_status_is_success) { \
    goto each_data_0110; \
  } else { \
    db_mdb_status_expect_notfound; \
  }; \
  goto each_key_0110;
#define db_relation_internal_delete_1010 \
  left = *left_pointer; \
  label = *label_pointer; \
  while (sph_array_current_in_range(left)) { \
    id_left = sph_array_current_get(left); \
    while (sph_array_current_in_range(label)) { \
      id_label = sph_array_current_get(label); \
      relation_key[0] = id_left; \
      relation_key[1] = id_label; \
      val_relation_key.mv_data = relation_key; \
      status.id = mdb_cursor_get(relation_lr, (&val_relation_key), (&val_relation_data), MDB_SET_KEY); \
      if (db_mdb_status_is_success) { \
        do { \
          status = db_relation_internal_delete_relation_rl(relation_rl, id_left, (db_relation_data_to_id((val_relation_data.mv_data))), id_label); \
          db_mdb_status_expect_read; \
          status = db_relation_internal_delete_relation_ll(relation_ll, id_label, id_left); \
          db_mdb_status_expect_read; \
          status.id = mdb_cursor_get(relation_lr, (&val_relation_key), (&val_relation_data), MDB_NEXT_DUP); \
        } while (db_mdb_status_is_success); \
        db_mdb_status_expect_notfound; \
        relation_key[0] = id_left; \
        relation_key[1] = id_label; \
        val_relation_key.mv_data = relation_key; \
        db_mdb_status_require((mdb_cursor_get(relation_lr, (&val_relation_key), (&val_relation_data), MDB_SET_KEY))); \
        db_mdb_status_require((mdb_cursor_del(relation_lr, MDB_NODUPDATA))); \
      } else { \
        db_mdb_status_expect_notfound; \
      }; \
      sph_array_current_forward(label); \
    }; \
    sph_array_current_rewind(label); \
    sph_array_current_forward(left); \
  };
#define db_relation_internal_delete_0100 \
  db_id_t id_left; \
  db_id_t id_right; \
  db_id_t id_label; \
  right = *right_pointer; \
  set_range_0100: \
  id_right = sph_array_current_get(right); \
  relation_key[0] = id_right; \
  relation_key[1] = 0; \
  val_relation_key.mv_data = relation_key; \
  status.id = mdb_cursor_get(relation_rl, (&val_relation_key), (&val_id), MDB_SET_RANGE); \
  if (db_mdb_status_is_success) { \
    if (id_right == db_pointer_to_id((val_relation_key.mv_data))) { \
      id_label = db_pointer_to_id_at((val_relation_key.mv_data), 1); \
      goto each_data_0100; \
    }; \
  } else { \
    db_mdb_status_expect_notfound; \
  }; \
  sph_array_current_forward(right); \
  if (sph_array_current_in_range(right)) { \
    goto set_range_0100; \
  } else { \
    goto exit; \
  }; \
  each_data_0100: \
  id_left = db_pointer_to_id((val_id.mv_data)); \
  relation_key[0] = id_left; \
  relation_key[1] = id_label; \
  val_relation_key.mv_data = relation_key; \
  status.id = mdb_cursor_get(relation_lr, (&val_relation_key), (&val_relation_data), MDB_SET_KEY); \
  if (db_mdb_status_is_success) { \
    status = db_mdb_relation_lr_seek_right(relation_lr, id_right); \
    if (db_mdb_status_is_success) { \
      db_mdb_status_require((mdb_cursor_del(relation_lr, 0))); \
    } else { \
      db_mdb_status_expect_notfound; \
    }; \
  } else { \
    db_mdb_status_expect_notfound; \
  }; \
  status = db_relation_internal_delete_relation_ll_conditional(relation_lr, relation_ll, id_label, id_left); \
  db_mdb_status_expect_read; \
  db_mdb_status_require((mdb_cursor_del(relation_rl, 0))); \
  status.id = mdb_cursor_get(relation_rl, (&val_relation_key), (&val_id), MDB_NEXT_DUP); \
  if (db_mdb_status_is_success) { \
    goto each_data_0100; \
  } else { \
    db_mdb_status_expect_notfound; \
  }; \
  goto set_range_0100;
#define db_relation_internal_delete_1000 \
  left = *left_pointer; \
  set_range_1000: \
  id_left = sph_array_current_get(left); \
  relation_key[0] = id_left; \
  relation_key[1] = 0; \
  val_relation_key.mv_data = relation_key; \
  status.id = mdb_cursor_get(relation_lr, (&val_relation_key), (&val_relation_data), MDB_SET_RANGE); \
  each_key_1000: \
  if (db_mdb_status_is_success) { \
    if (id_left == db_pointer_to_id((val_relation_key.mv_data))) { \
      id_label = db_pointer_to_id_at((val_relation_key.mv_data), 1); \
      goto each_data_1000; \
    }; \
  } else { \
    db_mdb_status_expect_notfound; \
  }; \
  sph_array_current_forward(left); \
  if (sph_array_current_in_range(left)) { \
    goto set_range_1000; \
  } else { \
    goto exit; \
  }; \
  each_data_1000: \
  id_right = db_relation_data_to_id((val_relation_data.mv_data)); \
  status = db_relation_internal_delete_relation_rl(relation_rl, id_left, id_right, id_label); \
  db_mdb_status_expect_read; \
  status = db_relation_internal_delete_relation_ll(relation_ll, id_label, id_left); \
  db_mdb_status_expect_read; \
  status.id = mdb_cursor_get(relation_lr, (&val_relation_key), (&val_relation_data), MDB_NEXT_DUP); \
  if (db_mdb_status_is_success) { \
    goto each_data_1000; \
  } else { \
    db_mdb_status_expect_notfound; \
  }; \
  relation_key[0] = id_left; \
  relation_key[1] = id_label; \
  db_mdb_status_require((mdb_cursor_get(relation_lr, (&val_relation_key), (&val_relation_data), MDB_SET_KEY))); \
  db_mdb_status_require((mdb_cursor_del(relation_lr, MDB_NODUPDATA))); \
  status.id = mdb_cursor_get(relation_lr, (&val_relation_key), (&val_relation_data), MDB_NEXT_NODUP); \
  goto each_key_1000;
#define db_relation_internal_delete_1100 \
  status_require((db_ids_to_set((*right_pointer), (&right_set)))); \
  left = *left_pointer; \
  relation_key[1] = 0; \
  set_range_1100: \
  id_left = sph_array_current_get(left); \
  relation_key[0] = id_left; \
  val_relation_key.mv_data = relation_key; \
  status.id = mdb_cursor_get(relation_lr, (&val_relation_key), (&val_relation_data), MDB_SET_RANGE); \
  each_key_1100: \
  if (db_mdb_status_is_success) { \
    if (id_left == db_pointer_to_id((val_relation_key.mv_data))) { \
      id_label = db_pointer_to_id_at((val_relation_key.mv_data), 1); \
      goto each_data_1100; \
    }; \
  } else { \
    db_mdb_status_expect_notfound; \
  }; \
  sph_array_current_forward(left); \
  if (sph_array_current_in_range(left)) { \
    relation_key[1] = 0; \
    goto set_range_1100; \
  } else { \
    goto exit; \
  }; \
  each_data_1100: \
  id_right = db_relation_data_to_id((val_relation_data.mv_data)); \
  if (db_id_set_get(right_set, id_right)) { \
    status = db_relation_internal_delete_relation_rl(relation_rl, id_left, id_right, id_label); \
    db_mdb_status_expect_read; \
    db_mdb_status_require((mdb_cursor_del(relation_lr, 0))); \
  }; \
  status.id = mdb_cursor_get(relation_lr, (&val_relation_key), (&val_relation_data), MDB_NEXT_DUP); \
  if (db_mdb_status_is_success) { \
    goto each_data_1100; \
  } else { \
    db_mdb_status_expect_notfound; \
  }; \
  status = db_relation_internal_delete_relation_ll_conditional(relation_lr, relation_ll, id_label, id_left); \
  db_mdb_status_expect_read; \
  relation_key[0] = id_left; \
  relation_key[1] = id_label; \
  val_relation_key.mv_data = relation_key; \
  status.id = mdb_cursor_get(relation_lr, (&val_relation_key), (&val_relation_data), MDB_SET_KEY); \
  if (db_mdb_status_is_success) { \
    status.id = mdb_cursor_get(relation_lr, (&val_relation_key), (&val_relation_data), MDB_NEXT_NODUP); \
    goto each_key_1100; \
  } else if (status.id == MDB_NOTFOUND) { \
    goto set_range_1100; \
  } else { \
    status.group = db_status_group_lmdb; \
    goto exit; \
  };
#define db_relation_internal_delete_1110 \
  status_require((db_ids_to_set((*right_pointer), (&right_set)))); \
  left = *left_pointer; \
  label = *label_pointer; \
  while (sph_array_current_in_range(left)) { \
    id_left = sph_array_current_get(left); \
    while (sph_array_current_in_range(label)) { \
      id_label = sph_array_current_get(label); \
      relation_key[0] = id_left; \
      relation_key[1] = id_label; \
      val_relation_key.mv_data = relation_key; \
      status.id = mdb_cursor_get(relation_lr, (&val_relation_key), (&val_relation_data), MDB_SET_KEY); \
      while (db_mdb_status_is_success) { \
        if (db_id_set_get(right_set, (db_relation_data_to_id((val_relation_data.mv_data))))) { \
          id_right = db_relation_data_to_id((val_relation_data.mv_data)); \
          status = db_relation_internal_delete_relation_rl(relation_rl, id_left, id_right, id_label); \
          db_mdb_status_expect_read; \
          db_mdb_status_require((mdb_cursor_del(relation_lr, 0))); \
        }; \
        status.id = mdb_cursor_get(relation_lr, (&val_relation_key), (&val_relation_data), MDB_NEXT_DUP); \
      }; \
      status = db_relation_internal_delete_relation_ll_conditional(relation_lr, relation_ll, id_label, id_left); \
      db_mdb_status_expect_read; \
      sph_array_current_forward(label); \
    }; \
    sph_array_current_rewind(label); \
    sph_array_current_forward(left); \
  };
#define db_relation_internal_delete_1001_1101 \
  ordinal_min = ordinal->min; \
  ordinal_max = ordinal->max; \
  left = *left_pointer; \
  relation_data[0] = ordinal_min; \
  relation_key[1] = 0; \
  if (right_pointer) { \
    status_require((db_ids_to_set((*right_pointer), (&right_set)))); \
  }; \
  set_range_1001_1101: \
  id_left = sph_array_current_get(left); \
  relation_key[0] = id_left; \
  val_relation_key.mv_data = relation_key; \
  status.id = mdb_cursor_get(relation_lr, (&val_relation_key), (&val_relation_data), MDB_SET_RANGE); \
  each_key_1001_1101: \
  if (db_mdb_status_is_success) { \
    if (id_left == db_pointer_to_id((val_relation_key.mv_data))) { \
      val_relation_data.mv_data = relation_data; \
      status.id = mdb_cursor_get(relation_lr, (&val_relation_key), (&val_relation_data), MDB_GET_BOTH_RANGE); \
      if (db_mdb_status_is_success) { \
        id_label = db_pointer_to_id_at((val_relation_key.mv_data), 1); \
        goto each_data_1001_1101; \
      } else { \
        db_mdb_status_expect_notfound; \
      }; \
      status.id = mdb_cursor_get(relation_lr, (&val_relation_key), (&val_relation_data), MDB_NEXT_NODUP); \
      goto each_key_1001_1101; \
    }; \
  } else { \
    db_mdb_status_expect_notfound; \
  }; \
  sph_array_current_forward(left); \
  if (sph_array_current_in_range(left)) { \
    relation_key[1] = 0; \
    goto set_range_1001_1101; \
  } else { \
    goto exit; \
  }; \
  each_data_1001_1101: \
  /* get-both-range should have positioned cursor at >= ordinal-min */ \
  if (!ordinal_max || (db_relation_data_to_ordinal((val_relation_data.mv_data)) <= ordinal_max)) { \
    id_right = db_relation_data_to_id((val_relation_data.mv_data)); \
    if (!right_pointer || db_id_set_get(right_set, id_right)) { \
      status = db_relation_internal_delete_relation_rl(relation_rl, id_left, id_right, id_label); \
      db_mdb_status_expect_read; \
      db_mdb_status_require((mdb_cursor_del(relation_lr, 0))); \
    }; \
  } else { \
    goto next_label_1001_1101; \
  }; \
  status.id = mdb_cursor_get(relation_lr, (&val_relation_key), (&val_relation_data), MDB_NEXT_DUP); \
  if (db_mdb_status_is_success) { \
    goto each_data_1001_1101; \
  } else { \
    db_mdb_status_expect_notfound; \
  }; \
  status = db_relation_internal_delete_relation_ll_conditional(relation_lr, relation_ll, id_label, id_left); \
  db_mdb_status_expect_read; \
  next_label_1001_1101: \
  relation_key[0] = id_left; \
  relation_key[1] = id_label; \
  val_relation_key.mv_data = relation_key; \
  status.id = mdb_cursor_get(relation_lr, (&val_relation_key), (&val_relation_data), MDB_SET_KEY); \
  if (db_mdb_status_is_success) { \
    status.id = mdb_cursor_get(relation_lr, (&val_relation_key), (&val_relation_data), MDB_NEXT_NODUP); \
    goto each_key_1001_1101; \
  } else if (status.id == MDB_NOTFOUND) { \
    goto set_range_1001_1101; \
  } else { \
    status.group = db_status_group_lmdb; \
    goto exit; \
  };
#define db_relation_internal_delete_1011_1111 \
  if (right_pointer) { \
    status_require((db_ids_to_set((*right_pointer), (&right_set)))); \
  }; \
  ordinal_min = ordinal->min; \
  ordinal_max = ordinal->max; \
  left = *left_pointer; \
  label = *label_pointer; \
  relation_data[0] = ordinal_min; \
  id_label = sph_array_current_get(label); \
  set_key_1011_1111: \
  id_left = sph_array_current_get(left); \
  relation_key[0] = id_left; \
  relation_key[1] = id_label; \
  val_relation_key.mv_data = relation_key; \
  val_relation_data.mv_data = relation_data; \
  status.id = mdb_cursor_get(relation_lr, (&val_relation_key), (&val_relation_data), MDB_GET_BOTH_RANGE); \
  if (db_mdb_status_is_success) { \
    goto each_data_1011_1111; \
  } else { \
  each_key_1011_1111: \
    sph_array_current_forward(left); \
    if (sph_array_current_in_range(left)) { \
      goto set_key_1011_1111; \
    } else { \
      sph_array_current_forward(label); \
      if (sph_array_current_in_range(label)) { \
        id_label = sph_array_current_get(label); \
        sph_array_current_rewind(left); \
        goto set_key_1011_1111; \
      } else { \
        goto exit; \
      }; \
    }; \
  }; \
  each_data_1011_1111: \
  if (!ordinal_max || (db_relation_data_to_ordinal((val_relation_data.mv_data)) <= ordinal_max)) { \
    if (!right_pointer || db_id_set_get(right_set, (db_relation_data_to_id((val_relation_data.mv_data))))) { \
      /* delete relation-rl */ \
      id_right = db_relation_data_to_id((val_relation_data.mv_data)); \
      status = db_relation_internal_delete_relation_rl(relation_rl, id_left, id_right, id_label); \
      db_mdb_status_expect_read; \
      db_mdb_status_require((mdb_cursor_del(relation_lr, 0))); \
    }; \
    status.id = mdb_cursor_get(relation_lr, (&val_relation_key), (&val_relation_data), MDB_NEXT_DUP); \
    if (db_mdb_status_is_success) { \
      goto each_data_1011_1111; \
    } else { \
      db_mdb_status_expect_notfound; \
    }; \
  }; \
  status = db_relation_internal_delete_relation_ll_conditional(relation_lr, relation_ll, id_label, id_left); \
  db_mdb_status_expect_read; \
  goto each_key_1011_1111;

/** db-relation-internal-delete does not open/close cursors.
   1111 / left-right-label-ordinal.
   tip: the code is nice to debug if current state information is displayed near the
     beginning of goto labels before cursor operations.
     example: (debug-log "each-key-1100 %lu %lu" id-left id-right)
   db-relation-internal-delete-* macros are allowed to leave status on MDB-NOTFOUND.
  the inner internal-delete macros should probably be converted to functions */
status_t db_relation_internal_delete(db_ids_t* left_pointer, db_ids_t* right_pointer, db_ids_t* label_pointer, db_ordinal_condition_t* ordinal, MDB_cursor* relation_lr, MDB_cursor* relation_rl, MDB_cursor* relation_ll) {
  status_declare;
  db_ordinal_t ordinal_min;
  db_ordinal_t ordinal_max;
  db_id_t id_left;
  db_id_t id_right;
  db_id_t id_label;
  db_id_set_t right_set;
  sph_array_declare(left, db_ids_t);
  sph_array_declare(right, db_ids_t);
  sph_array_declare(label, db_ids_t);
  db_mdb_declare_val_relation_key;
  db_mdb_declare_val_relation_data;
  db_mdb_declare_val_id;
  db_mdb_declare_val_id_2;
  db_declare_relation_key(relation_key);
  db_declare_relation_data(relation_data);
  if (left_pointer) {
    if (ordinal) {
      if (label_pointer) {
        db_relation_internal_delete_1011_1111;
      } else {
        db_relation_internal_delete_1001_1101;
      };
    } else {
      if (label_pointer) {
        if (right_pointer) {
          db_relation_internal_delete_1110;
        } else {
          db_relation_internal_delete_1010;
        };
      } else {
        if (right_pointer) {
          db_relation_internal_delete_1100;
        } else {
          db_relation_internal_delete_1000;
        };
      };
    };
  } else {
    if (right_pointer) {
      if (label_pointer) {
        db_relation_internal_delete_0110;
      } else {
        db_relation_internal_delete_0100;
      };
    } else {
      if (label_pointer) {
        db_relation_internal_delete_0010;
      } else {
        status_set_goto(db_status_group_db, db_status_id_not_implemented);
      };
    };
  };
exit:
  db_mdb_status_success_if_notfound;
  status_return;
}

/** db-relation-delete differs from db-relation-read in that it does not support
  partial processing and therefore does not need a state for repeated calls.
   it also differs in that it always needs all relation dbi
   to complete the deletion instead of just any dbi necessary to find relations.
  algorithm: delete all relations with any of the given ids at the corresponding position */
status_t db_relation_delete(db_txn_t txn, db_ids_t* left, db_ids_t* right, db_ids_t* label, db_ordinal_condition_t* ordinal) {
  status_declare;
  db_mdb_cursor_declare(relation_lr);
  db_mdb_cursor_declare(relation_rl);
  db_mdb_cursor_declare(relation_ll);
  db_mdb_status_require((db_mdb_env_cursor_open(txn, relation_lr)));
  db_mdb_status_require((db_mdb_env_cursor_open(txn, relation_rl)));
  db_mdb_status_require((db_mdb_env_cursor_open(txn, relation_ll)));
  status = db_relation_internal_delete(left, right, label, ordinal, relation_lr, relation_rl, relation_ll);
exit:
  db_mdb_cursor_close_if_active(relation_lr);
  db_mdb_cursor_close_if_active(relation_rl);
  db_mdb_cursor_close_if_active(relation_ll);
  status_return;
}
