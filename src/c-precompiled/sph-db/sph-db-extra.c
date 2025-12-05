
/* secondary api for dealing with internals */

#define db_system_label_format 0
#define db_system_label_type 1
#define db_system_label_index 2
#define db_system_label_t uint8_t
#define db_selection_flag_skip 1
#define db_relation_selection_flag_is_set_left 2
#define db_relation_selection_flag_is_set_right 4
#define db_type_id_limit db_type_id_mask
#define db_element_id_limit db_id_element_mask
#define db_type_flag_virtual 1
#define db_size_type_id_max 16
#define db_system_key_label_t uint8_t
#define db_system_key_id_t db_type_id_t
#define db_size_system_key (sizeof(db_system_key_id_t) + sizeof(db_system_key_label_t))
#define db_pointer_to_id_at(a, index) *(index + ((db_id_t*)(a)))
#define db_pointer_to_id(a) *((db_id_t*)(a))
#define db_field_type_is_fixed(a) (0 < a)
#define db_system_key_label(a) *((db_system_key_label_t*)(a))
#define db_system_key_id(a) *((db_system_key_id_t*)((1 + ((db_system_key_label_t*)(a)))))
#define db_relation_data_to_id(a) db_pointer_to_id((1 + ((db_ordinal_t*)(a))))
#define db_relation_data_to_ordinal(a) *((db_ordinal_t*)(a))
#define db_relation_data_set_id(a, value) db_relation_data_to_id(a) = value
#define db_relation_data_set_ordinal(a, value) db_relation_data_to_ordinal(a) = value
#define db_relation_data_set_both(a, ordinal, id) \
  db_relation_data_set_ordinal(ordinal); \
  db_relation_data_set_id(id)
status_t db_sequence_next_system(db_env_t* env, db_type_id_t* result);
status_t db_sequence_next(db_env_t* env, db_type_id_t type_id, db_id_t* result);
void db_debug_log_id_bits(db_id_t a);
void db_debug_log_ids(db_ids_t a);
void db_debug_log_ids_set(db_id_set_t a);
void db_debug_log_relations(db_relations_t records);
status_t db_debug_log_btree_counts(db_txn_t txn);
status_t db_debug_count_all_btree_entries(db_txn_t txn, db_count_t* result);
status_t db_index_key(db_env_t* env, db_index_t index, db_record_values_t values, void** result_data, size_t* result_size);
status_t db_indices_entry_ensure(db_txn_t txn, db_record_values_t values, db_id_t id);
status_t db_indices_entry_delete(db_txn_t txn, db_record_values_t values, db_id_t id);