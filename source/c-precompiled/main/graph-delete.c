status_t db_graph_internal_delete_label_to_left(MDB_cursor* label_to_left,
  db_id_t id_label,
  db_id_t id_left) {
  db_mdb_declare_val_id;
  db_mdb_declare_val_id_2;
  status_init;
  val_id.mv_data = &id_label;
  val_id_2.mv_data = &id_left;
  db_mdb_cursor_get_x(label_to_left, val_id, val_id_2, MDB_GET_BOTH);
  if (db_mdb_status_success_p) {
    db_mdb_cursor_del_x(label_to_left, 0);
    db_mdb_status_require;
  } else {
    db_mdb_status_require_notfound;
  };
  status_set_id(status_id_success);
exit:
  return (status);
};
status_t
db_graph_internal_delete_label_to_left_conditional(MDB_cursor* left_to_right,
  MDB_cursor* label_to_left,
  db_id_t id_label,
  db_id_t id_left) {
  status_init;
  db_mdb_declare_val_graph_key;
  db_define_graph_key(graph_key);
  (*(graph_key + 0)) = id_left;
  (*(graph_key + 1)) = id_label;
  val_graph_key.mv_data = graph_key;
  db_mdb_cursor_get_x(left_to_right, val_graph_key, val_null, MDB_SET);
  if (status_id_is_p(MDB_NOTFOUND)) {
    return (
      db_graph_internal_delete_label_to_left(label_to_left, id_label, id_left));
  } else {
    db_mdb_status_require;
  };
exit:
  return (status);
};
status_t db_graph_internal_delete_right_to_left(MDB_cursor* right_to_left,
  db_id_t id_left,
  db_id_t id_right,
  db_id_t id_label) {
  status_init;
  db_mdb_declare_val_graph_key;
  db_mdb_declare_val_id;
  db_define_graph_key(graph_key);
  (*(graph_key + 0)) = id_right;
  (*(graph_key + 1)) = id_label;
  val_graph_key.mv_data = graph_key;
  val_id.mv_data = &id_left;
  db_mdb_cursor_get_x(right_to_left, val_graph_key, val_id, MDB_GET_BOTH);
  if (db_mdb_status_success_p) {
    db_mdb_cursor_del_x(right_to_left, 0);
    db_mdb_status_require;
  } else {
    db_mdb_status_require_notfound;
  };
exit:
  return (status);
};
#define db_graph_internal_delete_0010() \
  db_id_t id_label; \
  db_id_t id_left; \
  set_key_0010: \
  id_label = db_ids_first(label); \
  val_id.mv_data = &id_label; \
  db_mdb_cursor_get_x(label_to_left, val_id, val_id_2, MDB_SET_KEY); \
  if (db_mdb_status_success_p) { \
    goto each_data_0010; \
  } else { \
    db_mdb_status_require_notfound; \
  }; \
  each_key_0010: \
  label = db_ids_rest(label); \
  if (label) { \
    goto set_key_0010; \
  } else { \
    goto exit; \
  }; \
  each_data_0010: \
  id_left = db_mdb_val_to_id(val_id_2); \
  (*(graph_key + 0)) = id_left; \
  (*(graph_key + 1)) = id_label; \
  val_graph_key.mv_data = graph_key; \
  db_mdb_cursor_get_x( \
    left_to_right, val_graph_key, val_graph_data, MDB_SET_KEY); \
  if (db_mdb_status_success_p) { \
  each_data_2_0010: \
    status = db_graph_internal_delete_right_to_left(right_to_left, \
      id_left, \
      db_mdb_val_graph_data_to_id(val_graph_data), \
      id_label); \
    db_mdb_status_require_read; \
    db_mdb_cursor_del_x(left_to_right, 0); \
    db_mdb_status_require; \
    db_mdb_cursor_next_dup_x(left_to_right, val_graph_key, val_graph_data); \
    if (db_mdb_status_success_p) { \
      goto each_data_2_0010; \
    } else { \
      db_mdb_status_require_notfound; \
    }; \
  } else { \
    db_mdb_status_require_notfound; \
  }; \
  db_mdb_cursor_del_x(label_to_left, 0); \
  db_mdb_status_require; \
  db_mdb_cursor_next_dup_x(label_to_left, val_id, val_id_2); \
  if (db_mdb_status_success_p) { \
    goto each_data_0010; \
  } else { \
    db_mdb_status_require_notfound; \
  }; \
  goto each_key_0010
