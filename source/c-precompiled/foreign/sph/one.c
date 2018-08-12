/* depends on sph.sc */
#include <string.h>
#include <stdlib.h>
/** set result to a new string with a trailing slash added, or the given string if it already has a trailing slash.
  returns 0 if result is the given string, 1 if new memory could not be allocated, 2 if result is a new string */
ui8 ensure_trailing_slash(ui8* a, ui8** result) {
  ui32 a_len = strlen(a);
  if (!a_len || ('/' == *(a + (a_len - 1)))) {
    *result = a;
    return (0);
  } else {
    char* new_a = malloc((2 + a_len));
    if (!new_a) {
      return (1);
    };
    memcpy(new_a, a, a_len);
    memcpy((new_a + a_len), "/", 1);
    *new_a = 0;
    *result = new_a;
    return (2);
  };
};
/** always returns a new string */
ui8* string_append(ui8* a, ui8* b) {
  size_t a_length = strlen(a);
  size_t b_length = strlen(b);
  ui8* result = malloc((1 + a_length + b_length));
  if (result) {
    memcpy(result, a, a_length);
    memcpy((result + a_length), b, (1 + b_length));
  };
  return (result);
};
/** return a new string with the same contents as the given string. return 0 if the memory allocation failed */
ui8* string_clone(ui8* a) {
  size_t a_size = (1 + strlen(a));
  ui8* result = malloc(a_size);
  if (result) {
    memcpy(result, a, a_size);
  };
  return (result);
};
#include <unistd.h>
#include <sys/stat.h>
#include <libgen.h>
#include <errno.h>
#define file_exists_p(path) !(access(path, F_OK) == -1)
/** like posix dirname, but never modifies its argument and always returns a new string */
ui8* dirname_2(ui8* a) {
  ui8* path_copy = string_clone(a);
  return (dirname(path_copy));
};
/** return 1 if the path exists or has been successfully created */
boolean ensure_directory_structure(ui8* path, mode_t mkdir_mode) {
  if (file_exists_p(path)) {
    return (1);
  } else {
    ui8* path_dirname = dirname_2(path);
    boolean status = ensure_directory_structure(path_dirname, mkdir_mode);
    free(path_dirname);
    return ((status && ((EEXIST == errno) || (0 == mkdir(path, mkdir_mode)))));
  };
};