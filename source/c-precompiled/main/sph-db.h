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
  db_status_id_max_id,
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
    } else if ((db_status_id_max_id == a.id)) {
      b = "maximum identifier value for the type has been reached";
    } else if ((db_status_id_max_type_id == a.id)) {
      b = "maximum type identifier value has been reached";
    } else if ((db_status_id_max_type_id_size == a.id)) {
      b = "type identifier size is configured to be greater than 16 bit, which "
          "is currently not supported";
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
    } else if ((db_status_id_max_id == a.id)) {
      b = "max-id-reached";
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
#define db_id_t b64
#define db_type_id_t b16
#define db_ordinal_t b32
#define db_index_count_t b8
#define db_field_count_t b8
#define db_name_len_t b8
#define db_name_len_max 255
#define db_field_type_t b8
#define db_id_mask UINT64_MAX
#define db_type_id_mask UINT16_MAX
#define db_size_id sizeof(db_id_t)
#define db_size_type_id sizeof(db_type_id_t)
#define db_size_ordinal sizeof(db_ordinal_t)
#define db_id_equal_p(a, b) (a == b)
#define db_id_compare(a, b) ((a < b) ? -1 : (a > b))
#define db_pointer_to_id(a, index) (*(index + ((db_id_t*)(a))))
#ifndef db_id_t
#define db_id_t b64
#endif
#ifndef db_type_id_t
#define db_type_id_t b8
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
#ifndef db_id_max
#define db_id_max UINT64_MAX
#endif
#ifndef db_size_id
#define db_size_id sizeof(db_id_t)
#endif
#ifndef db_size_ordinal
#define db_size_ordinal sizeof(db_ordinal_t)
#endif
#ifndef db_size_type_id
#define db_size_type_id sizeof(db_type_id_t)
#endif
#ifndef db_id_equal_p
#define db_id_equal_p(a, b) (a == b)
#endif
#ifndef db_id_compare
#define db_id_compare(a, b) ((a < b) ? -1 : (a > b))
#endif
#ifndef db_pointer_to_id
#define db_pointer_to_id(a, index) (*(index + ((db_id_t*)(a))))
#endif
#define db_ordinal_compare db_id_compare
#define db_size_graph_data (db_size_ordinal + db_size_id)
#define db_size_graph_key (2 * db_size_id)
#define db_read_option_skip 1
#define db_read_option_is_set_left 2
#define db_read_option_is_set_right 4
#define db_read_option_initialised 8
#define db_null 0
#define db_type_id_max db_type_id_mask
#define db_element_id_mask (db_type_id_mask ^ db_id_mask)
#define db_element_id_max db_element_id_mask
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
#define db_field_type_fixed_p(a) !(1 & a)
#define db_system_key_label(a) (*((b8*)(a)))
#define db_system_key_id(a) (*((db_type_id_t*)((1 + ((b8*)(a))))))
#define db_id_add_type(id, type_id) \
  (id | (type_id << (8 * sizeof(db_type_id_t))))
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
/** get the type id part from a node id. a node id without element id */
#define db_id_type(id) (db_type_id_mask & id)
/** get the element id part from a node id. a node id without type id */
#define db_id_element(id) (db_element_id_mask & id)
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
  db_id_t left;
  db_id_t right;
  db_id_t label;
  db_ordinal_t ordinal;
} db_graph_record_t;
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
b8 db_field_type_size(b8 a);
#define imht_set_key_t db_id_t
#include <stdlib.h>
#include <inttypes.h>
#ifndef imht_set_key_t
#define imht_set_key_t uint64_t
#endif
#ifndef imht_set_can_contain_zero_p
#define imht_set_can_contain_zero_p 1
#endif
#ifndef imht_set_size_factor
#define imht_set_size_factor 2
#endif
uint16_t imht_set_primes[] = { 0,
  3,
  7,
  13,
  19,
  29,
  37,
  43,
  53,
  61,
  71,
  79,
  89,
  101,
  107,
  113,
  131,
  139,
  151,
  163,
  173,
  181,
  193,
  199,
  223,
  229,
  239,
  251,
  263,
  271,
  281,
  293,
  311,
  317,
  337,
  349,
  359,
  373,
  383,
  397,
  409,
  421,
  433,
  443,
  457,
  463,
  479,
  491,
  503,
  521,
  541,
  557,
  569,
  577,
  593,
  601,
  613,
  619,
  641,
  647,
  659,
  673,
  683,
  701,
  719,
  733,
  743,
  757,
  769,
  787,
  809,
  821,
  827,
  839,
  857,
  863,
  881,
  887,
  911,
  929,
  941,
  953,
  971,
  983,
  997 };
