#!/bin/bash
./gradlew "$@" test install || { echo "Tests failed!"; exit 1; }
echo "----------------------------------------"
echo "Running integration tests!"
echo "----------------------------------------"
./run_integration_tests.sh
