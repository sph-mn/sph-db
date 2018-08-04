/* secondary api for dealing with internals */
/* imht-set is used for example for matching ids in graph-read */
#define imht_set_key_t db_id_t
#include <stdlib.h>
#include <inttypes.h>
#ifndef imht_set_key_t
#define imht_set_key_t uint64_t
#endif
#ifndef imht_set_can_contain_zero
#define imht_set_can_contain_zero 1
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
    if (min_size <= *primes) {
      return ((*primes));
    } else {
      primes = (1 + primes);
    };
  };
  if (min_size <= *primes) {
    return ((*primes));
  };
  return ((1 | min_size));
};
uint8_t imht_set_create(size_t min_size, imht_set_t** result) {
  *result = malloc(sizeof(imht_set_t));
  if (!*result) {
    return (0);
  };
  min_size = imht_set_calculate_hash_table_size(min_size);
  (**result).content = calloc(min_size, sizeof(imht_set_key_t));
  (**result).size = min_size;
  return (((*result)->content ? 1 : 0));
};
void imht_set_destroy(imht_set_t* a) {
  if (a) {
    free((a->content));
    free(a);
  };
};
#if imht_set_can_contain_zero
#define imht_set_hash(value, hash_table) \
  (value ? (1 + (value % (hash_table.size - 1))) : 0)
#else
#define imht_set_hash(value, hash_table) (value % hash_table.size)
#endif
/** returns the address of the element in the set, 0 if it was not found.
  caveat: if imht-set-can-contain-zero is defined, which is the default,
  pointer-geterencing a returned address for the found value 0 will return 1
  instead */
imht_set_key_t* imht_set_find(imht_set_t* a, imht_set_key_t value) {
  imht_set_key_t* h = (a->content + imht_set_hash(value, (*a)));
  if (*h) {
#if imht_set_can_contain_zero
    if ((*h == value) || (0 == value)) {
      return (h);
    };
#else
    if (*h == value) {
      return (h);
    };
#endif
    imht_set_key_t* content_end = (a->content + (a->size - 1));
    imht_set_key_t* h2 = (1 + h);
    while ((h2 < content_end)) {
      if (!*h2) {
        return (0);
      } else {
        if (value == *h2) {
          return (h2);
        };
      };
      h2 = (1 + h2);
    };
    if (!*h2) {
      return (0);
    } else {
      if (value == *h2) {
        return (h2);
      };
    };
    h2 = a->content;
    while ((h2 < h)) {
      if (!*h2) {
        return (0);
      } else {
        if (value == *h2) {
          return (h2);
        };
      };
      h2 = (1 + h2);
    };
  };
  return (0);
};
#define imht_set_contains(a, value) ((0 == imht_set_find(a, value)) ? 0 : 1)
/** returns 1 if the element was removed, 0 if it was not found */
uint8_t imht_set_remove(imht_set_t* a, imht_set_key_t value) {
  imht_set_key_t* value_address = imht_set_find(a, value);
  if (value_address) {
    *value_address = 0;
    return (1);
  } else {
    return (0);
  };
};
/** returns the address of the added or already included element, 0 if there is
 * no space left in the set */
imht_set_key_t* imht_set_add(imht_set_t* a, imht_set_key_t value) {
  imht_set_key_t* h = (a->content + imht_set_hash(value, (*a)));
  if (*h) {
#if imht_set_can_contain_zero
    if ((value == *h) || (0 == value)) {
      return (h);
    };
#else
    if (value == *h) {
      return (h);
    };
#endif
    imht_set_key_t* content_end = (a->content + (a->size - 1));
    imht_set_key_t* h2 = (1 + h);
    while (((h2 <= content_end) && *h2)) {
      h2 = (1 + h2);
    };
    if (h2 > content_end) {
      h2 = a->content;
      while (((h2 < h) && *h2)) {
        h2 = (1 + h2);
      };
      if (h2 == h) {
        return (0);
      } else {
#if imht_set_can_contain_zero
        *h2 = ((0 == value) ? 1 : value);
#else
        *h2 = value;
#endif
      };
    } else {
#if imht_set_can_contain_zero
      *h2 = ((0 == value) ? 1 : value);
#else
      *h2 = value;
#endif
    };
  } else {
#if imht_set_can_contain_zero
    *h = ((0 == value) ? 1 : value);
#else
    *h = value;
#endif
    return (h);
  };
};
#define db_system_label_format 0
#define db_system_label_type 1
#define db_system_label_index 2
#define db_selection_flag_skip 1
#define db_graph_selection_flag_is_set_left 2
#define db_graph_selection_flag_is_set_right 4
#define db_id_type_mask \
  (((db_id_t)(db_type_id_mask)) << (8 * db_size_element_id))
#define db_id_element_mask ~db_id_type_mask
#define db_type_id_limit db_type_id_mask
#define db_element_id_limit db_id_element_mask
#define db_type_flag_virtual 1
#define db_size_type_id_max 16
#define db_size_system_label 1
#define db_pointer_to_id_at(a, index) *(index + ((db_id_t*)(a)))
#define db_pointer_to_id(a) *((db_id_t*)(a))
#define db_field_type_is_fixed(a) !(1 & a)
#define db_system_key_label(a) *((ui8*)(a))
#define db_system_key_id(a) \
  *((db_type_id_t*)((db_size_system_label + ((ui8*)(a)))))
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
  *(len + variable) = 0
#define db_calloc(variable, count, size) \
  variable = calloc(count, size); \
  db_status_memory_error_if_null(variable)
#define db_realloc(variable, variable_temp, size) \
  variable_temp = realloc(variable, size); \
  db_status_memory_error_if_null(variable_temp); \
  variable = variable_temp
#define db_graph_data_to_id(a) db_pointer_to_id((1 + ((db_ordinal_t*)(a))))
#define db_graph_data_to_ordinal(a) *((db_ordinal_t*)(a))
#define db_graph_data_set_id(a, value) db_graph_data_to_id(a) = value
#define db_graph_data_set_ordinal(a, value) db_graph_data_to_ordinal(a) = value
#define db_graph_data_set_both(a, ordinal, id) \
  db_graph_data_set_ordinal(ordinal); \
  db_graph_data_set_id(id)
void db_debug_log_ids(db_ids_t a);
void db_debug_log_ids_set(imht_set_t a);
void db_debug_display_graph_records(db_graph_records_t records);
status_t db_debug_count_all_btree_entries(db_txn_t txn, db_count_t* result);
status_t db_debug_display_btree_counts(db_txn_t txn);
status_t db_debug_display_content_graph_lr(db_txn_t txn);
status_t db_debug_display_content_graph_rl(db_txn_t txn);
status_t db_index_key(db_env_t* env,
  db_index_t index,
  db_node_values_t values,
  void** result_data,
  size_t* result_size);
status_t
db_indices_entry_ensure(db_txn_t txn, db_node_values_t values, db_id_t id);
status_t db_index_name(db_type_id_t type_id,
  db_fields_len_t* fields,
  db_fields_len_t fields_len,
  ui8** result,
  size_t* result_size);
status_t
db_indices_entry_delete(db_txn_t txn, db_node_values_t values, db_id_t id);