#include <stdio.h>
#include <inttypes.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>

// functions with names starting with "_$" are internal procedures, they're not directly accessible from our language

void print_int64(int64_t value) {
    printf("%" PRId64 "\n", value);
}

int64_t read_int64() {
    int64_t value;
    scanf("%" SCNd64, &value);
    return value;
}

void* _$checked_malloc(size_t size) {
    void* address = malloc(size);
    if (size > 0 && address == NULL) {
        fprintf(stderr, "%s\n", strerror(ENOMEM));
        exit(1);
    }
    return address;
}

