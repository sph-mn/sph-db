/* return status code and error handling. uses a local variable named "status" and a goto label named "exit".
      a status has an identifier and a group to discern between status identifiers of different libraries.
      status id 0 is success, everything else can be considered a failure or special case.
      status ids are 32 bit signed integers for compatibility with error return codes from many other existing libraries.
      group ids are strings to make it easier to create new groups that dont conflict with others compared to using numbers */
#define sph_status 1
#define status_id_success 0
#define status_group_undefined ""
#define status_declare status_t status = { status_id_success, status_group_undefined }
#define status_reset status_set_both(status_group_undefined, status_id_success)
#define status_is_success (status_id_success == status.id)
#define status_is_failure !status_is_success
#define status_goto goto exit
/** like status declare but with a default group */
#define status_declare_group(group) status_t status = { status_id_success, group }
#define status_set_both(group_id, status_id) \
  status.group = group_id; \
  status.id = status_id
/** update status with the result of expression and goto error on failure */
#define status_require(expression) \
  status = expression; \
  if (status_is_failure) { \
    status_goto; \
  }
/** set the status id and goto error */
#define status_set_id_goto(status_id) \
  status.id = status_id; \
  status_goto
#define status_set_group_goto(group_id) \
  status.group = group_id; \
  status_goto
#define status_set_both_goto(group_id, status_id) \
  status_set_both(group_id, status_id); \
  status_goto
/** like status-require but expression returns only status.id */
#define status_id_require(expression) \
  status.id = expression; \
  if (status_is_failure) { \
    status_goto; \
  }
typedef int32_t status_id_t;
typedef struct {
  status_id_t id;
  uint8_t* group;
} status_t;