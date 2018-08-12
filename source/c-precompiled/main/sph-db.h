/* this file is for declarations and macros needed to use sph-db as a shared library */
#include <math.h>
#include <pthread.h>
#include <lmdb.h>
#include <inttypes.h>
#include <stdio.h>
#define boolean ui8
#define i8 int8_t
#define i16 int16_t
#define i32 int32_t
#define i64 int64_t
#define i8_least int_least8_t
#define i16_least int_least16_t
#define i32_least int_least32_t
#define i64_least int_least64_t
#define i8_fast int_fast8_t
#define i16_fast int_fast16_t
#define i32_fast int_fast32_t
#define i64_fast int_fast64_t
#define ui8 int8_t
#define ui16 uint16_t
#define ui32 uint32_t
#define ui64 uint64_t
#define ui8_least uint_least8_t
#define ui16_least uint_least16_t
#define ui32_least uint_least32_t
#define ui64_least uint_least64_t
#define ui8_fast uint_fast8_t
#define ui16_fast uint_fast16_t
#define ui32_fast uint_fast32_t
#define ui64_fast uint_fast64_t
#define f32 float
#define f64 double
/** writes values with current routine name and line info to standard output.
    example: (debug-log "%d" 1)
    otherwise like printf */
#define debug_log(format, ...) fprintf(stdout, "%s:%d " format "\n", __func__, __LINE__, __VA_ARGS__)
/* "iteration array" - a fixed size array with variable length content that makes iteration easier to code. it is used similar to a linked list.
  most bindings are generic macros that will work on all i-array types. i_array_add and i_array_forward go from left to right.
  examples:
    i_array_declare_type(my_type, int);
    i_array_allocate_my_type(a, 4);
    i_array_add(a, 1);
    i_array_add(a, 2);
    while(i_array_in_range(a)) { i_array_get(a); }
    i_array_free(a); */
#include <stdlib.h>
/** .current: to avoid having to write for-loops. it is what would be the index variable in loops
     .unused: to have variable length content in a fixed length array. points outside the memory area after the last element has been added
     .end: a boundary for iterations
     .start: the beginning of the allocated array and used for rewind and free */
#define i_array_declare_type(name, element_type) \
  typedef struct { \
    element_type* current; \
    element_type* unused; \
    element_type* end; \
    element_type* start; \
  } name; \
  boolean i_array_allocate_##name(name* a, size_t length) { \
    element_type* start; \
    start = malloc((length * sizeof(element_type))); \
    if (!start) { \
      return (0); \
    }; \
    a->start = start; \
    a->current = start; \
    a->unused = start; \
    a->end = (length + start); \
    return (1); \
  }
/** define so that in-range is false, length is zero and free doesnt fail */
#define i_array_declare(a, type) type a = { 0, 0, 0, 0 }
#define i_array_add(a, value) \
  *(a.unused) = value; \
  a.unused = (1 + a.unused)
/** set so that in-range is false, length is zero and free doesnt fail */
#define i_array_set_null(a) \
  a.start = 0; \
  a.unused = 0
#define i_array_in_range(a) (a.current < a.unused)
#define i_array_get_at(a, index) (a.start)[index]
#define i_array_get(a) *(a.current)
#define i_array_forward(a) a.current = (1 + a.current)
#define i_array_rewind(a) a.current = a.start
#define i_array_clear(a) a.unused = a.start
#define i_array_remove(a) a.unused = (a.unused - 1)
#define i_array_length(a) (a.unused - a.start)
#define i_array_max_length(a) (a.end - a.start)
#define i_array_free(a) free((a.start));
/* return status and error handling */
/* return status code and error handling. uses a local variable named "status" and a goto label named "exit".
   a status has an identifier and a group to discern between status identifiers of different libraries.
   status id 0 is success, everything else can be considered a failure or special case.
   status ids are 32 bit signed integers for compatibility with error return codes from many other existing libraries */
/** like status declare but with a default group */
#define status_declare_group(group) status_t status = { status_id_success, group }
#define status_id_success 0
#define status_group_undefined 0
#define status_declare status_t status = { status_id_success, status_group_undefined }
#define status_reset status_set_both(status_group_undefined, status_id_success)
#define status_is_success (status_id_success == status.id)
#define status_is_failure !status_is_success
#define status_goto goto exit
#define status_set_both(group_id, status_id) \
  status.group = group_id; \
  status.id = status_id
