<p align="center">
    <img src="app/assets/logo.png" align="center" width="30%">
</p>

<p align="center">
    <h1 align="center">HarpoChat</h1> </p>

<p align="center">
    <em><code>❯ Discreet end‑to‑end encrypted chat disguised as a full‑featured scientific calculator</code></em>
</p>

<p align="center">
    <img src="https://img.shields.io/github/license/Cronix2/HarpoChat?style=for-the-badge&logo=opensourceinitiative&logoColor=white&color=0080ff" alt="License">
    <img src="https://img.shields.io/github/last-commit/Cronix2/HarpoChat?style=for-the-badge&logo=git&logoColor=white&color=0080ff" alt="Last Commit">
    <img src="https://img.shields.io/github/languages/top/Cronix2/HarpoChat?style=for-the-badge&color=0080ff" alt="Top Language">
    <img src="https://img.shields.io/github/languages/count/Cronix2/HarpoChat?style=for-the-badge&color=0080ff" alt="Languages Count">
</p>

<p align="center">
    <em>Developed with the tools below.</em>
</p>

<p align="center">
    <img src="https://img.shields.io/badge/Kotlin-0095D5.svg?style=flat&logo=Kotlin&logoColor=white" alt="Kotlin">
    <img src="https://img.shields.io/badge/Jetpack&nbsp;Compose-4285F4.svg?style=flat&logo=Jetpack-Compose&logoColor=white" alt="Jetpack Compose">
    <img src="https://img.shields.io/badge/Gradle-02303A.svg?style=flat&logo=Gradle&logoColor=white" alt="Gradle">
    <img src="https://img.shields.io/badge/Coroutines-00599C.svg?style=flat&logo=kotlin&logoColor=white" alt="Coroutines">
    <img src="https://img.shields.io/badge/Room-007ACC.svg?style=flat&logo=sqlite&logoColor=white" alt="Room">
    <img src="https://img.shields.io/badge/SQLCipher-5B4E51.svg?style=flat&logo=sqlite&logoColor=white" alt="SQLCipher">
    <img src="https://img.shields.io/badge/Signal&nbsp;Protocol-000000.svg?style=flat&logo=signal&logoColor=white" alt="Signal Protocol">
</p>

---

## 🔗 Table of Contents

