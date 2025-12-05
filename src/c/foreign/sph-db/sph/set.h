
#ifndef sph_set_h_included
#define sph_set_h_included

/* a macro that defines set data types and related functions for arbitrary value types.
 * compared to hashtable.c, this uses less than half the space and operations are faster (about 20% in first tests)
 * linear probing for collision resolve
 * sph-set-declare-type allows the null value (used for unset elements) to be part of the set
 * except for the null value, values are in field .values starting from index 1
 * notnull is used at index 0 to check if the empty-value is included
 * sph-set-declare-type-nonull does not support the null value to be part of the set and should be a bit faster
 * values are in .values, starting from index 0
 * null and notnull arguments are user provided so that they have the same data type as other set elements
 * primes from https://planetmath.org/goodhashtableprimes
 * automatic resizing is not implemented. resizing can be done by re-inserting each value into a larger set */
#include <stdlib.h>
#include <string.h>
#include <inttypes.h>
#if (SIZE_MAX > 0xffffffffu)
#define sph_set_calculate_size_extra(n) n = (n | (n >> 32))
#else
#define sph_set_calculate_size_extra n
#endif

#define sph_set_hash_integer(value, hashtable_size) (value % hashtable_size)
#define sph_set_equal_integer(value_a, value_b) (value_a == value_b)
#define sph_set_declare_type(name, value_type, set_hash, set_equal, null, size_factor) \
  typedef struct { \
    size_t size; \
    size_t mask; \
    value_type* values; \
    uint8_t* occupied; \
    uint8_t nullable; \
  } name##_t; \
  uint8_t name##_occupied_get(const uint8_t* bitmap, size_t index) { \
    size_t byte_index; \
    size_t bit_index; \
    uint8_t byte_value; \
    byte_index = (index >> 3); \
    bit_index = (index & 7); \
    byte_value = bitmap[byte_index]; \
    return (((byte_value >> bit_index) & 1)); \
  } \
  void name##_occupied_set(uint8_t* bitmap, size_t index) { \
    size_t byte_index; \
    size_t bit_index; \
    byte_index = (index >> 3); \
    bit_index = (index & 7); \
    bitmap[byte_index] = (bitmap[byte_index] | ((uint8_t)((1u << bit_index)))); \
  } \
  void name##_occupied_clear(uint8_t* bitmap, size_t index) { \
    size_t byte_index; \
    size_t bit_index; \
    byte_index = (index >> 3); \
    bit_index = (index & 7); \
    bitmap[byte_index] = (((uint8_t)(bitmap[byte_index])) & ((uint8_t)(~(1u << bit_index)))); \
  } \
  size_t name##_calculate_size(size_t n) { \
    n = (size_factor * n); \
    if (n < 2) { \
      n = 2; \
    }; \
    n = (n - 1); \
    n = (n | (n >> 1)); \
    n = (n | (n >> 2)); \
    n = (n | (n >> 4)); \
    n = (n | (n >> 8)); \
    n = (n | (n >> 16)); \
    sph_set_calculate_size_extra(n); \
    return ((1 + n)); \
  } \
  void name##_clear(name##_t* a) { \
    size_t bytes; \
    bytes = ((7 + a->size) >> 3); \
    a->nullable = 0; \
    memset((a->occupied), 0, bytes); \
  } \
  void name##_free(name##_t a) { \
    free((a.occupied)); \
    free((a.values)); \
  } \
  uint8_t name##_new(size_t min_size, name##_t* out) { \
    name##_t a; \
    size_t bytes; \
    a.size = name##_calculate_size(min_size); \
    a.values = malloc((a.size * sizeof(value_type))); \
    if (!a.values) { \
      return (1); \
    }; \
    bytes = ((a.size + 7) >> 3); \
    a.occupied = calloc(bytes, 1); \
    if (!a.occupied) { \
      free((a.values)); \
      return (1); \
    }; \
    a.nullable = 0; \
    a.mask = (a.size - 1); \
    *out = a; \
    return (0); \
  } \
  value_type* name##_get(name##_t a, value_type value) { \
    size_t i; \
    size_t j; \
    if (set_equal(value, null)) { \
      return ((a.nullable ? a.values : 0)); \
    }; \
    i = set_hash(value, (a.size)); \
    j = 0; \
    while ((j < a.size)) { \
      if (name##_occupied_get((a.occupied), i)) { \
        if (set_equal(((a.values)[i]), value)) { \
          return ((a.values + i)); \
        }; \
      } else { \
        return (0); \
      }; \
      i = ((i + 1) & a.mask); \
      j += 1; \
    }; \
    return (0); \
  } \
  value_type* name##_add(name##_t* a, value_type value) { \
    if (set_equal(value, null)) { \
      a->nullable = 1; \
      return ((a->values)); \
    }; \
    size_t i = set_hash(value, (a->size)); \
    size_t j = 0; \
    uint8_t occupied = 0; \
    while ((j < a->size)) { \
      occupied = name##_occupied_get((a->occupied), i); \
      if (occupied && set_equal(((a->values)[i]), value)) { \
        return ((&((a->values)[i]))); \
      }; \
      if (!occupied) { \
        (a->values)[i] = value; \
        name##_occupied_set((a->occupied), i); \
        return ((&((a->values)[i]))); \
      }; \
      i = ((i + 1) & a->mask); \
      j += 1; \
    }; \
    return (0); \
  } \
  uint8_t name##_remove(name##_t* a, value_type value) { \
    if (set_equal(value, null)) { \
      if (!a->nullable) { \
        return (1); \
      }; \
      a->nullable = 0; \
      return (0); \
    }; \
    size_t i = set_hash(value, (a->size)); \
    size_t j = 0; \
    size_t h = 0; \
    size_t dj = 0; \
    size_t di = 0; \
    size_t found = 0; \
    while (!found) { \
      if (!name##_occupied_get((a->occupied), i)) { \
        return (1); \
      }; \
      if (set_equal(((a->values)[i]), value)) { \
        j = i; \
        found = 1; \
      } else { \
        i = ((i + 1) & a->mask); \
      }; \
    }; \
    while (1) { \
      i = ((i + 1) & a->mask); \
      if (!name##_occupied_get((a->occupied), i)) { \
        name##_occupied_clear((a->occupied), j); \
        return (0); \
      }; \
      h = set_hash(((a->values)[i]), (a->size)); \
      dj = ((j - h) & a->mask); \
      di = ((i - h) & a->mask); \
      if (dj <= di) { \
        (a->values)[j] = (a->values)[i]; \
        j = i; \
      }; \
    }; \
    return (1); \
  }
#endif
