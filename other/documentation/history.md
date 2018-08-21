# history
history of the design and implementation of sph-db

## 2018-04
after exploring designs for a more traditional table based database, the codebase of sph-db became relevant again after it was found that table based databases lead to complexities with node/row types and relations between them. particularly the fact that the node/row type has to be always known anyway and the complexity of having multiple relation tables and selecting them seemed convincing to come back to have the type encoded in the node id and store all in the same btree again, even if that costs significantly more space especially with relations

## 2018-03
relations were relatively efficient to store but it became clear that structured data is not. low-complexity records with selective indexing and full table searches were not possible. only unnecessarily costly records could be created, for example using relations with nothing less than maximum normalisation and full indexes for the complete row data. tables, column indexes and custom key-value associations seemed missing

insights: separate btrees are like prefixing a data area and more space saving than prefixing each element with a type for example. when using one big btree to relate any type with any other, relations need to store the type of related nodes when the datatype is required to address elements. the row is the more fundamental abstraction, not the relations/graph. rows are like arrays with eventually variable-length and typed fields

custom node/row/record types and limiting which nodes can be linked has interesting implications for data retrieval and storage space

## 2016
earlier versions of sph-db did not support labelled relations, but instead had relations that themselves have ids and identifier aliases. this was found to be overcomplicated and particularly too costly in terms of space usage and processing. one insight was that relations without labels are like relations with all having the same label. these relations are on their own not distinguishable semantically, especially for partitioning data and for deciding if specific relations are not needed anymore. relations between numbers posed another problem: since only a node with data type was specified, creating relations to numbers would potentially lead to a very high number of nodes. to solve this, a new node type that contains a number in the identifier and only exists in relations was introduced. earlier versions also used classifications like "shape" or "connectors" for nodes, but these classifications turned out to be useless at least in this context.

nodes were identifiers optionally with deduplicated binary data with one possible identifier per unique binary sequence, this made records costly

# author
## motivation
i want an embeddable database to store records without having to construct high-level query language strings for each query. the next best thing would probably be sqlite, but there i would waste resources with frequently building sql strings and having them parsed
graph-like relations in relational databases are usually modelled with tables (and custom naming schemes). it seems there is a more efficient way to store and query relations and it could be simpler to use with specialised built-in features

## applied knowledge
i previously wrote sph-dg, which is an embeddable, lmdb based database focused on graph-like relations. it is used on this website, works reliable and relation queries are fast. i am a long-time user of database systems like sqlite, mysql, lmdb (interface similar to berkeley db), oracle databases and postgresql and have used almost all sql features professionally. ive been contemplating for years about more simplistic and more efficient multi-use information storage and retrieval for contexts like web applications, generative art and ai knowledge storage
