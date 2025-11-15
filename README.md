# AI Helper Wear OS

This is an AI assistant application for Wear OS.

## Features

*   **AI Chat:** Chat with an AI assistant.
*   **Math Analysis:** Solve math problems by speaking or typing them.
*   **Chat History:** View your past conversations.
*   **Model Selection:** Choose from a variety of AI models.
*   **Language Selection:** The app is available in English and Italian.

## How to Build

1.  Create a `local.properties` file in the root of the project.
2.  Add your OpenRouter API key to the `local.properties` file:
    ```
    OPENROUTER_API_KEY="your_api_key"
    ```
3.  Add the path to your Android SDK to the `local.properties` file:
    ```
    sdk.dir=/path/to/your/sdk
    ```
4.  Build the project using Gradle:
    ```
    ./gradlew build
    ```
