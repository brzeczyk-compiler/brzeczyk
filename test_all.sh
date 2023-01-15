#!/bin/bash
./gradlew "$@" test install
echo "----------------------------------------"
echo "Running integration tests!"
echo "----------------------------------------"
./run_integration_tests.sh
