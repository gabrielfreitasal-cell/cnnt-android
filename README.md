# CNNT

CNNT is an Android handwriting and visual-note app focused on large stylus canvases, fast drawing, page navigation, and export workflows.

The project is owned by Gabriel Serpa. The current source lives in this repository and should be treated as the source of truth for future builds.

## Current State

- Android app written in Kotlin.
- Main package: `com.cnnt.app`.
- Branch currently in use: `devin/1748678400-cnnt-initial`.
- Debug APK build workflow is available through GitHub Actions.
- Local release APKs may exist under `releases/`.

## Project Layout

- `app/` - Android application module.
- `app/src/main/java/com/cnnt/app/canvas/` - drawing canvas logic.
- `app/src/main/java/com/cnnt/app/ink/` - ink engine and brush behavior.
- `app/src/main/java/com/cnnt/app/ui/` - main activity, toolbar, sidebar, and UI managers.
- `docs/` - QA notes and manual test checklist.
- `releases/` - locally generated APK builds.
- `.github/workflows/build-apk.yml` - GitHub Actions workflow for debug APK builds.

## Build Locally

Requirements:

- JDK 17
- Android SDK installed and configured through `ANDROID_HOME` or `local.properties`

PowerShell:

```powershell
cd "C:\Users\Ryzen 5\Projects\cnnt-android"
.\gradlew.bat assembleDebug
```

Output:

```text
app\build\outputs\apk\debug\app-debug.apk
```

## Build With GitHub Actions

The repository includes a workflow:

```text
.github/workflows/build-apk.yml
```

It runs on:

- pushes to `main`
- pushes to `devin/**`
- manual `workflow_dispatch`

The generated artifact is named:

```text
cnnt-debug-apk
```

## Important Notes

- Do not commit local build caches such as `.gradle/`, `build/`, or `app/build/`.
- Do not commit signing keys, keystores, or private credentials.
- Keep APKs in `releases/` only when they are intentionally preserved for testing.
- Before major changes, create a local backup or push a clean commit.

## QA

Use the checklist in:

```text
docs/QA_CHECKLIST.md
```

Core things to verify after each APK:

- strokes remain after closing and reopening the app;
- page switching keeps the correct canvas content;
- pinch zoom does not jump after release;
- brush size and texture feel consistent;
- toolbar and sidebar controls remain usable on the tablet screen.
