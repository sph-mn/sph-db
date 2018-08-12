#include <inttypes.h>
#include <stdio.h>
#define boolean ui8
#define i8 int8_t
#define i16 int16_t
#define i32 int32_t
#define i64 int64_t
#define i8_least int_least8_t
#define i16_least int_least16_t
#define i32_least int_least32_t
#define i64_least int_least64_t
#define i8_fast int_fast8_t
#define i16_fast int_fast16_t
#define i32_fast int_fast32_t
#define i64_fast int_fast64_t
#define ui8 int8_t
#define ui16 uint16_t
#define ui32 uint32_t
#define ui64 uint64_t
#define ui8_least uint_least8_t
#define ui16_least uint_least16_t
#define ui32_least uint_least32_t
#define ui64_least uint_least64_t
#define ui8_fast uint_fast8_t
#define ui16_fast uint_fast16_t
#define ui32_fast uint_fast32_t
#define ui64_fast uint_fast64_t
#define f32 float
#define f64 double
/** writes values with current routine name and line info to standard output.
    example: (debug-log "%d" 1)
    otherwise like printf */
#define debug_log(format, ...) fprintf(stdout, "%s:%d " format "\n", __func__, __LINE__, __VA_ARGS__)
