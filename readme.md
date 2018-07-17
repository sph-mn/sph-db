# about

sph-db is a database as a shared library for storing records with relations like a directed graph. it is supposed to be embeddable (link the shared library or include the full source code), minimalistic and fast.

* [homepage](http://sph.mn/c/view/52)
* [design](http://sph.mn/c/view/si).
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
each database can only be open once per process. multiple processes can open the same database and multiple threads can generally use it.
```c
dg_env_t env;
// database file will be created if not exists
status_require(db_open("/tmp/example", 0, &env));
// code that makes use of the database ...
db_close(&env);
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
