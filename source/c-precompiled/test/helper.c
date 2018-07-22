#include <stdio.h>
#include <stdlib.h>
#include <errno.h>
#include <pthread.h>
#include "../main/sph-db.h"
#include "../foreign/sph/one.c"
#define test_helper_db_root "/tmp/test-sph-db"
#define test_helper_path_data test_helper_db_root "/data"
#define set_plus_one(a) a = (1 + a)
#define set_minus_one(a) a = (a - 1)
#define test_helper_init(env_name) \
  status_declare; \
  db_env_define(env_name)
#define test_helper_report_status \
  if (status_is_success) { \
    printf(("--\ntests finished successfully.\n")); \
  } else { \
    printf(("\ntests failed. %d %s\n"), \
      (status.id), \
      db_status_description(status)); \
  }
#define test_helper_test_one(func, env) \
  printf("%s\n", #func); \
  status_require(test_helper_reset(env, 0)); \
  status_require(func(env))
#define test_helper_assert(description, expression) \
  if (!expression) { \
    printf("%s failed\n", description); \
    status_set_id_goto(1); \
  }
#define db_field_set(a, a_type, a_name, a_name_len) \
  a.type = a_type; \
  a.name = a_name; \
  a.name_len = a_name_len
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
boolean db_ids_contains(db_ids_t* ids, db_id_t id) {
  while (ids) {
    if (id == db_ids_first(ids)) {
      return (1);
    };
    ids = db_ids_rest(ids);
  };
  return (0);
};
status_t db_ids_reverse(db_ids_t* a, db_ids_t** result) {
  status_declare;
  db_ids_t* ids_temp;
  ids_temp = 0;
  while (a) {
    ids_temp = db_ids_add(ids_temp, db_ids_first(a));
    if (!ids_temp) {
      db_status_set_id_goto(db_status_id_memory);
    };
    a = db_ids_rest(a);
  };
  *result = ids_temp;
exit:
  return (status);
};
#define db_debug_define_graph_records_contains_at(field) \
  boolean db_debug_graph_records_contains_at_##field##_p( \
    db_graph_records_t* records, db_id_t id) { \
    while (records) { \
      if (id == db_graph_records_first(records).field) { \
        return (1); \
      }; \
      records = db_graph_records_rest(records); \
    }; \
    return (0); \
  }
db_debug_define_graph_records_contains_at(left);
db_debug_define_graph_records_contains_at(right);
db_debug_define_graph_records_contains_at(label);
/** create a new type with four fields, fixed and variable length, for testing
 */
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
/** for test-type-1 */
status_t test_helper_create_nodes_1(db_env_t* env,
  db_type_t* type,
  db_node_values_t** result_values,
  db_id_t** result_ids,
  ui32* result_len) {
  status_declare;
  db_txn_declare(env, txn);
  db_id_t* ids;
  ui8* value_1;
  i8* value_2;
  ui8* value_3;
  ui8* value_4;
  db_node_values_t* values;
  db_malloc(ids, (4 * sizeof(db_id_t)));
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
  status_require(db_txn_write_begin((&txn)));
  status_require((db_node_create(txn, (values[0]), (0 + ids))));
  status_require((db_node_create(txn, (values[0]), (1 + ids))));
  status_require((db_node_create(txn, (values[1]), (2 + ids))));
  status_require((db_node_create(txn, (values[1]), (3 + ids))));
  status_require(db_txn_commit((&txn)));
  *result_ids = ids;
  *result_len = 4;
  *result_values = values;
exit:
  return (status);
};
/** create only ids, without nodes. doesnt depend on node creation.
  dont reverse id list because it leads to more unorderly data which can expose
  bugs especially with relation reading where order lead to lucky success
  results */
status_t test_helper_create_ids(db_txn_t txn, ui32 count, db_ids_t** result) {
  status_declare;
  db_declare_ids(ids_temp);
  db_id_t id;
  while (count) {
    /* use type id zero - normally not valid for nodes but it works  and for
     * tests it keeps the ids small numbers */
    status_require((db_sequence_next((txn.env), 0, (&id))));
    ids_temp = db_ids_add(ids_temp, id);
    if (!ids_temp) {
      status_set_id_goto(db_status_id_memory);
    };
    count = (count - 1);
  };
  *result = ids_temp;
exit:
  return (status);
};
/** add newly created ids to the list.
   create as many elements as there are in ids-old. add them with interleaved
   overlap at half of ids-old
   approximately like this: 1 1 1 1 + 2 2 2 2 -> 1 1 2 1 2 1 2 2 */
