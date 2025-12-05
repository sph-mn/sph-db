(sc-comment "compile-time configuration start")

(pre-define
  db-id-t uint64-t
  db-type-id-t uint16-t
  db-ordinal-t uint16-t
  db-id-mask UINT64_MAX
  db-type-id-mask UINT16_MAX
  db-name-len-t uint8-t
  db-name-len-max UINT8_MAX
  db-fields-len-t uint16-t
  db-indices-len-t uint16-t
  db-count-t uint32-t
  db-batch-len 100
  db-id-printf-format (pre-concat-string "%" PRIu64)
  db-type-id-printf-format (pre-concat-string "%" PRIu16)
  db-ordinal-printf-format (pre-concat-string "%" PRIu16))

(sc-comment "compile-time configuration end")
