#define db_relation_key_equal(a, b) ((a[0] == b[0]) && (a[1] == b[1]))
#define db_relation_data_ordinal_set(relation_data, value) ((db_ordinal_t*)(relation_data))[0] = value
#define db_relation_data_id_set(relation_data, value) ((db_id_t*)((1 + ((db_ordinal_t*)(relation_data)))))[0] = value
#define db_declare_relation_key(name) db_id_t name[2] = { 0, 0 }
#define db_declare_relation_data(name) \
  uint8_t relation_data[(sizeof(db_ordinal_t) + sizeof(db_id_t))]; \
  memset(relation_data, 0, (sizeof(db_ordinal_t) + sizeof(db_id_t)))
#define db_declare_relation(name) db_relation_t name = { 0, 0, 0, 0 }
/** search data until the given id-right has been found */
status_t db_mdb_relation_lr_seek_right(MDB_cursor* relation_lr, db_id_t id_right) {
  status_declare;
  db_mdb_declare_val_relation_key;
  db_mdb_declare_val_relation_data;
  status.id = mdb_cursor_get(relation_lr, (&val_relation_key), (&val_relation_data), MDB_GET_CURRENT);
each_data:
  if (db_mdb_status_is_success) {
    if (id_right == db_relation_data_to_id((val_relation_data.mv_data))) {
      return (status);
    } else {
      status.id = mdb_cursor_get(relation_lr, (&val_relation_key), (&val_relation_data), MDB_NEXT_DUP);
      goto each_data;
    };
  } else {
    db_mdb_status_expect_notfound;
  };
exit:
  return (status);
}
/** check if a relation exists and create it if not */
status_t db_relation_ensure(db_txn_t txn, db_ids_t left, db_ids_t right, db_ids_t label, db_relation_ordinal_generator_t ordinal_generator, void* ordinal_generator_state) {
  status_declare;
  db_mdb_declare_val_id;
  db_mdb_declare_val_id_2;
  db_mdb_declare_val_relation_key;
  db_mdb_declare_val_relation_data;
  db_declare_relation_key(relation_key);
  db_declare_relation_data(relation_data);
  db_mdb_cursor_declare(relation_lr);
  db_mdb_cursor_declare(relation_rl);
  db_mdb_cursor_declare(relation_ll);
  db_id_t id_label;
  db_id_t id_left;
  db_id_t id_right;
  db_ordinal_t ordinal;
  db_mdb_status_require((db_mdb_env_cursor_open(txn, relation_lr)));
  db_mdb_status_require((db_mdb_env_cursor_open(txn, relation_rl)));
  db_mdb_status_require((db_mdb_env_cursor_open(txn, relation_ll)));
  ordinal = ((!ordinal_generator && ordinal_generator_state) ? (ordinal = *((db_ordinal_t*)(ordinal_generator_state))) : 0);
  while (i_array_in_range(left)) {
    id_left = i_array_get(left);
    while (i_array_in_range(label)) {
      id_label = i_array_get(label);
      val_id_2.mv_data = &id_label;
      while (i_array_in_range(right)) {
        id_right = i_array_get(right);
        relation_key[0] = id_right;
        relation_key[1] = id_label;
        val_relation_key.mv_data = relation_key;
        val_id.mv_data = &id_left;
        status.id = mdb_cursor_get(relation_rl, (&val_relation_key), (&val_id), MDB_GET_BOTH);
        if (MDB_NOTFOUND == status.id) {
          db_mdb_status_require((mdb_cursor_put(relation_rl, (&val_relation_key), (&val_id), 0)));
          db_mdb_status_require((mdb_cursor_put(relation_ll, (&val_id_2), (&val_id), 0)));
          relation_key[0] = id_left;
          relation_key[1] = id_label;
          if (ordinal_generator) {
            ordinal = (*ordinal_generator)(ordinal_generator_state);
          };
          db_relation_data_ordinal_set(relation_data, ordinal);
          db_relation_data_id_set(relation_data, id_right);
          val_relation_data.mv_data = relation_data;
          db_mdb_status_require((mdb_cursor_put(relation_lr, (&val_relation_key), (&val_relation_data), 0)));
        } else {
          if (!db_mdb_status_is_success) {
            status.group = db_status_group_lmdb;
            goto exit;
          };
        };
        i_array_forward(right);
      };
      i_array_rewind(right);
      i_array_forward(label);
    };
    i_array_rewind(label);
    i_array_forward(left);
  };
exit:
  db_mdb_cursor_close_if_active(relation_lr);
  db_mdb_cursor_close_if_active(relation_rl);
  db_mdb_cursor_close_if_active(relation_ll);
  return (status);
}
/** rebuild relation-rl and relation-ll based on relation-lr */
status_t db_relation_index_rebuild(db_env_t* env) {
  status_declare;
  db_mdb_declare_val_relation_key;
  db_mdb_declare_val_relation_data;
  db_mdb_declare_val_id;
  db_mdb_declare_val_id_2;
  db_mdb_cursor_declare(relation_lr);
  db_mdb_cursor_declare(relation_rl);
  db_mdb_cursor_declare(relation_ll);
  db_txn_declare(env, txn);
  db_declare_relation_data(relation_data);
  db_declare_relation_key(relation_key);
  db_id_t id_left;
  db_id_t id_right;
  db_id_t id_label;
  status_require((db_txn_write_begin((&txn))));
  db_mdb_status_require((mdb_drop((txn.mdb_txn), (env->dbi_relation_rl), 0)));
  db_mdb_status_require((mdb_drop((txn.mdb_txn), (env->dbi_relation_ll), 0)));
  status_require((db_txn_commit((&txn))));
  status_require((db_txn_write_begin((&txn))));
  db_mdb_status_require((db_mdb_env_cursor_open(txn, relation_lr)));
  db_mdb_status_require((db_mdb_env_cursor_open(txn, relation_rl)));
  db_mdb_status_require((db_mdb_env_cursor_open(txn, relation_ll)));
  db_mdb_cursor_each_key(relation_lr, val_relation_key, val_relation_data, ({id_left=db_pointer_to_id_at((val_relation_key.mv_data),0);id_label=db_pointer_to_id_at((val_relation_key.mv_data),1);do{id_right=db_pointer_to_id((val_relation_data.mv_data));
/* relation-rl */
relation_key[0]=id_right;relation_key[1]=id_label;val_relation_key.mv_data=relation_key;val_id.mv_data=&id_left;db_mdb_status_require((mdb_cursor_put(relation_rl,(&val_relation_key),(&val_id),0)));
/* relation-ll */
val_id_2.mv_data=&id_label;db_mdb_status_require((mdb_cursor_put(relation_ll,(&val_id_2),(&val_id),0)));status.id=mdb_cursor_get(relation_lr,(&val_relation_key),(&val_relation_data),MDB_NEXT_DUP);}while(db_mdb_status_is_success); }));
  status_require((db_txn_commit((&txn))));
exit:
  db_txn_abort_if_active(txn);
  return (status);
}
#include "./relation-read.c"
#include "./relation-delete.c"
