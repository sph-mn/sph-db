# motivation
i want an embeddable database to store records without having to construct high-level query language strings for each query. the next best thing would probably be sqlite, but there i would waste resources with frequently building sql strings and having them parsed
graph-like relations in relational databases are usually modelled with tables (and custom naming schemes). it seems there is a more efficient way to store and query relations and it could be simpler to use with specialised built-in features
# applied knowledge
  i previously wrote sph-dg, which is an embeddable, lmdb based database focused on graph-like relations. it is used on this website, works reliable and relation queries are fast. i am a long-time user of database systems like sqlite, mysql, lmdb (interface similar to berkeley db), oracle databases and postgresql and have used almost all sql features professionally. ive been contemplating for years about more simplistic and more efficient multi-use information storage and retrieval for contexts like web applications, generative art and ai knowledge storage