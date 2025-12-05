
#ifndef sph_array_h_included
#define sph_array_h_included

/* depends on stdlib.h (malloc/realloc/free) and string.h (memset) for the default allocators */
#include <sph-db/sph/status.h>

#define sph_array_status_id_memory 1
#define sph_array_status_group "sph"
#define sph_array_growth_factor 2
#define sph_array_memory_error status_set_goto(sph_array_status_group, sph_array_status_id_memory)
#define sph_array_default_alloc(s, es) malloc((s * es))
#define sph_array_default_realloc(d, s, u, n, es) realloc(d, (n * es))
#define sph_array_default_alloc_zero(s, es) calloc(s, es)
#define sph_array_declare_no_struct_type(name, element_type)
#define sph_array_default_declare_struct_type(name, element_type) \
  typedef struct { \
    size_t size; \
    size_t used; \
    element_type* data; \
  } name##_t
#define sph_array_declare_type_custom(name, element_type, sph_array_alloc, sph_array_realloc, sph_array_free, sph_array_declare_struct_type) \
  sph_array_declare_struct_type(name, element_type); \
  status_t name##_new(size_t size, name##_t* a) { \
    status_declare; \
    memset(a, 0, (sizeof(name##_t))); \
    element_type* data = sph_array_alloc(size, (sizeof(element_type))); \
    if (!data) { \
      sph_array_memory_error; \
    }; \
    a->data = data; \
    a->size = size; \
  exit: \
    status_return; \
  } \
  status_t name##_resize(name##_t* a, size_t new_size) { \
    status_declare; \
    element_type* data = sph_array_realloc((a->data), (a->size), (a->used), new_size, (sizeof(element_type))); \
    if (!data) { \
      sph_array_memory_error; \
    }; \
    a->data = data; \
    a->size = new_size; \
    a->used = ((new_size < a->used) ? new_size : a->used); \
  exit: \
    status_return; \
  } \
  void name##_free(name##_t* a) { sph_array_free((a->data)); } \
  status_t name##_ensure(size_t needed, name##_t* a) { \
    status_declare; \
    return ((a->data ? (((a->size - a->used) < needed) ? name##_resize(a, (needed + (sph_array_growth_factor * a->size))) : status) : name##_new(needed, a))); \
  }
#define sph_array_declare_type(name, element_type) sph_array_declare_type_custom(name, element_type, sph_array_default_alloc, sph_array_default_realloc, free, sph_array_default_declare_struct_type)
#define sph_array_declare_type_zeroed(name, element_type) sph_array_declare_type_custom(name, element_type, sph_array_default_alloc_zero, sph_array_default_realloc_zero, free, sph_array_default_declare_struct_type)
#define sph_array_declare(a, type) type a = { 0 }
#define sph_array_add(a, value) \
  (a.data)[a.used] = value; \
  a.used += 1
#define sph_array_set_null(a) \
  a.used = 0; \
  a.size = 0; \
  a.data = 0
#define sph_array_get(a, index) (a.data)[index]
#define sph_array_get_pointer(a, index) (a.data + index)
#define sph_array_clear(a) a.used = 0
#define sph_array_remove(a) a.used -= 1
#define sph_array_remove_swap(a, i) \
  if ((1 + i) < a.used) { \
    (a.data)[i] = (a.data)[a.used]; \
  }; \
  a.used -= 1
#define sph_array_unused_size(a) (a.size - a.used)
#define sph_array_full(a) (a.used == a.size)
#define sph_array_not_full(a) (a.used < a.size)
#define sph_array_take(a, data, size, used) \
  a->data = data; \
  a->size = size; \
  a->used = used
#define sph_array_last(a) (a.data)[(a.used - 1)]
#define sph_array_first_unused(a) (a.data)[a.used]
#define sph_array_declare_stack(name, array_size, type_t, value_t) \
  value_t name##_data[array_size]; \
  type_t name; \
  name.data = name##_data; \
  name.size = array_size; \
  name.used = 0
void* sph_array_default_realloc_zero(void* d, size_t s, size_t u, size_t n, size_t es) {
  void* nd = realloc(d, (n * es));
  if (!nd) {
    return (0);
  };
  if (n > s) {
    memset((((uint8_t*)(nd)) + (s * es)), 0, (es * (n - s)));
  };
  return (nd);
}
#endif
