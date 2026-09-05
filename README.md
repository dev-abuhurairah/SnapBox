# SnapDownloader - Snaptube-Styled Mobile Downloader (yt-dlp)

A modern Android video and audio downloader mobile application crafted with the iconic **Snaptube aesthetic** (rich amber yellow and sleek dark surfaces), powered directly by **yt-dlp** via embedded `youtubedl-android` (Python + FFmpeg) on the device.

---

## Key Features

* **Snaptube Visual Experience**:
  * Signature Snaptube dark mode (`#121212`) with vibrant amber yellow accents (`#FFC800`).
  * Instant search bar with clipboard auto-paste button.
  * Social platform shortcuts grid (YouTube, Instagram, TikTok, Facebook, Twitter/X, SoundCloud, Vimeo, Reddit).
* **Curated Format & Resolution Bottom Sheet**:
  * Displays video thumbnail, title, author, and duration.
  * **Music / Audio Section**: MP3 High Quality (320kbps), MP3 Standard (128kbps), M4A with estimated file size badges.
  * **Video Section**: 1080p Full HD, 720p HD, 480p SD, 360p Low with resolution tags.
  * Single-tap "Download Now" trigger.
* **In-App Browser**:
  * Browse YouTube, Instagram, TikTok, or any website directly inside the app.
  * Floating Snaptube yellow download action button lights up to extract whatever video is playing on the page!
* **Active Downloads Manager (My Files)**:
  * **Downloading Tab**: Real-time progress bar, percentage, download speed (e.g. `3.4 MB/s`), remaining time ETA, and cancel action.
  * **Finished Tab**: List of downloaded MP4 and MP3 files with thumbnail, title, direct playback via default media player, and sharing.
* **100% Standalone (Zero Server Costs)**:
  * Embedded `yt-dlp` and `ffmpeg` run directly on the Android phone.
  * Saves files directly to the public `Downloads/SnapDownloader` folder.

---

## How to Compile & Download the APK (Cloud Build via GitHub Actions)

You do **not** need to install Android Studio or gigabytes of SDKs on your computer. The repository includes an automated GitHub Actions cloud build workflow (`.github/workflows/build-apk.yml`).

### Step 1: Initialize Git and Commit
Open PowerShell inside this project folder (`C:\Users\abuhu\.gemini\antigravity\scratch\snaptube-dl-app`):

```powershell
git init
git add .
git commit -m "Initial commit of SnapDownloader app"
git branch -M main
```

### Step 2: Push to GitHub
1. Go to [github.com/new](https://github.com/new) and create a new repository (name it e.g. `snaptube-downloader`).
2. Copy your repository URL, then run:

```powershell
git remote add origin https://github.com/YOUR_USERNAME/snaptube-downloader.git
git push -u origin main
```

### Step 3: Download Your Compiled APK
1. Open your repository on GitHub in your browser.
2. Click on the **Actions** tab at the top.
3. You will see the workflow **"Compile Snaptube Downloader APK"** running.
4. When the build finishes (takes ~2 to 3 minutes), click on the completed run.
5. Under the **Artifacts** section at the bottom of the page, click **SnapDownloader-Debug-APK** to download your compiled `.apk` file!
6. Transfer the `.apk` to your phone (or download it directly from your phone's browser) and install it.

---

## Architecture & Technologies
* **Language**: Kotlin 1.9.22
* **Build System**: Android Gradle Plugin 8.2.2 / Gradle 8.5
* **Core Engine**: `io.github.junkfood02.youtubedl-android:library:0.18.1` & `ffmpeg:0.18.1`
* **UI**: Material Design 3, ViewBinding, Glide
* **Target SDK**: 34 (Android 14) / Min SDK: 24 (Android 7.0+)
