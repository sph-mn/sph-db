#include "./sph-db.h"
#include "./sph-db-extra.h"
#include "../foreign/sph/one.c"
#include <math.h>
#include "./lib/lmdb.c"
#define free_and_set_null(a) \
  free(a); \
  a = 0
#define db_error_log(pattern, ...) fprintf(stderr, "%s:%d error: " pattern "\n", __func__, __LINE__, __VA_ARGS__)
#define reduce_count count = (count - 1)
#define stop_if_count_zero \
  if (0 == count) { \
    goto exit; \
  }
#define db_size_system_key (1 + sizeof(db_type_id_t))
ui8* uint_to_string(uintmax_t a, size_t* result_len) {
  size_t size;
  ui8* result;
  size = (1 + ((0 == a) ? 1 : (1 + log10(a))));
  result = malloc(size);
  if (!result) {
    return (0);
  };
  if (snprintf(result, size, "%ju", a) < 0) {
    free(result);
    return (0);
  } else {
    *result_len = (size - 1);
    return (result);
  };
};
/** join strings into one string with each input string separated by delimiter.
  zero if strings-len is zero or memory could not be allocated */
ui8* string_join(ui8** strings, size_t strings_len, ui8* delimiter, size_t* result_len) {
  ui8* result;
  ui8* result_temp;
  size_t size;
  size_t size_temp;
  size_t i;
  size_t delimiter_len;
  if (!strings_len) {
    return (0);
  };
  /* size: string-null + delimiters + string-lengths */
  delimiter_len = strlen(delimiter);
  size = (1 + (delimiter_len * (strings_len - 1)));
  for (i = 0; (i < strings_len); i = (1 + i)) {
    size = (size + strlen((strings[i])));
  };
  result = malloc(size);
  if (!result) {
    return (0);
  };
  result_temp = result;
  size_temp = strlen((strings[0]));
  memcpy(result_temp, (strings[0]), size_temp);
  result_temp = (size_temp + result_temp);
  for (i = 1; (i < strings_len); i = (1 + i)) {
    memcpy(result_temp, delimiter, delimiter_len);
    result_temp = (delimiter_len + result_temp);
    size_temp = strlen((strings[i]));
    memcpy(result_temp, (strings[i]), size_temp);
    result_temp = (size_temp + result_temp);
  };
  result[(size - 1)] = 0;
  *result_len = (size - 1);
  return (result);
};
status_t db_txn_begin(db_txn_t* a) {
  status_declare;
  db_mdb_status_require((mdb_txn_begin((a->env->mdb_env), 0, MDB_RDONLY, (&(a->mdb_txn)))));
exit:
  return (status);
};
status_t db_txn_write_begin(db_txn_t* a) {
  status_declare;
  db_mdb_status_require((mdb_txn_begin((a->env->mdb_env), 0, 0, (&(a->mdb_txn)))));
exit:
  return (status);
};
void db_txn_abort(db_txn_t* a) {
  mdb_txn_abort((a->mdb_txn));
  a->mdb_txn = 0;
};
status_t db_txn_commit(db_txn_t* a) {
  status_declare;
  db_mdb_status_require((mdb_txn_commit((a->mdb_txn))));
  a->mdb_txn = 0;
exit:
  return (status);
};
/** display an ids array */
void db_debug_log_ids(db_ids_t a) {
  printf("ids (%lu):", i_array_length(a));
  while (i_array_in_range(a)) {
    printf(" %lu", i_array_get(a));
    i_array_forward(a);
  };
  printf("\n");
};
/** display an ids set */
void db_debug_log_ids_set(imht_set_t a) {
  ui32 i = 0;
  printf("id set (%lu):", (a.size));
  while ((i < a.size)) {
    printf(" %lu", ((a.content)[i]));
    i = (1 + i);
  };
  printf("\n");
};
void db_debug_log_graph_records(db_graph_records_t a) {
  db_graph_record_t b;
  printf(("graph records (ll -> or)\n"));
  while (i_array_in_range(a)) {
    b = i_array_get(a);
    printf(("  %lu %lu -> %lu %lu\n"), (b.left), (b.label), (b.ordinal), (b.right));
    i_array_forward(a);
  };
};
status_t db_debug_log_btree_counts(db_txn_t txn) {
  status_declare;
  db_statistics_t stat;
  status_require(db_statistics(txn, (&stat)));
  printf("btree entry count: system %zu, nodes %zu, graph-lr %zu, graph-rl %zu, graph-ll %zu\n", (stat.system.ms_entries), (stat.nodes.ms_entries), (stat.graph_lr.ms_entries), (stat.graph_rl.ms_entries), (stat.graph_ll.ms_entries));
exit:
  return (status);
};
/** sum the count of all entries in all btrees used by the database */
status_t db_debug_count_all_btree_entries(db_txn_t txn, ui32* result) {
  status_declare;
  db_statistics_t stat;
  status_require(db_statistics(txn, (&stat)));
  *result = (stat.system.ms_entries + stat.nodes.ms_entries + stat.graph_lr.ms_entries + stat.graph_rl.ms_entries + stat.graph_ll.ms_entries);
exit:
  return (status);
};
/** size in octets. zero for variable size types */
ui8 db_field_type_size(ui8 a) {
  if ((db_field_type_int64 == a) || (db_field_type_uint64 == a) || (db_field_type_char64 == a) || (db_field_type_float64 == a)) {
    return (8);
  } else if ((db_field_type_int32 == a) || (db_field_type_uint32 == a) || (db_field_type_char32 == a) || (db_field_type_float32 == a)) {
    return (4);
  } else if ((db_field_type_int16 == a) || (db_field_type_uint16 == a) || (db_field_type_char16 == a)) {
    return (2);
  } else if ((db_field_type_int8 == a) || (db_field_type_uint8 == a) || (db_field_type_char8 == a)) {
    return (1);
  } else {
    return (0);
  };
};
status_t db_ids_to_set(db_ids_t a, imht_set_t** result) {
  status_declare;
  db_status_memory_error_if_null(imht_set_create(i_array_length(a), result));
  while (i_array_in_range(a)) {
    imht_set_add((*result), i_array_get(a));
    i_array_forward(a);
  };
exit:
  return (status);
};
/** read a length prefixed string from system type data.
  on success set result to a newly allocated string and data to the next byte after the string */
