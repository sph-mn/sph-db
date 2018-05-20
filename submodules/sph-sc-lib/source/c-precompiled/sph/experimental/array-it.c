
#define array_it_define_type(name, size_t, data_t)                             \
  define_type(name, struct {                                                   \
    size_t size;                                                               \
    size_t index;                                                              \
    data_t data;                                                               \
  })
#define array_it_next(a) a.index = (1 + struct_ref(a, index))
#define array_it_next_p(a) ((1 + struct_ref(a, index)) < struct_ref(a, size))
#define array_it_prev(a) a.index = (struct_ref(a, index) - 1)
#define array_it_prev_p(a) (0 <= (struct_ref(a, index) - 1))
#define array_it_reset(a) a.index = 0
#define array_it_data(a) struct_ref(a, data)
#define array_it_get_address(a) (struct_ref(a, data) + struct_ref(a, index))
#define array_it_get(a) (*array_it_get_address(a))
