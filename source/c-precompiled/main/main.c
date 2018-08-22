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
uint8_t* uint_to_string(uintmax_t a, size_t* result_len) {
  size_t size;
  uint8_t* result;
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
uint8_t* string_join(uint8_t** strings, size_t strings_len, uint8_t* delimiter, size_t* result_len) {
  uint8_t* result;
  uint8_t* result_temp;
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
uint8_t* db_status_group_id_to_name(status_id_t a) {
  char* b;
  if (db_status_group_db == a) {
    b = "sph-db";
  } else if (db_status_group_lmdb == a) {
    b = "lmdb";
  } else if (db_status_group_libc == a) {
    b = "libc";
  } else {
    b = "";
  };
  return (b);
};
/** get the description if available for a status */
uint8_t* db_status_description(status_t a) {
  char* b;
  if (db_status_group_lmdb == a.group) {
    b = mdb_strerror((a.id));
  } else {
    if (db_status_id_success == a.id) {
      b = "success";
    } else if (db_status_id_invalid_argument == a.id) {
      b = "input argument is of wrong type";
    } else if (db_status_id_input_type == a.id) {
      b = "input argument is of wrong type";
    } else if (db_status_id_data_length == a.id) {
      b = "data too large";
    } else if (db_status_id_duplicate == a.id) {
      b = "element already exists";
    } else if (db_status_id_not_implemented == a.id) {
      b = "not implemented";
    } else if (db_status_id_missing_argument_db_root == a.id) {
      b = "missing argument 'db-root'";
    } else if (db_status_id_path_not_accessible_db_root == a.id) {
      b = "root not accessible";
    } else if (db_status_id_memory == a.id) {
      b = "not enough memory or other memory allocation error";
    } else if (db_status_id_max_element_id == a.id) {
      b = "maximum element identifier value has been reached for the type";
    } else if (db_status_id_max_type_id == a.id) {
      b = "maximum type identifier value has been reached";
    } else if (db_status_id_max_type_id_size == a.id) {
      b = "type identifier size is either configured to be greater than 16 bit, which is currently not supported, or is not smaller than node id size";
    } else if (db_status_id_condition_unfulfilled == a.id) {
      b = "condition unfulfilled";
    } else if (db_status_id_notfound == a.id) {
      b = "no more data to read";
    } else if (db_status_id_different_format == a.id) {
      b = "configured format differs from the format the database was created with";
    } else if (db_status_id_index_keysize == a.id) {
      b = "index key to be inserted exceeds mdb maxkeysize";
    } else {
      b = "";
    };
  };
  return (((uint8_t*)(b)));
};
/** get the name if available for a status */
uint8_t* db_status_name(status_t a) {
  char* b;
  if (db_status_group_lmdb == a.group) {
    b = mdb_strerror((a.id));
  } else {
    if (db_status_id_success == a.id) {
      b = "success";
    } else if (db_status_id_invalid_argument == a.id) {
      b = "invalid-argument";
    } else if (db_status_id_input_type == a.id) {
      b = "input-type";
    } else if (db_status_id_data_length == a.id) {
      b = "data-length";
    } else if (db_status_id_duplicate == a.id) {
      b = "duplicate";
    } else if (db_status_id_not_implemented == a.id) {
      b = "not-implemented";
    } else if (db_status_id_missing_argument_db_root == a.id) {
      b = "missing-argument-db-root";
    } else if (db_status_id_path_not_accessible_db_root == a.id) {
      b = "path-not-accessible-db-root";
    } else if (db_status_id_memory == a.id) {
      b = "memory";
    } else if (db_status_id_max_element_id == a.id) {
      b = "max-element-id-reached";
    } else if (db_status_id_max_type_id == a.id) {
      b = "max-type-id-reached";
    } else if (db_status_id_max_type_id_size == a.id) {
      b = "type-id-size-too-big";
    } else if (db_status_id_condition_unfulfilled == a.id) {
      b = "condition-unfulfilled";
    } else if (db_status_id_notfound == a.id) {
      b = "notfound";
    } else if (db_status_id_different_format == a.id) {
      b = "differing-db-format";
    } else if (db_status_id_index_keysize == a.id) {
      b = "index-key-mdb-keysize";
    } else {
      b = "unknown";
    };
  };
  return (((uint8_t*)(b)));
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
status_t db_txn_begin_child(db_txn_t parent_txn, db_txn_t* a) {
  status_declare;
  db_mdb_status_require((mdb_txn_begin((a->env->mdb_env), (parent_txn.mdb_txn), MDB_RDONLY, (&(a->mdb_txn)))));
exit:
  return (status);
};
status_t db_txn_write_begin_child(db_txn_t parent_txn, db_txn_t* a) {
  status_declare;
  db_mdb_status_require((mdb_txn_begin((a->env->mdb_env), (parent_txn.mdb_txn), 0, (&(a->mdb_txn)))));
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
void db_debug_log_id_bits(db_id_t a) {
  db_id_t index;
  printf("%u", (1 & a));
  for (index = 1; (index < (8 * sizeof(db_id_t))); index = (1 + index)) {
    printf("%u", (((((db_id_t)(1)) << index) & a) ? 1 : 0));
  };
  printf("\n");
};
/** display an ids array */
void db_debug_log_ids(db_ids_t a) {
  printf(("ids (%lu):"), (i_array_length(a)));
  while (i_array_in_range(a)) {
    printf(" %lu", (i_array_get(a)));
    i_array_forward(a);
  };
  printf("\n");
};
/** display an ids set */
void db_debug_log_ids_set(imht_set_t a) {
  uint32_t i = 0;
  printf(("id set (%lu):"), (a.size));
  while ((i < a.size)) {
    printf(" %lu", ((a.content)[i]));
    i = (1 + i);
  };
  printf("\n");
};
void db_debug_log_relations(db_relations_t a) {
  db_relation_t b;
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
  status_require((db_statistics(txn, (&stat))));
  printf("btree entry count: system %zu, nodes %zu, graph-lr %zu, graph-rl %zu, graph-ll %zu\n", (stat.system.ms_entries), (stat.nodes.ms_entries), (stat.graph_lr.ms_entries), (stat.graph_rl.ms_entries), (stat.graph_ll.ms_entries));
exit:
  return (status);
};
/** sum the count of all entries in all btrees used by the database */
status_t db_debug_count_all_btree_entries(db_txn_t txn, uint32_t* result) {
  status_declare;
  db_statistics_t stat;
  status_require((db_statistics(txn, (&stat))));
  *result = (stat.system.ms_entries + stat.nodes.ms_entries + stat.graph_lr.ms_entries + stat.graph_rl.ms_entries + stat.graph_ll.ms_entries);
exit:
  return (status);
};
/** size in octets. zero for variable size types */
uint8_t db_field_type_size(uint8_t a) {
  if ((db_field_type_int64 == a) || (db_field_type_uint64 == a) || (db_field_type_string64 == a) || (db_field_type_float64 == a)) {
    return (8);
  } else if ((db_field_type_int32 == a) || (db_field_type_uint32 == a) || (db_field_type_string32 == a) || (db_field_type_float32 == a)) {
    return (4);
  } else if ((db_field_type_int16 == a) || (db_field_type_uint16 == a) || (db_field_type_string16 == a)) {
    return (2);
  } else if ((db_field_type_int8 == a) || (db_field_type_uint8 == a) || (db_field_type_string8 == a)) {
    return (1);
  } else {
    return (0);
  };
};
/** create a virtual node with data of any type equal or smaller in size than db-size-id-element */
db_id_t db_node_virtual_from_any(db_type_id_t type_id, void* data, uint8_t data_size) {
  db_id_t id;
  memcpy((&id), data, data_size);
  return ((db_id_add_type(id, type_id)));
};
status_t db_ids_to_set(db_ids_t a, imht_set_t** result) {
  status_declare;
  db_status_memory_error_if_null((imht_set_create((i_array_length(a)), result)));
  while (i_array_in_range(a)) {
    imht_set_add((*result), (i_array_get(a)));
    i_array_forward(a);
  };
exit:
  return (status);
};
/** read a length prefixed string.
  on success set result to a newly allocated string and data to the next byte after the string */
status_t db_read_name(uint8_t** data_pointer, uint8_t** result) {
  status_declare;
  uint8_t* data;
  db_name_len_t len;
  uint8_t* name;
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
#define db_define_i_array_new(name, type) \
  /** like i-array-allocate-* but returns status-t */ \
  status_t name(size_t length, type* result) { \
    status_declare; \
    if (!i_array_allocate_##type(length, result)) { \
      status.id = db_status_id_memory; \
      status.group = db_status_group_db; \
    }; \
    return (status); \
  }
db_define_i_array_new(db_ids_new, db_ids_t);
db_define_i_array_new(db_nodes_new, db_nodes_t);
db_define_i_array_new(db_relations_new, db_relations_t);
/** copies to a db-ids-t array all ids from a db-nodes-t array. result-ids is allocated by the caller */
void db_nodes_to_ids(db_nodes_t nodes, db_ids_t* result_ids) {
  while (i_array_in_range(nodes)) {
    i_array_add((*result_ids), ((i_array_get(nodes)).id));
    i_array_forward(nodes);
  };
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
  db_calloc(a, 1, (sizeof(db_env_t)));
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
  env->is_open = 0;
  pthread_mutex_destroy((&(env->mutex)));
};
#include "./open.c"
#include "./type.c"
#include "./index.c"
#include "./node.c"
#include "./graph.c"
