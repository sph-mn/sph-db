(sc-comment
  "a minimal linked list with custom element types.
   this file can be included multiple times to create differently typed versions,
   depending the value of the preprocessor variables mi-list-name-infix and mi-list-element-t before inclusion")

(pre-include "stdlib.h" "inttypes.h")
(pre-if-not-defined mi-list-name-prefix (pre-define mi-list-name-prefix mi-list-64))
(pre-if-not-defined mi-list-element-t (pre-define mi-list-element-t uint64_t))

(sc-comment
  "there does not seem to be a simpler way for identifier concatenation in c in this case")

(pre-if-not-defined
  mi-list-name-concat
  (begin
    (pre-define (mi-list-name-concat a b) (pre-concat a _ b))
    (pre-define (mi-list-name-concatenator a b) (mi-list-name-concat a b))
    (pre-define (mi-list-name name) (mi-list-name-concatenator mi-list-name-prefix name))))

(pre-define
  mi-list-struct-name (mi-list-name struct)
  mi-list-t (mi-list-name t))

(declare
  mi-list-t
  (type
    (struct
      mi-list-struct-name
      (link
        (struct
          mi-list-struct-name*))
      (data mi-list-element-t))))

(pre-if-not-defined
  mi-list-first
  (begin
    (pre-define (mi-list-first a) (struct-pointer-get a data))
    (pre-define (mi-list-first-address a) (address-of (struct-pointer-get a data)))
    (pre-define (mi-list-rest a) (struct-pointer-get a link))))

(define ((mi-list-name drop) a) (mi-list-t* mi-list-t*)
  (define a-next mi-list-t* (mi-list-rest a))
  (free a)
  (return a-next))

(define ((mi-list-name destroy) a) (void mi-list-t*)
  "it would be nice to set the pointer to zero, but that would require more indirection with a pointer-pointer"
  (define a-next mi-list-t* 0)
  (while a
    (set a-next (struct-pointer-get a link))
    (free a)
    (set a a-next)))

(define ((mi-list-name add) a value) (mi-list-t* mi-list-t* mi-list-element-t)
  (define element mi-list-t* (calloc 1 (sizeof mi-list-t)))
  (if (not element) (return 0))
  (set
    (struct-pointer-get element data) value
    (struct-pointer-get element link) a)
  (return element))

(define ((mi-list-name length) a) (size-t mi-list-t*)
  (define result size-t 0)
  (while a
    (set result (+ 1 result))
    (set a (mi-list-rest a)))
  (return result))

(pre-undefine mi-list-name-prefix mi-list-element-t mi-list-struct-name mi-list-t)