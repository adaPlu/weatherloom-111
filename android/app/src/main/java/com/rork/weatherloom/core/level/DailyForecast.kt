package com.rork.weatherloom.core.level

import com.rork.weatherloom.core.sim.Cmp
import com.rork.weatherloom.core.sim.Metric
import com.rork.weatherloom.core.sim.ObjectiveSpec
import com.rork.weatherloom.core.sim.ThreadType
import java.util.Calendar
import java.util.Locale

/**
 * Builds the day's puzzle from a validated template plus deterministic mutations,
 * so every player on a given date gets the same board and it is always solvable.
 * Generation is local — the daily works with no network at all.
 */
object DailyForecast {

    /** Stable templates: levels whose canonical solution is known to pass validation. */
    private val templateIds = listOf("c1-1", "c1-3", "c2-2", "c3-1", "c4-1", "c5-1", "c6-1")

    fun dayKey(offsetDays: Int = 0): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -offsetDays)
        return String.format(
            Locale.US,
            "%04d-%02d-%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun recentKeys(count: Int): List<String> = (1..count).map { dayKey(it) }

    fun prettyDate(key: String): String {
        val parts = key.split("-")
        if (parts.size != 3) return key
        val months = listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )
        val m = parts[1].toIntOrNull()?.minus(1)?.coerceIn(0, 11) ?: 0
        return "${months[m]} ${parts[2].toInt()}"
    }

    /**
     * Deterministic per-day mutation of a validated template. Mutations only ever
     * loosen a target or trim the thread budget by one where a spare exists, so the
     * template's canonical solution keeps working.
     */
    fun forDay(key: String): Level? {
        val seed = key.fold(0L) { acc, c -> acc * 131 + c.code }
        val template = LevelLibrary.level(templateIds[(seed % templateIds.size).toInt().coerceAtLeast(0)])
            ?: return null

        val variant = ((seed / 7) % 3).toInt()
        val objectives = template.objectives.map { spec ->
            when {
                variant == 1 && spec.cmp == Cmp.Gte && spec.target > 2 && spec.metric != Metric.WindmillTicks ->
                    spec.copy(target = spec.target - 1)

                variant == 2 && spec.cmp == Cmp.Gte && spec.metric == Metric.WindmillTicks ->
                    spec.copy(target = spec.target + 5)

                else -> spec
            }
        }
        val threads: Map<ThreadType, Int> = if (variant == 2) {
            template.threads.mapValues { (_, n) -> n + 1 }
        } else {
            template.threads
        }

        return template.copy(
            id = "daily-$key",
            name = "Forecast for ${prettyDate(key)}",
            brief = template.brief,
            objectives = objectives,
            threads = threads,
            reward = null,
            chapter = 0
        )
    }

    fun blurb(variant: Int): String = when (variant) {
        1 -> "A gentler sky today."
        2 -> "One extra thread, one stubborn objective."
        else -> "The board as the loom first wove it."
    }
}
