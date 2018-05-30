/* return status and error handling */
/* return status code and error handling. uses a local variable named "status"
   and a goto label named "exit". a status has an identifier and a group to
   discern between status identifiers of different libraries. status id 0 is
   success, everything else can be considered a failure or special case. status
   ids are 32 bit signed integers for compatibility with error return codes from
   many other existing libraries. bindings with a ! suffix update the status
   from an expression */
/** like status init but sets a default group */
#define status_init_group(group) status_t status = { status_id_success, group }
#define status_id_success 0
#define status_group_undefined 0
#define status_init \
  status_t status = { status_id_success, status_group_undefined }
#define status_reset status_set_both(status_group_undefined, status_id_success)
#define status_success_p (status_id_success == status.id)
#define status_failure_p !status_success_p
#define status_goto goto exit
#define status_require \
  if (status_failure_p) { \
    status_goto; \
  }
#define status_set_group(group_id) status.group = group_id
#define status_set_id(status_id) status.id = status_id
#define status_set_both(group_id, status_id) \
  status_set_group(group_id); \
  status_set_id(status_id)
/** update status with the result of expression, check for failure and goto
 * error if so */
#define status_require_x(expression) \
  status = expression; \
  if (status_failure_p) { \
    status_goto; \
  }
/** set the status id and goto error */
#define status_set_id_goto(status_id) \
  status_set_id(status_id); \
  status_goto
#define status_set_group_goto(group_id) \
  status_set_group(group_id); \
  status_goto
#define status_set_both_goto(group_id, status_id) \
  status_set_both(group_id, status_id); \
  status_goto
#define status_id_is_p(status_id) (status_id == status.id)
/** update status with the result of expression, check for failure and goto
 * error if so */
#define status_i_require_x(expression) \
  status.id = expression; \
  if (status_failure_p) { \
    status_goto; \
  }
;
typedef b32_s status_i_t;
typedef struct {
  status_i_t id;
  b8 group;
} status_t;
enum {
  db_status_id_condition_unfulfilled,
  db_status_id_data_length,
  db_status_id_different_format,
  db_status_id_duplicate,
  db_status_id_input_type,
  db_status_id_max_element_id,
  db_status_id_max_type_id,
  db_status_id_max_type_id_size,
  db_status_id_memory,
  db_status_id_missing_argument_db_root,
  db_status_id_no_more_data,
  db_status_id_not_implemented,
  db_status_id_path_not_accessible_db_root,
  db_status_id_undefined,
  db_status_group_db,
  db_status_group_lmdb,
  db_status_group_libc
};
#define db_status_set_id_goto(status_id) \
  status_set_both_goto(db_status_group_db, status_id)
#define db_status_require_read_x(expression) \
  status = expression; \
  if (!(status_success_p || status_id_is_p(db_status_id_no_more_data))) { \
    status_goto; \
  }
#define db_status_no_more_data_if_mdb_notfound \
  if (db_mdb_status_notfound_p) { \
    status_set_both(db_status_group_db, db_status_id_no_more_data); \
  }
#define db_status_success_if_mdb_notfound \
  if (db_mdb_status_notfound_p) { \
    status_set_id(status_id_success); \
  }
#define db_status_success_if_no_more_data \
  if (status_id_is_p(db_status_id_no_more_data)) { \
    status.id = status_id_success; \
  }
#define db_mdb_status_success_p status_id_is_p(MDB_SUCCESS)
#define db_mdb_status_failure_p !db_mdb_status_success_p
#define db_mdb_status_notfound_p status_id_is_p(MDB_NOTFOUND)
#define db_mdb_status_set_id_goto(id) \
  status_set_both_goto(db_status_group_lmdb, id)
#define db_mdb_status_require_x(expression) \
  status_set_id(expression); \
  if (db_mdb_status_failure_p) { \
    status_set_group_goto(db_status_group_lmdb); \
  }
#define db_mdb_status_require \
  if (db_mdb_status_failure_p) { \
    status_set_group_goto(db_status_group_lmdb); \
  }
