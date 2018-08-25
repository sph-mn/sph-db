// work in progress, not yet working.
// example tutorial code for sph-db.
// compile like "gcc example-usage.c -o /tmp/sph-db-example -lsph-db"

#include <sph-db.h>
#include <stdio.h>

status_t collections() {
  // examples of how to use the dg_ids_*, dg_nodes_ and dg_relations_* arrays
  status_declare;
  // declare a new ids array variable
  db_ids_declare(ids);
  // allocate memory for three db_id_t elements
  status_require(db_ids_new(3, &ids));
  // add ids from left to right
  db_ids_add(ids, 10);
  db_ids_add(ids, 15);
  db_ids_add(ids, 28);
  // get the first element
  db_ids_get(ids);
  // the second element
  db_ids_forward(ids);
  db_ids_get(ids);
  // reset current element to the first element
  db_ids_rewind(ids);
  // get element at specific index
  db_ids_get_at(ids, 2);
  db_ids_free(ids);
exit:
  return status;
}

status_t create_type(db_env_t* env, db_type_t** result_type) {
  printf("create type\n");
  status_declare;
  db_field_t fields[4];
  db_type_t* type;
  // set field.type, field.name and field.name_len
  db_field_set(fields[0], db_field_type_uint8, "field-name-1", 12);
  db_field_set(fields[1], db_field_type_int8, "field-name-2", 12);
  db_field_set(fields[2], db_field_type_string, "field-name-3", 12);
  db_field_set(fields[3], db_field_type_string, "field-name-4", 12);
  // arguments: db_env_t*, type_name, db_field_t*, field_count, flags, result
  status_require(db_type_create(env, "test-type", fields, 4, 0, &type));
  *result_type = type;
  printf("type id: %u\n", type->id);
exit:
  return status;
}

status_t create_nodes(db_env_t* env, db_type_t* type) {
  printf("create nodes\n");
  status_declare;
  db_txn_declare(env, txn);
  db_node_values_declare(values);
  db_id_t id_1;
  db_id_t id_2;
  uint8_t value_1 = 11;
  int8_t value_2 = -128;
  uint8_t* value_3 = "abc";
  uint8_t* value_4 = "abcde";
  status_require(db_node_values_new(type, &values));
  // arguments: db_node_values_t*, field_index, value_address, size.
  // size is ignored for fixed length types
  db_node_values_set(&values, 0, &value_1, 0);
  db_node_values_set(&values, 1, &value_2, 0);
  // strings can be stored with or without a trailing null character
  db_node_values_set(&values, 2, value_3, 3);
  db_node_values_set(&values, 3, value_4, 5);
  status_require(db_txn_write_begin(&txn));
  status_require(db_node_create(txn, values, &id_1));
  printf("created node with id %u\n", id_1);
  // set a different value for the second field
  value_2 = 123;
  db_node_values_set(&values, 1, &value_2, 0);
  status_require(db_node_create(txn, values, &id_2));
  printf("created node with id %u\n", id_2);
  db_txn_commit(&txn);
exit:
  db_node_values_free(&values);
  return status;
}

boolean node_matcher(db_type_t* type, db_node_t node, void* matcher_state) {
  db_node_value_t field_data;
  field_data = db_node_ref(type, node, 2);
  *((uint8_t*)(matcher_state)) = 1;
  return 1;
};

status_t read_nodes(db_env_t* env, db_type_t* type) {
  // by id
  printf("read nodes by id\n");
  status_declare;
  db_txn_declare(env, txn);
  db_ids_declare(ids);
  db_nodes_declare(nodes);
  db_node_value_t field_data;
  db_node_t node;
  status_require(db_nodes_new(2, &nodes));
  status_require(db_ids_new(3, &ids));
  db_ids_add(ids, 1);
  db_ids_add(ids, 2);
  db_ids_add(ids, 3);
  status_require(db_txn_begin(&txn));
  status_require_read(db_node_get(txn, ids, &nodes));
  if(db_nodes_length(nodes)) {
    // arguments: type, db-node-t, field_index
    node = db_nodes_get_at(nodes, 0);
    field_data = db_node_ref(type, node, 1);
    // field_data.data: void*, field_data.size: size_t
  }

  // by type
  printf("read nodes by type\n");
  db_node_selection_declare(selection);
  db_nodes_clear(nodes);
  // arguments: db_txn_t, db_type_t*, offset, matcher, matcher_state, selection_address));
  status_require(db_node_select(txn, type, 0, 0, 0, &selection));
  status_require_read(db_node_read(selection, 3, &nodes));
  printf("read %lu nodes\n", db_nodes_length(nodes));
  while(db_nodes_in_range(nodes)) {
    node = db_nodes_get(nodes);
    field_data = db_node_ref(type, node, 0);
    db_nodes_forward(nodes);
  }
  // by type and custom matcher function
  printf("read nodes by matcher function\n");
  uint8_t matcher_state = 0;
  status_require(db_node_select(txn, type, 0, node_matcher, &matcher_state, &selection));
exit:
  db_txn_abort_if_active(txn);
  db_node_selection_finish(&selection);
  db_ids_free(ids);
  db_nodes_free(nodes);
  return status;
}

