package com.jiexian.app.data

import androidx.room.TypeConverter
import com.jiexian.app.model.AppTarget
import org.json.JSONArray
import org.json.JSONObject

class RuleTargetsConverter {
    @TypeConverter
    fun fromTargets(targets: List<AppTarget>): String =
        JSONArray()
            .apply {
                targets.forEach { target ->
                    put(
                        JSONObject()
                            .put("packageName", target.packageName)
                            .put("appName", target.appName),
                    )
                }
            }.toString()

    @TypeConverter
    fun toTargets(value: String): List<AppTarget> {
        if (value.isBlank()) return emptyList()
        val json = JSONArray(value)
        return buildList {
            for (index in 0 until json.length()) {
                val item = json.getJSONObject(index)
                add(
                    AppTarget(
                        packageName = item.getString("packageName"),
                        appName = item.getString("appName"),
                    ),
                )
            }
        }
    }
}
