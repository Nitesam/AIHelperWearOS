package com.base.aihelperwearos.data

import com.base.aihelperwearos.data.models.ChatMode
import com.base.aihelperwearos.data.models.ChatModeIds
import com.base.aihelperwearos.data.models.PromptProfile
import com.base.aihelperwearos.data.models.SpecializedChatRegistry

object Constants {
    @Deprecated("Use SpecializedChatRegistry and BuildConfig-backed mode flags instead.")
    const val ANALYSIS_MODULE_ENABLED = false

    private val WEAR_LATEX_POLICY_IT = """
        Formato Wear OS:
        Risposta leggibile su schermo piccolo (450x450px).
        Ogni passaggio matematico importante va in un blocco LaTeX separato racchiuso tra $$.
        Evita \begin{aligned}, tabelle e markdown complesso.
        Non mettere piu' di due passaggi algebrici nello stesso blocco.
        Fuori dal LaTeX usa solo frasi brevi, quando chiariscono il passaggio.
    """.trimIndent()

    private val WEAR_LATEX_POLICY_EN = """
        Wear OS format:
        Keep the answer readable on a small screen (450x450px).
        Put every important math step in a separate LaTeX block enclosed in $$.
        Avoid \begin{aligned}, tables, and complex markdown.
        Do not merge more than 2 algebraic transformations in the same block.
        Outside LaTeX, use short sentences only when they clarify the step.
    """.trimIndent()

    val MATH_MODE_PROMPT_IT = """
        Analisi Matematica 2, corso di laurea in Informatica.
        Risoluzione formale, passo per passo, nel taglio richiesto agli esami.

        Formato Wear OS:
        Risposta divisa in blocchi brevi per schermo piccolo (450x450px).
        Ogni passaggio matematico va in un blocco LaTeX indipendente racchiuso tra $$.
        Evita \begin{aligned} e markdown complesso.
        Le frasi descrittive fuori dal LaTeX devono essere brevi.
        Non unire piu' di due passaggi algebrici nello stesso blocco.

        Struttura:

        **PROBLEMA:**
        $$ \text{[Riscrivi il testo del problema ben formattato]} $$

        **SVOLGIMENTI:**
        Dettaglia i passaggi logici e algebrici necessari, senza saltare i passaggi intermedi importanti.

        **RISPOSTA FINALE:**
        $$ \boxed{\text{[Soluzione unica]}} $$

        Vincoli:
        - Separa ogni passo logico (derivata, sostituzione, soluzione di un sistema) in un nuovo blocco LaTeX.
        - Rispondi in ITALIANO.
    """.trimIndent()

    val MATH_MODE_PROMPT_EN = """
        Calculus 2, Computer Science degree level.
        Solve the exercise with formal, exam-style, step-by-step reasoning.

        Wear OS format:
        Use short separated blocks for a small screen (450x450px).
        Each mathematical step must be in a separate LaTeX block enclosed in $$...$$.
        Avoid \begin{aligned} and complex markdown.
        Use short plain-text sentences between blocks only when useful.
        Do NOT merge more than 2 algebraic transformations in the same block.

        Structure:

        **PROBLEM:**
        $$ \text{[Rewrite the problem clearly]} $$

        **WORKING:**
        Include the necessary logical and algebraic steps.

        **FINAL ANSWER:**
        $$ \boxed{\text{[Final solution]}} $$

        Constraints:
        - Put each logical step (derivative, substitution, system solving, etc.) in a new LaTeX block.
        - Respond in ENGLISH.
    """.trimIndent()

    private val PHYSICS_MODE_PROMPT_IT = """
        Fisica 1.
        Risolvi gli esercizi con taglio da quaderno: dati, formule essenziali, sostituzioni numeriche e risultato.

        STRUTTURA:
        **DATI:**
        Elenca solo le grandezze utili e converti le unità quando serve.

        **FORMULE:**
        Scrivi le leggi fisiche usate, poche e mirate.

        **CALCOLI:**
        Procedi come negli esercizi svolti: imposta le equazioni, sostituisci i numeri, mantieni le unità.
        Se serve, separa componenti x/y o energia/quantità di moto.

        **RISULTATO:**
        $$ \boxed{\text{[risultato con unità di misura]}} $$

        Rispondi in ITALIANO. Non aggiungere spiegazioni lunghe se non richieste.
    """.trimIndent()

