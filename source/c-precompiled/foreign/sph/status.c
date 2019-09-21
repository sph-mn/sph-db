/* return status as integer code with group identifier
for exception handling with a local variable and a goto label
status id 0 is success, everything else can be considered a special case or failure
status ids are signed integers for compatibility with error return codes from other existing libraries
group ids are strings used to categorise sets of errors codes from different libraries for example */
typedef struct {
  int id;
  uint8_t* group;
} status_t;
#define status_id_success 0
#define status_group_undefined ((uint8_t*)(""))
#define status_declare status_t status = { status_id_success, status_group_undefined }
#define status_is_success (status_id_success == status.id)
#define status_is_failure !status_is_success
#define status_return return (status)
#define status_set(group_id, status_id) \
  status.group = ((uint8_t*)(group_id)); \
  status.id = status_id
#define status_set_goto(group_id, status_id) \
  status_set(group_id, status_id); \
  goto exit
#define status_require(expression) \
  status = expression; \
  if (status_is_failure) { \
    goto exit; \
  }
#define status_i_require(expression) \
  status.id = expression; \
  if (status_is_failure) { \
    goto exit; \
  }