/** update status with the result of expression and goto error on failure */
#define status_require(expression) \
  status = expression; \
  if (status_is_failure) { \
    status_goto; \
  }
/** set the status id and goto error */
#define status_set_id_goto(status_id) \
  status.id = status_id; \
  status_goto
#define status_set_group_goto(group_id) \
  status.group = group_id; \
  status_goto
#define status_set_both_goto(group_id, status_id) \
  status_set_both(group_id, status_id); \
  status_goto
/** like status-require but expression returns only status.id */
#define status_id_require(expression) \
  status.id = expression; \
  if (status_is_failure) { \
    status_goto; \
  }
;
typedef i32 status_id_t;
typedef struct {
  status_id_t id;
  ui8 group;
} status_t;
enum { db_status_id_success,
  db_status_id_undefined,
  db_status_id_condition_unfulfilled,
  db_status_id_data_length,
  db_status_id_different_format,
  db_status_id_duplicate,
  db_status_id_input_type,
  db_status_id_invalid_argument,
  db_status_id_max_element_id,
  db_status_id_max_type_id,
  db_status_id_max_type_id_size,
  db_status_id_memory,
  db_status_id_missing_argument_db_root,
  db_status_id_notfound,
  db_status_id_not_implemented,
  db_status_id_path_not_accessible_db_root,
  db_status_id_index_keysize,
  db_status_group_db,
  db_status_group_lmdb,
  db_status_group_libc };
#define db_status_set_id_goto(status_id) status_set_both_goto(db_status_group_db, status_id)
#define db_status_require_read(expression) \
  status = expression; \
  if (!(status_is_success || (status.id == db_status_id_notfound))) { \
    status_goto; \
  }
#define db_status_success_if_notfound \
  if (status.id == db_status_id_notfound) { \
    status.id = status_id_success; \
  }
