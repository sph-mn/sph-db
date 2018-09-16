(sc-comment
  "\"iteration array\" - an array with variable length content that makes iteration easier to code.
  most bindings are generic macros that will work on all i-array types. i-array-add and i-array-forward go from left to right.
  examples:
    i_array_declare_type(my_type, int);
    my_type a;
    if(i_array_allocate_my_type(4, &a)) {
      // memory allocation error
    }
    i_array_add(a, 1);
    i_array_add(a, 2);
    while(i_array_in_range(a)) {
      i_array_get(a);
      i_array_forward(a);
    }
    i_array_free(a);")

(pre-include "stdlib.h")

(pre-define
  (i-array-declare-type name element-type)
  (begin
    ".current: to avoid having to write for-loops. this would correspond to the index variable in loops
     .unused: to have variable length content in a fixed length array. points outside the memory area after the last element has been added
     .end: start + max-length. (last-index + 1) of the allocated array
     .start: the beginning of the allocated array and used for rewind and free"
    (declare name
      (type
        (struct
          (current element-type*)
          (unused element-type*)
          (end element-type*)
          (start element-type*))))
    (define ((pre-concat i-array-allocate-custom_ name) length alloc a)
      (uint8-t size-t (function-pointer void* size-t) name*)
      (declare start element-type*)
      (set start (alloc (* length (sizeof element-type))))
      (if (not start) (return 1))
      (set
        a:start start
        a:current start
        a:unused start
        a:end (+ length start))
      (return 0))
    (define ((pre-concat i-array-allocate_ name) length a) (uint8-t size-t name*)
      (return ((pre-concat i-array-allocate-custom_ name) length malloc a))))
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
    "set so that in-range is false, length is zero and free doesnt fail"
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