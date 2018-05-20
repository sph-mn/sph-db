
#include <assert.h>
#include <inttypes.h>
#include <stdio.h>
#include <time.h>
/* a minimal linked list with custom element types.
   this file can be included multiple times to create differently typed
   versions, depending the value of the preprocessor variables
   mi-list-name-infix and mi-list-element-t before inclusion */
#include <inttypes.h>
#include <stdlib.h>
#ifndef mi_list_name_prefix
#define mi_list_name_prefix mi_list_64
#endif
#ifndef mi_list_element_t
#define mi_list_element_t uint64_t
#endif
/* there does not seem to be a simpler way for identifier concatenation in c in
 * this case */
#ifndef mi_list_name_concat
#define mi_list_name_concat(a, b) a##_##b
#define mi_list_name_concatenator(a, b) mi_list_name_concat(a, b)
#define mi_list_name(name) mi_list_name_concatenator(mi_list_name_prefix, name)
#endif
#define mi_list_struct_name mi_list_name(struct)
#define mi_list_t mi_list_name(t)
typedef struct mi_list_struct_name {
  struct mi_list_struct_name *link;
  mi_list_element_t data;
} mi_list_t;
#ifndef mi_list_first
#define mi_list_first(a) (*a).data
#define mi_list_first_address(a) &(*a).data
#define mi_list_rest(a) (*a).link
#endif
mi_list_t *mi_list_name(drop)(mi_list_t *a) {
  mi_list_t *a_next = mi_list_rest(a);
  free(a);
  return (a_next);
};
/** it would be nice to set the pointer to zero, but that would require more
 * indirection with a pointer-pointer */
void mi_list_name(destroy)(mi_list_t *a) {
  mi_list_t *a_next = 0;
  while (a) {
    a_next = (*a).link;
    free(a);
    a = a_next;
  };
};
mi_list_t *mi_list_name(add)(mi_list_t *a, mi_list_element_t value) {
  mi_list_t *element = calloc(1, sizeof(mi_list_t));
  if (!element) {
    return (0);
  };
  (*element).data = value;
  (*element).link = a;
  return (element);
};
size_t mi_list_name(length)(mi_list_t *a) {
  size_t result = 0;
  while (a) {
    result = (1 + result);
    a = mi_list_rest(a);
  };
  return (result);
};
#undef mi_list_name_prefix
#undef mi_list_element_t
#undef mi_list_struct_name
#undef mi_list_t
;
#define test_element_count 100
mi_list_64_t *insert_values(mi_list_64_t *a) {
  size_t counter = test_element_count;
  while (counter) {
    a = mi_list_64_add(a, counter);
    counter = (counter - 1);
  };
  mi_list_64_add(a, counter);
};
uint8_t test_value_existence(mi_list_64_t *a) {
  size_t counter = 0;
  while ((counter <= test_element_count)) {
    assert((counter == mi_list_first(a)));
    a = mi_list_rest(a);
    counter = (counter - 1);
  };
};
void print_contents(mi_list_64_t *a) {
  printf("print-contents\n");
  while (a) {
    printf("%lu\n", mi_list_first(a));
    a = mi_list_rest(a);
  };
};
#define get_time() ((uint64_t)(time(0)))
#define print_time(a) printf("%u\n", a)
int main() {
  mi_list_64_t *a = 0;
  a = insert_values(a);
  test_value_existence(a);
  print_contents(a);
  mi_list_64_destroy(a);
  printf("success\n");
  return (0);
};