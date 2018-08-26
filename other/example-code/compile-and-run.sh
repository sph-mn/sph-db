rm /tmp/sph-db-example-data/* 2&>/dev/null
gcc example-usage.c -o /tmp/sph-db-example -lsph-db &&
/tmp/sph-db-example
