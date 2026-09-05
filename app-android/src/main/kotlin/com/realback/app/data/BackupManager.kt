package com.realback.app.data

import androidx.room.withTransaction
import com.realback.app.data.db.AppLimitRuleEntity
import com.realback.app.data.db.CheckmarkEntity
import com.realback.app.data.db.GrowthPointsEntity
import com.realback.app.data.db.HabitEntity
import com.realback.app.data.db.RealBackDatabase
import org.json.JSONArray
import org.json.JSONObject

/**
 * M5 backup: versioned JSON export/import of user data.
 *
 * Scope: habits, checkmarks, limit rules, growth points. Daily usage
 * aggregates are intentionally excluded (bulky, reproducible from the
 * system, and useless on another device).
 */
object BackupManager {

    private const val FORMAT_VERSION = 1

    suspend fun export(db: RealBackDatabase): String {
        val root = JSONObject()
        root.put("version", FORMAT_VERSION)
        root.put("app", "com.realback.app")

        val habits = JSONArray()
        db.habitDao().all().forEach { h ->
            habits.put(
                JSONObject()
                    .put("id", h.id)
                    .put("name", h.name)
                    .put("description", h.description)
                    .put("scheduleType", h.scheduleType)
                    .put("scheduleParam", h.scheduleParam)
                    .put("archived", h.archived)
                    .put("createdAtEpochDay", h.createdAtEpochDay),
            )
        }
        root.put("habits", habits)

        val checkmarks = JSONArray()
        db.habitDao().allCheckmarks().forEach { c ->
            checkmarks.put(
                JSONObject()
                    .put("habitId", c.habitId)
                    .put("epochDay", c.epochDay)
                    .put("value", c.value),
            )
        }
        root.put("checkmarks", checkmarks)

        val rules = JSONArray()
        db.limitRuleDao().all().forEach { r ->
            rules.put(
                JSONObject()
                    .put("packageName", r.packageName)
                    .put("dailyLimitMillis", r.dailyLimitMillis)
                    .put("interventionEnabled", r.interventionEnabled),
            )
        }
        root.put("limitRules", rules)

        val growth = JSONArray()
        db.growthDao().allGrowthPoints().forEach { g ->
            growth.put(
                JSONObject()
                    .put("epochDay", g.epochDay)
                    .put("points", g.points),
            )
        }
        root.put("growthPoints", growth)

        return root.toString(2)
    }

    /**
     * Replaces ALL current data with the backup's content (atomic).
     * @throws BackupFormatException on unknown version or malformed JSON.
     */
    suspend fun import(db: RealBackDatabase, json: String) {
        val root = try {
            JSONObject(json)
        } catch (e: org.json.JSONException) {
            throw BackupFormatException("not valid JSON", e)
        }
        val version = root.optInt("version", -1)
        if (version != FORMAT_VERSION) {
            throw BackupFormatException("unsupported backup version: $version")
        }

        val habits = parseArray(root.optJSONArray("habits")) { it.toHabit() }
        val checkmarks = parseArray(root.optJSONArray("checkmarks")) { it.toCheckmark() }
        val rules = parseArray(root.optJSONArray("limitRules")) { it.toRule() }
        val growth = parseArray(root.optJSONArray("growthPoints")) { it.toGrowth() }

        db.withTransaction {
            db.habitDao().clearHabits()
            db.habitDao().clearCheckmarks()
            db.limitRuleDao().clearRules()
            db.growthDao().clearGrowth()
            habits.forEach { db.habitDao().upsert(it) }
            db.habitDao().insertAllCheckmarks(checkmarks)
            db.limitRuleDao().insertAllRules(rules)
            db.growthDao().insertAllGrowthPoints(growth)
        }
    }

    private fun <T> parseArray(
        array: JSONArray?,
        mapper: (JSONObject) -> T,
    ): List<T> {
        if (array == null) return emptyList()
        return (0 until array.length()).map { i ->
            mapper(array.getJSONObject(i))
        }
    }

    private fun JSONObject.toHabit() = HabitEntity(
        id = getLong("id"),
        name = getString("name"),
        description = optString("description", ""),
        scheduleType = getString("scheduleType"),
        scheduleParam = optInt("scheduleParam", 7),
        archived = optBoolean("archived", false),
        createdAtEpochDay = getLong("createdAtEpochDay"),
    )

    private fun JSONObject.toCheckmark() = CheckmarkEntity(
        habitId = getLong("habitId"),
        epochDay = getLong("epochDay"),
        value = getBoolean("value"),
    )

    private fun JSONObject.toRule() = AppLimitRuleEntity(
        packageName = getString("packageName"),
        dailyLimitMillis = getLong("dailyLimitMillis"),
        interventionEnabled = optBoolean("interventionEnabled", true),
    )

    private fun JSONObject.toGrowth() = GrowthPointsEntity(
        epochDay = getLong("epochDay"),
        points = getDouble("points"),
    )

    class BackupFormatException(message: String, cause: Throwable? = null) :
        Exception(message, cause)
}
