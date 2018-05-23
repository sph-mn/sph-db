#define db_id_t b64
#define db_type_id_t b16
#define db_ordinal_t b32
#define db_index_count_t b8
#define db_field_count_t b8
#define db_field_name_len_t b8
#define db_field_type_t b8
#define db_type_name_len_t b8
#define db_id_mask UINT64_MAX
#define db_type_id_mask UINT16_MAX
#define db_size_id sizeof(db_id_t)
#define db_size_type_id sizeof(db_type_id_t)
#define db_size_ordinal sizeof(db_ordinal_t)
#define db_id_equal_p(a, b) (a == b)
#define db_id_compare(a, b) ((a < b) ? -1 : (a > b))
