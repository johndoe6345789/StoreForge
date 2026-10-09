# StoreForge

App store for my apps: an Android app, written in Kotlin, that works like a small Play Store.
Browse the catalog, then install, update, open and uninstall apps. The apps themselves are
APKs attached to GitHub releases, and the list of apps is [`apps.json`](apps.json) in this repo.

StoreForge itself is published the same way: as an APK on this repo's
[Releases page](https://github.com/johndoe6345789/StoreForge/releases), and it is listed in its
own catalog so it updates itself.

## Install

1. On your phone, download
   [`StoreForge.apk`](https://github.com/johndoe6345789/StoreForge/releases/latest/download/StoreForge.apk)
   from the latest release and open it.
2. Android asks you to allow your browser to install apps; allow it once.
3. The first time you install something from StoreForge, Android asks you to allow StoreForge to
   install apps. On Android 12 and later, updates to apps that StoreForge installed can then go
   through without asking again.

Requires Android 8.0 (API 26) or newer.

## What it does

| Tab | |
| --- | --- |
| **Apps** | Featured apps, category filters, and every app in the catalog. Pull down to refresh. |
| **Search** | Search by name, developer, category, tags or description. |
| **My apps** | Installed apps, updates waiting (with a badge on the tab), and **Update all**. |

Each app has a details page with its icon, screenshots, description, the release notes of the
latest GitHub release, and Install / Update / Open / Uninstall buttons. Downloads show progress and
can be cancelled.

How it works:

- The catalog is fetched from
  `https://raw.githubusercontent.com/johndoe6345789/StoreForge/main/apps.json`, so editing
  `apps.json` on `main` changes what every installed StoreForge shows. The last copy is cached, so
  the store still opens offline.
- For each app, StoreForge asks the GitHub API for the latest release and picks the APK attached
  to it. Results are cached with ETags; GitHub doesn't count those revalidations against its
  rate limit of 60 requests an hour.
- The APK is downloaded, checked against the SHA-256 that GitHub publishes for release assets,
  checked to contain the expected package name, and handed to Android's `PackageInstaller`.
- An app counts as installed when a package with its `packageName` is on the device. An update is
  available when the release is newer than the installed `versionName` (or `versionCode`, for
  entries that give one).

## Adding an app

Add an entry to `apps.json` and push to `main`:

```json
{
  "packageName": "com.example.notes",
  "name": "Notes",
  "developer": "johndoe6345789",
  "summary": "Quick notes that sync nowhere",
  "description": "Longer text for the details page.",
  "category": "Productivity",
  "icon": "registry/icons/notes.png",
  "screenshots": ["registry/screenshots/notes-1.png"],
  "tags": ["notes", "writing"],
  "homepage": "https://github.com/johndoe6345789/notes",
  "license": "MIT",
  "github": { "repo": "johndoe6345789/notes" }
}
```

| Field | |
| --- | --- |
| `packageName` | **Required.** The app's `applicationId`. |
| `name`, `summary` | **Required.** |
| `github.repo` | `owner/repo` whose latest release holds the APK. |
| `github.asset` | Regex picking the APK when a release has several files. Default `\.apk$`. |
| `github.prereleases` | `true` to also offer pre-releases. Default `false`. |
| `apk` | Instead of `github`: a fixed APK, `{ "url", "versionName", "versionCode", "sha256", "size", "releaseNotes" }`. |
| `icon`, `screenshots` | Full URLs, or paths relative to `apps.json` (commit images under `registry/`). |
| `featured` (top level) | List of `packageName`s shown in the Featured row. |

For update detection to work, tag releases with the app's `versionName` (e.g. tag `v1.4.0` for
`versionName "1.4.0"`). Every release of an app must be signed with the same key, or Android
refuses to install it as an update.

The unit tests validate `apps.json`, so CI fails on a broken catalog:

```sh
./gradlew testDebugUnitTest
```

## Publishing releases

Every commit to `main` is built, tested and published as a GitHub release by
[`.github/workflows/release.yml`](.github/workflows/release.yml). Each release is tagged
`v1.0.<run number>` and carries `StoreForge.apk`, so the
[latest download link](https://github.com/johndoe6345789/StoreForge/releases/latest/download/StoreForge.apk)
always points at the newest build.

Each run signs the APK with a new self-signed key generated on the runner, and the key is
discarded afterwards. No signing secrets are needed. The trade-off: Android only updates an app
when the signing key matches, so a StoreForge installed from one release has to be uninstalled
before installing a later one. The same applies to StoreForge's own self-update.

Pull requests are checked by [`.github/workflows/ci.yml`](.github/workflows/ci.yml).

## Building locally

Needs JDK 17+ and the Android SDK (API 36).

```sh
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest lintDebug
```

The debug build installs next to the release one (`io.github.johndoe6345789.storeforge.debug`).
To point a build at another catalog, pass `-PcatalogUrl=https://…/apps.json`; it can also be
changed in the app under Settings.

## Code layout

```
apps.json                      the catalog
registry/icons/                images referenced from apps.json
app/src/main/java/io/github/johndoe6345789/storeforge/
  catalog/                     apps.json model and validation, GitHub releases, HTTP caching
  install/                     APK download, PackageInstaller install/update/uninstall
  ui/                          Compose screens: home, search, my apps, details, settings
  StoreViewModel.kt            store state and actions
```

## Limitations

- Downloads run while StoreForge is open; there are no background or scheduled updates yet.
- Without a GitHub token the API allows 60 uncached requests an hour per network, which is
  plenty for a personal catalog but not for hundreds of apps.
