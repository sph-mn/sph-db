#include "./helper.c"
#define db_field_set(a, a_type, a_name, a_name_len) \
  a.type = a_type; \
  a.name = a_name; \
  a.name_len = a_name_len
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
status_t test_type_create(db_env_t* env) {
  status_init;
  db_field_t fields[3];
  db_field_count_t i;
  db_type_t type_1;
  db_type_t* type_1_pointer;
  db_type_id_t type_id_1;
  db_type_t type_2;
  db_type_id_t type_id_2;
  db_type_t* type_2_pointer;
  /* type 1 */
  status_require_x(db_type_create(env, "test-type-1", 0, 0, 0, &type_id_1));
  type_1 = (*((*env).types + type_id_1));
  test_helper_assert("type id", (1 == type_id_1) && (type_id_1 == type_1.id));
  test_helper_assert("type sequence", (1 == type_1.sequence));
  test_helper_assert("type field count", (0 == type_1.fields_count));
  db_field_set((*(fields + 0)), db_field_type_int8, "test-field-1", 12);
  db_field_set((*(fields + 1)), db_field_type_int8, "test-field-2", 12);
  db_field_set((*(fields + 2)), db_field_type_string, "test-field-3", 12);
  /* type 2 */
  status_require_x(
    db_type_create(env, "test-type-2", 3, fields, 0, &type_id_2));
  type_2 = (*((*env).types + type_id_2));
  test_helper_assert(
    "second type id", (2 == type_id_2) && (type_id_2 == type_2.id));
  test_helper_assert("second type sequence", (1 == type_2.sequence));
  test_helper_assert("second type field-count", (3 == type_2.fields_count));
  test_helper_assert(
    "second type name", (0 == strcmp("test-type-2", type_2.name)));
  for (i = 0; (i < type_2.fields_count); i = (1 + i)) {
    test_helper_assert("second type field name len equality",
      ((*(i + fields)).name_len == (*(i + type_2.fields)).name_len));
    test_helper_assert("second type field name equality",
      (0 == strcmp((*(i + fields)).name, (*(i + type_2.fields)).name)));
    test_helper_assert("second type type equality",
      ((*(i + fields)).type == (*(i + type_2.fields)).type));
  };
  /* type-get */
  test_helper_assert("non existent type", !db_type_get(env, "test-type-x"));
  type_1_pointer = db_type_get(env, "test-type-1");
  type_2_pointer = db_type_get(env, "test-type-2");
  test_helper_assert("existent types", (type_1_pointer && type_2_pointer));
  test_helper_assert("existent type ids",
    (((type_id_1 == (*type_1_pointer).id)) &&
      ((type_id_2 == (*type_2_pointer).id))));
  test_helper_assert("existent types",
    (db_type_get(env, "test-type-1") && db_type_get(env, "test-type-2")));
/* type-delete */
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
  test_helper_test_one(test_type_create, env);
exit:
  test_helper_report_status;
  return (status.id);
};