(sc-comment
  "array type for linked-list like usage with the main features get, next, add, variable used range and easy iteration."
  "an array struct that tracks pointers to start, end, end of used range and current element."
  "when using add, the array is filled from left to right."
  "declarations of temporary indices and for loops arent necessary to iterate."
  "example: (while (db-ids-next a) (db-ids-get a))."
  "type declaration: declare new i-array types with i-array-declare-type, then use it with the generic i-array-* macros")

(pre-include "stdlib.h")

(pre-define
  (i-array-declare-type name element-type)
  (begin
    (declare name
      (type
        (struct
          (current element-type*)
          (used element-type*)
          (end element-type*)
          (start element-type*))))
    (define ((pre-concat i-array-allocate name) a length) (boolean name* size-t)
      (declare temp element-type*)
      (set temp (malloc (* length (sizeof element-type))))
      (if (not temp) (return 0))
      (set
        a:start temp
        a:current temp
        a:used temp
        a:end (+ length temp))
      (return 1)))
  (i-array-get a) *a.current
  (i-array-next a)
  (if* (< a.current a.used) (+ 1 a.current)
    0)
  (i-array-forward a) (set a.current (i-array-next a))
  (i-array-rewind a) (set a.current a.start)
  (i-array-add a value)
  (if* (< a.current a.end)
    (set
      a.current (+ 1 a.current)
      *a.current value
      a.used a.current)
    0)
  (i-array-remove a) (if (> a.used a.start) (set a.used (- a.used 1)))
  (i-array-length a) (- a.used a.start)
  (i-array-max-length a) (- a.end a.start)
  (i-array-declare a type) (define a type (struct-literal 0 0 0 0))
  (i-array-free a) (free a.start))