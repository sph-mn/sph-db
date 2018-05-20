#include "./sph-db.h"
#include "../foreign/sph/one.c"
#include "./lib/lmdb.c"
#include "./lib/debug.c"
#define db_error_log(pattern, ...) \
  fprintf(stderr, "%s:%d error: " pattern "\n", __func__, __LINE__, __VA_ARGS__)
#define reduce_count count = (count - 1)
#define stop_if_count_zero \
  if ((0 == count)) { \
    goto exit; \
  }
#define optional_count(count) ((0 == count) ? UINT32_MAX : count)
#define db_cursor_open(txn, name) \
  db_mdb_cursor_open(txn.mdb_txn, (*txn.env).dbi_##name, name)
#define db_cursor_declare(name) db_mdb_cursor_declare(name)
#define db_field_type_float32 4
#define db_field_type_float64 6
#define db_field_type_vbinary 1
#define db_field_type_vstring 3
#define db_system_label_format 0
#define db_system_label_type 1
#define db_system_label_index 2
#define db_size_system_key (1 + sizeof(db_type_id_t))
#define db_field_type_fixed_p(a) !(1 & a)
#define db_system_key_label(a) (*((b8*)(a)))
#define db_system_key_id(a) (*((db_type_id_t*)((1 + ((b8*)(a))))))
#define db_field_type_integer_p(a) !(15 & a)
#define db_field_type_string_p(a) (2 == (15 & a))
#define db_id_set_type(id, type_id) db_type_id(id) = type_id
/** 3b:size-exponent 1b:signed 4b:id-prefix:0000
    size-bit-count: 2 ** size-exponent + 3 = 8
    example (size-exponent 4): 10000000, 10010000 */
#define db_field_type_integer(signed, size_exponent) \
  ((size_exponent << 5) & (signed ? 16 : 0))
/** 4b:size-exponent 4b:id-prefix:0010 */
#define db_field_type_string(size_exponent) ((size_exponent << 4) & 2)
#define db_status_memory_error_if_null(variable) \
  if (!variable) { \
    status_set_both_goto(db_status_group_db, db_status_id_memory); \
  }
#define db_malloc(variable, size) \
  variable = malloc(size); \
  db_status_memory_error_if_null(variable)
/** allocate memory and set the last element to zero */
#define db_malloc_string(variable, size) \
  db_malloc(variable, size); \
  (*((size - 1) + variable)) = 0
#define db_calloc(variable, count, size) \
  variable = calloc(count, size); \
  db_status_memory_error_if_null(variable)
#define db_realloc(variable, variable_temp, size) \
  variable_temp = realloc(variable, size); \
  db_status_memory_error_if_null(variable_temp); \
  variable = variable_temp
#define db_select_ensure_offset(state, offset, reader) \
  if (offset) { \
    (*state).options = (db_read_option_skip | (*state).options); \
    status = reader(state, offset, 0); \
    if (!db_mdb_status_success_p) { \
      db_mdb_status_require_notfound; \
    }; \
    (*state).options = (db_read_option_skip ^ (*state).options); \
  }
#define free_and_set_null(a) \
  free(a); \
  a = 0
/** size in octets. only for fixed size types */
b8 db_field_type_size(b8 a) {
  return (((db_field_type_float32 == a)
      ? 4
      : ((db_field_type_float64 == a)
            ? 8
            : (db_field_type_integer_p(a)
                  ? (a >> 5)
                  : (db_field_type_string_p(a) ? (a >> 4) : 0)))));
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
/** expects an allocated db-statistics-t */
status_t db_statistics(db_txn_t txn, db_statistics_t* result) {
  status_init;
#define result_set(dbi_name) \
  db_mdb_status_require_x( \
    mdb_stat(txn.mdb_txn, (*txn.env).dbi_##dbi_name, &(*result).dbi_name))
  result_set(system);
  result_set(id_to_data);
  result_set(left_to_right);
  result_set(right_to_left);
  result_set(label_to_left);
#undef result_set
exit:
  return (status);
};
/** read a length prefixed string from system type data.
  on success set result to a newly allocated string and data to the next byte
  after the string */
status_t db_read_length_prefixed_string_b8(b8** data_pointer, b8** result) {
  status_init;
  b8* data = (*data_pointer);
  b8 len = (*data);
  b8* name = malloc((1 + len));
  if (!name) {
    status_set_both_goto(db_status_group_db, db_status_id_memory);
  };
  (*(len + name)) = 0;
  memcpy(name, (1 + data), len);
exit:
  (*result) = name;
  (*data_pointer) = (len + data);
  return (status);
};
/** return one new, unique and typed identifier */
status_t db_sequence_next(db_env_t* env, db_type_t* type, db_id_t* result) {
  status_init;
  pthread_mutex_t mutex = (*env).mutex;
  db_id_t sequence = (*type).sequence;
  pthread_mutex_lock(&mutex);
  if ((sequence < db_id_id_max)) {
    (*result) = db_id_set_type(sequence, (*type).id);
    (*type).sequence = (1 + sequence);
    pthread_mutex_unlock(&mutex);
  } else {
    status_set_both_goto(db_status_group_db, db_status_id_max_id);
  };
exit:
  return (status);
};
b0 db_free_env_types_indices(db_index_t** indices,
  db_field_count_t indices_len) {
  db_field_count_t index;
  db_index_t* index_pointer;
  if (!(*indices)) {
    return;
  };
  index = 0;
  while ((index < indices_len)) {
    index_pointer = (index + (*indices));
    free_and_set_null((*index_pointer).fields);
    index = (1 + index);
  };
  free_and_set_null((*indices));
};
b0 db_free_env_types_fields(db_field_t** fields, db_field_count_t fields_len) {
  db_field_count_t index;
  if (!(*fields)) {
    return;
  };
  index = 0;
  while ((index < fields_len)) {
    free_and_set_null((*(index + (*fields))).name);
    index = (1 + index);
  };
  free_and_set_null((*fields));
};
b0 db_free_env_types(db_type_t** types, db_type_id_t types_len) {
  db_type_id_t index;
  db_type_t* type;
  if (!(*types)) {
    return;
  };
  index = 0;
  while ((index < types_len)) {
    type = (index + (*types));
    if ((0 == (*type).id)) {
      free_and_set_null((*type).fields_fixed_offsets);
      db_free_env_types_fields(&(*type).fields, (*type).fields_count);
      db_free_env_types_indices(&(*type).indices, (*type).indices_count);
    };
    index = (1 + index);
  };
  free_and_set_null((*types));
};
b0 db_close(db_env_t* env) {
  MDB_env* mdb_env = (*env).mdb_env;
  if (mdb_env) {
    mdb_dbi_close(mdb_env, (*env).dbi_system);
    mdb_dbi_close(mdb_env, (*env).dbi_id_to_data);
    mdb_dbi_close(mdb_env, (*env).dbi_left_to_right);
    mdb_dbi_close(mdb_env, (*env).dbi_right_to_left);
    mdb_dbi_close(mdb_env, (*env).dbi_label_to_left);
    mdb_env_close(mdb_env);
    (*env).mdb_env = 0;
  };
  db_free_env_types(&(*env).types, (*env).types_len);
  if ((*env).root) {
    free_and_set_null((*env).root);
  };
  (*env).open = 0;
  pthread_mutex_destroy(&(*env).mutex);
};
#include "./open.c"
#include "./node.c"
