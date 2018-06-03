#include "./sph-db.h"
#include "../foreign/sph/one.c"
#include "./lib/lmdb.c"
#define free_and_set_null(a) \
  free(a); \
  a = 0
#define db_error_log(pattern, ...) \
  fprintf(stderr, "%s:%d error: " pattern "\n", __func__, __LINE__, __VA_ARGS__)
#define reduce_count count = (count - 1)
#define stop_if_count_zero \
  if (0 == count) { \
    goto exit; \
  }
#define optional_count(count) ((0 == count) ? UINT32_MAX : count)
#define db_cursor_declare(name) MDB_cursor* name = 0
#define db_cursor_open(txn, name) \
  db_mdb_status_require_x( \
    (mdb_cursor_open((txn.mdb_txn), ((txn.env)->dbi_##name), (&name))))
#define db_cursor_close(name) \
  mdb_cursor_close(name); \
  name = 0
#define db_cursor_close_if_active(name) \
  if (name) { \
    db_cursor_close(name); \
  }
#define db_size_system_key (1 + sizeof(db_type_id_t))
#define db_select_ensure_offset(state, offset, reader) \
  if (offset) { \
    state->options = (db_read_option_skip | state->options); \
    status = reader(state, offset, 0); \
    if (!db_mdb_status_success_p) { \
      db_mdb_status_require_notfound; \
    }; \
    state->options = (db_read_option_skip ^ state->options); \
  }
/** display an ids list */
b0 db_debug_log_ids(db_ids_t* a) {
  debug_log("length: %lu", db_ids_length(a));
  while (a) {
    debug_log("%lu", db_ids_first(a));
    a = db_ids_rest(a);
  };
};
/** display an ids set */
b0 db_debug_log_ids_set(imht_set_t a) {
  b32 index = 0;
  while ((index < a.size)) {
    debug_log("%lu", ((a.content)[index]));
    index = (1 + index);
  };
};
b0 db_debug_display_graph_records(db_graph_records_t* records) {
  db_graph_record_t record;
  printf("graph records\n");
  while (records) {
    record = db_graph_records_first(records);
    printf("  lcor %lu %lu %lu %lu\n",
      (record.left),
      (record.label),
      (record.ordinal),
      (record.right));
    records = db_graph_records_rest(records);
  };
};
status_t db_debug_display_btree_counts(db_txn_t txn) {
  status_init;
  db_statistics_t stat;
  status_require_x(db_statistics(txn, (&stat)));
  printf("btree entry count: system %zu, nodes %zu, graph-lr %zu, graph-rl "
         "%zu, graph-ll %zu\n",
    (stat.system.ms_entries),
    (stat.nodes.ms_entries),
    (stat.graph_lr.ms_entries),
    (stat.graph_rl.ms_entries),
    (stat.graph_ll.ms_entries));
exit:
  return (status);
};
status_t db_debug_count_all_btree_entries(db_txn_t txn, b32* result) {
  status_init;
  db_statistics_t stat;
  status_require_x(db_statistics(txn, (&stat)));
  *result =
    (stat.system.ms_entries + stat.nodes.ms_entries + stat.graph_lr.ms_entries +
      stat.graph_rl.ms_entries + stat.graph_ll.ms_entries);
exit:
  return (status);
};
/** size in octets. only for fixed size types */
b8 db_field_type_size(b8 a) {
  if ((db_field_type_int64 == a) || (db_field_type_uint64 == a) ||
    (db_field_type_char64 == a) || (db_field_type_float64 == a)) {
    return (64);
  } else if ((db_field_type_int32 == a) || (db_field_type_uint32 == a) ||
    (db_field_type_char32 == a) || (db_field_type_float32 == a)) {
    return (32);
  } else if ((db_field_type_int16 == a) || (db_field_type_uint16 == a) ||
    (db_field_type_char16 == a)) {
    return (16);
  } else if ((db_field_type_int8 == a) || (db_field_type_uint8 == a) ||
    (db_field_type_char8 == a)) {
    return (8);
  } else {
    return (0);
  };
};
status_t db_ids_to_set(db_ids_t* a, imht_set_t** result) {
  status_init;
  if (!imht_set_create(db_ids_length(a), result)) {
    db_status_set_id_goto(db_status_id_memory);
  };
  while (a) {
    imht_set_add((*result), db_ids_first(a));
    a = db_ids_rest(a);
  };
exit:
  return (status);
};
/** read a length prefixed string from system type data.
  on success set result to a newly allocated string and data to the next byte
  after the string */
status_t db_read_name(b8** data_pointer, b8** result) {
  status_init;
  b8* data;
  db_name_len_t len;
  b8* name;
  data = *data_pointer;
  len = *((db_name_len_t*)(data));
  data = (sizeof(db_name_len_t) + data);
  db_malloc_string(name, len);
  memcpy(name, data, len);
exit:
  *data_pointer = (len + data);
  *result = name;
  return (status);
};
/** expects an allocated db-statistics-t */
status_t db_statistics(db_txn_t txn, db_statistics_t* result) {
  status_init;
  db_mdb_status_require_x(
    (mdb_stat((txn.mdb_txn), ((txn.env)->dbi_system), (&(result->system)))));
  db_mdb_status_require_x(
    (mdb_stat((txn.mdb_txn), ((txn.env)->dbi_nodes), (&(result->nodes)))));
  db_mdb_status_require_x((
    mdb_stat((txn.mdb_txn), ((txn.env)->dbi_graph_lr), (&(result->graph_lr)))));
  db_mdb_status_require_x((
    mdb_stat((txn.mdb_txn), ((txn.env)->dbi_graph_ll), (&(result->graph_ll)))));
  db_mdb_status_require_x((
    mdb_stat((txn.mdb_txn), ((txn.env)->dbi_graph_rl), (&(result->graph_rl)))));
exit:
  return (status);
};
/** return one new unique type identifier.
  the maximum identifier returned is db-type-id-limit minus one */
status_t db_sequence_next_system(db_env_t* env, db_type_id_t* result) {
  status_init;
  db_type_id_t sequence;
  pthread_mutex_lock((&(env->mutex)));
  sequence = ((db_type_id_t)(env->types->sequence));
  if (db_type_id_limit > sequence) {
    env->types->sequence = (1 + sequence);
    pthread_mutex_unlock((&(env->mutex)));
    *result = sequence;
  } else {
    pthread_mutex_unlock((&(env->mutex)));
    status_set_both_goto(db_status_group_db, db_status_id_max_type_id);
  };
exit:
  return (status);
};
/** return one new unique type node identifier.
  the maximum identifier returned is db-id-limit minus one */
status_t
db_sequence_next(db_env_t* env, db_type_id_t type_id, db_id_t* result) {
  status_init;
  db_id_t sequence;
  pthread_mutex_lock((&(env->mutex)));
  sequence = (type_id + env->types)->sequence;
  if (db_element_id_limit > sequence) {
    (type_id + env->types)->sequence = (1 + sequence);
    pthread_mutex_unlock((&(env->mutex)));
    *result = db_id_add_type(sequence, type_id);
  } else {
    pthread_mutex_unlock((&(env->mutex)));
    status_set_both_goto(db_status_group_db, db_status_id_max_element_id);
  };
exit:
  return (status);
};
b0 db_free_env_types_indices(db_index_t** indices,
  db_field_count_t indices_len) {
  db_field_count_t i;
  db_index_t* index_pointer;
  if (!*indices) {
    return;
  };
  for (i = 0; (i < indices_len); i = (1 + i)) {
    index_pointer = (i + *indices);
    free_and_set_null((index_pointer->fields));
  };
  free_and_set_null((*indices));
};
b0 db_free_env_types_fields(db_field_t** fields, db_field_count_t fields_len) {
  db_field_count_t i;
  if (!*fields) {
    return;
  };
  for (i = 0; (i < fields_len); i = (1 + i)) {
    free_and_set_null(((i + *fields)->name));
  };
  free_and_set_null((*fields));
};
b0 db_free_env_type(db_type_t* type) {
  if (0 == type->id) {
    return;
  };
  free_and_set_null((type->fields_fixed_offsets));
  db_free_env_types_fields((&(type->fields)), (type->fields_count));
  db_free_env_types_indices((&(type->indices)), (type->indices_count));
  type->id = 0;
};
b0 db_free_env_types(db_type_t** types, db_type_id_t types_len) {
  db_type_id_t i;
  if (!*types) {
    return;
  };
  for (i = 0; (i < types_len); i = (1 + i)) {
    db_free_env_type((i + *types));
  };
  free_and_set_null((*types));
};
b0 db_close(db_env_t* env) {
  MDB_env* mdb_env = env->mdb_env;
  if (mdb_env) {
    mdb_dbi_close(mdb_env, (env->dbi_system));
    mdb_dbi_close(mdb_env, (env->dbi_nodes));
    mdb_dbi_close(mdb_env, (env->dbi_graph_lr));
    mdb_dbi_close(mdb_env, (env->dbi_graph_rl));
    mdb_dbi_close(mdb_env, (env->dbi_graph_ll));
    mdb_env_close(mdb_env);
    env->mdb_env = 0;
  };
  db_free_env_types((&(env->types)), (env->types_len));
  if (env->root) {
    free_and_set_null((env->root));
  };
  env->open = 0;
  pthread_mutex_destroy((&(env->mutex)));
};
#include "./open.c"
#include "./node.c"
#include "./graph.c"
