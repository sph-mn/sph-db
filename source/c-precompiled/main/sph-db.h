/* this file is for declarations and macros needed to use sph-db as a shared library */
#include <inttypes.h>
#include <math.h>
#include <pthread.h>
#include <lmdb.h>
#include <stdio.h>
/** writes values with current routine name and line info to standard output.
    example: (debug-log "%d" 1)
    otherwise like printf */
#define debug_log(format, ...) fprintf(stdout, "%s:%d " format "\n", __func__, __LINE__, __VA_ARGS__)
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
typedef int32_t status_id_t;
typedef struct {
  status_id_t id;
  uint8_t group;
} status_t;
/* "iteration array" - an array with variable length content that makes iteration easier to code.
  most bindings are generic macros that will work on all i-array types. i-array-add and i-array-forward go from left to right.
  examples:
    i_array_declare_type(my_type, int);
    my_type a;
    i_array_allocate_my_type(4, &a);
    i_array_add(a, 1);
    i_array_add(a, 2);
    while(i_array_in_range(a)) {
      i_array_get(a);
      i_array_forward(a);
    }
    i_array_free(a); */
#include <stdlib.h>
/** .current: to avoid having to write for-loops. this would correspond to the index variable in loops
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
  uint8_t i_array_allocate_##name(size_t length, name* a) { \
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
#define i_array_free(a) free((a.start))
#define db_id_t uint64_t
#define db_type_id_t uint16_t
#define db_ordinal_t uint32_t
#define db_id_mask UINT64_MAX
#define db_type_id_mask UINT16_MAX
#define db_data_len_t uint32_t
#define db_name_len_t uint8_t
#define db_name_len_max UINT8_MAX
#define db_data_len_max UINT32_MAX
#define db_fields_len_t uint8_t
#define db_indices_len_t uint8_t
#define db_count_t uint32_t
#define db_batch_len 100
typedef struct {
  db_id_t left;
  db_id_t right;
  db_id_t label;
  db_ordinal_t ordinal;
} db_relation_t;
typedef struct {
  db_id_t id;
  void* data;
  size_t size;
} db_record_t;
i_array_declare_type(db_ids_t, db_id_t);
i_array_declare_type(db_records_t, db_record_t);
i_array_declare_type(db_relations_t, db_relation_t);
#define db_ids_add i_array_add
#define db_ids_clear i_array_clear
#define db_ids_forward i_array_forward
#define db_ids_free i_array_free
#define db_ids_get i_array_get
#define db_ids_get_at i_array_get_at
#define db_ids_in_range i_array_in_range
#define db_ids_length i_array_length
#define db_ids_max_length i_array_max_length
#define db_ids_remove i_array_remove
#define db_ids_rewind i_array_rewind
#define db_ids_set_null i_array_set_null
#define db_relations_add i_array_add
#define db_relations_clear i_array_clear
#define db_relations_forward i_array_forward
#define db_relations_free i_array_free
#define db_relations_get i_array_get
#define db_relations_get_at i_array_get_at
#define db_relations_in_range i_array_in_range
#define db_relations_length i_array_length
#define db_relations_max_length i_array_max_length
#define db_relations_remove i_array_remove
#define db_relations_rewind i_array_rewind
#define db_relations_set_null i_array_set_null
#define db_records_add i_array_add
#define db_records_clear i_array_clear
#define db_records_forward i_array_forward
#define db_records_free i_array_free
#define db_records_get i_array_get
#define db_records_get_at i_array_get_at
#define db_records_in_range i_array_in_range
#define db_records_length i_array_length
#define db_records_max_length i_array_max_length
#define db_records_remove i_array_remove
#define db_records_rewind i_array_rewind
#define db_records_set_null i_array_set_null
#define boolean uint8_t
#define db_size_relation_data (sizeof(db_ordinal_t) + sizeof(db_id_t))
#define db_size_relation_key (2 * sizeof(db_id_t))
#define db_null 0
#define db_size_element_id (sizeof(db_id_t) - sizeof(db_type_id_t))
#define db_field_type_t uint8_t
#define db_field_type_binary 1
#define db_field_type_string 3
#define db_field_type_float32 4
#define db_field_type_float64 6
#define db_field_type_int16 80
#define db_field_type_int32 112
#define db_field_type_int64 144
#define db_field_type_int8 48
#define db_field_type_string16 66
#define db_field_type_string32 98
#define db_field_type_string64 130
#define db_field_type_string8 34
#define db_field_type_uint16 64
#define db_field_type_uint32 96
#define db_field_type_uint64 128
#define db_field_type_uint8 32
#define db_type_flag_virtual 1
#define db_id_type_mask (((db_id_t)(db_type_id_mask)) << (8 * db_size_element_id))
#define db_id_element_mask ~db_id_type_mask
#define db_status_set_id_goto(status_id) status_set_both_goto(db_status_group_db, status_id)
#define status_require_read(expression) \
  status = expression; \
  if (!(status_is_success || (status.id == db_status_id_notfound))) { \
    status_goto; \
  }
#define db_status_success_if_notfound \
  if (status.id == db_status_id_notfound) { \
    status.id = status_id_success; \
  }
#define db_record_values_declare(name) db_record_values_t name = { 0, 0, 0 }
#define db_env_declare(name) db_env_t* env = 0
#define db_ids_declare(name) i_array_declare(name, db_ids_t)
#define db_relations_declare(name) i_array_declare(name, db_relations_t)
#define db_records_declare(name) i_array_declare(name, db_records_t)
#define db_type_get_by_id(env, type_id) (type_id + env->types)
#define db_type_is_virtual(type) (db_type_flag_virtual & type->flags)
#define db_record_is_virtual(env, record_id) db_type_is_virtual((db_type_get_by_id(env, (db_id_type(record_id)))))
/** convert id and type-id to db-id-t to be able to pass c literals which might be initialised with some other type */
#define db_id_add_type(id, type_id) (db_id_element(((db_id_t)(id))) | (((db_id_t)(type_id)) << (8 * db_size_element_id)))
/** get the type id part from a record id. a record id without element id */
#define db_id_type(id) (id >> (8 * db_size_element_id))
/** get the element id part from a record id. a record id without type id */
#define db_id_element(id) (db_id_element_mask & id)
/** create a virtual record, which is a db-id-t */
#define db_record_virtual_from_uint(type_id, data) db_id_add_type(data, type_id)
#define db_record_virtual_from_int db_record_virtual_from_uint
/** get the data associated with a virtual record as a db-id-t
    this only works because the target type should be equal or smaller than db-size-id-element */
