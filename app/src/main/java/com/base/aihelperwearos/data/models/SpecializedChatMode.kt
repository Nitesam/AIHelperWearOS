package com.base.aihelperwearos.data.models

import com.base.aihelperwearos.BuildConfig
import com.base.aihelperwearos.R

object ChatModeIds {
    const val GENERAL = "general"
    const val ANALYSIS2 = "analysis2"
    const val PHYSICS = "physics"
    const val METODI_THEORY = "metodi_theory"
    const val METODI_CODE = "metodi_code"
}

enum class PromptProfile {
    NONE,
    ANALYSIS2,
    PHYSICS,
    METODI_THEORY,
    METODI_CODE
}

enum class ContextToolType {
    EXERCISE_ANALYSIS2,
    EXERCISE_PHYSICS,
    METODI_THEORY,
    METODI_CODE
}

data class ChatModeSpec(
    val id: String,
    val titleRes: Int,
    val historyLabelRes: Int,
    val inputHintRes: Int,
    val enabled: Boolean,
    val showInHome: Boolean,
    val promptProfile: PromptProfile,
    val maxTokens: Int,
    val temperature: Double,
    val historyLimit: Int?,
    val contextTools: List<ContextToolType>,
    val exerciseRawResId: Int? = null
) {
    val isSpecialized: Boolean get() = id != ChatModeIds.GENERAL
}

object SpecializedChatRegistry {
    private const val ANALYSIS_MAX_TOKENS = 8000
    private const val PHYSICS_MAX_TOKENS = 5000
    private const val METODI_MAX_TOKENS = 3500
    private const val CHAT_MAX_TOKENS = 1000

    val general = ChatModeSpec(
        id = ChatModeIds.GENERAL,
        titleRes = R.string.new_chat,
        historyLabelRes = R.string.chat,
        inputHintRes = R.string.write_message,
        enabled = BuildConfig.CHAT_GENERAL_ENABLED,
        showInHome = BuildConfig.CHAT_GENERAL_ENABLED,
        promptProfile = PromptProfile.NONE,
        maxTokens = CHAT_MAX_TOKENS,
        temperature = 0.7,
        historyLimit = null,
        contextTools = emptyList()
    )

    val analysis2 = ChatModeSpec(
        id = ChatModeIds.ANALYSIS2,
        titleRes = R.string.analysis2_mode,
        historyLabelRes = R.string.analysis2_short,
        inputHintRes = R.string.write_problem,
        enabled = BuildConfig.CHAT_ANALYSIS2_ENABLED,
        showInHome = BuildConfig.CHAT_ANALYSIS2_ENABLED,
        promptProfile = PromptProfile.ANALYSIS2,
        maxTokens = ANALYSIS_MAX_TOKENS,
        temperature = 0.2,
        historyLimit = null,
        contextTools = listOf(ContextToolType.EXERCISE_ANALYSIS2),
        exerciseRawResId = R.raw.esercizi_analisi2
    )

    val physics = ChatModeSpec(
        id = ChatModeIds.PHYSICS,
        titleRes = R.string.physics_mode,
        historyLabelRes = R.string.physics_short,
        inputHintRes = R.string.write_problem,
        enabled = BuildConfig.CHAT_PHYSICS_ENABLED,
        showInHome = BuildConfig.CHAT_PHYSICS_ENABLED,
        promptProfile = PromptProfile.PHYSICS,
        maxTokens = PHYSICS_MAX_TOKENS,
        temperature = 0.2,
        historyLimit = null,
        contextTools = listOf(ContextToolType.EXERCISE_PHYSICS),
        exerciseRawResId = R.raw.esercizi_fisica
    )

    val metodiTheory = ChatModeSpec(
        id = ChatModeIds.METODI_THEORY,
        titleRes = R.string.metodi_theory_mode,
        historyLabelRes = R.string.metodi_theory_short,
        inputHintRes = R.string.write_message,
        enabled = BuildConfig.CHAT_METODI_THEORY_ENABLED,
        showInHome = BuildConfig.CHAT_METODI_THEORY_ENABLED,
        promptProfile = PromptProfile.METODI_THEORY,
        maxTokens = METODI_MAX_TOKENS,
        temperature = 0.2,
        historyLimit = 10,
        contextTools = listOf(ContextToolType.METODI_THEORY)
    )

    val metodiCode = ChatModeSpec(
        id = ChatModeIds.METODI_CODE,
        titleRes = R.string.metodi_code_mode,
        historyLabelRes = R.string.metodi_code_short,
        inputHintRes = R.string.write_message,
        enabled = BuildConfig.CHAT_METODI_CODE_ENABLED,
        showInHome = BuildConfig.CHAT_METODI_CODE_ENABLED,
        promptProfile = PromptProfile.METODI_CODE,
        maxTokens = METODI_MAX_TOKENS,
        temperature = 0.15,
        historyLimit = 10,
        contextTools = listOf(ContextToolType.METODI_CODE)
    )

    private val modes = listOf(
        general,
        physics,
        analysis2,
        metodiTheory,
        metodiCode
    )

    private val byId = modes.associateBy { it.id }

    fun all(): List<ChatModeSpec> = modes

    fun homeModes(): List<ChatModeSpec> {
        val shownModes = modes.filter { it.showInHome }
        return shownModes.filter { it.isSpecialized } + shownModes.filterNot { it.isSpecialized }
    }

    fun get(modeId: String?): ChatModeSpec {
        return byId[normalizeModeId(modeId)] ?: general
    }

    fun isEnabled(modeId: String?): Boolean {
        return get(modeId).enabled
    }

    fun normalizeModeId(modeId: String?): String {
        val normalized = modeId?.trim()?.lowercase().orEmpty()
        return when (normalized) {
            "", "general", "chat" -> ChatModeIds.GENERAL
            "analysis", "analysis2", "analysis_2", "analisi", "analisi2", "analisi_2" -> ChatModeIds.ANALYSIS2
            "physics", "fisica" -> ChatModeIds.PHYSICS
            "metodi_teoria", "metodi_theory", "theory" -> ChatModeIds.METODI_THEORY
            "metodi_codice", "metodi_code", "code" -> ChatModeIds.METODI_CODE
            else -> normalized
        }
    }

    fun modeIdFromLegacy(mode: ChatMode, isAnalysisMode: Boolean): String {
        if (isAnalysisMode) return ChatModeIds.ANALYSIS2
        return when (mode) {
            ChatMode.GENERAL -> ChatModeIds.GENERAL
            ChatMode.ANALYSIS -> ChatModeIds.ANALYSIS2
            ChatMode.METODI_TEORIA -> ChatModeIds.METODI_THEORY
            ChatMode.METODI_CODICE -> ChatModeIds.METODI_CODE
        }
    }

    fun legacyModeFor(modeId: String?): ChatMode {
        return when (normalizeModeId(modeId)) {
            ChatModeIds.ANALYSIS2 -> ChatMode.ANALYSIS
            ChatModeIds.METODI_THEORY -> ChatMode.METODI_TEORIA
            ChatModeIds.METODI_CODE -> ChatMode.METODI_CODICE
            else -> ChatMode.GENERAL
        }
    }
}
