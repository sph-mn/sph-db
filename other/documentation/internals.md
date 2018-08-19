nodes
  identifiers
    include type information
      for cheap filtering of relation targets by specific types
    sequencing
      new identifiers are monotonical increments
      counter initialised on start from max identifier
      type independent. maybe separate sequences for standard types. 64b ids are almost too small for this. bigger native c datatype needed
    null
      a special "null" node of type id must be available. for example the identifier zero
      use cases for this are unspecified relation labels, sources or targets
  node types
    fields are accessed by column index and indirectly name
    fixed size columns stored first to make use of predictable offsets
    node data format: fixed-size-data ... variable-size-len variable-size-data ...
    type info cache
      contains information about types and associated indices
      max type id size limited to 16 bit with initial implementation
      to make type id array index ("arrays are the fastest hash tables")
      format
        \#escape
          {type-id}:
            field-count:
            field-fixed-count:
            field-fixed-offsets:
            fields:
            flags:
            id:
            indices:
            indices-count:
            name:
            sequence:
  field types
    have integer ids
    fixed length field type ids are even numbers, variable length field type ids are odd numbers
    there are many possible fixed length integer and string types with ids and data size calculated from a formula
    fixed length types
      integer
        3b:size-exponent 1b:signed 4b:id-prefix:0000
        data size in bits: 2 ** (size-exponent + 3)
      string
        id: 4b:size-exponent 4b:id-prefix:0010
        data size in bits: 2 ** (size-exponent + 4)
      float32
      float64
    variable length types
      vbinary
      vstring
  btrees
    system: config-label [id/custom ...] -> data
      format-label -> id-size id-type-size ordinal-size
      type-label id -> name-len name field-count (field-type name-len name) ...
      index-label type-id field-index ... -> ()
relations
  when a node is deleted then all relations that contain its id must be deleted
  there must only be one relation for every combination of left, label and right. cyclic relations may be allowed. relational integrity may be ignored when creating relations to avoid existence checks
  targets are stored ordered by ordinal value. ordinal values are stored only for the left to right direction to save space
  by convention left to right corresponds to general to specific
  btrees
    \#escape
      left:node label:node -> ordinal:float right:node
      right:node label:node -> left:node
indices
  separate btrees with data as the key and node id as the value
  update on each insert of the associated node type using the same transaction
  might use a hook mechanism available for extensions
implementation search performance
  nodes and relations are stored in b+trees with a basic o(log n) time complexity for search/insert/delete (average and worst case)
  node data
    translating between node identifier and data requires only one basic b+tree search. it is as fast as it gets. data fields can be indexed
  relations
    relations are filtered by giving lists of ids to match as arguments. 16 different filter combinations for left/label/ordinal/right, the parts of relations, are theoretically possible with dg_relation_select. 2 are unsupported: all combinations that contain a filter for right and ordinal but not left. because the number of these possible combinations is relatively low, pre-compiled code optimised for the exact combination is used when executing queries. the sph-db relation functionality is specifically designed for queries with these combinations
    here is a list of estimated relative dg_relation_read performance for each filter combination from best (fastest execution per element matched) to worst (slowest), some are equal:
    \#escape
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
    not supported
    \#escape
      * * ordinal right
      * label ordinal right
implementation space usage
  nodes and relations are stored in b+trees with a basic o(log n) space complexity (average and worst case)
  the required space depends on the chosen dg_id_t and dg_ordinal_t types. the maximum possible size is restricted by the largest available c type supported by the compiler that is used to compile sph-db. the commonly available range is 0 to 64 bit. the minimum possible size is 8 bit for identifiers and 0 bit for ordinals (untested)
  nodes
    type id: id-size
    other types: id-size + data-size
    virtual: 0 (only exist in relations)
  relations
    6 * id-size + ordinal-size
  example with 64 32
    old calculation with bi-directional ordinal
    id-size = 64, ordinal-size = 32
    relation-size = 6 * 64 + 2 * 32 = 416
    416 bit per relation
    3e9 relations: ~1.3 terabit of storage
    3e6 relations: ~1.3 gigabit of storage
  example with 32 0
    old calculation with bi-directional ordinal
    id-size = 32, ordinal-size = 0
    relation-size = 6 * 32 + 2 * 0 = 192
    192 bit per relation
    3e9 relations: ~576 gigabit
    3e6 relations: ~576 megabit
