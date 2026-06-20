package com.base.aihelperwearos.data.models

import com.base.aihelperwearos.data.repository.ChatSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpecializedChatRegistryTest {

    @Test
    fun `home modes include enabled modes only`() {
        val homeModeIds = SpecializedChatRegistry.homeModes().map { it.id }

        assertTrue(homeModeIds.contains(ChatModeIds.GENERAL))
        assertTrue(homeModeIds.contains(ChatModeIds.PHYSICS))
        assertFalse(homeModeIds.contains(ChatModeIds.ANALYSIS2))
        assertFalse(homeModeIds.contains(ChatModeIds.METODI_THEORY))
        assertFalse(homeModeIds.contains(ChatModeIds.METODI_CODE))
        assertTrue(SpecializedChatRegistry.homeModes().all { it.enabled })
    }

    @Test
    fun `legacy analysis session maps to analysis2 mode id`() {
        val session = ChatSession(
            id = 1L,
            modelId = "test",
            title = "legacy",
            timestamp = 0L,
            isAnalysisMode = true,
            mode = ChatMode.GENERAL
        )

        assertEquals(ChatModeIds.ANALYSIS2, session.effectiveModeId())
    }

    @Test
    fun `unknown mode id resolves to general spec without losing stored id`() {
        val session = ChatSession(
            id = 1L,
            modelId = "test",
            title = "future",
            timestamp = 0L,
            modeId = "future_mode"
        )

        assertEquals("future_mode", session.effectiveModeId())
        assertEquals(ChatModeIds.GENERAL, SpecializedChatRegistry.get(session.effectiveModeId()).id)
    }
}
