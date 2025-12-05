
/* compile-time configuration start */

#define db_id_t uint64_t
#define db_type_id_t uint16_t
#define db_ordinal_t uint16_t
#define db_id_mask UINT64_MAX
#define db_type_id_mask UINT16_MAX
#define db_name_len_t uint8_t
#define db_name_len_max UINT8_MAX
#define db_fields_len_t uint16_t
#define db_indices_len_t uint16_t
#define db_count_t uint32_t
#define db_batch_len 100
#define db_id_printf_format "%" PRIu64
#define db_type_id_printf_format "%" PRIu16
#define db_ordinal_printf_format "%" PRIu16

/* compile-time configuration end */
