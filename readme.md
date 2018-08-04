# about

sph-db is a database as a shared library for storing records with relations like a directed graph. it is supposed to be embeddable (by code inclusion or linking) and efficient through minimalism.

* [homepage](http://sph.mn/c/view/52)
* [design](http://sph.mn/c/view/si)
* license: lgpl3+

# features
* acid compliant, memory-mapped database that can grow to any size that fits on the local filesystem, unrestricted by available ram
* direct, high-speed interface using c data structures. no overhead from sql or similar query language parsing
* nodes are records with identifiers for random access. they are of custom type, like tables in relational databases, and indexable
* relations are directed, labeled, unidirectionally ordered and small
* read-optimised design with full support for parallel database reads. database performance corresponds to the performance of lmdb. benchmarks for lmdb can be found [here](https://symas.com/lightning-memory-mapped-database/technical/).
* written in c via [sc](https://github.com/sph-mn/sph-sc)

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
db routines return a "status_t" object that contains a status and a status-group identifier (error code and source library identifier). bindings to work with this small object are included with the main header "sph-db.h". sph-db usually uses a goto label named "exit" per routine where undesired return status ids are handled.
the following examples assume this pattern of calling ``status_init`` beforehand and having a label named ``exit``

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
// database file will be created if it does not exist
status_require(db_open("/tmp/example", 0, env));
// code that makes use of the database ...
db_close(env);
```

## create node types
```
db_field_t fields[4];
db_type_t* type;
db_field_set(fields[0], db_field_type_int8, "test-field-1", 12);
db_field_set(fields[1], db_field_type_int8, "test-field-2", 12);
db_field_set(fields[2], db_field_type_string, "test-field-3", 12);
db_field_set(fields[3], db_field_type_string, "test-field-4", 12);
status_require(db_type_create(env, "test-type", fields, 4, 0, &type));
```

## create nodes
db_txn_declare(env, txn);
db_node_values_t values;
db_id_t id;
ui8 value_1;
i8 value_2;
ui8* value_3 = "abc";
ui8* value_4 = "abcde";
status_require(db_node_values_new(type, (&values)));
value_1 = 11;
value_2 = -128;
db_node_values_set((&values_1), 0, &value_1, 0);
db_node_values_set((&values_1), 1, &value_2, 0);
db_node_values_set((&values_1), 2, value_3, 3);
db_node_values_set((&values_1), 3, value_4, 5);
db_txn_write_begin(txn);
status_require(db_node_create(txn, values, &id));
db_txn_commit();

## read nodes
```
db_node_data_t node_data;
db_node_data_t field_data;
status_require(db_node_get(txn, id, &node_data));
field_data = db_node_data_ref(type, node_data, 1);

status_require(db_node_select(txn, ids_filter, type, offset, matcher, matcher_state, &state));
status_require_read(db_node_next(state));
field_data = db_node_ref(state, 0);
```

## create relations
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
## types
```c
db_ordinal_t(*db_graph_ordinal_generator_t)(void*);
status_t(*db_graph_reader_t)(db_graph_selection_t*,db_count_t,db_graph_records_t**);
boolean(*db_node_matcher_t)(db_id_t,db_node_data_t,void*);
db_data_record_t struct
  db_id_t id
  size_t size
  void* data
db_env_t struct
  MDB_dbi dbi_nodes
  MDB_dbi dbi_graph_ll
  MDB_dbi dbi_graph_lr
  MDB_dbi dbi_graph_rl
  MDB_dbi dbi_system
  MDB_env* mdb_env
  uint8_t open
  uint8_t* root
  pthread_mutex_t mutex
  int maxkeysize
  db_type_t* types
  db_type_id_t types_len
db_field_t struct
  uint8_t* name
  db_name_len_t name_len
  db_field_type_t type
  db_fields_len_t index
db_graph_record_t struct
  db_id_t left
  db_id_t right
  db_id_t label
  db_ordinal_t ordinal
db_graph_selection_t struct
  status_t status
  MDB_cursor* restrict cursor
  MDB_cursor* restrict cursor_2
  void* left
  void* right
  void* label
  db_ids_t* left_first
  db_ids_t* right_first
  db_ordinal_condition_t* ordinal
  uint8_t options
  void* reader
db_index_selection_t struct
  db_id_t current
  MDB_cursor* cursor
db_index_t struct db_index_t
  MDB_dbi dbi
  db_fields_len_t* fields
  db_fields_len_t fields_len
  db_type_t* type
db_node_data_t struct
  void* data
  size_t size
db_node_index_selection_t struct
  db_node_data_t current
  db_id_t current_id
  db_index_selection_t index_selection
  MDB_cursor* nodes
db_node_selection_t struct
  db_count_t count
  db_node_data_t current
  db_id_t current_id
  MDB_cursor* cursor
  db_env_t* env
  db_ids_t* ids
  db_node_matcher_t matcher
  void* matcher_state
  uint8_t options
  db_type_t* type
db_node_value_t struct
  db_data_len_t size
  void* data
db_node_values_t struct
  db_node_value_t* data
  db_fields_len_t last
  db_type_t* type
db_open_options_t struct
  uint8_t is_read_only
  size_t maximum_size
  db_count_t maximum_reader_count
  db_count_t maximum_db_count
  uint8_t filesystem_has_ordered_writes
  uint32_t_least env_open_flags
  uint16_t file_permissions
db_ordinal_condition_t struct
  db_ordinal_t min
  db_ordinal_t max
db_statistics_t struct
  MDB_stat system
  MDB_stat nodes
  MDB_stat graph_lr
  MDB_stat graph_rl
  MDB_stat graph_ll
db_txn_t struct
  MDB_txn* mdb_txn
  db_env_t* env
db_type_t struct
  db_fields_len_t fields_len
  db_fields_len_t fields_fixed_count
  size_t* fields_fixed_offsets
  db_field_t* fields
  uint8_t flags
  db_type_id_t id
  struct db_index_t* indices
  db_indices_len_t indices_len
  size_t indices_size
  uint8_t* name
  db_id_t sequence
```

## enum
```c
enum{db_status_id_success,db_status_id_undefined,db_status_id_condition_unfulfilled,db_status_id_data_length,db_status_id_different_format,db_status_id_duplicate,db_status_id_input_type,db_status_id_invalid_argument,db_status_id_max_element_id,db_status_id_max_type_id,db_status_id_max_type_id_size,db_status_id_memory,db_status_id_missing_argument_db_root,db_status_id_notfound,db_status_id_not_implemented,db_status_id_path_not_accessible_db_root,db_status_id_index_keysize,db_status_group_db,db_status_group_lmdb,db_status_group_libc}
```

## routines
```c
db_field_t* db_type_field_get(db_type_t* type, uint8_t* name)
db_index_t* db_index_get(db_type_t* type, db_fields_len_t* fields, db_fields_len_t fields_len)
db_node_data_t db_node_data_ref(db_type_t* type, db_node_data_t data, db_fields_len_t field)
db_node_data_t db_node_ref(db_node_selection_t* state, db_fields_len_t field)
db_type_t* db_type_get(db_env_t* env, uint8_t* name)
status_t db_debug_count_all_btree_entries(db_txn_t txn, db_count_t* result)
status_t db_debug_display_btree_counts(db_txn_t txn)
status_t db_debug_display_content_graph_lr(db_txn_t txn)
status_t db_debug_display_content_graph_rl(db_txn_t txn)
status_t db_graph_delete(db_txn_t txn, db_ids_t* left, db_ids_t* right, db_ids_t* label, db_ordinal_condition_t* ordinal)
status_t db_graph_ensure(db_txn_t txn, db_ids_t* left, db_ids_t* right, db_ids_t* label, db_graph_ordinal_generator_t ordinal_generator, void* ordinal_generator_state)
status_t db_graph_ensure(db_txn_t txn, db_ids_t* left, db_ids_t* right, db_ids_t* label, db_graph_ordinal_generator_t ordinal_generator, void* ordinal_generator_state)
status_t db_graph_read(db_graph_selection_t* state, db_count_t count, db_graph_records_t** result)
status_t db_graph_select(db_txn_t txn, db_ids_t* left, db_ids_t* right, db_ids_t* label, db_ordinal_condition_t* ordinal, db_count_t offset, db_graph_selection_t* result)
status_t db_graph_select(db_txn_t txn, db_ids_t* left, db_ids_t* right, db_ids_t* label, db_ordinal_condition_t* ordinal, db_count_t offset, db_graph_selection_t* result)
status_t db_index_create(db_env_t* env, db_type_t* type, db_fields_len_t* fields, db_fields_len_t fields_len)
status_t db_index_delete(db_env_t* env, db_index_t* index)
status_t db_index_key(db_env_t* env, db_index_t index, db_node_values_t values, void** result_data, size_t* result_size)
status_t db_index_name(db_type_id_t type_id, db_fields_len_t* fields, db_fields_len_t fields_len, uint8_t** result, size_t* result_size)
status_t db_index_next(db_index_selection_t state)
status_t db_index_rebuild(db_env_t* env, db_index_t* index)
status_t db_index_select(db_txn_t txn, db_index_t index, db_node_values_t values, db_index_selection_t* result)
status_t db_indices_entry_delete(db_txn_t txn, db_node_values_t values, db_id_t id)
status_t db_indices_entry_ensure(db_txn_t txn, db_node_values_t values, db_id_t id)
status_t db_node_create(db_txn_t txn, db_node_values_t values, db_id_t* result)
status_t db_node_data_to_values(db_type_t* type, db_node_data_t data, db_node_values_t* result)
status_t db_node_delete(db_txn_t txn, db_ids_t* ids)
status_t db_node_exists(db_txn_t txn, db_ids_t* ids, uint8_t* result)
status_t db_node_get(db_txn_t txn, db_id_t id, db_node_data_t* result)
status_t db_node_index_next(db_node_index_selection_t selection)
status_t db_node_index_select(db_txn_t txn, db_index_t index, db_node_values_t values, db_node_index_selection_t* result)
status_t db_node_next(db_node_selection_t* state)
status_t db_node_select(db_txn_t txn, db_ids_t* ids, db_type_t* type, db_count_t offset, db_node_matcher_t matcher, void* matcher_state, db_node_selection_t* result_state)
status_t db_node_skip(db_node_selection_t* state, db_count_t count)
status_t db_node_update(db_txn_t txn, db_id_t id, db_node_values_t values)
status_t db_node_values_new(db_type_t* type, db_node_values_t* result)
status_t db_node_values_to_data(db_node_values_t values, db_node_data_t* result)
status_t db_open(uint8_t* root, db_open_options_t* options, db_env_t* env)
status_t db_sequence_next(db_env_t* env, db_type_id_t type_id, db_id_t* result)
status_t db_sequence_next_system(db_env_t* env, db_type_id_t* result)
status_t db_statistics(db_txn_t txn, db_statistics_t* result)
status_t db_txn_begin(db_txn_t* a)
status_t db_txn_commit(db_txn_t* a)
status_t db_txn_write_begin(db_txn_t* a)
status_t db_type_create(db_env_t* env, uint8_t* name, db_field_t* fields, db_fields_len_t fields_len, uint8_t flags, db_type_t** result)
status_t db_type_delete(db_env_t* env, db_type_id_t id)
uint8_t db_field_type_size(uint8_t a)
uint8_t* db_status_description(status_t a)
uint8_t* db_status_description(status_t a)
uint8_t* db_status_group_id_to_name(status_id_t a)
uint8_t* db_status_group_id_to_name(status_id_t a)
uint8_t* db_status_name(status_t a)
uint8_t* db_status_name(status_t a)
void db_close(db_env_t* env)
void db_debug_display_graph_records(db_graph_records_t* records)
void db_debug_log_ids(db_ids_t* a)
void db_debug_log_ids_set(imht_set_t a)
void db_graph_selection_destroy(db_graph_selection_t* state)
void db_graph_selection_destroy(db_graph_selection_t* state)
void db_index_selection_destroy(db_index_selection_t* state)
void db_node_index_selection_destroy(db_node_index_selection_t* selection)
void db_node_selection_destroy(db_node_selection_t* state)
void db_node_values_set(db_node_values_t* values, db_fields_len_t field_index, void* data, size_t size)
void db_txn_abort(db_txn_t* a)
```

## macros
```c
db_calloc(variable, count, size)
db_count_t
db_data_len_max
db_data_len_t
db_declare_ids(name)
db_element_id_limit
db_env_define(name)
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
db_field_type_is_fixed(a)
db_field_type_string
db_field_type_t
db_field_type_uint16
db_field_type_uint32
db_field_type_uint64
db_field_type_uint8
db_fields_len_t
db_graph_data_set_both(a, ordinal, id)
db_graph_data_set_id(a, value)
db_graph_data_set_ordinal(a, value)
db_graph_data_to_id(a)
db_graph_data_to_ordinal(a)
db_graph_selection_flag_is_set_left
db_graph_selection_flag_is_set_right
db_id_add_type(id, type_id)
db_id_element(id)
db_id_element_mask
db_id_mask
db_id_t
db_id_type(id)
db_id_type_mask
db_ids_add_require(target, source, ids_temp)
db_index_len_t
db_malloc(variable, size)
db_malloc_string(variable, len)
db_name_len_max
db_name_len_t
db_node_virtual_to_data(id)
db_null
db_ordinal_compare
db_ordinal_t
db_pointer_allocation_set(result, expression, result_temp)
db_pointer_to_id(a)
db_pointer_to_id_at(a, index)
db_realloc(variable, variable_temp, size)
db_selection_flag_skip
db_size_element_id
db_size_graph_data
db_size_graph_key
db_size_system_label
db_size_type_id_max
db_status_memory_error_if_null(variable)
db_status_require_read(expression)
db_status_set_id_goto(status_id)
db_status_success_if_notfound
db_system_key_id(a)
db_system_key_label(a)
db_system_label_format
db_system_label_index
db_system_label_type
db_txn_abort_if_active(a)
db_txn_declare(env, name)
db_txn_is_active(a)
db_type_flag_virtual
db_type_id_limit
db_type_id_mask
db_type_id_t
```

# other language bindings
* scheme: [sph-db-guile](https://github.com/sph-mn/sph-db-guile)

# additional features and caveats
* custom data types can be specified with preprocessor definitions before compiling the sph-db library in a file named config.c
* the data type of node identifiers for new databases can be set at compile time. currently identifiers can not be pointers
* the maximum number of type creations is currently 65535 and the maximum size of dg-type-id is 16 bit
* returned data pointers, not other values like integers, are only valid until the transaction is aborted or committed
* returned data pointers point to data that is immutable and should be treated as such
* make sure that db-data-list*, db-ids-t*, db-relation-records-t* and db-data-records-t* are set to 0 before adding the first element or otherwise the first link will likely point to a random memory initialisation address
* all readers add elements until they fail, which means there might be elements that need deallocation after an error occurred
* transactions are lmdb transactions. when in doubt you may refer to the documentation of lmdb
* there can/should only be one active transaction per thread. nested transactions are possible though
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
