/* "iteration array" - an array with variable length content that makes iteration easier to code.
  saves the size argument that usually has to be passed with arrays and saves the declaration of index counter variables.
  the data structure consists of only 4 pointers in a struct.
  most bindings are generic macros that will work on any i-array type. i-array-add and i-array-forward go from left to right.
  examples:
    i_array_declare_type(my_type, int);
    my_type a;
    if(i_array_allocate_my_type(4, &a)) {
      // memory allocation error
    }
    i_array_add(a, 1);
    i_array_add(a, 2);
    while(i_array_in_range(a)) {
      i_array_get(a);
      i_array_forward(a);
    }
    i_array_free(a); */
#include <stdlib.h>
/** .current: to avoid having to write for-loops. this would correspond to the index variable in loops
     .unused: to have variable length content in a fixed length array. points outside the memory area after the last element has been added
     .end: start + max-length. (last-index + 1) of the allocated array
     .start: the beginning of the allocated array and used for rewind and free */
#define i_array_declare_type(name, element_type) \
  typedef struct { \
    element_type* current; \
    element_type* unused; \
    element_type* end; \
    element_type* start; \
  } name; \
  uint8_t i_array_allocate_custom_##name(size_t length, void* (*alloc)(size_t), name* a) { \
    element_type* start; \
    start = alloc((length * sizeof(element_type))); \
    if (!start) { \
      return (1); \
    }; \
    a->start = start; \
    a->current = start; \
    a->unused = start; \
    a->end = (length + start); \
    return (0); \
  } \
  uint8_t i_array_allocate_##name(size_t length, name* a) { return ((i_array_allocate_custom_##name(length, malloc, a))); }
/** define so that in-range is false, length is zero and free doesnt fail.
     can be used to create empty/null i-arrays */
#define i_array_declare(a, type) type a = { 0, 0, 0, 0 }
#define i_array_add(a, value) \
  *(a.unused) = value; \
  a.unused = (1 + a.unused)
/** set so that in-range is false, length is zero and free doesnt fail */
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
/** create an i-array from a standard array.
     sets source as data array to use, with the first count number of slots used.
     source will not be copied but used as is, and i-array-free would free it.
     # example with a stack allocated array
     int other_array[4] = {1, 2, 0, 0};
     my_type a;
     i_array_take(a, other_array, 4 2); */
#define i_array_take(a, source, size, count) \
  a->start = source; \
  a->current = source; \
  a->unused = (count + source); \
  a->end = (size + source)
