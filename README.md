# Brzęczyk

[ˈbʒɛ̃n͇ʧ̑ɨk]

A simple programming language with very *polished* syntax.

Implemented by students at Theoretical Computer Science Department of Jagiellonian University
as the project for *Compilers* course in the 2022/23 winter semester.

The language does not really have anything out of ordinary and it's mostly C-like, with a few somewhat interesting (compared to C) features:

- nested functions able to access outer variables,
- dynamically allocated arrays with automatic reference counting,
- generator functions, allowing to pause execution and pass a value to the caller.

The compiler is written from scratch and produces x86_64 assembly code.

## Example program

```
czynność f(x: Liczba) -> Liczba {
    jeśli (x % 2 == 0)
        zwróć x / 2
    wpp
        zwróć 3 * x + 1
}

czynność główna() {
    zm x: Liczba = wczytaj()
    zm k: Liczba = 0

    dopóki (x != 1) {
        x = f(x)
        k = k + 1
    }

    powiedz(„Liczba kroków:”)
    napisz(k)
}
```

## What?

The following table lists all language keywords and their corresponding meanings.

Keyword     | Meaning
-------     | -------
`zm`        | variable
`wart`      | value
`stała`     | constant
`jeśli`     | if
`zaś gdy`   | and when; *else if*
`wpp`       | otherwise; *else*
`dopóki`    | while
`przerwij`  | stop; *break*
`pomiń`     | skip; *continue*
`czynność`  | function
`zwróć`     | return
`zakończ`   | finish; *return unit*
`zewnętrzna`| external
`jako`      | as
`ciąg`      | sequence; *array*
`dla`       | for
`wewnątrz`  | inside; *in*
`długość`   | length
`przekaźnik`| relay; *generator*
`przekaż`   | pass; *yield*
`otrzymując`| receiving; *for*
`od`        | from; *in*
`Liczba`    | number; *integer*
`Czy`       | whether; *boolean*
`Nic`       | nothing; *unit*
`prawda`    | truth; *true*
`fałsz`     | falsity; *false*
`nic`       | nothing; *unit*
`nie`       | not
`lub`       | or
`oraz`      | and
`wtw`       | if and only if
`albo`      | either; *xor*

(Note that the characters such as `ą` and `ź` cannot be replaced.)

See `tests` directory for more examples.

## Building, testing and running

This is a Gradle project bundled with Gradle Wrapper.

To build the project, use `./gradlew build`.

To run all tests, use `./test_all.sh`.

To compile the language's standard library, use `cc -c stdlib/stdlib.c`.

To package the compiler as a standalone program, use `./gradlew install`.
The result can be found in `app/build/install/app`.
To easily run the packaged compiler from project root, use `./run.sh`.

By default, the compiler expects the input file name as a single argument and produces executable `a.out` file as a result.
The standard library is expected at `stdlib.o` in the current working directory.
Use `./run.sh --help` to see all options.
