#include "./helper.c"
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
  db_statistics_t stat;
  db_type_id_t type_id;
  status_require_x(db_type_create(env, "test-type", 0, 0, 0, &type_id));
  debug_log("%lu", type_id);
  test_helper_assert("first type id", (1 == type_id));
exit:
  return (status);
};
int main() {
  test_helper_init(env);
  test_helper_test_one(env, test_statistics);
  test_helper_test_one(env, test_type_create);
exit:
  test_helper_report_status;
  return (status.id);
};