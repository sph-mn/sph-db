(sc-comment "depends on libc and libm, -lm")
(pre-include "stdio.h" "math.h")

(define (sph-helper2-uint->string a result-len) (uint8-t* uintmax-t size-t*)
  "get a decimal string representation of an unsigned integer"
  (declare size size-t result uint8-t*)
  (set size (+ 1 (if* (= 0 a) 1 (+ 1 (log10 a)))) result (malloc size))
  (if (not result) (return 0))
  (if (< (snprintf result size "%ju" a) 0) (begin (free result) (return 0))
    (begin (set *result-len (- size 1)) (return result))))