
#include <math.h>
#include "./sph-db.h"
#include "../foreign/sph/helper.c"
#include "../foreign/sph/helper2.c"
#include "../foreign/sph/string.c"
#include "../foreign/sph/filesystem.c"
#include "./sph-db-extra.h"
#include "./lmdb.c"

#define free_and_set_null(a) \
  free(a); \
  a = 0
#define db_error_log(pattern, ...) fprintf(stderr, "%s:%d error: " pattern "\n", __func__, __LINE__, __VA_ARGS__)
#define reduce_count count = (count - 1)
#define stop_if_count_zero \
  if (0 == count) { \
    goto exit; \
  }

/** get the description if available for a status */
uint8_t* db_status_description(status_t a) {
  char* b;
  if (!strcmp(db_status_group_lmdb, (a.group))) {
    b = mdb_strerror((a.id));
  } else if (!strcmp(db_status_group_sph, (a.group))) {
    b = sph_helper_status_description(a);
  } else if (!strcmp(db_status_group_db, (a.group))) {
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
      b = "type identifier size is either configured to be greater than 16 bit, which is currently not supported, or is not smaller than record id size";
    } else if (db_status_id_condition_unfulfilled == a.id) {
      b = "condition unfulfilled";
    } else if (db_status_id_notfound == a.id) {
      b = "entry not found or no more data to read";
    } else if (db_status_id_different_format == a.id) {
      b = "configured format differs from the format the database was created with";
    } else if (db_status_id_index_keysize == a.id) {
      b = "index key to be inserted exceeds mdb maxkeysize";
    } else if (db_status_id_invalid_field_type == a.id) {
      b = "invalid type for field";
    } else if (db_status_id_type_field_order == a.id) {
      b = "all fixed length type fields must come before variable length type fields";
    } else {
      b = "";
    };
  } else {
    if (status_id_success == a.id) {
      b = "success";
    } else {
      b = "";
    };
  };
  return (((uint8_t*)(b)));
}

/** get the name if available for a status */
uint8_t* db_status_name(status_t a) {
  char* b;
  if (!strcmp(db_status_group_lmdb, (a.group))) {
    b = mdb_strerror((a.id));
  } else if (!strcmp(db_status_group_sph, (a.group))) {
    b = sph_helper_status_name(a);
  } else if (!strcmp(db_status_group_db, (a.group))) {
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
    } else if (db_status_id_invalid_field_type == a.id) {
      b = "invalid-field-type";
    } else if (db_status_id_type_field_order == a.id) {
      b = "type-field-order";
    } else {
      b = "unknown";
    };
  } else {
    b = "unknown";
  };
  return (((uint8_t*)(b)));
}
status_t db_txn_begin(db_txn_t* a) {
  status_declare;
  db_mdb_status_require((mdb_txn_begin((a->env->mdb_env), 0, MDB_RDONLY, (&(a->mdb_txn)))));
exit:
  return (status);
}
status_t db_txn_write_begin(db_txn_t* a) {
  status_declare;
  db_mdb_status_require((mdb_txn_begin((a->env->mdb_env), 0, 0, (&(a->mdb_txn)))));
exit:
  return (status);
}
status_t db_txn_begin_child(db_txn_t parent_txn, db_txn_t* a) {
  status_declare;
  db_mdb_status_require((mdb_txn_begin((a->env->mdb_env), (parent_txn.mdb_txn), MDB_RDONLY, (&(a->mdb_txn)))));
exit:
  return (status);
}
status_t db_txn_write_begin_child(db_txn_t parent_txn, db_txn_t* a) {
  status_declare;
  db_mdb_status_require((mdb_txn_begin((a->env->mdb_env), (parent_txn.mdb_txn), 0, (&(a->mdb_txn)))));
exit:
  return (status);
}
void db_txn_abort(db_txn_t* a) {
  mdb_txn_abort((a->mdb_txn));
  a->mdb_txn = 0;
}
status_t db_txn_commit(db_txn_t* a) {
  status_declare;
  db_mdb_status_require((mdb_txn_commit((a->mdb_txn))));
  a->mdb_txn = 0;
exit:
  return (status);
}
void db_debug_log_id_bits(db_id_t a) {
  db_id_t index;
  printf("%u", (1 & a));
  for (index = 1; (index < (8 * sizeof(db_id_t))); index = (1 + index)) {
    printf("%u", (((((db_id_t)(1)) << index) & a) ? 1 : 0));
  };
  printf("\n");
}

