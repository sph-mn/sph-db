
#include <stdio.h>
#include <stdlib.h>
#include <errno.h>
#include <pthread.h>
#include <sph-db/sph/string.h>
#include <sph-db/sph/filesystem.h>
#include <sph-db/sph/memory.c>
#include "../sph-db/sph-db.h"
#include "../sph-db/sph-db-extra.h"
#include "../sph-db/lmdb.c"

#define test_helper_assert(description, expression) \
  if (!expression) { \
    printf("%s failed\n", description); \
    status.id = 1; \
    goto exit; \
  }
#define test_helper_db_root "/tmp/sph-db-test"
#define test_helper_path_data test_helper_db_root "/data"
#define test_helper_test_one(f, env) \
  printf("%s\n", #f); \
  status_require((test_helper_reset(env, 0))); \
  status_require((f(env)))

/** define a function that searches for an id in an array of relations at field */
#define test_helper_define_relations_contains_at(field_name) \
  boolean db_debug_relations_contains_at_##field_name(db_relations_t relations, db_id_t id) { \
    db_relation_t record; \
    while (sph_array_current_in_range(relations)) { \
      record = sph_array_current_get(relations); \
      if (id == record.field_name) { \
        return (1); \
      }; \
      sph_array_current_forward(relations); \
    }; \
    return (0); \
  }

/** define a function for getting a field from a relation record, to use with a function pointer */
#define test_helper_define_relation_get(field_name) \
  db_id_t test_helper_relation_get_##field_name(db_relation_t record) { return ((record.field_name)); }
typedef struct {
  db_txn_t txn;
  db_relations_t relations;
  db_ids_t e_left;
  db_ids_t e_right;
  db_ids_t e_label;
  uint32_t e_left_count;
  uint32_t e_right_count;
  uint32_t e_label_count;
  db_ids_t left;
  db_ids_t right;
  db_ids_t label;
} test_helper_relation_read_data_t;
typedef struct {
  db_env_t* env;
  uint32_t e_left_count;
  uint32_t e_right_count;
  uint32_t e_label_count;
} test_helper_relation_delete_data_t;
/** calculates the number of btree entries affected by a relation read or delete.
   assumes linearly incremented ordinal integers starting at 1 and queries for all or no ids */
uint32_t test_helper_estimate_relation_read_btree_entry_count(uint32_t e_left_count, uint32_t e_right_count, uint32_t e_label_count, db_ordinal_condition_t* ordinal) {
  uint32_t ordinal_min = 0;
  uint32_t ordinal_max = 0;
  if (ordinal) {
    ordinal_min = ordinal->min;
    ordinal_max = ordinal->max;
  };
  uint32_t label_left_count = 0;
  uint32_t left_right_count = 0;
  uint32_t right_left_count = 0;
  uint32_t ordinal_value = 1;
  uint32_t left_count = 0;
  uint32_t right_count = 0;
  uint32_t label_count = 0;
  /* the number of relations is not proportional to the number of entries in relation-ll.
       use a process similar to relation creation to correctly calculate relation-ll and ordinal dependent entries */
  while ((label_count < e_label_count)) {
    while ((left_count < e_left_count)) {
      if ((ordinal_value <= ordinal_max) && (ordinal_value >= ordinal_min)) {
        label_left_count = (1 + label_left_count);
      };
      while ((right_count < e_right_count)) {
        if ((ordinal_value <= ordinal_max) && (ordinal_value >= ordinal_min)) {
          ordinal_value = (1 + ordinal_value);
          left_right_count = (1 + left_right_count);
          right_left_count = (1 + right_left_count);
        };
        right_count = (1 + right_count);
      };
      left_count = (1 + left_count);
    };
    label_count = (1 + label_count);
  };
  return ((left_right_count + right_left_count + label_left_count));
}
status_t test_helper_display_all_relations(db_txn_t txn, uint32_t left_count, uint32_t right_count, uint32_t label_count) {
  status_declare;
  db_relation_selection_declare(selection);
  sph_array_declare(relations, db_relations_t);
  status_require((db_relations_new((left_count * right_count * label_count), (&relations))));
  status_require_read((db_relation_select(txn, 0, 0, 0, 0, (&selection))));
  status_require_read((db_relation_read((&selection), 0, (&relations))));
  printf("all ");
  db_relation_selection_finish((&selection));
  db_debug_log_relations(relations);
  db_relations_free((&relations));
exit:
  status_return;
}

