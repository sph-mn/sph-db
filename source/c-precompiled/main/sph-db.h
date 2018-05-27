/* this file is for declarations and macros needed to use sph-db as a shared
 * library */
#include <math.h>
#include <pthread.h>
#include <lmdb.h>
#include <inttypes.h>
#include <stdio.h>
#define boolean b8
#define pointer_t uintptr_t
#define b0 void
#define b8 uint8_t
#define b16 uint16_t
#define b32 uint32_t
#define b64 uint64_t
#define b8_s int8_t
#define b16_s int16_t
#define b32_s int32_t
#define b64_s int64_t
#define f32_s float
#define f64_s double
/** writes values with current routine name and line info to standard output.
    example: (debug-log "%d" 1)
    otherwise like printf */
#define debug_log(format, ...) \
  fprintf(stdout, "%s:%d " format "\n", __func__, __LINE__, __VA_ARGS__)
#define null ((b0)(0))
#define zero_p(a) (0 == a)
/* return status and error handling */
/* return status code and error handling. uses a local variable named "status"
   and a goto label named "exit". a status has an identifier and a group to
   discern between status identifiers of different libraries. status id 0 is
   success, everything else can be considered a failure or special case. status
   ids are 32 bit signed integers for compatibility with error return codes from
   many other existing libraries. bindings with a ! suffix update the status
   from an expression */
/** like status init but sets a default group */
#define status_init_group(group) status_t status = { status_id_success, group }
#define status_id_success 0
#define status_group_undefined 0
#define status_init \
  status_t status = { status_id_success, status_group_undefined }
#define status_reset status_set_both(status_group_undefined, status_id_success)
#define status_success_p (status_id_success == status.id)
#define status_failure_p !status_success_p
#define status_goto goto exit
#define status_require \
  if (status_failure_p) { \
    status_goto; \
  }
#define status_set_group(group_id) status.group = group_id
#define status_set_id(status_id) status.id = status_id
#define status_set_both(group_id, status_id) \
  status_set_group(group_id); \
  status_set_id(status_id)
/** update status with the result of expression, check for failure and goto
 * error if so */
#define status_require_x(expression) \
  status = expression; \
  if (status_failure_p) { \
    status_goto; \
  }
/** set the status id and goto error */
#define status_set_id_goto(status_id) \
  status_set_id(status_id); \
  status_goto
#define status_set_group_goto(group_id) \
  status_set_group(group_id); \
  status_goto
#define status_set_both_goto(group_id, status_id) \
  status_set_both(group_id, status_id); \
  status_goto
#define status_id_is_p(status_id) (status_id == status.id)
/** update status with the result of expression, check for failure and goto
 * error if so */
#define status_i_require_x(expression) \
  status.id = expression; \
  if (status_failure_p) { \
    status_goto; \
  }
;
typedef b32_s status_i_t;
typedef struct {
  status_i_t id;
  b8 group;
} status_t;
enum {
  db_status_id_condition_unfulfilled,
  db_status_id_data_length,
  db_status_id_different_format,
  db_status_id_duplicate,
  db_status_id_input_type,
  db_status_id_max_element_id,
  db_status_id_max_type_id,
  db_status_id_max_type_id_size,
  db_status_id_memory,
  db_status_id_missing_argument_db_root,
  db_status_id_no_more_data,
  db_status_id_not_implemented,
  db_status_id_path_not_accessible_db_root,
  db_status_id_undefined,
  db_status_group_db,
  db_status_group_lmdb,
  db_status_group_libc
};
#define db_status_set_id_goto(status_id) \
  status_set_both_goto(db_status_group_db, status_id)
#define db_status_require_read_x(expression) \
  status = expression; \
  if (!(status_success_p || status_id_is_p(db_status_id_no_more_data))) { \
    status_goto; \
  }
