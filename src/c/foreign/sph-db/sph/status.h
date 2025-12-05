
#ifndef sph_status_h_included
#define sph_status_h_included

/* return status as integer code with group identifier
for exception handling with a local variable and a goto label
status id 0 is success, everything else can be considered a special case or failure
status ids are signed integers for compatibility with error return codes from other existing libraries
group ids are strings, used to categorise sets of errors codes from different libraries for example */
#include <inttypes.h>
typedef struct {
  int id;
  char* group;
} status_t;
#define status_id_success 0
#define status_group_undefined ""
#define status_declare status_t status = { status_id_success, status_group_undefined }
#define status_is_success (status_id_success == status.id)
#define status_is_failure !status_is_success
#define status_return return (status)
#define status_i_return return ((status.id))
#define status_goto goto exit
#define status_set(group_id, status_id) \
  status.group = group_id; \
  status.id = status_id
#define status_set_goto(group_id, status_id) \
  status_set(group_id, status_id); \
  status_goto
#define status_require(expression) \
  status = expression; \
  if (status_is_failure) { \
    status_goto; \
  }
#define status_i_require(expression) \
  status.id = expression; \
  if (status_is_failure) { \
    status_goto; \
  }
#define status_require_return(expression) \
  status = expression; \
  if (status_is_failure) { \
    status_return; \
  }
#endif
