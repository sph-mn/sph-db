#include <stdio.h>
#include <stdlib.h>
#include <errno.h>
#include <pthread.h>
#include "../main/sph-db.h"
#include "../main/sph-db-extra.h"
#include "../main/lib/lmdb.c"
#include "../foreign/sph/one.c"
#define test_helper_db_root "/tmp/test-sph-db"
#define test_helper_path_data test_helper_db_root "/data"
#define test_helper_test_one(f, env) \
  printf("%s\n", #f); \
  status_require(test_helper_reset(env, 0)); \
  status_require(f(env))
#define test_helper_assert(description, expression) \
  if (!expression) { \
    printf("%s failed\n", description); \
    status_set_id_goto(1); \
  }
/** define a function that searches for an id in an array of records at field */
#define test_helper_define_relations_contains_at(field_name) \
  boolean db_debug_relations_contains_at_##field_name(db_relations_t records, db_id_t id) { \
    db_relation_t record; \
    while (i_array_in_range(records)) { \
      record = i_array_get(records); \
      if (id == record.field_name) { \
        return (1); \
      }; \
      i_array_forward(records); \
    }; \
    return (0); \
  }
;
/** define a function for getting a field from a graph record, to use with a function pointer */
#define test_helper_define_relation_get(field_name) \
  db_id_t test_helper_relation_get_##field_name(db_relation_t record) { return ((record.field_name)); }
;
typedef struct {
  db_txn_t txn;
  db_relations_t records;
  db_ids_t e_left;
  db_ids_t e_right;
  db_ids_t e_label;
  ui32 e_left_count;
  ui32 e_right_count;
  ui32 e_label_count;
  db_ids_t left;
  db_ids_t right;
  db_ids_t label;
} test_helper_graph_read_data_t;
typedef struct {
  db_env_t* env;
  ui32 e_left_count;
  ui32 e_right_count;
  ui32 e_label_count;
} test_helper_graph_delete_data_t;
/** calculates the number of btree entries affected by a relation read or delete.
   assumes linearly incremented ordinal integers starting at 1 and queries for all or no ids */
