package compiler.e2e

import kotlin.test.Test

class CorrectPrograms {
    @Test
    fun `test identifiers`() {
        E2eAsserter.assertProgramCorrect(
            """
            zm n: Liczba = 10234
            zm camelCaseIdentifier: Liczba = 10234
            zm snake_case_identifier: Liczba = 10234
            zm ident1f13r_w1tH_n00mb3r5: Liczba = 10234;
            """
        )
    }

    @Test
    fun `test types and literals`() {
        E2eAsserter.assertProgramCorrect(
            """
            czynność typy_i_literały() {
                zm n: Liczba = 10234
                n = 0
                n = -0
                n = -56789
                n = 56789
                n = 2147483647 // 32 bitowa ze znakiem
                n = -2147483648

                zm b: Czy = prawda
                b = fałsz
            }
            """
        )
    }

    @Test
    fun `test if, elif and else`() {
        E2eAsserter.assertProgramCorrect(
            """
            czynność jeśli_zaś_gdy_wpp() {
                zm x: Liczba
                zm y: Liczba = 10
                jeśli (fałsz) {
                    x = 1
                }
                zaś gdy (y == 10) {
                    x = 2
                }
                wpp {
                    x = 3
                }
                napisz(x) //wypisze 2
                jeśli(x == 2) {
                    napisz(14)
                }
            }
            """
        )
    }

    @Test
    fun `test while, break and continue`() {
        E2eAsserter.assertProgramCorrect(
            """
            czynność dopóKI_pomiń_przerwij() {
                zm x: Liczba = 0
                dopóki (prawda) { //dopóty
                jeśli (x == 420) pomiń
                napisz(x)
                x=x+1
                jeśli (x == 1000) przerwij
                }
            }
            """
        )
    }

    @Test
    fun `test return`() {
        E2eAsserter.assertProgramCorrect(
            """
            czynność przykład() -> Liczba {
                zwróć 42
            }

            czynność przykład2() {
                zm x: Liczba = 0
                dopóki (prawda) {
                    napisz(x)
                    x=x+1
                    jeśli(x==10) zakończ
                }
            }
            """
        )
    }

    @Test
    fun `test default arguments`() {
        E2eAsserter.assertProgramCorrect(
            """
            czynność suma(a: Liczba, b: Liczba = 2, c: Liczba = 4, d: Liczba = 8) -> Liczba {
                zwróć a + b + c + d
            }

            czynność domyślne_argumenty() {
                suma(10, 10, 10, 10) // 40
                suma(10, 10, 10)     // 38 = 10 + 10 + 10 + 8
                suma(0)              // 14 = 0 + 2 + 4 + 8
                suma(10, 11, d=0)    // 10 + 11 + 4 + 0
                suma(0, c=100)       // 110 = 0 + 2 + 100 + 8
            }
            """
        )
    }

    @Test
    fun `test local functions`() {
        E2eAsserter.assertProgramCorrect(
            """
            czynność zewnętrzna() {
                zm x: Liczba = 10

                czynność wewnętrzna(y: Liczba) -> Liczba {
                    // dopuszczamy odczyt zewnętrznej zmiennej
                    zwróć y + x
                }

                napisz(wewnętrzna(5)) // 15
                x = 5
                napisz(wewnętrzna(7)) // 12
            }
            """
        )
    }

    @Test
    fun `test recurrence`() {
        E2eAsserter.assertProgramCorrect(
            """
            czynność silnia(n: Liczba) -> Liczba {
                jeśli (n == 0)
                    zwróć 1
                wpp
                    zwróć n * silnia(n-1)
            }
            """
        )
    }

    @Test
    fun `test variables, values and constants`() {
        E2eAsserter.assertProgramCorrect(
            """
            czynność zmienne_wartości_stałe() {
                zm a: Liczba = 2
                a = 4
                zm b: Liczba = a // zmienne można modyfikować

                wart c: Liczba = 8
                wart d: Liczba = b // wartości mogą być ustalane w czasie wykonania

                stała e: Liczba = 271828

                zm g: Liczba

                jeśli (a > 0)
                    g = 0

                jeśli (b > 0)
                    g = 0
                wpp
                    g = 1

                zm h: Liczba = 10
                stała i: Liczba = 4

                {
                    zm h: Liczba = 32
                    zm i: Czy = prawda // to są nowe twory, mogą mieć inną stałość i typ
                    wart j: Liczba = 64
                    napisz(h) // 32
                }

                napisz(h) // 10
            }
            """
        )
    }
}