#define db_status_no_more_data_if_mdb_notfound \
  if (db_mdb_status_notfound_p) { \
    status_set_both(db_status_group_db, db_status_id_no_more_data); \
  }
#define db_status_success_if_mdb_notfound \
  if (db_mdb_status_notfound_p) { \
    status_set_id(status_id_success); \
  }
#define db_status_success_if_no_more_data \
  if (status_id_is_p(db_status_id_no_more_data)) { \
    status.id = status_id_success; \
  }
#define db_mdb_status_success_p status_id_is_p(MDB_SUCCESS)
#define db_mdb_status_failure_p !db_mdb_status_success_p
#define db_mdb_status_notfound_p status_id_is_p(MDB_NOTFOUND)
#define db_mdb_status_set_id_goto(id) \
  status_set_both_goto(db_status_group_lmdb, id)
#define db_mdb_status_require_x(expression) \
  status_set_id(expression); \
  if (db_mdb_status_failure_p) { \
    status_set_group_goto(db_status_group_lmdb); \
  }
#define db_mdb_status_require \
  if (db_mdb_status_failure_p) { \
    status_set_group_goto(db_status_group_lmdb); \
  }
#define db_mdb_status_require_read \
  if (!(db_mdb_status_success_p || db_mdb_status_notfound_p)) { \
    status_set_group_goto(db_status_group_lmdb); \
  }
#define db_mdb_status_require_read_x(expression) \
  status_set_id(expression); \
  db_mdb_status_require_read
#define db_mdb_status_require_notfound \
  if (!db_mdb_status_notfound_p) { \
    status_set_group_goto(db_status_group_lmdb); \
  }
