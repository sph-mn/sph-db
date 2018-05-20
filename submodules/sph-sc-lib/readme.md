# sph-sc-lib

various utility c libraries written in [sc](http://sph.mn/c/view/me).
c versions are in source/c-precompiled.

# included libraries
* local-memory: manage heap memory in function scope
* status: return-status and error handling with a tiny status object with status id and source library id
* imht-set: a minimal, macro-based fixed size hash-table based data structure for sets of integers
* mi-list: a minimal, macro-based linked list
* one: various helpers. experimental
* guile: helpers for working with guile. experimental

# license
code is under gpl3+, documentation under cc-by-nc.

# local-memory
track memory allocations on the stack and free all allocations up to point easily

```c
#include "sph/local-memory.c"

int main() {
  local_memory_init(2);
  int* data_a = malloc(12 * sizeof(int));
  if(!data_a) goto exit;  // have to free nothing
  local_memory_add(data_a);
  // more code ...
  char* data_b = malloc(20 * sizeof(char));
  if(!data_b) goto exit;  // have to free "data_a"
  local_memory_add(data_b);
  // ...
  if (is_error) goto exit;  // have to free "data_a" and "data_b"
  // ...
exit:
  local_memory_free();
  return(0);
}
```

defines two hidden local variables: an array for addresses and the next index

# status
helpers for error and return status code handling with a routine local goto label and a tiny status object that includes the status id and an id for the library it belongs to, for when multiple libraries can return possibly overlapping error codes.

## usage example
```c
status_t test() {
  status_init;
  if (1 < 2) {
    int group_id = 123;
    int error_id = 456;
    status_set_both_goto(group_id, error_id);
  }
exit:
  return status;
}

int main() {
  status_init;
  // code ...
  status_require(test());
  // more code ...
exit:
  return status.id;
}
```

## bindings
```
status_failure_p
status_goto
status_group_undefined
status_id_is_p(status_id)
status_id_success
status_init
status_require
status_require_x(expression)
status_reset
status_set_both(group_id, status_id)
status_set_both_goto(group_id, status_id)
status_set_group(group_id)
status_set_group_goto(group_id)
status_set_id(status_id)
status_set_id_goto(status_id)
status_success_p
```

# imht-set
a data structure for storing a set of integers.
can easily deal with millions of values. a benchmark on an "amd phenom 2" with 3ghz wrote and read 100 million entries in 4 seconds.
insert/delete/search should all be o(1). space usage is roughly double the size of elements. the maximum number of elements a set can store is defined on creation and does not automatically increase.
this implementation optimises maximum read and write speed and a small code size and trades a higher memory usage for it. if lower memory usage is important, there is an option to reduce memory usage, with a potential performance loss; otherwise you might want to look for a set implementation similar to google sparse hash.

the name "imht-set" is derived from "integer modulo hash table set".

## dependencies
* the c standard library (stdlib.h and inttypes.h)

## usage examples
```
#include "imht_set.c";
```

the file needs to be in the load path or the same directory.

### creation
```
imht_set_t* set;
imht_set_create(200, &set);
```

returns 1 on success or 0 if the memory allocation failed.

the set size does not automatically grow, so a new set has to be created should the specified size later turn out to be insufficient.

### insert
```c
imht_set_add(set, 4);
```

returns the address of the added or already included element, 0 if there is no space left in the set.

### search
```c
imht_set_contains_p(set, 4) ? 1 : 0;
```

```c
uint64_t* value_address = imht_set_find(set, 4);
```

returns the address of the element in the set, 0 if it was not found.

caveat: if "imht_set_can_contain_zero_p" is defined, which is the default, dereferencing the memory address for the value 0, if it was found, will give 1 instead.

### removal
```c
imht_set_remove(set, 4);
```

returns 1 if the element was removed, 0 if it was not found.

### deallocation
```c
imht_set_destroy(set);
```

this is an important call for when the set is no longer needed, since its memory is otherwise not deallocated until the process ends.

## configuration options
configuration can be done by defining certain macro variables before including the imht-set source code.

### integer size
an imht-set only stores integers of the same type. supported are all typical integer values, from char to uint64_t.
the type that sets can take is fixed and can not be changed after inclusion of the imht-set source file.

```c
#define imht_set_key_t uint64_t
```

the default type is unsigned 64 bit.

if you would like to use multiple sets with different integer sizes at the same time, include the source file multiple times with imht_set_key_t set to different values before inclusion.

### memory usage
```
#define imht_set_size_factor 2
```

by default, the memory allocated for the set is at least double the number of elements it is supposed to store (rounded to the nearest prime eventually).
this can be changed in this definition, and a lower set size factor approaching 1 leads to more efficient memory usage, with 1 being the lowest possible, where only as much space as the elements need by themselves is allocated.
the downside is that the insert/delete/search performance is more likely to approach and reach o(n).

### zero support
by default, the integer 0 is a valid value for a set. but as an optimisation, this can be disabled by defining a macro for "imht_set_can_contain_zero_p" with the value zero.

```c
#define imht_set_can_contain_zero_p 0
```

with this definition, a zero in sets can not be found, but the set routines should work a tiny little bit faster.

## modularity and implementation
the "imht_set_t" type is a structure with the two fields "size" and "content". "content" is a one-dimensional array that stores values at indices determined by a hash function.
the set routines automatically adapt should the values for size and content change. therefore, automatic resizing can be implemented by adding new "add" and "remove" routines and rewriting the content data.

# mi-list
a minimal, generically usable linked list with custom element types.

## dependencies
* the c standard library (stdlib.h and inttypes.h)

## usage example
```c
// include to define a list type
#define mi_list_name_prefix mylist
#define mi_list_element_t uint64_t
#include "mi_list.c"

// include again for another list type with a different element type
#define mi_list_name_prefix u32_list
#define mi_list_element_t uint32_t
#include mi_list.c

// new empty list creation
u32_list_t* a = 0;

// add elements
u32_list_add(a, 3);
u32_list_add(a, 4);

// iterate over all list elements
u32_list_t* rest = a;
while(rest) {
  uint32_t element = mi_list_first(rest);
  rest = mi_list_rest(rest);
}
```

## bindings
```c
{prefix}_add
{prefix}_destroy
{prefix}_drop
{prefix}_length
{prefix}_struct
{prefix}_t
mi_list_first
mi_list_first_address
mi_list_rest
```

## type
the type for mi-lists is defined as follows. this information would be useful for defining new list primitives.

```c
typedef struct mi_list_name_prefix##_struct {
  struct mi_list_name_prefix##_struct *link;
  mi_list_element_t data;
} mi_list_t;
```