/** display an ids array */
void db_debug_log_ids(db_ids_t a) {
  printf(("ids (%lu):"), (i_array_length(a)));
  while (i_array_in_range(a)) {
    printf(" %lu", (i_array_get(a)));
    i_array_forward(a);
  };
  printf("\n");
}

/** display an ids set */
void db_debug_log_ids_set(db_id_set_t a) {
  uint32_t i = 0;
  printf(("id set (%lu):"), (a.size));
  while ((i < a.size)) {
    if ((a.values)[i]) {
      printf(" %lu", ((a.values)[i]));
    };
    i = (1 + i);
  };
  printf("\n");
}
void db_debug_log_relations(db_relations_t a) {
  db_relation_t b;
  printf(("relation records (ll -> or)\n"));
  while (i_array_in_range(a)) {
    b = i_array_get(a);
    printf(("  %lu %lu -> %lu %lu\n"), (b.left), (b.label), (b.ordinal), (b.right));
    i_array_forward(a);
  };
}
status_t db_debug_log_btree_counts(db_txn_t txn) {
  status_declare;
  db_statistics_t stat;
  status_require((db_statistics(txn, (&stat))));
  printf("btree entry count: system %zu, records %zu, relation-lr %zu, relation-rl %zu, relation-ll %zu\n", (stat.system.ms_entries), (stat.records.ms_entries), (stat.relation_lr.ms_entries), (stat.relation_rl.ms_entries), (stat.relation_ll.ms_entries));
exit:
  return (status);
}

/** sum of all entries in all btrees used by the database */
status_t db_debug_count_all_btree_entries(db_txn_t txn, uint32_t* result) {
  status_declare;
  db_statistics_t stat;
  status_require((db_statistics(txn, (&stat))));
  *result = (stat.system.ms_entries + stat.records.ms_entries + stat.relation_lr.ms_entries + stat.relation_rl.ms_entries + stat.relation_ll.ms_entries);
exit:
  return (status);
}

/** size in octets. size of the size prefix for variable size types */
db_field_type_size_t db_field_type_size(db_field_type_t a) {
  if ((db_field_type_binary64f == a) || (db_field_type_uint64f == a) || (db_field_type_int64f == a) || (db_field_type_string64f == a) || (db_field_type_float64f == a) || (db_field_type_binary64 == a) || (db_field_type_string64 == a)) {
    return (8);
  } else if ((db_field_type_binary32f == a) || (db_field_type_uint32f == a) || (db_field_type_int32f == a) || (db_field_type_string32f == a) || (db_field_type_float32f == a) || (db_field_type_binary32 == a) || (db_field_type_string32 == a)) {
    return (4);
  } else if ((db_field_type_binary16f == a) || (db_field_type_uint16f == a) || (db_field_type_int16f == a) || (db_field_type_string16f == a) || (db_field_type_binary16 == a) || (db_field_type_string16 == a)) {
    return (2);
  } else if ((db_field_type_binary8f == a) || (db_field_type_uint8f == a) || (db_field_type_int8f == a) || (db_field_type_string8f == a) || (db_field_type_binary8 == a) || (db_field_type_string8 == a)) {
    return (1);
  } else if ((db_field_type_binary128f == a) || (db_field_type_uint128f == a) || (db_field_type_int128f == a) || (db_field_type_string128f == a)) {
    return (16);
  } else if ((db_field_type_binary256f == a) || (db_field_type_uint256f == a) || (db_field_type_int256f == a) || (db_field_type_string256f == a)) {
    return (32);
  } else {
    return (0);
  };
}
db_id_t db_record_virtual(db_type_id_t type_id, void* data, size_t data_size) {
  db_id_t id;
  id = 0;
  memcpy((&id), data, data_size);
  return ((db_id_add_type(id, type_id)));
}

/** result is allocated and owned by callee */
void* db_record_virtual_data(db_id_t id, void* result, size_t result_size) {
  id = db_id_element(id);
  memcpy(result, (&id), result_size);
  return (result);
}
status_t db_ids_to_set(db_ids_t a, db_id_set_t* result) {
  status_declare;
  db_id_set_t b;
  if (db_id_set_new((db_ids_length(a)), (&b))) {
    status_set_goto(db_status_group_db, db_status_id_memory);
  };
  while (i_array_in_range(a)) {
    db_id_set_add(b, (i_array_get(a)));
    i_array_forward(a);
  };
  *result = b;
exit:
  return (status);
}