#define db_mdb_status_require_read \
  if (!(db_mdb_status_success_p || db_mdb_status_notfound_p)) { \
    status_set_group_goto(db_status_group_lmdb); \
  }
#define db_mdb_status_require_read_x(expression) \
  status_set_id(expression); \
  db_mdb_status_require_read
#define db_mdb_status_require_notfound \
  if (!db_mdb_status_notfound_p) { \
    status_set_group_goto(db_status_group_lmdb); \
  }
b8* db_status_group_id_to_name(status_i_t a) {
  char* b;
  if (db_status_group_db == a) {
    b = "sph-db";
  } else if (db_status_group_lmdb == a) {
    b = "lmdb";
  } else if (db_status_group_libc == a) {
    b = "libc";
  } else {
    b = "";
  };
  return (b);
};
/** get the description if available for a status */
b8* db_status_description(status_t a) {
  char* b;
  if (db_status_group_db == a.group) {
    if (db_status_id_input_type == a.id) {
      b = "input argument is of wrong type";
    } else if (db_status_id_data_length == a.id) {
      b = "data too large";
    } else if (db_status_id_duplicate == a.id) {
      b = "element already exists";
    } else if (db_status_id_not_implemented == a.id) {
      b = "not implemented";
    } else if (db_status_id_missing_argument_db_root == a.id) {
      b = "missing argument 'db-root'";
    } else if (db_status_id_path_not_accessible_db_root == a.id) {
      b = "root not accessible";
    } else if (db_status_id_memory == a.id) {
      b = "not enough memory or other memory allocation error";
    } else if (db_status_id_max_element_id == a.id) {
      b = "maximum element identifier value has been reached for the type";
    } else if (db_status_id_max_type_id == a.id) {
      b = "maximum type identifier value has been reached";
    } else if (db_status_id_max_type_id_size == a.id) {
      b =
        "type identifier size is either configured to be greater than 16 bit, "
        "which is currently not supported, or is not smaller than node id size";
    } else if (db_status_id_condition_unfulfilled == a.id) {
      b = "condition unfulfilled";
    } else if (db_status_id_no_more_data == a.id) {
      b = "no more data to read";
    } else if (db_status_id_different_format == a.id) {
      b = "configured format differs from the format the database was created "
          "with";
    } else {
      b = "";
    };
  } else if (db_status_group_lmdb == a.group) {
    b = mdb_strerror((a.id));
  } else {
    b = "";
  };
  return (((b8*)(b)));
};
/** get the name if available for a status */
b8* db_status_name(status_t a) {
  char* b;
  if (db_status_group_db == a.group) {
    if (db_status_id_input_type == a.id) {
      b = "input-type";
    } else if (db_status_id_data_length == a.id) {
      b = "data-length";
    } else if (db_status_id_duplicate == a.id) {
      b = "duplicate";
    } else if (db_status_id_not_implemented == a.id) {
      b = "not-implemented";
    } else if (db_status_id_missing_argument_db_root == a.id) {
      b = "missing-argument-db-root";
    } else if (db_status_id_path_not_accessible_db_root == a.id) {
      b = "path-not-accessible-db-root";
    } else if (db_status_id_memory == a.id) {
      b = "memory";
    } else if (db_status_id_max_element_id == a.id) {
      b = "max-element-id-reached";
    } else if (db_status_id_max_type_id == a.id) {
      b = "max-type-id-reached";
    } else if (db_status_id_max_type_id_size == a.id) {
      b = "type-id-size-too-big";
    } else if (db_status_id_condition_unfulfilled == a.id) {
      b = "condition-unfulfilled";
    } else if (db_status_id_no_more_data == a.id) {
      b = "no-more-data";
    } else if (db_status_id_different_format == a.id) {
      b = "different-format";
    } else {
      b = "unknown";
    };
  } else if (db_status_group_lmdb == a.group) {
    b = mdb_strerror((a.id));
  } else {
    b = "unknown";
  };
  return (((b8*)(b)));
};