b8* db_status_group_id_to_name(status_i_t a) {
  char* b;
  if ((db_status_group_db == a)) {
    b = "sph-db";
  } else if ((db_status_group_lmdb == a)) {
    b = "lmdb";
  } else if ((db_status_group_libc == a)) {
    b = "libc";
  } else {
    b = "";
  };
  return (b);
};
/** get the description if available for a status */
b8* db_status_description(status_t a) {
  char* b;
  if ((db_status_group_db == a.group)) {
    if ((db_status_id_input_type == a.id)) {
      b = "input argument is of wrong type";
    } else if ((db_status_id_data_length == a.id)) {
      b = "data too large";
    } else if ((db_status_id_duplicate == a.id)) {
      b = "element already exists";
    } else if ((db_status_id_not_implemented == a.id)) {
      b = "not implemented";
    } else if ((db_status_id_missing_argument_db_root == a.id)) {
      b = "missing argument 'db-root'";
    } else if ((db_status_id_path_not_accessible_db_root == a.id)) {
      b = "root not accessible";
    } else if ((db_status_id_memory == a.id)) {
      b = "not enough memory or other memory allocation error";
    } else if ((db_status_id_max_element_id == a.id)) {
      b = "maximum element identifier value has been reached for the type";
    } else if ((db_status_id_max_type_id == a.id)) {
      b = "maximum type identifier value has been reached";
    } else if ((db_status_id_max_type_id_size == a.id)) {
      b =
        "type identifier size is either configured to be greater than 16 bit, "
        "which is currently not supported, or is not smaller than node id size";
    } else if ((db_status_id_condition_unfulfilled == a.id)) {
      b = "condition unfulfilled";
    } else if ((db_status_id_no_more_data == a.id)) {
      b = "no more data to read";
    } else if ((db_status_id_different_format == a.id)) {
      b = "configured format differs from the format the database was created "
          "with";
    } else {
      b = "";
    };
  } else if ((db_status_group_lmdb == a.group)) {
    b = mdb_strerror(a.id);
  } else {
    b = "";
  };
  return (((b8*)(b)));
};
/** get the name if available for a status */
b8* db_status_name(status_t a) {
  char* b;
  if ((db_status_group_db == a.group)) {
    if ((db_status_id_input_type == a.id)) {
      b = "input-type";
    } else if ((db_status_id_data_length == a.id)) {
      b = "data-length";
    } else if ((db_status_id_duplicate == a.id)) {
      b = "duplicate";
    } else if ((db_status_id_not_implemented == a.id)) {
      b = "not-implemented";
    } else if ((db_status_id_missing_argument_db_root == a.id)) {
      b = "missing-argument-db-root";
    } else if ((db_status_id_path_not_accessible_db_root == a.id)) {
      b = "path-not-accessible-db-root";
    } else if ((db_status_id_memory == a.id)) {
      b = "memory";
    } else if ((db_status_id_max_element_id == a.id)) {
      b = "max-element-id-reached";
    } else if ((db_status_id_max_type_id == a.id)) {
      b = "max-type-id-reached";
    } else if ((db_status_id_max_type_id_size == a.id)) {
      b = "type-id-size-too-big";
    } else if ((db_status_id_condition_unfulfilled == a.id)) {
      b = "condition-unfulfilled";
    } else if ((db_status_id_no_more_data == a.id)) {
      b = "no-more-data";
    } else if ((db_status_id_different_format == a.id)) {
      b = "different-format";
    } else {
      b = "unknown";
    };
  } else if ((db_status_group_lmdb == a.group)) {
    b = mdb_strerror(a.id);
  } else {
    b = "unknown";
  };
  return (((b8*)(b)));
};
#define db_field_count_t b8
#define db_field_type_t b8
#define db_id_mask UINT64_MAX
#define db_id_t b64
#define db_index_count_t b8
#define db_name_len_max UINT8_MAX
#define db_name_len_t b8
#define db_ordinal_t b32
#define db_type_id_mask UINT16_MAX
#define db_type_id_t b16
#define db_pointer_to_id(a, index) (*(index + ((db_id_t*)(a))))
#ifndef db_id_t
#define db_id_t b64
#endif
#ifndef db_type_id_t
#define db_type_id_t b16
#endif
#ifndef db_ordinal_t
#define db_ordinal_t b32
#endif
#ifndef db_index_count_t
#define db_index_count_t b8
#endif
#ifndef db_field_count_t
#define db_field_count_t b8
#endif
#ifndef db_name_len_t
#define db_name_len_t b8
#endif
#ifndef db_name_len_max
#define db_name_len_max UINT8_MAX
#endif
#ifndef db_field_type_t
#define db_field_type_t b8
#endif
#ifndef db_id_mask
#define db_id_mask UINT64_MAX
#endif
#ifndef db_type_id_mask
#define db_type_id_mask UINT16_MAX
#endif
#ifndef db_id_equal_p
#define db_id_equal_p(a, b) (a == b)
#endif
#ifndef db_id_compare
#define db_id_compare(a, b) ((a < b) ? -1 : (a > b))
#endif
#define db_size_id sizeof(db_id_t)
#define db_size_type_id sizeof(db_type_id_t)
#define db_size_ordinal sizeof(db_ordinal_t)
#define db_ordinal_compare db_id_compare
#define db_size_graph_data (db_size_ordinal + db_size_id)
#define db_size_graph_key (2 * db_size_id)
#define db_read_option_skip 1
#define db_read_option_is_set_left 2
#define db_read_option_is_set_right 4
#define db_read_option_initialised 8
#define db_null 0
#define db_type_id_limit db_type_id_mask
#define db_size_element_id (sizeof(db_id_t) - sizeof(db_type_id_t))
#define db_id_type_mask \
  (((db_id_t)(db_type_id_mask)) << (8 * db_size_element_id))
