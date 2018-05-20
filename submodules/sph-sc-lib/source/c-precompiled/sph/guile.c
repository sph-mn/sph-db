
#define scm_first SCM_CAR
#define scm_tail SCM_CDR
#define scm_c_define_procedure_c_init SCM scm_c_define_procedure_c_temp
#define scm_is_undefined(a) (SCM_UNDEFINED == a)
/** defines and registers a c routine as a scheme procedure with documentation.
    like scm-c-define-gsubr but also sets documentation.
    scm-c-define-procedure-c-init must have been called in scope */
#define scm_c_define_procedure_c(name, required, optional, rest, c_function,   \
                                 documentation)                                \
  scm_c_define_procedure_c_temp =                                              \
      scm_c_define_gsubr(name, required, optional, rest, c_function);          \
  scm_set_procedure_property_x(scm_c_define_procedure_c_temp,                  \
                               scm_from_locale_symbol("documentation"),        \
                               scm_from_locale_string(documentation))

/** SCM SCM c-compound-expression ->
    iterate over scm-list in c */
#define scm_c_list_each(list, e, body)                                         \
  while (!scm_is_null(list)) {                                                 \
    e = scm_first(list);                                                       \
    body;                                                                      \
    list = scm_tail(list);                                                     \
  }
b0 scm_debug_log(SCM value) {
  scm_call_2(scm_variable_ref(scm_c_lookup("write")), value,
             scm_current_output_port());
  scm_newline(scm_current_output_port());
};
SCM scm_c_bytevector_take(size_t size_octets, b8 *a) {
  SCM r = scm_c_make_bytevector(size_octets);
  memcpy(SCM_BYTEVECTOR_CONTENTS(r), a, size_octets);
  return (r);
};