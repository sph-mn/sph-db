
#define scm_c_error_create(id, group, data)                                    \
  scm_call_3(scm_error_create, scm_from_uint(id),                              \
             (group ? scm_from_uint(group) : SCM_BOOL_F),                      \
             (data ? data : SCM_EOL))
SCM scm_error_create;
SCM scm_error_p;
SCM scm_error_group;
SCM scm_error_id;
SCM scm_error_data;
b0 scm_error_init() {
  SCM m = scm_c_resolve_module("sph error");
  scm_error_create = scm_variable_ref(scm_c_module_lookup(m, "error-create-p"));
  scm_error_group = scm_variable_ref(scm_c_module_lookup(m, "error-group"));
  scm_error_id = scm_variable_ref(scm_c_module_lookup(m, "error-id"));
  scm_error_data = scm_variable_ref(scm_c_module_lookup(m, "error-data"));
  scm_error_p = scm_variable_ref(scm_c_module_lookup(m, "error?"));
};
#define scm_c_local_error_init                                                 \
  SCM local_error_origin;                                                      \
  SCM local_error_name;                                                        \
  SCM local_error_data;
#define scm_c_local_error(i, d)                                                \
  local_error_origin = scm_from_locale_symbol(__func__);                       \
  local_error_name = scm_from_locale_symbol(i);                                \
  local_error_data = d;                                                        \
  goto error
#define scm_c_local_error_create                                               \
  scm_call_3(scm_error_create, local_error_origin, local_error_name,           \
             (local_error_data ? local_error_data : SCM_BOOL_F))
#define scm_error_return_1(a)                                                  \
  if (scm_is_true(scm_call_1(scm_error_p, a))) {                               \
    return (a);                                                                \
  }
#define scm_error_return_2(a, b)                                               \
  scm_error_return_1(a);                                                       \
  scm_error_return_1(b)
#define scm_error_return_3(a, b, c)                                            \
  scm_error_return_2(a, b);                                                    \
  scm_error_return_1(c)
#define scm_error_return_4(a, b, c, d)                                         \
  scm_error_return_2(a, b);                                                    \
  scm_error_return_2(c, d)
