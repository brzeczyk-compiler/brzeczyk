#!/bin/bash
IGNORE_HEADER="// INTEGRATION TESTS IGNORE"
COMPILER_PATH="./app/build/install/app/bin/app"
TEMP_TEST_CASE_PREFIX="test_case"
TEMP_SPLIT_TEST_CASE_PREFIX="current_case"

FAILED_CASES=0
SUCCESSFUL_CASES=0
FAILED_TO_COMPILE=0

# bash trickery to get rid of identations at the start of lines
print_multiline_message() {
    echo "$1" | sed 's/^[ \t]*//g'
}

update() {
    echo -en "\rPassed: $SUCCESSFUL_CASES, Failed: $FAILED_CASES, Compilation errors: $FAILED_TO_COMPILE"
}

run_test_case() {
    csplit -f "$TEMP_SPLIT_TEST_CASE_PREFIX" -z $1 '/Input:/' '/Exit code:/' '/Output:/' 1>/dev/null
    input=$(cat "$TEMP_SPLIT_TEST_CASE_PREFIX"00 | tail +2)
    expected_exit_code=$(cat "$TEMP_SPLIT_TEST_CASE_PREFIX"01 | tail +2)
    expected_output=$(cat "$TEMP_SPLIT_TEST_CASE_PREFIX"02 | tail +2)
    actual_output=$(./a.out <<< "$input")
    actual_exit_code=$?

    if [[ $actual_output != $expected_output || $expected_exit_code != $actual_exit_code ]]; then
        print_multiline_message "
        TEST FAILED!!!!
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
    else
        ((SUCCESSFUL_CASES++))
    fi
    update
    rm "$TEMP_SPLIT_TEST_CASE_PREFIX"*
}

run_test_cases() {
    csplit --suppress-matched -f "$TEMP_TEST_CASE_PREFIX" -z $1 "/^-*$/" '{*}' 1>/dev/null
    for test_case in $(find . -name "$TEMP_TEST_CASE_PREFIX*"); do
        run_test_case $test_case
        rm $test_case
    done
}

if [[ ! -f $COMPILER_PATH ]]; then
    print_multiline_message "Could not find compiler at $COMPILER_PATH!
    Have you run ./gradlew install?"
    exit 0
fi

for tested_program in $(find . -path "./integration_tests/*.bzz"); do
    $COMPILER_PATH $tested_program -o a.out -e stdlib/stdlib.c 1>/dev/null
    if [[ ! -f a.out ]]; then
        echo "Cannot compile $tested_program! Skipping, but you should fix this..."
        ((FAILED_TO_COMPILE++))
        continue
    fi

    run_test_cases "${tested_program%%bzz}tests"
    # clean up
    rm a.o a.out a.asm
done

echo
# exit code for CI
exit $(($FAILED_TO_COMPILE + $FAILED_CASES))
