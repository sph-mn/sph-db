
#ifndef sph_string_h_included
#define sph_string_h_included

#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include <inttypes.h>

/** set result to a new string with a trailing slash added, or the given string if it already has a trailing slash.
   returns 0 if result is the given string, 1 if new memory could not be allocated, 2 if result is a new string */
uint8_t ensure_trailing_slash(char* a, char** result) {
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
    new_a[a_len] = '/';
    new_a[(a_len + 1)] = 0;
    *result = new_a;
    return (2);
  };
}

/** always returns a new string */
char* string_append(char* a, char* b) {
  size_t a_length = strlen(a);
  size_t b_length = strlen(b);
  char* result = malloc((1 + a_length + b_length));
  if (!result) {
    return (0);
  };
  memcpy(result, a, a_length);
  memcpy((result + a_length), b, (1 + b_length));
  result[(a_length + b_length)] = 0;
  return (result);
}

/** return a new string with the same contents as the given string. return 0 if the memory allocation failed */
char* string_clone(char* a) {
  size_t a_size = (1 + strlen(a));
  char* result = malloc(a_size);
  if (result) {
    memcpy(result, a, a_size);
  };
  return (result);
}

/** join strings into one string with each input string separated by delimiter.
   zero if strings-len is zero or memory could not be allocated */
char* string_join(char** strings, size_t strings_len, char* delimiter, size_t* result_len) {
  char* result;
  char* cursor;
  size_t total_size;
  size_t part_size;
  size_t delimiter_len;
  delimiter_len = strlen(((char*)(delimiter)));
  total_size = (1 + (delimiter_len * (strings_len - 1)));
  for (size_t i = 0; (i < strings_len); i += 1) {
    total_size = (total_size + strlen(((char*)(strings[i]))));
  };
  result = malloc(total_size);
  if (!result) {
    return (0);
  };
  cursor = result;
  part_size = strlen(((char*)(strings[0])));
  memcpy(cursor, (strings[0]), part_size);
  cursor = (cursor + part_size);
  for (size_t i = 1; (i < strings_len); i += 1) {
    memcpy(cursor, delimiter, delimiter_len);
    cursor = (cursor + delimiter_len);
    part_size = strlen(((char*)(strings[i])));
    memcpy(cursor, (strings[i]), part_size);
    cursor = (cursor + part_size);
  };
  result[(total_size - 1)] = 0;
  *result_len = (total_size - 1);
  return (result);
}
void sph_display_bits_u8(uint8_t a) {
  printf("%u", (1 & a));
  for (uint8_t i = 1; (i < 8); i += 1) {
    printf("%u", ((((((uint8_t)(1)) << i) & a) != 0) ? 1 : 0));
  };
}
void sph_display_bits(void* a, size_t size) {
  for (size_t i = 0; (i < size); i += 1) {
    sph_display_bits_u8((((char*)(a))[i]));
  };
  printf("\n");
}
char* sph_helper_uint_to_string(uintmax_t a, size_t* result_len) {
  uintmax_t t;
  size_t digits;
  char* result;
  char* p;
  t = a;
  digits = 1;
  while ((t >= 10)) {
    t = (t / 10);
    digits = (digits + 1);
  };
  result = ((char*)(malloc((digits + 1))));
  if (!result) {
    return (0);
  };
  p = (((char*)(result)) + digits);
  *p = 0;
  do {
    p = (p - 1);
    *p = ((char)((48 + (a % 10))));
    a = (a / 10);
  } while ((a > 0));
  *result_len = digits;
  return (result);
}
#endif
