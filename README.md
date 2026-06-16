# JAVIS — Just A Very Intelligent System

> "At your service, as always."

JAVIS is a modern AI voice assistant for Android — intelligent, witty, and subtly sarcastic, modelled after J.A.R.V.I.S from Iron Man. Built with Kotlin and Jetpack Compose, it combines Android SpeechRecognizer, TextToSpeech (tuned for a deep, authoritative voice), and Groq's Llama 3.3 70B for near-instant responses. Optimised for low-end devices like the Redmi A1.

---

## Quick Start (3 steps)

### 1. Clone
```bash
git clone https://github.com/YOUR_USERNAME/javis.git
cd javis
```

### 2. Set up your API key
Create `local.properties` (already gitignored):
```properties
sdk.dir=/path/to/your/android/sdk
GROQ_API_KEY=your_groq_key_here
```
Get a **free** Groq key at → **console.groq.com** (takes 30 seconds)

**Or** skip this and add the key at runtime in the app: **Settings → API Keys**

### 3. Build & Run
Open in **Android Studio Hedgehog** or newer → click **Run ▶**

---

## Push to GitHub + Auto-Build APK

### Step 1 — Create a GitHub repo
1. Go to github.com → New repository → name it `javis`
2. Don't initialise with README (you have one)

### Step 2 — Push the code
```bash
cd javis
git init
git add .
git commit -m "Initial JAVIS release"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/javis.git
git push -u origin main
```

### Step 3 — Add your API key as a GitHub Secret
1. Go to your repo on GitHub
2. **Settings → Secrets and variables → Actions → New repository secret**
3. Name: `GROQ_API_KEY`
4. Value: your Groq API key
5. Click **Add secret**

### Step 4 — GitHub Actions compiles your APK automatically
Every time you push to `main`, GitHub Actions will:
- Build a **debug APK** (ready to sideload)
- Build a **release APK** (optimised)
- Upload both as downloadable artifacts

**Download your APK:**
1. Go to your repo → **Actions** tab
2. Click the latest workflow run
3. Scroll down to **Artifacts** → download `JAVIS-debug-apk`
4. Unzip → install on your Android device

### Step 5 — Create a versioned release (optional)
```bash
git tag v1.0.0
git push origin v1.0.0
```
This triggers a GitHub Release with the APK attached automatically.

---

## Install on Device (Sideload)

1. On your Android phone: **Settings → Security → Unknown Sources** → enable
2. Transfer the APK via USB, WhatsApp, or Google Drive
3. Tap the APK file → Install
4. Open JAVIS → go to **Settings → API Keys** → enter your Groq key (if not baked in)
5. Tap the microphone and say *"Hello JAVIS"*

---

## Features

| | Feature | Description |
|---|---|---|
| 🎙️ | Voice | Push-to-talk SpeechRecognizer — tap, speak, listen |
| 🔊 | Jarvis Voice | Deep pitch, British-first TTS — authoritative and smooth |
| 🤖 | AI | Groq Llama 3.3 70B + DeepSeek — near-instant responses |
| 😏 | Personality | Witty, dry humour, slightly sarcastic — never robotic |
| 📱 | App Launcher | 25+ apps: WhatsApp, YouTube, Spotify, Instagram, Maps... |
| 🧠 | Memory | Remembers your name, preferences, app usage via Room DB |
| 🔔 | Notifications | Reads & summarises notifications aloud |
| ♿ | Accessibility | Optional service for active-app detection |
| ⚙️ | Settings | Speech rate, voice picker, provider switch, data management |

---

## Voice Personality Examples

| You say | JAVIS might say |
|---|---|
| "Hello" | "Good to hear from you. What chaos shall we prevent today?" |
| "Open WhatsApp" | "Connecting you to the world's favourite distraction." |
| "Open YouTube" | "Enabling the primary productivity destroyer. You're welcome." |
| "Open settings" | "Settings — the last refuge of the curious and the perpetually lost." |
| "Who are you?" | "I am JAVIS — Just A Very Intelligent System. Your personal AI, at your service. The modest name was my idea." |

---

## App Launch Support (25+ apps)

WhatsApp · Telegram · Instagram · Twitter/X · Facebook · Snapchat · TikTok  
YouTube · Netflix · Spotify · Chrome · Google Maps · Gmail · Play Store  
Calendar · Contacts · Files · Gallery · Calculator · Camera · Clock  
Phone/Dialer · Messages · Amazon · Uber · PayPal · Zoom

---

## Architecture

```
MVVM + Repository Pattern + Hilt DI
├── UI Layer       → Jetpack Compose + Material3 dark theme
├── Domain Layer   → Repository interfaces + Domain models
├── Data Layer     → Room DB + Retrofit (Groq/DeepSeek) + DataStore
└── Services       → NotificationListenerService + AccessibilityService
```

---

## Tech Stack

Kotlin · Jetpack Compose · Material3 · Hilt · Room · Retrofit · OkHttp  
DataStore · Coroutines + Flow · Groq API · DeepSeek API

**Minimum:** Android 8.0 (API 26) — optimised for low-end hardware

---

## Security

JAVIS never sends messages automatically, reads private data without permission, or changes settings without confirmation. All sensitive permissions are requested at runtime with clear explanations. Your API key lives in `local.properties` (gitignored) or GitHub Secrets — never in committed code.

---

## License

MIT — see [LICENSE](LICENSE)
