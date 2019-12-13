(sc-comment "a macro that defines set data types for arbitrary value types,"
  "using linear probing for collision resolve,"
  "with hash and equal functions customisable by defining macros and re-including the source."
  "when sph-set-allow-empty-value is 1, then the empty value is stored at the first index of .values and the other values start at index 1."
  "compared to hashtable.c, this uses less than half of the space and operations are faster (about 20% in first tests)")

(pre-include "stdlib.h" "inttypes.h")

(pre-define
  ; example hashing code
  (sph-set-hash-integer value hashtable-size) (modulo value hashtable-size)
  (sph-set-equal-integer value-a value-b) (= value-a value-b))

(pre-define-if-not-defined
  sph-set-size-factor 2
  sph-set-hash sph-set-hash-integer
  sph-set-equal sph-set-equal-integer
  sph-set-allow-empty-value 1
  sph-set-empty-value 0
  sph-set-true-value 1)

(declare sph-set-primes
  (array uint32-t ()
    ; from https://planetmath.org/goodhashtableprimes
    53 97 193
    389 769 1543
    3079 6151 12289
    24593 49157 98317
    196613 393241 786433
    1572869 3145739 6291469
    12582917 25165843 50331653 100663319 201326611 402653189 805306457 1610612741))

(define sph-set-primes-end uint32-t* (+ sph-set-primes 25))

(define (sph-set-calculate-size min-size) (size-t size-t)
  (set min-size (* sph-set-size-factor min-size))
  (declare primes uint32-t*)
  (for ((set primes sph-set-primes) (<= primes sph-set-primes-end) (set+ primes 1))
    (if (<= min-size *primes) (return *primes)))
  (sc-comment "if no prime has been found, make size at least an odd number")
  (return (bit-or 1 min-size)))

(pre-if sph-set-allow-empty-value
  (pre-define
    sph-set-get-part-1
    (begin
      (if (sph-set-equal sph-set-empty-value value)
        (return (if* (sph-set-equal sph-set-true-value *a.values) a.values 0)))
      (set hash-i (+ 1 (sph-set-hash value (- a.size 1)))))
    sph-set-get-part-2 (set i 1)
    sph-set-add-part-1
    (begin
      (if (sph-set-equal sph-set-empty-value value)
        (begin (set *a.values sph-set-true-value) (return a.values)))
      (set hash-i (+ 1 (sph-set-hash value (- a.size 1)))))
    sph-set-add-part-2 (set i 1)
    sph-set-new-part-1 (set+ min-size 1))
  (pre-define
    sph-set-get-part-1 (set hash-i (sph-set-hash value a.size))
    sph-set-get-part-2 (set i 0)
    sph-set-add-part-1 (set hash-i (sph-set-hash value a.size))
    sph-set-add-part-2 (set i 0)
    sph-set-new-part-1 0))

(pre-define (sph-set-declare-type name value-type)
  (begin
    (declare (pre-concat name _t) (type (struct (size size-t) (values value-type*))))
    (define ((pre-concat name _new) min-size result) (uint8-t size-t (pre-concat name _t*))
      ; returns 0 on success or 1 if the memory allocation failed
      (declare values value-type*)
      (set min-size (sph-set-calculate-size min-size))
      sph-set-new-part-1
      (set values (calloc min-size (sizeof value-type)))
      (if (not values) (return 1))
      (struct-set *result values values size min-size)
      (return 0))
    (define ((pre-concat name _free) a) (void (pre-concat name _t)) (begin (free a.values)))
    (define ((pre-concat name _get) a value) (value-type* (pre-concat name _t) value-type)
      "returns the address of the value or 0 if it was not found.
      if sph-set-allow-empty-value is true and the value is included, then address points to a sph-set-true-value"
      (declare i size-t hash-i size-t)
      sph-set-get-part-1
      (set i hash-i)
      (while (< i a.size)
        (if (sph-set-equal sph-set-empty-value (array-get a.values i)) (return 0)
          (if (sph-set-equal value (array-get a.values i)) (return (+ i a.values))))
        (set+ i 1))
      (sc-comment "wraps over")
      sph-set-get-part-2
      (while (< i hash-i)
        (if (sph-set-equal sph-set-empty-value (array-get a.values i)) (return 0)
          (if (sph-set-equal value (array-get a.values i)) (return (+ i a.values))))
        (set+ i 1))
      (return 0))
    (define ((pre-concat name _add) a value) (value-type* (pre-concat name _t) value-type)
      "returns the address of the value or 0 if no space is left"
      (declare i size-t hash-i size-t)
      sph-set-add-part-1
      (set i hash-i)
      (while (< i a.size)
        (if (sph-set-equal sph-set-empty-value (array-get a.values i))
          (begin (set (array-get a.values i) value) (return (+ i a.values))))
        (set+ i 1))
      (sc-comment "wraps over")
      sph-set-add-part-2
      (while (< i hash-i)
        (if (sph-set-equal sph-set-empty-value (array-get a.values i))
          (begin (set (array-get a.values i) value) (return (+ i a.values))))
        (set+ i 1))
      (return 0))
    (define ((pre-concat name _remove) a value) (uint8-t (pre-concat name _t) value-type)
      "returns 0 if the element was removed, 1 if it was not found"
      (define v value-type* ((pre-concat name _get) a value))
      (if v (begin (set *v sph-set-empty-value) (return 0)) (return 1)))))