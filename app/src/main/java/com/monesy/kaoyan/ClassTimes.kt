package com.monesy.kaoyan

import org.json.JSONArray

/**
 * 作息时间（来源：assets/config/app.json 的 bells 字段）。
 * 支持按教学楼特判：同一节次在指定教学楼（roomPrefix）有不同上下课时间。
 */
object ClassTimes {

    data class Bell(val start: String, val end: String)

    private class BellDef(
        val start: String,
        val end: String,
        val overrides: List<Pair<String, Bell>>,
    )

    /** 本学期教学周总数（第 N/18 教学周） */
    var SEMESTER_WEEKS: Int = 18
        private set

    private var bells: Map<Int, BellDef> = emptyMap()

    fun init(arr: JSONArray?) {
        SEMESTER_WEEKS = Config.semesterWeeks
        if (arr == null) return
        val map = mutableMapOf<Int, BellDef>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val overrides = mutableListOf<Pair<String, Bell>>()
            o.optJSONArray("overrides")?.let { ov ->
                for (j in 0 until ov.length()) {
                    val v = ov.optJSONObject(j) ?: continue
                    overrides += v.optString("roomPrefix") to Bell(v.optString("start"), v.optString("end"))
                }
            }
            map[o.optInt("slot")] = BellDef(o.optString("start"), o.optString("end"), overrides)
        }
        bells = map
    }

    /** 返回该节课的上下课时间；未配置的节次返回 null。 */
    fun bellFor(slot: Int, room: String): Bell? {
        val def = bells[slot] ?: return null
        val override = def.overrides.firstOrNull { room.startsWith(it.first) }
        return override?.second ?: Bell(def.start, def.end)
    }
}
