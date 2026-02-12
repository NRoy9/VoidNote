# 🌑 Void Note

**Privacy-focused notes app with Nothing design aesthetic**

*Notes that disappear into the void. Secure, minimal, yours.*

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-green.svg" />
  <img src="https://img.shields.io/badge/Language-Kotlin-blue.svg" />
  <img src="https://img.shields.io/badge/MinSDK-26-orange.svg" />
  <img src="https://img.shields.io/badge/License-MIT-red.svg" />
</p>

---

## ✨ Features

### 📝 Core Features (MVP - Implemented)
- ✅ **Rich Text Editor** - Bold, italic, underline, headings, lists, code blocks
- ✅ **Smart Folders** - Organize notes with nested folders
- ✅ **Auto-Save** - Intelligent debounced saving (500ms)
- ✅ **Pin Notes** - Keep important notes at the top
- ✅ **Archive & Trash** - Clean organization
- ✅ **Local-First** - Works offline, all data on device
- ✅ **Database Persistence** - Room + SQLite
- ✅ **Nothing Design** - Monochromatic, minimalist, OLED-friendly

### 🎨 UI/UX Highlights
- **Unified View** - Folders and notes on one screen
- **Expandable FAB** - Speed dial menu for quick actions
- **Dark Mode First** - Pure black OLED theme
- **Smooth Animations** - Polished, premium feel
- **Zero Ads, Zero Tracking** - Privacy-respecting

### 🚀 Upcoming Features
- 🔍 **Search** - Full-text search with filters
- 📸 **Image Support** - Inline images in notes
- 🔐 **Encryption** - AES-256 local encryption
- ☁️ **Cloud Sync** - Optional Google Drive backup (Premium)
- 🎙️ **Voice Notes** - Transcription (Premium)
- 🤖 **AI Features** - Auto-tags, smart titles (Free tier)

---

## 🏗️ Architecture

**Clean Architecture** with **MVVM** pattern
```
app/
├── data/              # Data layer (Room, repositories)
├── domain/            # Business logic (models, use cases)
└── presentation/      # UI layer (Compose, ViewModels)
```

### Tech Stack
- **Language:** Kotlin
- **UI:** Jetpack Compose + Material Design 3
- **Architecture:** MVVM + Clean Architecture
- **Database:** Room (SQLite)
- **DI:** Hilt (Dagger)
- **Async:** Coroutines + Flow
- **Navigation:** Jetpack Navigation Compose

---

## 📱 Screenshots

*Coming soon - App in active development*

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK (API 26-34)

### Build & Run
1. Clone the repository:
```bash
   git clone https://github.com/YOUR-USERNAME/void-note.git
```

2. Open project in Android Studio

3. Sync Gradle files

4. Run on emulator or device (Min SDK: Android 8.0 / API 26)

---

## 🎯 Roadmap

### Phase 1: MVP (Current) ✅
- [x] Notes CRUD operations
- [x] Folders system
- [x] Rich text formatting
- [x] Auto-save
- [x] Database persistence

### Phase 2: Search & Settings 🚧
- [ ] Full-text search
- [ ] Settings screen
- [ ] Theme customization
- [ ] Export notes

### Phase 3: Media & Encryption 📅
- [ ] Image support
- [ ] AES-256 encryption
- [ ] Biometric unlock
- [ ] Multiple vaults

### Phase 4: Premium Features 📅
- [ ] Google Drive sync
- [ ] Voice-to-text
- [ ] OCR
- [ ] Collaboration

---

## 🤝 Contributing

Contributions are welcome! This project is in active development.

**How to contribute:**
1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 🙏 Acknowledgments

- **Nothing Technology** - Design inspiration
- **Google Material Design** - UI components
- **Android Community** - Best practices and libraries

---

## 📧 Contact

**Developer:** GreenIcePhoenix  
**Project Link:** [https://github.com/NRoy9/VoidNote](https://github.com/NRoy9/VoidNote)

---

<p align="center">
  <i>Built with ❤️ and Kotlin</i>
</p>