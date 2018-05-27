(pre-define imht-set-key-t db-id-t)
(sc-include "foreign/sph/imht-set")

(pre-define
  mi-list-name-prefix db-ids
  mi-list-element-t db-id-t)

(sc-include "foreign/sph/mi-list")

#;(pre-define
  mi-list-name-prefix db-data-list
  mi-list-element-t db-data-t)

;(sc-include "foreign/sph/mi-list")

#;(pre-define
  mi-list-name-prefix db-data-records
  mi-list-element-t db-data-record-t)

;(sc-include "foreign/sph/mi-list")

(pre-define
  mi-list-name-prefix db-graph-records
  mi-list-element-t db-graph-record-t)

(sc-include "foreign/sph/mi-list")

(pre-define
  db-ids-first mi-list-first
  db-ids-first-address mi-list-first-address
  db-ids-rest mi-list-rest
  db-graph-records-first mi-list-first
  db-graph-records-first-address mi-list-first-address
  db-graph-records-rest mi-list-rest
  ;db-data-list-first mi-list-first
  ;db-data-list-first-address mi-list-first-address
  ;db-data-list-rest mi-list-rest
  ;db-data-records-first mi-list-first
  ;db-data-records-first-address mi-list-first-address
  ;db-data-records-rest mi-list-rest
  )