# Rebuilding Slam Log on a new or wiped PC

Use this if the PC is lost, wiped or replaced. Everything needed to build the app is in this repo,
**including the signing key** (`app/debug.keystore`). What isn't here is the build toolchain, which is
free to download again.

**Your workout history is on the phone, not the PC.** Losing the PC doesn't touch it. The one way to
lose it is uninstalling the app, so never uninstall it, even if an install error suggests doing so
(see "If the install fails" below).

## 1. Get the code

Either:
- On https://github.com/michaellaurio-oss/slamball-app click **Code → Download ZIP**, then unzip it
  (for example to `D:\Users\micha\Claude\slamball-app`), or
- Install Git (git-scm.com), then in PowerShell:
  `git clone https://github.com/michaellaurio-oss/slamball-app D:\Users\micha\Claude\slamball-app`

## 2. Install Java 17

1. Go to https://adoptium.net, choose **Temurin 17**, Windows x64, **.zip** (not the installer).
2. Unzip it, rename the folder inside to `jdk-17`, and put it at `D:\Users\micha\Android\jdk-17`
   (so `D:\Users\micha\Android\jdk-17\bin\java.exe` exists).

## 3. Install the Android SDK

1. Go to https://developer.android.com/studio, scroll to **Command line tools only**, and download
   the Windows zip.
2. Make the folder `D:\Users\micha\Android\sdk\cmdline-tools\latest` and unzip so that
   `D:\Users\micha\Android\sdk\cmdline-tools\latest\bin\sdkmanager.bat` exists. (The zip contains a
   `cmdline-tools` folder; its contents go inside `latest`.)
3. In PowerShell:

   ```
   $env:JAVA_HOME='D:\Users\micha\Android\jdk-17'
   cd D:\Users\micha\Android\sdk\cmdline-tools\latest\bin
   .\sdkmanager.bat --licenses
   .\sdkmanager.bat "platform-tools" "platforms;android-35" "build-tools;35.0.0"
   ```

   Answer `y` to the license questions.

Easier alternative: install Android Studio and let its setup wizard install the SDK. Then use the
SDK location it shows (usually `C:\Users\micha\AppData\Local\Android\Sdk`) in steps 4 and 5.

## 4. Create local.properties

In the project folder, create a file named `local.properties` with this one line:

```
sdk.dir=D\:\\Users\\micha\\Android\\sdk
```

(It's not on GitHub because it's specific to each PC.)

## 5. Build and install

1. On the phone: Settings → Developer options → **USB debugging** on.
2. Plug in the phone. Unlock it and tap **Allow** on "Allow USB debugging?". The new PC has a new
   adb identity, so the phone will ask again even if it was allowed before.
3. Double-click **install.bat** in the project folder. The first build downloads everything and
   takes several minutes.

If you put Java or the SDK somewhere other than `D:\Users\micha\Android`, edit the two paths at the
top of `install.bat` and the path in `local.properties` to match.

## If the install fails

- **`unauthorized`**: unlock the phone and tap **Allow** on the USB debugging prompt.
- **`INSTALL_FAILED_UPDATE_INCOMPATIBLE` or "signatures do not match"**: the app was built with a
  different signing key from the one on the phone. **Do not uninstall** — that deletes your
  history. Check that `app\debug.keystore` is present (it comes with the repo) and that
  `app\build.gradle.kts` still has the `signingConfigs` block pointing at it, then rebuild. If it
  still fails, ask Claude before doing anything else.
- **Build failed**: copy the red error lines and paste them to Claude.

## Backing up workout history

The history lives only on the phone. For a copy elsewhere, open History → **Export** now and then and
email yourself the CSV. (The app can't import it back yet; it's a record you can read in Excel or
give to Claude.)
