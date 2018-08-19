# considerations
considered questions and decisions while developing sph-db

todo: finish conversion of this document to markdown, cleanup sections

# evaluation of existing databases
##  sqlite
###    sqlite pros
      multi platform, well tested, well documented, query optimiser, views, order on select, group, filtering sub-selects, triggers, sql-functions, integer primary keys are automatically auto increment,
###    sqlite cons
      building sql strings requires quoting, escaping and generally protection against sql-injection. this adds another step where strings are analysed and possibly rewritten
      a table where all fields make up the key might not be possible as efficiently. in lmdb btrees one could use duplicate keys or store all fields in the key without values
      cant predict or guarantee order of values as it is stored
      all types are variable length and all data fields are therefore prefixed with type information
      checking if an index exists is not supported
      no duplicate keys
      might require copying of selected data for reading instead of offering pointers to it directly
      there is always an integer row id, even when using for example strings as the primary key
###    shared features
      create indices selectively for columns
      filter table rows whose columns match column content and logical conditions
      automatically find and use index
###    sph-db pros
      better performance (fewer code paths, more minimalistic design, lmdb)
      uses c data structures for queries instead of sql strings which need to be constructed by concatenating strings then parsed and evaluated
      sph-db gives access to data without copying. preparator (select) -> row-positioner (next) -> column-accessor (get)
      direct lmdb btree access support. custom key-value btrees can be added to the environment
      no relation tables with specific layout and indices needs to be managed
      automatic deletion of dependent relations when deleting nodes instead of having to create triggers
      low-level filter functions that can make range searches on compound key parts and dont have to analyse table schemas or find indices
## graph databases
   graph databases typically store relations in an adjacency list, which tends to be efficient for traversing long paths from node to node. but adjacency lists alone arent particularly well suited for frequent random access to nodes as if they are records or finding all relations a node is in. sph-db uses b+trees instead because its focus is on mapping data to identifiers (random access) and filtering on the set of all relations (optimised for queries such as "is x related to y" and "which nodes are adjacent to x" but not optimised for long path traversals)


# return notfound with read
## option - selected
position at first, get current, position at next
cant position at element before the first
that is why cursor is always at the current
### pro
* results and notfound in one call
### con
* wouldnt have to position at next for first call
## option
if positioned at first element then get else next
readers dont signal notfound when there is data to read
### con
extra branch
### pro
readers behave more predictably

# what about virtual nodes and fields
could technically encode multiple 8 bit fields or similar
## option - selected
no field types
initially not supported
on demand

# schema changes require cache updates
  cache update should only be made after the mdb transaction committed
  option - selected
    make schema changes have their own transaction each time
    pro
      less complex
    con
      lower performance for a high frequency of schema changes. but is a significant amount at all likely
  option
    track schema updates with transaction and apply after successful mdb commit
# inserts while type deletion
  scenario
    delete/insert might both happen on the same or concurrent transactions
    data for a deleted type could be inserted
  consequences
    data becomes inaccessible and wastes space
    if type id is ever reused, previously existing data becomes accessible again. this can be confusing in the new data model. sensitive data can become re-accessible with inadequate treatment
  option - first choice
    as long as type id re-use is not implemented, ignore the issue for initial versions
  option - second choice
    relieve integrity issues and delete excess data on demand
    feature
      delete all data with type id when a new type is created
      old data might still waste space but can never be confused with new data
    feature
      add a garbage collection helper that deletes all data for type ids that are unused
    pro
      low complexity, performant solution
  option
    prevent excess type data from ever existing
    feature
      register inserted types in transaction
      acquire schema lock before each commit with inserts or type delete
      before commit with inserts check if all inserted types still exist
    con
      mutex locking/unlocking on every insert
      extra complexity to track cross-transaction conflicts
index access after deletion
  index open -> index delete -> index read -> error
  all index handles become invalid with delete
how to choose and use indices
  manually
  either selecting only node ids or nodes
  cursor to make custom btree searches
# database handles or one open database per process
  option - selected
    handles
    can use multiple databases in one process
    almost all api routines require a transaction object which encapsulates the environment
  option
    process
    dont have to pass handle argument to any api routine
    must use separate processes and ipc to work with multiple databases
