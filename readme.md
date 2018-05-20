# about

sph-db is a database as a shared library for storing records with relations like a directed graph. it is supposed to be embeddable (link the shared library or include the full source code), minimalistic and fast

* [homepage](http://sph.mn/c/view/52)
* [design](http://sph.mn/c/view/si).
* license: lgpl3+

# features
* fully acid compliant, memory-mapped database that can grow to any size that fits on the local filesystem, unrestricted by available ram
* direct, high-speed interface using c data structures. no overhead from sql or similar query language parsing
* nodes are records with identifiers for random access. they are of custom type, like tables in relational databases and indexable
* relations are directed, labelled, ordered and small
* read optimised design with full support for parallel database reads. database performance nearly matches the performance of lmdb. benchmarks for lmdb can be found [here](https://symas.com/lightning-memory-mapped-database/technical/).
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
1. change into the project directory and execute ``./exe/compile-c``
1. execute ``./exe/install``. this supports one optional argument: a path prefix to install to

optionally execute ``./exe/test`` to see if the tests run successful.

# usage in c
the following documentation is work in progress for v2018

## compilation of programs using sph-db
for example with gcc:
```bash
gcc example.c -o example-executable -llmdb -lsph-db
```

## inclusion of declarations
```c
#include "<sph-db.h>"
```

## error handling
db routines return a "status_t" object that contains a status and a status-group identifier (error code and source library identifier). bindings to work with this small object are included with the main header "sph-db.c". sph-db usually uses a goto label named "exit" per routine where undesired return status ids are handled.
the following examples assume this pattern of calling ``status_init`` beforehand and having a label named ``exit``:

```c
int main() {
  status_init;
  // example code ...
exit:
  return status.id;
}
```

## initialisation
each database can only be open once per process. multiple processes can open the same database and multiple threads can generally use it.
```c
dg_env_t env;
status_require_x(db_open("/tmp/example", 0, &env));
// code that makes use of the database ...
db_close(&env);
```

## create relations
```c
db_ids_t* ids_left = 0;
db_ids_t* ids_right = 0;
db_ids_t* ids_label = 0;
db_txn_introduce;

// create some nodes. node ids are needed to create relations.
// in this example nodes of type "id" are created, which do not have data stored with them.
// the second argument to db_id_create specifies how many new nodes should be created.
db_txn_write_begin;
status_require_x(db_id_create(db_txn, 1, &ids_left));
status_require_x(db_id_create(db_txn, 1, &ids_right));
// used as the labels of the relations. labels are ids of nodes
status_require_x(db_id_create(db_txn, 1, &ids_label));

// create relations for each label between all the specified left and right nodes (relations = left * right * label)
status_require_x(db_relation_ensure(db_txn, left, right, label, 0, 0));
db_txn_commit;

exit:
  if(db_txn) db_txn_abort;
  // deallocate the id lists
  db_ids_destroy(ids_left);
  db_ids_destroy(ids_right);
  db_ids_destroy(ids_label);
```

## read relations
```c
db_ids_t* ids_left = 0;
db_ids_t* ids_label = 0;
db_relation_records_t* records = 0;
db_relation_read_state_t state;
db_txn_introduce;

// node ids to be used to filter
ids_left = db_ids_add(ids_left, 123);
ids_label = db_ids_add(ids_label, 456);

// select relations whose left side is in "ids_left" and label in "ids_label"
status_require_x(db_relation_select(db_txn, ids_left, 0, ids_label, 0, 0, &state))

// read 2 of the selected relations
db_status_require_read_x(db_relation_read(&state, 2, &records));

// read as many matching relations as there are left
db_status_require_read_x(db_relation_read(&state, 0, &records));

db_relation_selection_destroy(&state);

// display records. "ordinal" might not be set in the record unless the query uses a filter for a left value
while(records) {
  record = db_relation_records_first(records);
  printf("record: %lu %lu %lu %lu\n", record.left, record.label, record.ordinal, record.right);
  records = db_relation_records_rest(records);
};

exit:
  if(db_txn) db_txn_abort;
  db_ids_destroy(ids_left);
  db_ids_destroy(ids_label);
  db_relation_records_destroy(records);
```

## node type creation
## node creation
## relation creation
## index creation

# api
*work in progress*

## types
```c
db_id_t
db_type_id_t
db_ordinal_t
db_txn_t
status_i_t
db_ordinal_t (*db_relation_ordinal_generator_t)(b0*)
status_t (*db_relation_reader_t)(db_relation_read_state_t*,b32,db_relation_records_t**)
status_t struct
  status_i_t id;
  uint8_t group;
```

## enum
```c
db_status_id_undefined, db_status_id_input_type, db_status_id_max_id,
db_status_id_data_length, db_status_id_not_implemented, db_status_id_duplicate,
db_status_id_memory, db_status_id_condition_unfulfilled, db_status_id_missing_argument_db_root,
db_status_id_path_not_accessible_db_root, db_status_id_no_more_data, db_status_group_db,
db_status_group_lmdb, db_status_group_libc
```

## routines
```c
db_ids_t* db_ids_add(db_ids_t* a, db_id_t value)
db_ids_t* db_ids_drop(db_ids_t* a)
db_open_options_t db_open_options_set_defaults(db_open_options_t* a)
db_relation_records_t* db_relation_records_add(db_relation_records_t* a, db_relation_record_t value)
db_relation_records_t* db_relation_records_drop(db_relation_records_t* a)
size_t db_data_list_length(db_data_list_t* a)
size_t db_data_records_length(db_data_records_t* a)
size_t db_ids_length(db_ids_t* a)
size_t db_relation_records_length(db_relation_records_t* a)
status_t db_open(uint8_t* db_root_path, db_open_options_t* options)
status_t db_node_read(db_node_read_state_t* state, uint32_t count, db_data_records_t** result)
status_t db_node_select(db_txn_t* txn, uint8_t types, uint32_t offset, db_node_read_state_t* state)
status_t db_relation_delete(db_txn_t* txn, db_ids_t* left, db_ids_t* right, db_ids_t* label, db_ordinal_match_data_t* ordinal)
status_t db_relation_ensure(db_txn_t* txn, db_ids_t* left, db_ids_t* right, db_ids_t* label, db_relation_ordinal_generator_t ordinal_generator, void* ordinal_generator_state)
status_t db_relation_read(db_relation_read_state_t* state, uint32_t count, db_relation_records_t** result)
status_t db_relation_select(db_txn_t* txn, db_ids_t* left, db_ids_t* right, db_ids_t* label, db_ordinal_match_data_t* ordinal, uint32_t offset, db_relation_read_state_t* result)
status_t db_statistics(db_txn_t* txn, db_statistics_t* result)
uint8_t* db_status_description(status_t a)
uint8_t* db_status_group_id_to_name(status_i_t a)
uint8_t* db_status_name(status_t a)
void db_close(dg_env_t* env)
void db_node_selection_destroy(db_node_read_state_t* state)
void db_relation_selection_destroy(db_relation_read_state_t* state)
```

## macros
```c
db_data_list_first
db_data_list_first_address
db_data_list_rest
db_data_records_first
db_data_records_first_address
db_data_records_rest
db_id_compare(a, b)
db_id_equal_p(a, b)
db_ids_first
db_ids_first_address
db_ids_rest
db_null
db_ordinal_compare
db_relation_records_first
db_relation_records_first_address
db_relation_records_rest
db_status_require_read_x(expression)
db_status_set_id_goto(status_id)
db_status_success_if_mdb_notfound
db_status_success_if_no_more_data
db_txn_abort
db_txn_begin
db_txn_commit
db_txn_write_begin
status_failure_p
status_goto
status_group_undefined
status_id_is_p(status_id)
status_id_success
status_init
status_require
status_require_x(expression)
status_reset
status_set_both(group_id, status_id)
status_set_both_goto(group_id, status_id)
status_set_group(group_id)
status_set_group_goto(group_id)
status_set_id(status_id)
status_set_id_goto(status_id)
status_success_p
```

## variables
```c
db_index_errors_relation_t db_index_errors_relation_null
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
this section is only intended for when you want to change the core sph-db itself.

* make sure that the field "mv-size" of db-null is never set to something other than zero, as it is assumed to be zero. failure to do so can lead to index corruption (which the validator routines can detect)
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