/** 1101 -> "1101" */
uint8_t* test_helper_reader_suffix_integer_to_string(uint8_t a) {
  uint8_t* result = malloc(40);
  result[0] = ((8 & a) ? '1' : '0');
  result[1] = ((4 & a) ? '1' : '0');
  result[2] = ((2 & a) ? '1' : '0');
  result[3] = ((1 & a) ? '1' : '0');
  result[4] = 0;
  return (result);
}
test_helper_define_relations_contains_at(left)
  test_helper_define_relations_contains_at(right)
    test_helper_define_relations_contains_at(label)
      test_helper_define_relation_get(left)
        test_helper_define_relation_get(right)
          test_helper_define_relation_get(label)
            status_t test_helper_reset(db_env_t* env, boolean re_use) {
  status_declare;
  if (env->is_open) {
    db_close(env);
  };
  if (!re_use && file_exists(test_helper_path_data)) {
    status.id = system("rm " test_helper_path_data);
    if (status_is_failure) {
      goto exit;
    };
  };
  status_require((db_open(test_helper_db_root, 0, env)));
exit:
  status_return;
}
void test_helper_print_binary_uint64_t(uint64_t a) {
  size_t i;
  uint8_t result[65];
  *(64 + result) = 0;
  for (i = 0; (i < 64); i = (1 + i)) {
    *(i + result) = (((((uint64_t)(1)) << i) & a) ? '1' : '0');
  };
  printf("%s\n", result);
}
void test_helper_display_array_uint8_t(uint8_t* a, size_t size) {
  size_t i;
  for (i = 0; (i < size); i += 1) {
    printf("%" PRIu8 " ", (a[i]));
  };
  printf("\n");
}
boolean db_ids_contains(db_ids_t ids, db_id_t id) {
  while (sph_array_current_in_range(ids)) {
    if (id == sph_array_current_get(ids)) {
      return (1);
    };
    sph_array_current_forward(ids);
  };
  return (0);
}
status_t db_ids_reverse(db_ids_t a, db_ids_t* result) {
  status_declare;
  db_ids_t temp;
  status_require((db_ids_new((a.used), (&temp))));
  while (sph_array_current_in_range(a)) {
    sph_array_add(temp, (sph_array_current_get(a)));
    sph_array_current_forward(a);
  };
  *result = temp;
exit:
  status_return;
}

/** create a new type with four fields, fixed and variable length, for testing */
status_t test_helper_create_type_1(db_env_t* env, db_type_t** result) {
  status_declare;
  db_field_t fields[4];
  db_field_set((fields[0]), db_field_type_uint8f, "test-field-1");
  db_field_set((fields[1]), db_field_type_int16f, "test-field-2");
  db_field_set((fields[2]), db_field_type_string8, "test-field-3");
  db_field_set((fields[3]), db_field_type_string16, "test-field-4");
  status_require((db_type_create(env, "test-type-1", fields, 4, 0, result)));
exit:
  status_return;
}

/** create multiple record-values */
status_t test_helper_create_values_1(db_env_t* env, db_type_t* type, db_record_values_t** result_values, uint32_t* result_values_len) {
  status_declare;
  uint8_t* value_1;
  int8_t* value_2;
  uint8_t* value_3;
  uint8_t* value_4;
  db_record_values_t* values;
  status_require((sph_memory_malloc(1, ((void**)(&value_1)))));
  status_require((sph_memory_malloc(1, ((void**)(&value_2)))));
  status_require((sph_memory_malloc((2 * sizeof(db_record_values_t)), ((void**)(&values)))));
  *value_1 = 11;
  *value_2 = -128;
  status_require((sph_memory_malloc_string(3, ((char**)(&value_3)))));
  status_require((sph_memory_malloc_string(5, ((char**)(&value_4)))));
  memcpy(value_3, (&"abc"), 3);
  memcpy(value_4, (&"abcde"), 5);
  status_require((db_record_values_new(type, (0 + values))));
  status_require((db_record_values_new(type, (1 + values))));
  status_require((db_record_values_set((0 + values), 0, value_1, 1)));
  status_require((db_record_values_set((0 + values), 1, value_2, 2)));
  status_require((db_record_values_set((0 + values), 2, value_3, 3)));
  status_require((db_record_values_set((0 + values), 3, value_4, 5)));
  status_require((db_record_values_set((1 + values), 0, value_1, 1)));
  status_require((db_record_values_set((1 + values), 1, value_1, 2)));
  status_require((db_record_values_set((1 + values), 2, value_3, 3)));
  *result_values_len = 4;
  *result_values = values;
exit:
  status_return;
}