- [🔗 Table of Contents](#-table-of-contents)
- [📍 Overview](#-overview)
- [🧙 Name Origin](#-name-origin)
- [👾 Features](#-features)
- [📁 Project Structure](#-project-structure)
- [🚀 Getting Started](#-getting-started)
  - [☑️ Prerequisites](#️-prerequisites)
  - [⚙️ Installation](#️-installation)
  - [🤖 Usage](#-usage)
- [🧪 Future Testing](#-future-testing)
- [📌 Project Roadmap](#-project-roadmap)
- [🔰 Contributing](#-contributing)
- [🎗 License](#-license)

---

## 📍 Overview

**HarpoChat** is an Android application that hides a modern, end‑to‑end encrypted messenger behind the guise of a **full‑featured scientific calculator**.  The app opens as a convincing calculator with basic and advanced operations; only by entering a secret PIN does the messaging interface reveal itself.  A secondary *duress* PIN instantly wipes the encrypted database and preferences.  Once unlocked, users are presented with a conversation list and can chat using a Compose‑based UI backed by persistent storage.

The chat layer uses a **Room** database wrapped with **SQLCipher** so that all stored messages and threads are encrypted.  A passphrase for the database is generated once and stored in Android’s `EncryptedSharedPreferences` via the `DbCrypto` helper.  Conversations are exposed as **Flows** so that the UI reacts automatically to new messages.  A lightweight network simulator in the repository acknowledges outgoing messages, updates their status and echoes a reply, paving the way for future real networking.

---

## 🧙 Name Origin

The name **HarpoChat** pays homage to **Harpocrates**, the Greco‑Roman god of silence and secrets.  Greeks and Romans interpreted statues of the Egyptian child‑god **Horus** holding a finger to his lips as a gesture of secrecy.  This misunderstanding transformed Harpocrates into a deity representing confidentiality and discreet communication.  Naming the project after Harpocrates underscores its focus on keeping conversations hidden and private.

---

## 👾 Features

- 🔐 **Secret & Duress PINs** – Enter a **secret PIN** on the calculator to reveal the messaging UI.  Enter the **duress PIN** to erase the encrypted database and clear all preferences.
- 🧮 **Complete Scientific Calculator** – Supports basic arithmetic, memory functions (MC, M+, M–, MR) and advanced operations such as powers, roots, factorial, inverse, reciprocals, trigonometric functions with radian/degree and inverse modes, constants like π and e, percentage and more.  The layout adapts between portrait and landscape, with landscape exposing the scientific keys.
- 📱 **Conversation List** – A dedicated screen lists existing conversations with avatar placeholders, last messages, timestamps and unread counts.  Tapping a row opens the corresponding chat.
- 💬 **Persistent Messaging** – Messages and threads are stored in a local Room database and encrypted using SQLCipher.  The `ChatRepository` exposes a `messages(threadId)` flow that maps database entities to UI models.  Sending a message inserts it as `SENDING`, then simulates acknowledgement and an incoming reply.  Message statuses (Sending, Sent, Delivered, Read) are tracked in the database.
- 🔑 **Secure Storage** – A random 32‑byte passphrase for SQLCipher is generated and stored encrypted using `EncryptedSharedPreferences` so only the app can decrypt the database.  The duress PIN removes this passphrase, rendering the database unreadable.
- 🔄 **Reactive UI** – Kotlin Coroutines and `StateFlow` are used to emit message lists, while Jetpack Compose collects these flows and updates the UI automatically.
- 🛠️ **Modular Architecture** – The project is organised into clear packages: `calculator` for the UI façade, `messaging` for conversation lists, `ui` for chat screens, `data` for Room/SQLCipher logic, `crypto` for end‑to‑end encryption utilities, and `security` for key storage.  This separation makes it easy to extend or replace components.
- 📡 **Future‑Ready Encryption** – Although the current version uses only local encryption, a pluggable `CryptoEngine` interface and a `SignalCryptoEngine` implementation remain in the codebase, laying the groundwork for true end‑to‑end encryption with the Signal Protocol in future revisions.

---

## 📁 Project Structure

```text
HarpoChat/
├── LICENSE
├── README.md
├── build.gradle.kts
├── gradle.properties
├── gradle/                    # Wrapper and version catalog
├── gradlew / gradlew.bat
└── app/
    ├── build.gradle.kts       # Module‑level Gradle script
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml       # Declares activities
        │   ├── java/com/example/harpochat/
        │   │   ├── calculator/
        │   │   │   └── CalculatorActivity.kt  # Scientific calculator with PIN logic
        │   │   ├── messaging/
        │   │   │   └── ConversationsActivity.kt  # Conversation list UI
        │   │   ├── data/
        │   │   │   ├── AppDatabase.kt         # Room DB with SQLCipher
        │   │   │   ├── DbCrypto.kt            # Generates encrypted DB passphrase & duress wipe
        │   │   │   ├── Dao.kt                 # DAO interfaces for threads and messages
        │   │   │   ├── Entities.kt            # ThreadEntity and MessageEntity definitions
        │   │   │   ├── Model.kt               # ChatMessage model and enums
        │   │   │   └── ChatRepository.kt      # Handles storage, message status and network simulation
        │   │   ├── ui/
        │   │   │   ├── ChatScreen.kt          # Compose UI for chat bubbles and input
        │   │   │   ├── ChatViewModel.kt       # ViewModel exposing message flow and send/openThread
        │   │   ├── ChatActivity.kt            # Activity to host ChatScreen for a given thread
        │   │   ├── MainActivity.kt            # Entry point (currently opens a default chat)
        │   │   ├── crypto/                    # Signal Protocol integration (future use)
        │   │   │   ├── CryptoEngine.kt        # Interface and fake engine
        │   │   │   ├── SignalCryptoEngine.kt  # Signal-based implementation
        │   │   │   └── SignalStores.kt        # Key generation & stores
        │   │   └── security/
        │   │       └── SecureStore.kt         # Wrapper around EncryptedSharedPreferences
        │   └── res/                           # Resources (themes, icons, layouts)
        ├── test/                              # Unit tests (empty)
        └── androidTest/                       # Instrumentation tests (empty)
```

---

## 🚀 Getting Started

### ☑️ Prerequisites

To build and run **HarpoChat** you will need:

- **JDK 17** – the project targets Java 17.
- **Android Studio Hedgehog** or later, or the command‑line **Gradle** wrapper.
- **Android SDK** – `compileSdk` is 36 and `minSdk` is 26.
- A device or emulator running **Android 8.0 (API 26)** or higher.

### ⚙️ Installation

Clone the repository and build the app:

```sh
git clone https://github.com/Cronix2/HarpoChat.git
cd HarpoChat
./gradlew assembleDebug
```

The debug APK will be generated in `app/build/outputs/apk/debug/`.

### 🤖 Usage

1. **Launch the app.**  It opens on a dark‑themed scientific calculator.  Use it as you would any calculator – with memory keys and scientific functions available in landscape.
2. **Unlock the chat.**  Enter your **secret PIN** (default `527418`) and press the equals key (`=`).  This will navigate to the conversation list.  Enter the **duress PIN** (default `1234`) to wipe the encrypted database and preferences.
3. **Select a conversation.**  Tap a chat preview to open it.  A top bar shows the contact’s name and an avatar.  Your messages appear in blue bubbles on the right; incoming messages appear in grey bubbles on the left.
4. **Send messages.**  Type your message into the text field and press **Envoyer**.  Messages are inserted into the database as `SENDING`, then marked `SENT` after 250 ms and echoed back as a reply after another 600 ms.  Status icons update accordingly.
5. **Return to calculator.**  Press the back button in the chat header to return to the conversation list or calculator façade.

*Note:* Real network communication and contact management are not yet implemented.  Conversations are local and simulated.

---

## 🧪 Future Testing

There are currently **no automated tests**.  Potential tests include:

- Unit tests for calculator operations, PIN validation and database encryption logic.
- Unit and instrumentation tests for `ChatRepository` to verify message status transitions and persistence.
- UI tests using Espresso or Compose Test Kit for conversation navigation and chat interactions.

---

## 📌 Project Roadmap

- **True End‑to‑End Encryption:** Integrate the existing `SignalCryptoEngine` with the new database layer so that plaintext messages are never stored; encrypt/decrypt messages on send/receive.
- **Real Networking:** Replace the network simulator with actual peer‑to‑peer or server‑mediated message exchange.
- **Conversation Creation & Management:** Add UI and logic to create new threads, manage contacts and group chats.
- **Secure PIN Configuration:** Provide a settings screen to change the secret and duress PINs and to configure biometric unlock.
- **Rich Media & Attachments:** Support sending images, files and voice messages.
- **Improved Calculator:** Add parentheses support and additional scientific functions and refine animations.
- **Persistent App Settings:** Persist dark/light mode preferences and custom themes.

---

## 🔰 Contributing

Contributions are welcome!  To contribute:

1. **Fork** the repository on GitHub.
2. **Clone** your fork:

   ```sh
   git clone https://github.com/<your-username>/HarpoChat.git
   cd HarpoChat
   ```
3. **Create a new branch** for your feature or fix:

   ```sh
   git checkout -b your-feature-name
   ```
4. **Make your changes** and commit them with clear messages:

   ```sh
   git commit -m "Add awesome feature"
   ```
5. **Push** the branch to your fork:

   ```sh
   git push origin your-feature-name
   ```
6. **Open a Pull Request** describing your changes and why they should be merged.

Please follow the existing code style and include tests where relevant.  For major changes, open an issue first to discuss your proposal.

---

## 🎗 License

This project is licensed under the **MIT License**.  See the [LICENSE](./LICENSE) file for details.

---

🚀 **Thank you for exploring HarpoChat!**  If this project sparks ideas for privacy‑focused communication or hidden apps, please consider starring the repository and sharing your feedback.
