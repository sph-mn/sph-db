#include "./helper.c"
/* these values should not be below 3, or important cases would not be tested.
   the values should also not be so high that the linearly created ordinals exceed the size of the ordinal type.
   tip: reduce when debugging to make tests run faster */
uint32_t common_element_count = 3;
uint32_t common_label_count = 3;
#define db_env_types_extra_count 20
status_t test_open_empty(db_env_t* env) {
  status_declare;
  test_helper_assert(("env.open is true"), (1 == env->open));
  test_helper_assert(("env.root is set"), (0 == strcmp((env->root), test_helper_db_root)));
exit:
  return (status);
};
status_t test_statistics(db_env_t* env) {
  status_declare;
  db_statistics_t stat;
  db_txn_declare(env, txn);
  status_require(db_txn_begin((&txn)));
  status_require(db_statistics(txn, (&stat)));
  test_helper_assert("dbi-system contanis only one entry", (1 == stat.system.ms_entries));
exit:
  db_txn_abort_if_active(txn);
  return (status);
};
status_t test_type_create_get_delete(db_env_t* env) {
  status_declare;
  db_field_t fields[4];
  db_field_t* fields_2[4];
  db_fields_len_t i;
  db_type_t* type_1;
  db_type_t* type_2;
  db_type_t* type_1_1;
  db_type_t* type_2_1;
  /* create type-1 */
  status_require(db_type_create(env, "test-type-1", 0, 0, 0, (&type_1)));
  test_helper_assert("type id", (1 == type_1->id));
  test_helper_assert("type sequence", (1 == type_1->sequence));
  test_helper_assert("type field count", (0 == type_1->fields_len));
  /* create type-2 */
  db_field_set((fields[0]), db_field_type_int8, "test-field-1", 12);
  db_field_set((fields[1]), db_field_type_int8, "test-field-2", 12);
  db_field_set((fields[2]), db_field_type_string, "test-field-3", 12);
  db_field_set((fields[3]), db_field_type_string, "test-field-4", 12);
  status_require(db_type_create(env, "test-type-2", fields, 4, 0, (&type_2)));
  test_helper_assert("second type id", (2 == type_2->id));
  test_helper_assert("second type sequence", (1 == type_2->sequence));
  test_helper_assert("second type fields-len", (4 == type_2->fields_len));
  test_helper_assert("second type name", (0 == strcmp("test-type-2", (type_2->name))));
  /* test cached field values */
  for (i = 0; (i < type_2->fields_len); i = (1 + i)) {
    test_helper_assert("second type field name len equality", ((i + fields)->name_len == (i + type_2->fields)->name_len));
    test_helper_assert("second type field name equality", (0 == strcmp(((i + fields)->name), ((i + type_2->fields)->name))));
    test_helper_assert("second type type equality", ((i + fields)->type == (i + type_2->fields)->type));
  };
  /* test db-type-field-get */
  fields_2[0] = db_type_field_get(type_2, "test-field-1");
  fields_2[1] = db_type_field_get(type_2, "test-field-2");
  fields_2[2] = db_type_field_get(type_2, "test-field-3");
  /* test fixed count and offsets */
  test_helper_assert("fixed count", (2 == type_2->fields_fixed_count));
  test_helper_assert("fixed offsets", ((0 == (type_2->fields_fixed_offsets)[0]) && (1 == (type_2->fields_fixed_offsets)[1])));
  /* test type-field-get */
  test_helper_assert("type-field-get", (fields_2[0] == (0 + type_2->fields)) && ((0 + type_2->fields) == fields_2[1]) && (fields_2[1] == (1 + type_2->fields)) && ((1 + type_2->fields) == fields_2[2]) && (fields_2[2] == (2 + type_2->fields)) && ((2 + type_2->fields) == fields_2[3]) && (fields_2[3] == (3 + type_2->fields)));
  /* test type-get */
  test_helper_assert("non existent type", !db_type_get(env, "test-type-x"));
  type_1_1 = db_type_get(env, "test-type-1");
  type_2_1 = db_type_get(env, "test-type-2");
  test_helper_assert("existent types", (type_1_1 && type_2_1));
  test_helper_assert("existent type ids", ((type_1->id == type_1_1->id) && (type_2->id == type_2_1->id)));
  test_helper_assert("existent types", (db_type_get(env, "test-type-1") && db_type_get(env, "test-type-2")));
  /* test type-delete */
  status_require((db_type_delete(env, (type_1->id))));
  status_require((db_type_delete(env, (type_2->id))));
  type_1_1 = db_type_get(env, "test-type-1");
  type_2_1 = db_type_get(env, "test-type-2");
  test_helper_assert("type-delete type-get", !(type_1_1 || type_2_1));
exit:
  return (status);
};
/** create several types, particularly to test automatic env:types array resizing */
status_t test_type_create_many(db_env_t* env) {
  status_declare;
  db_type_id_t i;
  uint8_t name[255];
  db_type_t* type;
  /* 10 times as many as there is extra room left for new types in env:types */
  for (i = 0; (i < (10 * db_env_types_extra_count)); i = (1 + i)) {
    sprintf(name, "test-type-%lu", i);
    status_require(db_type_create(env, name, 0, 0, 0, (&type)));
  };
exit:
  return (status);
};
status_t test_sequence(db_env_t* env) {
  status_declare;
  size_t i;
  db_id_t id;
  db_id_t prev_id;
  db_type_id_t prev_type_id;
  db_type_t* type;
  db_type_id_t type_id;
  /* node sequence. note that sequences only persist through data inserts */
  status_require(db_type_create(env, "test-type", 0, 0, 0, (&type)));
  type->sequence = (db_element_id_limit - 100);
  prev_id = db_id_add_type((db_element_id_limit - 100 - 1), (type->id));
  for (i = db_element_id_limit; (i <= db_element_id_limit); i = (i + 1)) {
    status = db_sequence_next(env, (type->id), (&id));
    if (db_element_id_limit <= db_id_element((1 + prev_id))) {
      test_helper_assert("node sequence is limited", (db_status_id_max_element_id == status.id));
      status.id = status_id_success;
    } else {
      test_helper_assert("node sequence is monotonically increasing", (status_is_success && (1 == (id - prev_id))));
    };
    prev_id = id;
  };
  /* system sequence. test last, otherwise type ids would be exhausted */
  prev_type_id = type->id;
  for (i = type->id; (i <= db_type_id_limit); i = (i + 1)) {
    status = db_sequence_next_system(env, (&type_id));
    if (db_type_id_limit <= (1 + prev_type_id)) {
      test_helper_assert("system sequence is limited", (db_status_id_max_type_id == status.id));
      status.id = status_id_success;
    } else {
      test_helper_assert("system sequence is monotonically increasing", (status_is_success && (1 == (type_id - prev_type_id))));
    };
    prev_type_id = type_id;
  };
exit:
  return (status);
};
status_t test_open_nonempty(db_env_t* env) {
  status_declare;
  status_require(test_type_create_get_delete(env));
  status_require(test_helper_reset(env, 1));
exit:
  return (status);
};
/** test features related to the combination of element and type id to node id */
status_t test_id_construction(db_env_t* env) {
  status_declare;
  /* id creation */
  db_type_id_t type_id;
  type_id = (db_type_id_limit / 2);
  test_helper_assert("type-id-size + element-id-size = id-size", (sizeof(db_id_t) == (sizeof(db_type_id_t) + db_size_element_id)));
  test_helper_assert("type and element masks not conflicting", !(db_id_type_mask & db_id_element_mask));
  test_helper_assert("type-id-mask | element-id-mask = id-mask", (db_id_mask == (db_id_type_mask | db_id_element_mask)));
  test_helper_assert("id type", (type_id == db_id_type(db_id_add_type(db_element_id_limit, type_id))));
  /* take a low value to be compatible with different configurations */
  test_helper_assert("id element", (254 == db_id_element(db_id_add_type(254, type_id))));
exit:
  return (status);
};
status_t test_graph_read(db_env_t* env) {
  status_declare;
  db_txn_declare(env, txn);
  test_helper_graph_read_data_t data;
  status_require(test_helper_graph_read_setup(env, common_element_count, common_element_count, common_label_count, (&data)));
  db_txn_begin((&txn));
  status_require(test_helper_graph_read_one(txn, data, 0, 0, 0, 0, 0));
  status_require(test_helper_graph_read_one(txn, data, 1, 0, 0, 0, 0));
  status_require(test_helper_graph_read_one(txn, data, 0, 1, 0, 0, 0));
  status_require(test_helper_graph_read_one(txn, data, 1, 1, 0, 0, 0));
  status_require(test_helper_graph_read_one(txn, data, 0, 0, 1, 0, 0));
  status_require(test_helper_graph_read_one(txn, data, 1, 0, 1, 0, 0));
  status_require(test_helper_graph_read_one(txn, data, 0, 1, 1, 0, 0));
  status_require(test_helper_graph_read_one(txn, data, 1, 1, 1, 0, 0));
  status_require(test_helper_graph_read_one(txn, data, 1, 0, 0, 1, 0));
  status_require(test_helper_graph_read_one(txn, data, 1, 1, 0, 1, 0));
  status_require(test_helper_graph_read_one(txn, data, 1, 0, 1, 1, 0));
  status_require(test_helper_graph_read_one(txn, data, 1, 1, 1, 1, 0));
exit:
  db_txn_abort_if_active(txn);
  test_helper_graph_read_teardown((&data));
  return (status);
};
/** some assertions depend on the correctness of graph-read */
status_t test_graph_delete(db_env_t* env) {
  status_declare;
  test_helper_graph_delete_data_t data;
  status_require(test_helper_graph_delete_setup(env, common_element_count, common_element_count, common_label_count, (&data)));
  status_require(test_helper_graph_delete_one(data, 1, 0, 0, 0));
  status_require(test_helper_graph_delete_one(data, 0, 1, 0, 0));
  status_require(test_helper_graph_delete_one(data, 1, 1, 0, 0));
  status_require(test_helper_graph_delete_one(data, 0, 0, 1, 0));
  status_require(test_helper_graph_delete_one(data, 1, 0, 1, 0));
  status_require(test_helper_graph_delete_one(data, 0, 1, 1, 0));
  status_require(test_helper_graph_delete_one(data, 1, 1, 1, 0));
  status_require(test_helper_graph_delete_one(data, 1, 0, 0, 1));
  status_require(test_helper_graph_delete_one(data, 1, 1, 0, 1));
  status_require(test_helper_graph_delete_one(data, 1, 0, 1, 1));
  status_require(test_helper_graph_delete_one(data, 1, 1, 1, 1));
exit:
  return (status);
};
boolean node_matcher(db_id_t id, db_node_t data, void* matcher_state) {
  *((uint8_t*)(matcher_state)) = 1;
  return (1);
};
status_t test_node_select(db_env_t* env) {
  status_declare;
  db_txn_declare(env, txn);
  i_array_declare(ids, db_ids_t);
  uint8_t value_1;
  uint8_t matcher_state;
  db_node_t data;
  db_node_selection_t selection;
  uint32_t btree_size_before_delete;
  uint32_t btree_size_after_delete;
  db_type_t* type;
  db_node_values_t* values;
  db_id_t* node_ids;
  uint32_t node_ids_len;
  uint32_t values_len;
  /* create nodes */
  status_require(test_helper_create_type_1(env, (&type)));
  status_require(test_helper_create_values_1(env, type, (&values), (&values_len)));
  status_require(test_helper_create_nodes_1(env, values, (&node_ids), (&node_ids_len)));
  value_1 = *((uint8_t*)((((values[0]).data)[0]).data));
exit:
  i_array_free(ids);
  db_txn_abort_if_active(txn);
  return (status);
};
status_t test_helper_dbi_entry_count(db_txn_t txn, MDB_dbi dbi, size_t* result) {
  status_declare;
  MDB_stat stat;
  db_mdb_status_require((mdb_stat((txn.mdb_txn), dbi, (&stat))));
  *result = stat.ms_entries;
exit:
  return (status);
};
int main() {
  db_env_t* env;
  status_declare;
  db_env_new((&env));
  test_helper_test_one(test_open_empty, env);
  test_helper_test_one(test_statistics, env);
  test_helper_test_one(test_id_construction, env);
  test_helper_test_one(test_sequence, env);
  test_helper_test_one(test_type_create_get_delete, env);
  test_helper_test_one(test_type_create_many, env);
  test_helper_test_one(test_open_nonempty, env);
  test_helper_test_one(test_graph_read, env);
  test_helper_test_one(test_graph_delete, env);
exit:
  if (status_is_success) {
    printf(("--\ntests finished successfully.\n"));
  } else {
    printf(("\ntests failed. %d %s\n"), (status.id), db_status_description(status));
  };
  return ((status.id));
};