status_t test_helper_ids_add_new_ids(db_txn_t txn,
  db_ids_t* ids_old,
  db_ids_t** result) {
  status_declare;
  db_declare_ids(ids_new);
  ui32 target_count;
  ui32 start_mixed;
  ui32 start_new;
  ui32 count;
  *result = 0;
  status_require(
    test_helper_create_ids(txn, db_ids_length(ids_old), (&ids_new)));
  target_count = (2 * db_ids_length(ids_old));
  start_mixed = (target_count / 4);
  start_new = (target_count - start_mixed);
  for (count = 0; (count < target_count); count = (1 + count)) {
    if (count < start_mixed) {
      *result = db_ids_add((*result), db_ids_first(ids_old));
      ids_old = db_ids_rest(ids_old);
    } else {
      if (count < start_new) {
        if (1 & count) {
          *result = db_ids_add((*result), db_ids_first(ids_old));
          ids_old = db_ids_rest(ids_old);
        } else {
          *result = db_ids_add((*result), db_ids_first(ids_new));
          ids_new = db_ids_rest(ids_new);
        };
      } else {
        *result = db_ids_add((*result), db_ids_first(ids_new));
        ids_new = db_ids_rest(ids_new);
      };
    };
  };
exit:
  return (status);
};
ui32 test_helper_calculate_relation_count(ui32 left_count,
  ui32 right_count,
  ui32 label_count) {
  return ((left_count * right_count * label_count));
};
ui32 test_helper_calculate_relation_count_from_ids(db_ids_t* left,
  db_ids_t* right,
  db_ids_t* label) {
  return (test_helper_calculate_relation_count(
    db_ids_length(left), db_ids_length(right), db_ids_length(label)));
};
/** test that the result records contain all filter-ids, and the filter-ids
 * contain all result record values for field "name". */