/** creates several records with the given values */
status_t test_helper_create_records_1(db_env_t* env, db_record_values_t* values, db_id_t** result_ids, uint32_t* result_len) {
  status_declare;
  db_txn_declare(env, txn);
  db_id_t* ids;
  status_require((sph_memory_malloc((4 * sizeof(db_id_t)), ((void**)(&ids)))));
  status_require((db_txn_write_begin((&txn))));
  status_require((db_record_create(txn, (values[0]), (0 + ids))));
  status_require((db_record_create(txn, (values[0]), (1 + ids))));
  status_require((db_record_create(txn, (values[1]), (2 + ids))));
  status_require((db_record_create(txn, (values[1]), (3 + ids))));
  status_require((db_txn_commit((&txn))));
  *result_ids = ids;
  *result_len = 4;
exit:
  status_return;
}

/** create only ids, without records. doesnt depend on record creation.
   especially with relation reading where order lead to lucky success results */
status_t test_helper_create_ids(db_txn_t txn, uint32_t count, db_ids_t* result) {
  status_declare;
  db_id_t id;
  db_ids_t result_temp;
  status_require((db_ids_new(count, (&result_temp))));
  while (count) {
    /* use type id zero to have small record ids for testing which are easier to debug */
    status_require((db_sequence_next((txn.env), 0, (&id))));
    sph_array_add(result_temp, id);
    count = (count - 1);
  };
  status_require((db_ids_reverse(result_temp, result)));
exit:
  db_ids_free((&result_temp));
  status_return;
}

/** merge ids from two lists into a new list, interleave at half the size of the arrays.
   result is as long as both id lists combined.
   approximately like this: 1 1 1 1 + 2 2 2 2 -> 1 1 2 1 2 1 2 2 */
status_t test_helper_interleave_ids(db_txn_t txn, db_ids_t ids_a, db_ids_t ids_b, db_ids_t* result) {
  status_declare;
  sph_array_declare(ids_result, db_ids_t);
  uint32_t target_count;
  uint32_t start_mixed;
  uint32_t start_new;
  uint32_t i;
  target_count = (ids_a.used + ids_b.used);
  start_mixed = (target_count / 4);
  start_new = (target_count - start_mixed);
  status_require((db_ids_new(target_count, (&ids_result))));
  for (i = 0; (i < target_count); i = (1 + i)) {
    if (i < start_mixed) {
      sph_array_add(ids_result, (sph_array_current_get(ids_a)));
      sph_array_current_forward(ids_a);
    } else {
      if (i < start_new) {
        if (1 & i) {
          sph_array_add(ids_result, (sph_array_current_get(ids_a)));
          sph_array_current_forward(ids_a);
        } else {
          sph_array_add(ids_result, (sph_array_current_get(ids_b)));
          sph_array_current_forward(ids_b);
        };
      } else {
        sph_array_add(ids_result, (sph_array_current_get(ids_b)));
        sph_array_current_forward(ids_b);
      };
    };
  };
  *result = ids_result;
exit:
  if (status_is_failure) {
    db_ids_free((&ids_result));
  };
  status_return;
}
uint32_t test_helper_calculate_relation_count(uint32_t left_count, uint32_t right_count, uint32_t label_count) { return ((left_count * right_count * label_count)); }
uint32_t test_helper_calculate_relation_count_from_ids(db_ids_t left, db_ids_t right, db_ids_t label) { return ((test_helper_calculate_relation_count((left.used), (right.used), (label.used)))); }

/** test if the result relations contain all filter-ids,
   and the filter-ids contain all result record values for field "name". */
