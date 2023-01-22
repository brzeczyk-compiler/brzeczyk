package compiler.e2e

import kotlin.test.Test

class CorrectProgramsE2eTest {
    @Test
    fun `test identifiers`() {
        E2eTestUtils.assertProgramCorrect(
            """
            czynność główna() {}
            zm n: Liczba = 10234
            zm camelCaseIdentifier: Liczba = 10234
            zm snake_case_identifier: Liczba = 10234
            zm ident1f13r_w1tH_n00mb3r5: Liczba = 10234;
            """
        )
    }

    @Test
    fun `test types and literals`() {
        E2eTestUtils.assertProgramCorrect( // FIXME: the last number literal does not work
            """
            czynność główna() {}
            czynność typy_i_literały() {
                zm n: Liczba = 10234
                n = 0
                n = -0
                n = -56789
                n = 56789
                n = 9223372036854775807 // 64 bitowa ze znakiem
                // n = -9223372036854775808

                zm b: Czy = prawda
                b = fałsz
            }
            """
        )
    }

    @Test
    fun `test if, elif and else`() {
        E2eTestUtils.assertProgramCorrect(
            """
            czynność główna() {}
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
        E2eTestUtils.assertProgramCorrect(
            """
            czynność główna() {}
            czynność dopóki_pomiń_przerwij() {
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
        E2eTestUtils.assertProgramCorrect(
            """
            czynność główna() {}
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
        E2eTestUtils.assertProgramCorrect(
            """
            czynność główna() {}
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
        E2eTestUtils.assertProgramCorrect(
            """
            czynność główna() {}
            czynność wierzchnia() {
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
        E2eTestUtils.assertProgramCorrect(
            """
            czynność główna() {}
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
        E2eTestUtils.assertProgramCorrect(
            """
            czynność główna() {}
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

    @Test
    fun `test redefining builtin function`() {
        E2eTestUtils.assertProgramCorrect(
            """
            czynność napisz(wartość: Liczba = 0) {
                zewnętrzna czynność print_int64(wartość: Liczba) jako napisz
                napisz(-wartość)
            }
            
            czynność główna() {
                napisz() // wypisze 0
                napisz(4) // wypisze -4
            }
            """
        )
    }

    @Test
    fun `test generators`() {
        E2eTestUtils.assertProgramCorrect(
            """
            czynność główna() {
                przekaźnik mój_generator(a: Liczba = 17) -> Liczba {
                    zm x: Liczba = 1
                    dopóki (x <= a) {
                        przekaż x
                
                        x = x + 1
                
                        jeśli (x % 2 == 0)
                            przekaż (100 + x/2)
                    }
                    zakończ // możliwa tylko forma bez wartości
                }
                
                otrzymując x: Liczba od mój_generator(25) { 
                    jeśli (x == 10)
                        pomiń
            
                    napisz(x)
            
                    jeśli (x == 20)
                        przerwij
                }
            }
            """,
        )
    }

    @Test
    fun `test arrays list initialization`() {
        E2eTestUtils.assertProgramCorrect(
            """
            czynność główna() {
                zm x: [Liczba] = ciąg Liczba{1,2,4,8,16} 
                
                x[2] = 13
                
                napisz(długość x) // 5
                napisz(x[0]) // 1
                napisz(x[2]) // 13
                napisz(x[3]) // 8
            }   
            """
        )
    }

    @Test
    fun `test arrays default value initialization`() {
        E2eTestUtils.assertProgramCorrect(
            """
            czynność główna() {
                zm x: [Liczba] = ciąg Liczba[11](1) 
                zm y: [Liczba] = x
                
                x[3] = 4
                x[2] = y[3]
                
                napisz(długość x) // 11
                napisz(długość y) // 11
                napisz(x[0]) // 1
                napisz(y[10]) // 1
                napisz(x[2]) // 4
            }   
            """
        )
    }

    @Test
    fun `test arrays`() {
        E2eTestUtils.assertProgramCorrect(
            """
            czynność jedynki(n: Liczba) -> [Liczba] {
                zwróć ciąg Liczba[n](1)
            }
            czynność główna() {
                zm x: [[Liczba]] = ciąg [Liczba] {
                    ciąg Liczba[3](0),
                    ciąg Liczba{1, 2},
                    ciąg Liczba{3, 4, 5, 6}
                }
                
                zm y: [Liczba] = x[0]
                y[1] = 17
                
                napisz(długość x) // 3
                napisz(długość x[2]) // 4
                napisz(x[0][1]) // 17
                napisz(x[2][x[1][0]]) // 4
                napisz(długość jedynki(3)) // 3
                napisz(jedynki(2)[1]) // 1
            }   
            """
        )
    }

    @Test
    fun `test looping over arrays`() {
        E2eTestUtils.assertProgramCorrect(
            """
            czynność główna() {
                zm tab: [Liczba] = ciąg Liczba {0, 1, 1, 2, 3, 5, 8}
                dla (x: Liczba wewnątrz tab) {
                    napisz(x)
                }
            }
            """
        )
    }
}