# custom extra fields for relations
  changing the default btree would add considerable complexity
  possible with labels that are compound data structure nodes
# use as key-value database
  possible with lmdb features
# uses
## use as key-value database
possible with native lmdb features. lmdb data structures available in the various types
## use as traditional table/record database
possible with typed nodes

# should partial indices be supported
initially not supported

# clustering of nodes of the same type
archieved by having sorted keys and the type at the beginning of keys

# complex filter conditions for node selection
  for example conditions in a nestable linked list with comparison operators. and/or/not/begins/ends/contains/equals/etc
  or custom tests and branches
  more complex searchers could be an extension
  con custom
    index selection cant come from conditions
    elevating the process to higher-level languages exacerbates costs
  pro custom
    hardcoded checks. no interpretation of a condition query structure
# where to encode the type of each individual node
  option
    store type in identifier
    con
      increased node identifier length. affects every node. affects every relation times four (source and target in two directions)
    pro
      node id is enough to know the type. selecting just relations allows to filter by node type before accessing the nodes
      cheap type filtering
      relations between all types possible in one btree. no type combination-specific btree selection/creation process or need to access multiple btrees for a query
  option
    store type with the data
    con
      store type in data. makes get-all-of-type-x more difficult. possibly needs additional index
      duplicated with each node
      have to read data to find type
    pro
      smaller relations
  option
    group nodes types by using separate btrees
    con
      types need to be carried with node references to find relevant grouping btrees and read record fields
      one btree for each type combination necessary. has to be foaund for queries, possible multiple have to be accessed for different types
    pro
      space saving not every row needs to store type information
# informal column types
  the database only needs a fixed and a variable length binary record field datatype to store data
  informal datatypes are string, integer and similar, anything beyond binary
  are stored with the schema to not avoid having to store it somewhere else for reifying useful datatypes
# unidirectional relations to save space
  maybe later
  issue: how to garbage collect unidirectional relations
  option (favorite): go by label. label must be connected
  option: maybe decay factor and regular refresh but it should completely be an application issue at this point
# database network distribution
there are ideas, but takes more resources to implement
rudimentary implementation (read scaling): a hook for each modification that gets passed the current write transaction and an extension that manages a commit log and applies it on another host
  see \.(link-c-one ("database" "distribution"))
# the common database design
  type has to be carried anyway with ids
  one btree for all tables or one for each table
    one for each table because space saving. acts like a prefix to a data collection
  graph
    should label/ordinal be optional
    should graph relations of both directions be kept in the sys-info cache
    are extra relation fields needed
      example situation: "when every ingredient can be added in different amounts to different recipes"
  custom primary keys
    a dictionary, a one to one association, could be considered a subset of plain tables. yet rows might have row identifiers identifying a pairing even if that is not needed. this points to custom primary keys
    ambiguity of auto-increment and provided keys. each insert requires additional checks if auto-increment and eventually if key has been provided. id key potentially quicker to join as primary and foreign key because efficient data type
  sequences per table or one global
    global
      fast load on startup
    per-table
      can theoretically be shorter
      need to query every table on startup
      good for many fragmented types with local cluster relations
    variable per-table
      size loaded from schema variable
# what happens on concurrent delete and update for a row
  description
    update is reinsert
    last committed transaction wins
    that is why updates are problematic
    "update might prevent deletion"
# how to create custom virtual types
  set the corresponding flag with db-type-new
# how to select type ids by name
  option - current choice
    cache data scan
    no extra work
    relatively fast operation
  option
    index btree
  option
    system data scan
# how to alter a type
  initially not supported
  planned to require complete rewrite of all rows unless append
  reason is maximum space saving and performance for the row encoding
  an option might be to create new types for the changed versions,
  use multiple as if one and merge at some point (might never become fully merged)
# one sequence for all types or one per type
  64 bit keys seem desirable because it is the biggest native c datatype. but with that size and a reasonable 16 bit type identifier, only 48 bit are left for row ids
  initialise counters in array. type id equals index
  at startup, for each type, find max id
# selecting from multiple indices
  intersection of multiple index search results
# are more node operations needed
  node first/last/prev/range etc seem not needed because of the data model (ids to for the user unsorted data)
  but should allow first/last forward/reverse searches