status_t create_relations(db_env_t* env) {
  printf("create relations\n");
  status_declare;
  db_txn_declare(env, txn);
  db_ids_declare(left);
  db_ids_declare(right);
  db_ids_declare(label);
  status_require(db_ids_new(3, &left));
  status_require(db_ids_new(2, &right));
  status_require(db_ids_new(2, &label));
  db_ids_add(left, 1);
  db_ids_add(left, 2);
  db_ids_add(left, 3);
  db_ids_add(right, 4);
  db_ids_add(right, 5);
  db_ids_add(label, 6);
  db_ids_add(label, 7);
  // create relations between all given left and right nodes for each label. relations = left * right * label
  db_txn_write_begin(&txn);
  status_require(db_graph_ensure(txn, left, right, label, 0, 0));
  db_txn_commit(&txn);
exit:
  db_txn_abort_if_active(txn);
  db_ids_free(left);
  db_ids_free(right);
  db_ids_free(label);
  return status;
}

status_t read_relations(db_env_t* env) {
  printf("read relations\n");
  status_declare;
  db_txn_declare(env, txn);
  db_ids_declare(ids_left);
  db_ids_declare(ids_label);
  db_relations_declare(relations);
  db_graph_selection_declare(selection);
  db_relation_t relation;
  status_require(db_ids_new(1, &ids_left));
  status_require(db_ids_new(1, &ids_label));
  db_relations_new(10, &relations);
  // node ids to be used to filter
  db_ids_add(ids_left, 123);
  db_ids_add(ids_label, 456);
  // select relations whose left side is in "ids_left" and label in "ids_label".
  db_txn_begin(&txn);
  status_require(db_graph_select(txn, &ids_left, 0, &ids_label, 0, 0, &selection));
  // read 2 of the selected relations
  status_require(db_graph_read(&selection, 2, &relations));
  // read as many remaining matches as fit into the relations array
  status_require_read(db_graph_read(&selection, 0, &relations));
  db_graph_selection_finish(&selection);
  // display relations. "ordinal" might not be set unless a filter for left was used
  while(db_relations_in_range(relations)) {
    relation = db_relations_get(relations);
    printf("relation: %lu %lu %lu %lu\n", relation.left, relation.label, relation.ordinal, relation.right);
    db_relations_forward(relations);
  };
exit:
  db_txn_abort_if_active(txn);
  db_ids_free(ids_left);
  db_ids_free(ids_label);
  db_relations_free(relations);
}

status_t create_index(db_env_t* env, db_type_t* type, db_index_t** index) {
  printf("create index\n");
  status_declare;
  // array of field indices to index
  db_fields_len_t fields[2] = {1, 2};
  status_require(db_index_create(env, type, fields, 2));
  *index = db_index_get(type, fields, 2);
exit:
  return status;
}

status_t read_ids_from_index(db_env_t* env, db_type_t* type, db_index_t* index) {
  printf("read ids from index\n");
  status_declare;
  db_txn_declare(env, txn);
  db_index_selection_declare(selection);
  db_ids_declare(ids);
  db_node_values_declare(values);
  uint8_t value_1 = 11;
  uint8_t* value_2 = "abc";
  status_require(db_ids_new(2, &ids));
  status_require(db_node_values_new(type, &values));
  db_node_values_set(&values, 1, &value_1, 0);
  db_node_values_set(&values, 2, &value_2, 3);
  // values for unused fields will be ignored.
  db_txn_begin(&txn);
  status_require_read(db_index_select(txn, *index, values, &selection));
  status_require_read(db_index_read(selection, 2, &ids));
  db_index_selection_finish(&selection);
exit:
  return status;
}

status_t read_nodes_from_index(db_env_t* env, db_type_t* type, db_index_t* index) {
  printf("read nodes from index\n");
  status_declare;
  db_txn_declare(env, txn);
  db_nodes_declare(nodes);
  db_node_values_declare(values);
  db_node_index_selection_declare(selection);
  status_require(db_nodes_new(2, &nodes));
  status_require(db_node_values_new(type, &values));
  db_node_t node;
  uint8_t value_1 = 11;
  uint8_t* value_2 = "abc";
  db_node_values_set(&values, 1, &value_1, 0);
  db_node_values_set(&values, 2, &value_2, 3);
  db_txn_begin(&txn);
  status_require(db_node_index_select(txn, *index, values, &selection));
  status_require(db_node_index_read(selection, 1, &nodes));
  node = db_nodes_get(nodes);
  db_node_index_selection_finish(&selection);
exit:
  db_txn_abort_if_active(txn);
  return status;
}

status_t create_virtual_nodes(db_env_t* env) {
  printf("create virtual nodes\n");
  db_id_t id;
  uint32_t data;
  // create virtual node type
  status_declare;
  db_field_t fields;
  db_type_t* type;
  // set field.type, field.name and field.name_len
  db_field_set(fields, db_field_type_uint16, 0, 0);
  // arguments: db_env_t*, type_name, db_field_t*, field_count, flags, result
  status_require(db_type_create(env, "test-type", &fields, 1, 0, &type));
  // create nodes
  data = 123;
  id = db_node_virtual_from_uint(type->id, data);
  data = db_node_virtual_data(id, uint32_t);
  db_type_is_virtual(type);
exit:
  return status;
}

int main() {
  status_declare;
  db_env_declare(env);
  status_require(db_env_new(&env));
  // the database file will be created if it does not exist
  status_require(db_open("/tmp/sph-db-example-data", 0, env));
  db_type_t* type;
  db_index_t* index;
  status_require(create_type(env, &type));
  status_require(create_nodes(env, type));
  status_require(read_nodes(env, type));
  status_require(create_relations(env));
  status_require(read_relations(env));
  status_require(create_index(env, type, &index));
  status_require(read_ids_from_index(env, type, index));
  status_require(read_nodes_from_index(env, type, index));
  status_require(create_virtual_nodes(env));
  db_close(env);
  printf("%s\n", db_status_description(status));
exit:
  if(status_is_failure) {
    printf("error: %s\n", db_status_description(status));
  }
  return status.id;
}
