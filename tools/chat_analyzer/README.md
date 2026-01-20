# AI Helper WearOS Chat Analyzer

GUI tool for analyzing chat history from the AI Helper WearOS app.

## Requirements

- Python 3.8+
- ADB (Android Debug Bridge) in PATH
- WearOS device connected via ADB

## Usage

### 1. Export from WearOS App
1. Open AI Helper on your watch
2. Go to **Settings** (⚙️)
3. Tap **📤 Export Chat**

### 2. Run the Analyzer
```bash
cd tools/chat_analyzer
python main.py
```

### 3. Retrieve Data
- Click **🔄 Refresh Devices** to detect connected devices
- Click **📥 Retrieve & Clean** to:
  - Pull the JSON from the watch
  - Automatically delete it from the watch
  - Load it into the analyzer

### Features
- 📊 Statistics: sessions, messages, avg response length, models used
- 📋 Sessions list with date and message count
- 💬 Message viewer with role highlighting
- 🗑️ Auto-cleanup from watch after retrieve
