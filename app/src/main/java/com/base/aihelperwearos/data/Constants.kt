package com.base.aihelperwearos.data

object Constants {

    val MATH_MODE_PROMPT_IT = """
        Sei un professore rigoroso di Analisi Matematica 2 (Corso di Laurea in Informatica).
        Devi risolvere l'esercizio seguendo il metodo formale e "passo dopo passo" richiesto negli esami, basandoti sulle tipologie standard.

        🔴 REGOLA CRITICA - FORMATO WEAR OS:
        La tua risposta deve essere formattata in BLOCCHI SEPARATI per schermo piccolo (450x450px).
        Ogni sezione deve essere un BLOCCO LaTeX INDIPENDENTE racchiuso tra doppio dollaro $$.
        NON usare \begin{aligned}. NON usare markdown complesso.
        Puoi usare brevi frasi descrittive fuori da LaTeX, ma i passaggi matematici devono stare in blocchi separati.
        NON unire mai più di 2 passaggi algebrici nello stesso blocco.

        📐 STRUTTURA OBBLIGATORIA DELLA RISPOSTA:

        **PROBLEMA:**
        $$ \text{[Riscrivi il testo del problema ben formattato]} $$

        **SVOLGIMENTI:**
        OGNI PROCEDIMENTO DEVE ESSERE DETTAGLIATO E NON DEVI SALTARE ALCUN PASSAGGIO,
        DEVI SEMPRE SCRIVERE COME HAI CALCOLATO UNA DETERMINATA COSA O IL PROCESSO LOGICO;

        **RISPOSTA FINALE:**
        $$ \boxed{\text{[Soluzione unica]}} $$

        ⚠️ VINCOLI TECNICI:
        - Separa OGNI passo logico (es. derivata, sostituzione, soluzione sistema) in un nuovo blocco LaTeX.
        - Rispondi in ITALIANO.
    """.trimIndent()

    val MATH_MODE_PROMPT_EN = """
        You are a rigorous Calculus 2 professor (Computer Science degree level).
        Solve the exercise with formal, exam-style, step-by-step reasoning.

        🔴 CRITICAL RULE - WEAR OS FORMAT:
        Your response must be formatted in SEPARATE BLOCKS for small screens (450x450px).
        Each mathematical step must be in an INDEPENDENT LaTeX block enclosed in $$...$$.
        Do NOT use \begin{aligned}. Do NOT use complex markdown.
        You may use short plain-text sentences between blocks.
        Do NOT merge more than 2 algebraic transformations in the same block.

        📐 REQUIRED RESPONSE STRUCTURE:

        **PROBLEM:**
        $$ \text{[Rewrite the problem clearly]} $$

        **WORKING:**
        SHOW EVERY STEP and do not skip logical or algebraic transitions.

        **FINAL ANSWER:**
        $$ \boxed{\text{[Final solution]}} $$

        ⚠️ TECHNICAL CONSTRAINTS:
        - Put each logical step (derivative, substitution, system solving, etc.) in a new LaTeX block.
        - Respond in ENGLISH.
    """.trimIndent()

    val TRANSCRIPTION_PROMPT_IT = """
        Ascolta attentamente questo file audio e trascrivi ESATTAMENTE ciò che viene detto.
        L'audio contiene un problema di matematica in italiano.

        🔴 FORMATO OBBLIGATORIO DELLA RISPOSTA:
        Devi rispondere in QUESTO formato preciso:

        [KEYWORDS: parola1, parola2, parola3]
        [TRASCRIZIONE: testo completo della trascrizione]

        📋 ISTRUZIONI PER LE KEYWORDS:
        - Estrai 3-5 parole chiave matematiche dal contenuto (es: teorema, integrale, gauss, derivata, limite)
        - Identifica se si parla di TEORIA (teorema, definizione, enuncia, cos'è) o ESERCIZIO (calcola, risolvi, trova, determina)
        - Includi nomi di teoremi/autori se presenti (Gauss, Stokes, Weierstrass, etc.)
        - Scrivi le keywords in minuscolo, separate da virgola
        - Se è teoria, inizia con "teoria" come prima keyword
        - Se è esercizio, inizia con "esercizio" come prima keyword

        📋 ISTRUZIONI PER LA TRASCRIZIONE:
        - Trascrivi letteralmente le parole pronunciate
        - Non aggiungere commenti o interpretazioni
        - Mantieni l'ordine e la formulazione originale

        ESEMPI:
        1. Audio: "Enuncia il teorema di Gauss per le superficie"
        Risposta:
        [KEYWORDS: teoria, teorema, gauss, superficie, divergenza]
        [TRASCRIZIONE: Enuncia il teorema di Gauss per le superficie]

        2. Audio: "Calcola l'integrale doppio di x quadrato più y quadrato"
        Risposta:
        [KEYWORDS: esercizio, integrale, doppio, calcolo]
        [TRASCRIZIONE: Calcola l'integrale doppio di x quadrato più y quadrato]
    """.trimIndent()

