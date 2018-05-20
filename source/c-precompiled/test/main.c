#include "./helper.c"
status_t test_statistics(db_env_t* db_s) {
  status_init;
  db_txn_define(db_s, txn);
  db_statistics_t stat;
  status_require_x(db_statistics(txn, &stat));
exit:
  if (txn.mdb_txn) {
    db_txn_abort(txn);
  };
  return (status);
};
status_t test_init(db_env_t* db_s) {
  status_init;
  status_require_x(test_helper_db_reset(db_s, 1));
exit:
  return (status);
};
int main() {
  status_init;
  db_s_define(db_s);
  test_helper_test_one(db_s, test_init);
  test_helper_test_one(db_s, test_statistics);
exit:
  if (status_success_p) {
    printf("--\ntests finished successfully.\n");
  } else {
    printf("\ntests failed. %d %s\n", status.id, db_status_description(status));
  };
  return (status.id);
};