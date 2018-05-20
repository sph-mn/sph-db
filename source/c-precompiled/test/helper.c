#include <stdio.h>
#include <stdlib.h>
#include <errno.h>
#include <pthread.h>
#define increment(a) a = (1 + a)
#define decrement(a) a = (a - 1)
#define debug_log_p 1
#include "../main/sph-db.h"
#include "../main/lib/lmdb.c"
#include "../main/lib/debug.c"
#include "../foreign/sph/one.c"
#define test_helper_db_root "/tmp/test-sph-db"
#define test_helper_path_data test_helper_db_root "/data"
status_t test_helper_db_reset(db_env_t* env, boolean re_use) {
  status_init;
  if ((*env).open) {
    db_close(env);
  };
  if ((!re_use && file_exists_p(test_helper_path_data))) {
    status_set_id(system("rm " test_helper_path_data));
  };
  status_require;
  status_require_x(db_open(test_helper_db_root, 0, env));
exit:
  return (status);
};
#define test_helper_test_one(env, func) \
  printf("%s\n", #func); \
  status_require_x(test_helper_db_reset(env, 0)); \
  status_require_x(func(env))