/** read a length prefixed string.
  on success set result to a newly allocated, null terminated string and
  data-pointer is positioned at the first byte after the string */
status_t db_read_name(uint8_t** data_pointer, uint8_t** result) {
  status_declare;
  uint8_t* data;
  db_name_len_t len;
  uint8_t* name;
  data = *data_pointer;
  len = *((db_name_len_t*)(data));
  data = (sizeof(db_name_len_t) + data);
  status_require((sph_helper_malloc_string(len, (&name))));
  memcpy(name, data, len);
  *data_pointer = (len + data);
  *result = name;
exit:
  return (status);
}

/** copies to a db-ids-t array all ids from a db-records-t array. result-ids is allocated by the caller */
void db_records_to_ids(db_records_t records, db_ids_t* result_ids) {
  while (i_array_in_range(records)) {
    i_array_add((*result_ids), ((i_array_get(records)).id));
    i_array_forward(records);
  };
}

/** expects an allocated db-statistics-t */
status_t db_statistics(db_txn_t txn, db_statistics_t* result) {
  status_declare;
  db_mdb_status_require((mdb_stat((txn.mdb_txn), ((txn.env)->dbi_system), (&(result->system)))));
  db_mdb_status_require((mdb_stat((txn.mdb_txn), ((txn.env)->dbi_records), (&(result->records)))));
  db_mdb_status_require((mdb_stat((txn.mdb_txn), ((txn.env)->dbi_relation_lr), (&(result->relation_lr)))));
  db_mdb_status_require((mdb_stat((txn.mdb_txn), ((txn.env)->dbi_relation_ll), (&(result->relation_ll)))));
  db_mdb_status_require((mdb_stat((txn.mdb_txn), ((txn.env)->dbi_relation_rl), (&(result->relation_rl)))));
exit:
  return (status);
}

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
    status_set_goto(db_status_group_db, db_status_id_max_type_id);
  };
exit:
  return (status);
}

/** return one new unique type record identifier.
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
    status_set_goto(db_status_group_db, db_status_id_max_element_id);
  };
exit:
  return (status);
}
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
}
void db_free_env_types_fields(db_field_t** fields, db_fields_len_t fields_len) {
  db_fields_len_t i;
  if (!*fields) {
    return;
  };
  for (i = 0; (i < fields_len); i = (1 + i)) {
    free_and_set_null(((i + *fields)->name));
  };
  free_and_set_null((*fields));
}
void db_free_env_type(db_type_t* type) {
  if (!type->id) {
    return;
  };
  free_and_set_null((type->fields_fixed_offsets));
  db_free_env_types_fields((&(type->fields)), (type->fields_len));
  db_free_env_types_indices((&(type->indices)), (type->indices_len));
  type->id = 0;
}
void db_free_env_types(db_type_t** types, db_type_id_t types_len) {
  db_type_id_t i;
  if (!*types) {
    return;
  };
  for (i = 0; (i < types_len); i = (1 + i)) {
    db_free_env_type((i + *types));
  };
  free_and_set_null((*types));
}

/** caller has to free result when not needed anymore.
  this routine makes sure that .is-open is zero */
status_t db_env_new(db_env_t** result) {
  status_declare;
  db_env_t* a;
  status_require((sph_helper_calloc((sizeof(db_env_t)), ((void**)(&a)))));
  *result = a;
exit:
  return (status);
}
void db_close(db_env_t* env) {
  MDB_env* mdb_env = env->mdb_env;
  if (mdb_env) {
    mdb_dbi_close(mdb_env, (env->dbi_system));
    mdb_dbi_close(mdb_env, (env->dbi_records));
    mdb_dbi_close(mdb_env, (env->dbi_relation_lr));
    mdb_dbi_close(mdb_env, (env->dbi_relation_rl));
    mdb_dbi_close(mdb_env, (env->dbi_relation_ll));
    mdb_env_close(mdb_env);
    env->mdb_env = 0;
  };
  db_free_env_types((&(env->types)), (env->types_len));
  if (env->root) {
    free_and_set_null((env->root));
  };
  env->is_open = 0;
  pthread_mutex_destroy((&(env->mutex)));
}
#include "./open.c"
#include "./type.c"
#include "./index.c"
#include "./record.c"
#include "./relation.c"
