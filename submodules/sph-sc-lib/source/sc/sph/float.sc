(pre-include "float.h" "math.h")

(pre-define (define-float-sum prefix type)
  (define ((pre-concat prefix _sum) numbers len) (type type* size-t)
    ; "sum numbers with rounding error compensation using kahan summation with neumaier modification"
    (define
      temp type
      element type)
    (define correction type 0)
    (set len (- len 1))
    (define result type (pointer-get numbers len))
    (while len
      (set len (- len 1))
      (set element (pointer-get numbers len))
      (set temp (+ result element))
      (set
        correction
        (+
          correction
          (if* (>= result element) (+ (- result temp) element) (+ (- element temp) result)))
        result temp))
    (return (+ correction result))))

(pre-define (define-float-array-nearly-equal? prefix type)
  (define ((pre-concat prefix _array-nearly-equal?) a a-len b b-len error-margin)
    (boolean type* size-t type* size-t type)
    (define index size-t 0)
    (if (not (= a-len b-len)) (return #f))
    (while (< index a-len)
      (if
        (not
          ( (pre-concat prefix _nearly-equal?)
            (pointer-get a index) (pointer-get b index) error-margin))
        (return #f))
      (set index (+ 1 index)))
    (return #t)))

(define (f64-nearly-equal? a b margin) (boolean f64-s f64-s f64-s)
  "approximate float comparison. margin is a factor and is low for low accepted differences.
   http://floating-point-gui.de/errors/comparison/"
  (if (= a b)
    (return #t)
    (begin
      (define diff f64-s (fabs (- a b)))
      (return
        (if* (or (= 0 a) (= 0 b) (< diff DBL_MIN))
          (< diff (* margin DBL_MIN)) (< (/ diff (fmin (+ (fabs a) (fabs b)) DBL_MAX)) margin))))))

(define (f32-nearly-equal? a b margin) (boolean f32-s f32-s f32-s)
  "approximate float comparison. margin is a factor and is low for low accepted differences.
   http://floating-point-gui.de/errors/comparison/"
  (if (= a b)
    (return #t)
    (begin
      (define diff f32-s (fabs (- a b)))
      (return
        (if* (or (= 0 a) (= 0 b) (< diff FLT_MIN))
          (< diff (* margin FLT_MIN)) (< (/ diff (fmin (+ (fabs a) (fabs b)) FLT_MAX)) margin))))))

(define-float-array-nearly-equal? f32 f32-s)
(define-float-array-nearly-equal? f64 f64-s)
(define-float-sum f32 f32-s)
(define-float-sum f64 f64-s)