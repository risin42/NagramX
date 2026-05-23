package xyz.nextalone.nagram

enum class ScheduleTimeShift(val minutes: Int, val label: String) {
    MINUTES_5(5, "5 min"),
    MINUTES_10(10, "10 min"),
    MINUTES_15(15, "15 min"),
    MINUTES_30(30, "30 min"),
    HOURS_1(60, "1 hour"),
    HOURS_2(120, "2 hours"),
    HOURS_4(240, "4 hours"),
    HOURS_8(480, "8 hours"),
    DAYS_1(1440, "1 day"),
    DAYS_2(2880, "2 days"),
    DAYS_3(4320, "3 days"),
    DAYS_7(10080, "7 days");

    val seconds: Int get() = minutes * 60

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


