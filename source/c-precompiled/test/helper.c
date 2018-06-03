#define debug_log_p 1
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
  status_init; \
  db_env_define(env_name)
#define test_helper_report_status \
  if (status_success_p) { \
    printf(("--\ntests finished successfully.\n")); \
  } else { \
    printf(("\ntests failed. %d %s\n"), \
      (status.id), \
      db_status_description(status)); \
  }
#define test_helper_test_one(func, env) \
  printf("%s\n", #func); \
  status_require_x(test_helper_reset(env, 0)); \
  status_require_x(func(env))
#define test_helper_assert(description, expression) \
  if (!expression) { \
    printf("%s failed\n", description); \
    status_set_id_goto(1); \
  }
status_t test_helper_reset(db_env_t* env, boolean re_use) {
  status_init;
  if (env->open) {
    db_close(env);
  };
  if (!re_use && file_exists_p(test_helper_path_data)) {
    status_set_id(system("rm " test_helper_path_data));
    status_require;
  };
  status_require_x(db_open(test_helper_db_root, 0, env));
exit:
  return (status);
};
b0 test_helper_print_binary_b64(b64 a) {
  size_t i;
  b8 result[65];
  *(64 + result) = 0;
  for (i = 0; (i < 64); i = (1 + i)) {
    *(i + result) = (((((b64)(1)) << i) & a) ? '1' : '0');
  };
  printf("%s\n", result);
};
boolean db_ids_contains_p(db_ids_t* ids, db_id_t id) {
  while (ids) {
    if (id == db_ids_first(ids)) {
      return (1);
    };
    ids = db_ids_rest(ids);
  };
  return (0);
};
status_t db_ids_reverse(db_ids_t* source, db_ids_t** result) {
  status_init;
  db_ids_t* ids_temp;
  while (source) {
    db_ids_add_x((*result), db_ids_first(source), ids_temp);
    source = db_ids_rest(source);
  };
exit:
  return (status);
};
#define db_debug_define_graph_records_contains_at_p(field) \
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
db_debug_define_graph_records_contains_at_p(left);
db_debug_define_graph_records_contains_at_p(right);
db_debug_define_graph_records_contains_at_p(label);
/** create only ids without nodes. doesnt depend on node creation */
status_t test_helper_create_ids(db_env_t* env, b32 count, db_ids_t** result) {
  status_init;
  db_declare_ids(ids_temp);
  db_id_t id;
  for (id = 1; (id <= count); id = (1 + id)) {
    ids_temp = db_ids_add(ids_temp, id);
    if (!ids_temp) {
      status_set_id_goto(db_status_id_memory);
    };
  };
exit:
  return (status);
};
/** add newly created ids to the list.
   create as many elements as there are in ids-old. add them with interleaved
   overlap at half of ids-old
   approximately like this: 1 1 1 1 + 2 2 2 2 -> 1 1 2 1 2 1 2 2 */
status_t test_helper_ids_add_new_ids(db_env_t* env,
  db_ids_t* ids_old,
  db_ids_t** result) {
  status_init;
  db_declare_ids(ids_new);
  b32 target_count;
  b32 start_mixed;
  b32 start_new;
  b32 count;
  *result = 0;
  status_require_x(
    test_helper_create_ids(env, db_ids_length(ids_old), (&ids_new)));
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
b32 test_helper_calculate_relation_count(b32 left_count,
  b32 right_count,
  b32 label_count) {
  return ((left_count * right_count * label_count));
};
b32 test_helper_calculate_relation_count_from_ids(db_ids_t* left,
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
    if (!db_ids_contains_p( \
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
  status_init;
  db_graph_records_t* records_temp;
  db_ids_t* ids_temp;
  test_helper_graph_read_records_validate_one(left);
  test_helper_graph_read_records_validate_one(right);
  test_helper_graph_read_records_validate_one(label);
exit:
  return (status);
};
db_ordinal_t test_helper_default_ordinal_generator(b0* state) {
  db_ordinal_t* ordinal_pointer = state;
  db_ordinal_t result = (1 + *ordinal_pointer);
  *ordinal_pointer = result;
  return (result);
};
status_t test_helper_create_relations(db_env_t* env,
  b32 count_left,
  b32 count_right,
  b32 count_label,
  db_ids_t** left,
  db_ids_t** right,
  db_ids_t** label) {
  status_init;
  db_txn_declare(env, txn);
  db_ordinal_t ordinal_state_value;
  status_require_x(test_helper_create_ids(env, count_left, left));
  status_require_x(test_helper_create_ids(env, count_right, right));
  status_require_x(test_helper_create_ids(env, count_label, label));
  ordinal_state_value = 0;
  db_txn_write_begin(txn);
  status_require_x(db_graph_ensure(txn,
    (*left),
    (*right),
    (*label),
    test_helper_default_ordinal_generator,
    (&ordinal_state_value)));
  db_txn_commit(txn);
exit:
  db_txn_abort_if_active(txn);
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
  status_require_x( \
    db_graph_select(txn, left, right, label, ordinal, offset, (&state))); \
  db_status_require_read_x(db_graph_read((&state), 2, (&records))); \
  db_status_require_read_x(db_graph_read((&state), 0, (&records))); \
  if (status_id_is_p(db_status_id_no_more_data)) { \
    status_set_id(status_id_success); \
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
    printf("the read "); \
    db_debug_display_graph_records(records); \
    db_debug_display_all_relations(txn); \
    status_set_id_goto(1); \
  }; \
  if (!ordinal) { \
    status_require_x(test_helper_graph_read_records_validate(records, \
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
  status_init; \
  db_txn_declare(env, txn); \
  db_declare_ids_three(existing_left, existing_right, existing_label); \
  db_declare_ids_three(left, right, label); \
  db_graph_read_state_t state; \
  b32 ordinal_min; \
  b32 ordinal_max; \
  db_ordinal_condition_t ordinal_condition; \
  db_ordinal_condition_t* ordinal; \
  b32 existing_left_count; \
  b32 existing_right_count; \
  b32 existing_label_count; \
  db_graph_records_t* records; \
  b32 expected_count; \
  b8 reader_suffix; \
  b8* reader_suffix_string; \
  ordinal_min = 2; \
  ordinal_max = 5; \
  ordinal_condition.min = ordinal_min; \
  ordinal_condition.max = ordinal_max; \
  ordinal = &ordinal_condition; \
  records = 0; \
  existing_left_count = common_label_count; \
  existing_right_count = common_element_count; \
  existing_label_count = common_label_count; \
  db_txn_begin(txn); \
  status_require_x(test_helper_create_relations(env, \
    existing_left_count, \
    existing_right_count, \
    existing_label_count, \
    (&existing_left), \
    (&existing_right), \
    (&existing_label))); \
  /* add ids that do not exist anywhere in the graph */ \
  status_require_x(test_helper_ids_add_new_ids(env, existing_left, (&left))); \
  status_require_x( \
    test_helper_ids_add_new_ids(env, existing_right, (&right))); \
  status_require_x( \
    test_helper_ids_add_new_ids(env, existing_label, (&label))); \
  printf(" ")
#define test_helper_graph_read_footer \
  db_status_success_if_no_more_data; \
  exit: \
  printf("\n"); \
  db_txn_abort_if_active(txn); \
  return (status);
status_t db_debug_display_all_relations(db_txn_t txn) {
  status_init;
  db_graph_records_t* records;
  db_graph_read_state_t state;
  records = 0;
  db_status_require_read_x(db_graph_select(txn, 0, 0, 0, 0, 0, (&state)));
  db_status_require_read_x(db_graph_read((&state), 0, (&records)));
  printf("all ");
  db_graph_selection_destroy((&state));
  db_debug_display_graph_records(records);
  db_graph_records_destroy(records);
exit:
  return (status);
};
#define test_helper_filter_ids_to_reader_suffix_integer( \
  left, right, label, ordinal) \
  ((left ? 8 : 0) | (right ? 4 : 0) | (label ? 2 : 0) | (ordinal ? 1 : 0))
/** assumes linearly set-plus-oneed ordinal integers starting at 1 and queries
 * for all or no ids */
b32 test_helper_estimate_graph_read_result_count(b32 left_count,
  b32 right_count,
  b32 label_count,
  db_ordinal_condition_t* ordinal) {
  b32 count = (left_count * right_count * label_count);
  b32 max;
  b32 min;
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
b32 test_helper_estimate_graph_read_btree_entry_count(b32 existing_left_count,
  b32 existing_right_count,
  b32 existing_label_count,
  db_ordinal_condition_t* ordinal) {
  b32 ordinal_min = 0;
  b32 ordinal_max = 0;
  if (ordinal) {
    ordinal_min = ordinal->min;
    ordinal_max = ordinal->max;
  };
  b32 label_left_count = 0;
  b32 left_right_count = 0;
  b32 right_left_count = 0;
  b32 ordinal_value = 1;
  b32 left_count = 0;
  b32 right_count = 0;
  b32 label_count = 0;
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
/** 1101 -> "1101" */
b8* test_helper_reader_suffix_integer_to_string(b8 a) {
  b8* result = malloc(40);
  result[0] = ((8 & a) ? '1' : '0');
  result[1] = ((4 & a) ? '1' : '0');
  result[2] = ((2 & a) ? '1' : '0');
  result[3] = ((1 & a) ? '1' : '0');
  result[4] = 0;
  return (result);
};