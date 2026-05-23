package xyz.nextalone.nagram

import org.telegram.messenger.LocaleController
import org.telegram.messenger.R

enum class ScheduleTimeShift(val minutes: Int, val labelResId: Int) {
    MINUTES_5(5, R.string.ScheduleTime5Min),
    MINUTES_10(10, R.string.ScheduleTime10Min),
    MINUTES_15(15, R.string.ScheduleTime15Min),
    MINUTES_30(30, R.string.ScheduleTime30Min),
    HOURS_1(60, R.string.ScheduleTime1Hour),
    HOURS_2(120, R.string.ScheduleTime2Hours),
    HOURS_4(240, R.string.ScheduleTime4Hours),
    HOURS_8(480, R.string.ScheduleTime8Hours),
    DAYS_1(1440, R.string.ScheduleTime1Day),
    DAYS_2(2880, R.string.ScheduleTime2Days),
    DAYS_3(4320, R.string.ScheduleTime3Days),
    DAYS_7(10080, R.string.ScheduleTime7Days);

    val seconds: Int get() = minutes * 60

    val label: String get() = LocaleController.getString(labelResId)

    companion object {
        @JvmStatic
        fun labels(): Array<String> = entries.map { it.label }.toTypedArray()

        @JvmStatic
        fun minuteValues(): IntArray = entries.map { it.minutes }.toIntArray()

        @JvmStatic
        fun fromMinutes(minutes: Int): ScheduleTimeShift =
            entries.firstOrNull { it.minutes == minutes } ?: MINUTES_10
    }
}