#define db_id_element_mask ~db_id_type_mask
#define db_element_id_limit db_id_element_mask
#define db_type_flag_virtual 1
#define db_system_label_format 0
#define db_system_label_type 1
#define db_system_label_index 2
#define db_field_type_float32 4
#define db_field_type_float64 6
#define db_field_type_binary 1
#define db_field_type_string 3
#define db_field_type_int8 48
#define db_field_type_int16 80
#define db_field_type_int32 112
#define db_field_type_int64 144
#define db_field_type_uint8 32
#define db_field_type_uint16 64
#define db_field_type_uint32 96
#define db_field_type_uint64 128
#define db_field_type_char8 34
#define db_field_type_char16 66
#define db_field_type_char32 98
#define db_field_type_char64 130
#define db_env_types_extra_count 20
#define db_size_type_id_max 16
#define db_size_system_label 1
#define db_id_add_type(id, type_id) \
  (id | (((db_id_t)(type_id)) << (8 * db_size_element_id)))
/** get the type id part from a node id. a node id without element id */
#define db_id_type(id) (id >> (8 * db_size_element_id))
/** get the element id part from a node id. a node id without type id */
#define db_id_element(id) (db_id_element_mask & id)
#define db_pointer_to_id(a, index) (*(index + ((db_id_t*)(a))))
#define db_field_type_fixed_p(a) !(1 & a)
#define db_system_key_label(a) (*((b8*)(a)))
#define db_system_key_id(a) \
  (*((db_type_id_t*)((db_size_system_label + ((b8*)(a))))))
#define db_status_memory_error_if_null(variable) \
  if (!variable) { \
    status_set_both_goto(db_status_group_db, db_status_id_memory); \
  }
#define db_malloc(variable, size) \
  variable = malloc(size); \
  db_status_memory_error_if_null(variable)
/** allocate memory for a string with size and one extra last null element */
#define db_malloc_string(variable, len) \
  db_malloc(variable, (1 + len)); \
  (*(len + variable)) = 0
#define db_calloc(variable, count, size) \
  variable = calloc(count, size); \
  db_status_memory_error_if_null(variable)
#define db_realloc(variable, variable_temp, size) \
  variable_temp = realloc(variable, size); \
  db_status_memory_error_if_null(variable_temp); \
  variable = variable_temp
#define db_env_define(name) \
  db_env_t* name; \
  db_calloc(name, 1, sizeof(db_env_t))
#define db_data_t MDB_val
#define db_data_data(a) data.mv_data
#define db_data_data_set(a, value) data.mv_data = value
#define db_data_size(a) data.mv_size
#define db_data_size_set(a, value) data.mv_size = value
#define db_txn_declare(env, name) db_txn_t name = { 0, env }
#define db_txn_begin(txn) \
  db_mdb_status_require_x( \
    mdb_txn_begin((*txn.env).mdb_env, 0, MDB_RDONLY, &(txn.mdb_txn)))
#define db_txn_write_begin(txn) \
  db_mdb_status_require_x( \
    mdb_txn_begin((*txn.env).mdb_env, 0, 0, &(txn.mdb_txn)))
#define db_txn_abort(a) \
  mdb_txn_abort(a.mdb_txn); \
  a.mdb_txn = 0
#define db_txn_abort_if_active(a) \
  if (a.mdb_txn) { \
    db_txn_abort(a); \
  }
#define db_txn_active_p(a) (a.mdb_txn ? 1 : 0)
#define db_txn_commit(a) \
  db_mdb_status_require_x(mdb_txn_commit(a.mdb_txn)); \
  a.mdb_txn = 0
/** db-id-t -> db-id-t */
#define db_node_virtual_to_data(id) (id >> 2)
#define db_pointer_allocation_set(result, expression, result_temp) \
  result_temp = expression; \
  if (result_temp) { \
    result = result_temp; \
  } else { \
    db_status_set_id_goto(db_status_id_memory); \
  }
#define db_ids_add_x(target, source, ids_temp) \
  db_pointer_allocation_set(target, db_ids_add(target, source), ids_temp)
#define db_define_ids(name) db_ids_t* name = 0
#define db_define_ids_2(name_1, name_2) \
  db_define_ids(name_1); \
  db_define_ids(name_2)
