#include <stdio.h>
#include <inttypes.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>

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

void _$populate_dynamic_array(uint64_t* address, uint64_t value, int64_t should_increment_refcount) {
    if (address == 0)
        return;

    uint64_t length = *(address + 1);
    uint64_t* valueRefCount = ((uint64_t*) value) - 2;

    for (uint64_t i = 2; i<length; i++) {
        address[i] = value;
        if (should_increment_refcount) ++(*valueRefCount);
    }
}

void _$array_ref_count_decrement(uint64_t* address, int64_t level) { // simple array has level 1
    if (address == 0)
        return;

    uint64_t* ref_count = address - 2;
    uint64_t* length = address - 1;


    if (--*ref_count == 0) {
        if (level > 1) {
            for (size_t i = 0; i < *length; i++) {
                _$array_ref_count_decrement(((uint64_t**)address)[i], level - 1);
            }
        }
        free(ref_count); // reference counter is the first field of the memory block we want to deallocate
    }
}

// generators

typedef int64_t generator_id_t;
typedef int64_t generator_state_t;

typedef struct {
    int64_t value;
    generator_state_t state;
} resume_result_t;

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