ui32 test_helper_estimate_graph_read_btree_entry_count(ui32 e_left_count, ui32 e_right_count, ui32 e_label_count, db_ordinal_condition_t* ordinal) {
  ui32 ordinal_min = 0;
  ui32 ordinal_max = 0;
  if (ordinal) {
    ordinal_min = ordinal->min;
    ordinal_max = ordinal->max;
  };
  ui32 label_left_count = 0;
  ui32 left_right_count = 0;
  ui32 right_left_count = 0;
  ui32 ordinal_value = 1;
  ui32 left_count = 0;
  ui32 right_count = 0;
  ui32 label_count = 0;
  /* the number of relations is not proportional to the number of entries in graph-ll.
    use a process similar to relation creation to correctly calculate graph-ll and ordinal dependent entries */
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
};
status_t test_helper_display_all_relations(db_txn_t txn, ui32 left_count, ui32 right_count, ui32 label_count) {
  status_declare;
  db_relations_t records;
  db_graph_selection_t state;
  i_array_allocate_db_relations_t((&records), (left_count * right_count * label_count));
  status_require_read(db_graph_select(txn, 0, 0, 0, 0, 0, (&state)));
  status_require_read(db_graph_read((&state), 0, (&records)));
  printf("all ");
  db_graph_selection_finish((&state));
  db_debug_log_relations(records);
  i_array_free(records);
exit:
  return (status);
};
/** 1101 -> "1101" */
ui8* test_helper_reader_suffix_integer_to_string(ui8 a) {
  ui8* result = malloc(40);
  result[0] = ((8 & a) ? '1' : '0');
  result[1] = ((4 & a) ? '1' : '0');
  result[2] = ((2 & a) ? '1' : '0');
  result[3] = ((1 & a) ? '1' : '0');
  result[4] = 0;
  return (result);
};
test_helper_define_relations_contains_at(left);
test_helper_define_relations_contains_at(right);
test_helper_define_relations_contains_at(label);
test_helper_define_relation_get(left);
test_helper_define_relation_get(right);
test_helper_define_relation_get(label);
status_t test_helper_reset(db_env_t* env, boolean re_use) {
  status_declare;
  if (env->open) {
    db_close(env);
  };
  if (!re_use && file_exists_p(test_helper_path_data)) {
    status.id = system("rm " test_helper_path_data);
    if (status_is_failure) {
      status_goto;
    };
  };
  status_require(db_open(test_helper_db_root, 0, env));
exit:
  return (status);
};
void test_helper_print_binary_ui64(ui64 a) {
  size_t i;
  ui8 result[65];
  *(64 + result) = 0;
  for (i = 0; (i < 64); i = (1 + i)) {
    *(i + result) = (((((ui64)(1)) << i) & a) ? '1' : '0');
  };
  printf("%s\n", result);
};
void test_helper_display_array_ui8(ui8* a, size_t size) {
  size_t i;
  for (i = 0; (i < size); i = (1 + i)) {
    printf("%lu ", (a[i]));
  };
  printf("\n");
};
boolean db_ids_contains(db_ids_t ids, db_id_t id) {
  while (i_array_in_range(ids)) {
    if (id == i_array_get(ids)) {
      return (1);
    };
    i_array_forward(ids);
  };
  return (0);
};
status_t db_ids_reverse(db_ids_t a, db_ids_t* result) {
  status_declare;
  db_ids_t temp;
  if (!i_array_allocate_db_ids_t((&temp), i_array_length(a))) {
    status_set_id_goto(db_status_id_memory);
  };
  while (i_array_in_range(a)) {
    i_array_add(temp, i_array_get(a));
    i_array_forward(a);
  };
  *result = temp;
exit:
  return (status);
};
/** create a new type with four fields, fixed and variable length, for testing */
status_t test_helper_create_type_1(db_env_t* env, db_type_t** result) {
  status_declare;
  db_field_t fields[4];
  db_field_set((fields[0]), db_field_type_uint8, "test-field-1", 12);
  db_field_set((fields[1]), db_field_type_int8, "test-field-2", 12);
  db_field_set((fields[2]), db_field_type_string, "test-field-3", 12);
  db_field_set((fields[3]), db_field_type_string, "test-field-4", 12);
  status_require(db_type_create(env, "test-type-1", fields, 4, 0, result));
exit:
  return (status);
};
/** create multiple node-values */
status_t test_helper_create_values_1(db_env_t* env, db_type_t* type, db_node_values_t** result_values, ui32* result_values_len) {
  status_declare;
  ui8* value_1;
  i8* value_2;
  ui8* value_3;
  ui8* value_4;
  db_node_values_t* values;
  db_malloc(value_1, 1);
  db_malloc(value_2, 1);
  db_malloc(values, (2 * sizeof(db_node_values_t)));
  *value_1 = 11;
  *value_2 = -128;
  db_malloc_string(value_3, 3);
  db_malloc_string(value_4, 5);
  memcpy(value_3, (&"abc"), 3);
  memcpy(value_4, (&"abcde"), 5);
  status_require(db_node_values_new(type, (0 + values)));
  status_require(db_node_values_new(type, (1 + values)));
  db_node_values_set((0 + values), 0, value_1, 0);
  db_node_values_set((0 + values), 1, value_2, 0);
  db_node_values_set((0 + values), 2, value_3, 3);
  db_node_values_set((0 + values), 3, value_4, 5);
  db_node_values_set((1 + values), 0, value_1, 0);
  db_node_values_set((1 + values), 1, value_1, 0);
  db_node_values_set((1 + values), 2, value_3, 3);
  *result_values_len = 4;
  *result_values = values;
exit:
  return (status);
};
/** creates several nodes with the given values */
status_t test_helper_create_nodes_1(db_env_t* env, db_node_values_t* values, db_id_t** result_ids, ui32* result_len) {
  status_declare;
  db_txn_declare(env, txn);
  db_id_t* ids;
  db_malloc(ids, (4 * sizeof(db_id_t)));
  status_require(db_txn_write_begin((&txn)));
  status_require((db_node_create(txn, (values[0]), (0 + ids))));
  status_require((db_node_create(txn, (values[0]), (1 + ids))));
  status_require((db_node_create(txn, (values[1]), (2 + ids))));
  status_require((db_node_create(txn, (values[1]), (3 + ids))));
  status_require(db_txn_commit((&txn)));
  *result_ids = ids;
  *result_len = 4;
exit:
  return (status);
};
/** create only ids, without nodes. doesnt depend on node creation.
  especially with relation reading where order lead to lucky success results */
