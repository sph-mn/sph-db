# about

sph-db is a database as a shared library for records and relations.

* [homepage](http://sph.mn/c/view/52)
* [design](http://sph.mn/c/view/si)
* license: lgpl3+

# project goals
* a minimal embeddable database to store records without having to construct high-level query language strings for each query
* graph-like relations without having to manage many-to-many tables

# features
## data model
* nodes that can be in relations to build a graph
* nodes have identifiers for random access. they are of custom type, similar to table rows in relational databases, and indexable
* relations are directed, labeled, unidirectionally ordered and small

## technology
* acid compliant, memory-mapped database that can grow to any size that fits on the local filesystem, unrestricted by available ram
* direct, high-speed interface using c data structures. no overhead from sql or similar query language parsing
* embeddable by linking or code inclusion
* read-optimised design with full support for parallel database reads
* efficient through focus on limited feature-set and thin abstraction over lmdb. benchmarks for lmdb can be found [here](https://symas.com/lightning-memory-mapped-database/technical/)
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

optionally execute ``./exe/test`` to see if the tests run successful.

# usage in c
## compilation of programs using sph-db
for example with gcc:
```bash
gcc example.c -o example-executable -llmdb -lsph-db
```

## inclusions of api declarations
```c
#include "<sph-db.h>"
```

## error handling
db routines return a "status_t" object that contains a status and a status-group identifier (error code and source library identifier). bindings to work with this small object are included with the main header "sph-db.h". sph-db internally usually uses a goto label named "exit" per routine where undesired return status ids are handled. ``status_require`` goes to exit on any failure status (status.id not zero).
the following examples assume this pattern of calling ``status_declare`` beforehand and having a label named ``exit``

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
* returned data pointers, for example by db_node_data_ref, are only valid until the transaction is aborted or committed. such data pointers are always only for reading and must never be written to

declare a transaction handle variable
```c
// db-env-t*, custom_variable_name
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

in the following examples, where a ``txn`` variable occurs, use of the appropriate transaction features is implied.

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

fields can be fixed length (signed and unsigned integers, 64 and 32 bit floating point, characters) or variable length (string, binary). possible field types are db_field_type_* macro variables, see api reference below.

## create nodes
```c
db_node_values_t values;
db_id_t id-1;
db_id_t id-2;
ui8 value_1 = 11;
i8 value_2 = -128;
ui8* value_3 = "abc";
ui8* value_4 = "abcde";
status_require(db_node_values_new(type, &values));
// arguments: db_node_values_t*, field_index, value_address, size.
// size is ignored for fixed length types
db_node_values_set(&values_1, 0, &value_1, 0);
db_node_values_set(&values_1, 1, &value_2, 0);
db_node_values_set(&values_1, 2, value_3, 3);
db_node_values_set(&values_1, 3, value_4, 5);
status_require(db_node_create(txn, values, &id-1));
status_require(db_node_create(txn, values, &id-2));
db_node_values_free(&values);
```

## read nodes
if no results are found or the end of results has been reached, status is set to db_status_id_notfound. here is one example of how to handle this
```c
// like status_require but tolerates notfound
status_require_read(db_node_next(state));
if(db_status_id_notfound != status.id) {
  field_data = db_node_ref(state, 0);
}
```

by unique identifier
```c
db_id_t id;
db_node_data_t node_data;
db_node_data_t field_data;
id = 123;
status_require_read(db_node_get(txn, id, &node_data));
if(db-status-id-notfound != status.id) {
  // arguments: type, node_data, field_index
  field_data = db_node_data_ref(type, node_data, 1);
  // field_data.data: void*, field_data.size: size_t
}
```

all of type
```c
db_node_selection_t selection;
db_node_data_t field_data;
// argumens: db_txn_t, db_ids_t*, db_type_t*, offset, matcher, matcher_state, &selection));
status_require(db_node_select(txn, 0, type, 0, 0, 0, &selection));
status_require_read(db_node_next(selection));
if(db-status-id-notfound != status.id) {
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
i_array_allocate_db_ids_t(ids, 3);
i_array_add(ids, 10);
i_array_add(ids, 15);
i_array_add(ids, 28);
status_require(db_node_select(txn, ids, 0, 0, 0, 0, &selection));
status_require_read(db_node_next(selection));
status_require_read(db_node_next(selection));
db_node_selection_finish(&selection);
```

by custom matcher function
```c
boolean node_matcher(db_id_t id, db_node_data_t data, void* matcher_state) {
  db_node_data_t field_data;
  field_data = db_node_data_ref(type, node_data, 2);
  *((ui8*)(matcher_state)) = 1;
  return 1;
};
ui8 matcher_state = 0;
status_require(db_node_select(txn, 0, type, 0, node_matcher, &matcher_state, &selection));
status_require(db_node_next((&selection)));
```

## create relations
# todo: update examples
```c
db_ids_t* left = 0;
db_ids_t* right = 0;
db_ids_t* label = 0;
db_txn_declare(env txn);
// store node-ids in left, right and label

db_txn_write_begin(txn);

// create relations for each label between all the specified left and right nodes (relations = left * right * label)
status_require(db_graph_ensure(txn, left, right, label, 0, 0));
db_txn_commit(txn);

exit:
  if(db_txn_active(txn)) db_txn_abort(txn);
  // deallocate the id lists
  db_ids_destroy(left);
  db_ids_destroy(right);
  db_ids_destroy(label);
```

## read relations
```c
db_ids_t* ids_left = 0;
db_ids_t* ids_label = 0;
db_graph_records_t* records = 0;
db_graph_selection_t state;
db_txn_introduce;

// node ids to be used to filter
ids_left = db_ids_add(ids_left, 123);
ids_label = db_ids_add(ids_label, 456);

// select relations whose left side is in "ids_left" and label in "ids_label"
status_require(db_graph_select(db_txn, ids_left, 0, ids_label, 0, 0, &state))

// read 2 of the selected relations
db_status_require_read(db_graph_read(&state, 2, &records));

// read as many matching relations as there are left
db_status_require_read(db_graph_read(&state, 0, &records));

db_graph_selection_destroy(&state);

// display records. "ordinal" might not be set in the record unless the query uses a filter for a left value
while(records) {
  record = db_graph_records_first(records);
  printf("record: %lu %lu %lu %lu\n", record.left, record.label, record.ordinal, record.right);
  records = db_graph_records_rest(records);
};

exit:
  if(db_txn) db_txn_abort;
  db_ids_destroy(ids_left);
  db_ids_destroy(ids_label);
  db_graph_records_destroy(records);
```

## create indices
## read nodes via indices
index_select();
node_index_select();

# api
# routines
```c
db_close :: db_env_t*:env -> void
db_env_new :: db_env_t**:result -> status_t
db_field_type_size :: uint8_t:a -> uint8_t
db_graph_delete :: db_txn_t:txn db_ids_t*:left db_ids_t*:right db_ids_t*:label db_ordinal_condition_t*:ordinal -> status_t
db_graph_ensure :: db_txn_t:txn db_ids_t:left db_ids_t:right db_ids_t:label db_graph_ordinal_generator_t:ordinal_generator void*:ordinal_generator_state -> status_t
db_graph_read :: db_graph_selection_t*:state db_count_t:count db_graph_records_t*:result -> status_t
db_graph_select :: db_txn_t:txn db_ids_t*:left db_ids_t*:right db_ids_t*:label db_ordinal_condition_t*:ordinal db_count_t:offset db_graph_selection_t*:result -> status_t
db_graph_select :: db_txn_t:txn db_ids_t*:left db_ids_t*:right db_ids_t*:label db_ordinal_condition_t*:ordinal db_count_t:offset db_graph_selection_t*:result -> status_t
db_graph_selection_finish :: db_graph_selection_t*:state -> void
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
db_sequence_next :: db_env_t*:env db_type_id_t:type_id db_id_t*:result -> status_t
db_sequence_next_system :: db_env_t*:env db_type_id_t*:result -> status_t
db_statistics :: db_txn_t:txn db_statistics_t*:result -> status_t
db_status_description :: status_t:a -> uint8_t*
db_status_description :: status_t:a -> uint8_t*
db_status_group_id_to_name :: status_id_t:a -> uint8_t*
db_status_group_id_to_name :: status_id_t:a -> uint8_t*
db_status_name :: status_t:a -> uint8_t*
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

# macros
```c
db_count_t
db_data_len_max
db_data_len_t
db_field_set(a, a_type, a_name, a_name_len)
db_field_type_binary
db_field_type_char16
db_field_type_char32
db_field_type_char64
db_field_type_char8
db_field_type_float32
db_field_type_float64
db_field_type_int16
db_field_type_int32
db_field_type_int64
db_field_type_int8
db_field_type_string
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
db_index_len_t
db_name_len_max
db_name_len_t
db_node_virtual_to_data(id)
db_null
db_ordinal_compare
db_ordinal_t
db_pointer_allocation_set(result, expression, result_temp)
db_size_element_id
db_size_graph_data
db_size_graph_key
db_status_require_read(expression)
db_status_set_id_goto(status_id)
db_status_success_if_notfound
db_txn_abort_if_active(a)
db_txn_declare(env, name)
db_txn_is_active(a)
db_type_id_mask
db_type_id_t
```

# types
```c
db_graph_ordinal_generator_t: void* -> db_ordinal_t
db_graph_reader_t: db_graph_selection_t* db_count_t db_graph_records_t* -> status_t
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
db_graph_record_t: struct
  left: db_id_t
  right: db_id_t
  label: db_id_t
  ordinal: db_ordinal_t
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
  env_open_flags: uint32_t_least
  file_permissions: uint16_t
db_ordinal_condition_t: struct
  min: db_ordinal_t
  max: db_ordinal_t
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
```

# enum
```c
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

# additional features and caveats
* custom data types can be specified with preprocessor definitions before compiling the sph-db library in a file named config.c
* the data type of node identifiers for new databases can be set at compile time. currently identifiers can not be pointers
* the maximum number of type creations is currently 65535 and the maximum size of dg-type-id is 16 bit
* returned data pointers point to data that is immutable and should be treated as such
* make sure that db-data-list*, db-ids-t*, db-relation-records-t* and db-data-records-t* are set to 0 before adding the first element or otherwise the first link will likely point to a random memory initialisation address
* all readers add elements until they fail, which means there might be elements that need deallocation after an error occurred
* transactions are lmdb transactions. when in doubt you may refer to the documentation of lmdb
* make sure that you do not try to insert ordinals or ids bigger than what is defined to be possible by the data types for ordinals and node identifiers. otherwise numerical overflows might occur
* updating an ordinal value requires the re-creation of the corresponding relation. ordinals are primarily intended to store data in a pre-calculated order for fast ordered retrieval but not for frequent updates. for example, the ordinal field is perhaps not well suited for storing a constantly changing vote count or weight value

# db-relation-select and db-relation-read
* "db-relation-select" internally chooses the reader, relevant databases and other values to use for the search
* if a filter parameter is set to a non-zero value, it is used, otherwise no filter is used for that parameter
* search strategies for most filter combinations are pre-coded to make it fast

## features
* partial reads with max read count: the reader can be called repeatedly to get the next results from the full result set
* optional filter by left, right, label, minimum and maximum ordinal/weight. for filtering by ordinal, "left" filter values must be given
* offset: selected results begin after ``n`` matches
* can return results and indicate the end of the data stream in one call

## development
this section is for when you want to change the core sph-db itself.

### setup
* install the development dependencies listed above
* clone the sourcecode repository "git clone https://github.com/sph-mn/sph-db.git"
* clone the submodule repositories. "git submodule init" "git submodule update"

### notes
* "mdb_cursor_close" seems to be ok with null pointers like "free"
* struct db-index-errors-*-t field names have the format {type-of-error}-{source-key-name}-{source-value-name}
* when values from an secondary dbi are not found in the primary dbi, they are excess values. when values from a primary dbi are not found in a secondary dbi (index), they are missing values. with these terms, which are used in the validator routines, one can discern the location of errors
* conception, tests that use the new features, memory-leak tests
* sph-db-extra.h contains declarations for internally used things

## db-relation-select
* positions every relevant mdb cursor at the first entry of the dbi or exits with an error status if the database is empty
* chooses an appropiate reader routine
* applies the read offset

## db-relation-read
readers of type "db-relation-reader-t" support the following:
* partial reads. for example reading up to ten matches at a time. this is why a state object is used (this has many subtle consequences, like having to get the current cursor value at the beginning of the reader code to check if the right key or data is already set)
* skipping: matching but not copying the result. this is used for preparing reads from an offset
* cursors as arguments are always in position at a valid entry. the reader ends as soon as all results have been read or an error has occurred and eventually rejects additional calls with the same state by returning the same end-of-data or error result
* readers and deleters are built using stacked goto labels because this makes it much easier in this case to control the execution flow, compared to the alternative of nested while loops. especially for choosing the best place for evaluating the read-count stop condition
* db-relation-read-1001-1101 is a good example of how queries with ordinals make the code more complicated (range lookups), and why using ordinals is only supported when a filter on "left" is given

## db-relation-delete
* db-relation-delete differs from db-relation-read in that it does not need a state because it does not support partial processing
* it also differs in that it always needs to use all three relation dbi to complete the deletion instead of just any dbi necessary to match relations
