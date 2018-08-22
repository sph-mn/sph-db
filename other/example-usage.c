// work in progress, not yet working.
// example tutorial code for sph-db.
// compile like "gcc example.c -o sph-db-example -llmdb -lsph-db"

#include "<sph-db.h>"

int main() {
  status_declare;

  // initialisation
  db_env_t* env;
  db_env_new(&env);
  // the database file will be created if it does not exist
  status_require(db_open("/tmp/example", 0, env));
  // code that makes use of the database ...
  db_close(&env);

  // create a type
  db_field_t fields[4];
  db_type_t* type;
  // set field.type, field.name and field.name_len
  db_field_set(fields[0], db_field_type_uint8, "field-name-1", 12);
  db_field_set(fields[1], db_field_type_int8, "field-name-2", 12);
  db_field_set(fields[2], db_field_type_string, "field-name-3", 12);
  db_field_set(fields[3], db_field_type_string, "field-name-4", 12);
  // arguments: db_env_t*, type_name, db_field_t*, field_count, flags, result
  status_require(db_type_create(env, "test-type", fields, 4, 0, &type));

  // create nodes
  db_node_values_t values;
  db_id_t id_1;
  db_id_t id_2;
  uint8_t value_1 = 11;
  i8 value_2 = -128;
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
  status_require(db_node_create(txn, values, &id_1));
  db_node_values_set(&values, 1, &value_1, 0);
  status_require(db_node_create(txn, values, &id_2));
  db_node_values_free(&values);

  // array data types
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

// read nodes
// by unique identifier
  db_ids_declare(ids);
  db_nodes_declare(nodes);
  db_node_value_t field_data;
  status_require(db_nodes_new(2, &nodes));
  status_require(db_ids_new(3, &ids));
  db_ids_add(ids, 10);
  db_ids_add(ids, 15);
  db_ids_add(ids, 28);
  status_require_read(db_node_get(txn, ids, &nodes));
  if(db_status_id_notfound != status.id) {
  // arguments: type, db-node-t, field_index
  field_data = db_node_ref(type, db_nodes_get_at(nodes, 0), 1);
  // field_data.data: void*, field_data.size: size_t
}
db_ids_free(ids);
db_nodes_free(nodes);

exit:
  return status.id;
}
