/* array type for linked-list like usage with the main features get, next, add,
 * variable used range and easy iteration. an array struct that tracks pointers
 * to start, end, end of used range and current element. when using add, the
 * array is filled from left to right. declarations of temporary indices and for
 * loops arent necessary to iterate. example: (while (db-ids-next a) (db-ids-get
 * a)). type declaration: declare new i-array types with i-array-declare-type,
 * then use it with the generic i-array-* macros */
#include <stdlib.h>
#define i_array_declare_type(name, element_type) \
  typedef struct { \
    element_type* current; \
    element_type* used; \
    element_type* end; \
    element_type* start; \
  } name; \
  boolean i_array_allocate##name(name* a, size_t length) { \
    element_type* temp; \
    temp = malloc((length * sizeof(element_type))); \
    if (!temp) { \
      return (0); \
    }; \
    a->start = temp; \
    a->current = temp; \
    a->used = temp; \
    a->end = (length + temp); \
    return (1); \
  }
#define i_array_get(a) *(a.current)
#define i_array_next(a) ((a.current < a.used) ? (1 + a.current) : 0)
#define i_array_forward(a) a.current = i_array_next(a)
#define i_array_rewind(a) a.current = a.start
#define i_array_add(a, value) \
  ((a.current < a.end) ? (a.current = (1 + a.current), \
                           *(a.current) = value, \
                           a.used = a.current) \
                       : 0)
#define i_array_remove(a) \
  if (a.used > a.start) { \
    a.used = (a.used - 1); \
  }
#define i_array_length(a) (a.used - a.start)
#define i_array_max_length(a) (a.end - a.start)
#define i_array_declare(a, type) type a = { 0, 0, 0, 0 }
#define i_array_free(a) free((a.start))
