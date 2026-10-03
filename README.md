# AIChat

A simple, fully offline desktop chat for local LLMs.

Built with **Java 25** and **JavaFX 25**, powered by **[Ollama](https://ollama.com)**
through **[LangChain4j](https://github.com/langchain4j/langchain4j)**.

## Why

Most chat apps send your data to the cloud. This one doesn't.
Everything runs locally — no internet, no telemetry, no leaks.
Perfect for experimenting with LLMs on confidential data.

## Features

- Fully offline — runs against a local Ollama instance
- Model switching from the UI
- Conversation history with system prompt
- Configurable base URL, timeout and temperature
- Response time per message
- Clean separation of UI, chat logic and LLM service

## Requirements

- **Java 25** or newer — [Download](https://jdk.java.net/25/)
- **Maven 3.9+** — [Download](https://maven.apache.org/download.cgi)
- **Ollama** — see below

## Installing Ollama

Ollama runs the LLM locally on your machine and exposes a small HTTP API
on `http://127.0.0.1:11434`. The app connects to that API.

### Windows

1. Download the installer from [ollama.com/download](https://ollama.com/download).
2. Run `OllamaSetup.exe` and follow the wizard.
3. Ollama starts in the background and adds the `ollama` command to `PATH`.
4. Open a **new** terminal and verify:
   ollama --version

### macOS

1. Download `Ollama-darwin.zip` from [ollama.com/download](https://ollama.com/download).
2. Unzip and drag `Ollama.app` into `Applications`.
3. Launch it once — the Ollama icon appears in the menu bar.
4. Verify in a terminal:
   ollama --version

### Linux

Run the official install script:

```bash
curl -fsSL https://ollama.com/install.sh | sh
```
Then verify:
```
ollama --version
```

To start the server manually (if not already running as a service):

bash
```
ollama serve
```

### Downloading models
Models are pulled separately from the Ollama engine. You need at least one.

### Recommended models
```
Model	    Size	    Notes
qwen2.5:7b	~4.7 GB	    Best balance for Russian and English
llama3.1:8b	~4.9 GB	    Strong general-purpose model
gemma2:2b	~1.6 GB	    Very small, good for low-RAM machines
gemma2:9b	~5.4 GB	    Larger, slower, better quality
```

### Pull a model
bash
```
ollama pull qwen2.5:7b
```
This downloads the model into Ollama's local storage. After that, the app
can use it even without an internet connection.

### List installed models
bash
```
ollama list
```
Example output:

text
```
NAME              ID              SIZE      MODIFIED
qwen2.5:7b        xxxxxxxxxxxx    4.7 GB    2 minutes ago
llama3.1:8b       xxxxxxxxxxxx    4.9 GB    1 hour ago
```

### Remove a model (optional)
bash
```
ollama rm qwen2.5:7b
```

### Running the app
From the project root:

bash
```
mvn clean javafx:run
```
Or, in IntelliJ IDEA, run the Launcher class directly.

### VM options

Two JVM flags are recommended when running the app:

- `--enable-native-access=javafx.graphics` — required by JDK 24+ for JavaFX's native calls.
  Without it, the JVM prints a "restricted method" warning on startup.
- `-Dorg.slf4j.simpleLogger.defaultLogLevel=debug` — enables debug logs.
  Optional; useful when troubleshooting.

Optional, to print logs to stdout instead of stderr:

- `-Dorg.slf4j.simpleLogger.logFile=System.out` — makes INFO logs appear in white
  instead of red in IntelliJ's console. Purely cosmetic.

#### IntelliJ IDEA

**Run → Edit Configurations → VM options**, paste:

### First request is slow
The first time you send a message, Ollama loads the model into memory.
This can take 10–30 seconds depending on your hardware. Subsequent
requests are much faster.