uint16_t* imht_set_primes_end = (imht_set_primes + 83);
typedef struct {
  size_t size;
  imht_set_key_t* content;
} imht_set_t;
size_t imht_set_calculate_hash_table_size(size_t min_size) {
  min_size = (imht_set_size_factor * min_size);
  uint16_t* primes = imht_set_primes;
  while ((primes < imht_set_primes_end)) {
    if ((min_size <= (*primes))) {
      return ((*primes));
    } else {
      primes = (1 + primes);
    };
  };
  if ((min_size <= (*primes))) {
    return ((*primes));
  };
  return ((1 | min_size));
};
uint8_t imht_set_create(size_t min_size, imht_set_t** result) {
  (*result) = malloc(sizeof(imht_set_t));
  if (!(*result)) {
    return (0);
  };
  min_size = imht_set_calculate_hash_table_size(min_size);
  (*(*result)).content = calloc(min_size, sizeof(imht_set_key_t));
  (*(*result)).size = min_size;
  return (((*(*result)).content ? 1 : 0));
};
void imht_set_destroy(imht_set_t* a) {
  if (a) {
    free((*a).content);
    free(a);
  };
};
#if imht_set_can_contain_zero_p
#define imht_set_hash(value, hash_table) \
  (value ? (1 + (value % (hash_table.size - 1))) : 0)
#else
#define imht_set_hash(value, hash_table) (value % hash_table.size)
#endif
/** returns the address of the element in the set, 0 if it was not found.
  caveat: if imht-set-can-contain-zero? is defined, which is the default,
  pointer-geterencing a returned address for the found value 0 will return 1
  instead */
imht_set_key_t* imht_set_find(imht_set_t* a, imht_set_key_t value) {
  imht_set_key_t* h = ((*a).content + imht_set_hash(value, (*a)));
  if ((*h)) {
#if imht_set_can_contain_zero_p
    if (((((*h) == value)) || ((0 == value)))) {
      return (h);
    };
#else
    if (((*h) == value)) {
      return (h);
    };
#endif
    imht_set_key_t* content_end = ((*a).content + ((*a).size - 1));
    imht_set_key_t* h2 = (1 + h);
    while ((h2 < content_end)) {
      if (!(*h2)) {
        return (0);
      } else {
        if ((value == (*h2))) {
          return (h2);
        };
      };
      h2 = (1 + h2);
    };
    if (!(*h2)) {
      return (0);
    } else {
      if ((value == (*h2))) {
        return (h2);
      };
    };
    h2 = (*a).content;
    while ((h2 < h)) {
      if (!(*h2)) {
        return (0);
      } else {
        if ((value == (*h2))) {
          return (h2);
        };
      };
      h2 = (1 + h2);
    };
  };
  return (0);
};
#define imht_set_contains_p(a, value) ((0 == imht_set_find(a, value)) ? 0 : 1)
/** returns 1 if the element was removed, 0 if it was not found */
uint8_t imht_set_remove(imht_set_t* a, imht_set_key_t value) {
  imht_set_key_t* value_address = imht_set_find(a, value);
  if (value_address) {
    (*value_address) = 0;
    return (1);
  } else {
    return (0);
  };
};
/** returns the address of the added or already included element, 0 if there is
 * no space left in the set */
imht_set_key_t* imht_set_add(imht_set_t* a, imht_set_key_t value) {
  imht_set_key_t* h = ((*a).content + imht_set_hash(value, (*a)));
  if ((*h)) {
#if imht_set_can_contain_zero_p
    if ((((value == (*h))) || ((0 == value)))) {
      return (h);
    };
#else
    if ((value == (*h))) {
      return (h);
    };
#endif
    imht_set_key_t* content_end = ((*a).content + ((*a).size - 1));
    imht_set_key_t* h2 = (1 + h);
    while ((((h2 <= content_end)) && (*h2))) {
      h2 = (1 + h2);
    };
    if ((h2 > content_end)) {
      h2 = (*a).content;
      while (((h2 < h) && (*h2))) {
        h2 = (1 + h2);
      };
      if ((h2 == h)) {
        return (0);
      } else {
#if imht_set_can_contain_zero_p
        (*h2) = ((0 == value) ? 1 : value);
#else
        (*h2) = value;
#endif
      };
    } else {
#if imht_set_can_contain_zero_p
      (*h2) = ((0 == value) ? 1 : value);
#else
      (*h2) = value;
#endif
    };
  } else {
#if imht_set_can_contain_zero_p
    (*h) = ((0 == value) ? 1 : value);
#else
    (*h) = value;
#endif
    return (h);
  };
};
#define mi_list_name_prefix db_ids
#define mi_list_element_t db_id_t
/* a minimal linked list with custom element types.
   this file can be included multiple times to create differently typed
   versions, depending the value of the preprocessor variables
   mi-list-name-infix and mi-list-element-t before inclusion */
