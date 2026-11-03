package io.github.mikhasrahulia.pixelclock.alarm

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.AlarmClock
import io.github.mikhasrahulia.pixelclock.MainActivity
import io.github.mikhasrahulia.pixelclock.data.AlarmModel
import io.github.mikhasrahulia.pixelclock.data.AlarmRepository
import java.time.LocalDate
import java.util.Calendar

/**
 * Handles the standard [AlarmClock.ACTION_SET_ALARM] intent, so voice assistants and other apps
 * can create alarms here. It has no UI of its own.
 *
 * Standard extras: hour, minutes, message, days (Calendar.SUNDAY..SATURDAY), vibrate, skip UI.
 *
 * Optional extras that only this app understands:
 *  - [EXTRA_DATE]: ISO date (YYYY-MM-DD), the alarm rings once on that day.
 *  - [EXTRA_ACTIVE_WEEK_OF]: ISO date inside the week that should ring; makes the alarm repeat
 *    every 2 weeks on its days, starting with that week.
 *
 * Creating the same alarm twice is a no-op.
 */
class SetAlarmActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val created = runCatching { handle(intent) }.getOrDefault(false)
        if (created && !intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, false)) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        finish()
    }

    private fun handle(intent: Intent): Boolean {
        if (intent.action != AlarmClock.ACTION_SET_ALARM) return false
        if (!intent.hasExtra(AlarmClock.EXTRA_HOUR)) return false

        val hour = intent.getIntExtra(AlarmClock.EXTRA_HOUR, -1)
        val minute = intent.getIntExtra(AlarmClock.EXTRA_MINUTES, 0)
        if (hour !in 0..23 || minute !in 0..59) return false

        val days = intent.getIntegerArrayListExtra(AlarmClock.EXTRA_DAYS)
            .orEmpty()
            .mapNotNull(::calendarDayToIso)
            .toSet()
        val label = intent.getStringExtra(AlarmClock.EXTRA_MESSAGE).orEmpty().trim()
        val vibrate = intent.getBooleanExtra(AlarmClock.EXTRA_VIBRATE, true)
        val date = parseDate(intent.getStringExtra(EXTRA_DATE))
        val activeWeek = parseDate(intent.getStringExtra(EXTRA_ACTIVE_WEEK_OF))

        val specificDate = when {
            date != null -> date
            days.isEmpty() -> AlarmModel.oneTimeDate(hour, minute)
            else -> null
        }

        val candidate = AlarmModel(
            hour = hour,
            minute = minute,
            label = label,
            daysOfWeek = if (date != null) emptySet() else days,
            specificDate = specificDate?.toString(),
            isBiWeekly = activeWeek != null && days.isNotEmpty() && date == null,
            weekParity = activeWeek?.let { AlarmModel.getWeekParity(it) } ?: AlarmModel.currentWeekParity(),
            vibrate = vibrate
        )

        val repository = AlarmRepository.getInstance(this)
        val duplicate = repository.alarms.value.any {
            it.hour == candidate.hour &&
                it.minute == candidate.minute &&
                it.label == candidate.label &&
                it.daysOfWeek == candidate.daysOfWeek &&
                it.isBiWeekly == candidate.isBiWeekly &&
                (candidate.specificDate == null || it.specificDate == candidate.specificDate)
        }
        if (duplicate) return true

        repository.addAlarm(candidate)
        AlarmScheduler.getInstance(this).scheduleAlarm(candidate)
        return true
    }

    /** Calendar: SUNDAY = 1 … SATURDAY = 7. App: Monday = 1 … Sunday = 7. */
    private fun calendarDayToIso(day: Int): Int? = when (day) {
        Calendar.SUNDAY -> 7
        in Calendar.MONDAY..Calendar.SATURDAY -> day - 1
        else -> null
    }

    private fun parseDate(value: String?): LocalDate? =
        value?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    companion object {
        const val EXTRA_DATE = "io.github.mikhasrahulia.pixelclock.extra.DATE"
        const val EXTRA_ACTIVE_WEEK_OF = "io.github.mikhasrahulia.pixelclock.extra.ACTIVE_WEEK_OF"
    }
}
