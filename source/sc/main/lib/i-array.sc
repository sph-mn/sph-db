(sc-comment
  "array type for linked-list like usage with the main features get, next, add, variable used range and easy iteration."
  "an array struct that tracks pointers to start, end, end of used range and current element."
  "when using add, the array is filled from left to right."
  "declarations of temporary indices and for loops arent necessary to iterate."
  "example: (while (i-array-in-range a) (i-array-get a))."
  "type declaration: declare new i-array types with i-array-declare-type, then use it with the generic i-array-* macros")

(pre-include "stdlib.h")

(pre-define
  (i-array-declare-type name element-type)
  (begin
    (declare name
      (type
        (struct
          (current element-type*)
          (unused element-type*)
          (end element-type*)
          (start element-type*))))
    (define ((pre-concat i-array-allocate- name) a length) (boolean name* size-t)
      (declare temp element-type*)
      (set temp (malloc (* length (sizeof element-type))))
      (if (not temp) (return 0))
      (set
        a:start temp
        a:current temp
        a:unused temp
        a:end (+ length temp))
      (return 1)))
  (i-array-declare a type)
  (begin
    "define so that in-range is false, length is zero and free doesnt fail"
    (define a type (struct-literal 0 0 0 0)))
  (i-array-add a value)
  (set
    *a.unused value
    a.unused (+ 1 a.unused))
  (i-array-set-null a)
  (begin
    "set so that in-range is false and length is zero"
    (set
      a.start 0
      a.unused 0))
  (i-array-in-range a) (< a.current a.unused)
  (i-array-get-at a index) (array-get a.start index)
  (i-array-get a) *a.current
  (i-array-forward a) (set a.current (+ 1 a.current))
  (i-array-rewind a) (set a.current a.start)
  (i-array-clear a) (set a.unused a.start)
  (i-array-remove a) (set a.unused (- a.unused 1))
  (i-array-length a) (- a.unused a.start)
  (i-array-max-length a) (- a.end a.start)
  (i-array-free a) (free a.start))