#include <stdlib.h>
#include <inttypes.h>
#ifndef mi_list_name_prefix
#define mi_list_name_prefix mi_list_64
#endif
#ifndef mi_list_element_t
#define mi_list_element_t uint64_t
#endif
/* there does not seem to be a simpler way for identifier concatenation in c in
 * this case */
#ifndef mi_list_name_concat
#define mi_list_name_concat(a, b) a##_##b
#define mi_list_name_concatenator(a, b) mi_list_name_concat(a, b)
#define mi_list_name(name) mi_list_name_concatenator(mi_list_name_prefix, name)
#endif
#define mi_list_struct_name mi_list_name(struct)
#define mi_list_t mi_list_name(t)
typedef struct mi_list_struct_name {
  struct mi_list_struct_name* link;
  mi_list_element_t data;
} mi_list_t;
#ifndef mi_list_first
#define mi_list_first(a) (*a).data
#define mi_list_first_address(a) &(*a).data
#define mi_list_rest(a) (*a).link
#endif
mi_list_t* mi_list_name(drop)(mi_list_t* a) {
  mi_list_t* a_next = mi_list_rest(a);
  free(a);
  return (a_next);
};
/** it would be nice to set the pointer to zero, but that would require more
 * indirection with a pointer-pointer */
void mi_list_name(destroy)(mi_list_t* a) {
  mi_list_t* a_next = 0;
  while (a) {
    a_next = (*a).link;
    free(a);
    a = a_next;
  };
};
mi_list_t* mi_list_name(add)(mi_list_t* a, mi_list_element_t value) {
  mi_list_t* element = calloc(1, sizeof(mi_list_t));
  if (!element) {
    return (0);
  };
  (*element).data = value;
  (*element).link = a;
  return (element);
};
size_t mi_list_name(length)(mi_list_t* a) {
  size_t result = 0;
  while (a) {
    result = (1 + result);
    a = mi_list_rest(a);
  };
  return (result);
};
#undef mi_list_name_prefix
#undef mi_list_element_t
#undef mi_list_struct_name
#undef mi_list_t
;
#define mi_list_name_prefix db_data_list
#define mi_list_element_t db_data_t
/* a minimal linked list with custom element types.
   this file can be included multiple times to create differently typed
   versions, depending the value of the preprocessor variables
   mi-list-name-infix and mi-list-element-t before inclusion */
#include <stdlib.h>
#include <inttypes.h>
#ifndef mi_list_name_prefix
#define mi_list_name_prefix mi_list_64
#endif
#ifndef mi_list_element_t
#define mi_list_element_t uint64_t
#endif
/* there does not seem to be a simpler way for identifier concatenation in c in
 * this case */
#ifndef mi_list_name_concat
#define mi_list_name_concat(a, b) a##_##b
#define mi_list_name_concatenator(a, b) mi_list_name_concat(a, b)
#define mi_list_name(name) mi_list_name_concatenator(mi_list_name_prefix, name)
#endif
#define mi_list_struct_name mi_list_name(struct)
#define mi_list_t mi_list_name(t)
typedef struct mi_list_struct_name {
  struct mi_list_struct_name* link;
  mi_list_element_t data;
} mi_list_t;
#ifndef mi_list_first
#define mi_list_first(a) (*a).data
#define mi_list_first_address(a) &(*a).data
#define mi_list_rest(a) (*a).link
#endif
mi_list_t* mi_list_name(drop)(mi_list_t* a) {
  mi_list_t* a_next = mi_list_rest(a);
  free(a);
  return (a_next);
};
/** it would be nice to set the pointer to zero, but that would require more
 * indirection with a pointer-pointer */
void mi_list_name(destroy)(mi_list_t* a) {
  mi_list_t* a_next = 0;
  while (a) {
    a_next = (*a).link;
    free(a);
    a = a_next;
  };
};
mi_list_t* mi_list_name(add)(mi_list_t* a, mi_list_element_t value) {
  mi_list_t* element = calloc(1, sizeof(mi_list_t));
  if (!element) {
    return (0);
  };
  (*element).data = value;
  (*element).link = a;
  return (element);
};
size_t mi_list_name(length)(mi_list_t* a) {
  size_t result = 0;
  while (a) {
    result = (1 + result);
    a = mi_list_rest(a);
  };
  return (result);
};
#undef mi_list_name_prefix
#undef mi_list_element_t
#undef mi_list_struct_name
#undef mi_list_t
;
#define mi_list_name_prefix db_data_records
#define mi_list_element_t db_data_record_t
/* a minimal linked list with custom element types.
   this file can be included multiple times to create differently typed
   versions, depending the value of the preprocessor variables
   mi-list-name-infix and mi-list-element-t before inclusion */
#include <stdlib.h>
#include <inttypes.h>
#ifndef mi_list_name_prefix
#define mi_list_name_prefix mi_list_64
#endif
#ifndef mi_list_element_t
#define mi_list_element_t uint64_t
#endif
/* there does not seem to be a simpler way for identifier concatenation in c in
 * this case */
