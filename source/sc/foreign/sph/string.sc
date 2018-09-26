(pre-include "string.h" "stdlib.h")

(define (ensure-trailing-slash a result) (uint8-t uint8-t* uint8-t**)
  "set result to a new string with a trailing slash added, or the given string if it already has a trailing slash.
  returns 0 if result is the given string, 1 if new memory could not be allocated, 2 if result is a new string"
  (define a-len uint32-t (strlen a))
  (if (or (not a-len) (= #\/ (pointer-get (+ a (- a-len 1)))))
    (begin
      (set *result a)
      (return 0))
    (begin
      (define new-a char* (malloc (+ 2 a-len)))
      (if (not new-a) (return 1))
      (memcpy new-a a a-len)
      (memcpy (+ new-a a-len) "/" 1)
      (set
        *new-a 0
        *result new-a)
      (return 2))))

(define (string-append a b) (uint8-t* uint8-t* uint8-t*)
  "always returns a new string"
  (define a-length size-t (strlen a))
  (define b-length size-t (strlen b))
  (define result uint8-t* (malloc (+ 1 a-length b-length)))
  (if result
    (begin
      (memcpy result a a-length)
      (memcpy (+ result a-length) b (+ 1 b-length))))
  (return result))

(define (string-clone a) (uint8-t* uint8-t*)
  "return a new string with the same contents as the given string. return 0 if the memory allocation failed"
  (define a-size size-t (+ 1 (strlen a)))
  (define result uint8-t* (malloc a-size))
  (if result (memcpy result a a-size))
  (return result))

(define (string-join strings strings-len delimiter result-len)
  (uint8-t* uint8-t** size-t uint8-t* size-t*)
  "join strings into one string with each input string separated by delimiter.
  zero if strings-len is zero or memory could not be allocated"
  (declare
    result uint8-t*
    result-temp uint8-t*
    size size-t
    size-temp size-t
    i size-t
    delimiter-len size-t)
  (if (not strings-len) (return 0))
  (sc-comment "size: string-null + delimiters + string-lengths")
  (set
    delimiter-len (strlen delimiter)
    size (+ 1 (* delimiter-len (- strings-len 1))))
  (for ((set i 0) (< i strings-len) (set i (+ 1 i)))
    (set size (+ size (strlen (array-get strings i)))))
  (set result (malloc size))
  (if (not result) (return 0))
  (set
    result-temp result
    size-temp (strlen (array-get strings 0)))
  (memcpy result-temp (array-get strings 0) size-temp)
  (set result-temp (+ size-temp result-temp))
  (for ((set i 1) (< i strings-len) (set i (+ 1 i)))
    (memcpy result-temp delimiter delimiter-len)
    (set
      result-temp (+ delimiter-len result-temp)
      size-temp (strlen (array-get strings i)))
    (memcpy result-temp (array-get strings i) size-temp)
    (set result-temp (+ size-temp result-temp)))
  (set
    (array-get result (- size 1)) 0
    *result-len (- size 1))
  (return result))