status_t test_helper_create_ids(db_txn_t txn, ui32 count, db_ids_t* result) {
  status_declare;
  db_id_t id;
  db_ids_t result_temp;
  if (!i_array_allocate_db_ids_t((&result_temp), count)) {
    status_set_id_goto(db_status_id_memory);
  };
  while (count) {
    /* use type id zero to have small node ids for testing which are easier to debug */
    status_require((db_sequence_next((txn.env), 0, (&id))));
    i_array_add(result_temp, id);
    count = (count - 1);
  };
  status_require(db_ids_reverse(result_temp, result));
exit:
  i_array_free(result_temp);
  return (status);
};
/** merge ids from two lists into a new list, interleave at half the size of the arrays.
   result is as long as both id lists combined.
   approximately like this: 1 1 1 1 + 2 2 2 2 -> 1 1 2 1 2 1 2 2 */
status_t test_helper_interleave_ids(db_txn_t txn, db_ids_t ids_a, db_ids_t ids_b, db_ids_t* result) {
  status_declare;
  i_array_declare(ids_result, db_ids_t);
  ui32 target_count;
  ui32 start_mixed;
  ui32 start_new;
  ui32 i;
  target_count = (i_array_length(ids_a) + i_array_length(ids_b));
  start_mixed = (target_count / 4);
  start_new = (target_count - start_mixed);
  if (!i_array_allocate_db_ids_t((&ids_result), target_count)) {
    status_set_id_goto(db_status_id_memory);
  };
  for (i = 0; (i < target_count); i = (1 + i)) {
    if (i < start_mixed) {
      i_array_add(ids_result, i_array_get(ids_a));
      i_array_forward(ids_a);
    } else {
      if (i < start_new) {
        if (1 & i) {
          i_array_add(ids_result, i_array_get(ids_a));
          i_array_forward(ids_a);
        } else {
          i_array_add(ids_result, i_array_get(ids_b));
          i_array_forward(ids_b);
        };
      } else {
        i_array_add(ids_result, i_array_get(ids_b));
        i_array_forward(ids_b);
      };
    };
  };
  *result = ids_result;
exit:
  if (status_is_failure) {
    i_array_free(ids_result);
  };
  return (status);
};
ui32 test_helper_calculate_relation_count(ui32 left_count, ui32 right_count, ui32 label_count) { return ((left_count * right_count * label_count)); };
ui32 test_helper_calculate_relation_count_from_ids(db_ids_t left, db_ids_t right, db_ids_t label) { return (test_helper_calculate_relation_count(i_array_length(left), i_array_length(right), i_array_length(label))); };
/** test if the result records contain all filter-ids,
  and the filter-ids contain all result record values for field "name". */
