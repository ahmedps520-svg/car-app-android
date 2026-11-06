# Car Browser

A web browser made for Android car screens (head units) whose own browser is
locked, hidden or too old. Big buttons, a big search box and Google one tap away.
It's built to run on old Android (4.1 and up), which is what most of these screens
really run.

**⬇ Download the app:**
[`apk/CarBrowser.apk`](https://github.com/ahmedps520-svg/car-app-android/raw/claude/awesome-wright-6m0pny/apk/CarBrowser.apk)
(3.7 MB). Once this branch is merged, the link becomes
`https://github.com/ahmedps520-svg/car-app-android/raw/main/apk/CarBrowser.apk`.

---

## What your car screen actually is

From the *About* screen in your photo:

| Shown on screen | What it means |
|---|---|
| Model number `rkpx3` | A **Rockchip PX3** head unit, a common Chinese car-screen board. |
| Android version `6.0.4` | **Not real.** The build number `KTU84Q` belongs to **Android 4.4.4 KitKat** (2014). These units often show a fake, newer-looking version. |
| Build `rkpx3-eng … test-keys` | An **engineering** firmware. Installing apps is usually allowed, and "ADB" (installing from a computer over Wi‑Fi) is usually switched on. |

Why the usual fixes don't work: current Chrome and most Play Store apps no longer
support Android 4.4, and the screen's own web engine dates from 2014.
On top of that, the screen's list of trusted website certificates is from 2014, so
many modern sites show security errors. Car Browser was built around all three problems.

---

## Step 1: Put the app on the car screen

Try **A** first. If that's blocked, use **B** (laptop) or **C** (phone).

### A. With a USB stick (easiest)

1. Download `CarBrowser.apk` (link above) and copy it onto a USB stick.
   The stick should be **FAT32** (most are).
2. Plug the stick into the car's USB port.
3. On the car screen, open the **file manager** app. It may be called *File Manager*,
   *Explorer*, *RK Explorer*, *Files* or *文件管理*. If it isn't on the home screen,
   look in the screen's app list.
4. Open the USB drive (often called `usb_storage`, `udisk`, `USB` or `usbhost`),
   tap **CarBrowser.apk**, then **Install**, then **Open**.

**"Install blocked"?** Tap **Settings** in that message (or go to
*Settings → Security*), tick **Unknown sources**, press OK, then tap the APK again.

**No file manager at all?** Go to B or C.

### B. Over Wi‑Fi with a laptop (works even when the screen is locked down)

1. Connect the **car screen and the laptop to the same Wi‑Fi**. Your phone's hotspot works.
2. Find the car screen's IP address: **Settings → About → Status → IP address**
   (*Status* is the item just above *Model number* in your photo). It looks like `192.168.43.57`.
3. On the laptop:
   * Download this project: green **Code** button → **Download ZIP**, then unzip it.
   * Download Google's **SDK Platform-Tools** for your computer from
     <https://developer.android.com/tools/releases/platform-tools> and unzip it **into
     the `install` folder**. You should end up with `install/platform-tools/`.
4. Run the installer:
   * **Windows:** double-click `install/install-windows.bat` and type the IP address.
   * **Mac / Linux:** open a terminal and run `sh install/install-mac-linux.sh 192.168.43.57`
     (use your IP address).
5. Car Browser opens on the car screen when it's done.

**"failed to connect" / "connection refused"?** Turn on ADB on the car screen:

1. *Settings → About*, then tap **Build number** 7 times (the last item in your photo).
   You'll see "You are now a developer".
2. Go back. Open **Developer options** and turn on **USB debugging**. Also turn on
   **ADB over network** / **Network debugging** if your screen has it.
3. Run the installer again. If the car screen asks **"Allow USB debugging?"**, tick
   *Always allow* and press **OK**.

The same thing by hand, if you prefer typing commands:

```sh
adb connect 192.168.43.57:5555
adb install -r CarBrowser.apk
adb shell am start -n io.github.ahmedps520.carbrowser/.MainActivity
```

### C. Over Wi‑Fi with an Android phone (no laptop)

Same idea as B, but use an ADB app on your phone, for example **Bugjaeger** from the
Play Store. Put the phone and the car screen on the same Wi‑Fi (the phone's hotspot
is easiest). In the app, connect to the car screen's IP address with port `5555`, then
install `CarBrowser.apk`. B's "failed to connect" tips apply here too.

### D. Last resort: the factory menu

Many of these screens have a hidden *Factory settings* menu that needs a code.
Codes people commonly report: `8888`, `1234`, `0000`, `3368`, `1617`, `126`. Some
of these menus have a switch that allows installing apps. **Don't change anything
else in there.** Car model, CAN-bus and screen settings can break your steering-wheel
buttons, reversing camera or display.

---

## Step 2: Open it

* Right after installing, tap **Open**.
* Look for **Car Browser** (blue globe icon) in the car screen's app list.
* **Car's home screen doesn't show it?** Copy `install/open-car-browser.html` to the USB
  stick, tap it in the file manager and choose **Car Browser**. Or use ADB:
  `adb shell am start -n io.github.ahmedps520.carbrowser/.MainActivity`

---

## Using Car Browser

* **Top bar:** Back, Forward, Reload, Home, the address/search box, then two more buttons:
  * **Grid button:** *Apps & settings* (see below).
  * **⋮ button:** the menu.
* **Home page:** a big search box plus tiles for Google, YouTube, Maps, Weather, News,
  Wikipedia and Translate. Remove tiles with *Edit bookmarks*; add the page you're on
  with *⋮ → Add bookmark*.
* **⋮ menu:**
  * **Desktop site:** loads the full computer version of sites. Good on wide car screens.
  * **Text size:** 75% to 200%.
  * **Search engine:** Google, DuckDuckGo or Bing.
  * **Downloads**, **Clear browsing data**, **Help** and **Exit**.
* **Downloads:** tapping a download link saves it to *Downloads*. If it's an app
  (`.apk`), Car Browser offers to install it. So once Car Browser is on the screen,
  you can install other apps straight from the internet.
* **Videos** can go full screen. **Sound keeps playing** when you switch to another
  car app such as navigation or radio.
* **Location:** map sites can use the car's GPS. You're asked per site.

### Apps & settings (grid button)

This screen shows **every app on the car screen, including ones the car's home
screen hides**. Some units already have a browser, file manager or the full Android
Settings installed, just hidden. Long-press an app for its info page.

It also has shortcuts straight into Android settings that car firmwares often hide:
Wi‑Fi, **Security (Unknown sources)**, **Developer options**, Manage apps,
**Date & time**, Keyboard, Display, Sound, Bluetooth, Storage, About and Downloads.

If an app was **switched off** by the manufacturer, it appears under *Disabled apps*.
Tap it, then press **Enable**.

---

## What works and what doesn't

Car Browser uses the web engine built into the car screen, which on Android 4.4 is
from 2014 and can't be updated. That means:

* ✅ Google search, Google Maps (simple version), Wikipedia, news sites, weather,
  most normal websites.
* ⚠️ Heavy modern sites (**YouTube**, big web apps) may be slow, look basic, or say
  "browser not supported".
* ❌ Netflix, Spotify Web and other sites that need copy-protection (DRM) won't play.

**For heavy sites, tap "Get Firefox" on the home page.** It downloads **Firefox 68.11**
from Mozilla's official archive. That's the last Firefox that runs on Android 4.x, and
it has its own, much newer engine. It no longer gets security updates, so don't use
it (or this car screen) for banking.

### Certificates and "Security warning"

Android 4.4 doesn't recognise the certificates of many modern websites, for example
anything using Let's Encrypt. Car Browser includes Mozilla's current list of trusted
certificates. When the old engine rejects a site, the app checks it again against that
list over a modern, secure connection. If the site passes, it opens normally with no
prompt. You only see **"Security warning"** when a site fails that check too. In that
case, only continue if you trust the site, and never type passwords there.

### Clock

If the car screen's date is wrong (these units often reset to 2017–2019), secure
websites refuse to load. Car Browser warns you and has a button to open *Date & time*
settings. Turn on **automatic date & time** if your unit has it.

---

## Is there already a hidden browser? (quick checks)

* In Car Browser, open the **grid button**. If *Browser* or *Chrome* is in *All apps*,
  it was only hidden. If it's under *Disabled apps*, tap it and press **Enable**.
* With ADB (from step B):

  ```sh
  adb shell am start -a android.intent.action.VIEW -d https://www.google.com   # opens any browser that exists
  adb shell pm list packages -d                                                # lists disabled apps
  adb shell pm enable com.android.browser                                      # re-enables the stock browser
  ```

---

## Troubleshooting

| Problem | Fix |
|---|---|
| **Install blocked** | *Settings → Security → Unknown sources* ✔ (also under the grid button). |
| **App not installed** | An older copy may be installed. Uninstall it first. Also check free storage. |
| **There was a problem parsing the package** | The file got damaged while copying. Copy it to the USB stick again. |
| **adb: failed to connect** | Same Wi‑Fi? Correct IP? Turn on Developer options → USB debugging (step B). |
| **adb: unauthorized** | Accept the *Allow USB debugging?* prompt on the car screen and try again. |
| **"Can't reach the internet"** | Check the car screen is connected to Wi‑Fi (or your hotspot) and that the hotspot has data. |
| **Every site shows a security error** | The car's clock is probably wrong. Fix *Date & time*. |
| **A site doesn't work or is blank** | Try *⋮ → Desktop site*, or use **Get Firefox**. |

**Please only use the browser while parked.**

---

## For developers

* Plain Java, no AndroidX: `minSdk 16`, `targetSdk 34`. APKs are signed with v1 + v2
  signatures, because Android 4.x only understands v1.
* Build: `./gradlew assembleRelease`, which writes
  `app/build/outputs/apk/release/CarBrowser-release.apk`. Tests:
  `./gradlew testReleaseUnitTest`.
* GitHub Actions builds the APK on every push (download it from the run's *Artifacts*).
  Pushing a `v*` tag attaches it to a GitHub release.
* `app/carbrowser.jks` is a **public sideloading key** (password `carbrowser`). It's
  committed on purpose so that any build can be installed over the previous one. Don't
  reuse it for anything that needs a private key.
* Modern TLS on Android 4.x comes from [Conscrypt](https://github.com/google/conscrypt)
  2.5.3, which is the last version that supports API < 21. The root certificate list in
  `app/src/main/res/raw/cacerts.pem` is Mozilla's CA bundle (from Ubuntu's
  `ca-certificates` 2026-08).

| File | What it does |
|---|---|
| `MainActivity.java` | Browser screen, toolbar, menu, full-screen video, location prompts |
| `HomePage.java` | Generates the start page (ES5/old-CSS only, for the 2014 engine) |
| `AppsActivity.java` | All apps (including hidden and disabled ones) plus Android settings shortcuts |
| `SslHelper.java` / `Tls.java` | Re-checks certificates the old WebView rejects, using Mozilla's root list and Conscrypt |
| `Downloader.java` | Downloads (with modern TLS on Android < 7) and APK install prompts |
| `Search.java` / `UserAgent.java` | Address-vs-search logic, desktop mode |
