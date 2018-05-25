#include "./helper.c"
status_t test_open_empty(db_env_t* env) {
  status_init;
  test_helper_assert("env.open is true", (1 == (*env).open));
  test_helper_assert(
    "env.root is set", (0 == strcmp((*env).root, test_helper_db_root)));
exit:
  return (status);
};
status_t test_statistics(db_env_t* env) {
  status_init;
  db_statistics_t stat;
  db_txn_declare(env, txn);
  db_txn_begin(txn);
  status_require_x(db_statistics(txn, &stat));
  test_helper_assert("dbi-system has one entry", (1 == stat.system.ms_entries));
exit:
  db_txn_abort(txn);
  return (status);
};
#define db_field_set(a, a_type, a_name, a_name_len) \
  a.type = a_type; \
  a.name = a_name; \
  a.name_len = a_name_len
status_t test_type_create(db_env_t* env) {
  status_init;
  db_field_t fields[3];
  db_type_t type;
  db_type_id_t type_id;
  status_require_x(db_type_create(env, "test-type-1", 0, 0, 0, &type_id));
  type = (*((*env).types + type_id));
  test_helper_assert("type id", (1 == type_id) && (type_id == type.id));
  test_helper_assert("type sequence", (1 == type.sequence));
  test_helper_assert("type field count", (0 == type.fields_count));
  db_field_set((*(fields + 0)), db_field_type_int8, "test-field-1", 12);
  db_field_set((*(fields + 1)), db_field_type_int8, "test-field-2", 12);
  db_field_set((*(fields + 2)), db_field_type_int8, "test-field-3", 12);
  status_require_x(db_type_create(env, "test-type-2", 3, fields, 0, &type_id));
  type = (*((*env).types + type_id));
  debug_log("%lu %lu", type_id, type.id);
  test_helper_assert("second type id", (2 == type_id) && (type_id == type.id));
  test_helper_assert("second type sequence", (2 == type.sequence));
  test_helper_assert("second type field-count", (3 == type.fields_count));
  test_helper_assert(
    "second type name", (0 == strcmp("test-type-2", type.name)));
exit:
  return (status);
};
status_t test_open_nonempty(db_env_t* env) {
  status_init;
  status_require_x(test_type_create(env));
  status_require_x(test_helper_reset(env, 1));
exit:
  return (status);
};
int main() {
  test_helper_init(env);
  test_helper_test_one(env, test_type_create);
exit:
  test_helper_report_status;
  return (status.id);
};