#define test_helper_graph_read_records_validate_one(name) \
  records_temp = records; \
  while (records_temp) { \
    if (!db_ids_contains( \
          existing_##name, (db_graph_records_first(records_temp).name))) { \
      printf("\n  result records contain inexistant %s ids\n", #name); \
      db_debug_display_graph_records(records); \
      status_set_id_goto(1); \
    }; \
    records_temp = db_graph_records_rest(records_temp); \
  }; \
  ids_temp = existing_##name; \
  while (ids_temp) { \
    if (!db_debug_graph_records_contains_at_##name##_p( \
          records, db_ids_first(ids_temp))) { \
      printf( \
        "\n  %s result records do not contain all existing-ids\n", #name); \
      db_debug_display_graph_records(records); \
      status_set_id_goto(2); \
    }; \
    ids_temp = db_ids_rest(ids_temp); \
  }
;
status_t test_helper_graph_read_records_validate(db_graph_records_t* records,
  db_ids_t* left,
  db_ids_t* existing_left,
  db_ids_t* right,
  db_ids_t* existing_right,
  db_ids_t* label,
  db_ids_t* existing_label,
  db_ordinal_condition_t* ordinal) {
  status_declare;
  db_graph_records_t* records_temp;
  db_ids_t* ids_temp;
  test_helper_graph_read_records_validate_one(left);
  test_helper_graph_read_records_validate_one(right);
  test_helper_graph_read_records_validate_one(label);
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
status_t test_helper_create_relations(db_txn_t txn,
  db_ids_t* left,
  db_ids_t* right,
  db_ids_t* label) {
  status_declare;
  db_ordinal_t ordinal_state_value;
  ordinal_state_value = 0;
  status_require(db_graph_ensure(txn,
    left,
    right,
    label,
    test_helper_default_ordinal_generator,
    (&ordinal_state_value)));
exit:
  return (status);
};
#define test_helper_graph_read_one(txn, left, right, label, ordinal, offset) \
  reader_suffix = test_helper_filter_ids_to_reader_suffix_integer( \
    left, right, label, ordinal); \
  reader_suffix_string = \
    test_helper_reader_suffix_integer_to_string(reader_suffix); \
  printf(" %s", reader_suffix_string); \
  free(reader_suffix_string); \
  records = 0; \
  status_require( \
    db_graph_select(txn, left, right, label, ordinal, offset, (&state))); \
  db_status_require_read(db_graph_read((&state), 2, (&records))); \
  db_status_require_read(db_graph_read((&state), 0, (&records))); \
  if (status.id == db_status_id_no_more_data) { \
    status.id = status_id_success; \
  } else { \
    printf( \
      "\n  final read result does not indicate that there is no more data"); \
    status_set_id_goto(1); \
  }; \
  expected_count = test_helper_estimate_graph_read_result_count( \
    existing_left_count, existing_right_count, existing_label_count, ordinal); \
  if (!(db_graph_records_length(records) == expected_count)) { \
    printf(("\n  expected %lu read %lu. ordinal min %d max %d\n"), \
      expected_count, \
      db_graph_records_length(records), \
      (ordinal ? ordinal_min : 0), \
      (ordinal ? ordinal_max : 0)); \
    printf("read "); \
    db_debug_display_graph_records(records); \
    test_helper_display_all_relations(txn); \
    status_set_id_goto(1); \
  }; \
  if (!ordinal) { \
    status_require(test_helper_graph_read_records_validate(records, \
      left, \
      existing_left, \
      right, \
      existing_right, \
      label, \
      existing_label, \
      ordinal)); \
  }; \
  db_status_success_if_no_more_data; \
  db_graph_selection_destroy((&state)); \
  db_graph_records_destroy(records)
#define test_helper_graph_read_header(env) \
  status_declare; \
  db_txn_declare(env, txn); \
  db_declare_ids_three(existing_left, existing_right, existing_label); \
  db_declare_ids_three(left, right, label); \
  db_graph_selection_t state; \
  ui32 ordinal_min; \
  ui32 ordinal_max; \
  db_ordinal_condition_t ordinal_condition; \
  db_ordinal_condition_t* ordinal; \
  ui32 existing_left_count; \
  ui32 existing_right_count; \
  ui32 existing_label_count; \
  db_graph_records_t* records; \
  ui32 expected_count; \
  ui8 reader_suffix; \
  ui8* reader_suffix_string; \
  ordinal_min = 2; \
  ordinal_max = 5; \
  ordinal_condition.min = ordinal_min; \
  ordinal_condition.max = ordinal_max; \
  ordinal = &ordinal_condition; \
  records = 0; \
  existing_left_count = common_label_count; \
  existing_right_count = common_element_count; \
  existing_label_count = common_label_count; \
  status_require(db_txn_write_begin((&txn))); \
  test_helper_create_ids(txn, existing_left_count, (&existing_left)); \
  test_helper_create_ids(txn, existing_right_count, (&existing_right)); \
  test_helper_create_ids(txn, existing_label_count, (&existing_label)); \
  status_require(test_helper_create_relations( \
    txn, existing_left, existing_right, existing_label)); \
  /* add ids that do not exist anywhere in the graph */ \
  status_require(test_helper_ids_add_new_ids(txn, existing_left, (&left))); \
  status_require(test_helper_ids_add_new_ids(txn, existing_right, (&right))); \
  status_require(test_helper_ids_add_new_ids(txn, existing_label, (&label))); \
  printf(" ")
#define test_helper_graph_read_footer \
  db_status_success_if_no_more_data; \
  exit: \
  printf("\n"); \
  db_txn_abort_if_active(txn); \
  return (status);
#define test_helper_filter_ids_to_reader_suffix_integer( \
  left, right, label, ordinal) \
  ((left ? 8 : 0) | (right ? 4 : 0) | (label ? 2 : 0) | (ordinal ? 1 : 0))
status_t test_helper_display_all_relations(db_txn_t txn) {
  status_declare;
  db_graph_records_t* records;
  db_graph_selection_t state;
  records = 0;
  db_status_require_read(db_graph_select(txn, 0, 0, 0, 0, 0, (&state)));
  db_status_require_read(db_graph_read((&state), 0, (&records)));
  printf("all ");
  db_graph_selection_destroy((&state));
  db_debug_display_graph_records(records);
  db_graph_records_destroy(records);
exit:
  return (status);
};
/** assumes linearly set-plus-oneed ordinal integers starting at 1 and queries
 * for all or no ids */
ui32 test_helper_estimate_graph_read_result_count(ui32 left_count,
  ui32 right_count,
  ui32 label_count,
  db_ordinal_condition_t* ordinal) {
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
/** calculates the number of btree entries affected by a relation read or
   delete. assumes linearly set-plus-oneed ordinal integers starting at 1 and
   queries for all or no ids */
ui32 test_helper_estimate_graph_read_btree_entry_count(ui32 existing_left_count,
  ui32 existing_right_count,
  ui32 existing_label_count,
  db_ordinal_condition_t* ordinal) {
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
  /* the number of relations is not proportional to the number of entries in
     graph-ll.
      use a process similar to relation creation to correctly calculate graph-ll
     and ordinal dependent entries */
  while ((label_count < existing_label_count)) {
    while ((left_count < existing_left_count)) {
      if ((ordinal_value <= ordinal_max) && (ordinal_value >= ordinal_min)) {
        set_plus_one(label_left_count);
      };
      while ((right_count < existing_right_count)) {
        if ((ordinal_value <= ordinal_max) && (ordinal_value >= ordinal_min)) {
          set_plus_one(ordinal_value);
          set_plus_one(left_right_count);
          set_plus_one(right_left_count);
        };
        set_plus_one(right_count);
      };
      set_plus_one(left_count);
    };
    set_plus_one(label_count);
  };
  return ((left_right_count + right_left_count + label_left_count));
};
#define test_helper_graph_delete_header \
  status_declare; \
  db_declare_ids_three(left, right, label); \
  db_txn_declare(env, txn); \
  db_graph_selection_t state; \
  ui32 read_count_before_expected; \
  ui32 btree_count_after_delete; \
  ui32 btree_count_before_create; \
  ui32 btree_count_deleted_expected; \
  db_graph_records_t* records; \
  db_ordinal_condition_t* ordinal; \
  ui32 existing_left_count; \
  ui32 existing_right_count; \
  ui32 existing_label_count; \
  db_ordinal_condition_t ordinal_condition = { 2, 5 }; \
  records = 0; \
  ordinal = &ordinal_condition; \
  existing_left_count = common_label_count; \
  existing_right_count = common_element_count; \
  existing_label_count = common_label_count; \
  printf(" ");
/** for any given argument permutation:
     * checks btree entry count difference
     * checks read result count after deletion, using the same search query
    relations are assumed to be created with linearly incremented ordinals
   starting with 1 */
#define test_helper_graph_delete_one(left_p, right_p, label_p, ordinal_p) \
  printf(" %d%d%d%d", left_p, right_p, label_p, ordinal_p); \
  read_count_before_expected = test_helper_estimate_graph_read_result_count( \
    existing_left_count, existing_right_count, existing_label_count, ordinal); \
  btree_count_deleted_expected = \
    test_helper_estimate_graph_read_btree_entry_count(existing_left_count, \
      existing_right_count, \
      existing_label_count, \
      ordinal); \
  status_require(db_txn_write_begin((&txn))); \
  test_helper_create_ids(txn, existing_left_count, (&left)); \
  test_helper_create_ids(txn, existing_right_count, (&right)); \
  test_helper_create_ids(txn, existing_label_count, (&label)); \
  db_debug_count_all_btree_entries(txn, (&btree_count_before_create)); \
  status_require(test_helper_create_relations(txn, left, right, label)); \
  status_require(db_txn_commit((&txn))); \
  status_require(db_txn_write_begin((&txn))); \
  /* delete */ \
  status_require(db_graph_delete(txn, \
    (left_p ? left : 0), \
    (right_p ? right : 0), \
    (label_p ? label : 0), \
    (ordinal_p ? ordinal : 0))); \
  status_require(db_txn_commit((&txn))); \
  status_require(db_txn_begin((&txn))); \
  db_debug_count_all_btree_entries(txn, (&btree_count_after_delete)); \
  db_status_require_read(db_graph_select(txn, \
    (left_p ? left : 0), \
    (right_p ? right : 0), \
    (label_p ? label : 0), \
    (ordinal_p ? ordinal : 0), \
    0, \
    (&state))); \
  /* check that readers can handle empty selections */ \
  db_status_require_read(db_graph_read((&state), 0, (&records))); \
  db_graph_selection_destroy((&state)); \
  db_txn_abort((&txn)); \
  if (!(0 == db_graph_records_length(records))) { \
    printf(("\n    failed deletion. %lu relations not deleted\n"), \
      db_graph_records_length(records)); \
    db_debug_display_graph_records(records); \
    status_set_id_goto(1); \
  }; \
  db_graph_records_destroy(records); \
  records = 0; \
  /* test only if not using ordinal condition because the expected counts \
   * arent estimated */ \
  if (!(ordinal_p || \
        (btree_count_after_delete == btree_count_before_create))) { \
    printf(("\n failed deletion. %lu btree entries not deleted\n"), \
      (btree_count_after_delete - btree_count_before_create)); \
    status_require(db_txn_begin((&txn))); \
    db_debug_display_btree_counts(txn); \
    db_status_require_read(db_graph_select(txn, 0, 0, 0, 0, 0, (&state))); \
    db_status_require_read(db_graph_read((&state), 0, (&records))); \
    printf("all remaining "); \
    db_debug_display_graph_records(records); \
    db_graph_selection_destroy((&state)); \
    db_txn_abort((&txn)); \
    status_set_id_goto(1); \
  }; \
  db_ids_destroy(left); \
  db_ids_destroy(right); \
  db_ids_destroy(label); \
  db_status_success_if_no_more_data; \
  records = 0; \
  left = 0; \
  right = 0; \
  label = 0
;
#define test_helper_graph_delete_footer \
  exit: \
  db_txn_abort_if_active(txn); \
  printf("\n"); \
  return (status);
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