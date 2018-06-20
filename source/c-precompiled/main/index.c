#define db_index_errors_data_log(message, type, id) \
  db_error_log("(groups index %s) (description %s) (id %lu)", type, message, id)
