# internals

# overview of lmdb databases (b+trees)
* system: config-label [custom ...] -> data
  * format-label -> id-size id-type-size ordinal-size
  * type-label id -> name-len name field-count (field-type name-len name) ...
  * index-label type-id field-index ... -> null
* records
  * record-id: fixed-size-data ... (variable-size-len variable-size-data) ...
* relation-lr
  * left:record-id label:record-id -> ordinal:number right:record-id
* relation-rl
  * right:record-id label:record-id -> left:record-id
* relation-ll
  * label:record-id -> left:record-id
* i-{type-id}-{field-offset}[-{field-offset} ...]
  * field-data ... record-id -> null

# records
* all records are stored in one lmdb database
* ids include type information
  * to cluster records by type because records are stored sorted by id
  * to filter lists of relation target record ids by type cheaply
  * record-id: type-id element-id
  * zero is the null identifier. use case: unspecified relation labels, sources or targets
* sequences
  * one sequence per type
  * new identifiers are increments
  * counter initialised on open from finding max used identifier
* types
  * registered in the system lmdb database
  * have names
  * have information about fields
* fields
  * fields have names
  * fields are accessed by field index/offset and optionally name via translation function
  * fixed size columns are stored before variable size columns to make use of predictable offsets for fixed size types
* field types
  * identified by unsigned integers
  * fixed length field-type-ids are even numbers, variable length field-type-ids are odd numbers. negative/positive could perhaps have been used instead
  * fixed length integer and string types of varying size have calculated field-type-ids
    * fixed
      * integern
        * 3b:size-exponent 1b:signed 4b:id-prefix:0000
        * data size in bits: 2 ** (size-exponent + 3)
      * stringn
        * id: 4b:size-exponent 4b:id-prefix:0010
        * data size in bits: 2 ** (size-exponent + 4)
      * float32
      * float64
    * variable
      * binary
      * string
* indices
  * associate record field data with record ids
  * one separate lmdb database per index
  * update on each change of the associated record using the same transaction
* system cache
  * information about types, sequences and indices is loaded on open and cached in memory in the db_env_t struct
  * type structs are cached in a dynamically resized array with the type-id as index ("arrays are the fastest hash tables")
* relations
  * there must only be one relation for every combination of left, label and right. cyclic relations are allowed. relational integrity may be ignored when creating relations to avoid existence checks
  * when a record is deleted then all relations that contain its id are deleted with it
  * targets are stored ordered by ordinal value. ordinal values are stored only for the right part of the left-to-right direction to save space
  * by convention, left to right corresponds to general to specific
  * terminology for relation start and end point: left and right, or source and target for unspecified direction

# search performance
* records and relations are stored in b+trees with a basic o(log n) time complexity for search/insert/delete (average and worst case)
* record data: getting data from record identifiers requires only one basic b+tree search. it is as fast as it gets. data fields can be indexed
* relations: relations are filtered by giving lists of ids to match as arguments. 16 different filter combinations for left/label/ordinal/right, the parts of relations, are theoretically possible with db_relation_select. 2 are unsupported: all combinations that contain a filter for right and ordinal but not left. because the number of the possible combinations is small, pre-compiled code optimised for the exact filter combination is used when executing queries. the sph-db relation functionality is specifically designed for queries with these filters

here is a list of estimated, relative db_relation_read performance for each filter combination from best (fastest execution per element matched) to worst (slowest), some are equal:
```
* * * *
left * * *
* * * right
left label * *
* label * right
* * ordinal *
left * ordinal *
left * ordinal right
left label ordinal *
left label ordinal right
left label * right
left * * right
* label * *
* label ordinal *
```

not supported
```
* * ordinal right
* label ordinal right
```

# space requirements
* records and relations are stored in b+trees with a basic o(log n) space complexity (average and worst case)
* the required space depends on the chosen db_id_t and db_ordinal_t types. the maximum possible size for these types is restricted by the largest available c type supported by the compiler that is used to compile sph-db because pointers are not supported. the commonly supported range is 0 to 64 bit. the minimum possible size is 8 bit for identifiers and 0 bit for ordinals (untested)
* records
  * record-size = id-size + data-size
  * virtual-record-size = 0 (only exist in relations or field data)
* relations
  * relation-size = 8 * id-size + ordinal-size
  * example 1
    * id-size = 64, ordinal-size = 32
    * relation-size = 8 * 64 + 32 = 544
    * 544 bit per relation
    * 3e9 relations: 1632 gigabit of storage
    * 3e6 relations: 1632 megabit of storage
  * example 2
    * id-size = 32, ordinal-size = 0
    * relation-size = 8 * 32 + 0 = 256
    * 256 bit per relation
    * 3e9 relations: 768 gigabit
    * 3e6 relations: 768 megabit
  * implementation of unidirectional relations and omitted label-to-left indexing in the future can reduce the required size per relation to (2 * id-size + ordinal-size)

# code
* ``sph-db-extra.h`` contains declarations for internally used things
* the code assumes that "mdb_cursor_close" can be called with null pointers at some places

## db-relation-select
* chooses the reader, relevant databases and other values to use for the search
* checks if the database is empty
* applies the read offset

## db-relation-read
* supports partial reads. for example reading up to ten matches at a time. this is why a selection object is used. this has many subtle consequences, like having to get the current cursor value at the beginning of the reader code, to check if the right key or data is already set
* supports skipping: matching but not copying the result. this is used for reading from an offset
* cursors as arguments are assumed to be in position at the current entry on call
* readers must not be called after any failure status
* readers and deleters are built using stacked goto labels because this makes it much easier in this case to control the execution flow, compared to the alternative of nested while loops, particularly for choosing the best place for evaluating the read-count stop condition
* db-relation-read-1001-1101 is a good example of how queries with ordinals make the code more complicated (range lookups) and why using ordinals is only supported when a filter on "left" is given

## db-relation-delete
* db-relation-delete differs from db-relation-read in that it does not need a state because it does not support partial processing
* it also differs in that it always needs to use all three relation dbi to complete the deletion instead of just any dbi necessary to match relations

## conventions/principles
* batch processing by taking collections, and eventually processing multiple elements in one call, is preferred when call overhead like declarations, allocations and preparations are saved. single element collections can still be used
* call by contract is assumed strictly. a program might crash if the contract is broken. pro: few checks, efficiency
* output arguments come last
* acted-on arguments come first
* boolean variables are prefixed with is_, for example is_open
* in c function definitions the order is: declaration macros, declarations, initialisations, rest
* in c files the order is: general includes, macro definitions, function definitions. includes, macros and functions are ordered by dependency primarily and from more standard/external/general to more local/specific secondarily. for example include inttypes.h before myappfile.h, define string-join before database-open
* word separator is always underscore. if you need shift to type it, change that. indent with spaces
* pass by value if the argument is small and does not need to be mutated
