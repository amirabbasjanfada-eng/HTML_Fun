<div align="center">

[![Download APK](https://img.shields.io/badge/Download-APK-brightgreen?style=for-the-badge&logo=android)](https://github.com/amirabbasjanfada-eng/HTML_Fun/releases/latest)

</div>

<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Run and deploy your AI Studio app

This contains everything you need to run your app locally.

View your app in AI Studio: https://ai.studio/apps/bdf1c0e8-bacd-4ccb-bdbb-9aa8d5cd0649

## Run Locally

**Prerequisites:**  [Android Studio](https://developer.android.com/studio)


1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project.
4. Create a file named `.env` in the project directory and set `GEMINI_API_KEY` in that file to your Gemini API key (see `.env.example` for an example)
5. Remove this line from the app's `build.gradle.kts` file: `signingConfig = signingConfigs.getByName("debugConfig")`
6. Run the app on an emulator or physical device
7. If you have already published your app in AI Studio, please [request upload key reset](https://support.google.com/googleplay/android-developer/answer/9842756#zippy=%2Crequest-an-upload-key-reset) in Google Play Console.

---

## WebPaper architecture

A single-screen app: preview, library, code editor and settings share one scrolling page, in a classic
minimal light or dark theme (System / Light / Dark).

```
app/src/main/
  assets/presets/cosmic-nebula/index.html   the one built-in wallpaper (reference / test preset)
  assets/templates/starter.html             template for "New"
  java/com/example/
    data/       Wallpaper (metadata), WallpaperStore (disk, async, atomic), AppSettings
    web/        HtmlHelper (serve-time helpers), ContentRoutes + ContentServer (local https origin),
                WebViewSetup (shared WebView config), Libraries (one-tap CDN snippets)
    wallpaper/  HtmlWallpaperService (glue) + WebRenderer: GpuWebRenderer / CpuWebRenderer
    ui/         MainViewModel (all state), MainScreen (the page), Preview (WebView), theme/
```

**Wallpapers are folders.** `files/wallpapers/<id>/index.html` plus anything it references. You can import a
single `.html` or a `.zip` web project (it must contain `index.html`), so CSS, JS modules, fonts, images and
locally bundled libraries all work.

**Real https origin per wallpaper.** Pages are served from `https://<id>.webpaper.local/` by
`ContentServer` (nothing goes over the network for those requests). That gives each wallpaper isolated
localStorage / IndexedDB, secure-context APIs, relative URLs, ES modules, import maps and top-level `await`.
CDN requests (jsDelivr, esm.sh, unpkg, ...) go to the network as usual.

**Stored code stays yours.** The helper layer (full-bleed CSS, viewport tag, frame-rate cap, optional
touch-to-mouse bridge) is injected when a page is served, never saved into your code.

**Latest web features.** Wallpapers run on the system WebView (Chromium), which updates through Google Play.
The library chips insert the newest release (`@latest`) of Three.js, p5.js, GSAP, PixiJS, D3, Matter.js and
Tailwind; "ES module" shows the `esm.sh` pattern for any npm package. Pin a version number to freeze a
wallpaper.

**Rendering.** *GPU rendering* (default) hosts the WebView in a `Presentation` on a private `VirtualDisplay`
whose output is the wallpaper surface: hardware-accelerated, WebGL-capable, no per-frame CPU copy.
If it cannot start, or the page never renders a frame, the service falls back to *compatibility rendering*
(a software WebView drawn into the surface). The frame-rate cap works by throttling `requestAnimationFrame`
inside the page, so it saves real CPU/GPU work; use `delta time` in animations so speed does not depend on it.

**Pages.** Home (preview + Library only), Code, Settings. Code and Settings are reached from the Home
header; system Back returns to Home.

**Home screen and Lock screen are independent.** There are two live wallpaper services:
`HtmlWallpaperService` (Home) and `HtmlLockWallpaperService` (Lock). Each reads only its own screen's
wallpaper id and content version (`AppSettings.idFor/versionFor(screen)`) and listens only to its own
preference keys, so choosing, editing or deleting for one screen can never change or reload the other.
"Set as wallpaper" always asks Home screen or Lock screen first. If our service for that screen is already
set, the running engine switches instantly; otherwise the system picker for that service opens (choose
"HTML Fun - Home screen" for the Home screen and "HTML Fun - Lock screen" for the Lock screen).
The Library label (Home Screen / Lock Screen / Both / Neither) is read from what the system reports as
applied (`WallpaperManager.getWallpaperInfo(which)` on Android 14+; before that one live wallpaper is shown
on both screens). Wallpapers chosen with the older single "active" setting keep showing on both screens
until each screen is set separately.

**Troubleshooting.** Blank live wallpaper? Settings -> turn off *GPU rendering*. Debug builds enable
`chrome://inspect` for wallpaper pages.
