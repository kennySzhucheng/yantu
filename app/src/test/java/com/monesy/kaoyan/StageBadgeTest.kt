package com.monesy.kaoyan

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 阶段序号徽标回归：计划编辑器允许新增阶段，id 会超过 6；
 * 历史实现 `"①②③④⑤⑥"[id - 1]` 在 id≥7 时越界崩溃。
 */
class StageBadgeTest {

    @Test
    fun defaultPlanStagesUseCircledNumbers() {
        assertEquals("①", stageBadge(1))
        assertEquals("⑥", stageBadge(6))
    }

    @Test
    fun extraStagesUseCircledNumbersInsteadOfCrashing() {
        assertEquals("⑦", stageBadge(7))
        assertEquals("⑳", stageBadge(20))
    }

    @Test
    fun outOfRangeFallsBackToPlainNumber() {
        assertEquals("(0)", stageBadge(0))
        assertEquals("(21)", stageBadge(21))
    }
}
