
/* return status code and error handling. uses a local variable named "status"
   and a goto label named "exit". a status has an identifier and a group to
   discern between status identifiers of different libraries. status id 0 is
   success, everything else can be considered a failure or special case. status
   ids are 32 bit signed integers for compatibility with error return codes from
   many other existing libraries. bindings with a ! suffix update the status
   from an expression */
/** like status init but sets a default group */
#define status_init_group(group) status_t status = {status_id_success, group}
#define status_id_success 0
#define status_group_undefined 0
#define status_init                                                            \
  status_t status = {status_id_success, status_group_undefined}
#define status_reset status_set_both(status_group_undefined, status_id_success)
#define status_success_p (status_id_success == status.id)
#define status_failure_p !status_success_p
#define status_goto goto exit
#define status_require                                                         \
  if (status_failure_p) {                                                      \
    status_goto;                                                               \
  }
#define status_set_group(group_id) status.group = group_id
#define status_set_id(status_id) status.id = status_id
#define status_set_both(group_id, status_id)                                   \
  status_set_group(group_id);                                                  \
  status_set_id(status_id)
/** update status with the result of expression, check for failure and goto
 * error if so */
#define status_require_x(expression)                                           \
  status = expression;                                                         \
  if (status_failure_p) {                                                      \
    status_goto;                                                               \
  }

/** set the status id and goto error */
#define status_set_id_goto(status_id)                                          \
  status_set_id(status_id);                                                    \
  status_goto
#define status_set_group_goto(group_id)                                        \
  status_set_group(group_id);                                                  \
  status_goto
#define status_set_both_goto(group_id, status_id)                              \
  status_set_both(group_id, status_id);                                        \
  status_goto
#define status_id_is_p(status_id) (status_id == status.id)
/** update status with the result of expression, check for failure and goto
 * error if so */
#define status_i_require_x(expression)                                         \
  status.id = expression;                                                      \
  if (status_failure_p) {                                                      \
    status_goto;                                                               \
  }
;
typedef b32_s status_i_t;
typedef struct {
  status_i_t id;
  b8 group;
} status_t;