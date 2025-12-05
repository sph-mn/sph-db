
#ifndef sph_filesystem_h_included
#define sph_filesystem_h_included

#include <unistd.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <libgen.h>
#include <errno.h>
#include <sph-db/sph/string.h>
#define file_exists(path) !(access(path, F_OK) == -1)

/** like posix dirname, but never modifies its argument and always returns a new string */
char* dirname_2(char* a) {
  char* path_copy = string_clone(a);
  return ((dirname(path_copy)));
}

/** return 1 if the path exists or has been successfully created */
uint8_t ensure_directory_structure(char* path, mode_t mkdir_mode) {
  if (file_exists(path)) {
    return (1);
  } else {
    char* path_dirname = dirname_2(path);
    uint8_t status = ensure_directory_structure(path_dirname, mkdir_mode);
    free(path_dirname);
    return ((status && ((EEXIST == errno) || (0 == mkdir(path, mkdir_mode)))));
  };
}
#endif