#define db_graph_internal_delete_0110() \
  db_id_t id_right; \
  db_id_t id_left; \
  db_id_t id_label; \
  db_ids_t* right_pointer = right; \
  set_key_0110: \
  id_right = db_ids_first(right_pointer); \
  id_label = db_ids_first(label); \
  (*(graph_key + 0)) = id_right; \
  (*(graph_key + 1)) = id_label; \
  val_graph_key.mv_data = graph_key; \
  db_mdb_cursor_get_x(right_to_left, val_graph_key, val_id, MDB_SET_KEY); \
  if (db_mdb_status_success_p) { \
    goto each_data_0110; \
  } else { \
    db_mdb_status_require_notfound; \
  }; \
  each_key_0110: \
  right_pointer = db_ids_rest(right_pointer); \
  if (right_pointer) { \
    goto set_key_0110; \
  } else { \
    label = db_ids_rest(label); \
    if (label) { \
      right_pointer = right; \
      goto set_key_0110; \
    } else { \
      goto exit; \
    }; \
  }; \
  each_data_0110: \
  id_left = db_mdb_val_to_id(val_id); \
  (*(graph_key + 0)) = id_left; \
  (*(graph_key + 1)) = id_label; \
  val_graph_key.mv_data = graph_key; \
  db_mdb_cursor_get_x( \
    left_to_right, val_graph_key, val_graph_data, MDB_SET_KEY); \
  if (db_mdb_status_success_p) { \
    status = db_mdb_left_to_right_seek_right(left_to_right, id_right); \
    if (db_mdb_status_success_p) { \
      db_mdb_cursor_del_x(left_to_right, 0); \
      db_mdb_status_require; \
    } else { \
      db_mdb_status_require_notfound; \
    }; \
  } else { \
    db_mdb_status_require_notfound; \
  }; \
  status = \
    db_graph_internal_delete_label_to_left(label_to_left, id_label, id_left); \
  db_mdb_status_require_read; \
  db_mdb_cursor_del_x(right_to_left, 0); \
  db_mdb_status_require; \
  db_mdb_cursor_next_dup_x(right_to_left, val_graph_key, val_id); \
  if (db_mdb_status_success_p) { \
    goto each_data_0110; \
  } else { \
    db_mdb_status_require_notfound; \
  }; \
  goto each_key_0110
#define db_graph_internal_delete_1010() \
  db_id_t id_label; \
  db_id_t id_left; \
  db_ids_t* label_pointer; \
  while (left) { \
    id_left = db_ids_first(left); \
    label_pointer = label; \
    while (label_pointer) { \
      id_label = db_ids_first(label_pointer); \
      (*(graph_key + 0)) = id_left; \
      (*(graph_key + 1)) = id_label; \
      val_graph_key.mv_data = graph_key; \
      db_mdb_cursor_get_x( \
        left_to_right, val_graph_key, val_graph_data, MDB_SET_KEY); \
      if (db_mdb_status_success_p) { \
        do { \
          db_graph_internal_delete_right_to_left(right_to_left, \
            id_left, \
            db_mdb_val_graph_data_to_id(val_graph_data), \
            id_label); \
          db_graph_internal_delete_label_to_left( \
            label_to_left, id_label, id_left); \
          db_mdb_cursor_next_dup_x( \
            left_to_right, val_graph_key, val_graph_data); \
        } while (db_mdb_status_success_p); \
        db_mdb_status_require_notfound; \
        (*(graph_key + 0)) = id_left; \
        (*(graph_key + 1)) = id_label; \
        val_graph_key.mv_data = graph_key; \
        db_mdb_cursor_get_x( \
          left_to_right, val_graph_key, val_graph_data, MDB_SET_KEY); \
        db_mdb_status_require; \
        db_mdb_cursor_del_x(left_to_right, MDB_NODUPDATA); \
        db_mdb_status_require; \
      } else { \
        db_mdb_status_require_notfound; \
      }; \
      label_pointer = db_ids_rest(label_pointer); \
    }; \
    left = db_ids_rest(left); \
  }
