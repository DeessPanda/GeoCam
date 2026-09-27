# Submitting GeoCam FLOSS to F-Droid

Everything needed is already in this repository. This page exists so the
submission is copy-paste rather than research.

## Why a metadata file and not an APK

F-Droid never accepts an uploaded binary. You submit a small text file that
tells their build server which repository to clone and which build variant to
compile, and they build it themselves. That is how they can promise the binary
matches the published source.

## 1. Open the merge request

Go to <https://gitlab.com/fdroid/fdroiddata/-/merge_requests/new> and import
from URL:

```
https://github.com/DeessPanda/GeoCam
```

## 2. Add the metadata file

Create a file named exactly after the applicationId, **not** the repo name:

```
dev.geocam.app.floss.yml
```

```yaml
RepoType: git
Repo: https://github.com/DeessPanda/GeoCam
UpdateCheckMode: Tags
UpdateCheckName: dev.geocam.app.floss
CurrentVersion: 1.0
CurrentVersionCode: 4
Builds:
  - versionName: '1.0'
    versionCode: 4
    commit: <full 40-character commit hash>
    gradle: floss
```

The two fields that silently break the build if you get them wrong:

- **`gradle: floss`** — selects the FLOSS variant. Omit it and F-Droid tries to
  build *both* flavours, including the one with Play Services.
- **`UpdateCheckName`** — required because the applicationId has a `.floss`
  suffix. F-Droid's documentation calls out exactly this case.

Also note `commit` must be the **full 40-character hash**, not a tag name.
F-Droid's docs say so explicitly.

Find the hash with:

```bash
git rev-parse HEAD
```

## 3. Explain the two flavours in the MR description

A human reads this, so say it plainly:

> GeoCam ships as two product flavours from this one repository. The `floss`
> flavour contains no proprietary dependencies: positioning uses Android's
> platform `LocationManager` and the mini-map uses OpenStreetMap. That is the
> flavour being submitted here. The `google` flavour additionally uses Play
> Services and Google map tiles, and is distributed separately.

## 4. Watch the build bot

`checkupdates-bot-fdroiddata` posts a build log within a few hours. It is
public, so you can follow it either way. This is where you find out whether the
scanner objects to the `googleImplementation` dependency being visible in the
build file even though it is never compiled into this variant.

## 5. Human review

Expect a few days. They may ask questions. If the scanner flags the Google
dependency, the usual outcome is either a documented exception or moving that
flavour to a separate repository.

## Store listing

F-Droid reads the app name, summary, description and changelog from the
Fastlane metadata already committed in this repo:

```
app/src/floss/fastlane/metadata/android/en-US/
├── title.txt
├── short_description.txt
├── full_description.txt
└── changelogs/
```

The path is the per-flavour layout F-Droid looks for. To change what the
listing says, edit those files and open a follow-up merge request — no code
change needed.
