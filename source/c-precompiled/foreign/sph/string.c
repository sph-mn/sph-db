#include <string.h>
#include <stdlib.h>
/** set result to a new string with a trailing slash added, or the given string if it already has a trailing slash.
  returns 0 if result is the given string, 1 if new memory could not be allocated, 2 if result is a new string */
uint8_t ensure_trailing_slash(uint8_t* a, uint8_t** result) {
  uint32_t a_len = strlen(a);
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
uint8_t* string_append(uint8_t* a, uint8_t* b) {
  size_t a_length = strlen(a);
  size_t b_length = strlen(b);
  uint8_t* result = malloc((1 + a_length + b_length));
  if (result) {
    memcpy(result, a, a_length);
    memcpy((result + a_length), b, (1 + b_length));
  };
  return (result);
};
/** return a new string with the same contents as the given string. return 0 if the memory allocation failed */
uint8_t* string_clone(uint8_t* a) {
  size_t a_size = (1 + strlen(a));
  uint8_t* result = malloc(a_size);
  if (result) {
    memcpy(result, a, a_size);
  };
  return (result);
};
/** join strings into one string with each input string separated by delimiter.
  zero if strings-len is zero or memory could not be allocated */
uint8_t* string_join(uint8_t** strings, size_t strings_len, uint8_t* delimiter, size_t* result_len) {
  uint8_t* result;
  uint8_t* result_temp;
  size_t size;
  size_t size_temp;
  size_t i;
  size_t delimiter_len;
  if (!strings_len) {
    return (0);
  };
  /* size: string-null + delimiters + string-lengths */
  delimiter_len = strlen(delimiter);
  size = (1 + (delimiter_len * (strings_len - 1)));
  for (i = 0; (i < strings_len); i = (1 + i)) {
    size = (size + strlen((strings[i])));
  };
  result = malloc(size);
  if (!result) {
    return (0);
  };
  result_temp = result;
  size_temp = strlen((strings[0]));
  memcpy(result_temp, (strings[0]), size_temp);
  result_temp = (size_temp + result_temp);
  for (i = 1; (i < strings_len); i = (1 + i)) {
    memcpy(result_temp, delimiter, delimiter_len);
    result_temp = (delimiter_len + result_temp);
    size_temp = strlen((strings[i]));
    memcpy(result_temp, (strings[i]), size_temp);
    result_temp = (size_temp + result_temp);
  };
  result[(size - 1)] = 0;
  *result_len = (size - 1);
  return (result);
};