(pre-define
  db-id-t ui64
  db-type-id-t ui16
  db-ordinal-t ui32
  db-id-mask UINT64_MAX
  db-type-id-mask UINT16_MAX
  db-data-len-t ui32
  db-name-len-t ui8
  db-name-len-max UINT8_MAX
  db-data-len-max UINT32_MAX
  db-fields-len-t ui8
  db-indices-len-t ui8
  db-count-t ui32)