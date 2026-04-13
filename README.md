# LM Studio Chat

An Android chat app for talking to local [LM Studio](https://lmstudio.ai/) servers over your network. Add connection profiles, pick a model, and chat — responses stream in real time.

## Features

- **Multiple connections** — save and switch between different LM Studio servers
- **Streaming responses** — tokens appear as they're generated, with a stop button to cancel mid-stream
- **Chat history** — sessions are saved per connection; browse, load, or delete past chats
- **Chat renaming** — tap the chat title to rename any session
- **ngrok support** — toggle ngrok mode per connection to skip the browser warning header
- **Markdown rendering** — bold, italic, inline code, code blocks, and headings rendered in-chat
- **Text-to-speech** — listen to any assistant message
- **Export to Markdown** — save any chat as a `.md` file
- **Three themes** — Light, Dark, and Claude

## Requirements

- Android 7.0+ (API 24)
- [LM Studio](https://lmstudio.ai/) running locally with the local server enabled
- The device and the LM Studio machine must be on the same network (or reachable via ngrok/VPN)

## Download

Grab the latest APK from the [Releases](../../releases) page and install it directly on your device.  
> Enable **Install from unknown sources** in your Android settings if prompted.

## Build from Source

```bash
# Clone the repo
git clone https://github.com/guberm/LMStudioChatApp.git
cd LMStudioChatApp

# Debug APK
./gradlew assembleDebug

# Install directly to connected device
./gradlew installDebug
```

Output APK: `app/build/outputs/apk/debug/app-debug.apk`

Requires Android Studio / Android SDK with API 34 build tools.

## Setup

1. Open LM Studio → **Local Server** tab → Start the server
2. Note the IP address shown (e.g. `192.168.1.100`) and port (default `1234`)
3. In the app, tap **+** to add a connection — enter the IP, port, and a name
4. Tap the connection card → select a model → tap **Launch Interface**

For remote access via ngrok, enable **ngrok mode** on the connection and paste the ngrok hostname (without `https://`) into the IP field, with port `443`.

## Architecture

Four source files, MVVM:

| File | Role |
|---|---|
| `MainActivity.kt` | All Composables and screen routing |
| `ChatViewModel.kt` | State, business logic, streaming |
| `LmStudioApiClient.kt` | Retrofit + SSE streaming |
| `PreferencesManager.kt` | JSON ↔ SharedPreferences persistence |

## License

MIT
