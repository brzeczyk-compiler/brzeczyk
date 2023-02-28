# Brzęczyk

[ˈbʒɛ̃n͇ʧ̑ɨk]

A simple programming language with a very *polished* syntax.

Implemented by students at the Theoretical Computer Science Department of the Jagiellonian University
during the *Compilers* course in the 2022/23 winter semester.

The language is mostly C-like, with a few somewhat interesting (compared to C) features:

- nested functions able to access outer variables,
- dynamically allocated arrays with automatic reference counting,
- generator functions, allowing to pause their execution and pass values to the caller.

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

## Deciphering

The following table lists all of the language's keywords and their corresponding meanings.

Keyword    | Meaning             | Keyword      | Meaning               | Keyword  | Meaning
-----------|---------------------|--------------|-----------------------|----------|--------------------
`zm`       | variable            | `zakończ`    | finish; *return unit* | `Liczba` | number; *integer*
`wart`     | value               | `zewnętrzna` | external              | `Czy`    | whether; *boolean*
`stała`    | constant            | `jako`       | as                    | `Nic`    | nothing; *unit*
`jeśli`    | if                  | `ciąg`       | sequence; *array*     | `prawda` | truth; *true*
`zaś gdy`  | and when; *else if* | `dla`        | for                   | `fałsz`  | falsity; *false*
`wpp`      | otherwise; *else*   | `wewnątrz`   | inside; *in*          | `nic`    | nothing; *unit*
`dopóki`   | while               | `długość`    | length                | `nie`    | not
`przerwij` | stop; *break*       | `przekaźnik` | relay; *generator*    | `lub`    | or
`pomiń`    | skip; *continue*    | `przekaż`    | pass; *yield*         | `oraz`   | and
`czynność` | function            | `otrzymując` | receiving; *for*      | `wtw`    | if and only if
`zwróć`    | return              | `od`         | from; *in*            | `albo`   | either; *xor*

(Note that the characters such as `ą` and `ź` cannot be replaced.)

See [tests/](tests/) for more examples.

## Building, testing and running

This is a Gradle project bundled with Gradle Wrapper.

To build the project, use `./gradlew build`.

To run all tests, use `./test_all.sh`.

To compile the language's standard library, use `cc -c stdlib/stdlib.c`.

To package the compiler as a standalone program, use `./gradlew install`.
The result can be found in `app/build/install/app`.
To easily run the packaged compiler from the project root, use `./run.sh`.

By default, the compiler expects a single argument with input file name and produces executable `a.out` file as a result.
The standard library is expected at `stdlib.o` in the current working directory.
Use `./run.sh --help` to see all the options.