status_t test_helper_relation_read_relations_validate_one(char* name, db_ids_t e_ids, db_relations_t relations) {
  status_declare;
  boolean (*contains_at)(db_relations_t, db_id_t);
  db_id_t (*record_get)(db_relation_t);
  contains_at = 0;
  record_get = 0;
  if (0 == strcmp("left", name)) {
    contains_at = db_debug_relations_contains_at_left;
    record_get = test_helper_relation_get_left;
  } else if (0 == strcmp("right", name)) {
    contains_at = db_debug_relations_contains_at_right;
    record_get = test_helper_relation_get_right;
  } else if (0 == strcmp("label", name)) {
    contains_at = db_debug_relations_contains_at_label;
    record_get = test_helper_relation_get_label;
  } else {
    printf("\n  invalid field name %s\n", name);
    status.id = 1;
    goto exit;
  };
  while (sph_array_current_in_range(relations)) {
    if (!db_ids_contains(e_ids, (record_get((sph_array_current_get(relations)))))) {
      printf("\n  result relations contain inexistant %s ids\n", name);
      status.id = 1;
      goto exit;
    };
    sph_array_current_forward(relations);
  };
  sph_array_current_rewind(relations);
  while (sph_array_current_in_range(e_ids)) {
    if (!contains_at(relations, (sph_array_current_get(e_ids)))) {
      printf("\n  %s result relations do not contain all existing-ids\n", name);
      status.id = 2;
      goto exit;
    };
    sph_array_current_forward(e_ids);
  };
exit:
  status_return;
}
status_t test_helper_relation_read_relations_validate(test_helper_relation_read_data_t data) {
  status_declare;
  status_require((test_helper_relation_read_relations_validate_one("left", (data.e_left), (data.relations))));
  status_require((test_helper_relation_read_relations_validate_one("right", (data.e_right), (data.relations))));
  status_require((test_helper_relation_read_relations_validate_one("label", (data.e_label), (data.relations))));
exit:
  status_return;
}
db_ordinal_t test_helper_default_ordinal_generator(void* ordinal_state) {
  db_ordinal_t* ordinal_pointer = ordinal_state;
  db_ordinal_t result = (1 + *ordinal_pointer);
  *ordinal_pointer = result;
  return (result);
}

/** create relations with linearly increasing ordinal starting from zero */
status_t test_helper_create_relations(db_txn_t txn, db_ids_t left, db_ids_t right, db_ids_t label) {
  status_declare;
  db_ordinal_t ordinal_state_value;
  ordinal_state_value = 0;
  status_require((db_relation_ensure(txn, left, right, label, test_helper_default_ordinal_generator, (&ordinal_state_value))));
exit:
  status_return;
}

