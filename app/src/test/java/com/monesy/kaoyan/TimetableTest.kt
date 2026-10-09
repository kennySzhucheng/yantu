package com.monesy.kaoyan

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TimetableTest {
    private val originalStart = Timetable.SEMESTER_START

    @After
    fun resetTimetable() {
        Timetable.init(JSONObject().put("semesterStart", originalStart.toString()))
    }

    @Test
    fun datesBeforeFirstWeekDoNotShowFirstWeekClasses() {
        val start = LocalDate.of(2026, 8, 31)
        Timetable.init(JSONObject("""
            {
              "semesterStart": "$start",
              "entries": {
                "SUNDAY": [{"slot": 0, "name": "测试课程", "room": "", "weeks": "1"}]
              }
            }
        """.trimIndent()))

        for (daysBefore in 1L..7L) {
            assertEquals(0, Timetable.weekOf(start.minusDays(daysBefore)))
        }
        assertTrue(Timetable.classesFor(start.minusDays(1)).isEmpty())
        assertEquals(1, Timetable.classesFor(start.plusDays(6)).size)
        assertEquals(1, Timetable.weekOf(start))
        assertEquals(2, Timetable.weekOf(start.plusWeeks(1)))
        assertEquals(-1, Timetable.weekOf(start.minusDays(8)))
    }

    @Test
    fun nonMondayStartUsesMondayToSundayTeachingWeeks() {
        val monday = LocalDate.of(2026, 8, 31)
        for (startDay in 0L..6L) {
            Timetable.init(JSONObject().put("semesterStart", monday.plusDays(startDay).toString()))
            assertEquals(0, Timetable.weekOf(monday.minusDays(1)))
            for (day in 0L..6L) {
                assertEquals(1, Timetable.weekOf(monday.plusDays(day)))
            }
            assertEquals(2, Timetable.weekOf(monday.plusWeeks(1)))
        }
    }

    @Test
    fun teachingWeeksContinueAcrossYearBoundary() {
        Timetable.init(JSONObject().put("semesterStart", "2026-12-30"))
        assertEquals(1, Timetable.weekOf(LocalDate.of(2027, 1, 3)))
        assertEquals(2, Timetable.weekOf(LocalDate.of(2027, 1, 4)))
    }
}
