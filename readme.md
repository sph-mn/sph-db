# about

sph-db is a database as a shared library for records and relations. sph-db is in beta as of 2018-08, please try it and report any issues

* [design](http://sph.mn/c/view/si)
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
1. eventually adjust source/c-precompiled/main/config.c
1. change into the project directory and execute ``./exe/compile-c``
1. execute ``./exe/install``. this supports one optional argument: a path prefix to install to

optionally execute ``./exe/test`` to see if the tests run successful

# usage in c
## compilation of programs using sph-db
for example with gcc:
```bash
gcc example.c -o example-executable -llmdb -lsph-db
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
db_env_t* env;
db_env_new(&env);
// the database file will be created if it does not exist
status_require(db_open("/tmp/example", 0, env));
// code that makes use of the database ...
db_close(env);
```

## transactions
* transactions are required for reading and writing. routines for schema changes like type and index creation create a transaction internally and must not be used while another transaction is active in the same thread
* there must only be one active transaction per thread (there is an option to relax this for read transactions). nested transactions are possible
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
status_require(db_type_create(env, "test-type", fields, 4, 0, &type));
```
fields can be fixed length (for example for integers and floating point values) or variable length. possible field types are db_field_type_* macro variables, see api reference below.
apart from indicating storage type and size, field types are mostly a hint because no conversions take place

## create nodes
```c
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
```

## read nodes
if no results are found or the end of results has been reached, status is set to ``db_status_id_notfound``. here is one example of how to handle this
```c
// like status_require but tolerates notfound
status_require_read(db_node_next(selection));
if(db_status_id_notfound != status.id) {
  field_data = db_node_ref(selection, 0);
}
```

by unique identifier
```c
db_id_t id;
db_node_data_t node_data;
db_node_data_t field_data;
id = 123;
status_require_read(db_node_get(txn, id, &node_data));
if(db_status_id_notfound != status.id) {
  // arguments: type, node_data, field_index
  field_data = db_node_data_ref(type, node_data, 1);
  // field_data.data: void*, field_data.size: size_t
}
```

all of type
```c
db_node_selection_t selection;
db_node_data_t field_data;
// arguments: db_txn_t, db_ids_t*, db_type_t*, offset, matcher, matcher_state, selection_address));
status_require(db_node_select(txn, 0, type, 0, 0, 0, &selection));
status_require_read(db_node_next(selection));
if(db_status_id_notfound != status.id) {
  // arguments: selection, field_index
  field_data = db_node_ref(selection, 0);
}
db_node_selection_finish(&selection);
```

by any of a list of ids
```c
db_node_selection_t selection;
db_node_data_t field_data;
db_ids_t ids;
db_ids_new(ids, 3);
i_array_add(ids, 10);
i_array_add(ids, 15);
i_array_add(ids, 28);
status_require(db_node_select(txn, ids, 0, 0, 0, 0, &selection));
status_require_read(db_node_next(selection));
status_require_read(db_node_next(selection));
db_node_selection_finish(&selection);
i_array_free(ids);
```

by custom matcher function and optionally either type or ids list
```c
boolean node_matcher(db_id_t id, db_node_data_t data, void* matcher_state) {
  db_node_data_t field_data;
  field_data = db_node_data_ref(type, node_data, 2);
  *((uint8_t*)(matcher_state)) = 1;
  return 1;
};
uint8_t matcher_state = 0;
status_require(db_node_select(txn, 0, type, 0, node_matcher, &matcher_state, &selection));
```

## create relations
```c
db_ids_t left;
db_ids_t right;
db_ids_t label;
db_ids_new(left, 5);
db_ids_new(right, 5);
db_ids_new(label, 2);
// ... add ids to left, right and label ...
// create relations between all given left and right nodes for each label. relations = left * right * label
status_require(db_graph_ensure(txn, left, right, label, 0, 0));
i_array_free(left);
i_array_free(right);
i_array_free(label);
```

## read relations
```c
db_ids_t ids_left;
db_ids_t ids_label;
db_relations_t relations;
db_relation_t relation;
db_graph_selection_t selection;
db_ids_new(ids_left, 1);
db_ids_new(ids_label, 1);
db_relations_new(relations, 10);
// node ids to be used to filter
ids_left = i_array_add(ids_left, 123);
ids_label = i_array_add(ids_label, 456);
// select relations whose left side is in "ids_left" and label in "ids_label".
status_require(db_graph_select(txn, &ids_left, 0, &ids_label, 0, 0, &selection));
// read 2 of the selected relations
status_require(db_graph_read(&selection, 2, &relations));
// read as many remaining matches as fit into the relations array
status_require_read(db_graph_read(&selection, 0, &relations));
db_graph_selection_finish(&selection);
// display relations. "ordinal" might not be set unless a filter for left was used
while(i_array_in_range(relations)) {
  relation = i_array_get(relations);
  printf("relation: %lu %lu %lu %lu\n", relation.left, relation.label, relation.ordinal, relation.right);
  i_array_forward(relations);
};

i_array_free(ids_left);
i_array_free(ids_label);
i_array_free(relations);
```

## create indices
```c
db_index_t* index;
// array of field indices to index
db_fields_len_t fields[2] = {1, 2};
status_require(db_index_create(env, type, fields, 2));
```

* existing nodes will be indexed on index creation
* new nodes will be automatically added or removed from the index when they are created or deleted
* there is a limit on the combined size of indexed field data for a node, which is defined by the lmdb compile-time constant MDB_MAXKEYSIZE, default 511 bytes minus db_id_t size. currently index inserts with data too large are rejected

## read node ids from indices
```c
db_index_selection_t selection;
db_node_values_t values;
db_id_t id;
uint8_t value_1 = 11;
uint8_t* value_2 = "abc";
status_require(db_node_values_new(type, &values));
db_node_values_set(&values, 1, &value_1, 0);
db_node_values_set(&values, 2, &value_2, 3);
// values for other fields will be ignored.
// unlike node_ and graph_select, db_index_select already searches for the first match on call
status_require(db_index_select(txn, *index, values, &selection));
id = selection.current;
status_require(db_index_next(selection));
db_index_selection_finish(&selection);
```

## read nodes via indices
```c
db_node_index_selection_t selection;
status_require(db_node_index_select(txn, *index, values, &selection));
// db_node_data_t
selection.current
// db_id_t
selection.current_id
status_require(db_node_index_next(selection));
db_node_index_selection_finish(&selection);
```

# api
## routines
```
db_close :: db_env_t*:env -> void
db_env_new :: db_env_t**:result -> status_t
db_field_type_size :: uint8_t:a -> uint8_t
db_graph_delete :: db_txn_t:txn db_ids_t*:left db_ids_t*:right db_ids_t*:label db_ordinal_condition_t*:ordinal -> status_t
db_graph_ensure :: db_txn_t:txn db_ids_t:left db_ids_t:right db_ids_t:label db_graph_ordinal_generator_t:ordinal_generator void*:ordinal_generator_state -> status_t
db_graph_read :: db_graph_selection_t*:state db_count_t:count db_relations_t*:result -> status_t
db_graph_select :: db_txn_t:txn db_ids_t*:left db_ids_t*:right db_ids_t*:label db_ordinal_condition_t*:ordinal db_count_t:offset db_graph_selection_t*:result -> status_t
db_graph_selection_finish :: db_graph_selection_t*:selection -> void
db_index_create :: db_env_t*:env db_type_t*:type db_fields_len_t*:fields db_fields_len_t:fields_len -> status_t
db_index_delete :: db_env_t*:env db_index_t*:index -> status_t
db_index_get :: db_type_t*:type db_fields_len_t*:fields db_fields_len_t:fields_len -> db_index_t*
db_index_next :: db_index_selection_t:selection -> status_t
db_index_rebuild :: db_env_t*:env db_index_t*:index -> status_t
db_index_select :: db_txn_t:txn db_index_t:index db_node_values_t:values db_index_selection_t*:result -> status_t
db_index_selection_finish :: db_index_selection_t*:selection -> void
db_node_create :: db_txn_t:txn db_node_values_t:values db_id_t*:result -> status_t
db_node_data_to_values :: db_type_t*:type db_node_data_t:data db_node_values_t*:result -> status_t
db_node_data_ref :: db_type_t*:type db_node_data_t:data db_fields_len_t:field -> db_node_data_t
db_node_delete :: db_txn_t:txn db_ids_t*:ids -> status_t
db_node_exists :: db_txn_t:txn db_ids_t:ids uint8_t*:result -> status_t
db_node_get :: db_txn_t:txn db_id_t:id db_node_data_t*:result -> status_t
db_node_index_next :: db_node_index_selection_t:selection -> status_t
db_node_index_select :: db_txn_t:txn db_index_t:index db_node_values_t:values db_node_index_selection_t*:result -> status_t
db_node_index_selection_finish :: db_node_index_selection_t*:selection -> void
db_node_next :: db_node_selection_t*:selection -> status_t
db_node_ref :: db_node_selection_t*:selection db_fields_len_t:field -> db_node_data_t
db_node_select :: db_txn_t:txn db_ids_t*:ids db_type_t*:type db_count_t:offset db_node_matcher_t:matcher void*:matcher_state db_node_selection_t*:result_selection -> status_t
db_node_selection_finish :: db_node_selection_t*:selection -> void
db_node_skip :: db_node_selection_t*:selection db_count_t:count -> status_t
db_node_update :: db_txn_t:txn db_id_t:id db_node_values_t:values -> status_t
db_node_values_to_data :: db_node_values_t:values db_node_data_t*:result -> status_t
db_node_values_free :: db_node_values_t*:a -> void
db_node_values_new :: db_type_t*:type db_node_values_t*:result -> status_t
db_node_values_set :: db_node_values_t*:values db_fields_len_t:field_index void*:data size_t:size -> void
db_open :: uint8_t*:root db_open_options_t*:options db_env_t*:env -> status_t
db_statistics :: db_txn_t:txn db_statistics_t*:result -> status_t
db_status_description :: status_t:a -> uint8_t*
db_status_group_id_to_name :: status_id_t:a -> uint8_t*
db_status_name :: status_t:a -> uint8_t*
db_txn_abort :: db_txn_t*:a -> void
db_txn_begin :: db_txn_t*:a -> status_t
db_txn_commit :: db_txn_t*:a -> status_t
db_txn_write_begin :: db_txn_t*:a -> status_t
db_type_create :: db_env_t*:env uint8_t*:name db_field_t*:fields db_fields_len_t:fields_len uint8_t:flags db_type_t**:result -> status_t
db_type_delete :: db_env_t*:env db_type_id_t:id -> status_t
db_type_field_get :: db_type_t*:type uint8_t*:name -> db_field_t*
db_type_get :: db_env_t*:env uint8_t*:name -> db_type_t*
```

## macros
```
db_count_t
db_data_len_max
db_data_len_t
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
db_id_add_type(id, type_id)
db_id_element(id)
db_id_mask
db_id_t
db_id_type(id)
db_ids_new
db_indices_len_t
db_name_len_max
db_name_len_t
db_node_virtual_to_data(id)
db_null
db_ordinal_t
db_relations_new
db_size_element_id
db_size_graph_data
db_size_graph_key
db_status_set_id_goto(status_id)
db_status_success_if_notfound
db_txn_abort_if_active(a)
db_txn_declare(env, name)
db_txn_is_active(a)
db_type_id_mask
db_type_id_t
i_array_add(a, value)
i_array_clear(a)
i_array_declare(a, type)
i_array_forward(a)
i_array_free(a)
i_array_get(a)
i_array_get_at(a, index)
i_array_in_range(a)
i_array_length(a)
i_array_max_length(a)
i_array_remove(a)
i_array_rewind(a)
i_array_set_null(a)
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
uint8_t
```

## types
```
status_id_t: int32_t
db_graph_ordinal_generator_t: void* -> db_ordinal_t
db_graph_reader_t: db_graph_selection_t* db_count_t db_relations_t* -> status_t
db_node_matcher_t: db_id_t db_node_data_t void* -> boolean
db_env_t: struct
  dbi_nodes: MDB_dbi
  dbi_graph_ll: MDB_dbi
  dbi_graph_lr: MDB_dbi
  dbi_graph_rl: MDB_dbi
  dbi_system: MDB_dbi
  mdb_env: MDB_env*
  open: uint8_t
  root: uint8_t*
  mutex: pthread_mutex_t
  maxkeysize: int
  types: db_type_t*
  types_len: db_type_id_t
db_field_t: struct
  name: uint8_t*
  name_len: db_name_len_t
  type: db_field_type_t
  index: db_fields_len_t
db_graph_selection_t: struct
  status: status_t
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
  current: db_id_t
  cursor: MDB_cursor*
db_index_t: struct db_index_t
  dbi: MDB_dbi
  fields: db_fields_len_t*
  fields_len: db_fields_len_t
  type: db_type_t*
db_node_data_t: struct
  data: void*
  size: size_t
db_node_index_selection_t: struct
  current: db_node_data_t
  current_id: db_id_t
  index_selection: db_index_selection_t
  nodes: MDB_cursor*
db_node_selection_t: struct
  count: db_count_t
  current: db_node_data_t
  current_id: db_id_t
  cursor: MDB_cursor*
  env: db_env_t*
  ids: db_ids_t
  matcher: db_node_matcher_t
  matcher_state: void*
  options: uint8_t
  type: db_type_t*
db_node_value_t: struct
  size: db_data_len_t
  data: void*
db_node_values_t: struct
  data: db_node_value_t*
  last: db_fields_len_t
  type: db_type_t*
db_open_options_t: struct
  is_read_only: uint8_t
  maximum_size: size_t
  maximum_reader_count: db_count_t
  maximum_db_count: db_count_t
  filesystem_has_ordered_writes: uint8_t
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
these values can be set before compilation in ``c-precompiled/main/config.c``. once compiled, they can not be changed. databases created with one configuration must only be used by code compiled with the same configuration. if necessary, for example, multiple shared libraries with different configuration can be created and linked to

|db_id_t|ui64|for node identifiers. will also contain a node type id|
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

# additional features and caveats
* the maximum number of type creations is currently 65535
* make sure that you do not try to insert ordinals or ids bigger than what is defined to be possible by the data types for ordinals and node identifiers. otherwise numerical overflows might occur
* ordinals are primarily intended to store data in a pre-calculated order for fast ordered retrieval
* to use db_graph_select and a filter by ordinal, "left" filter values must be given
* readers can return results and indicate the end of results in the same call

# possible enhancements
* virtual nodes (custom types that carry the data with the identifier) are designed but need to be implemented
* currently index inserts with data too large are rejected. add an option to truncate instead
* make it possible to increase the maximum number of types. needs a new data structure for types for that
* validator functions for indices and graph data consistency
* float values as ordinals has not yet been tested
* at some places MDB_SET_RANGE and MDB_GET_BOTH_RANGE is used in succession. maybe get-both-range includes set-range and the latter can be left out

# development
this section is for when you want to change sph-db itself.
the primary source code is currently under source/sc. source/c-precompiled is updated by ``exe/compile-sc``. code files from submodules are copied into source/sc/foreign before compiling from sc to c.
depending on circumstances, in the future, the sc dependency could be dropped and the c code could be made primary.
the general development stages for new sph-db features is design, basic code implementation, tests that use the new features, debugging, memory-leak tests (``exe/valgrind-test``) and documentation

## setup
* install the development dependencies listed above
* clone the sourcecode repository "git clone https://github.com/sph-mn/sph-db.git"
* clone the submodule repositories. "git submodule init" "git submodule update"

## contribution
* send feature requests, patches or pull requests via issues or e-mail and they will be considered
* bug reports or design commentaries are welcome

# internals
* ``sph-db-extra.h`` contains declarations for internally used things
* the code assumes that "mdb_cursor_close" can be called with null pointers at some places

## db-graph-select
* chooses the reader, relevant databases and other values to use for the search
* positions every relevant mdb cursor at the first entry of the dbi or exits with an error status if the database is empty
* chooses the appropiate reader routine
* applies the read offset

## db-graph-read
* supports partial reads. for example reading up to ten matches at a time. this is why a selection object is used. this has many subtle consequences, like having to get the current cursor value at the beginning of the reader code, to check if the right key or data is already set
* supports skipping: matching but not copying the result. this is used for reading from an offset
* cursors as arguments are assumed to be in position at a valid entry on call
* readers must not be called after db-status-id-notfound
* readers and deleters are built using stacked goto labels because this makes it much easier for this case to control the execution flow, compared to the alternative of nested while loops. especially for choosing the best place for evaluating the read-count stop condition
* db-relation-read-1001-1101 is a good example of how queries with ordinals make the code more complicated (range lookups) and why using ordinals is only supported when a filter on "left" is given

## db-relation-delete
* db-relation-delete differs from db-relation-read in that it does not need a state because it does not support partial processing
* it also differs in that it always needs to use all three relation dbi to complete the deletion instead of just any dbi necessary to match relations
