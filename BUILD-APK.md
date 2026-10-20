# Get the Nova APK (no coding, browser only, free)

## Build it in the cloud with GitHub
1. Make a free account at github.com. Tap + > New repository > name it nova > Create.
2. Unzip Nova-v5. Open the Nova folder. Upload everything INSIDE it (not the folder itself):
   on the repo page tap "uploading an existing file" and drag the contents in. Commit.
3. If the hidden ".github" folder did not upload: tap Add file > Create new file, type
   .github/workflows/build.yml  as the name, paste the text from the bottom of this file, Commit.
4. Tap the Actions tab. Wait 5-10 minutes for a green tick on "Build Nova APK".
   Red cross? Open it, copy the red error lines, send them to Claude.
5. Open the finished run, scroll to Artifacts, download "Nova-debug-apk", unzip it. Inside: app-debug.apk.

## Install on your iQOO
1. Send app-debug.apk to your phone (USB cable, Google Drive, or email to yourself).
2. Tap it. When asked, allow your file app to "Install unknown apps".
3. If Play Protect warns, tap "Install anyway" / "More details" > "Install anyway".
4. Open Nova and follow SETUP.md (part A backend, then part B step 2 onwards).

## workflow text for step 3
(see .github/workflows/build.yml in this project)
name: Build Nova APK
on:
  push:
  workflow_dispatch:
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17
      - uses: android-actions/setup-android@v3
      - uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: 8.7
      - run: gradle assembleDebug --no-daemon
      - uses: actions/upload-artifact@v4
        with:
          name: Nova-debug-apk
          path: app/build/outputs/apk/debug/app-debug.apk
