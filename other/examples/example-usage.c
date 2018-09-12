// example tutorial code for sph-db.
// compile like "gcc example-usage.c -o /tmp/sph-db-example -lsph-db".
// see "compile-and-run.sh"

#include <sph-db.h>

// only needed for printf
#include <stdio.h>

status_t collections() {
  // examples of how to use the dg_ids_*, dg_records_ and dg_relations_* arrays
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
  db_field_set(fields[0], db_field_type_uint8f, "field-name-1", 12);
  db_field_set(fields[1], db_field_type_int8f, "field-name-2", 12);
  db_field_set(fields[2], db_field_type_string8, "field-name-3", 12);
  db_field_set(fields[3], db_field_type_string8, "field-name-4", 12);
  // arguments: db_env_t*, type_name, db_field_t*, field_count, flags, result
  status_require(db_type_create(env, "test-type-name", fields, 4, 0, &type));
  *result_type = type;
  printf("type id: %u\n", type->id);
exit:
  return status;
}

status_t create_records(db_env_t* env, db_type_t* type) {
  printf("create records\n");
  // declarations
  status_declare;
  db_txn_declare(env, txn);
  db_record_values_declare(values);
  db_id_t id_1;
  db_id_t id_2;
  uint8_t value_1 = 11;
  int8_t value_2 = -128;
  uint8_t* value_3 = "abc";
  uint8_t* value_4 = "abcde";
  status_require(db_record_values_new(type, &values));
  // set field values.
  // size argument is ignored for fixed length types
  // arguments: db_record_values_t*, field_index, value_address, size.
  db_record_values_set(&values, 0, &value_1, 0);
  db_record_values_set(&values, 1, &value_2, 0);
  // strings can be stored with or without a trailing null character
  db_record_values_set(&values, 2, value_3, 3);
  db_record_values_set(&values, 3, value_4, 5);
  status_require(db_txn_write_begin(&txn));
  status_require(db_record_create(txn, values, &id_1));
  printf("created record with id %u\n", id_1);
  // set a different value for the second field
  value_2 = 123;
  db_record_values_set(&values, 1, &value_2, 0);
  status_require(db_record_create(txn, values, &id_2));
  printf("created record with id %u\n", id_2);
  db_txn_commit(&txn);
exit:
  db_record_values_free(&values);
  return status;
}

boolean record_matcher(db_type_t* type, db_record_t record, void* matcher_state) {
  db_record_value_t field_data;
  field_data = db_record_ref(type, record, 2);
  *((uint8_t*)(matcher_state)) = 1;
  return 1;
};

status_t read_records(db_env_t* env, db_type_t* type) {
  // by id
  printf("read records by id\n");
  status_declare;
  db_txn_declare(env, txn);
  db_ids_declare(ids);
  db_records_declare(records);
  db_record_value_t field_data;
  db_record_t record;
  status_require(db_records_new(2, &records));
  status_require(db_ids_new(3, &ids));
  db_ids_add(ids, 1);
  db_ids_add(ids, 2);
  db_ids_add(ids, 3);
  status_require(db_txn_begin(&txn));
  status_require_read(db_record_get(txn, ids, &records));
  if(db_records_length(records)) {
    // arguments: type, db-record-t, field_index
    record = db_records_get_at(records, 0);
    field_data = db_record_ref(type, record, 1);
    // field_data.data: void*, field_data.size: size_t
  }

  // by type
  printf("read records by type\n");
  db_record_selection_declare(selection);
  db_records_clear(records);
  // arguments: db_txn_t, db_type_t*, offset, matcher, matcher_state, selection_address));
  status_require(db_record_select(txn, type, 0, 0, &selection));
  status_require_read(db_record_read(selection, 3, &records));
  printf("read %lu records\n", db_records_length(records));
  while(db_records_in_range(records)) {
    record = db_records_get(records);
    field_data = db_record_ref(type, record, 0);
    db_records_forward(records);
  }

  // by type and custom matcher function
  printf("read records by matcher function\n");
  uint8_t matcher_state = 0;
  status_require(db_record_select(txn, type, record_matcher, &matcher_state, &selection));
exit:
  db_txn_abort_if_active(txn);
  db_record_selection_finish(&selection);
  db_ids_free(ids);
  db_records_free(records);
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
  // create relations between all given left and right records for each label. relations = left * right * label
  db_txn_write_begin(&txn);
  status_require(db_relation_ensure(txn, left, right, label, 0, 0));
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
  db_relation_selection_declare(selection);
  db_relation_t relation;
  status_require(db_ids_new(1, &ids_left));
  status_require(db_ids_new(1, &ids_label));
  status_require(db_relations_new(10, &relations));
  // record ids to be used to filter
  db_ids_add(ids_left, 1);
  db_ids_add(ids_label, 6);
  db_ids_add(ids_label, 7);
  // select relations whose left side is in "ids_left" and label in "ids_label".
  db_txn_begin(&txn);
  status_require(db_relation_select(txn, &ids_left, 0, &ids_label, 0, &selection));
  // read 2 of the selected relations
  status_require(db_relation_read(&selection, 2, &relations));
  db_relation_selection_finish(&selection);
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
  return status;
}

