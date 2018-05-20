#define db_type_flag_virtual 1
#define db_type_name_max_len 255
/** extend the types array if type-id is an index out of bounds */
status_t db_env_types_extend(db_env_t* env, db_type_id_t type_id) {
  status_init;
  db_type_t* types_temp;
  db_type_id_t types_len;
  db_type_t* types;
  db_type_id_t index;
  types_len = (*env).types_len;
  if ((types_len < type_id)) {
    types = (*env).types;
    index = types_len;
    types_len = (20 + type_id);
    db_realloc(types, types_temp, types_len);
    while ((index < types_len)) {
      (*(index + types)).id = 0;
    };
    (*env).types = types;
    (*env).types_len = types_len;
  };
exit:
  return (status);
};
/** return a pointer to the type struct for the type with the given name. zero
 * if not found */
db_type_t* db_type_get(db_env_t* env, b8* name) {
  db_type_id_t index;
  db_type_id_t types_len;
  db_type_t* type;
  index = 0;
  types_len = (*env).types_len;
  while ((index < types_len)) {
    type = (index + (*env).types);
    if (!strcmp(name, (*type).name)) {
      return (type);
    };
    index = (1 + index);
  };
  return (0);
};