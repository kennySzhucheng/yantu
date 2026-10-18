package com.monesy.kaoyan

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 局部更新配置时必须保留未提交的字段。
 * 回归背景：v1.6.0「修改考试信息」用只含 4 个键的 JSON 整段覆盖 app 段，
 * 导致院校 / 专业 / 科目 / 关于页被清空。
 */
class ConfigMergeTest {

    @Test
    fun patchKeepsFieldsThatWereNotSubmitted() {
        val base = JSONObject()
            .put("schoolFrom", "某大学")
            .put("schoolTo", "某学院")
            .put("subjects", "数学二,英语二")
            .put("examDate", "2027-12-18")
        val patch = JSONObject()
            .put("examDate", "2027-12-19")
            .put("countdownLabel", "距 27 考研初试")

        val merged = UserConfig.merge(base, patch)

        assertEquals("2027-12-19", merged.getString("examDate"))
        assertEquals("距 27 考研初试", merged.getString("countdownLabel"))
        assertEquals("某大学", merged.getString("schoolFrom"))
        assertEquals("某学院", merged.getString("schoolTo"))
        assertEquals("数学二,英语二", merged.getString("subjects"))
    }

    @Test
    fun patchStartsFromEmptySectionWithoutCrashing() {
        val merged = UserConfig.merge(JSONObject(), JSONObject().put("examDate", "2027-12-19"))
        assertEquals("2027-12-19", merged.getString("examDate"))
        assertTrue(merged.length() == 1)
    }
}
