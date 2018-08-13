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
status_t test_node_create(db_env_t* env) {
  status_declare;
  db_txn_declare(env, txn);
  db_node_data_t field_data;
  db_fields_len_t field_index;
  db_ids_t ids;
  db_id_t id_1;
  db_id_t id_2;
  db_node_data_t node_data_1;
  db_node_data_t node_data_2;
  size_t size_1;
  size_t size_2;
  boolean exists;
  db_type_t* type;
  uint8_t value_1;
  int8_t value_2;
  db_node_values_t values_1;
  db_node_values_t values_2;
  uint8_t* value_3 = ((uint8_t*)("abc"));
  uint8_t* value_4 = ((uint8_t*)("abcde"));
  status_require(test_helper_create_type_1(env, (&type)));
  /* prepare node values */
  status_require(db_node_values_new(type, (&values_1)));
  value_1 = 11;
  value_2 = -128;
  db_node_values_set((&values_1), 0, (&value_1), 0);
  db_node_values_set((&values_1), 1, (&value_2), 0);
  db_node_values_set((&values_1), 2, value_3, 3);
  db_node_values_set((&values_1), 3, value_4, 5);
  /* test node values/data conversion */
  db_node_values_to_data(values_1, (&node_data_1));
  test_helper_assert(("node-values->data size"), (((2 * sizeof(db_data_len_t)) + 10) == node_data_1.size));
  db_node_data_to_values(type, node_data_1, (&values_2));
  test_helper_assert(("node-data->values type equal"), (values_1.type == values_2.type));
  test_helper_assert(("node-data->values size equal"), ((((values_1.data)[0]).size == ((values_2.data)[0]).size) && (((values_1.data)[1]).size == ((values_2.data)[1]).size) && (((values_1.data)[2]).size == ((values_2.data)[2]).size) && (((values_1.data)[3]).size == ((values_2.data)[3]).size)));
  test_helper_assert(("node-data->values data equal 1"), ((0 == memcmp(value_3, (((values_1.data)[2]).data), 3)) && (0 == memcmp(value_4, (((values_1.data)[3]).data), 5))));
  for (field_index = 0; (field_index < type->fields_len); field_index = (1 + field_index)) {
    size_1 = ((values_1.data)[field_index]).size;
    size_2 = ((values_2.data)[field_index]).size;
    test_helper_assert(("node-data->values data equal 2"), (0 == memcmp((((values_1.data)[field_index]).data), (((values_2.data)[field_index]).data), ((size_1 < size_2) ? size_2 : size_1))));
  };
  db_node_values_to_data(values_2, (&node_data_2));
  test_helper_assert(("node-values->data"), ((node_data_1.size == node_data_2.size) && (0 == memcmp((node_data_1.data), (node_data_2.data), ((node_data_1.size < node_data_2.size) ? node_data_2.size : node_data_1.size)))));
  /* test node-data-ref */
  field_data = db_node_data_ref(type, node_data_1, 3);
  test_helper_assert("node-data-ref-1", ((5 == field_data.size) && (0 == memcmp(value_4, (field_data.data), (field_data.size)))));
  /* test actual node creation */
  status_require(db_txn_write_begin((&txn)));
  status_require(db_node_create(txn, values_1, (&id_1)));
  test_helper_assert("element id 1", (1 == db_id_element(id_1)));
  status_require(db_node_create(txn, values_1, (&id_2)));
  test_helper_assert("element id 2", (2 == db_id_element(id_2)));
  status_require(db_txn_commit((&txn)));
  status_require(db_txn_begin((&txn)));
  /* test node-get */
  status_require(db_node_get(txn, id_1, (&node_data_1)));
  field_data = db_node_data_ref(type, node_data_1, 1);
  test_helper_assert("node-data-ref-2", ((sizeof(int8_t) == field_data.size) && (value_2 == *((int8_t*)(field_data.data)))));
  field_data = db_node_data_ref(type, node_data_1, 3);
  test_helper_assert("node-data-ref-3", ((5 == field_data.size) && (0 == memcmp(value_4, (field_data.data), (field_data.size)))));
  status_require(db_node_get(txn, id_2, (&node_data_1)));
  status = db_node_get(txn, 9999, (&node_data_1));
  test_helper_assert("node-get non-existing", (db_status_id_notfound == status.id));
  status.id = status_id_success;
  /* test node-exists */
  i_array_allocate_db_ids_t((&ids), 3);
  i_array_add(ids, id_1);
  i_array_add(ids, id_2);
  status_require(db_node_exists(txn, ids, (&exists)));
  test_helper_assert("node-exists exists", exists);
  i_array_add(ids, 9999);
  status_require(db_node_exists(txn, ids, (&exists)));
  test_helper_assert("node-exists does not exist", !exists);
  db_txn_abort((&txn));
exit:
  db_txn_abort_if_active(txn);
  return (status);
};
boolean node_matcher(db_id_t id, db_node_data_t data, void* matcher_state) {
  *((uint8_t*)(matcher_state)) = 1;
  return (1);
};
status_t test_node_select(db_env_t* env) {
  status_declare;
  db_txn_declare(env, txn);
  i_array_declare(ids, db_ids_t);
  uint8_t value_1;
  uint8_t matcher_state;
  db_node_data_t data;
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
  status_require(db_txn_begin((&txn)));
  /* type */
  status_require(db_node_select(txn, 0, type, 0, 0, 0, (&selection)));
  status_require(db_node_next((&selection)));
  data = db_node_ref((&selection), 0);
  test_helper_assert("node-ref size", (1 == data.size));
  test_helper_assert("node-ref value", (value_1 == *((uint8_t*)(data.data))));
  test_helper_assert("current id set", (db_id_element((selection.current_id))));
  status_require(db_node_next((&selection)));
  status_require(db_node_next((&selection)));
  status = db_node_next((&selection));
  test_helper_assert("all type entries found", (db_status_id_notfound == status.id));
  status.id = status_id_success;
  db_node_selection_finish((&selection));
  /* ids */
  if (!i_array_allocate_db_ids_t((&ids), 5)) {
    status_set_id_goto(db_status_id_memory);
  };
  i_array_add(ids, (node_ids[0]));
  i_array_add(ids, 9999);
  i_array_add(ids, (node_ids[1]));
  i_array_add(ids, (node_ids[2]));
  i_array_add(ids, (node_ids[3]));
  status_require(db_node_select(txn, (&ids), 0, 0, 0, 0, (&selection)));
  status_require(db_node_next((&selection)));
  data = db_node_ref((&selection), 3);
  db_node_selection_finish((&selection));
  /* matcher */
  matcher_state = 0;
  status_require(db_node_select(txn, 0, type, 0, node_matcher, (&matcher_state), (&selection)));
  status_require(db_node_next((&selection)));
  data = db_node_ref((&selection), 0);
  test_helper_assert("node-ref size", (1 == data.size));
  test_helper_assert("matcher-state", (1 == matcher_state));
  db_node_selection_finish((&selection));
  /* type and skip */
  status_require(db_node_select(txn, 0, type, 0, 0, 0, (&selection)));
  status_require(db_node_skip((&selection), 2));
  status_require(db_node_next((&selection)));
  status = db_node_next((&selection));
  test_helper_assert("entries skipped", (db_status_id_notfound == status.id));
  status.id = status_id_success;
  db_node_selection_finish((&selection));
  db_txn_abort((&txn));
  status_require(db_txn_write_begin((&txn)));
  db_debug_count_all_btree_entries(txn, (&btree_size_before_delete));
  status_require((db_node_update(txn, (node_ids[1]), (values[1]))));
  status_require(db_node_delete(txn, (&ids)));
  status_require(db_txn_commit((&txn)));
  status_require(db_txn_begin((&txn)));
  db_debug_count_all_btree_entries(txn, (&btree_size_after_delete));
  db_txn_abort((&txn));
  test_helper_assert("after size", (4 == (btree_size_before_delete - btree_size_after_delete)));
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
status_t test_index(db_env_t* env) {
  status_declare;
  db_txn_declare(env, txn);
  db_fields_len_t fields[2] = { 1, 2 };
  db_fields_len_t fields_len;
  db_type_t* type;
  db_index_t* index;
  uint8_t* index_name;
  size_t index_name_len;
  db_node_values_t* values;
  uint32_t values_len;
  void* key_data;
  size_t key_size;
  db_id_t* node_ids;
  uint32_t node_ids_len;
  db_index_selection_t selection;
  uint8_t* index_name_expected = "i-1-1-2";
  fields_len = 2;
  status_require(test_helper_create_type_1(env, (&type)));
  status_require(test_helper_create_values_1(env, type, (&values), (&values_len)));
  /* test with no existing nodes */
  status_require((db_index_name((type->id), fields, fields_len, (&index_name), (&index_name_len))));
  test_helper_assert("index name", (0 == strcmp(index_name_expected, index_name)));
  status_require(db_index_create(env, type, fields, fields_len));
  index = db_index_get(type, fields, fields_len);
  test_helper_assert("index-get not null", index);
  test_helper_assert("index-get fields-len", (fields_len == index->fields_len));
  test_helper_assert("index-get fields set", ((1 == (index->fields)[0]) && (2 == (index->fields)[1])));
  status_require((db_index_key(env, (*index), (values[0]), (&key_data), (&key_size))));
  test_helper_assert("key size", (4 == key_size));
  test_helper_assert("key memory ref", (((uint8_t*)(key_data))[3]));
  /* test node index update */
  status_require(test_helper_create_nodes_1(env, values, (&node_ids), (&node_ids_len)));
  /* test delete */
  status_require(db_index_delete(env, index));
  test_helper_assert("index-delete", !db_index_get(type, fields, fields_len));
  /* test with existing nodes */
  status_require(db_index_create(env, type, fields, fields_len));
  /* this call exposed a memory error before */
  status_require((db_index_name((type->id), fields, fields_len, (&index_name), (&index_name_len))));
  index = db_index_get(type, fields, fields_len);
  test_helper_assert("index-get not null 2", index);
  /* test index select */
  db_txn_begin((&txn));
  status_require((db_index_select(txn, (*index), (values[1]), (&selection))));
  test_helper_assert("index-select type-id 1", (type->id == db_id_type((selection.current))));
  status_require(db_index_next(selection));
  test_helper_assert("index-select type-id 2", (type->id == db_id_type((selection.current))));
  status = db_index_next(selection);
  test_helper_assert("index-select next end", (db_status_id_notfound == status.id));
  status.id = status_id_success;
  db_index_selection_finish((&selection));
  db_txn_abort((&txn));
  db_txn_begin((&txn));
  status_require(db_index_rebuild(env, index));
  status_require((db_index_select(txn, (*index), (values[0]), (&selection))));
  test_helper_assert("index-select type-id 1", (type->id == db_id_type((selection.current))));
  db_txn_abort((&txn));
  db_txn_begin((&txn));
  db_node_index_selection_t node_index_selection;
  status_require((db_node_index_select(txn, (*index), (values[0]), (&node_index_selection))));
  db_node_index_selection_finish((&node_index_selection));
  db_txn_abort((&txn));
exit:
  db_txn_abort_if_active(txn);
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
  test_helper_test_one(test_node_create, env);
  test_helper_test_one(test_node_select, env);
  test_helper_test_one(test_index, env);
exit:
  if (status_is_success) {
    printf(("--\ntests finished successfully.\n"));
  } else {
    printf(("\ntests failed. %d %s\n"), (status.id), db_status_description(status));
  };
  return ((status.id));
};