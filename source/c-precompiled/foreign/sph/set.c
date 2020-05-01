/* a macro that defines set data types for arbitrary value types,
using linear probing for collision resolve,
hash and equal functions are customisable by defining macros and re-including the source.
sph-set-empty-value and sph-set-true-value need to be set for values types other than integers.
when sph-set-allow-empty-value is 1, then the empty value is stored at the first index of .values and the other values start at index 1.
the default hash functions work on integers.
compared to hashtable.c, this uses less than half of the space and operations are faster (about 20% in first tests) */
#include <stdlib.h>
#include <inttypes.h>
#define sph_set_hash_integer(value, hashtable_size) (value % hashtable_size)
#define sph_set_equal_integer(value_a, value_b) (value_a == value_b)
/* sph-set-true-value is used only at index 0 for the empty-value */
#ifndef sph_set_size_factor
#define sph_set_size_factor 2
#endif
#ifndef sph_set_hash
#define sph_set_hash sph_set_hash_integer
#endif
#ifndef sph_set_equal
#define sph_set_equal sph_set_equal_integer
#endif
#ifndef sph_set_allow_empty_value
#define sph_set_allow_empty_value 1
#endif
#ifndef sph_set_empty_value
#define sph_set_empty_value 0
#endif
#ifndef sph_set_true_value
#define sph_set_true_value 1
#endif
uint32_t sph_set_primes[] = { 53, 97, 193, 389, 769, 1543, 3079, 6151, 12289, 24593, 49157, 98317, 196613, 393241, 786433, 1572869, 3145739, 6291469, 12582917, 25165843, 50331653, 100663319, 201326611, 402653189, 805306457, 1610612741 };
uint32_t* sph_set_primes_end = (sph_set_primes + 25);
size_t sph_set_calculate_size(size_t min_size) {
  min_size = (sph_set_size_factor * min_size);
  uint32_t* primes;
  for (primes = sph_set_primes; (primes <= sph_set_primes_end); primes += 1) {
    if (min_size <= *primes) {
      return ((*primes));
    };
  };
  /* if no prime has been found, make size at least an odd number */
  return ((1 | min_size));
}
#if sph_set_allow_empty_value
#define sph_set_get_part_1 \
  if (sph_set_equal(sph_set_empty_value, value)) { \
    return ((sph_set_equal(sph_set_true_value, (*(a.values))) ? a.values : 0)); \
  }; \
  hash_i = (1 + sph_set_hash(value, (a.size - 1)));
#define sph_set_get_part_2 i = 1
#define sph_set_add_part_1 \
  if (sph_set_equal(sph_set_empty_value, value)) { \
    *(a.values) = sph_set_true_value; \
    return ((a.values)); \
  }; \
  hash_i = (1 + sph_set_hash(value, (a.size - 1)));
#define sph_set_add_part_2 i = 1
#define sph_set_new_part_1 min_size += 1
#else
#define sph_set_get_part_1 hash_i = sph_set_hash(value, (a.size))
#define sph_set_get_part_2 i = 0
#define sph_set_add_part_1 hash_i = sph_set_hash(value, (a.size))
#define sph_set_add_part_2 i = 0
#define sph_set_new_part_1 0
#endif
#define sph_set_declare_type(name, value_type) \
  typedef struct { \
    size_t size; \
    value_type* values; \
  } name##_t; \
  uint8_t name##_new(size_t min_size, name##_t* result) { \
    value_type* values; \
    min_size = sph_set_calculate_size(min_size); \
    sph_set_new_part_1; \
    values = calloc(min_size, (sizeof(value_type))); \
    if (!values) { \
      return (1); \
    }; \
    (*result).values = values; \
    (*result).size = min_size; \
    return (0); \
  } \
  void name##_free(name##_t a) { free((a.values)); } \
\
  /** returns the address of the value or 0 if it was not found. \
        if sph_set_allow_empty_value is true and the value is included, then address points to a sph_set_true_value */ \
  value_type* name##_get(name##_t a, value_type value) { \
    size_t i; \
    size_t hash_i; \
    sph_set_get_part_1; \
    i = hash_i; \
    while ((i < a.size)) { \
      if (sph_set_equal(sph_set_empty_value, ((a.values)[i]))) { \
        return (0); \
      } else { \
        if (sph_set_equal(value, ((a.values)[i]))) { \
          return ((i + a.values)); \
        }; \
      }; \
      i += 1; \
    }; \
    /* wraps over */ \
    sph_set_get_part_2; \
    while ((i < hash_i)) { \
      if (sph_set_equal(sph_set_empty_value, ((a.values)[i]))) { \
        return (0); \
      } else { \
        if (sph_set_equal(value, ((a.values)[i]))) { \
          return ((i + a.values)); \
        }; \
      }; \
      i += 1; \
    }; \
    return (0); \
  } \
\
  /** returns the address of the value or 0 if no space is left */ \
  value_type* name##_add(name##_t a, value_type value) { \
    size_t i; \
    size_t hash_i; \
    sph_set_add_part_1; \
    i = hash_i; \
    while ((i < a.size)) { \
      if (sph_set_equal(sph_set_empty_value, ((a.values)[i]))) { \
        (a.values)[i] = value; \
        return ((i + a.values)); \
      }; \
      i += 1; \
    }; \
    /* wraps over */ \
    sph_set_add_part_2; \
    while ((i < hash_i)) { \
      if (sph_set_equal(sph_set_empty_value, ((a.values)[i]))) { \
        (a.values)[i] = value; \
        return ((i + a.values)); \
      }; \
      i += 1; \
    }; \
    return (0); \
  } \
\
  /** returns 0 if the element was removed, 1 if it was not found */ \
  uint8_t name##_remove(name##_t a, value_type value) { \
    value_type* v = name##_get(a, value); \
    if (v) { \
      *v = sph_set_empty_value; \
      return (0); \
    } else { \
      return (1); \
    }; \
  }
