(pre-include "inttypes.h" "stdio.h")

(pre-define
  ; shorter fixed-length type names derived from inttypes.h
  boolean b8
  pointer-t uintptr_t
  b0 void
  b8 uint8_t
  b16 uint16_t
  b32 uint32_t
  b64 uint64_t
  b8-s int8_t
  b16-s int16_t
  b32-s int32_t
  b64-s int64_t
  f32-s float
  f64-s double
  (debug-log format ...)
  (begin
    "writes values with current routine name and line info to standard output.
    example: (debug-log \"%d\" 1)
    otherwise like printf"
    (fprintf stdout (pre-string-concat "%s:%d " format "\n") __func__ __LINE__ __VA_ARGS__))
  ; definition of null as seen in other libraries
  null (convert-type 0 b0)
  (zero? a) (= 0 a))