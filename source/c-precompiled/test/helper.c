#define debug_log_p 1
#include <stdio.h>
#include <stdlib.h>
#include <errno.h>
#include <pthread.h>
#include "../main/sph-db.h"
#include "../foreign/sph/one.c"
#define test_helper_db_root "/tmp/test-sph-db"
#define test_helper_path_data test_helper_db_root "/data"
#define set_plus_one(a) a = (1 + a)
#define set_minus_one(a) a = (a - 1)
#define test_helper_init(env_name) \
  status_init; \
  db_env_define(env_name)
#define test_helper_report_status \
  if (status_success_p) { \
    printf("--\ntests finished successfully.\n"); \
  } else { \
    printf( \
      "\ntests failed. %d %s\n", status.id, db_status_description(status)); \
  }
#define test_helper_test_one(func, env) \
  printf("%s\n", #func); \
  status_require_x(test_helper_reset(env, 0)); \
  status_require_x(func(env))
#define test_helper_assert(description, expression) \
  if (!expression) { \
    printf("%s failed\n", description); \
    status_set_id_goto(1); \
  }
status_t test_helper_reset(db_env_t* env, boolean re_use) {
  status_init;
  if ((*env).open) {
    db_close(env);
  };
  if ((!re_use && file_exists_p(test_helper_path_data))) {
    status_set_id(system("rm " test_helper_path_data));
    status_require;
  };
  status_require_x(db_open(test_helper_db_root, 0, env));
exit:
  return (status);
};
b0 test_helper_print_binary_b64(b64 a) {
  size_t i;
  b8 result[65];
  (*(64 + result)) = 0;
  for (i = 0; (i < 64); i = (1 + i)) {
    (*(i + result)) = (((((b64)(1)) << i) & a) ? '1' : '0');
  };
  printf("%s\n", result);
};