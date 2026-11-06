# PureScanner

A small Android app that scans documents with a network scanner over your local Wi-Fi, using the eSCL (AirScan) protocol. No cloud, no account, no vendor app.

## Features

- Finds eSCL scanners automatically via mDNS (`_uscan._tcp` and `_uscans._tcp`), or lets you add one by IP address or host name.
- Picks the paper size (A4, A5, Letter, Legal) and resolution from what the scanner reports it supports.
- Scans one page, shows a preview, and saves it as a PDF or a JPEG through the system file picker (no storage permission needed).
- PDFs embed the scanner's JPEG as-is, so there is no re-encoding or quality loss.
- Sends scanner traffic over Wi-Fi or Ethernet even when mobile data or a VPN is the default network.

## Requirements

- Android 8.0 (API 26) or newer.
- A scanner that supports eSCL / AirScan and can output JPEG.
- The phone and the scanner on the same network.

## Usage

1. Open the app. Scanners on your network appear in the list. If yours doesn't, tap the manual option and enter its IP address (for example `192.168.1.50` or `192.168.1.50:8080`).
2. Select the scanner, then choose a size and quality.
3. Tap **Start scanning**. When the page arrives, review it in the preview.
4. Tap **Save as** and choose PDF or JPEG.

## Building

Requirements: JDK 17 and the Android SDK (compile SDK 37).

```sh
cd app
echo "sdk.dir=/path/to/Android/Sdk" > local.properties
./gradlew :app:assembleDebug      # APK in app/app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest  # unit tests
```

The Gradle wrapper (`gradlew`) downloads Gradle 9.1.0 automatically.

## Project layout

```
app/app/src/main/java/com/purescanner/app/
  escl/        eSCL client, XML building/parsing, TLS handling
  discovery/   mDNS discovery, manual devices, Wi-Fi socket factory
  save/        PDF writer and file saving
  ui/          Compose screens (devices, manual add, scan, preview)
  MainViewModel.kt
tools/fake_escl_server.py   fake eSCL scanner for testing without hardware
```

## Limitations

- **One page per scan.** Multi-page scanning is not supported. With an automatic document feeder, only the first sheet is fetched.
- **JPEG only.** Scanners that cannot output JPEG are rejected.
- **Flatbed preferred.** If the scanner has a flatbed, it is always used; there is no feeder selection.
- **Scans are held in memory.** If Android kills the app while a scan is on screen, the scan is lost. Save it before switching away.
- **Self-signed certificates are accepted for HTTPS scanners, but only when the scanner's address is a private-network IP literal** (such as `192.168.x.x`, `10.x.x.x`, or `fe80::`). This does not protect against someone else on the same network impersonating the scanner. Plain HTTP has the same exposure.
- Discovery runs for about 8 seconds per refresh.
- Debug build only; there is no release signing configuration.

## License

MIT. See [LICENSE](LICENSE).