status_t db_read_name(ui8** data_pointer, ui8** result) {
  status_declare;
  ui8* data;
  db_name_len_t len;
  ui8* name;
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
  status_declare;
  db_mdb_status_require((mdb_stat((txn.mdb_txn), ((txn.env)->dbi_system), (&(result->system)))));
  db_mdb_status_require((mdb_stat((txn.mdb_txn), ((txn.env)->dbi_nodes), (&(result->nodes)))));
  db_mdb_status_require((mdb_stat((txn.mdb_txn), ((txn.env)->dbi_graph_lr), (&(result->graph_lr)))));
  db_mdb_status_require((mdb_stat((txn.mdb_txn), ((txn.env)->dbi_graph_ll), (&(result->graph_ll)))));
  db_mdb_status_require((mdb_stat((txn.mdb_txn), ((txn.env)->dbi_graph_rl), (&(result->graph_rl)))));
exit:
  return (status);
};
/** return one new unique type identifier.
  the maximum identifier returned is db-type-id-limit minus one */
status_t db_sequence_next_system(db_env_t* env, db_type_id_t* result) {
  status_declare;
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
status_t db_sequence_next(db_env_t* env, db_type_id_t type_id, db_id_t* result) {
  status_declare;
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
void db_free_env_types_indices(db_index_t** indices, db_fields_len_t indices_len) {
  db_fields_len_t i;
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
void db_free_env_types_fields(db_field_t** fields, db_fields_len_t fields_len) {
  db_fields_len_t i;
  if (!*fields) {
    return;
  };
  for (i = 0; (i < fields_len); i = (1 + i)) {
    free_and_set_null(((i + *fields)->name));
  };
  free_and_set_null((*fields));
};
void db_free_env_type(db_type_t* type) {
  if (0 == type->id) {
    return;
  };
  free_and_set_null((type->fields_fixed_offsets));
  db_free_env_types_fields((&(type->fields)), (type->fields_len));
  db_free_env_types_indices((&(type->indices)), (type->indices_len));
  type->id = 0;
};
void db_free_env_types(db_type_t** types, db_type_id_t types_len) {
  db_type_id_t i;
  if (!*types) {
    return;
  };
  for (i = 0; (i < types_len); i = (1 + i)) {
    db_free_env_type((i + *types));
  };
  free_and_set_null((*types));
};
/** caller has to free result when not needed anymore.
  this routine makes sure that .is-open is zero */
status_t db_env_new(db_env_t** result) {
  status_declare;
  db_env_t* a;
  db_calloc(a, 1, sizeof(db_env_t));
  *result = a;
exit:
  return (status);
};
void db_close(db_env_t* env) {
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
#include "./type.c"
#include "./index.c"
#include "./node.c"
#include "./graph.c"
