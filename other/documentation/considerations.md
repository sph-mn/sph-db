# considerations
notes on considered questions and decisions made while developing sph-db

# evaluation of existing databases
* sqlite
  * sqlite pros
    * multi platform, well tested, well documented, views, order on select, group, filtering sub-selects, triggers, sql-functions, query optimiser, integer primary keys are automatically auto increment, automatically find and use indices
  * sqlite cons
    * building sql strings requires quoting, escaping and generally protection against sql-injection. another step where strings are analysed and possibly rewritten
    * cant predict or guarantee order of values as they are stored
    * all types are variable length and all data fields are therefore probably prefixed with type information
    * checking if an index exists is not supported
    * might require copying of selected data for reading instead of offering pointers to it directly
    * there is always an integer row id anyway, even when using for example strings as the primary key
  * shared features
    * create indices selectively for columns
    * filter table rows whose columns match column content and logical conditions
  * sph-db pros
    * better performance (fewer code paths, more minimalistic design, lmdb)
    * uses c data structures for queries instead of sql strings which need to be constructed by concatenating strings then parsed and evaluated
    * sph-db gives access to data without copying
    * direct lmdb btree access support. custom key-value btrees can be added to the environment
    * no junction tables with specific layout, naming and indices need to be managed
    * automatic deletion of dependent relations when deleting nodes instead of first having to create triggers
* graph databases
  * graph databases typically store relations in an adjacency list, which tends to be efficient for traversing long paths from node to node. but adjacency lists alone arent particularly well suited for frequent random access to nodes as if they are records or finding all relations a node is in. sph-db uses b+trees instead because its focus is on mapping data to identifiers (random access) and filtering on the set of all relations (optimised for queries such as "is x related to y" and "which nodes are adjacent to x" but not optimised for long path traversals)
* relational databases
  * for relations between various types, the type has to be carried anyway with ids
  * one btree for each table because space saving. acts like a prefix to a data collection

# uses
* use as key-value database
  * possible with native lmdb features. lmdb data structures available in the various types
* use as traditional table/record database
  * possible with types and nodes

# how to type nodes
* option - selected
  * store type in identifier
  * con
    * increased node identifier length. affects every node. affects every relation times four (source and target in two directions)
  * pro
    * node id is enough to know the type. selecting just relations allows to filter by node type before accessing the nodes
    * cheap type filtering
    * relations between all types possible in one btree. no type combination-specific btree selection/creation process or need to access multiple btrees for a query
* option
  * group node types by using separate btrees
  * con
    * types need to be carried with node references to find relevant grouping btrees and read record fields
    * one btree for each type combination necessary. has to be found for queries, possible multiple have to be accessed for different types
  * pro
    * space saving as not every row needs to store type information
* option
  * store type with the data
  * con
    * store type in data. makes get-all-of-type-x more difficult. possibly needs additional index
    * duplicated with each node
    * have to read data to find type
  * pro
    * smaller relations

# how to cluster nodes of the same type
archieved by having sorted keys and the type at the beginning of keys

# should readers possibly return results and end-of-data in one call
* option - selected
  * position at first, get current, position at next
  * cant position at element before the first. that is why cursor is always at the current
  * pro
    * results and end-of-data in one call
  * con
    * wouldnt have to position at next for first call
* option
  * if positioned at first element then get else next
  * readers dont signal notfound when there is data to read
  * con
    * extra branch
  * pro
    * readers behave more predictably with read actually only reading the next

# multiple rows or one result at a time with node-read
difference to graph read: less filters

* option
  * read returns next one
  * add to state current match and return
  * set state to current row data (id, size, pointer)
  * pro
    * copy only id and data pointer to result
    * custom row content matching can stop at find (main reason for choice)
  * con
    * repeated call cost when reading multiple rows
    * repeated call initialisation (declare status/val-null/val-id/val-data, return status)
    * may need to repeatedly extract and cache values from state