#define db_record_virtual_data(id, type_name) *((type_name*)(&id))
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
#define db_relation_selection_declare(name) \
  /* declare so that *-finish succeeds even if it has not yet been initialised. \
  for having cleanup tasks at one place like with a goto exit label */ \
  db_relation_selection_t name; \
  name.cursor = 0; \
  name.cursor_2 = 0; \
  name.options = 0; \
  name.ids_set = 0
#define db_record_selection_declare(name) \
  db_record_selection_t name; \
  name.cursor = 0
#define db_index_selection_declare(name) \
  db_index_selection_t name; \
  name.cursor = 0
#define db_record_index_selection_declare(name) \
  db_record_index_selection_t name; \
  name.records_cursor = 0; \
  name.index_selection.cursor = 0
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
  db_status_id_type_field_order,
  db_status_id_last };
enum { db_status_group_db,
  db_status_group_lmdb,
  db_status_group_libc,
  db_status_group_last };
typedef struct {
  uint8_t* name;
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
  uint8_t flags;
  db_type_id_t id;
  struct db_index_t* indices;
  db_indices_len_t indices_len;
  size_t indices_size;
  uint8_t* name;
  db_id_t sequence;
} db_type_t;
typedef struct db_index_t {
  MDB_dbi dbi;
  db_fields_len_t* fields;
  db_fields_len_t fields_len;
  db_type_t* type;
} db_index_t;
typedef struct {
  MDB_dbi dbi_records;
  MDB_dbi dbi_relation_ll;
  MDB_dbi dbi_relation_lr;
  MDB_dbi dbi_relation_rl;
  MDB_dbi dbi_system;
  MDB_env* mdb_env;
  boolean is_open;
  uint8_t* root;
  pthread_mutex_t mutex;
  uint32_t maxkeysize;
  uint32_t format;
  db_type_t* types;
  db_type_id_t types_len;
} db_env_t;
typedef struct {
  MDB_txn* mdb_txn;
  db_env_t* env;
} db_txn_t;
typedef struct {
  MDB_stat system;
  MDB_stat records;
  MDB_stat relation_lr;
  MDB_stat relation_rl;
  MDB_stat relation_ll;
} db_statistics_t;
typedef struct {
  boolean is_read_only;
  size_t maximum_size;
  db_count_t maximum_reader_count;
  db_count_t maximum_db_count;
  boolean filesystem_has_ordered_writes;
  uint_least32_t env_open_flags;
  uint16_t file_permissions;
} db_open_options_t;
typedef db_ordinal_t (*db_relation_ordinal_generator_t)(void*);
typedef struct {
  db_ordinal_t min;
  db_ordinal_t max;
} db_ordinal_condition_t;
typedef struct {
  db_data_len_t size;
  void* data;
} db_record_value_t;
typedef struct {
  db_record_value_t* data;
  db_fields_len_t extent;
  db_type_t* type;
} db_record_values_t;
typedef boolean (*db_record_matcher_t)(db_type_t*, db_record_t, void*);
typedef struct {
  MDB_cursor* cursor;
} db_index_selection_t;
typedef struct {
  db_index_selection_t index_selection;
  MDB_cursor* records_cursor;
} db_record_index_selection_t;
typedef struct {
  MDB_cursor* cursor;
  db_record_matcher_t matcher;
  void* matcher_state;
  uint8_t options;
  db_type_t* type;
} db_record_selection_t;
typedef struct {
  MDB_cursor* restrict cursor;
  MDB_cursor* restrict cursor_2;
  db_ids_t left;
  db_ids_t right;
  db_ids_t label;
  void* ids_set;
  db_ordinal_condition_t* ordinal;
  uint8_t options;
  void* reader;
} db_relation_selection_t;
typedef status_t (*db_relation_reader_t)(db_relation_selection_t*, db_count_t, db_relations_t*);
void db_open_options_set_defaults(db_open_options_t* a);
status_t db_env_new(db_env_t** result);
status_t db_statistics(db_txn_t txn, db_statistics_t* result);
void db_close(db_env_t* env);
status_t db_open(uint8_t* root, db_open_options_t* options, db_env_t* env);
db_field_t* db_type_field_get(db_type_t* type, uint8_t* name);
db_type_t* db_type_get(db_env_t* env, uint8_t* name);
status_t db_type_create(db_env_t* env, uint8_t* name, db_field_t* fields, db_fields_len_t fields_len, uint8_t flags, db_type_t** result);
status_t db_type_delete(db_env_t* env, db_type_id_t id);
uint8_t db_field_type_size(uint8_t a);
uint8_t* db_status_description(status_t a);
uint8_t* db_status_name(status_t a);
uint8_t* db_status_group_id_to_name(status_id_t a);
status_t db_ids_new(size_t length, db_ids_t* result_ids);
status_t db_records_new(size_t length, db_records_t* result_records);
status_t db_relations_new(size_t length, db_relations_t* result_relations);
void db_records_to_ids(db_records_t records, db_ids_t* result_ids);
void db_relation_selection_finish(db_relation_selection_t* selection);
status_t db_relation_select(db_txn_t txn, db_ids_t* left, db_ids_t* right, db_ids_t* label, db_ordinal_condition_t* ordinal, db_count_t offset, db_relation_selection_t* result);
status_t db_relation_read(db_relation_selection_t* selection, db_count_t count, db_relations_t* result);
status_t db_relation_ensure(db_txn_t txn, db_ids_t left, db_ids_t right, db_ids_t label, db_relation_ordinal_generator_t ordinal_generator, void* ordinal_generator_state);
status_t db_relation_delete(db_txn_t txn, db_ids_t* left, db_ids_t* right, db_ids_t* label, db_ordinal_condition_t* ordinal);
void db_record_values_free(db_record_values_t* a);
status_t db_record_values_new(db_type_t* type, db_record_values_t* result);
void db_record_values_set(db_record_values_t* values, db_fields_len_t field_index, void* data, size_t size);
status_t db_record_values_to_data(db_record_values_t values, db_record_t* result);
status_t db_record_data_to_values(db_type_t* type, db_record_t data, db_record_values_t* result);
status_t db_record_create(db_txn_t txn, db_record_values_t values, db_id_t* result);
status_t db_record_get(db_txn_t txn, db_ids_t ids, db_records_t* result_records);
status_t db_record_delete(db_txn_t txn, db_ids_t ids);
status_t db_record_delete_type(db_txn_t txn, db_type_id_t type_id);
db_record_value_t db_record_ref(db_type_t* type, db_record_t record, db_fields_len_t field);
status_t db_record_select(db_txn_t txn, db_type_t* type, db_count_t offset, db_record_matcher_t matcher, void* matcher_state, db_record_selection_t* result_selection);
status_t db_record_read(db_record_selection_t selection, db_count_t count, db_records_t* result_records);
status_t db_record_skip(db_record_selection_t selection, db_count_t count);
void db_record_selection_finish(db_record_selection_t* selection);
status_t db_record_update(db_txn_t txn, db_id_t id, db_record_values_t values);
db_id_t db_record_virtual_from_any(db_type_id_t type_id, void* data, uint8_t data_size);
status_t db_txn_write_begin(db_txn_t* a);
status_t db_txn_begin(db_txn_t* a);
status_t db_txn_commit(db_txn_t* a);
void db_txn_abort(db_txn_t* a);
status_t db_txn_begin_child(db_txn_t parent_txn, db_txn_t* a);
status_t db_txn_write_begin_child(db_txn_t parent_txn, db_txn_t* a);
db_index_t* db_index_get(db_type_t* type, db_fields_len_t* fields, db_fields_len_t fields_len);
status_t db_index_create(db_env_t* env, db_type_t* type, db_fields_len_t* fields, db_fields_len_t fields_len, db_index_t** result_index);
status_t db_index_delete(db_env_t* env, db_index_t* index);
status_t db_index_rebuild(db_env_t* env, db_index_t* index);
status_t db_index_read(db_index_selection_t selection, db_count_t count, db_ids_t* result_ids);
void db_index_selection_finish(db_index_selection_t* selection);
status_t db_index_select(db_txn_t txn, db_index_t index, db_record_values_t values, db_index_selection_t* result);
status_t db_record_index_read(db_record_index_selection_t selection, db_count_t count, db_records_t* result_records);
status_t db_record_index_select(db_txn_t txn, db_index_t index, db_record_values_t values, db_record_index_selection_t* result);
void db_record_index_selection_finish(db_record_index_selection_t* selection);