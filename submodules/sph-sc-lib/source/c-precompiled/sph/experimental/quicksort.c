
#define define_quicksort(name, type_array, type_index, max_levels)             \
  b8 name(type_array a, type_index element_count) {                            \
    type_index i = 0;                                                          \
    type_index pivot;                                                          \
    type_index start[max_levels];                                              \
    type_index end[max_levels];                                                \
    type_index left;                                                           \
    type_index right;                                                          \
    (*(start + 0)) = 0;                                                        \
    (*(end + 0)) = element_count;                                              \
    while ((i >= 0)) {                                                         \
      left = (*(start + i));                                                   \
      right = ((*(end + i)) - 1);                                              \
      if ((left < right)) {                                                    \
        pivot = (*(a + left));                                                 \
        if ((i == (max_levels - 1))) {                                         \
          return (1);                                                          \
        };                                                                     \
        while ((left < right)) {                                               \
          while (((((*(a + right)) >= pivot)) && (left < right))) {            \
            right = (right - 1);                                               \
            if ((left < right)) {                                              \
              left = (left + 1);                                               \
              (*(a + left)) = (*(a + right));                                  \
            };                                                                 \
          };                                                                   \
          while (((((*(a + left)) <= pivot)) && (left < right))) {             \
            left = (left + 1);                                                 \
            if ((left < right)) {                                              \
              right = (right - 1);                                             \
              (*(a + right)) = (*(a + left));                                  \
            };                                                                 \
          };                                                                   \
        };                                                                     \
        (*(a + left)) = pivot;                                                 \
        (*(start + (i + 1))) = (left + 1);                                     \
        (*(end + (i + 1))) = (*(end + i));                                     \
        i = (i + 1);                                                           \
        (*(end + i)) = left;                                                   \
      } else {                                                                 \
        i = (i - 1);                                                           \
      };                                                                       \
    };                                                                         \
    return (0);                                                                \
  }
define_quicksort(quicksort, b32 *, b32, 1000);