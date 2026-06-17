# JAVIS — AI Voice Assistant for Android

> *"Just A Very Intelligent System"*

JAVIS is a Jarvis-inspired AI assistant for Android — smart, fast, and always available through the Accessibility shortcut button. Talk to it, and it actually does things.

---

## Features

| Feature | Details |
|---|---|
| **AI Chat** | Groq (llama-3.3-70b) as primary, DeepSeek as fallback |
| **AI Voice** | ElevenLabs neural voice (free tier) or built-in Android TTS |
| **Speech Recognition** | Android SpeechRecognizer with partial results |
| **Global Shortcut** | Accessibility Service shortcut — works outside the app |
| **WhatsApp Automation** | Open chats, type & send messages via Accessibility |
| **App Control** | Open any app, search web/YouTube by voice |
| **Phone Calls** | Call contacts by name via voice command |
| **Alarms** | Set alarms by voice |
| **Notification Reader** | Read from all apps — summarize, respond |
| **Memory System** | Remembers your name, prefs, habits — persisted in Room |
| **Dark UI** | Cyber-black design with cyan accents |

---

## Quick Start

### 1. Clone & Open

```bash
git clone https://github.com/yourname/javis.git
cd javis
```

Open the `javis-android` folder in **Android Studio Hedgehog (2023.1)** or newer.

### 2. Set API Keys

Create `local.properties` (copy from `local.properties.template`) and add:

```properties
sdk.dir=/path/to/Android/Sdk
GROQ_API_KEY=gsk_...         # FREE at https://console.groq.com
ELEVENLABS_API_KEY=...       # FREE tier at https://elevenlabs.io  (optional)
DEEPSEEK_API_KEY=sk-...      # Optional fallback
```

> **Groq is completely free** with a generous rate limit. Sign up at [console.groq.com](https://console.groq.com) — no credit card needed.

### 3. Build & Run

```
Build → Run 'app'   (Shift+F10)
```

Minimum API 26 (Android 8.0). Tested on Redmi A1.

---

## First Launch — Grant Permissions

The app will guide you through:

1. **Microphone** — voice input
2. **Contacts + Phone** — call by name
3. **Notification Listener** (Settings → Special App Access → Notification Access → JAVIS)
4. **Accessibility Service** (Settings → Accessibility → JAVIS → Enable)

After enabling the Accessibility Service, Android adds a **floating accessibility button** to your screen. Tap it anytime — JAVIS greets you and starts listening, even while you're in another app.

---

## Using JAVIS

| Voice Command | What happens |
|---|---|
| "Open WhatsApp" | WhatsApp opens |
| "Reply to John I'm busy" | JAVIS types the message, asks to confirm |
| "Send" | JAVIS sends it |
| "Call Mom" | Searches contacts and calls |
| "Search YouTube for lo-fi music" | Opens YouTube with that search |
| "What notifications do I have?" | Reads active notifications |
| "Set an alarm for 7am" | Opens clock with alarm |
| "Tell me a joke" | Just talks to you |

---

## Architecture

```
app/
├── ai/           # Groq + DeepSeek providers, command parser
├── accessibility/ # JavisAccessibilityService — WhatsApp + gestures
├── notifications/ # NotificationListenerService + persistence
├── memory/        # MemoryManager (Room)
├── voice/         # ElevenLabsTts, AndroidTtsFallback, SpeechRecognizer
├── data/          # Room DB, DAOs, models
├── domain/        # JavisRepository (orchestrates everything)
├── di/            # Hilt modules
├── storage/       # DataStore preferences
├── ui/
│   ├── screens/   # Chat, Memory, Notifications, Settings, Permissions
│   ├── components/ # MicButton, MessageBubble, BottomBar
│   ├── viewmodel/  # ChatVM, SettingsVM, MemoryVM, NotificationsVM
│   ├── navigation/ # JavisNavGraph
│   └── theme/      # Dark cyber theme
├── receiver/      # BootReceiver
└── utils/         # Extensions
```

**Stack:** Kotlin · Jetpack Compose · MVVM · Hilt · Room · DataStore · Retrofit · OkHttp · Coroutines · Accompanist Permissions · ExoPlayer (Media3)

---

## ElevenLabs Voice (Recommended)

1. Sign up free at [elevenlabs.io](https://elevenlabs.io) (10,000 chars/month free)
2. Copy your API key → Settings in JAVIS
3. Enable "Use ElevenLabs AI Voice"

Included voice presets (paste the ID in Settings):

| Voice | ID |
|---|---|
| **Adam** (default) — confident, clear | `pNInz6obpgDQGcFmaJgB` |
| Josh — warm, natural | `TxGEqnHWrfWFTfGW9XjX` |
| Arnold — deep, authoritative | `VR6AewLTigWG4xSOukaG` |

---

## Build APK

```
Build → Build Bundle(s) / APK(s) → Build APK(s)
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

---

## Optimized for Low-End Devices

- No overlay required (uses Android Accessibility shortcut instead)
- Lazy loading throughout
- Minimal background processes
- Room database with indexed queries
- ElevenLabs audio cached to disk before playback

---

## Privacy

- JAVIS never sends messages automatically — always asks first
- No file access without your permission
- API keys stay on your device (DataStore, never logged)
- Accessibility access is only used for app automation you explicitly trigger

---

## License

MIT — build, fork, customize freely.
