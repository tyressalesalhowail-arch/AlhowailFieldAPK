# Alhowail Field — Android app (APK)

The APK is a thin native shell around your salesman web app on GitHub Pages
(`https://tyressalesalhowail-arch.github.io/SalesmanApp/`). It adds:
- **Fake-GPS detection**: Android reports mock locations; punch in, visits and pins are refused while one is active.
- **Android printing** for the SMAN List / Statement PDFs (Print → Save as PDF → share to WhatsApp).
- App icon, full screen, camera and location permissions asked once and remembered.

**Updates:** screens and features still come from GitHub Pages — upload `index.html` as usual and every phone gets it.
Build a new APK only when this folder changes (name, icon, permissions, native code).

## One-time setup (about 10 minutes)

1. **Check the link** in `capacitor.config.json` → `server.url` (and in `www/offline.html`) is exactly the link of your
   salesman app on GitHub Pages. Change both if your repo name is different.
2. **Create a GitHub repository** `AlhowailFieldAPK` (company account). Make it **Public** so salesmen can download
   the APK without a GitHub account — this folder has no secrets in it.
   Upload **all files and folders** of this folder, including the hidden `.github` folder
   (easiest: GitHub Desktop, or drag the folder contents into the repo's "Add file → Upload files" page).
3. **Add the 4 secrets** — Settings → Secrets and variables → Actions → New repository secret. The values are in the
   private folder **KEEP-PRIVATE-Signing-Key → README-KEEP-THIS-SAFE.txt**.
4. **Build**: Actions tab → *Build Alhowail Field APK* → **Run workflow** → version `1.0` → Run.
   After about 5–8 minutes it appears under **Releases**.

## Installing on a salesman's phone

Send the permanent link (always the newest version):

`https://github.com/tyressalesalhowail-arch/AlhowailFieldAPK/releases/latest/download/Alhowail-Field.apk`

On the phone: open the link → download → tap the file → allow "Install unknown apps" for Chrome/Files the first time
→ Install → open **Alhowail Field** → allow **Location** (Precise) and **Camera**.

## Releasing a new APK version

Actions → *Build Alhowail Field APK* → Run workflow → a higher version (e.g. `1.1`). Salesmen open the same link and
install over the old app — their sign-in stays.

## Optional: app-only mode
In the Field DB → Settings, set `REQUIRE_ANDROID_APP` = `Yes` to allow punch in/out, visits and pins **only** from the
APK (a browser can still show lists and statements). Do this after every salesman has installed the app.

## If the build fails
Open the failed run in the Actions tab → click the red step → send a screenshot of the error. The most common cause is a
missing or mistyped secret.