#ifndef mi_list_name_concat
#define mi_list_name_concat(a, b) a##_##b
#define mi_list_name_concatenator(a, b) mi_list_name_concat(a, b)
#define mi_list_name(name) mi_list_name_concatenator(mi_list_name_prefix, name)
#endif
#define mi_list_struct_name mi_list_name(struct)
#define mi_list_t mi_list_name(t)
typedef struct mi_list_struct_name {
  struct mi_list_struct_name* link;
  mi_list_element_t data;
} mi_list_t;
#ifndef mi_list_first
#define mi_list_first(a) (*a).data
#define mi_list_first_address(a) &(*a).data
#define mi_list_rest(a) (*a).link
#endif
mi_list_t* mi_list_name(drop)(mi_list_t* a) {
  mi_list_t* a_next = mi_list_rest(a);
  free(a);
  return (a_next);
};
/** it would be nice to set the pointer to zero, but that would require more
 * indirection with a pointer-pointer */
void mi_list_name(destroy)(mi_list_t* a) {
  mi_list_t* a_next = 0;
  while (a) {
    a_next = (*a).link;
    free(a);
    a = a_next;
  };
};
mi_list_t* mi_list_name(add)(mi_list_t* a, mi_list_element_t value) {
  mi_list_t* element = calloc(1, sizeof(mi_list_t));
  if (!element) {
    return (0);
  };
  (*element).data = value;
  (*element).link = a;
  return (element);
};
size_t mi_list_name(length)(mi_list_t* a) {
  size_t result = 0;
  while (a) {
    result = (1 + result);
    a = mi_list_rest(a);
  };
  return (result);
};
#undef mi_list_name_prefix
#undef mi_list_element_t
#undef mi_list_struct_name
#undef mi_list_t
;
#define mi_list_name_prefix db_graph_records
#define mi_list_element_t db_graph_record_t
/* a minimal linked list with custom element types.
   this file can be included multiple times to create differently typed
   versions, depending the value of the preprocessor variables
   mi-list-name-infix and mi-list-element-t before inclusion */
#include <stdlib.h>
#include <inttypes.h>
#ifndef mi_list_name_prefix
#define mi_list_name_prefix mi_list_64
#endif
#ifndef mi_list_element_t
#define mi_list_element_t uint64_t
#endif
/* there does not seem to be a simpler way for identifier concatenation in c in
 * this case */
#ifndef mi_list_name_concat
#define mi_list_name_concat(a, b) a##_##b
#define mi_list_name_concatenator(a, b) mi_list_name_concat(a, b)
#define mi_list_name(name) mi_list_name_concatenator(mi_list_name_prefix, name)
#endif
#define mi_list_struct_name mi_list_name(struct)
#define mi_list_t mi_list_name(t)
typedef struct mi_list_struct_name {
  struct mi_list_struct_name* link;
  mi_list_element_t data;
} mi_list_t;
#ifndef mi_list_first
#define mi_list_first(a) (*a).data
#define mi_list_first_address(a) &(*a).data
#define mi_list_rest(a) (*a).link
#endif
mi_list_t* mi_list_name(drop)(mi_list_t* a) {
  mi_list_t* a_next = mi_list_rest(a);
  free(a);
  return (a_next);
};
/** it would be nice to set the pointer to zero, but that would require more
 * indirection with a pointer-pointer */
void mi_list_name(destroy)(mi_list_t* a) {
  mi_list_t* a_next = 0;
  while (a) {
    a_next = (*a).link;
    free(a);
    a = a_next;
  };
};
mi_list_t* mi_list_name(add)(mi_list_t* a, mi_list_element_t value) {
  mi_list_t* element = calloc(1, sizeof(mi_list_t));
  if (!element) {
    return (0);
  };
  (*element).data = value;
  (*element).link = a;
  return (element);
};
size_t mi_list_name(length)(mi_list_t* a) {
  size_t result = 0;
  while (a) {
    result = (1 + result);
    a = mi_list_rest(a);
  };
  return (result);
};
#undef mi_list_name_prefix
#undef mi_list_element_t
#undef mi_list_struct_name
#undef mi_list_t
;
#define db_ids_first mi_list_first
#define db_ids_first_address mi_list_first_address
#define db_ids_rest mi_list_rest
#define db_data_list_first mi_list_first
#define db_data_list_first_address mi_list_first_address
#define db_data_list_rest mi_list_rest
#define db_data_records_first mi_list_first
#define db_data_records_first_address mi_list_first_address
#define db_data_records_rest mi_list_rest
#define db_graph_records_first mi_list_first
#define db_graph_records_first_address mi_list_first_address
#define db_graph_records_rest mi_list_rest