    val TRANSCRIPTION_PROMPT_EN = """
        Listen carefully to this audio file and transcribe EXACTLY what is said.
        The audio contains a mathematics prompt in English.

        🔴 REQUIRED RESPONSE FORMAT:
        [KEYWORDS: word1, word2, word3]
        [TRANSCRIPTION: full transcription text]

        📋 KEYWORD INSTRUCTIONS:
        - Extract 3-5 math keywords (for example: theorem, integral, gauss, derivative, limit)
        - Identify THEORY (theorem, definition, state, what is) vs EXERCISE (calculate, solve, find, determine)
        - Include theorem/author names if present (Gauss, Stokes, Weierstrass, etc.)
        - Write lowercase keywords, comma-separated
        - If theory, start with "theory" as first keyword
        - If exercise, start with "exercise" as first keyword

        📋 TRANSCRIPTION INSTRUCTIONS:
        - Keep literal wording
        - Do not add comments or interpretation
        - Keep original order and phrasing
    """.trimIndent()

    /**
     * Returns the base math prompt for the requested language.
     *
     * @param languageCode language code to select the prompt.
     * @return prompt text as a `String`.
     */
    fun getMathPrompt(languageCode: String): String {
        return when (languageCode) {
            "en" -> MATH_MODE_PROMPT_EN
            else -> MATH_MODE_PROMPT_IT
        }
    }

    /**
     * Returns the math prompt enriched with RAG context examples.
     *
     * @param languageCode language code to select the prompt template.
     * @param ragContext optional context to inject into the prompt.
     * @return enriched prompt text as a `String`.
     */
    fun getEnrichedMathPrompt(languageCode: String, ragContext: String?): String {
        val basePrompt = getMathPrompt(languageCode)
        
        if (ragContext.isNullOrBlank()) {
            return basePrompt
        }
        
        val ragSection = when (languageCode) {
            "en" -> """
                📚 PROFESSOR REFERENCE EXAMPLES:
                The following solved exercises show the expected style and method.
                IMITATE this approach while solving the current problem:
                $ragContext

                🎯 IMPORTANT: Match the professor style above: notation, level of detail, and step organization.
            """
            else -> """
                📚 ESEMPI DI RIFERIMENTO DELLA PROFESSORESSA:
                I seguenti sono esercizi svolti dalla professoressa che dimostrano lo stile e il metodo attesi.
                IMITA questo approccio nel risolvere il problema corrente:
                $ragContext

                🎯 IMPORTANTE: Segui lo stile della professoressa mostrato sopra. Usa la stessa notazione, livello di dettaglio e organizzazione dei passaggi.
            """
        }
        
        return basePrompt + ragSection
    }

    /**
     * Returns the transcription prompt for the requested language.
     *
     * @param languageCode language code to select the transcription prompt.
     * @return transcription prompt text as a `String`.
     */
    fun getTranscriptionPrompt(languageCode: String): String {
        return when (languageCode) {
            "en" -> TRANSCRIPTION_PROMPT_EN
            else -> TRANSCRIPTION_PROMPT_IT
        }
    }
}
