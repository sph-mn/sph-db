(sc-comment "depends on sph/status.c")
(pre-include "stdlib.h" "inttypes.h" "stdio.h")

(pre-define
  sph-helper-status-group "sph"
  (sph-helper-malloc size result)
  (begin
    "add explicit type cast to prevent compiler warning"
    (sph-helper-primitive-malloc size (convert-type result void**)))
  (sph-helper-malloc-string size result)
  (sph-helper-primitive-malloc-string size (convert-type result uint8-t**))
  (sph-helper-calloc size result) (sph-helper-primitive-calloc size (convert-type result void**))
  (sph-helper-realloc size result) (sph-helper-primitive-realloc size (convert-type result void**)))

(enum (sph-helper-status-id-memory))

(define (sph-helper-status-description a) (uint8-t* status-t)
  (declare b char*)
  (case = a.id
    (sph-helper-status-id-memory (set b "not enough memory or other memory allocation error"))
    (else (set b ""))))

(define (sph-helper-status-name a) (uint8-t* status-t)
  (declare b char*)
  (case = a.id
    (sph-helper-status-id-memory (set b "memory"))
    (else (set b "unknown"))))

(define (sph-helper-primitive-malloc size result) (status-t size-t void**)
  status-declare
  (declare a void*)
  (set a (malloc size))
  (if a (set *result a)
    (set
      status.group sph-helper-status-group
      status.id sph-helper-status-id-memory))
  (return status))

(define (sph-helper-primitive-malloc-string length result) (status-t size-t uint8-t**)
  "like sph-helper-malloc but allocates one extra byte that is set to zero"
  status-declare
  (declare a uint8-t*)
  (status-require (sph-helper-malloc (+ 1 length) &a))
  (set
    (array-get a length) 0
    *result a)
  (label exit
    (return status)))

(define (sph-helper-primitive-calloc size result) (status-t size-t void**)
  status-declare
  (declare a void*)
  (set a (calloc size 1))
  (if a (set *result a)
    (set
      status.group sph-helper-status-group
      status.id sph-helper-status-id-memory))
  (return status))

(define (sph-helper-primitive-realloc size block) (status-t size-t void**)
  status-declare
  (declare a void*)
  (set a (realloc *block size))
  (if a (set *block a)
    (set
      status.group sph-helper-status-group
      status.id sph-helper-status-id-memory))
  (return status))

(define (sph-helper-uint->string a result-len) (uint8-t* uintmax-t size-t*)
  "get a decimal string representation of an unsigned integer"
  (declare
    size size-t
    result uint8-t*)
  (set
    size
    (+ 1
      (if* (= 0 a) 1
        (+ 1 (log10 a))))
    result (malloc size))
  (if (not result) (return 0))
  (if (< (snprintf result size "%ju" a) 0)
    (begin
      (free result)
      (return 0))
    (begin
      (set *result-len (- size 1))
      (return result))))

(define (sph-helper-display-bits-u8 a) (void uint8-t)
  "display the bits of an octet"
  (declare i uint8-t)
  (printf "%u" (bit-and 1 a))
  (for ((set i 1) (< i 8) (set i (+ 1 i)))
    (printf "%u"
      (if* (bit-and (bit-shift-left (convert-type 1 uint8-t) i) a) 1
        0))))

(define (sph-helper-display-bits a size) (void void* size-t)
  "display the bits of the specified memory region"
  (declare i size-t)
  (for ((set i 0) (< i size) (set i (+ 1 i)))
    (sph-helper-display-bits-u8 (array-get (convert-type a uint8-t*) i)))
  (printf "\n"))