#define db_graph_internal_delete_0100() \
  db_id_t id_left; \
  db_id_t id_right; \
  db_id_t id_label; \
  set_range_0100: \
  id_right = db_ids_first(right); \
  (*(graph_key + 0)) = id_right; \
  (*(graph_key + 1)) = 0; \
  val_graph_key.mv_data = graph_key; \
  db_mdb_cursor_get_x(right_to_left, val_graph_key, val_id, MDB_SET_RANGE); \
  if (db_mdb_status_success_p) { \
    if ((id_right == db_mdb_val_to_id_at(val_graph_key, 0))) { \
      if (db_mdb_status_success_p) { \
      }; \
      id_label = db_mdb_val_to_id_at(val_graph_key, 1); \
      goto each_data_0100; \
    }; \
  } else { \
    db_mdb_status_require_notfound; \
  }; \
  right = db_ids_rest(right); \
  if (right) { \
    goto set_range_0100; \
  } else { \
    goto exit; \
  }; \
  each_data_0100: \
  id_left = db_mdb_val_to_id(val_id); \
  (*(graph_key + 0)) = id_left; \
  (*(graph_key + 1)) = id_label; \
  val_graph_key.mv_data = graph_key; \
  db_mdb_cursor_get_x( \
    left_to_right, val_graph_key, val_graph_data, MDB_SET_KEY); \
  if (db_mdb_status_success_p) { \
    status = db_mdb_left_to_right_seek_right(left_to_right, id_right); \
    if (db_mdb_status_success_p) { \
      db_mdb_cursor_del_x(left_to_right, 0); \
      db_mdb_status_require; \
    } else { \
      db_mdb_status_require_notfound; \
    }; \
  } else { \
    db_mdb_status_require_notfound; \
  }; \
  status_require_x(db_graph_internal_delete_label_to_left_conditional( \
    left_to_right, label_to_left, id_label, id_left)); \
  db_mdb_cursor_del_x(right_to_left, 0); \
  db_mdb_status_require; \
  db_mdb_cursor_next_dup_x(right_to_left, val_graph_key, val_id); \
  if (db_mdb_status_success_p) { \
    goto each_data_0100; \
  } else { \
    db_mdb_status_require_notfound; \
  }; \
  goto set_range_0100
#define db_graph_internal_delete_1000() \
  db_id_t id_left; \
  db_id_t id_label; \
  db_id_t id_right; \
  set_range_1000: \
  id_left = db_ids_first(left); \
  (*(graph_key + 0)) = id_left; \
  (*(graph_key + 1)) = 0; \
  val_graph_key.mv_data = graph_key; \
  db_mdb_cursor_get_x( \
    left_to_right, val_graph_key, val_graph_data, MDB_SET_RANGE); \
  each_key_1000: \
  if (db_mdb_status_success_p) { \
    if ((id_left == db_mdb_val_to_id_at(val_graph_key, 0))) { \
      id_label = db_mdb_val_to_id_at(val_graph_key, 1); \
      goto each_data_1000; \
    }; \
  } else { \
    db_mdb_status_require_notfound; \
  }; \
  left = db_ids_rest(left); \
  if (left) { \
    goto set_range_1000; \
  } else { \
    goto exit; \
  }; \
  each_data_1000: \
  id_right = db_mdb_val_graph_data_to_id(val_graph_data); \
  db_graph_internal_delete_right_to_left( \
    right_to_left, id_left, id_right, id_label); \
  db_graph_internal_delete_label_to_left(label_to_left, id_label, id_left); \
  db_mdb_cursor_next_dup_x(left_to_right, val_graph_key, val_graph_data); \
  if (db_mdb_status_success_p) { \
    goto each_data_1000; \
  } else { \
    db_mdb_status_require_notfound; \
  }; \
  (*(graph_key + 0)) = id_left; \
  (*(graph_key + 1)) = id_label; \
  db_mdb_cursor_get_x( \
    left_to_right, val_graph_key, val_graph_data, MDB_SET_KEY); \
  db_mdb_status_require; \
  db_mdb_cursor_del_x(left_to_right, MDB_NODUPDATA); \
  db_mdb_status_require; \
  db_mdb_cursor_next_nodup_x(left_to_right, val_graph_key, val_graph_data); \
  goto each_key_1000
