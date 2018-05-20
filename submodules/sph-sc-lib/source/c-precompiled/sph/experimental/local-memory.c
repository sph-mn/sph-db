
#define local_define_malloc(variable_name, type, on_error)                     \
  type *variable_name = malloc(sizeof(type));                                  \
  if (!variable_name) {                                                        \
    on_error;                                                                  \
  }
#define local_memory_init(max_address_count)                                   \
  b0 *sph_local_memory_addresses[max_address_count];                           \
  b8 sph_local_memory_index = 0
#define local_memory_add(pointer)                                              \
  (*(sph_local_memory_addresses + sph_local_memory_index)) = pointer;          \
  sph_local_memory_index = (1 + sph_local_memory_index)
#define local_memory_free                                                      \
  while (sph_local_memory_index) {                                             \
    decrement_one(sph_local_memory_index);                                     \
    free((*(sph_local_memory_addresses + sph_local_memory_index)));            \
  }
