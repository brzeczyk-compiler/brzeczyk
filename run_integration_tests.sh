#!/bin/bash
IGNORE_HEADER="// INTEGRATION TESTS IGNORE"
VALGRIND_HEADER="// USE VALGRIND"
COMPILER_PATH="./app/build/install/app/bin/app"
TEMP_TEST_CASE_PREFIX="test_case"
TEMP_SPLIT_TEST_CASE_PREFIX="current_case"

FAILED_CASES=0
SUCCESSFUL_CASES=0
FAILED_TO_COMPILE=0

CLEAR_CR="\r\033[K"

# bash trickery to get rid of indentations at the start of lines
print_multiline_message() {
    echo "$1" | sed 's/^[ \t]*//g'
}

update() {
    echo -en "${CLEAR_CR}Passed: $SUCCESSFUL_CASES, Failed: $FAILED_CASES, Compilation errors: $FAILED_TO_COMPILE"
}

run_test_case() {
    csplit -f "$TEMP_SPLIT_TEST_CASE_PREFIX" -z "$1" '/Input:/' '/Exit code:/' '/Output:/' 1>/dev/null
    input=$(tail +2 "${TEMP_SPLIT_TEST_CASE_PREFIX}00")
    expected_exit_code=$(tail +2 "${TEMP_SPLIT_TEST_CASE_PREFIX}01")
    expected_output=$(tail +2 "${TEMP_SPLIT_TEST_CASE_PREFIX}02")
    actual_output=$(./a.out <<< "$input")
    actual_exit_code=$?
    test_name="TEST $2::$(basename $1)"

    if [[ $actual_output != "$expected_output" || $expected_exit_code != "$actual_exit_code" ]]; then
        print_multiline_message "
        $test_name FAILED!!!!
        Input:
        $input
        Expected output:
        $expected_output
        Actual output:
        $actual_output
        Expected exit code: $expected_exit_code
        Actual exit code: $actual_exit_code
        "
        ((FAILED_CASES++))
    elif [[ "$3" = true && ! $(valgrind --error-exitcode=1 --leak-check=full -s ./a.out <<< "$input" 2>/dev/null) ]]; then
        print_multiline_message "
        $test_name has upset Valgrind!"
        ((FAILED_CASES++))
    else
        ((SUCCESSFUL_CASES++))
    fi
    update
    rm "$TEMP_SPLIT_TEST_CASE_PREFIX"*
}

run_test_cases() {
    csplit --suppress-matched -f "$TEMP_TEST_CASE_PREFIX" -z "$1" "/^-*$/" '{*}' 1>/dev/null
    for test_case in $(find . -name "$TEMP_TEST_CASE_PREFIX*"); do
        run_test_case "$test_case" "$2" "$3"
        rm "$test_case"
    done
}

if [[ ! -f $COMPILER_PATH ]]; then
    print_multiline_message "Could not find compiler at $COMPILER_PATH!
    Have you run ./gradlew install?"
    exit 0
fi

for tested_program in $(find . -path "./tests/*.bzz"); do
    if [[ $(head -1 "$tested_program") == "$IGNORE_HEADER" ]]; then
        echo -e "${CLEAR_CR}Skipping $tested_program..."
        update
        continue
    fi
    use_valgrind=false
    if [[ $(head -1 "$tested_program") == "$VALGRIND_HEADER" ]]; then
        use_valgrind=true
    fi

    $COMPILER_PATH "$tested_program" -o a.out -l stdlib/stdlib.c 1>/dev/null
    if [[ ! -f a.out ]]; then
        echo -e "${CLEAR_CR}Cannot compile $tested_program! Skipping, but you should fix this..."
        ((FAILED_TO_COMPILE++))
        update
        continue
    fi

    run_test_cases "${tested_program%%bzz}tests" "${tested_program%%.bzz}" "$use_valgrind"
    # clean up
    rm a.o a.out a.asm
done

echo
# exit code for CI
exit $((FAILED_TO_COMPILE + FAILED_CASES))
