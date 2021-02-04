
/* this file is for declarations and macros needed to use sph-db as a shared library */
#include <inttypes.h>
#include <math.h>
#include <pthread.h>
#include <lmdb.h>

/* compile-time configuration start */

#define db_id_t uint64_t
#define db_type_id_t uint16_t
#define db_ordinal_t uint16_t
#define db_id_mask UINT64_MAX
#define db_type_id_mask UINT16_MAX
#define db_name_len_t uint8_t
#define db_name_len_max UINT8_MAX
#define db_fields_len_t uint16_t
#define db_indices_len_t uint16_t
#define db_count_t uint32_t
#define db_batch_len 100

/* compile-time configuration end */
#include <stdio.h>

/** writes values with current routine name and line info to standard output.
    example: (debug-log "%d" 1)
    otherwise like printf */
#define debug_log(format, ...) fprintf(stdout, "%s:%d " format "\n", __func__, __LINE__, __VA_ARGS__)

/** display current function name and given number.
    example call: (debug-trace 1) */
#define debug_trace(n) fprintf(stdout, "%s %d\n", __func__, n)
/* return status as integer code with group identifier
for exception handling with a local variable and a goto label
status id 0 is success, everything else can be considered a special case or failure
status ids are signed integers for compatibility with error return codes from other existing libraries
group ids are strings used to categorise sets of errors codes from different libraries for example */
typedef struct {
  int id;
  uint8_t* group;
} status_t;
#define status_id_success 0
#define status_group_undefined ((uint8_t*)(""))
#define status_declare status_t status = { status_id_success, status_group_undefined }
#define status_is_success (status_id_success == status.id)
#define status_is_failure !status_is_success
#define status_return return (status)
#define status_set(group_id, status_id) \
  status.group = ((uint8_t*)(group_id)); \
  status.id = status_id
#define status_set_goto(group_id, status_id) \
  status_set(group_id, status_id); \
  goto exit
#define status_require(expression) \
  status = expression; \
  if (status_is_failure) { \
    goto exit; \
  }
#define status_i_require(expression) \
  status.id = expression; \
  if (status_is_failure) { \
    goto exit; \
  }
/* "iteration array" - an array with variable length content that makes iteration easier to code.
   saves the size argument that usually has to be passed with arrays and saves the declaration of index counter variables.
   the data structure consists of only 4 pointers in a struct.
   most bindings are generic macros that will work on any i-array type. i-array-add and i-array-forward go from left to right.
   examples:
     i_array_declare_type(my_type, int);
     my_type_t a;
     if(my_type_new(4, &a)) {
       // memory allocation error
     }
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
     .end: start + max-length. (last-index + 1) of the allocated array
     .start: the beginning of the allocated array and used for rewind and free */
#define i_array_declare_type(name, element_type) \
  typedef struct { \
    element_type* current; \
    element_type* unused; \
    element_type* end; \
    element_type* start; \
  } name##_t; \
  uint8_t name##_new_custom(size_t length, void* (*alloc)(size_t), name##_t* a) { \
    element_type* start; \
    start = alloc((length * sizeof(element_type))); \
    if (!start) { \
      return (1); \
    }; \
    a->start = start; \
    a->current = start; \
    a->unused = start; \
    a->end = (length + start); \
    return (0); \
  } \
\
  /** return 0 on success, 1 for memory allocation error */ \
  uint8_t name##_new(size_t length, name##_t* a) { return ((name##_new_custom(length, malloc, a))); } \
  uint8_t name##_resize_custom(name##_t* a, size_t new_length, void* (*realloc)(void*, size_t)) { \
    element_type* start = realloc((a->start), (new_length * sizeof(element_type))); \
    if (!start) { \
      return (1); \
    }; \
    a->current = (start + (a->current - a->start)); \
    a->unused = (start + (a->unused - a->start)); \
    a->start = start; \
    a->end = (new_length + start); \
    return (0); \
  } \
\
  /** return 0 on success, 1 for realloc error */ \
  uint8_t name##_resize(name##_t* a, size_t new_length) { return ((name##_resize_custom(a, new_length, realloc))); }

/** define so that in-range is false, length is zero and free doesnt fail.
     can be used to create empty/null i-arrays */
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
#define i_array_get_index(a) (a.current - a.start)
#define i_array_forward(a) a.current = (1 + a.current)
#define i_array_rewind(a) a.current = a.start
#define i_array_clear(a) a.unused = a.start
#define i_array_remove(a) a.unused = (a.unused - 1)
#define i_array_length(a) (a.unused - a.start)
#define i_array_max_length(a) (a.end - a.start)
#define i_array_free(a) free((a.start))

