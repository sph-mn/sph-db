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
status_t test_type_create(db_env_t* env) {
  status_init;
  db_type_id_t type_id;
  status_require_x(db_type_create(env, "test-type", 0, 0, 0, &type_id));
  test_helper_assert("first type id", (1 == type_id));
  test_helper_assert("type sequence", (2 == (*(*env).types).sequence));
  test_helper_assert(
    "type cache id", (type_id == (*(type_id + (*env).types)).id));
exit:
  return (status);
};
status_t test_open_nonempty(db_env_t* env) {
  status_init;
  test_type_create(env);
  test_helper_reset(env, 1);
exit:
  return (status);
};
int main() {
  test_helper_init(env);
  test_helper_test_one(env, test_open_empty);
  test_helper_test_one(env, test_type_create);
  test_helper_test_one(env, test_open_nonempty);
exit:
  test_helper_report_status;
  return (status.id);
};