/** assumes linearly set-plus-oneed ordinal integers starting at 1 and queries for all or no ids */
uint32_t test_helper_estimate_relation_read_result_count(uint32_t left_count, uint32_t right_count, uint32_t label_count, db_ordinal_condition_t* ordinal) {
  uint32_t count = (left_count * right_count * label_count);
  uint32_t max;
  uint32_t min;
  if (ordinal) {
    min = (ordinal->min ? (ordinal->min - 1) : 0);
    max = ordinal->max;
    ((max > count) ? (max = count) : 0);
  } else {
    min = 0;
    max = count;
  };
  return ((count - min - (count - max)));
}
status_t test_helper_relation_read_one(db_txn_t txn, test_helper_relation_read_data_t data, boolean use_left, boolean use_right, boolean use_label, boolean use_ordinal, uint32_t offset) {
  status_declare;
  db_relation_selection_declare(selection);
  db_ids_t* left_pointer;
  db_ids_t* right_pointer;
  db_ids_t* label_pointer;
  db_ordinal_t ordinal_min;
  db_ordinal_t ordinal_max;
  db_ordinal_condition_t ordinal_condition;
  db_ordinal_condition_t* ordinal;
  size_t expected_count;
  uint8_t reader_suffix;
  uint8_t* reader_suffix_string;
  ordinal_min = 2;
  ordinal_max = 5;
  ordinal_condition.min = ordinal_min;
  ordinal_condition.max = ordinal_max;
  left_pointer = (use_left ? &(data.left) : 0);
  right_pointer = (use_right ? &(data.right) : 0);
  label_pointer = (use_label ? &(data.label) : 0);
  ordinal = (use_ordinal ? &ordinal_condition : 0);
  reader_suffix = ((use_left ? 8 : 0) | (use_right ? 4 : 0) | (use_label ? 2 : 0) | (use_ordinal ? 1 : 0));
  reader_suffix_string = test_helper_reader_suffix_integer_to_string(reader_suffix);
  expected_count = test_helper_estimate_relation_read_result_count((data.e_left_count), (data.e_right_count), (data.e_label_count), ordinal);
  printf("  %s", reader_suffix_string);
  free(reader_suffix_string);
  status_require((db_relation_select(txn, left_pointer, right_pointer, label_pointer, ordinal, (&selection))));
  if (offset) {
    status_require((db_relation_skip((&selection), offset)));
  };
  /* test multiple read calls */
  status_require((db_relation_read((&selection), 2, (&(data.relations)))));
  /* this call assumes that results never exceed the length of data.relations */
  status_require_read((db_relation_read((&selection), (db_relations_max_length((data.relations))), (&(data.relations)))));
  if (status.id == db_status_id_notfound) {
    status.id = status_id_success;
  } else {
    printf("\n  final read result does not indicate that there is no more data");
    status.id = 1;
    goto exit;
  };
  if (!(data.relations.used == expected_count)) {
    printf(("\n  expected %zu read %zu. ordinal min " db_ordinal_printf_format " max " db_ordinal_printf_format "\n"), expected_count, (data.relations.used), (ordinal ? ordinal_min : ((db_ordinal_t)(0))), (ordinal ? ordinal_max : ((db_ordinal_t)(0))));
    printf("read ");
    db_debug_log_relations((data.relations));
    test_helper_display_all_relations(txn, (data.e_left_count), (data.e_right_count), (data.e_label_count));
    status.id = 1;
    goto exit;
  };
  if (!ordinal) {
    status_require((test_helper_relation_read_relations_validate(data)));
  };
  db_relation_selection_finish((&selection));
  db_status_success_if_notfound;
  sph_array_current_rewind((data.relations));
exit:
  printf("\n");
  status_return;
}

/** prepare arrays with ids to be used in the relation (e, existing) and ids unused in the relation
   (ne, non-existing) and with both partly interleaved (left, right, label) */
status_t test_helper_relation_read_setup(db_env_t* env, uint32_t e_left_count, uint32_t e_right_count, uint32_t e_label_count, test_helper_relation_read_data_t* r) {
  status_declare;
  db_txn_declare(env, txn);
  sph_array_declare(ne_left, db_ids_t);
  sph_array_declare(ne_right, db_ids_t);
  sph_array_declare(ne_label, db_ids_t);
  status_require((db_relations_new((e_left_count * e_right_count * e_label_count), (&(r->relations)))));
  status_require((db_ids_new(e_left_count, (&(r->e_left)))));
  status_require((db_ids_new(e_right_count, (&(r->e_right)))));
  status_require((db_ids_new(e_label_count, (&(r->e_label)))));
  status_require((db_txn_write_begin((&txn))));
  test_helper_create_ids(txn, e_left_count, (&(r->e_left)));
  test_helper_create_ids(txn, e_right_count, (&(r->e_right)));
  test_helper_create_ids(txn, e_label_count, (&(r->e_label)));
  status_require((test_helper_create_relations(txn, (r->e_left), (r->e_right), (r->e_label))));
  /* add ids that do not exist in the relation */
  test_helper_create_ids(txn, e_left_count, (&ne_left));
  test_helper_create_ids(txn, e_right_count, (&ne_right));
  test_helper_create_ids(txn, e_label_count, (&ne_label));
  status_require((test_helper_interleave_ids(txn, (r->e_left), ne_left, (&(r->left)))));
  status_require((test_helper_interleave_ids(txn, (r->e_right), ne_right, (&(r->right)))));
  status_require((test_helper_interleave_ids(txn, (r->e_label), ne_label, (&(r->label)))));
  status_require((db_txn_commit((&txn))));
  r->e_left_count = e_left_count;
  r->e_right_count = e_right_count;
  r->e_label_count = e_label_count;
exit:
  db_ids_free((&ne_left));
  db_ids_free((&ne_right));
  db_ids_free((&ne_label));
  status_return;
}
void test_helper_relation_read_teardown(test_helper_relation_read_data_t* data) {
  db_relations_free((&(data->relations)));
  db_ids_free((&(data->e_left)));
  db_ids_free((&(data->e_right)));
  db_ids_free((&(data->e_label)));
  db_ids_free((&(data->left)));
  db_ids_free((&(data->right)));
  db_ids_free((&(data->label)));
}
status_t test_helper_relation_delete_setup(db_env_t* env, uint32_t e_left_count, uint32_t e_right_count, uint32_t e_label_count, test_helper_relation_delete_data_t* r) {
  status_declare;
  r->env = env;
  r->e_left_count = e_left_count;
  r->e_right_count = e_right_count;
  r->e_label_count = e_label_count;
  status_return;
}