#define db_graph_internal_delete_1100() \
  db_id_t id_left; \
  db_id_t id_right; \
  db_id_t id_label; \
  imht_set_t* right_set; \
  status_require_x(db_ids_to_set(right, &right_set)); \
  (*(graph_key + 1)) = 0; \
  set_range_1100: \
  id_left = db_ids_first(left); \
  (*(graph_key + 0)) = id_left; \
  val_graph_key.mv_data = graph_key; \
  db_mdb_cursor_get_x( \
    left_to_right, val_graph_key, val_graph_data, MDB_SET_RANGE); \
  each_key_1100: \
  if (db_mdb_status_success_p) { \
    if ((id_left == db_mdb_val_to_id_at(val_graph_key, 0))) { \
      id_label = db_mdb_val_to_id_at(val_graph_key, 1); \
      goto each_data_1100; \
    }; \
  } else { \
    db_mdb_status_require_notfound; \
  }; \
  left = db_ids_rest(left); \
  if (left) { \
    (*(graph_key + 1)) = 0; \
    goto set_range_1100; \
  } else { \
    goto exit; \
  }; \
  each_data_1100: \
  id_right = db_mdb_val_graph_data_to_id(val_graph_data); \
  if (imht_set_contains_p(right_set, id_right)) { \
    db_graph_internal_delete_right_to_left( \
      right_to_left, id_left, id_right, id_label); \
    db_mdb_cursor_del_x(left_to_right, 0); \
    db_mdb_status_require; \
  }; \
  db_mdb_cursor_next_dup_x(left_to_right, val_graph_key, val_graph_data); \
  if (db_mdb_status_success_p) { \
    goto each_data_1100; \
  } else { \
    db_mdb_status_require_notfound; \
  }; \
  status_require_x(db_graph_internal_delete_label_to_left_conditional( \
    left_to_right, label_to_left, id_label, id_left)); \
  (*(graph_key + 0)) = id_left; \
  (*(graph_key + 1)) = id_label; \
  val_graph_key.mv_data = graph_key; \
  db_mdb_cursor_get_x( \
    left_to_right, val_graph_key, val_graph_data, MDB_SET_KEY); \
  if (db_mdb_status_success_p) { \
    db_mdb_cursor_next_nodup_x(left_to_right, val_graph_key, val_graph_data); \
    goto each_key_1100; \
  } else if (status_id_is_p(MDB_NOTFOUND)) { \
    goto set_range_1100; \
  } else { \
    status_set_group_goto(db_status_group_lmdb); \
  }
#define db_graph_internal_delete_1110() \
  db_id_t id_left; \
  db_id_t id_label; \
  imht_set_t* right_set; \
  db_id_t id_right; \
  db_ids_t* label_first = label; \
  status_require_x(db_ids_to_set(right, &right_set)); \
  while (left) { \
    id_left = db_ids_first(left); \
    while (label) { \
      id_label = db_ids_first(label); \
      (*(graph_key + 0)) = id_left; \
      (*(graph_key + 1)) = id_label; \
      val_graph_key.mv_data = graph_key; \
      db_mdb_cursor_get_x( \
        left_to_right, val_graph_key, val_graph_data, MDB_SET_KEY); \
      while (db_mdb_status_success_p) { \
        if (imht_set_contains_p( \
              right_set, db_mdb_val_graph_data_to_id(val_graph_data))) { \
          id_right = db_mdb_val_graph_data_to_id(val_graph_data); \
          db_graph_internal_delete_right_to_left( \
            right_to_left, id_left, id_right, id_label); \
          db_mdb_cursor_del_x(left_to_right, 0); \
          db_mdb_status_require; \
        }; \
        db_mdb_cursor_next_dup_x( \
          left_to_right, val_graph_key, val_graph_data); \
      }; \
      db_graph_internal_delete_label_to_left_conditional( \
        left_to_right, label_to_left, id_label, id_left); \
      label = db_ids_rest(label); \
    }; \
    label = label_first; \
    left = db_ids_rest(left); \
  }
#define db_graph_internal_delete_get_ordinal_data(ordinal) \
  db_ordinal_t ordinal_min = (*ordinal).min; \
  db_ordinal_t ordinal_max = (*ordinal).max
