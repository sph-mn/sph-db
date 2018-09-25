#include <stdlib.h>
#include <inttypes.h>
#ifndef imht_set_key_t
#define imht_set_key_t uint64_t
#endif
#ifndef imht_set_can_contain_zero
#define imht_set_can_contain_zero 1
#endif
#ifndef imht_set_size_factor
#define imht_set_size_factor 2
#endif
uint16_t imht_set_primes[] = { 0, 3, 7, 13, 19, 29, 37, 43, 53, 61, 71, 79, 89, 101, 107, 113, 131, 139, 151, 163, 173, 181, 193, 199, 223, 229, 239, 251, 263, 271, 281, 293, 311, 317, 337, 349, 359, 373, 383, 397, 409, 421, 433, 443, 457, 463, 479, 491, 503, 521, 541, 557, 569, 577, 593, 601, 613, 619, 641, 647, 659, 673, 683, 701, 719, 733, 743, 757, 769, 787, 809, 821, 827, 839, 857, 863, 881, 887, 911, 929, 941, 953, 971, 983, 997 };
uint16_t* imht_set_primes_end = (imht_set_primes + 83);
typedef struct {
  size_t size;
  imht_set_key_t* content;
} imht_set_t;
size_t imht_set_calculate_hash_table_size(size_t min_size) {
  min_size = (imht_set_size_factor * min_size);
  uint16_t* primes = imht_set_primes;
  while ((primes < imht_set_primes_end)) {
    if (min_size <= *primes) {
      return ((*primes));
    } else {
      primes = (1 + primes);
    };
  };
  if (min_size <= *primes) {
    return ((*primes));
  };
  return ((1 | min_size));
};
uint8_t imht_set_create(size_t min_size, imht_set_t** result) {
  *result = malloc((sizeof(imht_set_t)));
  if (!*result) {
    return (1);
  };
  min_size = imht_set_calculate_hash_table_size(min_size);
  (**result).content = calloc(min_size, (sizeof(imht_set_key_t)));
  (**result).size = min_size;
  return (((*result)->content ? 0 : 1));
};
void imht_set_destroy(imht_set_t* a) {
  if (a) {
    free((a->content));
    free(a);
  };
};
#if imht_set_can_contain_zero
#define imht_set_hash(value, hash_table) (value ? (1 + (value % (hash_table.size - 1))) : 0)
#else
#define imht_set_hash(value, hash_table) (value % hash_table.size)
#endif
/** returns the address of the element in the set, 0 if it was not found.
  caveat: if imht-set-can-contain-zero is defined, which is the default,
  pointer-geterencing a returned address for the found value 0 will return 1 instead */
imht_set_key_t* imht_set_find(imht_set_t* a, imht_set_key_t value) {
  imht_set_key_t* h = (a->content + imht_set_hash(value, (*a)));
  if (*h) {
#if imht_set_can_contain_zero
    if ((*h == value) || (0 == value)) {
      return (h);
    };
#else
    if (*h == value) {
      return (h);
    };
#endif
    imht_set_key_t* content_end = (a->content + (a->size - 1));
    imht_set_key_t* h2 = (1 + h);
    while ((h2 < content_end)) {
      if (!*h2) {
        return (0);
      } else {
        if (value == *h2) {
          return (h2);
        };
      };
      h2 = (1 + h2);
    };
    if (!*h2) {
      return (0);
    } else {
      if (value == *h2) {
        return (h2);
      };
    };
    h2 = a->content;
    while ((h2 < h)) {
      if (!*h2) {
        return (0);
      } else {
        if (value == *h2) {
          return (h2);
        };
      };
      h2 = (1 + h2);
    };
  };
  return (0);
};
#define imht_set_contains(a, value) ((0 == imht_set_find(a, value)) ? 0 : 1)
/** returns 1 if the element was removed, 0 if it was not found */
uint8_t imht_set_remove(imht_set_t* a, imht_set_key_t value) {
  imht_set_key_t* value_address = imht_set_find(a, value);
  if (value_address) {
    *value_address = 0;
    return (1);
  } else {
    return (0);
  };
};
/** returns the address of the added or already included element, 0 if there is no space left in the set */
imht_set_key_t* imht_set_add(imht_set_t* a, imht_set_key_t value) {
  imht_set_key_t* h = (a->content + imht_set_hash(value, (*a)));
  if (*h) {
#if imht_set_can_contain_zero
    if ((value == *h) || (0 == value)) {
      return (h);
    };
#else
    if (value == *h) {
      return (h);
    };
#endif
    imht_set_key_t* content_end = (a->content + (a->size - 1));
    imht_set_key_t* h2 = (1 + h);
    while (((h2 <= content_end) && *h2)) {
      h2 = (1 + h2);
    };
    if (h2 > content_end) {
      h2 = a->content;
      while (((h2 < h) && *h2)) {
        h2 = (1 + h2);
      };
      if (h2 == h) {
        return (0);
      } else {
#if imht_set_can_contain_zero
        *h2 = ((0 == value) ? 1 : value);
#else
        *h2 = value;
#endif
      };
    } else {
#if imht_set_can_contain_zero
      *h2 = ((0 == value) ? 1 : value);
#else
      *h2 = value;
#endif
    };
  } else {
#if imht_set_can_contain_zero
    *h = ((0 == value) ? 1 : value);
#else
    *h = value;
#endif
    return (h);
  };
};