# about

sph-db is a database as a shared library for records and relations. sph-db is in beta as of 2018-08, please try it and report any issues

* license: lgpl3+

# project goals
* a minimal embeddable database to store records without having to construct high-level query language strings for each query
* graph-like relations without having to manage junction tables

# features
## data model
* records that act as nodes and can be in relations to build a graph
* nodes have identifiers for random access. they are of custom type, similar to table rows in relational databases, and indexable
* relations are directed, labeled, unidirectionally ordered and small

## technology
* acid compliant, memory-mapped database that can grow to any size that fits on the local filesystem, unrestricted by available ram
* direct, high-speed interface using c data structures. no overhead from sql or similar query language parsing
* embeddable by linking or code inclusion
* read-optimised design with full support for parallel database reads
* efficient through focus on limited feature-set and thin abstraction over lmdb. benchmarks for lmdb can be found [here](https://symas.com/lmdb/technical/)
* written in c, currently via [sc](https://github.com/sph-mn/sph-sc)

# dependencies
* run-time
  * lmdb - http://symas.com/mdb/ (bsd-style license), code here https://github.com/LMDB/lmdb
  * c standard library, for example glibc
* quick build
  * gcc and shell for the provided compile script
* development build
  * sc - https://github.com/sph-mn/sph-sc
  * clang-format (part of cmake)

# setup
1. install run-time and quick build dependencies
1. eventually ``adjust source/c-precompiled/main/config.c``
1. change into the project directory and execute ``./exe/compile-c``
1. execute ``./exe/install``. this supports one optional argument: a path prefix to install to

optionally execute ``./exe/test`` to see if the tests run successful

# usage in c
the example code can be found in ![other/examples/example-usage.c](other/examples/example-usage.c)

## compilation of programs using sph-db
for example with gcc:
```bash
gcc example.c -o example-executable -lsph-db
```

## inclusion of api declarations
```c
#include "<sph-db.h>"
```

## error handling
db routines return a "status_t" object that contains a status and a status-group identifier (error code and source library identifier). bindings to work with this small object are included with the main header "sph-db.h". sph-db internally usually uses a goto label named "exit" per routine where undesired return stati are handled. ``status_require`` goes to exit on any failure status (status.id not zero).
the following examples assume this pattern of calling ``status_declare`` to introduce an initialised variable named ``status`` and having a label named ``exit``

```c
int main() {
  status_declare;
  // example code ...
exit:
  return status.id;
}
```

## initialisation
```c
db_env_declare(env);
status_require(db_env_new(&env));
// the directory and database will be created if it does not exist
status_require(db_open("/tmp/example", 0, env));
// code that makes use of the database ...
db_close(&env);
```

## transactions
* transactions are required for reading and writing. routines for schema changes like type and index creation create a transaction internally and must not be used while another transaction is active in the same thread
* there must only be one active transaction per thread (there is an option to relax this for read transactions). nested transactions might be possible in the future
* returned data pointers, for example from db_node_data_ref, are only valid until the corresponding transaction is aborted or committed. such data pointers are always only for reading and must never be written to

declare a transaction handle variable
```c
// arguments: db-env-t*, custom_variable_name
db_txn_declare(env, txn);
```

start a read-only transaction
```c
db_txn_begin(&txn);
```

start a read-write transaction
```c
db_txn_write_begin(&txn);
```

finish transaction, discarding any writes
```c
db_txn_abort(&txn);
```

finish transaction, applying any writes made in the transaction
```c
db_txn_commit(&txn);
```

in the following examples, where a ``txn`` variable occurs, use of the appropriate transaction features is implied

## create a type
```c
db_field_t fields[4];
db_type_t* type;
// set field.type, field.name and field.name_len
db_field_set(fields[0], db_field_type_uint8, "field-name-1", 12);
db_field_set(fields[1], db_field_type_int8, "field-name-2", 12);
db_field_set(fields[2], db_field_type_string, "field-name-3", 12);
db_field_set(fields[3], db_field_type_string, "field-name-4", 12);
// arguments: db_env_t*, type_name, db_field_t*, field_count, flags, result
status_require(db_type_create(env, "test-type-name", fields, 4, 0, &type));
```

fields can be fixed length (for example for integers and floating point values) or variable length. possible field types are db_field_type_* macro variables, see api reference below.
apart from indicating storage type and size, field types are mostly a hint because no conversions take place

## create nodes
```c
// declarations
db_node_values_declare(values);
db_id_t id_1;
db_id_t id_2;
uint8_t value_1 = 11;
int8_t value_2 = -128;
uint8_t* value_3 = "abc";
uint8_t* value_4 = "abcde";
// memory allocation
status_require(db_node_values_new(type, &values));
// set field values.
// size argument is ignored for fixed length types.
// strings can be stored with or without a trailing null character.
// arguments: db_node_values_t*, field_index, value_address, size.
db_node_values_set(&values, 0, &value_1, 0);
db_node_values_set(&values, 1, &value_2, 0);
db_node_values_set(&values, 2, value_3, 3);
db_node_values_set(&values, 3, value_4, 5);
// create one entry
status_require(db_node_create(txn, values, &id_1));
// create a second entry with a different value for the second field
value_2 = 123;
db_node_values_set(&values, 1, &value_2, 0);
status_require(db_node_create(txn, values, &id_2));
// memory deallocation
db_node_values_free(&values);
```

## array data types
db_ids_t, db_nodes_t, db_relations_t store and pass collections of db_id_t, db_node_t and db_relation_t respectively and are special arrays with a fixed maximum size but variable length
content. they make iteration easier to code and have a currently selected element.
it is possible to get, remove or add elements without specifying an index, as the corresponding bindings will act on the current, last or next after the last element.
memory for these arrays has to be allocated before use.

usage
```c
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
```

db_nodes_* and db_relations_* bindings work the same. see the api documentation for more features. you can always access the data in a plain c array of db_id_t, db_node_t or db_relation_t with the struct field ``data``, for example ``ids.data``

## read nodes
if no results are found or the end of results has been reached, the returned status id is ``db_status_id_notfound``. here is one example of how to handle this
```c
// like status_require but tolerates notfound
status_require_read(db_node_read(selection, count, &results));
if(db_status_id_notfound != status.id) {
  field_data = db_node_ref(db_node_get_at(results, 0), 0);
}
// sets status.id to zero if the current status is db_status_id_notfound
db_status_success_if_notfound;
```

by unique identifier
```c
db_ids_declare(ids);
db_nodes_declare(nodes);
db_node_value_t field_data;
db_node_t node;
status_require(db_nodes_new(3, &nodes));
status_require(db_ids_new(3, &ids));
db_ids_add(ids, 1);
db_ids_add(ids, 2);
db_ids_add(ids, 3);
status_require_read(db_node_get(txn, ids, &nodes));
if(db_nodes_length(nodes)) {
  node = db_nodes_get_at(nodes, 0);
  // arguments: type, db-node-t, field_index
  field_data = db_node_ref(type, node, 1);
  // field_data: void* .data, size_t .size
}
db_ids_free(ids);
db_nodes_free(nodes);
```

all of type
```c
// arguments: db_txn_t, db_type_t*, offset, matcher, matcher_state, selection_address));
status_require(db_node_select(txn, type, 0, 0, 0, &selection));
status_require_read(db_node_read(selection, 3, &nodes));
while(db_nodes_in_range(nodes)) {
  node = db_nodes_get(nodes);
  field_data = db_node_ref(type, node, 0);
  db_nodes_forward(nodes);
}
```

by custom matcher function and optionally either type or ids list
```c
boolean node_matcher(db_type_t* type, db_node_t node, void* matcher_state) {
  db_node_value_t field_data;
  field_data = db_node_ref(type, node, 2);
  *((uint8_t*)(matcher_state)) = 1;
  return 1;
};
uint8_t matcher_state = 0;
status_require(db_node_select(txn, type, 0, node_matcher, &matcher_state, &selection));
```

## create relations
```c
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
status_require(db_graph_ensure(txn, left, right, label, 0, 0));
db_ids_free(left);
db_ids_free(right);
db_ids_free(label);
```

## read relations
```c
// declarations
db_ids_declare(ids_left);
db_ids_declare(ids_label);
db_relations_declare(relations);
db_graph_selection_declare(selection);
db_relation_t relation;
// memory allocation
status_require(db_ids_new(1, &ids_left));
status_require(db_ids_new(1, &ids_label));
db_relations_new(10, &relations);
// node ids to be used to filter
db_ids_add(ids_left, 123);
db_ids_add(ids_label, 456);
// select relations whose left side is in "ids_left" and label in "ids_label".
status_require(db_graph_select(txn, &ids_left, 0, &ids_label, 0, 0, &selection));
// read 2 of the selected relations
status_require(db_graph_read(&selection, 2, &relations));
// read as many remaining matches as there still fit into the relations array
status_require_read(db_graph_read(&selection, 0, &relations));
db_graph_selection_finish(&selection);
// display relations. "ordinal" might not be set unless a filter for left was used
while(db_relations_in_range(relations)) {
  relation = db_relations_get(relations);
  printf("relation: %lu %lu %lu %lu\n", relation.left, relation.label, relation.ordinal, relation.right);
  db_relations_forward(relations);
};
db_ids_free(ids_left);
db_ids_free(ids_label);
db_relations_free(relations);
```

## create index
```c
db_index_t* index;
// array of field indices to index
db_fields_len_t fields[2] = {1, 2};
status_require(db_index_create(env, type, fields, 2));
index = db_index_get(type, fields, 2);
```

* existing nodes will be indexed on index creation
* new nodes will be automatically added or removed from the index when they are created or deleted
* there is a limit on the combined size of indexed field data for a node, which is defined by the lmdb compile-time constant MDB_MAXKEYSIZE, default 511 bytes minus db_id_t size. currently index inserts with data too large are rejected

## read node ids from indices
```c
db_index_selection_declare(selection);
db_ids_declare(ids);
db_node_values_declare(values);
uint8_t value_1 = 11;
uint8_t* value_2 = "abc";
// allocate memory
status_require(db_ids_new(2, &ids));
status_require(db_node_values_new(type, &values));
// set indexed values to search with. unused fields will be ignored
db_node_values_set(&values, 1, &value_1, 0);
db_node_values_set(&values, 2, &value_2, 3);
status_require_read(db_index_select(txn, *index, values, &selection));
if(db_status_id_notfound != status.id) {
  status_require_read(db_index_read(selection, 2, &ids));
}
db_status_success_if_notfound;
db_index_selection_finish(&selection);
db_ids_free(ids);
db_node_values_free(&values);
```

## read nodes via indices
```c
db_node_index_selection_declare(selection);
status_require_read(db_node_index_select(txn, *index, values, &selection));
if(db_status_id_notfound != status.id) {
  status_require_read(db_node_index_read(selection, 1, &nodes));
  node = db_nodes_get(nodes);
}
```

## virtual nodes
virtual nodes carry the data in the identifier and only exist in relations or field data. one use-case are relations with a possibly large number of numeric values that dont need a separate data record, for example timestamps. they are to save space and processing costs. they can store data of any type that is equal to or smaller than id-size minus type-size
to create a virtual node type, pass db_type_flag_virtual to db_type_create and only a single field

```c
db_id_t id;
uint32_t data;
db_field_t fields;
db_type_t* type;
// create virtual node type. must have only one field
db_field_set(fields, db_field_type_uint16, 0, 0);
status_require(db_type_create(env, "test-vtype", &fields, 1, db_type_flag_virtual, &type));
// create node. exists only as id
data = 123;
id = db_node_virtual_from_uint(type->id, data);
// get data. arguments: id, datatype
data = db_node_virtual_data(id, uint32_t);
```

# api
## routines
```
db_close :: db_env_t*:env -> void
db_env_new :: db_env_t**:result -> status_t
db_field_type_size :: uint8_t:a -> uint8_t
db_graph_delete :: db_txn_t:txn db_ids_t*:left db_ids_t*:right db_ids_t*:label db_ordinal_condition_t*:ordinal -> status_t
db_graph_ensure :: db_txn_t:txn db_ids_t:left db_ids_t:right db_ids_t:label db_graph_ordinal_generator_t:ordinal_generator void*:ordinal_generator_state -> status_t
db_graph_read :: db_graph_selection_t*:selection db_count_t:count db_relations_t*:result -> status_t
db_graph_select :: db_txn_t:txn db_ids_t*:left db_ids_t*:right db_ids_t*:label db_ordinal_condition_t*:ordinal db_count_t:offset db_graph_selection_t*:result -> status_t
db_graph_selection_finish :: db_graph_selection_t*:selection -> void
db_ids_new :: size_t:length db_ids_t*:result_ids -> status_t
db_index_create :: db_env_t*:env db_type_t*:type db_fields_len_t*:fields db_fields_len_t:fields_len -> status_t
db_index_delete :: db_env_t*:env db_index_t*:index -> status_t
db_index_get :: db_type_t*:type db_fields_len_t*:fields db_fields_len_t:fields_len -> db_index_t*
db_index_read :: db_index_selection_t:selection db_count_t:count db_ids_t*:result_ids -> status_t
db_index_rebuild :: db_env_t*:env db_index_t*:index -> status_t
db_index_select :: db_txn_t:txn db_index_t:index db_node_values_t:values db_index_selection_t*:result -> status_t
db_index_selection_finish :: db_index_selection_t*:selection -> void
db_node_create :: db_txn_t:txn db_node_values_t:values db_id_t*:result -> status_t
db_node_data_to_values :: db_type_t*:type db_node_t:data db_node_values_t*:result -> status_t
db_node_delete :: db_txn_t:txn db_ids_t:ids -> status_t
db_node_delete_type :: db_txn_t:txn db_type_id_t:type_id -> status_t
db_node_get :: db_txn_t:txn db_ids_t:ids db_nodes_t*:result_nodes -> status_t
db_node_index_read :: db_node_index_selection_t:selection db_count_t:count db_nodes_t*:result_nodes -> status_t
db_node_index_select :: db_txn_t:txn db_index_t:index db_node_values_t:values db_node_index_selection_t*:result -> status_t
db_node_index_selection_finish :: db_node_index_selection_t*:selection -> void
db_node_read :: db_node_selection_t:selection db_count_t:count db_nodes_t*:result_nodes -> status_t
db_node_ref :: db_type_t*:type db_node_t:node db_fields_len_t:field -> db_node_value_t
db_node_select :: db_txn_t:txn db_type_t*:type db_count_t:offset db_node_matcher_t:matcher void*:matcher_state db_node_selection_t*:result_selection -> status_t
db_node_selection_finish :: db_node_selection_t*:selection -> void
db_node_skip :: db_node_selection_t:selection db_count_t:count -> status_t
db_node_update :: db_txn_t:txn db_id_t:id db_node_values_t:values -> status_t
db_node_values_to_data :: db_node_values_t:values db_node_t*:result -> status_t
db_node_values_free :: db_node_values_t*:a -> void
db_node_values_new :: db_type_t*:type db_node_values_t*:result -> status_t
db_node_values_set :: db_node_values_t*:values db_fields_len_t:field_index void*:data size_t:size -> void
db_node_virtual_from_any :: db_type_id_t:type_id void*:data uint8_t:data_size -> db_id_t
db_nodes_to_ids :: db_nodes_t:nodes db_ids_t*:result_ids -> void
db_nodes_new :: size_t:length db_nodes_t*:result_nodes -> status_t
db_open :: uint8_t*:root db_open_options_t*:options db_env_t*:env -> status_t
db_relations_new :: size_t:length db_relations_t*:result_relations -> status_t
db_statistics :: db_txn_t:txn db_statistics_t*:result -> status_t
db_status_description :: status_t:a -> uint8_t*
db_status_group_id_to_name :: status_id_t:a -> uint8_t*
db_status_name :: status_t:a -> uint8_t*
db_txn_abort :: db_txn_t*:a -> void
db_txn_begin :: db_txn_t*:a -> status_t
db_txn_begin_child :: db_txn_t:parent_txn db_txn_t*:a -> status_t
db_txn_commit :: db_txn_t*:a -> status_t
db_txn_write_begin :: db_txn_t*:a -> status_t
db_txn_write_begin_child :: db_txn_t:parent_txn db_txn_t*:a -> status_t
db_type_create :: db_env_t*:env uint8_t*:name db_field_t*:fields db_fields_len_t:fields_len uint8_t:flags db_type_t**:result -> status_t
db_type_delete :: db_env_t*:env db_type_id_t:id -> status_t
db_type_field_get :: db_type_t*:type uint8_t*:name -> db_field_t*
db_type_get :: db_env_t*:env uint8_t*:name -> db_type_t*
```

## macros
```
boolean
db_batch_len
db_count_t
db_data_len_max
db_data_len_t
db_env_declare(name)
db_field_set(a, a_type, a_name, a_name_len)
db_field_type_binary
db_field_type_float32
db_field_type_float64
db_field_type_int16
db_field_type_int32
db_field_type_int64
db_field_type_int8
db_field_type_string
db_field_type_string16
db_field_type_string32
db_field_type_string64
db_field_type_string8
db_field_type_t
db_field_type_uint16
db_field_type_uint32
db_field_type_uint64
db_field_type_uint8
db_fields_len_t
db_graph_selection_declare(name)
db_id_add_type(id, type_id)
db_id_element(id)
db_id_element_mask
db_id_mask
db_id_t
db_id_type(id)
db_id_type_mask
db_ids_add
db_ids_clear
db_ids_declare(name)
db_ids_forward
db_ids_free
db_ids_get
db_ids_get_at
db_ids_in_range
db_ids_length
db_ids_max_length
db_ids_remove
db_ids_rewind
db_ids_set_null
db_index_selection_declare(name)
db_indices_len_t
db_name_len_max
db_name_len_t
db_node_index_selection_declare(name)
db_node_is_virtual(env, node_id)
db_node_selection_declare(name)
db_node_values_declare(name)
db_node_virtual_data(id, type_name)
db_node_virtual_from_int
db_node_virtual_from_uint(type_id, data)
db_nodes_add
db_nodes_clear
db_nodes_declare(name)
db_nodes_forward
db_nodes_free
db_nodes_get
db_nodes_get_at
db_nodes_in_range
db_nodes_length
db_nodes_max_length
db_nodes_remove
db_nodes_rewind
db_nodes_set_null
db_null
db_ordinal_t
db_relations_add
db_relations_clear
db_relations_declare(name)
db_relations_forward
db_relations_free
db_relations_get
db_relations_get_at
db_relations_in_range
db_relations_length
db_relations_max_length
db_relations_remove
db_relations_rewind
db_relations_set_null
db_size_element_id
db_size_graph_data
db_size_graph_key
db_status_set_id_goto(status_id)
db_status_success_if_notfound
db_txn_abort_if_active(a)
db_txn_declare(env, name)
db_txn_is_active(a)
db_type_flag_virtual
db_type_get_by_id(env, type_id)
db_type_id_mask
db_type_id_t
db_type_is_virtual(type)
status_declare
status_declare_group(group)
status_goto
status_group_undefined
status_id_require(expression)
status_id_success
status_is_failure
status_is_success
status_require(expression)
status_require_read(expression)
status_reset
status_set_both(group_id, status_id)
status_set_both_goto(group_id, status_id)
status_set_group_goto(group_id)
status_set_id_goto(status_id)
```

## types
```
status_id_t: int32_t
db_graph_ordinal_generator_t: void* -> db_ordinal_t
db_graph_reader_t: db_graph_selection_t* db_count_t db_relations_t* -> status_t
db_node_matcher_t: db_type_t* db_node_t void* -> boolean
db_env_t: struct
  dbi_nodes: MDB_dbi
  dbi_graph_ll: MDB_dbi
  dbi_graph_lr: MDB_dbi
  dbi_graph_rl: MDB_dbi
  dbi_system: MDB_dbi
  mdb_env: MDB_env*
  is_open: boolean
  root: uint8_t*
  mutex: pthread_mutex_t
  maxkeysize: int
  format: uint32_t
  types: db_type_t*
  types_len: db_type_id_t
db_field_t: struct
  name: uint8_t*
  name_len: db_name_len_t
  type: db_field_type_t
  index: db_fields_len_t
db_graph_selection_t: struct
  cursor: MDB_cursor* restrict
  cursor_2: MDB_cursor* restrict
  left: db_ids_t
  right: db_ids_t
  label: db_ids_t
  ids_set: void*
  ordinal: db_ordinal_condition_t*
  options: uint8_t
  reader: void*
db_index_selection_t: struct
  cursor: MDB_cursor*
db_index_t: struct db_index_t
  dbi: MDB_dbi
  fields: db_fields_len_t*
  fields_len: db_fields_len_t
  type: db_type_t*
db_node_index_selection_t: struct
  index_selection: db_index_selection_t
  nodes_cursor: MDB_cursor*
db_node_selection_t: struct
  cursor: MDB_cursor*
  matcher: db_node_matcher_t
  matcher_state: void*
  options: uint8_t
  type: db_type_t*
db_node_t: struct
  id: db_id_t
  data: void*
  size: size_t
db_node_value_t: struct
  size: db_data_len_t
  data: void*
db_node_values_t: struct
  data: db_node_value_t*
  extent: db_fields_len_t
  type: db_type_t*
db_open_options_t: struct
  is_read_only: boolean
  maximum_size: size_t
  maximum_reader_count: db_count_t
  maximum_db_count: db_count_t
  filesystem_has_ordered_writes: boolean
  env_open_flags: uint_least32_t
  file_permissions: uint16_t
db_ordinal_condition_t: struct
  min: db_ordinal_t
  max: db_ordinal_t
db_relation_t: struct
  left: db_id_t
  right: db_id_t
  label: db_id_t
  ordinal: db_ordinal_t
db_statistics_t: struct
  system: MDB_stat
  nodes: MDB_stat
  graph_lr: MDB_stat
  graph_rl: MDB_stat
  graph_ll: MDB_stat
db_txn_t: struct
  mdb_txn: MDB_txn*
  env: db_env_t*
db_type_t: struct
  fields_len: db_fields_len_t
  fields_fixed_count: db_fields_len_t
  fields_fixed_offsets: size_t*
  fields: db_field_t*
  flags: uint8_t
  id: db_type_id_t
  indices: struct db_index_t*
  indices_len: db_indices_len_t
  indices_size: size_t
  name: uint8_t*
  sequence: db_id_t
status_t: struct
  id: status_id_t
  group: uint8_t
```

## enum
```
db_status_id_success db_status_id_undefined db_status_id_condition_unfulfilled
  db_status_id_data_length db_status_id_different_format db_status_id_duplicate
  db_status_id_input_type db_status_id_invalid_argument db_status_id_max_element_id
  db_status_id_max_type_id db_status_id_max_type_id_size db_status_id_memory
  db_status_id_missing_argument_db_root db_status_id_notfound db_status_id_not_implemented
  db_status_id_path_not_accessible_db_root db_status_id_index_keysize db_status_group_db
  db_status_group_lmdb db_status_group_libc
```

# other language bindings
* scheme: [sph-db-guile](https://github.com/sph-mn/sph-db-guile)

# compile-time configuration
these values can be set before compilation in ``c-precompiled/main/config.c``. once compiled, they can not be changed. databases created with one configuration must only be used by code compiled with the same configuration. if necessary, for example, multiple shared libraries with different configuration can be created.

|name|default|description|
| --- | --- | --- |
|db_id_t|uint64_t|for node identifiers. will also contain the type id|
|db_type_id_t|uint16_t||for type ids. limits the number of possible types. currently can not be larger than 16 bit|
|db_ordinal_t|uint32_t|for relation order values|
|db_id_mask|UINT64_MAX|maximum value for the id type (all digits set to one)|
|db_type_id_mask|UINT16_MAX|maximum value for the type-id type|
|db_data_len_t|uint32_t|to store field sizes|
|db_data_len_max|UINT32_MAX|maximum allowed field size|
|db_name_len_t|uint8_t|to store name string lengths (for type and field names)|
|db_name_len_max|UINT8_MAX|maximum allowed name length|
|db_fields_len_t|uint8_t|for field indices. limits the number of possible fields|
|db_indices_len_t|uint8_t|limits the number of possible indices per type|
|db_count_t|uint32_t|for values like the count of elements to read. does not need to be larger than half size_t|
|db_batch_len|uint32_t|number of elements to process at once internally for example in db-node-select-delete. mostly for db-id-t|

# additional features and caveats
* make sure that you do not try to insert ordinals or ids bigger than what is defined to be possible by the data types for ordinals and node identifiers. otherwise numerical overflows might occur
* ordinals are primarily intended to store data in a pre-calculated order for fast ordered retrieval
* to use db_graph_select and a filter by ordinal, "left" filter values must be given
* readers can return results and indicate the end of results in the same call
* the maximum number of type creations is currently 65535. this limit is to be removed in the future

# possible enhancements
* float values as ordinals has not been tested
* lift the type creation limit. allow all unsigned integer datatypes for type ids and reclaim unused ids
* nested transactions. supposedly possible in lmdb but not working
* validator functions for indices and graph data consistency
* partial indices. with a data filter function given at index definition
* currently index inserts with data too large are rejected. maybe add an option to truncate instead
* at some places MDB_SET_RANGE and MDB_GET_BOTH_RANGE is used in succession. maybe get-both-range includes set-range and the latter can be left out
* search with matcher functions in index keys
* simplified naming for status bindings. status_require is a long word to be written frequently

# development
this section is for when you want to change sph-db itself.
the primary source code is currently under source/sc. source/c-precompiled is updated by ``exe/compile-sc``. code files from submodules are copied into source/sc/foreign before compilation from sc to c.
depending on circumstances, in the future, the sc dependency could be dropped and the c code could be made primary.
the general development stages for new sph-db features is design, basic code implementation, tests that use the new features, debugging, memory-leak tests (``exe/valgrind-test``) and documentation

## setup
* install the development dependencies listed above
* clone the sourcecode repository "git clone https://github.com/sph-mn/sph-db.git"
* clone the submodule repositories. "git submodule init" "git submodule update"

## contribution
* send feature requests, patches or pull requests via issues or e-mail and they will be considered
* bug reports or design commentaries are welcome

# further reading
see [other/documentation](other/documentation) for more details about internals and design considerations