status_t test_helper_graph_read_records_validate_one(ui8* name, db_ids_t e_ids, db_relations_t records) {
  status_declare;
  boolean (*contains_at)(db_relations_t, db_id_t);
  db_id_t (*record_get)(db_relation_t);
  if (0 == strcmp("left", name)) {
    contains_at = db_debug_relations_contains_at_left;
    record_get = test_helper_relation_get_left;
  } else if (0 == strcmp("right", name)) {
    contains_at = db_debug_relations_contains_at_right;
    record_get = test_helper_relation_get_right;
  } else if (0 == strcmp("label", name)) {
    contains_at = db_debug_relations_contains_at_label;
    record_get = test_helper_relation_get_label;
  };
  while (i_array_in_range(records)) {
    if (!db_ids_contains(e_ids, record_get(i_array_get(records)))) {
      printf("\n result records contain inexistant %s ids\n", name);
      status_set_id_goto(1);
    };
    i_array_forward(records);
  };
  i_array_rewind(records);
  while (i_array_in_range(e_ids)) {
    if (!contains_at(records, i_array_get(e_ids))) {
      printf("\n  %s result records do not contain all existing-ids\n", name);
      status_set_id_goto(2);
    };
    i_array_forward(e_ids);
  };
exit:
  return (status);
};
status_t test_helper_graph_read_records_validate(test_helper_graph_read_data_t data) {
  status_declare;
  status_require((test_helper_graph_read_records_validate_one("left", (data.e_left), (data.records))));
  status_require((test_helper_graph_read_records_validate_one("right", (data.e_right), (data.records))));
  status_require((test_helper_graph_read_records_validate_one("label", (data.e_label), (data.records))));
exit:
  return (status);
};
db_ordinal_t test_helper_default_ordinal_generator(void* state) {
  db_ordinal_t* ordinal_pointer = state;
  db_ordinal_t result = (1 + *ordinal_pointer);
  *ordinal_pointer = result;
  return (result);
};
/** create relations with linearly increasing ordinal starting from zero */
status_t test_helper_create_relations(db_txn_t txn, db_ids_t left, db_ids_t right, db_ids_t label) {
  status_declare;
  db_ordinal_t ordinal_state_value;
  ordinal_state_value = 0;
  status_require(db_graph_ensure(txn, left, right, label, test_helper_default_ordinal_generator, (&ordinal_state_value)));
exit:
  return (status);
};
/** assumes linearly set-plus-oneed ordinal integers starting at 1 and queries for all or no ids */
ui32 test_helper_estimate_graph_read_result_count(ui32 left_count, ui32 right_count, ui32 label_count, db_ordinal_condition_t* ordinal) {
  ui32 count = (left_count * right_count * label_count);
  ui32 max;
  ui32 min;
  if (ordinal) {
    min = (ordinal->min ? (ordinal->min - 1) : 0);
    max = ordinal->max;
    ((max > count) ? (max = count) : 0);
  } else {
    min = 0;
    max = count;
  };
  return ((count - min - (count - max)));
};
status_t test_helper_graph_read_one(db_txn_t txn, test_helper_graph_read_data_t data, boolean use_left, boolean use_right, boolean use_label, boolean use_ordinal, ui32 offset) {
  status_declare;
  db_ids_t* left_pointer;
  db_ids_t* right_pointer;
  db_ids_t* label_pointer;
  db_graph_selection_t state;
  ui32 ordinal_min;
  ui32 ordinal_max;
  db_ordinal_condition_t ordinal_condition;
  db_ordinal_condition_t* ordinal;
  ui32 expected_count;
  ui8 reader_suffix;
  ui8* reader_suffix_string;
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
  expected_count = test_helper_estimate_graph_read_result_count((data.e_left_count), (data.e_right_count), (data.e_label_count), ordinal);
  printf("  %s", reader_suffix_string);
  free(reader_suffix_string);
  status_require(db_graph_select(txn, left_pointer, right_pointer, label_pointer, ordinal, offset, (&state)));
  status_require_read((db_graph_read((&state), 2, (&(data.records)))));
  status_require_read((db_graph_read((&state), 0, (&(data.records)))));
  if (status.id == db_status_id_notfound) {
    status.id = status_id_success;
  } else {
    printf("\n  final read result does not indicate that there is no more data");
    status_set_id_goto(1);
  };
  if (!(i_array_length((data.records)) == expected_count)) {
    printf(("\n  expected %lu read %lu. ordinal min %d max %d\n"), expected_count, (i_array_length((data.records))), (ordinal ? ordinal_min : 0), (ordinal ? ordinal_max : 0));
    printf("read ");
    db_debug_log_relations((data.records));
    test_helper_display_all_relations(txn, (data.e_left_count), (data.e_right_count), (data.e_label_count));
    status_set_id_goto(1);
  };
  if (!ordinal) {
    status_require(test_helper_graph_read_records_validate(data));
  };
  db_graph_selection_finish((&state));
  db_status_success_if_notfound;
  i_array_rewind((data.records));
exit:
  printf("\n");
  return (status);
};
/** prepare arrays with ids to be used in the graph (e, existing) and ids unused in the graph
  (ne, non-existing) and with both partly interleaved (left, right, label) */
