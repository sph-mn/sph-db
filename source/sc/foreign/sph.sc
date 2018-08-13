(pre-include "stdio.h")

(pre-define (debug-log format ...)
  (begin
    "writes values with current routine name and line info to standard output.
    example: (debug-log \"%d\" 1)
    otherwise like printf"
    (fprintf stdout (pre-string-concat "%s:%d " format "\n") __func__ __LINE__ __VA-ARGS__)))