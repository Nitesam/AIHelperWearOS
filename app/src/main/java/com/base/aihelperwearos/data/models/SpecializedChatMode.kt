package com.base.aihelperwearos.data.models

import com.base.aihelperwearos.BuildConfig
import com.base.aihelperwearos.R

object ChatModeIds {
    const val GENERAL = "general"
    const val ANALYSIS = "analysis2"
    @Deprecated("Use ANALYSIS. The persisted id remains analysis2 for compatibility.")
    const val ANALYSIS2 = ANALYSIS
    const val PHYSICS = "physics"
    const val SOFTWARE_ENGINEERING = "software_engineering"
    const val METODI_THEORY = "metodi_theory"
    const val METODI_CODE = "metodi_code"
}

enum class PromptProfile {
    NONE,
    ANALYSIS,
    PHYSICS,
    SOFTWARE_ENGINEERING,
    METODI_THEORY,
    METODI_CODE
}

enum class ContextToolType {
    EXERCISE_ANALYSIS,
    EXERCISE_PHYSICS,
    SOFTWARE_ENGINEERING,
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
    private const val SOFTWARE_MAX_TOKENS = 6500
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

    val analysis = ChatModeSpec(
        id = ChatModeIds.ANALYSIS,
        titleRes = R.string.analysis,
        historyLabelRes = R.string.analysis,
        inputHintRes = R.string.write_problem,
        enabled = BuildConfig.CHAT_ANALYSIS2_ENABLED,
        showInHome = BuildConfig.CHAT_ANALYSIS2_ENABLED,
        promptProfile = PromptProfile.ANALYSIS,
        maxTokens = ANALYSIS_MAX_TOKENS,
        temperature = 0.2,
        historyLimit = null,
        contextTools = listOf(ContextToolType.EXERCISE_ANALYSIS),
        exerciseRawResId = R.raw.esercizi_analisi
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

    val softwareEngineering = ChatModeSpec(
        id = ChatModeIds.SOFTWARE_ENGINEERING,
        titleRes = R.string.software_engineering_mode,
        historyLabelRes = R.string.software_engineering_short,
        inputHintRes = R.string.write_problem,
        enabled = BuildConfig.CHAT_SOFTWARE_ENGINEERING_ENABLED,
        showInHome = BuildConfig.CHAT_SOFTWARE_ENGINEERING_ENABLED,
        promptProfile = PromptProfile.SOFTWARE_ENGINEERING,
        maxTokens = SOFTWARE_MAX_TOKENS,
        temperature = 0.15,
        historyLimit = null,
        contextTools = listOf(ContextToolType.SOFTWARE_ENGINEERING),
        exerciseRawResId = R.raw.ingegneria_software
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
        analysis,
        softwareEngineering,
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
            "analysis", "analysis1", "analysis_1", "analysis2", "analysis_2",
            "analisi", "analisi1", "analisi_1", "analisi2", "analisi_2" -> ChatModeIds.ANALYSIS
            "physics", "fisica" -> ChatModeIds.PHYSICS
            "software", "software_engineering", "software-engineering", "ingegneria",
            "ingegneria_software", "ingegneria-software", "ingegneria_del_software",
            "ingegneria-del-software", "ingsoft", "ids" -> ChatModeIds.SOFTWARE_ENGINEERING
            "metodi_teoria", "metodi_theory", "theory" -> ChatModeIds.METODI_THEORY
            "metodi_codice", "metodi_code", "code" -> ChatModeIds.METODI_CODE
            else -> normalized
        }
    }

    fun modeIdFromLegacy(mode: ChatMode, isAnalysisMode: Boolean): String {
        if (isAnalysisMode) return ChatModeIds.ANALYSIS
        return when (mode) {
            ChatMode.GENERAL -> ChatModeIds.GENERAL
            ChatMode.ANALYSIS -> ChatModeIds.ANALYSIS
            ChatMode.METODI_TEORIA -> ChatModeIds.METODI_THEORY
            ChatMode.METODI_CODICE -> ChatModeIds.METODI_CODE
        }
    }

    fun legacyModeFor(modeId: String?): ChatMode {
        return when (normalizeModeId(modeId)) {
            ChatModeIds.ANALYSIS -> ChatMode.ANALYSIS
            ChatModeIds.METODI_THEORY -> ChatMode.METODI_TEORIA
            ChatModeIds.METODI_CODE -> ChatMode.METODI_CODICE
            else -> ChatMode.GENERAL
        }
    }
}