/** for any given argument permutation:
   * checks btree entry count difference
   * checks read result count after deletion, using the same search query
   relations are assumed to be created with linearly incremented ordinals starting with 1 */
status_t test_helper_relation_delete_one(test_helper_relation_delete_data_t data, boolean use_left, boolean use_right, boolean use_label, boolean use_ordinal) {
  status_declare;
  db_txn_declare((data.env), txn);
  sph_array_declare(left, db_ids_t);
  sph_array_declare(right, db_ids_t);
  sph_array_declare(label, db_ids_t);
  sph_array_declare(relations, db_relations_t);
  db_relation_selection_declare(selection);
  size_t btree_count_after_delete;
  size_t btree_count_before_create;
  db_ordinal_condition_t* ordinal;
  db_ordinal_condition_t ordinal_condition = { 2, 5 };
  printf("  "
         "%" PRIu8 "%" PRIu8 "%" PRIu8 "%" PRIu8,
    use_left,
    use_right,
    use_label,
    use_ordinal);
  ordinal = &ordinal_condition;
  status_require((db_ids_new((data.e_left_count), (&left))));
  status_require((db_ids_new((data.e_right_count), (&right))));
  status_require((db_ids_new((data.e_label_count), (&label))));
  db_relations_new((data.e_left_count * data.e_right_count * data.e_label_count), (&relations));
  status_require((db_txn_write_begin((&txn))));
  test_helper_create_ids(txn, (data.e_left_count), (&left));
  test_helper_create_ids(txn, (data.e_right_count), (&right));
  test_helper_create_ids(txn, (data.e_label_count), (&label));
  db_debug_count_all_btree_entries(txn, (&btree_count_before_create));
  status_require((test_helper_create_relations(txn, left, right, label)));
  status_require((db_txn_commit((&txn))));
  status_require((db_txn_write_begin((&txn))));
  /* delete */
  status_require((db_relation_delete(txn, (use_left ? &left : 0), (use_right ? &right : 0), (use_label ? &label : 0), (use_ordinal ? ordinal : 0))));
  status_require((db_txn_commit((&txn))));
  status_require((db_txn_begin((&txn))));
  db_debug_count_all_btree_entries(txn, (&btree_count_after_delete));
  status_require_read((db_relation_select(txn, (use_left ? &left : 0), (use_right ? &right : 0), (use_label ? &label : 0), (use_ordinal ? ordinal : 0), (&selection))));
  db_relation_selection_finish((&selection));
  db_txn_abort((&txn));
  if (!(0 == relations.used)) {
    printf(("\n  failed deletion. %zu relations not deleted\n"), (relations.used));
    db_debug_log_relations(relations);
    status.id = 1;
    goto exit;
  };
  sph_array_clear(relations);
  /* test only if not using ordinal condition because the expected counts arent estimated */
  if (!(use_ordinal || (btree_count_after_delete == btree_count_before_create))) {
    printf(("\n  failed deletion. %zu btree entries not deleted\n"), (btree_count_after_delete - btree_count_before_create));
    status_require((db_txn_begin((&txn))));
    db_debug_log_btree_counts(txn);
    status_require_read((db_relation_select(txn, 0, 0, 0, 0, (&selection))));
    status_require_read((db_relation_read((&selection), (db_relations_length(relations)), (&relations))));
    printf("all remaining ");
    db_debug_log_relations(relations);
    db_relation_selection_finish((&selection));
    db_txn_abort((&txn));
    status.id = 1;
    goto exit;
  };
  db_status_success_if_notfound;
exit:
  printf("\n");
  db_ids_free((&left));
  db_ids_free((&right));
  db_ids_free((&label));
  db_relations_free((&relations));
  status_return;
}
