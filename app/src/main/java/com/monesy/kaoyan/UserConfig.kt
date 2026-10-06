package com.monesy.kaoyan

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

/**
 * 用户配置层：存于 DataStore 的一份 JSON `{app/plan/timetable/holidays}`（只存用户改过的段）。
 * 配置优先级：用户层 > assets 内置 > 代码默认。
 * 向导、编辑、导入全部写这一层；"恢复内置" = 清空这一层。
 */
object UserConfig {

    const val SECTION_APP = "app"
    const val SECTION_PLAN = "plan"
    const val SECTION_TIMETABLE = "timetable"
    const val SECTION_HOLIDAYS = "holidays"

    /** 读取用户层全部配置（同步，供启动时 ConfigLoader 使用） */
    fun readAll(store: Store): JSONObject = runBlocking {
        val raw = store.userConfigRaw.first() ?: return@runBlocking JSONObject()
        runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
    }

    /** 读取单个段（导出/编辑用） */
    suspend fun readSection(store: Store, section: String): JSONObject? = runBlocking {
        runCatching { JSONObject(store.userConfigRaw.first() ?: "{}") }.getOrNull()
            ?.optJSONObject(section)
    }

    /** 写入/覆盖单个段 */
    suspend fun putSection(store: Store, section: String, json: JSONObject) {
        val cur = runCatching { JSONObject(store.userConfigRaw.first() ?: "{}") }.getOrElse { JSONObject() }
        cur.put(section, json)
        store.setUserConfigRaw(cur.toString())
    }

    /** 整份替换（导入配置时用） */
    suspend fun putWhole(store: Store, json: JSONObject) = store.setUserConfigRaw(json.toString())

    /** 移除单个段（如"应用内置示例计划" = 移除 plan 段回落到内置） */
    suspend fun removeSection(store: Store, section: String) {
        val cur = runCatching { JSONObject(store.userConfigRaw.first() ?: "{}") }.getOrElse { JSONObject() }
        cur.remove(section)
        store.setUserConfigRaw(cur.toString())
    }

    suspend fun clear(store: Store) = store.clearUserConfig()

    /** 浅层合并：override 的同名字段覆盖 base（数组/对象整体替换） */
    fun merge(base: JSONObject, override: JSONObject?): JSONObject {
        if (override == null) return base
        val out = JSONObject(base.toString())
        for (key in override.keys()) out.put(key, override.get(key))
        return out
    }

    /** 合并导出：内置 + 用户层，四个段合成一份完整配置 */
    fun exportMerged(context: android.content.Context, store: Store): JSONObject {
        val user = readAll(store)
        val out = JSONObject()
        for (section in listOf(SECTION_APP, SECTION_PLAN, SECTION_TIMETABLE, SECTION_HOLIDAYS)) {
            val assetPath = "config/$section.json"
            val base = runCatching {
                JSONObject(context.assets.open(assetPath).bufferedReader().use { it.readText() })
            }.getOrElse { JSONObject() }
            out.put(section, merge(base, user.optJSONObject(section)))
        }
        return out
    }
}
