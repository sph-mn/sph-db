(sc-comment "depends on sph/string.c")
(pre-include "unistd.h" "sys/stat.h" "sys/types.h" "libgen.h" "errno.h")
(pre-define (file-exists path) (not (= (access path F-OK) -1)))

(define (dirname-2 a) (uint8-t* uint8-t*)
  "like posix dirname, but never modifies its argument and always returns a new string"
  (define path-copy uint8-t* (string-clone a))
  (return (dirname path-copy)))

(define (ensure-directory-structure path mkdir-mode) (uint8-t uint8-t* mode-t)
  "return 1 if the path exists or has been successfully created"
  (if (file-exists path) (return #t)
    (begin
      (define path-dirname uint8-t* (dirname-2 path))
      (define status uint8-t (ensure-directory-structure path-dirname mkdir-mode))
      (free path-dirname)
      (return (and status (or (= EEXIST errno) (= 0 (mkdir path mkdir-mode))))))))