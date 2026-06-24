package com.base.aihelperwearos.data.models

import com.base.aihelperwearos.data.Constants
import com.base.aihelperwearos.data.repository.ChatSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpecializedChatRegistryTest {

    @Test
    fun `home modes include enabled modes only`() {
        val homeModeIds = SpecializedChatRegistry.homeModes().map { it.id }

        assertEquals(
            listOf(
                ChatModeIds.ANALYSIS,
                ChatModeIds.SOFTWARE_ENGINEERING,
                ChatModeIds.GENERAL
            ),
            homeModeIds
        )
        assertFalse(homeModeIds.contains(ChatModeIds.PHYSICS))
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

        assertEquals(ChatModeIds.ANALYSIS, session.effectiveModeId())
    }

    @Test
    fun `analysis aliases normalize to persisted analysis id`() {
        assertEquals(ChatModeIds.ANALYSIS, SpecializedChatRegistry.normalizeModeId("analysis1"))
        assertEquals(ChatModeIds.ANALYSIS, SpecializedChatRegistry.normalizeModeId("analisi1"))
        assertEquals(ChatModeIds.ANALYSIS, SpecializedChatRegistry.normalizeModeId("analysis2"))
        assertEquals(ChatModeIds.ANALYSIS, SpecializedChatRegistry.normalizeModeId("analisi2"))
    }

    @Test
    fun `software engineering aliases normalize to software engineering id`() {
        assertEquals(ChatModeIds.SOFTWARE_ENGINEERING, SpecializedChatRegistry.normalizeModeId("software"))
        assertEquals(ChatModeIds.SOFTWARE_ENGINEERING, SpecializedChatRegistry.normalizeModeId("ingegneria_software"))
        assertEquals(ChatModeIds.SOFTWARE_ENGINEERING, SpecializedChatRegistry.normalizeModeId("ingsoft"))
        assertEquals(ChatModeIds.SOFTWARE_ENGINEERING, SpecializedChatRegistry.normalizeModeId("ids"))
    }

    @Test
    fun `software engineering prompt covers exam sections without math latex policy`() {
        val prompt = Constants.getPrompt(
            profile = PromptProfile.SOFTWARE_ENGINEERING,
            languageCode = "it",
            ragContext = "Esempio stream del professore"
        )

        assertNotNull(prompt)
        val text = prompt.orEmpty()
        assertTrue(text.contains("Dieci domande a risposta multipla"))
        assertTrue(text.contains("batch da 2, 3, 4 o 5"))
        assertTrue(text.contains("batch fino a 4"))
        assertTrue(text.contains("Il problema sui design pattern arriva da solo"))
        assertTrue(text.contains("Stream API"))
        assertTrue(text.contains("design pattern"))
        assertTrue(text.contains("diagramma UML"))
        assertFalse(text.contains("Ogni passaggio matematico"))
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
