package com.monesy.kaoyan

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotifyTest {

    @Test
    fun recurringReminderKindsContinueTheirChains() {
        assertTrue(Notify.isRecurringKind(Notify.KIND_MORNING))
        assertTrue(Notify.isRecurringKind(Notify.KIND_EVENING))
        assertTrue(Notify.isRecurringKind(Notify.KIND_BOTTOMLINE))
        assertTrue(Notify.isRecurringKind(Notify.KIND_WEEKLY))
    }

    @Test
    fun oneOffNodeReminderDoesNotContinueAChain() {
        assertFalse(Notify.isRecurringKind(Notify.KIND_NODE))
    }

    @Test
    fun unknownReminderKindDoesNotContinueAChain() {
        assertFalse(Notify.isRecurringKind("unknown"))
    }
}