* option
  * read returns list of multiple
  * create list of result data references
  * :: state count -> row-list
  * con
    * malloc for each row result struct
    * when filtering rows one by one more results than needed might have been loaded
  * pro
    * less function calls, eventually less preparation to continue search (no re-allocation of cursor, status and mdb-val local variables)
    * works the same as for relations. less likely to be changed for relations at this point
    * timely more separated data read and use step separates repeated processes and might improve low-level caching
* how sqlite does it
  * to read all fields: col-count, index, type, one of multiple type getters
  * state contains pointer to current data
  * state can be advanced without copying or accessing data
  * select syntax can vary col count
  * get-col-count(state)
  * get-type(state, col-index)
  * choose get-value function then get-value(state, col-index)

## how to return reader results
* option - selected
  * multiple using array type
  * pro
    * read count is specified/known on call
* option
  * one per read/next call
  * con
    * call overhead
* option
  * multiple using linked-lists
  * con
    * malloc per added list element

# should virtual nodes support multiple fields
could technically encode multiple 8 bit fields or similar

* option - selected
  * no field types
  * initially not supported
  * on demand

# how to choose and use indices
* option - selected
* manually
* either selecting only node ids or nodes via ids from index
* custom btree searches possible with lmdb features or extension

# database handles or one open database per process
* option - selected
* handles
* can use multiple databases in one process
* almost all api routines require a transaction object which encapsulates the environment
* option
* process, global variable
* dont have to pass handle argument to any api routine
* must use separate processes and ipc to work with multiple databases

# should complex filter conditions be supported for node selection
* for example conditions in a nestable linked list with comparison operators. and/or/not/begins/ends/contains/equals/etc
* or custom tests and branches
* more complex searchers could be an extension
* con
  * elevating the process to higher-level languages exacerbates costs

# should partial indices be supported
initially not supported

# informal field types
* the database only needs one fixed and one variable length field datatype to store data, as no conversions take place
* informal datatypes are string, integer and similar, anything beyond binary
* are stored with the schema to not avoid having to store it somewhere else for reifying useful datatypes

# unidirectional relations to save space
* maybe later
* how to garbage collect unidirectional relations
  * option (favorite): go by label. label must be connected
  * option: maybe decay factor and regular refresh but it should completely be an application issue at this point

# database network distribution and scaling
* there are ideas, but takes more resources to implement
* rudimentary implementation (read scaling): a hook for each modification that gets passed the current write transaction and an extension that manages a commit log and applies it on another host

# are custom extra relation fields needed
* change of the default btree would add considerable complexity
* possible with labels that are compound data structure node ids
* example situation: "when every ingredient can be added in different amounts to different recipes"

# sequences per table or one global
* option - selected
  * per-table
    * can theoretically be shorter
    * need to query every table to load current sequence number on startup
* option
  * database global
    * fast load on startup
    * exhausts much sooner
* option
  * custom-size per-table
    * size loaded from schema variable

# what happens on concurrent delete and update for a row
* update is reinsert
* probably last committed transaction wins
* that is why updates are problematic
* "update might prevent deletion"

# how to create custom virtual types
set the corresponding flag with db-type-new

# how to select type ids by name
* option - current choice
  * cache data scan
  * no extra work
  * relatively fast operation
* option
  * index btree
* option
  * system data scan

# how to alter a type
* initially not supported
* planned to require complete rewrite of all rows unless append
* reason is maximum space saving and performance for the row encoding
* an option might be to create new types for the changed versions,
* use multiple as if one and merge at some point (might never become fully merged)

# one sequence for all types or one per type
* 64 bit keys seem desirable because it is the biggest native c datatype. but with that size and a reasonable 16 bit type identifier, only 48 bit are left for row ids
* initialise counters in array. type id equals index
* at startup, for each type, find max id

# selecting from multiple indices
intersection of multiple index search results

# are more node operations needed
node first/last/prev/range etc seem not needed because of the data model (ids to for the user unsorted data)

# how to filter with node-select
* options: by id, by type, by matcher
*  type filter
* option - selected
  * equal only
  * one type to match
  * multiple calls possible for searching different types
  * matcher function will differ anyway per type
  * easily extendable to any matcher if needed
  * pro
  * most needed option
