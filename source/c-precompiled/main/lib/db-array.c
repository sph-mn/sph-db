/* array type for linked-list like usage with the main features get, next, add,
 * variable used range and easy iteration. an array struct that tracks pointers
 * to start, end, end of used range and current element. when using add, the
 * array is filled from left to right. declarations of temporary indices and for
 * loops arent necessary to iterate. example: (while (db-ids-next a) (db-ids-get
 * a)). type declaration: declare new i-array types with i-array-declare-type,
 * then use it with the generic i-array-* macros */
#define i_array_init(a) \
  a.used = start; \
  a.current = start
#define i_array_declare_type(name, element_type) \
  struct name { \
    element_type* current; \
    element_type* used; \
    element_type* end; \
    element_type* start; \
  }
#define i_array_get(a) *(a.current)
#define i_array_next(a) ((a.current < used) ? (1 + a.current) : 0)
#define i_array_forward(a) a.current = i_array_next(a)
#define i_array_rewind(a) a.current = a.start
#define i_array_add(a, value) \
  ((a.current < a.end) ? (a.current = (1 + a.current); *(a.current) = value; \
                           a.used = a.current;) \
                       : 0)
#define i_array_remove(a) \
  if (a.used > a.start) { \
    a.used = (a.used - 1); \
  }
#define i_array_length(a) (a.used - a.start)
#define i_array_max_length(a) (a.end - a.start)
