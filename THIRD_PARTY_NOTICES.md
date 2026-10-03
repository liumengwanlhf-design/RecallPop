# Third-party notices

The root MIT license covers original AnkiGate code and documentation. It does not replace the following licenses.

## Gradle wrapper

`gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.jar` are Gradle wrapper distribution files. The scripts retain their original copyright and Apache License 2.0 headers. The wrapper is distributed under Apache License 2.0; see [the included license](licenses/Apache-2.0.txt) and [Gradle's license](https://github.com/gradle/gradle/blob/master/LICENSE).

The wrapper is retained from the working project with text line endings normalized for Git; it downloads the distribution declared in `gradle-wrapper.properties` rather than bundling Gradle itself. The wrapper JAR is unchanged. No third-party notice was present in the supplied wrapper JAR.

## Android Open Source Project reference sources

The following unmodified Android 16 reference sources are retained for analysis only and are not compiled or packaged into the application:

- `tests/references/ActivityTaskManagerService.android16.txt`: Copyright (C) 2018 The Android Open Source Project.
- `tests/references/BackgroundActivityStartController.android16.txt`: Copyright (C) 2022 The Android Open Source Project.

Both retain their original Apache License 2.0 headers. See [the included license](licenses/Apache-2.0.txt). Upstream source location: [frameworks/base, android16-release, services/core/java/com/android/server/wm](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/core/java/com/android/server/wm/). The exact upstream revision of the supplied snapshots was not recorded; these files document the development reference and do not claim to match every Android 16 device build. Redundant base64 copies are excluded from the public repository.

## AnkiDroid API

AnkiGate communicates with the installed AnkiDroid content provider using independently written code. No AnkiDroid implementation or collection is included. `tests/check-anki-contract.mjs` accepts a separately obtained official contract file; that external file remains under its upstream license.