/** move a standard array into an i-array
     sets source as data array to use, with the first count number of slots used.
     source will not be copied but used as is, and i-array-free would free it.
     # example with a stack allocated array
     int other_array[4] = {1, 2, 0, 0};
     my_type a;
     i_array_take(a, other_array, 4, 2); */
#define i_array_take(a, source, size, count) \
  a->start = source; \
  a->current = source; \
  a->unused = (count + source); \
  a->end = (size + source)
/* a macro that defines set data types for arbitrary value types,
using linear probing for collision resolve,
hash and equal functions are customisable by defining macros and re-including the source.
sph-set-empty-value and sph-set-true-value need to be set for values types other than integers.
when sph-set-allow-empty-value is 1, then the empty value is stored at the first index of .values and the other values start at index 1.
the default hash functions work on integers.
compared to hashtable.c, this uses less than half of the space and operations are faster (about 20% in first tests) */
#include <stdlib.h>
#include <inttypes.h>

#define sph_set_hash_integer(value, hashtable_size) (value % hashtable_size)
#define sph_set_equal_integer(value_a, value_b) (value_a == value_b)

/* sph-set-true-value is used only at index 0 for the empty-value */

#ifndef sph_set_size_factor
#define sph_set_size_factor 2
#endif
#ifndef sph_set_hash
#define sph_set_hash sph_set_hash_integer
#endif
#ifndef sph_set_equal
#define sph_set_equal sph_set_equal_integer
#endif
#ifndef sph_set_allow_empty_value
#define sph_set_allow_empty_value 1
#endif
#ifndef sph_set_empty_value
#define sph_set_empty_value 0
#endif
#ifndef sph_set_true_value
#define sph_set_true_value 1
#endif
uint32_t sph_set_primes[] = { 53, 97, 193, 389, 769, 1543, 3079, 6151, 12289, 24593, 49157, 98317, 196613, 393241, 786433, 1572869, 3145739, 6291469, 12582917, 25165843, 50331653, 100663319, 201326611, 402653189, 805306457, 1610612741 };
uint32_t* sph_set_primes_end = (sph_set_primes + 25);
size_t sph_set_calculate_size(size_t min_size) {
  min_size = (sph_set_size_factor * min_size);
  uint32_t* primes;
  for (primes = sph_set_primes; (primes <= sph_set_primes_end); primes += 1) {
    if (min_size <= *primes) {
      return ((*primes));
    };
  };
  /* if no prime has been found, make size at least an odd number */
  return ((1 | min_size));
}
#if sph_set_allow_empty_value
#define sph_set_get_part_1 \
  if (sph_set_equal(sph_set_empty_value, value)) { \
    return ((sph_set_equal(sph_set_true_value, (*(a.values))) ? a.values : 0)); \
  }; \
  hash_i = (1 + sph_set_hash(value, (a.size - 1)));
#define sph_set_get_part_2 i = 1
#define sph_set_add_part_1 \
  if (sph_set_equal(sph_set_empty_value, value)) { \
    *(a.values) = sph_set_true_value; \
    return ((a.values)); \
  }; \
  hash_i = (1 + sph_set_hash(value, (a.size - 1)));
#define sph_set_add_part_2 i = 1
#define sph_set_new_part_1 min_size += 1
#else
#define sph_set_get_part_1 hash_i = sph_set_hash(value, (a.size))
#define sph_set_get_part_2 i = 0
#define sph_set_add_part_1 hash_i = sph_set_hash(value, (a.size))
#define sph_set_add_part_2 i = 0
#define sph_set_new_part_1 0
#endif
#define sph_set_declare_type(name, value_type) \
  typedef struct { \
    size_t size; \
    value_type* values; \
  } name##_t; \
  uint8_t name##_new(size_t min_size, name##_t* result) { \
    value_type* values; \
    min_size = sph_set_calculate_size(min_size); \
    sph_set_new_part_1; \
    values = calloc(min_size, (sizeof(value_type))); \
    if (!values) { \
      return (1); \
    }; \
    (*result).values = values; \
    (*result).size = min_size; \
    return (0); \
  } \
  void name##_free(name##_t a) { free((a.values)); } \