status_t test_helper_graph_read_setup(db_env_t* env, ui32 e_left_count, ui32 e_right_count, ui32 e_label_count, test_helper_graph_read_data_t* r) {
  status_declare;
  db_txn_declare(env, txn);
  i_array_declare(ne_left, db_ids_t);
  i_array_declare(ne_right, db_ids_t);
  i_array_declare(ne_label, db_ids_t);
  if (!(i_array_allocate_db_relations_t((&(r->records)), (e_left_count * e_right_count * e_label_count)) && i_array_allocate_db_ids_t((&(r->e_left)), e_left_count) && i_array_allocate_db_ids_t((&(r->e_right)), e_right_count) && i_array_allocate_db_ids_t((&(r->e_label)), e_label_count))) {
    status_set_id_goto(db_status_id_memory);
  };
  status_require(db_txn_write_begin((&txn)));
  test_helper_create_ids(txn, e_left_count, (&(r->e_left)));
  test_helper_create_ids(txn, e_right_count, (&(r->e_right)));
  test_helper_create_ids(txn, e_label_count, (&(r->e_label)));
  status_require((test_helper_create_relations(txn, (r->e_left), (r->e_right), (r->e_label))));
  /* add ids that do not exist in the graph */
  test_helper_create_ids(txn, e_left_count, (&ne_left));
  test_helper_create_ids(txn, e_right_count, (&ne_right));
  test_helper_create_ids(txn, e_label_count, (&ne_label));
  status_require((test_helper_interleave_ids(txn, (r->e_left), ne_left, (&(r->left)))));
  status_require((test_helper_interleave_ids(txn, (r->e_right), ne_right, (&(r->right)))));
  status_require((test_helper_interleave_ids(txn, (r->e_label), ne_label, (&(r->label)))));
  status_require(db_txn_commit((&txn)));
  r->e_left_count = e_left_count;
  r->e_right_count = e_right_count;
  r->e_label_count = e_label_count;
exit:
  i_array_free(ne_left);
  i_array_free(ne_right);
  i_array_free(ne_label);
  return (status);
};
void test_helper_graph_read_teardown(test_helper_graph_read_data_t* data) {
  i_array_free((data->records));
  i_array_free((data->e_left));
  i_array_free((data->e_right));
  i_array_free((data->e_label));
  i_array_free((data->left));
  i_array_free((data->right));
  i_array_free((data->label));
};
status_t test_helper_graph_delete_setup(db_env_t* env, ui32 e_left_count, ui32 e_right_count, ui32 e_label_count, test_helper_graph_delete_data_t* r) {
  status_declare;
  r->env = env;
  r->e_left_count = e_left_count;
  r->e_right_count = e_right_count;
  r->e_label_count = e_label_count;
  return (status);
};
status_t test_helper_graph_delete_one(test_helper_graph_delete_data_t data, boolean use_left, boolean use_right, boolean use_label, boolean use_ordinal) {
  "for any given argument permutation:\n     * checks btree entry count difference\n     * checks read result count after deletion, using the same search query\n    relations are assumed to be created with linearly incremented ordinals starting with 1";
  status_declare;
  db_txn_declare((data.env), txn);
  i_array_declare(left, db_ids_t);
  i_array_declare(right, db_ids_t);
  i_array_declare(label, db_ids_t);
  i_array_declare(records, db_relations_t);
  db_graph_selection_t state;
  ui32 read_count_before_expected;
  ui32 btree_count_after_delete;
  ui32 btree_count_before_create;
  ui32 btree_count_deleted_expected;
  db_ordinal_condition_t* ordinal;
  db_ordinal_condition_t ordinal_condition = { 2, 5 };
  printf("  %d%d%d%d", use_left, use_right, use_label, use_ordinal);
  ordinal = &ordinal_condition;
  read_count_before_expected = test_helper_estimate_graph_read_result_count((data.e_left_count), (data.e_right_count), (data.e_label_count), ordinal);
  btree_count_deleted_expected = test_helper_estimate_graph_read_btree_entry_count((data.e_left_count), (data.e_right_count), (data.e_label_count), ordinal);
  i_array_allocate_db_ids_t((&left), (data.e_left_count));
  i_array_allocate_db_ids_t((&right), (data.e_right_count));
  i_array_allocate_db_ids_t((&label), (data.e_label_count));
  i_array_allocate_db_relations_t((&records), (data.e_left_count * data.e_right_count * data.e_label_count));
  status_require(db_txn_write_begin((&txn)));
  test_helper_create_ids(txn, (data.e_left_count), (&left));
  test_helper_create_ids(txn, (data.e_right_count), (&right));
  test_helper_create_ids(txn, (data.e_label_count), (&label));
  db_debug_count_all_btree_entries(txn, (&btree_count_before_create));
  status_require(test_helper_create_relations(txn, left, right, label));
  status_require(db_txn_commit((&txn)));
  status_require(db_txn_write_begin((&txn)));
  /* delete */
  status_require(db_graph_delete(txn, (use_left ? &left : 0), (use_right ? &right : 0), (use_label ? &label : 0), (use_ordinal ? ordinal : 0)));
  status_require(db_txn_commit((&txn)));
  status_require(db_txn_begin((&txn)));
  db_debug_count_all_btree_entries(txn, (&btree_count_after_delete));
  status_require_read(db_graph_select(txn, (use_left ? &left : 0), (use_right ? &right : 0), (use_label ? &label : 0), (use_ordinal ? ordinal : 0), 0, (&state)));
  /* check that readers can handle empty selections */
  status_require_read(db_graph_read((&state), 0, (&records)));
  db_graph_selection_finish((&state));
  db_txn_abort((&txn));
  if (!(0 == i_array_length(records))) {
    printf(("\n    failed deletion. %lu relations not deleted\n"), i_array_length(records));
    db_debug_log_relations(records);
    status_set_id_goto(1);
  };
  i_array_clear(records);
  /* test only if not using ordinal condition because the expected counts arent estimated */
  if (!(use_ordinal || (btree_count_after_delete == btree_count_before_create))) {
    printf(("\n failed deletion. %lu btree entries not deleted\n"), (btree_count_after_delete - btree_count_before_create));
    status_require(db_txn_begin((&txn)));
    db_debug_log_btree_counts(txn);
    status_require_read(db_graph_select(txn, 0, 0, 0, 0, 0, (&state)));
    status_require_read(db_graph_read((&state), 0, (&records)));
    printf("all remaining ");
    db_debug_log_relations(records);
    db_graph_selection_finish((&state));
    db_txn_abort((&txn));
    status_set_id_goto(1);
  };
  db_status_success_if_notfound;
exit:
  printf("\n");
  i_array_free(left);
  i_array_free(right);
  i_array_free(label);
  i_array_free(records);
  return (status);
};