#define db_graph_internal_delete_1001_1101() \
  db_id_t id_left; \
  db_id_t id_right; \
  db_id_t id_label; \
  imht_set_t* right_set; \
  db_graph_internal_delete_get_ordinal_data(ordinal); \
  (*(graph_data + 0)) = ordinal_min; \
  (*(graph_key + 1)) = 0; \
  if (right) { \
    status_require_x(db_ids_to_set(right, &right_set)); \
  }; \
  set_range_1001_1101: \
  id_left = db_ids_first(left); \
  (*(graph_key + 0)) = id_left; \
  val_graph_key.mv_data = graph_key; \
  db_mdb_cursor_get_x( \
    left_to_right, val_graph_key, val_graph_data, MDB_SET_RANGE); \
  each_key_1001_1101: \
  if (db_mdb_status_success_p) { \
    if ((id_left == db_mdb_val_to_id_at(val_graph_key, 0))) { \
      val_graph_data.mv_data = graph_data; \
      db_mdb_cursor_get_x( \
        left_to_right, val_graph_key, val_graph_data, MDB_GET_BOTH_RANGE); \
      if (db_mdb_status_success_p) { \
        id_label = db_mdb_val_to_id_at(val_graph_key, 1); \
        goto each_data_1001_1101; \
      } else { \
        db_mdb_status_require_notfound; \
      }; \
      db_mdb_cursor_next_nodup_x( \
        left_to_right, val_graph_key, val_graph_data); \
      goto each_key_1001_1101; \
    }; \
  } else { \
    db_mdb_status_require_notfound; \
  }; \
  left = db_ids_rest(left); \
  if (left) { \
    (*(graph_key + 1)) = 0; \
    goto set_range_1001_1101; \
  } else { \
    goto exit; \
  }; \
  each_data_1001_1101: \
  if ((!ordinal_max || \
        ((db_mdb_val_graph_data_to_ordinal(val_graph_data) <= \
          ordinal_max)))) { \
    id_right = db_mdb_val_graph_data_to_id(val_graph_data); \
    if ((!right || imht_set_contains_p(right_set, id_right))) { \
      status = db_graph_internal_delete_right_to_left( \
        right_to_left, id_left, id_right, id_label); \
      db_mdb_status_require_read; \
      db_mdb_cursor_del_x(left_to_right, 0); \
      db_mdb_status_require; \
    }; \
  } else { \
    goto next_label_1001_1101; \
  }; \
  db_mdb_cursor_next_dup_x(left_to_right, val_graph_key, val_graph_data); \
  if (db_mdb_status_success_p) { \
    goto each_data_1001_1101; \
  } else { \
    db_mdb_status_require_notfound; \
  }; \
  status_require_x(db_graph_internal_delete_label_to_left_conditional( \
    left_to_right, label_to_left, id_label, id_left)); \
  next_label_1001_1101: \
  (*(graph_key + 0)) = id_left; \
  (*(graph_key + 1)) = id_label; \
  val_graph_key.mv_data = graph_key; \
  db_mdb_cursor_get_x( \
    left_to_right, val_graph_key, val_graph_data, MDB_SET_KEY); \
  if (db_mdb_status_success_p) { \
    db_mdb_cursor_next_nodup_x(left_to_right, val_graph_key, val_graph_data); \
    goto each_key_1001_1101; \
  } else if (status_id_is_p(MDB_NOTFOUND)) { \
    goto set_range_1001_1101; \
  } else { \
    status_set_group_goto(db_status_group_lmdb); \
  }
