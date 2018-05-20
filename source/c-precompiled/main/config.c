typedef b64 db_id_t;
typedef b16 db_type_id_t;
typedef b32 db_ordinal_t;
#define db_id_max UINT64_MAX
#define db_size_id sizeof(db_id_t)
#define db_size_type_id sizeof(db_type_id_t)
#define db_size_ordinal sizeof(db_ordinal_t)
#define db_id_equal_p(a, b) (a == b)
#define db_id_compare(a, b) ((a < b) ? -1 : (a > b))
