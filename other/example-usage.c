// work in progress, not yet working.
// example tutorial code for sph-db.
// compile like "gcc example-usage.c -o /tmp/sph-db-example -lsph-db"

#include <sph-db.h>
#include <stdio.h>

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
  value_2 = 123;
  db_node_values_set(&values, 1, &value_2, 0);
  status_require(db_node_create(txn, values, &id_2));
  printf("created node with id %u\n", id_2);
  db_txn_commit(&txn);
exit:
  db_node_values_free(&values);
  return status;
}

status_t collections() {
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

status_t read_nodes(db_env_t* env, db_type_t* type) {
  // by unique identifier
  status_declare;
  db_txn_declare(env, txn);
  db_ids_declare(ids);
  db_nodes_declare(nodes);
  db_node_value_t field_data;
  status_require(db_nodes_new(2, &nodes));
  status_require(db_ids_new(3, &ids));
  db_ids_add(ids, 1);
  db_ids_add(ids, 2);
  db_ids_add(ids, 3);
  status_require_read(db_node_get(txn, ids, &nodes));
  if(db_status_id_notfound != status.id) {
    // arguments: type, db-node-t, field_index
    field_data = db_node_ref(type, db_nodes_get_at(nodes, 0), 1);
    // field_data.data: void*, field_data.size: size_t
  }
  db_ids_free(ids);
  db_nodes_free(nodes);
exit:
  return status;
}

int main() {
  status_declare;
  db_env_declare(env);
  status_require(db_env_new(&env));
  // the database file will be created if it does not exist
  status_require(db_open("/tmp", 0, env));

  db_type_t* type;
  status_require(create_type(env, &type));
  status_require(create_nodes(env, type));
  //status_require(read_nodes(env, &type));
exit:
  db_close(env);
  printf("%s\n", db_status_description(status));
  return status.id;
}
