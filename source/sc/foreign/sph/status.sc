(sc-comment
  "return status code and error handling. uses a local variable named \"status\" and a goto label named \"exit\".
      a status has an identifier and a group to discern between status identifiers of different libraries.
      status id 0 is success, everything else can be considered a failure or special case.
      status ids are 32 bit signed integers for compatibility with error return codes from many other existing libraries.
      group ids are strings to make it easier to create new groups that dont conflict with others compared to using numbers")

(pre-define
  sph-status #t
  status-id-success 0
  status-group-undefined ""
  status-declare (define status status-t (struct-literal status-id-success status-group-undefined))
  status-reset (status-set-both status-group-undefined status-id-success)
  status-is-success (= status-id-success status.id)
  status-is-failure (not status-is-success)
  status-goto (goto exit)
  (status-declare-group group)
  (begin
    "like status declare but with a default group"
    (define status status-t (struct-literal status-id-success group)))
  (status-set-both group-id status-id)
  (set
    status.group group-id
    status.id status-id)
  (status-require expression)
  (begin
    "update status with the result of expression and goto error on failure"
    (set status expression)
    (if status-is-failure status-goto))
  (status-set-id-goto status-id)
  (begin
    "set the status id and goto error"
    (set status.id status-id)
    status-goto)
  (status-set-group-goto group-id)
  (begin
    (set status.group group-id)
    status-goto)
  (status-set-both-goto group-id status-id)
  (begin
    (status-set-both group-id status-id)
    status-goto)
  (status-id-require expression)
  (begin
    "like status-require but expression returns only status.id"
    (set status.id expression)
    (if status-is-failure status-goto)))

(declare
  status-id-t (type int32-t)
  status-t
  (type
    (struct
      (id status-id-t)
      (group uint8-t*))))