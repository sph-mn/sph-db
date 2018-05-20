
#include <inttypes.h>
#include <stdio.h>
#define boolean b8
#define pointer_t uintptr_t
#define b0 void
#define b8 uint8_t
#define b16 uint16_t
#define b32 uint32_t
#define b64 uint64_t
#define b8_s int8_t
#define b16_s int16_t
#define b32_s int32_t
#define b64_s int64_t
#define f32_s float
#define f64_s double
/** writes values with current routine name and line info to standard output.
    example: (debug-log "%d" 1)
    otherwise like printf */
#define debug_log(format, ...)                                                 \
  fprintf(stdout, "%s:%d " format "\n", __func__, __LINE__, __VA_ARGS__)
#define null ((b0)(0))
#define zero_p(a) (0 == a)