#define db_define_ids_3(name_1, name_2, name_3) \
  db_define_ids_2(name_1, name_2); \
  db_define_ids(name_3)
#define db_graph_data_to_id(a) db_pointer_to_id((1 + ((db_ordinal_t*)(a))), 0)
#define db_graph_data_to_ordinal(a) (*((db_ordinal_t*)(a)))
#define db_graph_data_set_id(a, value) db_graph_data_to_id(a) = value
#define db_graph_data_set_ordinal(a, value) db_graph_data_to_ordinal(a) = value
#define db_graph_data_set_both(a, ordinal, id) \
  db_graph_data_set_ordinal(ordinal); \
  db_graph_data_set_id(id)
typedef struct {
  b8* name;
  db_name_len_t name_len;
  db_field_type_t type;
} db_field_t;
typedef struct {
  MDB_dbi dbi;
  db_field_t* fields;
  db_field_count_t fields_count;
  db_type_id_t type;
} db_index_t;
typedef struct {
  db_field_count_t fields_count;
  db_field_count_t fields_fixed_count;
  db_field_count_t* fields_fixed_offsets;
  db_field_t* fields;
  b8 flags;
  db_type_id_t id;
  db_index_t* indices;
  db_index_count_t indices_count;
  b8* name;
  db_id_t sequence;
} db_type_t;
typedef struct {
  MDB_dbi dbi_nodes;
  MDB_dbi dbi_graph_ll;
  MDB_dbi dbi_graph_lr;
  MDB_dbi dbi_graph_rl;
  MDB_dbi dbi_system;
  MDB_env* mdb_env;
  boolean open;
  b8* root;
  pthread_mutex_t mutex;
  db_type_t* types;
  db_type_id_t types_len;
} db_env_t;
typedef struct {
  db_id_t id;
  size_t size;
  b0* data;
} db_data_record_t;
typedef struct {
  MDB_txn* mdb_txn;
  db_env_t* env;
} db_txn_t;
typedef struct {
  MDB_stat system;
  MDB_stat nodes;
  MDB_stat graph_lr;
  MDB_stat graph_rl;
  MDB_stat graph_ll;
} db_statistics_t;
typedef struct {
  b8 read_only_p;
  size_t maximum_size_octets;
  b32 maximum_reader_count;
  b32 maximum_db_count;
  b8 filesystem_has_ordered_writes_p;
  b32 env_open_flags;
  b16 file_permissions;
} db_open_options_t;
typedef struct {
  db_id_t left;
  db_id_t right;
  db_id_t label;
  db_ordinal_t ordinal;
} db_graph_record_t;
typedef db_ordinal_t (*db_graph_ordinal_generator_t)(b0*);
#include "./lib/data-structures.c"
status_t db_statistics(db_txn_t txn, db_statistics_t* result);
b0 db_close(db_env_t* env);
status_t db_open(b8* root, db_open_options_t* options, db_env_t* env);
db_type_t* db_type_get(db_env_t* env, b8* name);
status_t db_type_create(db_env_t* env,
  b8* name,
  db_field_count_t field_count,
  db_field_t* fields,
  b8 flags,
  db_type_id_t* result);
status_t db_type_delete(db_env_t* env, db_type_id_t id);
status_t db_sequence_next_system(db_env_t* env, db_type_id_t* result);
status_t db_sequence_next(db_env_t* env, db_type_id_t type_id, db_id_t* result);
b8 db_field_type_size(b8 a);
status_t db_graph_ensure(db_txn_t txn,
  db_ids_t* left,
  db_ids_t* right,
  db_ids_t* label,
  db_graph_ordinal_generator_t ordinal_generator,
  b0* ordinal_generator_state);
b8* db_status_description(status_t a);
b8* db_status_name(status_t a);
b8* db_status_group_id_to_name(status_i_t a);