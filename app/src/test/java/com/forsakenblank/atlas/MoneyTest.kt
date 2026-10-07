package com.forsakenblank.atlas

import com.forsakenblank.atlas.data.MoneyEntry
import com.forsakenblank.atlas.util.balanceSeries
import com.forsakenblank.atlas.util.categoryTotals
import com.forsakenblank.atlas.util.monthlyTotals
import com.forsakenblank.atlas.util.parsePence
import com.forsakenblank.atlas.util.pounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class MoneyTest {

    private fun entry(day: LocalDate, pence: Long, category: String? = null, transfer: Long? = null) =
        MoneyEntry(accountId = 1, day = day.toEpochDay(), amountPence = pence, category = category, transferId = transfer)

    @Test
    fun amountsParseToPence() {
        assertEquals(1250L, parsePence("12.50"))
        assertEquals(120_000L, parsePence("£1,200"))
        assertEquals(5L, parsePence("0.05"))
        assertNull(parsePence("abc"))
        assertNull(parsePence("-4"))
        assertEquals(-4000L, parsePence("-40", allowNegative = true))
    }

    @Test
    fun poundsAreFormatted() {
        assertEquals("£1,234.50", pounds(123_450))
        assertEquals("-£3.00", pounds(-300))
    }

    @Test
    fun transfersStayOutOfMonthlyTotals() {
        val today = LocalDate.of(2026, 10, 7)
        val entries = listOf(
            entry(LocalDate.of(2026, 10, 1), 200_000),
            entry(LocalDate.of(2026, 10, 3), -5_000, "Food"),
            entry(LocalDate.of(2026, 10, 4), -10_000, transfer = 9),
            entry(LocalDate.of(2026, 10, 4), 10_000, transfer = 9),
            entry(LocalDate.of(2026, 8, 20), -2_500, "Fun"),
        )
        val totals = monthlyTotals(entries, 3, today)
        assertEquals(3, totals.size)
        assertEquals(2_500L, totals[0].spent) // august, a past entry lands in its own month
        assertEquals(0L, totals[1].earned)
        assertEquals(200_000L, totals[2].earned)
        assertEquals(5_000L, totals[2].spent)
    }

    @Test
    fun categoriesAreSortedBiggestFirst() {
        val day = LocalDate.of(2026, 10, 1)
        val list = categoryTotals(
            listOf(entry(day, -300, "Food"), entry(day, -900, "Bills"), entry(day, -200, "Food"), entry(day, 5000, "Wages")),
            fromDay = 0,
        )
        assertEquals(listOf("Bills" to 900L, "Food" to 500L), list)
    }

    @Test
    fun balanceCountsEarlierEntries() {
        val d = LocalDate.of(2026, 10, 1)
        val entries = listOf(entry(d.minusDays(5), 1_000), entry(d, -300), entry(d.plusDays(1), 500))
        val series = balanceSeries(10_000, entries, d.toEpochDay(), d.plusDays(2).toEpochDay())
        assertEquals(listOf(10_700L, 11_200L, 11_200L), series.map { it.second })
    }
}
