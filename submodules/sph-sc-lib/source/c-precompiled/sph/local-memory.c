
/** register memory in a local variable to free all memory allocated at point */
#define local_memory_init(register_size)                                       \
  define_array(sph_local_memory_register, b0 *, register_size());              \
  b8 sph_local_memory_index = 0

/** do not try to add more entries than specified by register-size or a buffer
 * overflow occurs */
#define local_memory_add(address)                                              \
  (*(sph_local_memory_register + sph_local_memory_index)) = address;           \
  sph_local_memory_index = (1 + sph_local_memory_index)
#define local_memory_free                                                      \
  while (sph_local_memory_index) {                                             \
    sph_local_memory_index = (sph_local_memory_index - 1);                     \
    free((*(sph_local_memory_register + sph_local_memory_index)));             \
  };