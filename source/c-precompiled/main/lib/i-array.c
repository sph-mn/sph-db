/* array type for linked-list like usage with the main features get, next, add,
 * variable used range and easy iteration. an array struct that tracks pointers
 * to start, end, end of used range and current element. when using add, the
 * array is filled from left to right. declarations of temporary indices and for
 * loops arent necessary to iterate. example: (while (i-array-in-range a)
 * (i-array-get a)). type declaration: declare new i-array types with
 * i-array-declare-type, then use it with the generic i-array-* macros */
#include <stdlib.h>
#define i_array_declare_type(name, element_type) \
  typedef struct { \
    element_type* current; \
    element_type* unused; \
    element_type* end; \
    element_type* start; \
  } name; \
  boolean i_array_allocate_##name(name* a, size_t length) { \
    element_type* temp; \
    temp = malloc((length * sizeof(element_type))); \
    if (!temp) { \
      return (0); \
    }; \
    a->start = temp; \
    a->current = temp; \
    a->unused = temp; \
    a->end = (length + temp); \
    return (1); \
  }
#define i_array_declare(a, type) type a = { 0, 0, 0, 0 }
#define i_array_add(a, value) \
  *(a.unused) = value; \
  a.unused = (1 + a.unused)
/** set so that in-range is false and length is zero */
#define i_array_set_null(a) \
  a.start = 0; \
  a.unused = 0
#define i_array_in_range(a) (a.current < a.unused)
#define i_array_get_at(a, index) (a.start)[index]
#define i_array_get(a) *(a.current)
#define i_array_forward(a) a.current = (1 + a.current)
#define i_array_rewind(a) a.current = a.start
#define i_array_clear(a) a.unused = a.start
#define i_array_remove(a) a.unused = (a.unused - 1)
#define i_array_length(a) (a.unused - a.start)
#define i_array_max_length(a) (a.end - a.start)
#define i_array_free(a) free((a.start))
