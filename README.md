# GeoCam

---

GeoCam is an open source and ad free alternative for GPS map camera. This app is vibe-coded with the help of Claude Code, GitHub Copilot and OpenCode. I am still new to vibe coding so any reports on bugs is appreciated, and will try my best to fix it.

---

## Features

---

1. Configurable mini map tile size (3 sizes)
2. Selfie camera support
3. Inbuilt gallery for quick checking and deleting
4. Location reload button for force refresh current location

---

## Two versions

---

There are two builds of GeoCam, made from the same code. They install side by side, so you can try both.

|  | **GeoCam** | **GeoCam FLOSS** |
|---|---|---|
| Package | `dev.geocam.app` | `dev.geocam.app.floss` |
| Location provider | Google Play fused location | Android's own `LocationManager` |
| Mini-map tiles | Google | OpenStreetMap |
| Satellite imagery | Yes | No |
| Proprietary dependencies | Yes (Play Services) | **None** |
| APK size | 4.2 MB | 3.9 MB |
| Available on | GitHub Releases | GitHub Releases, F-Droid (pending) |

### Which should I install?

**Pick GeoCam if** you want the best possible satellite imagery and slightly faster position fixes, and you have a normal phone with Google apps.

**Pick GeoCam FLOSS if** you care about not depending on Google, use a de-Gooed ROM, or want something installable from F-Droid.

#### GeoCam — pros and cons

**Pros**
- Satellite imagery in the mini-map
- Fused location is fast and works well in poor signal
- Deeper tile detail available

**Cons**
- Depends on Google Play Services, which is proprietary
- Will not work properly on ROMs without Google apps
- The Google tile endpoint is undocumented and could stop working at any time
- Not eligible for F-Droid

#### GeoCam FLOSS — pros and cons

**Pros**
- **Zero proprietary code** — nothing from Google or anyone else
- Works on any Android device, including de-Gooed ROMs
- Uses OpenStreetMap, whose data is openly licensed
- Can be installed from F-Droid with no Google account and no tracking
- Slightly smaller download

**Cons**
- Street map only — there is no free, open satellite imagery to use
- Tile detail is capped at zoom 19
- Raw `LocationManager` can be a little slower to get an initial fix
- Depends on the OpenStreetMap tile servers being reachable

> **Accuracy note:** in everyday testing the two are very close. The FLOSS build's positioning was surprisingly good, but Google Play's fused provider does have an edge in dense urban areas where raw GPS struggles.

---

## Screenshots

---

<!-- Drop your screenshots in the /screenshots folder and reference them here. -->
<!-- Android Studio: right-click res/drawable > New > Image Asset, or just screenshot on a phone. -->

| Main screen | Gallery | Settings |
|---|---|---|
| _coming soon_ | _coming soon_ | _coming soon_ |

---

## Building from source

---

You need **JDK 21**. Android's lint tooling fails on newer JDKs (26 and above), so a release build will crash with an obscure `lintVitalAnalyzeRelease` error otherwise.

```bash
git clone https://github.com/DeessPanda/GeoCam.git
cd GeoCam

# Google build
JAVA_HOME=/path/to/jdk-21 ./gradlew packageReleaseApk

# FLOSS build
JAVA_HOME=/path/to/jdk-21 ./gradlew packageFlossApk
```

Each task writes a signed release APK into the project root:

```
GeoCam v1.0.apk
GeoCam v1.0 FLOSS.apk
```

### Signing

Release signing reads from a `keystore.properties` file in the project root, which is gitignored. It is optional — without it the release build is simply left unsigned, so anyone can clone and build without your key.

```properties
storeFile=/absolute/path/to/your-release.jks
storePassword=your-store-password
keyAlias=your-alias
keyPassword=your-key-password
```

Create a key with `keytool` if you need one:

```bash
keytool -genkeypair -v -keystore geocam-release.jks -alias geocam \
  -keyalg RSA -keysize 4096 -validity 10000
```

**Keep that keystore safe.** It is the app's permanent identity. If you lose it, future updates cannot install over the ones people already have.

---

## Reporting bugs

---

Issues are welcome, especially from Android 10 devices, which are the least tested. If the app crashes, `adb logcat -d AndroidRuntime:E *:S` right after the crash gives the most useful report.

---

## License

---

GeoCam is free software, licensed under the **GNU General Public License v3.0**.

Copyright (C) 2026 DeessPanda

In plain terms: you are welcome to use, study, modify and share GeoCam, including for class projects and coursework. If you publish a modified version, you just need to release that version's source too, under the same license.

Map data in the FLOSS build is © OpenStreetMap contributors, available under the ODbL.
