
/* depends on libc and libm, -lm */
#include <stdio.h>
#include <math.h>

/** get a decimal string representation of an unsigned integer */
uint8_t* sph_helper2_uint_to_string(uintmax_t a, size_t* result_len) {
  size_t size;
  uint8_t* result;
  size = (1 + ((0 == a) ? 1 : (1 + log10(a))));
  result = malloc(size);
  if (!result) {
    return (0);
  };
  if (snprintf(result, size, "%ju", a) < 0) {
    free(result);
    return (0);
  } else {
    *result_len = (size - 1);
    return (result);
  };
}
