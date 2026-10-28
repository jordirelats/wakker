package cat.jordi.wakker.dades

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Els períodes de l'Historial són de calendari (maqueta 10): un dia, una setmana de dilluns a diumenge, un mes de l'1 a
 * l'últim i un any de gener a desembre. Un període es diu pel dia que hi cau; les fletxes van al de davant o al de darrere.
 */
fun iniciPeriode(p: Periode, dia: LocalDate): LocalDate = when (p) {
    Periode.DIA -> dia
    Periode.SETMANA -> dia.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    Periode.MES -> dia.withDayOfMonth(1)
    Periode.ANY -> dia.withDayOfYear(1)
}

/** El primer dia del període següent (el final, sense incloure'l). */
fun fiPeriode(p: Periode, dia: LocalDate): LocalDate = mou(p, iniciPeriode(p, dia), 1)

/** El mateix dia [n] períodes més endavant (o enrere, si és negatiu). */
fun mou(p: Periode, dia: LocalDate, n: Long): LocalDate = when (p) {
    Periode.DIA -> dia.plusDays(n)
    Periode.SETMANA -> dia.plusWeeks(n)
    Periode.MES -> dia.plusMonths(n)
    Periode.ANY -> dia.plusYears(n)
}

/** Quants períodes enrere és el de [dia] respecte del d'[avui]: 0 si és el d'ara. */
fun periodesEnrere(p: Periode, dia: LocalDate, avui: LocalDate): Long {
    val a = iniciPeriode(p, dia)
    val b = iniciPeriode(p, avui)
    return when (p) {
        Periode.DIA -> ChronoUnit.DAYS.between(a, b)
        Periode.SETMANA -> ChronoUnit.WEEKS.between(a, b)
        Periode.MES -> ChronoUnit.MONTHS.between(a, b)
        Periode.ANY -> ChronoUnit.YEARS.between(a, b)
    }
}