# multiple rows or one result at a time with node-next
  difference to graph read: less filters
  option
    read returns next one - selected
    add to state current match and return
    set state to current row data (id, size, pointer)
    pro
      copy only id and data pointer to result
      custom row content matching can stop at find (main reason for choice)
    con
      repeated call cost when reading multiple rows
      repeated call initialisation (declare status/val-null/val-id/val-data, return status)
      may need to repeatedly extract and cache values from state
  option
    read returns list of multiple
    create list of result data references
    :: state count -> row-list
    con
      malloc for each row result struct
      when filtering rows one by one more results than needed might have been loaded
    pro
      less function calls, eventually less preparation to continue search (no re-allocation of cursor, status and mdb-val local variables)
      works the same as for relations. less likely to be changed for relations at this point
      timely more separated data read and use step separates repeated processes and might improve low-level caching
  how sqlite does it
    to read all fields: col-count, index, type, one of multiple type getters
    state contains pointer to current data
    state can be advanced without copying or accessing data
    select syntax can vary col count
    get-col-count(state)
    get-type(state, col-index)
    choose get-value function then get-value(state, col-index)
# how to filter with node-select
  options: by id, by type, by matcher
  type filter
    option - selected
      equal only
      one type to match
      multiple calls possible for searching different types
      matcher function will differ anyway per type
      easily extendable to any matcher if needed
      pro
        most needed option
    option
      any
      list of types to match
      con
        needs new list type
  row data filter
    option - selected
      optional supplied matcher function
      call of matcher function for each existing row
      vs returning and allowing re-call
      pro
        saves node-next call costs
    option
      built-in matchers for fields
      probably special match structure/config needed
      con
        considerable extra complexity
  combinations (ids, type):
    0 0: all nodes of any type. unsupported
    ids type: all nodes of ids and type. unsupported
    ids 0: nodes of ids
    0 type: all nodes of type
    i dont see a purpose for 00 and 11 filters, so i tend towards having it unsupported for now
    what would one be searching for with 0 0? to match algorithmically you need shared properties
    how could it be useful to filter by ids and type, since ids contain the type. it is just filtering the plain list and checking existence
# allow mixed-type node-select
  for example with an ids list read from graph relations
  usually databases cant select cross type, they can also not read fields cross type
  always possible to go type by type with separate searches
  also it would add a feature that might be lost with a slightly different base design
  checking for type after match is cheaper than calling node-select for each type
  option - selected
    call db select once with mixed-type ids then check type of results
  option
    malloc new lists separated by type
    call db select for each
  option
    call db select multiple times with mixed-type ids and specify a type filter for each
    in effect filtering the list multiple times but not reallocating
# node values datatype
  option - selected
    array of field count length
    pro
      easy to check if data set and overwrite
    con
      types with many fields and few fields set waste memory
  option
    linked-list with field offset in element
    pro
      saves space by storing only the fields that are set
    con
      costly to check if data already set, but perhaps not necessary
      conversion to btree data needs to check if field set or not
      malloc per element
# should graph-read be changed to read one-by-one and not lists
  con
    seems everything is already matched at read
  pro
    saves malloc per element
# why implement db-node-exists
  use case: externally retrieved ids list, for example page id or file ids, quick check if even exists before loading details
# field value get error handling
  error cases
    requested field index out of bounds
    result memory allocation error
  option - selected
    on error default value for type
    done so by sqlite
  option
    return status-t
# copy or reference selected strings
  option - selected
    reference or copy using different functions
  option
    reference
    might allow for writes into the database
    invalid on transaction end
    pro
      no copy if just searching
# how to identify indices
  option - selected
    type-id and field-offsets
    con
      more data required
      longer btree string name
    pro
      id numbers would have to be found by specifying type and fields anyway
  option
    monotonically increasing id
    pro
      less complex btree name creation (uint->string)
    con
      exhausts system sequence
# schema changes and synchronisation
  other active transactions might be trying to use the schema and for example insert data of types that are deleted
  option - selected
    make schema changes have their own transaction each time
    pro
      less complex
    con
      lower performance for many schema changes. but schema changes arent predicted to be frequent enough
  option
    allow schema changes and data changes with the same transaction
    add schema updates to be done to transaction handle and apply after successful mdb commit
