#!/bin/bash
./gradlew test "$@"
echo "----------------------------------------"
echo "Running integration tests!"
echo "----------------------------------------"
./run_integration_tests.sh