* option
  * any
  * list of types to match
  * con
    * needs new list type
* option - selected
  * optional supplied matcher function
  * call of matcher function for each existing row
  * vs returning and allowing re-call
  * pro
    * saves node-next call costs
* option
  * built-in matchers for fields
  * probably special match structure/config needed
    * con
      * considerable extra complexity
* combinations (ids, type):
  * 0 0: all nodes of any type. unsupported
  * ids type: all nodes of ids and type. unsupported
  * ids 0: nodes of ids
  * 0 type: all nodes of type
  * i dont see a purpose for 00 and 11 filters, so i tend towards having it unsupported for now
  * what would one be searching for with 0 0? to match algorithmically you need shared properties
  * how could it be useful to filter by ids and type, since ids contain the type. it is just filtering the plain list and checking existence

# allow mixed-type node-select
* for example with an ids-list, then read from graph relations
* usually databases cant select cross type, they can also not read fields cross type
* always possible to go type by type with separate searches
* would be a feature that might be lost with a slightly different base design
* checking for type after match is cheaper than calling node-select for each type
* option - selected
  * call db select once with mixed-type ids then check types of results
* option
  * malloc new lists separated by type
  * call db select for each
* option
  * call db select multiple times with mixed-type ids and specify a type filter for each
  * in effect filtering the list multiple times but not reallocating

# node values datatype
* option - selected
  * array of field count length
  * pro
    * easy to check if data is set and overwrite
  * con
    * types with many fields and few fields set waste memory
* option
  * linked-list with field index in element
  * pro
    * saves space by storing only the fields that are set
  * con
    * costly to check if data already set
    * malloc per element

# why implement db-node-get or db-node-exists
* faster for externally retrieved ids list, for example page id or file ids, to quickly check if even exists before loading details

# copy or reference selected fields
* option - selected
  * only reference
* option
  * reference or copy using different functions
* option
  * reference
  * might allow for writes into the database
  * invalid on transaction end
  * pro
    * no copy if just searching

# how to identify indices
* option - selected
  * type-id and field-offsets
  * con
    * more data required
    * longer btree string name
  * pro
    * id numbers would have to be found by specifying type and fields anyway
* option
  * monotonically increasing id
  * pro
    * less complex btree name creation (uint->string)
  * con
    * exhausts system sequence

# schema changes and concurrent database operations
other active transactions might be trying to use the schema and for example insert data of types that are deleted

* option - selected
  * make schema changes have their own transaction each time
  * pro
    * less complex
  * con
    * lower performance for many schema changes. but schema changes arent predicted to be frequent enough
* option
  * allow schema changes and data changes with the same transaction
  * track schema updates in transaction struct and apply after successful mdb commit

# inserts for a type being deleted
* delete/insert might both happen on the same or concurrent transactions
* data for a deleted type could be inserted
* data becomes inaccessible and wastes space
* if type id is ever reused, previously existing data becomes accessible again. this can be confusing in the new data model. sensitive data can become re-accessible with inadequate treatment
* option - selected
  * delete all data with the new type id when a new type is created
* option
  * as long as type id re-use is not implemented, ignore the issue for initial versions
  * old data might still waste space but can never be confused with new data
* option
  * add a garbage collection helper that deletes all data for type ids that are unused
* option
  * prevent excess type data from ever existing
  * feature
    * register inserted types in transaction
    * acquire schema lock before each commit with inserts or type delete
    * before commit with inserts check if all inserted types still exist
  * con
    * mutex locking/unlocking on every insert
    * extra complexity to track cross-transaction conflicts

# other
* should label/ordinal be optional
* if separate type-to-type btrees are used, should graph relations of both directions be kept in the sys-info cache
* custom primary keys
  * a dictionary, a one to one association, could be considered a subset of plain tables. yet rows might have row identifiers identifying a pairing even if that is not needed. this points to custom primary keys
  * con: ambiguity of auto-increment and provided keys. each insert requires additional checks if auto-increment and eventually if key has been provided. id key potentially quicker to join as primary and foreign key because efficient data type
