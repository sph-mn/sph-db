
#define status_ii_init status_i_t status_ii = status_success
#define status_ii_success_p(a) (status_success == a)
#define status_ii_failure_p(a) !status_ii_success_p(a)
#define status_ii_require(a, cont)                                             \
  if (!(status_success == a)) {                                                \
    cont;                                                                      \
  }
#define status_ii_require_x(expression, cont)                                  \
  status_ii = expression;                                                      \
  status_ii_require(status_ii, cont)
#define status_ii_require_goto(a) status_ii_require(a, goto exit)
#define status_ii_require_goto_x(expression)                                   \
  status_ii_require_x(expression, goto exit)
#define status_ii_require_return(a) status_ii_require(a, return (status))
#define status_ii_require_return_x(expression)                                 \
  status_ii_require_x(expression, return (status))
