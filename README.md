# SnapSearch

SnapSearch is an Android app that lets a user search their own photo and screenshot library using natural language and visual similarity, **fully on-device**. No cloud, no account, no server. Just fast, private, offline search.

## What This Project Is

Instead of scrolling through thousands of images, you can type something like *"receipt from last week"* or *"that error message screenshot"*, and the app returns matching images using:
- **Visual similarity search** (CLIP-style image embeddings)
- **Text search** over OCR'd text and auto-generated tags/captions

All inference happens locally on your phone.

### Architecture Overview

SnapSearch is designed with a two-tier architecture to balance app size and capability:

- **Tier 1 (Base App)**: A lean, always-offline base app under 100MB. It uses MobileCLIP for visual and text search and ML Kit for OCR. It provides zero-shot tags instead of full sentences.
- **Tier 2 (Optional Upgrade)**: A downloadable upgrade pack (~240-265MB) that brings real generative captions (SmolVLM-256M) and better text embedding (all-MiniLM-L6-v2) for users who want richer, full-sentence captions.

### Target Device Profile
- **Android 10+ (API 29+)**
- **arm64-v8a only** 

## Configuration

This project is built using modern Android development practices with Kotlin, Jetpack Compose, and Room. 

### Local Properties
Ensure your SDK path is configured in `local.properties`:
```properties
sdk.dir=C\:\\Users\\[YourUser]\\AppData\\Local\\Android\\Sdk
```

### Gradle Properties
The project requires **JDK 25** (Eclipse Adoptium `jdk-25.0.3.9-hotspot`) for its Gradle daemon, as defined in `gradle.properties`:
```properties
org.gradle.java.home=C\:\\Program Files\\Eclipse Adoptium\\jdk-25.0.3.9-hotspot
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
```
*Note: Make sure your `org.gradle.java.home` path points to your valid JDK 25 installation. If you use a different JDK version, you can update this path.*

## How to Run This Project

### Using Android Studio (Recommended)
1. Open Android Studio.
2. Select **File > Open** and navigate to the `SnapSearch` project directory.
3. Wait for the Gradle sync to finish.
4. Select the `app` run configuration and press the **Run** button (or `Shift + F10`) with a physical device or emulator connected. Note that the emulator or device must use an **arm64** architecture.

### Using the Command Line
To build and install the app from the terminal via Gradle, use:

```bash
# To build a debug APK
./gradlew assembleDebug

# To install the debug APK directly to a connected device
./gradlew installDebug
```
*(On Windows, use `gradlew.bat` instead of `./gradlew`)*

## Contributing

We welcome contributions to SnapSearch! To contribute, please follow these steps:

1. **Clone the Repository** (or your fork) locally.
2. **Create a New Branch** for your feature or bug fix:
   ```bash
   git checkout -b feature/your-feature-name
   # or
   git checkout -b bugfix/your-bugfix-name
   ```
3. **Make Your Changes**: Write your code, and ensure it builds correctly.
4. **Commit Your Changes**: Use clear and descriptive commit messages.
   ```bash
   git commit -m "Add your descriptive commit message here"
   ```
5. **Push to Your Branch**:
   ```bash
   git push origin feature/your-feature-name
   ```
6. **Submit a Pull Request**: Open a PR against the `main` branch. Provide a clear description of the problem you're solving and the changes you've made.

## License

This project is licensed under the [MIT License](LICENSE).
