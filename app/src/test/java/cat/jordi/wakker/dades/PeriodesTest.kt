package cat.jordi.wakker.dades

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PeriodesTest {
    private val dc21 = LocalDate.of(2026, 10, 21)

    @Test
    fun iniciIFi() {
        assertEquals(dc21, iniciPeriode(Periode.DIA, dc21))
        assertEquals(LocalDate.of(2026, 10, 19), iniciPeriode(Periode.SETMANA, dc21))
        assertEquals(LocalDate.of(2026, 10, 26), fiPeriode(Periode.SETMANA, dc21))
        // El diumenge és de la setmana que comença el dilluns d'abans
        assertEquals(LocalDate.of(2026, 10, 19), iniciPeriode(Periode.SETMANA, LocalDate.of(2026, 10, 25)))
        assertEquals(LocalDate.of(2026, 10, 1), iniciPeriode(Periode.MES, dc21))
        assertEquals(LocalDate.of(2026, 11, 1), fiPeriode(Periode.MES, dc21))
        assertEquals(LocalDate.of(2026, 1, 1), iniciPeriode(Periode.ANY, dc21))
        assertEquals(LocalDate.of(2027, 1, 1), fiPeriode(Periode.ANY, dc21))
    }

    @Test
    fun quantsEnrere() {
        val avui = LocalDate.of(2026, 10, 26)
        assertEquals(5, periodesEnrere(Periode.DIA, dc21, avui))
        assertEquals(1, periodesEnrere(Periode.SETMANA, dc21, avui))
        assertEquals(0, periodesEnrere(Periode.MES, dc21, avui))
        assertEquals(3, periodesEnrere(Periode.SETMANA, mou(Periode.SETMANA, avui, -3), avui))
        assertEquals(2, periodesEnrere(Periode.ANY, LocalDate.of(2024, 12, 31), avui))
        // El 31 de gener menys un mes és el 31 de desembre, i un mes després, el 31 de gener
        assertEquals(LocalDate.of(2025, 12, 31), mou(Periode.MES, LocalDate.of(2026, 1, 31), -1))
    }
}
