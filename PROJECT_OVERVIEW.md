# Panoramica del Progetto: AIHelperWearOS

## Introduzione
**AIHelperWearOS** è un'applicazione assistente intelligente nativa per **Wear OS** (Android), sviluppata in **Kotlin**. L'app funge da interfaccia avanzata per modelli linguistici di grandi dimensioni (LLM) tramite l'API **OpenRouter**, permettendo agli utenti di interagire vocalmente o testualmente con l'AI direttamente dal polso.

L'applicazione è progettata per gestire conversazioni generiche e una "Modalità Analisi" specializzata per la risoluzione di problemi matematici complessi, con supporto per il rendering di formule LaTeX e trascrizione audio avanzata.

## Architettura e Design
Il progetto segue i principi della **Clean Architecture** e il pattern **MVVM (Model-View-ViewModel)** per garantire separazione delle responsabilità, testabilità e manutenibilità.

### Struttura dei Package
- **`presentation`**: Contiene la logica UI (Compose), ViewModel, Temi e Componenti visivi.
- **`data`**: Gestisce la persistenza (DataStore), il networking (Ktor) e i repository.
- **`domain`** (implicito): Logica di business integrata nei Repository e ViewModel.
- **`utils`**: Helper per Audio, Locale, Parsing e Input.

## Stack Tecnologico

### Core & Android
- **Linguaggio**: Kotlin 2.0.21
- **Piattaforma**: Android Wear OS (Min SDK 30, Target SDK 36)
- **UI Toolkit**: Jetpack Compose for Wear OS (Material, Foundation, Navigation)
- **Lifecycle**: ViewModel & Lifecycle Runtime Compose

### Networking & Data
- **Networking**: Ktor Client (Android Engine) per chiamate API asincrone efficienti.
- **Serialization**: Kotlinx Serialization (JSON) per il parsing dei dati.
- **API Integration**: OpenRouter API (Accesso a Gemini, Claude, GPT).
- **Persistenza**: Jetpack DataStore (Preferences) per salvare cronologia chat e impostazioni utente.

### Media & Rendering
- **Audio Input**: `MediaRecorder` + `AudioRecordingService` (Foreground Service) per registrazione vocale affidabile su Wear OS.
- **Audio Output**: `MediaPlayer` per riproduzione audio e Text-To-Speech (TTS).
- **Rendering Testo**:
    - `compose-markdown`: Rendering di testo Markdown.
    - **LaTeX**: Rendering personalizzato di formule matematiche convertite in immagini tramite API `i.upmath.me`, gestite con **Coil**.
- **Speech-to-Text (STT)**: Trascrizione audio server-side tramite modello **Gemini 2.5 Flash** (multimodale) per accuratezza superiore rispetto al riconoscimento vocale on-device standard.

## Funzionalità Chiave

### 1. Chat Multimodale
- Interfaccia chat ottimizzata per schermi rotondi.
- Supporto per input vocale e tastiera (RemoteInput).
- Selezione dinamica del modello AI (Gemini 2.5 Pro, Claude 3.5 Sonnet, GPT-5).
- Cronologia delle conversazioni persistente.

### 2. Modalità Analisi (Math Mode)
- Workflow dedicato per la risoluzione di problemi matematici.
- Prompt di sistema specializzati (Italiano/Inglese) per guidare l'AI nel ragionamento step-by-step.
- Rendering automatico di formule LaTeX all'interno della chat.
- Zoom interattivo delle formule matematiche.

### 3. Gestione Audio Avanzata
- Registrazione vocale con visual feedback.
- Invio dell'audio raw all'AI per trascrizione contestuale (migliore per termini tecnici/matematici).
- Riproduzione delle risposte (TTS o audio generato).

### 4. Internazionalizzazione
- Supporto completo per Italiano e Inglese.
- Cambio lingua dinamico in-app con gestione del Locale.

## Workflow Applicativo

1.  **Avvio**: `MainActivity` inizializza il `MainViewModel` e lega il `AudioRecordingService`.
2.  **Home**: L'utente sceglie tra "Nuova Chat", "Modalità Analisi" o "Cronologia".
3.  **Interazione**:
    - **Testo**: L'utente usa la tastiera Wear OS.
    - **Voce**: L'utente registra un audio. Il file viene inviato a OpenRouter (Gemini Flash) per la trascrizione.
4.  **Elaborazione**:
    - Il testo (o trascrizione) viene inviato al modello selezionato tramite `OpenRouterService`.
    - La risposta viene streamata o ricevuta in blocco.
    - Se la risposta contiene LaTeX (`$$...$$`), viene parsata da `LatexParser` e renderizzata come immagine.
5.  **Salvataggio**: Ogni messaggio e sessione viene salvato automaticamente nel `DataStore` tramite `ChatRepository`.

## Configurazione Build
- **Gradle Version Catalog** (`libs.versions.toml`) per la gestione centralizzata delle dipendenze.
- **Build Types**: Configurazione `release` con regole ProGuard/R8.
- **Secrets**: API Key gestite tramite `local.properties` e `BuildConfig`.

## Note per lo Sviluppo
- Il progetto utilizza feature sperimentali di Wear OS e Compose, richiedendo l'annotazione `@OptIn`.
- La gestione dei permessi (Microfono) è gestita a runtime con `ActivityResultContracts`.
- Per testare l'audio su emulatore, assicurarsi che l'input microfono sia configurato correttamente nell'AVD.