#define db_graph_internal_delete_1011_1111() \
  db_id_t id_left; \
  db_id_t id_label; \
  imht_set_t* right_set; \
  db_id_t id_right; \
  db_ids_t* left_pointer = left; \
  if (right) { \
    status_require_x(db_ids_to_set(right, &right_set)); \
  }; \
  db_graph_internal_delete_get_ordinal_data(ordinal); \
  (*(graph_data + 0)) = ordinal_min; \
  id_label = db_ids_first(label); \
  set_key_1011_1111: \
  id_left = db_ids_first(left_pointer); \
  (*(graph_key + 0)) = id_left; \
  (*(graph_key + 1)) = id_label; \
  val_graph_key.mv_data = graph_key; \
  val_graph_data.mv_data = graph_data; \
  db_mdb_cursor_get_x( \
    left_to_right, val_graph_key, val_graph_data, MDB_GET_BOTH_RANGE); \
  if (db_mdb_status_success_p) { \
    goto each_data_1011_1111; \
  } else { \
  each_key_1011_1111: \
    left_pointer = db_ids_rest(left_pointer); \
    if (left_pointer) { \
      goto set_key_1011_1111; \
    } else { \
      label = db_ids_rest(label); \
      if (label) { \
        left_pointer = left; \
        id_label = db_ids_first(label); \
        goto set_key_1011_1111; \
      } else { \
        goto exit; \
      }; \
    }; \
  }; \
  each_data_1011_1111: \
  if ((!ordinal_max || \
        ((db_mdb_val_graph_data_to_ordinal(val_graph_data) <= \
          ordinal_max)))) { \
    if ((!right || \
          imht_set_contains_p( \
            right_set, db_mdb_val_graph_data_to_id(val_graph_data)))) { \
      id_right = db_mdb_val_graph_data_to_id(val_graph_data); \
      status = db_graph_internal_delete_right_to_left( \
        right_to_left, id_left, id_right, id_label); \
      db_mdb_status_require_read; \
      db_mdb_cursor_del_x(left_to_right, 0); \
      db_mdb_status_require; \
    }; \
    db_mdb_cursor_next_dup_x(left_to_right, val_graph_key, val_graph_data); \
    if (db_mdb_status_success_p) { \
      goto each_data_1011_1111; \
    } else { \
      db_mdb_status_require_notfound; \
    }; \
  }; \
  status_require_x(db_graph_internal_delete_label_to_left_conditional( \
    left_to_right, label_to_left, id_label, id_left)); \
  goto each_key_1011_1111
/** db-graph-internal-delete does not open/close cursors.
  1111 / left-right-label-ordinal.
  tip: the code is nice to debug if variable state is displayed near the
    beginning of goto labels, before cursor operations.
    example display on stdout: (debug-log "each-key-1100 %lu %lu" id-left
  id-right) */
status_t db_graph_internal_delete(db_ids_t* left,
  db_ids_t* right,
  db_ids_t* label,
  db_ordinal_condition_t* ordinal,
  MDB_cursor* left_to_right,
  MDB_cursor* right_to_left,
  MDB_cursor* label_to_left) {
  status_init;
  db_mdb_declare_val_graph_key;
  db_mdb_declare_val_graph_data;
  db_mdb_declare_val_id;
  db_mdb_declare_val_id_2;
  db_define_graph_key(graph_key);
  db_define_graph_data(graph_data);
  if (left) {
    if (ordinal) {
      if (label) {
        db_graph_internal_delete_1011_1111();
      } else {
        db_graph_internal_delete_1001_1101();
      };
    } else {
      if (label) {
        if (right) {
          db_graph_internal_delete_1110();
        } else {
          db_graph_internal_delete_1010();
        };
      } else {
        if (right) {
          db_graph_internal_delete_1100();
        } else {
          db_graph_internal_delete_1000();
        };
      };
    };
  } else {
    if (right) {
      if (label) {
        db_graph_internal_delete_0110();
      } else {
        db_graph_internal_delete_0100();
      };
    } else {
      if (label) {
        db_graph_internal_delete_0010();
      } else {
        db_status_set_id_goto(db_status_id_not_implemented);
      };
    };
  };
exit:
  db_status_success_if_mdb_notfound;
  return (status);
};
status_t db_graph_delete(db_txn_t txn,
  db_ids_t* left,
  db_ids_t* right,
  db_ids_t* label,
  db_ordinal_condition_t* ordinal) {
  status_init;
  db_mdb_cursor_define_3(txn.mdb_txn,
    (*txn.s).dbi_left_to_right,
    left_to_right,
    (*txn.s).dbi_right_to_left,
    right_to_left,
    (*txn.s).dbi_label_to_left,
    label_to_left);
  status = db_graph_internal_delete(
    left, right, label, ordinal, left_to_right, right_to_left, label_to_left);
exit:
  db_mdb_cursor_close_3(left_to_right, right_to_left, label_to_left);
  return (status);
};