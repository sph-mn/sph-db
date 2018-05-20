#define db_index_errors_graph_log(message, left, right, label) \
  db_error_log("(groups index graph) (description \"%s\") (left %lu) (right " \
               "%lu) (label %lu)", \
    message, \
    left, \
    right, \
    label)
#define db_index_errors_data_log(message, type, id) \
  db_error_log("(groups index %s) (description %s) (id %lu)", type, message, id)
status_t db_index_recreate_graph() {
  status_init;
  db_mdb_declare_val_graph_key;
  db_mdb_declare_val_graph_data;
  db_mdb_declare_val_id;
  db_mdb_declare_val_id_2;
  db_define_graph_data(graph_data);
  db_define_graph_key(graph_key);
  db_mdb_cursor_declare_3(left_to_right, right_to_left, label_to_left);
  db_txn_introduce;
  db_txn_write_begin;
  db_mdb_status_require_x(mdb_drop(db_txn, dbi_right_to_left, 0));
  db_mdb_status_require_x(mdb_drop(db_txn, dbi_label_to_left, 0));
  db_txn_commit;
  db_txn_write_begin;
  db_mdb_cursor_open_3(db_txn, left_to_right, right_to_left, label_to_left);
  db_id_t id_left;
  db_id_t id_right;
  db_id_t id_label;
  db_mdb_cursor_each_key(left_to_right, val_graph_key, val_graph_data, {
    id_left = db_mdb_val_to_id_at(val_graph_key, 0);
    id_label = db_mdb_val_to_id_at(val_graph_key, 1);
    do {
      id_right = db_mdb_val_graph_data_to_id(val_graph_data);
      (*(graph_key + 0)) = id_right;
      (*(graph_key + 1)) = id_label;
      val_graph_key.mv_data = graph_key;
      val_id.mv_data = &id_left;
      db_mdb_status_require_x(
        mdb_cursor_put(right_to_left, &val_graph_key, &val_id, 0));
      val_id_2.mv_data = &id_label;
      db_mdb_status_require_x(
        mdb_cursor_put(label_to_left, &val_id_2, &val_id, 0));
      db_mdb_cursor_next_dup_x(left_to_right, val_graph_key, val_graph_data);
    } while (db_mdb_status_success_p);
  });
  db_txn_commit;
exit:
  if (db_txn) {
    db_txn_abort;
  };
  return (status);
};
status_t db_index_recreate_intern() {
  status_init;
  db_mdb_declare_val_id;
  db_mdb_declare_val_data;
  db_mdb_cursor_declare_2(id_to_data, data_intern_to_id);
  db_txn_introduce;
  db_txn_write_begin;
  mdb_drop(db_txn, dbi_data_intern_to_id, 0);
  db_txn_commit;
  db_txn_write_begin;
  db_mdb_cursor_open_2(db_txn, id_to_data, data_intern_to_id);
  db_mdb_cursor_each_key(id_to_data, val_id, val_data, {
    if ((val_data.mv_size && db_intern_p(db_mdb_val_to_id(val_id)))) {
      db_mdb_status_require_x(
        mdb_cursor_put(data_intern_to_id, &val_data, &val_id, 0));
    };
  });
  db_txn_commit;
exit:
  if (db_txn) {
    db_txn_abort;
  };
  return (status);
};
status_t db_index_recreate_extern() {
  status_init;
  db_mdb_declare_val_id;
  db_mdb_declare_val_data;
  db_mdb_cursor_declare(id_to_data);
  db_mdb_cursor_declare(data_intern_to_id);
  db_txn_introduce;
  db_txn_write_begin;
  mdb_drop(db_txn, dbi_data_intern_to_id, 0);
  db_txn_commit;
  db_txn_write_begin;
  db_mdb_cursor_open(db_txn, id_to_data);
  db_mdb_cursor_open(db_txn, data_intern_to_id);
  db_mdb_cursor_each_key(id_to_data, val_id, val_data, {
    if ((val_data.mv_size && db_intern_p(db_mdb_val_to_id(val_id)))) {
      db_mdb_status_require_x(
        mdb_cursor_put(data_intern_to_id, &val_data, &val_id, 0));
    };
  });
  db_txn_commit;
exit:
  if (db_txn) {
    db_txn_abort;
  };
  return (status);
};
status_t
db_index_errors_graph(db_txn_t* db_txn, db_index_errors_graph_t* result) {
  status_init;
  (*result) = db_index_errors_graph_null;
  db_mdb_declare_val_id;
  db_mdb_declare_val_id_2;
  db_mdb_declare_val_graph_key;
  db_mdb_declare_val_graph_data;
  db_id_t id_right;
  db_id_t id_left;
  db_id_t id_label;
  db_graph_records_t* records_temp;
  db_graph_record_t record;
  db_define_graph_key(graph_key);
  db_define_graph_data(graph_data);
  db_mdb_cursor_define_3(db_txn, left_to_right, right_to_left, label_to_left);
  db_mdb_cursor_each_key(left_to_right, val_graph_key, val_graph_data, {
    id_left = db_mdb_val_to_id_at(val_graph_key, 0);
    id_label = db_mdb_val_to_id_at(val_graph_key, 1);
    do {
      id_right = db_mdb_val_graph_data_to_id(val_graph_data);
      (*(graph_key + 0)) = id_right;
      (*(graph_key + 1)) = id_label;
      val_graph_key.mv_data = graph_key;
      val_id.mv_data = &id_left;
      db_mdb_cursor_get_x(right_to_left, val_graph_key, val_id, MDB_SET_KEY);
      db_mdb_cursor_get_x(right_to_left, val_graph_key, val_id, MDB_GET_BOTH);
      if (db_mdb_status_failure_p) {
        if ((MDB_NOTFOUND == status.id)) {
          db_index_errors_graph_log("entry from left->right not in right->left",
            id_left,
            id_right,
            id_label);
          (*result).errors_p = 1;
          record.left = id_left;
          record.right = id_right;
          record.label = id_label;
          db_graph_records_add_x(
            (*result).missing_right_left, record, records_temp);
        } else {
          status_goto;
        };
      };
      val_id_2.mv_data = &id_label;
      db_mdb_cursor_get_x(label_to_left, val_id_2, val_id, MDB_GET_BOTH);
      if (!db_mdb_status_success_p) {
        if ((MDB_NOTFOUND == status.id)) {
          db_index_errors_graph_log("entry from left->right not in label->left",
            id_left,
            id_right,
            id_label);
          (*result).errors_p = 1;
          record.left = id_left;
          record.right = id_right;
          record.label = id_label;
          db_graph_records_add_x(
            (*result).missing_label_left, record, records_temp);
        } else {
          status_goto;
        };
      };
      db_mdb_cursor_next_dup_x(left_to_right, val_graph_key, val_graph_data);
    } while (db_mdb_status_success_p);
  });
  db_mdb_cursor_each_key(right_to_left, val_graph_key, val_id, {
    id_right = db_mdb_val_to_id_at(val_graph_key, 0);
    id_label = db_mdb_val_to_id_at(val_graph_key, 1);
    do {
      id_left = db_mdb_val_to_id(val_id);
      (*(graph_key + 0)) = id_left;
      (*(graph_key + 1)) = id_label;
      val_graph_key.mv_data = graph_key;
      db_mdb_cursor_get_x(
        left_to_right, val_graph_key, val_graph_data, MDB_SET_KEY);
      if (db_mdb_status_success_p) {
        status = db_mdb_left_to_right_seek_right(left_to_right, id_right);
      };
      if (!db_mdb_status_success_p) {
        if ((MDB_NOTFOUND == status.id)) {
          db_index_errors_graph_log("entry from right->left not in left->right",
            id_left,
            id_right,
            id_label);
          (*result).errors_p = 1;
          record.left = id_left;
          record.right = id_right;
          record.label = id_label;
          db_graph_records_add_x(
            (*result).excess_right_left, record, records_temp);
        } else {
          status_goto;
        };
      };
      db_mdb_cursor_next_dup_x(right_to_left, val_graph_key, val_id);
    } while (db_mdb_status_success_p);
  });
  db_mdb_cursor_each_key(label_to_left, val_id, val_id_2, {
    id_label = db_mdb_val_to_id(val_id);
    do {
      id_left = db_mdb_val_to_id(val_id_2);
      (*(graph_key + 0)) = id_left;
      (*(graph_key + 1)) = id_label;
      val_graph_key.mv_data = graph_key;
      db_mdb_cursor_get_x(
        left_to_right, val_graph_key, val_graph_data, MDB_SET);
      if (!db_mdb_status_success_p) {
        if ((MDB_NOTFOUND == status.id)) {
          db_index_errors_graph_log("entry from label->left not in left->right",
            id_left,
            id_right,
            id_label);
          (*result).errors_p = 1;
          record.left = id_left;
          record.right = 0;
          record.label = id_label;
          db_graph_records_add_x(
            (*result).excess_label_left, record, records_temp);
        } else {
          status_goto;
        };
      };
      db_mdb_cursor_next_dup_x(label_to_left, val_id, val_id_2);
    } while (db_mdb_status_success_p);
  });
  db_status_success_if_mdb_notfound;
exit:
  db_mdb_cursor_close_3(left_to_right, right_to_left, label_to_left);
  return (status);
};
status_t
db_index_errors_intern(db_txn_t* txn, db_index_errors_intern_t* result) {
  status_init;
  (*result) = db_index_errors_intern_null;
  db_mdb_declare_val_id;
  db_mdb_declare_val_data;
  db_mdb_declare_val_data_2;
  db_mdb_cursor_define_2(txn, data_intern_to_id, id_to_data);
  db_ids_t* ids_temp;
  db_mdb_cursor_each_key(data_intern_to_id, val_data, val_id, {
    db_mdb_cursor_get_x(id_to_data, val_id, val_data_2, MDB_SET_KEY);
    if (db_mdb_status_success_p) {
      if (db_mdb_compare_data(&val_data, &val_data_2)) {
        db_index_errors_data_log("intern",
          "data from data-intern->id differs in id->data",
          db_mdb_val_to_id(val_id));
        (*result).errors_p = 1;
        db_ids_add_x(
          (*result).different_data_id, db_mdb_val_to_id(val_id), ids_temp);
      };
    } else {
      if ((MDB_NOTFOUND == status.id)) {
        db_index_errors_data_log("intern",
          "data from data-intern->id not in id->data",
          db_mdb_val_to_id(val_id));
        (*result).errors_p = 1;
        db_ids_add_x(
          (*result).excess_data_id, db_mdb_val_to_id(val_id), ids_temp);
      } else {
        status_goto;
      };
    };
  });
  db_mdb_declare_val_id_2;
  db_mdb_cursor_each_key(id_to_data, val_id, val_data, {
    if (db_intern_p(db_mdb_val_to_id(val_id))) {
      db_mdb_cursor_get_x(data_intern_to_id, val_data, val_id_2, MDB_SET_KEY);
      if (db_mdb_status_success_p) {
        if (!db_id_equal_p(
              db_mdb_val_to_id(val_id), db_mdb_val_to_id(val_id_2))) {
          db_index_errors_data_log("intern",
            "data from id->data differs in data-intern->id",
            db_mdb_val_to_id(val_id));
          (*result).errors_p = 1;
          db_ids_add_x(
            (*result).different_id_data, db_mdb_val_to_id(val_id), ids_temp);
        };
      } else {
        if ((MDB_NOTFOUND == status.id)) {
          db_index_errors_data_log("intern",
            "data from id->data not in data-intern->id",
            db_mdb_val_to_id(val_id_2));
          (*result).errors_p = 1;
          db_ids_add_x(
            (*result).missing_id_data, db_mdb_val_to_id(val_id_2), ids_temp);
        } else {
          status_goto;
        };
      };
    };
  });
  db_status_success_if_mdb_notfound;
exit:
  db_mdb_cursor_close_2(id_to_data, data_intern_to_id);
  return (status);
};
status_t
db_index_errors_extern(db_txn_t* txn, db_index_errors_extern_t* result) {
  status_init;
  (*result) = db_index_errors_extern_null;
  db_mdb_declare_val_id;
  db_mdb_declare_val_data;
  db_mdb_declare_val_data_2;
  db_ids_t* ids_temp;
  db_mdb_cursor_declare_2(id_to_data, data_extern_to_extern);
  db_mdb_cursor_open_2(txn, id_to_data, data_extern_to_extern);
  db_mdb_cursor_each_key(data_extern_to_extern, val_data, val_id, {
    if (val_data.mv_size) {
      db_mdb_cursor_get_x(id_to_data, val_id, val_data_2, MDB_SET_KEY);
      if (db_mdb_status_success_p) {
        if (db_mdb_compare_data(&val_data, &val_data_2)) {
          db_index_errors_data_log("extern",
            "data from data-extern->extern differs in id->data",
            db_mdb_val_to_id(val_id));
          (*result).errors_p = 1;
          db_ids_add_x((*result).different_data_extern,
            db_mdb_val_to_id(val_id),
            ids_temp);
        };
      } else {
        if ((MDB_NOTFOUND == status.id)) {
          db_index_errors_data_log("extern",
            "data from data-extern->extern not in id->data",
            db_mdb_val_to_id(val_id));
          (*result).errors_p = 1;
          db_ids_add_x(
            (*result).excess_data_extern, db_mdb_val_to_id(val_id), ids_temp);
        } else {
          status_goto;
        };
      };
    };
  });
  db_mdb_cursor_each_key(id_to_data, val_id, val_data, {
    if ((db_extern_p(db_mdb_val_to_id(val_id)) && val_data.mv_size)) {
      db_mdb_cursor_get_x(
        data_extern_to_extern, val_data, val_id, MDB_GET_BOTH);
      if ((MDB_NOTFOUND == status.id)) {
        db_index_errors_data_log("extern",
          "data from id->data not in data-extern->extern",
          db_mdb_val_to_id(val_id));
        (*result).errors_p = 1;
        db_ids_add_x(
          (*result).missing_id_data, db_mdb_val_to_id(val_id), ids_temp);
      } else {
        status_goto;
      };
    };
  });
  db_status_success_if_mdb_notfound;
exit:
  db_mdb_cursor_close_2(id_to_data, data_extern_to_extern);
  return (status);
};