status_t create_index(db_env_t* env, db_type_t* type, db_index_t** result_index) {
  printf("create index\n");
  status_declare;
  // array of field indices to index
  db_fields_len_t fields[2] = {1, 2};
  status_require(db_index_create(env, type, fields, 2, result_index));
exit:
  return status;
}

status_t read_ids_from_index(db_env_t* env, db_type_t* type, db_index_t* index) {
  printf("read ids from index\n");
  // declarations
  status_declare;
  db_txn_declare(env, txn);
  db_index_selection_declare(selection);
  db_ids_declare(ids);
  db_record_values_declare(values);
  uint8_t value_1 = 11;
  uint8_t* value_2 = "abc";
  // allocate memory
  status_require(db_ids_new(2, &ids));
  status_require(db_record_values_new(type, &values));
  // set indexed values to search with. unused fields will be ignored
  db_record_values_set(&values, 1, &value_1, 0);
  db_record_values_set(&values, 2, &value_2, 3);
  // start transaction and read from index
  db_txn_begin(&txn);
  status_require_read(db_index_select(txn, *index, values, &selection));
  if(db_status_id_notfound != status.id) {
    status_require_read(db_index_read(selection, 2, &ids));
  }
  db_status_success_if_notfound;
exit:
  db_txn_abort_if_active(txn);
  db_index_selection_finish(&selection);
  db_ids_free(ids);
  db_record_values_free(&values);
  return status;
}

status_t read_records_from_index(db_env_t* env, db_type_t* type, db_index_t* index) {
  printf("read records from index\n");
  // declarations
  status_declare;
  db_txn_declare(env, txn);
  db_records_declare(records);
  db_record_values_declare(values);
  db_record_index_selection_declare(selection);
  db_record_t record;
  uint8_t value_1 = 11;
  uint8_t* value_2 = "abc";
  // allocate memory
  status_require(db_records_new(2, &records));
  status_require(db_record_values_new(type, &values));
  // set indexed values to search with
  db_record_values_set(&values, 1, &value_1, 0);
  db_record_values_set(&values, 2, &value_2, 3);
  // begin transaction and read
  db_txn_begin(&txn);
  status_require_read(db_record_index_select(txn, *index, values, &selection));
  if(db_status_id_notfound != status.id) {
    status_require_read(db_record_index_read(selection, 1, &records));
    record = db_records_get(records);
  }
  db_status_success_if_notfound;
exit:
  db_txn_abort_if_active(txn);
  db_ids_free(records);
  db_record_index_selection_finish(&selection);
  return status;
}

status_t create_virtual_records(db_env_t* env) {
  printf("create virtual records\n");
  status_declare;
  db_id_t id;
  uint32_t data;
  db_field_t fields;
  db_type_t* type;
  // create virtual record type. must have only one field
  db_field_set(fields, db_field_type_uint16f, 0, 0);
  status_require(db_type_create(env, "test-vtype", &fields, 1, db_type_flag_virtual, &type));
  if(db_type_is_virtual(type)) {
    printf("type is a virtual record type\n");
  }
  // create record. exists only as id
  data = 123;
  id = db_record_virtual_from_uint(type->id, data);
  // get data. arguments: id, datatype
  data = db_record_virtual_data_uint(id, uint32_t);
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
  status_require(create_records(env, type));
  status_require(read_records(env, type));
  status_require(create_relations(env));
  status_require(read_relations(env));
  status_require(create_index(env, type, &index));
  status_require(read_ids_from_index(env, type, index));
  status_require(read_records_from_index(env, type, index));
  status_require(create_virtual_records(env));
  db_close(env);
  printf("%s\n", db_status_description(status));
exit:
  if(status_is_failure) {
    printf("%lu\n", status.group);
    printf("error: %s\n", db_status_description(status));
  }
  return status.id;
}