\
  /** returns the address of the value or 0 if it was not found. \
        if sph_set_allow_empty_value is true and the value is included, then address points to a sph_set_true_value */ \
  value_type* name##_get(name##_t a, value_type value) { \
    size_t i; \
    size_t hash_i; \
    sph_set_get_part_1; \
    i = hash_i; \
    while ((i < a.size)) { \
      if (sph_set_equal(sph_set_empty_value, ((a.values)[i]))) { \
        return (0); \
      } else { \
        if (sph_set_equal(value, ((a.values)[i]))) { \
          return ((i + a.values)); \
        }; \
      }; \
      i += 1; \
    }; \
    /* wraps over */ \
    sph_set_get_part_2; \
    while ((i < hash_i)) { \
      if (sph_set_equal(sph_set_empty_value, ((a.values)[i]))) { \
        return (0); \
      } else { \
        if (sph_set_equal(value, ((a.values)[i]))) { \
          return ((i + a.values)); \
        }; \
      }; \
      i += 1; \
    }; \
    return (0); \
  } \
\
  /** returns the address of the value or 0 if no space is left */ \
  value_type* name##_add(name##_t a, value_type value) { \
    size_t i; \
    size_t hash_i; \
    sph_set_add_part_1; \
    i = hash_i; \
    while ((i < a.size)) { \
      if (sph_set_equal(sph_set_empty_value, ((a.values)[i]))) { \
        (a.values)[i] = value; \
        return ((i + a.values)); \
      }; \
      i += 1; \
    }; \
    /* wraps over */ \
    sph_set_add_part_2; \
    while ((i < hash_i)) { \
      if (sph_set_equal(sph_set_empty_value, ((a.values)[i]))) { \
        (a.values)[i] = value; \
        return ((i + a.values)); \
      }; \
      i += 1; \
    }; \
    return (0); \
  } \
\
  /** returns 0 if the element was removed, 1 if it was not found */ \
  uint8_t name##_remove(name##_t a, value_type value) { \
    value_type* v = name##_get(a, value); \
    if (v) { \
      *v = sph_set_empty_value; \
      return (0); \
    } else { \
      return (1); \
    }; \
  }
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
i_array_declare_type(db_ids, db_id_t)
  i_array_declare_type(db_records, db_record_t)
    i_array_declare_type(db_relations, db_relation_t)
      sph_set_declare_type(db_id_set, db_id_t)

#define db_format_version 1
#define db_status_group_sph "sph"
#define db_status_group_db "sph-db"
#define db_status_group_lmdb "lmdb"
#define db_status_group_libc "libc"
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
#define db_field_type_t int8_t
#define db_field_type_string64 -8
#define db_field_type_string32 -7
#define db_field_type_string16 -6
#define db_field_type_string8 -5
#define db_field_type_binary64 -4
#define db_field_type_binary32 -3
#define db_field_type_binary16 -2
#define db_field_type_binary8 -1
#define db_field_type_binary8f 1
#define db_field_type_binary16f 2
#define db_field_type_binary32f 3
#define db_field_type_binary64f 4
#define db_field_type_binary128f 5
#define db_field_type_binary256f 6
#define db_field_type_uint8f 8
#define db_field_type_uint16f 9
#define db_field_type_uint32f 10
#define db_field_type_uint64f 11
#define db_field_type_uint128f 12
#define db_field_type_uint256f 13
#define db_field_type_int8f 15
#define db_field_type_int16f 16
#define db_field_type_int32f 17
#define db_field_type_int64f 18
#define db_field_type_int128f 19
#define db_field_type_int256f 20
#define db_field_type_string8f 22
#define db_field_type_string16f 23
#define db_field_type_string32f 24
#define db_field_type_string64f 25
#define db_field_type_string128f 26
#define db_field_type_string256f 27
#define db_field_type_float32f 29
#define db_field_type_float64f 30
#define db_id_type_mask ((db_id_t)(db_type_id_mask))
#define db_id_element_mask ~db_id_type_mask
#define db_status_set_id_goto(status_id) status_set_goto(db_status_group_db, status_id)
#define status_require_read(expression) \
  status = expression; \
  if (!(status_is_success || (status.id == db_status_id_notfound))) { \
    goto exit; \
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

/** convert id and type-id to db-id-t to be able to pass c literals which might be initialised with some other type.
    string type part from id with db-id-element in case there are type bits set after for example typecasting from a smaller datatype */
#define db_id_add_type(id, type_id) (db_id_type(((db_id_t)(type_id))) | (((db_id_t)(id)) << (8 * sizeof(db_type_id_t))))

/** get the type id part from a record id. a record id minus element id */
#define db_id_type(id) (db_id_type_mask & id)

/** get the element id part from a record id. a record id minus type id */
#define db_id_element(id) (id >> (8 * sizeof(db_type_id_t)))
#define db_txn_declare(env, name) db_txn_t name = { 0, env }
#define db_txn_abort_if_active(a) \
  if (a.mdb_txn) { \
    db_txn_abort((&a)); \
  }
#define db_txn_is_active(a) (a.mdb_txn ? 1 : 0)
#define db_field_set(a, a_type, a_name) \
  a.type = a_type; \
  a.name = a_name

/** set so that *-finish succeeds even if it has not yet been initialised.
      for having cleanup tasks at one place like with a goto exit label */
#define db_relation_selection_set_null(name) \
  name.cursor = 0; \
  name.cursor_2 = 0; \
  name.options = 0; \
  name.id_set = 0
#define db_relation_selection_declare(name) \
  db_relation_selection_t name; \
  db_relation_selection_set_null(name)
#define db_record_selection_set_null(name) name.cursor = 0
#define db_record_selection_declare(name) \
  db_record_selection_t name; \
  db_record_selection_set_null(name)
#define db_index_selection_set_null(name) name.cursor = 0
#define db_index_selection_declare(name) \
  db_index_selection_t name; \
  db_index_selection_set_null(name)
#define db_record_index_selection_set_null(name) \
  name.records_cursor = 0; \
  name.index_selection.cursor = 0
#define db_record_index_selection_declare(name) \
  db_record_index_selection_t name; \
  db_record_index_selection_set_null(name)

/* virtual records */

#define db_type_flag_virtual 1
#define db_record_virtual_data_uint(id, type_name) ((type_name)(db_id_element(id)))
#define db_record_virtual_data_int db_record_virtual_data_uint
#define db_record_virtual_from_int db_record_virtual_from_uint
#define db_type_is_virtual(type) (db_type_flag_virtual & type->flags)
#define db_record_is_virtual(env, record_id) db_type_is_virtual((db_type_get_by_id(env, (db_id_type(record_id)))))
#define db_record_virtual_from_uint(type_id, data) db_id_add_type(data, type_id)
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
          db_status_id_invalid_field_type,
          db_status_id_last };