ui8* db_status_group_id_to_name(status_id_t a) {
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
ui8* db_status_description(status_t a) {
  char* b;
  if (db_status_group_lmdb == a.group) {
    b = mdb_strerror((a.id));
  } else {
    if (db_status_id_invalid_argument == a.id) {
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
  return (((ui8*)(b)));
};
/** get the name if available for a status */
ui8* db_status_name(status_t a) {
  char* b;
  if (db_status_group_lmdb == a.group) {
    b = mdb_strerror((a.id));
  } else {
    if (db_status_id_invalid_argument == a.id) {
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
  return (((ui8*)(b)));
};
#define db_count_t ui32
#define db_fields_len_t ui8
#define db_field_type_t ui8
#define db_id_mask UINT64_MAX
#define db_id_t ui64
#define db_index_len_t ui8
#define db_name_len_max UINT8_MAX
#define db_name_len_t ui8
#define db_data_len_t ui32
#define db_data_len_max UINT32_MAX
#define db_ordinal_t ui32
#define db_type_id_mask UINT16_MAX
#define db_type_id_t ui16
#ifndef db_id_t
#define db_id_t ui64
#endif
#ifndef db_type_id_t
#define db_type_id_t ui16
#endif
#ifndef db_ordinal_t
#define db_ordinal_t ui32
#endif
#ifndef db_count_t
#define db_count_t ui32
#endif
#ifndef db_indices_len_t
#define db_indices_len_t ui8
#endif
#ifndef db_fields_len_t
#define db_fields_len_t ui8
#endif
#ifndef db_name_len_t
#define db_name_len_t ui8
#endif
#ifndef db_name_len_max
#define db_name_len_max UINT8_MAX
#endif
#ifndef db_field_type_t
#define db_field_type_t ui8
#endif
#ifndef db_id_mask
#define db_id_mask UINT64_MAX
#endif
#ifndef db_type_id_mask
#define db_type_id_mask UINT16_MAX
#endif
#ifndef db_id_equal
#define db_id_equal(a, b) (a == b)
#endif
#ifndef db_id_compare
#define db_id_compare(a, b) ((a < b) ? -1 : (a > b))
#endif
#define db_ordinal_compare db_id_compare
#define db_size_graph_data (sizeof(db_ordinal_t) + sizeof(db_id_t))
#define db_size_graph_key (2 * sizeof(db_id_t))
#define db_null 0
#define db_size_element_id (sizeof(db_id_t) - sizeof(db_type_id_t))
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
#define db_id_add_type(id, type_id) (id | (((db_id_t)(type_id)) << (8 * db_size_element_id)))
/** get the type id part from a node id. a node id without element id */
#define db_id_type(id) (id >> (8 * db_size_element_id))
/** get the element id part from a node id. a node id without type id */
#define db_id_element(id) (db_id_element_mask & id)
/** db-id-t -> db-id-t */
#define db_node_virtual_to_data(id) (id >> 2)
#define db_pointer_allocation_set(result, expression, result_temp) \
  result_temp = expression; \
  if (result_temp) { \
    result = result_temp; \
  } else { \
    db_status_set_id_goto(db_status_id_memory); \
  }
#define db_txn_declare(env, name) db_txn_t name = { 0, env }
#define db_txn_abort_if_active(a) \
  if (a.mdb_txn) { \
    db_txn_abort((&a)); \
  }
#define db_txn_is_active(a) (a.mdb_txn ? 1 : 0)
#define db_field_set(a, a_type, a_name, a_name_len) \
  a.type = a_type; \
  a.name = a_name; \
  a.name_len = a_name_len
typedef struct {
  db_id_t left;
  db_id_t right;
  db_id_t label;
  db_ordinal_t ordinal;
} db_graph_record_t;
i_array_declare_type(db_ids_t, db_id_t);
i_array_declare_type(db_graph_records_t, db_graph_record_t);
typedef struct {
  ui8* name;
  db_name_len_t name_len;
  db_field_type_t type;
  db_fields_len_t index;
} db_field_t;
struct db_index_t;
typedef struct {
  db_fields_len_t fields_len;
  db_fields_len_t fields_fixed_count;
  size_t* fields_fixed_offsets;
  db_field_t* fields;
  ui8 flags;
  db_type_id_t id;
  struct db_index_t* indices;
  db_indices_len_t indices_len;
  size_t indices_size;
  ui8* name;
  db_id_t sequence;
} db_type_t;
typedef struct db_index_t {
  MDB_dbi dbi;
  db_fields_len_t* fields;
  db_fields_len_t fields_len;
  db_type_t* type;
} db_index_t;
typedef struct {
  MDB_dbi dbi_nodes;
  MDB_dbi dbi_graph_ll;
  MDB_dbi dbi_graph_lr;
  MDB_dbi dbi_graph_rl;
  MDB_dbi dbi_system;
  MDB_env* mdb_env;
  boolean open;
  ui8* root;
  pthread_mutex_t mutex;
  int maxkeysize;
  db_type_t* types;
  db_type_id_t types_len;
} db_env_t;
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
  boolean is_read_only;
  size_t maximum_size;
  db_count_t maximum_reader_count;
  db_count_t maximum_db_count;
  boolean filesystem_has_ordered_writes;
  ui32_least env_open_flags;
  ui16 file_permissions;
} db_open_options_t;
typedef db_ordinal_t (*db_graph_ordinal_generator_t)(void*);
typedef struct {
  db_ordinal_t min;
  db_ordinal_t max;
} db_ordinal_condition_t;
typedef struct {
  db_data_len_t size;
  void* data;
} db_node_value_t;
typedef struct {
  db_node_value_t* data;
  db_fields_len_t last;
  db_type_t* type;
} db_node_values_t;
typedef struct {
  void* data;
  size_t size;
} db_node_data_t;
typedef boolean (*db_node_matcher_t)(db_id_t, db_node_data_t, void*);
typedef struct {
  db_id_t current;
  MDB_cursor* cursor;
} db_index_selection_t;
typedef struct {
  db_node_data_t current;
  db_id_t current_id;
  db_index_selection_t index_selection;
  MDB_cursor* nodes;
} db_node_index_selection_t;
typedef struct {
  db_count_t count;
  db_node_data_t current;
  db_id_t current_id;
  MDB_cursor* cursor;
  db_env_t* env;
  db_ids_t ids;
  db_node_matcher_t matcher;
  void* matcher_state;
  ui8 options;
  db_type_t* type;
} db_node_selection_t;
typedef struct {
  status_t status;
  MDB_cursor* restrict cursor;
  MDB_cursor* restrict cursor_2;
  db_ids_t left;
  db_ids_t right;
  db_ids_t label;
  void* ids_set;
  db_ordinal_condition_t* ordinal;
  ui8 options;
  void* reader;
} db_graph_selection_t;
typedef status_t (*db_graph_reader_t)(db_graph_selection_t*, db_count_t, db_graph_records_t*);
status_t db_env_new(db_env_t** result);
status_t db_statistics(db_txn_t txn, db_statistics_t* result);
void db_close(db_env_t* env);
status_t db_open(ui8* root, db_open_options_t* options, db_env_t* env);
db_field_t* db_type_field_get(db_type_t* type, ui8* name);
db_type_t* db_type_get(db_env_t* env, ui8* name);
status_t db_type_create(db_env_t* env, ui8* name, db_field_t* fields, db_fields_len_t fields_len, ui8 flags, db_type_t** result);
status_t db_type_delete(db_env_t* env, db_type_id_t id);
status_t db_sequence_next_system(db_env_t* env, db_type_id_t* result);
status_t db_sequence_next(db_env_t* env, db_type_id_t type_id, db_id_t* result);
ui8 db_field_type_size(ui8 a);
ui8* db_status_description(status_t a);
ui8* db_status_name(status_t a);
ui8* db_status_group_id_to_name(status_id_t a);
void db_graph_selection_destroy(db_graph_selection_t* state);
status_t db_graph_select(db_txn_t txn, db_ids_t* left, db_ids_t* right, db_ids_t* label, db_ordinal_condition_t* ordinal, db_count_t offset, db_graph_selection_t* result);
status_t db_graph_read(db_graph_selection_t* state, db_count_t count, db_graph_records_t* result);
status_t db_graph_ensure(db_txn_t txn, db_ids_t left, db_ids_t right, db_ids_t label, db_graph_ordinal_generator_t ordinal_generator, void* ordinal_generator_state);
void db_graph_selection_destroy(db_graph_selection_t* state);
status_t db_graph_delete(db_txn_t txn, db_ids_t* left, db_ids_t* right, db_ids_t* label, db_ordinal_condition_t* ordinal);
status_t db_graph_select(db_txn_t txn, db_ids_t* left, db_ids_t* right, db_ids_t* label, db_ordinal_condition_t* ordinal, db_count_t offset, db_graph_selection_t* result);
status_t db_node_values_new(db_type_t* type, db_node_values_t* result);
void db_node_values_set(db_node_values_t* values, db_fields_len_t field_index, void* data, size_t size);
status_t db_node_values_to_data(db_node_values_t values, db_node_data_t* result);
status_t db_node_data_to_values(db_type_t* type, db_node_data_t data, db_node_values_t* result);
status_t db_node_create(db_txn_t txn, db_node_values_t values, db_id_t* result);
status_t db_node_get(db_txn_t txn, db_id_t id, db_node_data_t* result);
status_t db_node_delete(db_txn_t txn, db_ids_t* ids);
db_node_data_t db_node_data_ref(db_type_t* type, db_node_data_t data, db_fields_len_t field);
db_node_data_t db_node_ref(db_node_selection_t* state, db_fields_len_t field);
status_t db_node_exists(db_txn_t txn, db_ids_t ids, boolean* result);
status_t db_node_select(db_txn_t txn, db_ids_t* ids, db_type_t* type, db_count_t offset, db_node_matcher_t matcher, void* matcher_state, db_node_selection_t* result_state);
status_t db_node_next(db_node_selection_t* state);
status_t db_node_skip(db_node_selection_t* state, db_count_t count);
void db_node_selection_destroy(db_node_selection_t* state);
status_t db_node_update(db_txn_t txn, db_id_t id, db_node_values_t values);
status_t db_txn_write_begin(db_txn_t* a);
status_t db_txn_begin(db_txn_t* a);
status_t db_txn_commit(db_txn_t* a);
void db_txn_abort(db_txn_t* a);
db_index_t* db_index_get(db_type_t* type, db_fields_len_t* fields, db_fields_len_t fields_len);
status_t db_index_create(db_env_t* env, db_type_t* type, db_fields_len_t* fields, db_fields_len_t fields_len);
status_t db_index_delete(db_env_t* env, db_index_t* index);
status_t db_index_rebuild(db_env_t* env, db_index_t* index);
status_t db_index_next(db_index_selection_t state);
void db_index_selection_destroy(db_index_selection_t* state);
status_t db_index_select(db_txn_t txn, db_index_t index, db_node_values_t values, db_index_selection_t* result);
status_t db_node_index_next(db_node_index_selection_t selection);
status_t db_node_index_select(db_txn_t txn, db_index_t index, db_node_values_t values, db_node_index_selection_t* result);
void db_node_index_selection_destroy(db_node_index_selection_t* selection);