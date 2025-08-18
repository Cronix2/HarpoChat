<p align="center">
    <img src="app\assets\logo.png" align="center" width="30%">
</p>

<p align="center">
    <h1 align="center">HarpoChat</h1>
</p>

<p align="center">
    <em><code>❯ Experimental encrypted chat application built with Kotlin, Jetpack Compose, and the Signal Protocol</code></em>
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
    <img src="https://img.shields.io/badge/Signal&nbsp;Protocol-000000.svg?style=flat&logo=signal&logoColor=white" alt="Signal Protocol">
</p>

---

## 🔗 Table of Contents

- [🔗 Table of Contents](#-table-of-contents)
- [📍 Overview](#-overview)
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

**HarpoChat** is an experimental Android chat application built in **Kotlin** and **Jetpack Compose**.  It demonstrates how to integrate true **end‑to‑end encryption (E2EE)** using the open‑source [Signal Protocol](https://signal.org/docs/), while keeping the rest of the stack simple.  Messages are composed in a Compose UI, encrypted on the sender device, decrypted on a simulated peer, and displayed in plain text.  The project uses **Kotlin coroutines** and **StateFlow** to reactively update the UI.  A secure local store based on Android’s `EncryptedSharedPreferences` provides a safe place for keys and other sensitive values.  This proof‑of‑concept is a starting point for building secure messaging apps and illustrates how to wire together encryption, data flows and modern Android UI components.

Key components include:

- A **Jetpack Compose** UI with a `LazyColumn` chat log and input bar.
- A `ChatRepository` that tracks messages and invokes the encryption engine.
- A pluggable **CryptoEngine** interface with a `SignalCryptoEngine` implementation based on the **signal-protocol-java** library.
- A `SecureStore` wrapper around `EncryptedSharedPreferences` and `MasterKey` for storing secrets.
- Gradle build scripts targeting **SDK 36**, with Kotlin 17 and Compose enabled.

This repository currently contains only a single commit that integrates the Signal encryption engine and associated dependencies.  As such it should be regarded as a minimal demonstration rather than a production‑ready chat system.

---

## 👾 Features

- ✅ **End‑to‑End Encryption** – messages are encrypted on the sender and decrypted on the receiver using the Signal Protocol.
- 📱 **Jetpack Compose UI** – simple, reactive chat interface implemented with Compose’s `LazyColumn`, `ElevatedCard` and Material3 components.
- 🔄 **Reactive State** – messages are exposed as a Kotlin **StateFlow** and collected in the UI to update automatically.
- 🔐 **Secure Key Storage** – secrets are persisted using `EncryptedSharedPreferences` with a generated `MasterKey`.
- ⚡ **Kotlin Coroutines** – background work (encryption, decryption) runs off the main thread, keeping the UI responsive.
- 🔌 **Signal Protocol Integration** – uses the `signal-protocol-java` library to generate identities, prekeys, sessions and encrypt/decrypt messages.
- 🧰 **Gradle & Kotlin DSL** – configured via `build.gradle.kts` with Compose enabled and dependencies declared in a version catalog.

---

## 📁 Project Structure

```
HarpoChat/
├── LICENSE
├── README.md
├── build.gradle.kts
├── gradle.properties
├── settings.gradle.kts
├── gradlew
├── gradlew.bat
├── gradle/
│   └── … (wrapper and version catalog files)
└── app/
    ├── build.gradle.kts          # Module-level Gradle script
    ├── proguard-rules.pro        # ProGuard rules (currently empty)
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/example/harpochat/
        │   │   ├── MainActivity.kt           # Entry point; wires ViewModel & Compose
        │   │   ├── crypto/
        │   │   │   ├── CryptoEngine.kt       # Interface + Fake implementation
        │   │   │   ├── SignalCryptoEngine.kt # Signal-based engine
        │   │   │   └── SignalStores.kt       # Key generation & stores
        │   │   ├── data/
        │   │   │   └── ChatRepository.kt      # Holds messages and performs E2EE
        │   │   ├── security/
        │   │   │   └── SecureStore.kt         # Secure SharedPreferences wrapper
        │   │   └── ui/
        │   │       ├── ChatScreen.kt          # Compose UI and ChatMessage model
        │   │       ├── ChatViewModel.kt       # Exposes messages & send()
        │   │       └── theme/
        │   │           ├── Color.kt
        │   │           ├── Theme.kt
        │   │           └── Type.kt
        │   └── res/                            # Application resources
        ├── test/                              # Unit tests (currently empty)
        └── androidTest/                       # Instrumentation tests (empty)
```

---

## 🚀 Getting Started

### ☑️ Prerequisites

To build and run HarpoChat you will need:

- **JDK 17** – the project targets Java 17.
- **Android Studio** (Hedgehog or later) or the command‑line **Gradle** wrapper.
- An installed **Android SDK**; `compileSdk` is set to 36 and `minSdk` to 26.
- A device or emulator running Android 8.0 (API 26) or higher.

### ⚙️ Installation

Clone this repository:

```sh
git clone https://github.com/Cronix2/HarpoChat.git
cd HarpoChat
```

Open the project in Android Studio and let it download dependencies, or build from the command line:

```sh
./gradlew assembleDebug
```

The `assembleDebug` task will compile the app and produce an APK in `app/build/outputs/apk/debug/`.

### 🤖 Usage

Run the app on an emulator or physical device (API 26+).  You will be presented with a simple chat screen.  Type a message in the text field and press **Envoyer**.  The `ChatRepository` will:

1. Encrypt the plaintext using the Signal engine, generating a ciphertext.
2. Decrypt the ciphertext on a simulated peer to obtain the original message.
3. Update the `messages` flow, causing the UI to display both your sent message and an echo with the size of the encrypted payload.

Currently HarpoChat operates entirely in memory and on one device (a “loopback” demo).  No network communication or persistent storage beyond the cryptographic identity is included.

---

## 🧪 Future Testing

HarpoChat is a prototype, and **no automated tests are currently implemented**.  Future work could include:

- Unit tests for encryption/decryption routines and message formatting.
- Instrumentation tests to verify Compose UI behaviour and ViewModel lifecycles.
- UI tests using Espresso or Compose Test Kit.

---

## 📌 Project Roadmap

This project is at an early stage.  Ideas for future improvements include:

- **Real networking** – integrate a server or peer‑to‑peer transport so that messages are exchanged between multiple devices rather than looped back locally.
- **User accounts & authentication** – allow users to register, exchange prekeys and manage contacts.
- **Persistent storage** – store chat history securely using a local database (e.g. Room) instead of keeping messages in memory.
- **Group chats & attachments** – support sending media, files and group conversations.
- **Improved UI/UX** – refine the Compose UI, add dark mode, message bubbles, avatars and notification support.
- **Key management** – integrate secure backup/restore of identity keys and optional biometric unlock.

Contributions or suggestions for additional features are welcome!  See the section below for details on how to help.

---

## 🔰 Contributing

Contributions are very welcome!  To contribute:

1. **Fork** the repository on GitHub.
2. **Clone** your fork:

   ```sh
   git clone https://github.com/<your-username>/HarpoChat.git
   cd HarpoChat
   ```

3. **Create a new branch** for your changes:

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

Please follow the existing code style and include tests where relevant.  For major changes, please open an issue first to discuss what you would like to change.

---

## 🎗 License

This project is licensed under the **MIT License**.  See the [LICENSE](./LICENSE) file for more details.

---

🚀 **Thank you for exploring HarpoChat!**  If this project inspires you to build secure messaging experiences or if you learn something useful, consider starring the repository and sharing your feedback.