typedef uint8_t db_field_type_size_t;
typedef struct {
  uint8_t* name;
  db_field_type_t type;
  db_fields_len_t offset;
  db_field_type_size_t size;
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
  size_t size;
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
  db_id_set_t* id_set;
  db_ordinal_condition_t ordinal;
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
uint8_t db_field_type_size(db_field_type_t a);
uint8_t* db_status_description(status_t a);
uint8_t* db_status_name(status_t a);
void db_records_to_ids(db_records_t records, db_ids_t* result_ids);
void db_relation_selection_finish(db_relation_selection_t* selection);
status_t db_relation_select(db_txn_t txn, db_ids_t* left, db_ids_t* right, db_ids_t* label, db_ordinal_condition_t* ordinal, db_relation_selection_t* result);
status_t db_relation_read(db_relation_selection_t* selection, db_count_t count, db_relations_t* result);
status_t db_relation_skip(db_relation_selection_t* selection, db_count_t count);
status_t db_relation_ensure(db_txn_t txn, db_ids_t left, db_ids_t right, db_ids_t label, db_relation_ordinal_generator_t ordinal_generator, void* ordinal_generator_state);
status_t db_relation_delete(db_txn_t txn, db_ids_t* left, db_ids_t* right, db_ids_t* label, db_ordinal_condition_t* ordinal);
void db_record_values_free(db_record_values_t* a);
status_t db_record_values_new(db_type_t* type, db_record_values_t* result);
status_t db_record_values_set(db_record_values_t* values, db_fields_len_t field_index, void* data, size_t size);
status_t db_record_values_to_data(db_record_values_t values, db_record_t* result);
status_t db_record_data_to_values(db_type_t* type, db_record_t data, db_record_values_t* result);
status_t db_record_create(db_txn_t txn, db_record_values_t values, db_id_t* result);
status_t db_record_get(db_txn_t txn, db_ids_t ids, boolean match_all, db_records_t* result_records);
status_t db_record_delete(db_txn_t txn, db_ids_t ids);
status_t db_record_delete_type(db_txn_t txn, db_type_id_t type_id);
db_record_value_t db_record_ref(db_type_t* type, db_record_t record, db_fields_len_t field);
status_t db_record_select(db_txn_t txn, db_type_t* type, db_record_matcher_t matcher, void* matcher_state, db_record_selection_t* result_selection);
status_t db_record_read(db_record_selection_t selection, db_count_t count, db_records_t* result_records);
status_t db_record_skip(db_record_selection_t selection, db_count_t count);
void db_record_selection_finish(db_record_selection_t* selection);
status_t db_record_update(db_txn_t txn, db_id_t id, db_record_values_t values);
void* db_record_virtual_data(db_id_t id, void* result, size_t result_size);
db_id_t db_record_virtual(db_type_id_t type_id, void* data, size_t data_size);
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