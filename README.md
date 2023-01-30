# Brzeczyk
A compiler implemented by TCS UJ students at "Compilers" course in the 2022/23 winter semester.

## Building
Project can be built using `gradle`. Installation of `gradle` is not required. To build, you can run
```bash
./gradlew build
```

## Testing
Tests can be run with
```bash
./test_all.sh
```

## Formatting
Code *must* be formatted with `ktlint`. CI will ensure this. You can format your code with
```bash
./gradlew ktlintFormat
```

## Linting
Linter for this repo is `ktlint`. To view issues with your code you can run:
```bash
./gradlew ktlint
```
Any issues with the code will be treated as errors by CI.

## Commits
Use [imperative commit message convention](https://cbea.ms/git-commit).
