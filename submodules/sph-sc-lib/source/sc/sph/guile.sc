(pre-define
  scm-first SCM_CAR
  scm-tail SCM_CDR
  scm-c-define-procedure-c-init (define scm-c-define-procedure-c-temp SCM)
  (scm-is-undefined a) (= SCM-UNDEFINED a)
  (scm-c-define-procedure-c name required optional rest c-function documentation)
  (begin
    "defines and registers a c routine as a scheme procedure with documentation.
    like scm-c-define-gsubr but also sets documentation.
    scm-c-define-procedure-c-init must have been called in scope"
    (set scm-c-define-procedure-c-temp (scm-c-define-gsubr name required optional rest c-function))
    (scm-set-procedure-property!
      scm-c-define-procedure-c-temp
      (scm-from-locale-symbol "documentation") (scm-from-locale-string documentation)))
  (scm-c-list-each list e body)
  (begin
    "SCM SCM c-compound-expression ->
    iterate over scm-list in c"
    (while (not (scm-is-null list))
      (set e (scm-first list))
      body
      (set list (scm-tail list)))))

(define (scm-debug-log value) (b0 SCM)
  ;display value with scheme write and add a newline
  (scm-call-2 (scm-variable-ref (scm-c-lookup "write")) value (scm-current-output-port))
  (scm-newline (scm-current-output-port)))

(define (scm-c-bytevector-take size-octets a) (SCM size-t b8*)
  ;creates a new bytevector of size-octects that contains the given bytevector
  (define r SCM (scm-c-make-bytevector size-octets))
  (memcpy (SCM-BYTEVECTOR-CONTENTS r) a size-octets)
  (return r))