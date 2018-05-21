#include "./helper.c"
status_t test_statistics(db_env_t* env) {
  status_init;
  db_statistics_t stat;
  db_txn_declare(env, txn);
  db_txn_begin(txn);
  status_require_x(db_statistics(txn, &stat));
exit:
  db_txn_abort(txn);
  return (status);
};
int main() {
  test_helper_init(env);
  test_helper_test_one(env, test_statistics);
exit:
  test_helper_report_status;
  return (status.id);
};