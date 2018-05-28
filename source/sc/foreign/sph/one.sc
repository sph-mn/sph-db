(sc-comment "depends on sph.sc")
;-- string
(pre-include "string.h" "stdlib.h")

(define (ensure-trailing-slash a result) (b8 b8* b8**)
  "set result to a new string with a trailing slash added, or the given string if it already has a trailing slash.
  returns 0 if result is the given string, 1 if new memory could not be allocated, 2 if result is a new string"
  (define a-len b32 (strlen a))
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

(define (string-append a b) (b8* b8* b8*)
  "always returns a new string"
  (define a-length size-t (strlen a))
  (define b-length size-t (strlen b))
  (define result b8* (malloc (+ 1 a-length b-length)))
  (if result
    (begin
      (memcpy result a a-length)
      (memcpy (+ result a-length) b (+ 1 b-length))))
  (return result))

(define (string-clone a) (b8* b8*)
  "return a new string with the same contents as the given string. return 0 if the memory allocation failed"
  (define a-size size-t (+ 1 (strlen a)))
  (define result b8* (malloc a-size))
  (if result (memcpy result a a-size))
  (return result))

;-- filesystem
; access, mkdir dirname
(pre-include "unistd.h" "sys/stat.h" "libgen.h" "errno.h")
(pre-define (file-exists? path) (not (= (access path F-OK) -1)))

(define (dirname-2 a) (b8* b8*)
  "like posix dirname, but never modifies its argument and always returns a new string"
  (define path-copy b8* (string-clone a))
  (return (dirname path-copy)))

(define (ensure-directory-structure path mkdir-mode) (boolean b8* mode-t)
  "return 1 if the path exists or has been successfully created"
  (if (file-exists? path) (return #t)
    (begin
      (define path-dirname b8* (dirname-2 path))
      (define status boolean (ensure-directory-structure path-dirname mkdir-mode))
      (free path-dirname)
      (return (and status (or (= EEXIST errno) (= 0 (mkdir path mkdir-mode))))))))