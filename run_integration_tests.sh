#!/bin/bash
IGNORE_HEADER="// INTEGRATION TESTS IGNORE"
TEMP_TEST_CASE_PREFIX="test_case"
TEMP_SPLIT_TEST_CASE_PREFIX="current_case"

FAILED_CASES=0
FAILED_TO_COMPILE=0

# bash trickery to get rid of identations at the start of lines
print_multiline_message() {
    echo "$1" | sed 's/^[ \t]*//g'
}

run_test_case() {
    test_number=$(echo $1 | grep -Eo '[0-9]+$')
    echo "Case number $test_number..."
    csplit -f "$TEMP_SPLIT_TEST_CASE_PREFIX" -z $1 '/Input:/' '/Exit code:/' '/Output:/' 1>/dev/null
    input=$(cat "$TEMP_SPLIT_TEST_CASE_PREFIX"00 | tail +2)
    expected_exit_code=$(cat "$TEMP_SPLIT_TEST_CASE_PREFIX"01 | tail +2)
    expected_output=$(cat "$TEMP_SPLIT_TEST_CASE_PREFIX"02 | tail +2)
    actual_output=$(./a.out <<< "$input")
    actual_exit_code=$?

    if [[ $actual_output != $expected_output || $expected_exit_code != $actual_exit_code ]]; then
        print_multiline_message "TEST FAILED!!!!
        Input:
        $input
        Expected output:
        $expected_output
        Actual output:
        $actual_output
        Expected exit code:
        $expected_exit_code
        Actual exit code:
        $actual_exit_code
        "
        ((FAILED_CASES++))
    else
        echo "Test number $test_number succeeded!"
    fi
    rm "$TEMP_SPLIT_TEST_CASE_PREFIX"*
}

run_test_cases() {
    csplit --suppress-matched -f "$TEMP_TEST_CASE_PREFIX" -z $1 "/^-*$/" '{*}' 1>/dev/null
    for test_case in $(find . -name "$TEMP_TEST_CASE_PREFIX*"); do
        run_test_case $test_case
        rm $test_case
    done
}

./gradlew install

for tested_program in $(find . -path "./integration_tests/*.bzz"); do
    if [[ $(head -1 $tested_program) == $IGNORE_HEADER ]]; then
        echo "Skipping $tested_program..."
        continue
    fi

    print_multiline_message "Compiling $tested_program...
    "
    ./app/build/install/app/bin/app $tested_program -o a.out -e stdlib/stdlib.c

    if [[ ! -f a.out ]]; then
        echo "Compilation failed! Skipping, but you should fix this..."
        ((FAILED_CASES++))
        ((FAILED_TO_COMPILE++))
        continue
    fi

    print_multiline_message "
    Compiled, testing $tested_program...
    "

    run_test_cases "${tested_program%%bzz}tests"
    # clean up
    rm a.o a.out a.asm
done

echo "Failed tests: $FAILED_CASES"
echo "Failed to compile: $FAILED_TO_COMPILE"
# exit code for CI
exit $FAILED_CASES
