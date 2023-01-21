#include <stdio.h>
#include <inttypes.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>

// type definitions

typedef struct {
    uint64_t ref_count;
    uint64_t length;
    uint64_t values[];
} array_t;

#define ARRAY_OFFSET 2

array_t* get_array_ptr(uint64_t *address) {
    return (array_t*)(address - ARRAY_OFFSET);
}

typedef int64_t generator_id_t;
typedef int64_t generator_state_t;

typedef struct {
    int64_t value;
    generator_state_t state;
} resume_result_t;

typedef resume_result_t(*resume_func_t)(generator_id_t, generator_state_t);
typedef void(*finalize_func_t)(generator_id_t);

// input / output

void print_int64(int64_t value) {
    printf("%" PRId64 "\n", value);
}

int64_t read_int64() {
    int64_t value;
    scanf("%" SCNd64, &value);
    return value;
}

// internal procedures
// these functions have names starting with "_$" which makes them not accessible from our language

void* _$checked_malloc(size_t size) {
    void* address = malloc(size);
    if (size > 0 && address == NULL) {
        fprintf(stderr, "%s\n", strerror(ENOMEM));
        exit(1);
    }
    return address;
}

void* _$checked_realloc(void* address, size_t new_size) {
    address = realloc(address, new_size);
    if (new_size > 0 && address == NULL) {
        fprintf(stderr, "%s\n", strerror(ENOMEM));
        exit(1);
    }
    return address;
}

void _$populate_dynamic_array(uint64_t* address, uint64_t value, int64_t should_increment_refcount) {
    if (address == 0)
        return;
    array_t* array = get_array_ptr(address);

    for (uint64_t i = 0; i < array->length; i++) {
        array->values[i] = value; 
    }

    if (should_increment_refcount)
        get_array_ptr((uint64_t*)value)->ref_count += array->length;
}

void _$array_ref_count_decrement(uint64_t* address, int64_t level) { // simple array has level 1
    if (address == 0)
        return;
    array_t* array = get_array_ptr(address);

    if (--array->ref_count == 0) {
        if (level > 1) {
            for (size_t i = 0; i < array->length; i++) {
                _$array_ref_count_decrement((uint64_t*)array->values[i], level - 1);
            }
        }
        free(array);
    }
}

uint64_t* _$make_array_from_generator(resume_func_t resume, finalize_func_t finalize, generator_id_t id) {
    // the generator should be initialized, but never resumed
    array_t *array = _$checked_malloc((ARRAY_OFFSET + 4) * 8); // initial size = 4
    generator_state_t state = 0;

    for (size_t i = 0, size = 4; ; i++) {
        resume_result_t result = resume(id, state);
        if (result.state == 0) {
            array->length = i;
            break;
        }
        state = result.state;

        if (i == size) {
            size *= 2;
            array = _$checked_realloc(array, (ARRAY_OFFSET + size) * 8);
        }
        array->values[i] = result.value;
    }

    finalize(id);
    array = _$checked_realloc(array, (ARRAY_OFFSET + array->length) * 8);
    array->ref_count = 1;
    return array->values;
}

// generators

generator_id_t int64_range_init(int64_t max) {
    return max;
}

resume_result_t int64_range_resume(generator_id_t max, generator_state_t value) {
    resume_result_t result;

    if (value < max) {
        result.value = value;
        result.state = value + 1;
    } else
        result.state = 0;

    return result;
}

void int64_range_finalize(generator_id_t id) { }

generator_id_t int64_input_init() {
    return 0;
}

resume_result_t int64_input_resume(generator_id_t id, generator_state_t state) {
    resume_result_t result;
    result.state = scanf("%" SCNd64, &result.value) != EOF ? 1 : 0;
    return result;
}

void int64_input_finalize(generator_id_t id) { }