    private val PHYSICS_MODE_PROMPT_EN = """
        Physics 1.
        Solve the exercise in a notebook style: data, essential formulas, numeric substitutions, and final result.

        STRUCTURE:
        **DATA:**
        List only useful quantities and convert units when needed.

        **FORMULAS:**
        Write the physical laws used, few and targeted.

        **CALCULATIONS:**
        Work like the solved examples: set equations, substitute numbers, keep units.
        Split x/y components or energy/momentum only when useful.

        **FINAL RESULT:**
        $$ \boxed{\text{[result with units]}} $$

        Respond in ENGLISH. Avoid long explanations unless requested.
    """.trimIndent()

    val TRANSCRIPTION_PROMPT_IT = """
        Ascolta attentamente questo file audio e trascrivi ESATTAMENTE ciò che viene detto.
        L'audio può contenere un problema di matematica in italiano, una domanda, oppure una semplice prova microfono.
        NON inventare, NON completare esercizi e NON aggiungere testo non pronunciato.
        Se senti solo una frase di test come "prova microfono", trascrivi solo quella frase.
        Se una parte non è chiara, scrivi [incomprensibile].

        Formato:

        [KEYWORDS: parola1, parola2, parola3]
        [TRASCRIZIONE: testo completo della trascrizione]

        Keywords:
        - Estrai 3-5 parole chiave matematiche dal contenuto (es: teorema, integrale, gauss, derivata, limite)
        - Identifica se si parla di TEORIA (teorema, definizione, enuncia, cos'è) o ESERCIZIO (calcola, risolvi, trova, determina)
        - Includi nomi di teoremi/autori se presenti (Gauss, Stokes, Weierstrass, etc.)
        - Scrivi le keywords in minuscolo, separate da virgola
        - Se è teoria, inizia con "teoria" come prima keyword
        - Se è esercizio, inizia con "esercizio" come prima keyword

        Trascrizione:
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
        The audio may contain a mathematics prompt, a question, or a simple microphone test.
        Do NOT invent, do NOT complete exercises, and do NOT add text that was not spoken.
        If you only hear a test phrase such as "microphone test", transcribe only that phrase.
        If a part is unclear, write [inaudible].

        Format:
        [KEYWORDS: word1, word2, word3]
        [TRANSCRIPTION: full transcription text]

        Keywords:
        - Extract 3-5 math keywords (for example: theorem, integral, gauss, derivative, limit)
        - Identify THEORY (theorem, definition, state, what is) vs EXERCISE (calculate, solve, find, determine)
        - Include theorem/author names if present (Gauss, Stokes, Weierstrass, etc.)
        - Write lowercase keywords, comma-separated
        - If theory, start with "theory" as first keyword
        - If exercise, start with "exercise" as first keyword

        Transcription:
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
                Reference examples:
                The solved exercises below show the expected method.
                Use the same notation and level of detail when relevant.
                $ragContext
            """
            else -> """
                Esempi di riferimento:
                Gli esercizi svolti qui sotto mostrano metodo e livello di dettaglio attesi.
                Usa la stessa notazione quando e' pertinente.
                $ragContext
            """
        }
        
        return basePrompt + ragSection
    }

    fun getPrompt(profile: PromptProfile, languageCode: String, ragContext: String?): String? {
        if (profile == PromptProfile.NONE) return null

        val policy = if (languageCode == "en") WEAR_LATEX_POLICY_EN else WEAR_LATEX_POLICY_IT
        val base = when (profile) {
            PromptProfile.ANALYSIS2 -> getMathPrompt(languageCode)
            PromptProfile.PHYSICS -> if (languageCode == "en") PHYSICS_MODE_PROMPT_EN else PHYSICS_MODE_PROMPT_IT
            PromptProfile.METODI_THEORY -> getMetodiTheoryPrompt(languageCode, ragContext = null)
            PromptProfile.METODI_CODE -> getMetodiCodePrompt(languageCode, ragContext = null)
            PromptProfile.NONE -> return null
        }

        val contextBlock = if (ragContext.isNullOrBlank()) {
            ""
        } else {
            val label = when {
                languageCode == "en" -> "RELEVANT REFERENCE CONTEXT"
                profile == PromptProfile.PHYSICS -> "ESEMPI DI FISICA RILEVANTI"
                else -> "CONTESTO DI RIFERIMENTO RILEVANTE"
            }
            "\n\n$label:\n$ragContext"
        }

        val styleReminder = when {
            ragContext.isNullOrBlank() -> ""
            languageCode == "en" -> "\n\nMatch the notation, level of detail, and step organization of the retrieved references when relevant."
            profile == PromptProfile.PHYSICS -> "\n\nUsa il taglio degli esempi: formule essenziali, sostituzioni, unita' e risultato breve."
            else -> "\n\nSegui notazione, livello di dettaglio e organizzazione degli esempi recuperati quando pertinenti."
        }

        return "$policy\n\n$base$contextBlock$styleReminder"
    }

    fun getMetodiPrompt(chatMode: ChatMode, languageCode: String, ragContext: String?): String {
        return when (chatMode) {
            ChatMode.METODI_TEORIA -> getMetodiTheoryPrompt(languageCode, ragContext)
            ChatMode.METODI_CODICE -> getMetodiCodePrompt(languageCode, ragContext)
            else -> ""
        }
    }

    private fun getMetodiTheoryPrompt(languageCode: String, ragContext: String?): String {
        val base = if (languageCode == "en") {
            """
                Metodi Matematici e Statistici, theory.
                Use the excerpts from TEORIA_CORSO.pdf when relevant.
                Treat the course notes as the factual base, without copying their wording.
                Write in a clear exam style: correct, concise, and not textbook-like.
                Prefer a short definition, the main idea, and the essential formulas or conditions.
                Keep answers concise and suitable for a Wear OS display.
                If the provided excerpts do not contain enough information, say so briefly and answer with standard course-level knowledge.
                Cite page references from the excerpts using "p. N" when you rely on them.
                Do not over-cite: page references are supporting notes, not the structure of the answer.
                Do not produce Python code unless explicitly requested.
            """.trimIndent()
        } else {
            """
                Metodi Matematici e Statistici, teoria.
                Usa gli estratti da TEORIA_CORSO.pdf quando sono pertinenti.
                Gli appunti del docente sono la base dei contenuti, ma non copiarne la formulazione.
                Scrivi con tono da compito universitario: corretto, chiaro, sintetico, senza impostazione da manuale.
                Preferisci una breve definizione, l'idea principale e le sole formule o condizioni essenziali.
                Mantieni le risposte concise e leggibili su Wear OS.
                Se gli estratti forniti non bastano, dichiaralo brevemente e rispondi con conoscenza standard del corso.
                Cita le pagine dagli estratti con "p. N" quando le usi.
                Non citare troppo: i riferimenti di pagina servono come supporto, non come struttura della risposta.
                Non produrre codice Python salvo richiesta esplicita.
            """.trimIndent()
        }

        return if (ragContext.isNullOrBlank()) {
            base
        } else {
            "$base\n\nESTRATTI TEORIA RILEVANTI:\n$ragContext"
        }
    }

    private fun getMetodiCodePrompt(languageCode: String, ragContext: String?): String {
        val base = if (languageCode == "en") {
            """
                Metodi Matematici e Statistici, Python.
                Solve dictated exercises with Python code in the professor's style.
                The target environment is Jupyter Notebook, not a standalone Python script.
                Structure the solution as notebook-style cells separated by short comments like "# Dati", "# Calcoli", "# Grafico", "# Risultati".
                Do not write CLI code, input(), argparse, file prompts, or `if __name__ == "__main__"`.
                Prefer simple notebook-style code cells, explicit variables, and display/print of final numerical results.
                Use plotting only through `import matplotlib.pyplot as plt` and direct `plt.*` calls.
                For normal line plots, keep the professor's minimal style: one `plt.plot(...)` line per curve and `plt.show()`.
                Do not add `plt.figure(...)`, `plt.grid()`, `plt.legend()`, `plt.xlabel()`, `plt.ylabel()`, `plt.title()`, `plt.xscale()`, or `plt.yscale()` unless the exercise explicitly asks for them.
                Do not use `plt.subplots`, `subplot`, `fig`, `ax`, `ax1`, `ax2` or `tight_layout`.
                If the retrieved example uses only `plt.plot(...)` and `plt.show()`, copy that compact plotting pattern.
                Do not use pandas, seaborn, sklearn, statsmodels, or other complex plotting/statistics libraries.
                Use `numpy` only when arrays, simulations, linspace, mean/std, or linear algebra are really useful.
                Use `scipy.stats` only when a distribution quantile/pdf/cdf is explicitly needed and simpler formulas are not practical.
                Start with a short plan only when it clarifies the exercise, then provide a single executable Python code block.
                Keep comments short and useful.
                If data are missing, create clearly named placeholders and state what the user must replace.
                Do not use theory PDF excerpts in this mode.
            """.trimIndent()
        } else {
            """
                Metodi Matematici e Statistici, Python.
                Risolvi gli esercizi dettati con codice Python nello stile del docente.
                L'ambiente di destinazione è Jupyter Notebook, non uno script Python standalone.
                Struttura la soluzione come celle notebook separate da brevi commenti tipo "# Dati", "# Calcoli", "# Grafico", "# Risultati".
                Non scrivere codice CLI, input(), argparse, richieste interattive da terminale o `if __name__ == "__main__"`.
                Preferisci celle stile notebook, variabili esplicite, e display/print dei risultati numerici finali.
                Per i grafici usa solo `import matplotlib.pyplot as plt` e chiamate dirette `plt.*`.
                Per i grafici normali mantieni lo stile minimale del docente: una riga `plt.plot(...)` per curva e `plt.show()`.
                Non aggiungere `plt.figure(...)`, `plt.grid()`, `plt.legend()`, `plt.xlabel()`, `plt.ylabel()`, `plt.title()`, `plt.xscale()` o `plt.yscale()` se l'esercizio non li richiede esplicitamente.
                Non usare `plt.subplots`, `subplot`, `fig`, `ax`, `ax1`, `ax2` o `tight_layout`.
                Se l'esempio recuperato usa solo `plt.plot(...)` e `plt.show()`, copia quel pattern compatto.
                Non usare pandas, seaborn, sklearn, statsmodels o altre librerie complesse di grafica/statistica.
                Usa `numpy` solo quando servono davvero array, simulazioni, linspace, media/deviazione standard o algebra lineare.
                Usa `scipy.stats` solo quando servono esplicitamente quantili/pdf/cdf di distribuzioni e le formule semplici non sono pratiche.
                Inserisci un piano breve solo se chiarisce l'esercizio, poi fornisci un unico blocco Python eseguibile.
                Tieni i commenti brevi e utili.
                Se mancano dati, crea placeholder con nomi chiari e indica cosa sostituire.
                Non usare estratti del PDF di teoria in questa modalità.
            """.trimIndent()
        }

        val referenceNote = if (languageCode == "en") {
            "Reference use: the first retrieved example is the priority style reference; use later examples only when they do not conflict with it. Do not add plotting decoration that is absent from the priority example."
        } else {
            "Uso dei riferimenti: il primo esempio recuperato è il riferimento di stile prioritario; usa gli esempi successivi solo se non lo contraddicono. Non aggiungere decorazioni ai grafici assenti dall'esempio prioritario."
        }

        return if (ragContext.isNullOrBlank()) {
            base
        } else {
            "$base\n\n$referenceNote\n\nESEMPI CODICE RILEVANTI DEL DOCENTE:\n$ragContext"
        }
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

    fun getTranscriptionPrompt(languageCode: String, modeId: String): String {
        return when (SpecializedChatRegistry.normalizeModeId(modeId)) {
            ChatModeIds.METODI_THEORY -> if (languageCode == "en") {
                """
                    Listen carefully and transcribe exactly what is said.
                    The audio may contain a Metodi Matematici e Statistici theory question or a microphone test.
                    Do not infer or complete a question. Transcribe only words actually spoken.
                    If you only hear "microphone test" or similar, output only that phrase as transcription.

                    Format:
                    [KEYWORDS: theory, keyword1, keyword2, keyword3]
                    [TRANSCRIPTION: full literal transcription]

                    Extract 3-5 useful course keywords such as probability, random variable, distribution, estimator, confidence interval, hypothesis test, Markov chain, regression.
                    Do not answer the question.
                """.trimIndent()
            } else {
                """
                    Ascolta attentamente e trascrivi esattamente ciò che viene detto.
                    L'audio può contenere una domanda di teoria di Metodi Matematici e Statistici oppure una prova microfono.
                    Non dedurre e non completare la domanda. Trascrivi solo parole realmente pronunciate.
                    Se senti solo "prova microfono" o simile, inserisci solo quella frase nella trascrizione.

                    Formato:
                    [KEYWORDS: teoria, parola1, parola2, parola3]
                    [TRASCRIZIONE: trascrizione letterale completa]

                    Estrai 3-5 parole chiave utili del corso, ad esempio probabilità, variabile aleatoria, distribuzione, stimatore, intervallo di confidenza, test di ipotesi, catena di Markov, regressione.
                    Non rispondere alla domanda.
                """.trimIndent()
            }
            ChatModeIds.METODI_CODE -> if (languageCode == "en") {
                """
                    Listen carefully and transcribe exactly what is said.
                    The audio may contain a Metodi Matematici e Statistici coding exercise or a microphone test.
                    Do not infer or complete an exercise. Transcribe only words actually spoken.
                    If you only hear "microphone test" or similar, output only that phrase as transcription.

                    Format:
                    [KEYWORDS: exercise, python, keyword1, keyword2, keyword3]
                    [TRANSCRIPTION: full literal transcription]

                    Extract 3-5 useful coding/statistics keywords such as binomial, normal, hypergeometric, histogram, regression, confidence interval, chi-square, simulation, Markov.
                    Preserve numbers, variable names and data values exactly.
                    Do not solve the exercise.
                """.trimIndent()
            } else {
                """
                    Ascolta attentamente e trascrivi esattamente ciò che viene detto.
                    L'audio può contenere un esercizio di codice per Metodi Matematici e Statistici oppure una prova microfono.
                    Non dedurre e non completare l'esercizio. Trascrivi solo parole realmente pronunciate.
                    Se senti solo "prova microfono" o simile, inserisci solo quella frase nella trascrizione.

                    Formato:
                    [KEYWORDS: esercizio, python, parola1, parola2, parola3]
                    [TRASCRIZIONE: trascrizione letterale completa]

                    Estrai 3-5 parole chiave utili per codice/statistica, ad esempio binomiale, normale, ipergeometrica, istogramma, regressione, intervallo di confidenza, chi quadro, simulazione, Markov.
                    Preserva esattamente numeri, nomi di variabili e dati.
                    Non risolvere l'esercizio.
                """.trimIndent()
            }
            ChatModeIds.PHYSICS -> if (languageCode == "en") {
                """
                    Listen carefully and transcribe exactly what is said.
                    The audio may contain a Physics 1 exercise or a microphone test.
                    Do not infer or complete an exercise. Transcribe only words actually spoken.

                    Format:
                    [KEYWORDS: exercise, physics, keyword1, keyword2, keyword3]
                    [TRANSCRIPTION: full literal transcription]

                    Extract 3-6 useful physics keywords such as motion, acceleration, projectile, circular motion, force, energy, impulse, collision, momentum.
                    Preserve numbers, units and variable names exactly.
                    Do not solve the exercise.
                """.trimIndent()
            } else {
                """
                    Ascolta attentamente e trascrivi esattamente ciò che viene detto.
                    L'audio può contenere un esercizio di Fisica 1 oppure una prova microfono.
                    Non dedurre e non completare l'esercizio. Trascrivi solo parole realmente pronunciate.

                    Formato:
                    [KEYWORDS: esercizio, fisica, parola1, parola2, parola3]
                    [TRASCRIZIONE: trascrizione letterale completa]

                    Estrai 3-6 parole chiave fisiche utili, ad esempio moto, accelerazione, proiettile, moto circolare, forza, energia, impulso, urto, quantità di moto.
                    Preserva esattamente numeri, unità di misura e nomi delle variabili.
                    Non risolvere l'esercizio.
                """.trimIndent()
            }
            else -> getTranscriptionPrompt(languageCode)
        }
    }

    fun getTranscriptionPrompt(languageCode: String, chatMode: ChatMode): String {
        return getTranscriptionPrompt(
            languageCode = languageCode,
            modeId = SpecializedChatRegistry.modeIdFromLegacy(chatMode, isAnalysisMode = false)
        )
    }
}
