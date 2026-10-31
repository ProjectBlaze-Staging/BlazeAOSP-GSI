# BlazeAOSP Android 17 (CP2A) GSI — Generic System Image

<p align="center">
  <img src="https://raw.githubusercontent.com/ProjectBlaze-Staging/android_vendor_blaze/17.0/overlay/common/frameworks/base/core/res/res/drawable-xxhdpi/blaze_logo.png" width="160" alt="BlazeAOSP Logo" />
</p>

<h3 align="center">Next-Generation BlazeAOSP Android 17 Generic System Image</h3>

---

## Build Information

- **OS Version:** Android 17 (Baklava / VanillaIceCream Next)
- **API Level:** 37
- **Build ID:** `CP2A.260605.016`
- **Flavor:** `generic/lineage_gsi_arm64:userdebug` (ARM64-v8a, AB Partition Style)
- **Target Architecture:** `arm64` (compatible with Treble-enabled ARM64 A/B devices)
- **Primary Tested Device:** Google Pixel 6a (`bluejay`) running live via DSU and fastbootd
- **Google Apps:** MindTheGapps 17.0 Prebuilt + Integrated Google Play Store & Services
- **Image Size:** 
  - Raw `system.img`: **3.2 GB** (`3,408,465,920 bytes`)
  - Compressed `system.img.gz`: **1.7 GB** (`1,798,394,828 bytes`)
- **SHA-256 Checksum (`system.img.gz`):**
  ```
  21631948647bcc38bf1de100004b399f9f2e057521f2b343bf1b5fe6d4cc01d1
  ```

---

## Features & Highlights

### 1. Pixel Dock Quick Search Box (QSB)
- Fully integrated Google Search pill docked at the bottom of the home screen (`Launcher3QuickStep`).
- One-tap access to **Google Search**, **Voice Search**, and **Google Lens**.
- Scalable phone grid profiles (`3x3`, `4x4`, `5x5`) dynamically calculate QSB dimensions without layout clipping.
- Legacy AOSP `QuickSearchBox` and `Jelly` completely removed.

### 2. BlazeHouse Customization Suite
- **Custom Lockscreen Clock Engine:** Dual-mode clock architecture with exclusive lockscreen clock source selection (System Clock vs. BlazeHouse Styles) preventing overlapping views.
- **Status Bar Clock Customization:** Multi-position support (**Left**, **Center**, **Right**), AM/PM toggles, seconds counter, and dynamic date formatting.
- **Lockscreen Charging Info:** Real-time charging telemetry showing wattage, voltage, battery temperature, and estimated full charge time (`% • Hızlı şarj oluyor • 15W • 4.2V • 30.5°C`).

### 3. Advanced Device & Hardware Spoofing
- **TrickyStore & Play Integrity Fix (PIF):** Embedded keystore and attestation framework to maintain integrity on unlocked bootloaders.
- **Pixel Props Engine:** Spoof device properties on a per-app basis as Google Pixel 11 Pro XL, Pixel 9 Pro XL, or any supported target.
- **Tensor Feature Targets:** Expose Google Tensor-exclusive AI features to selected system and third-party apps.
- **App-Specific Spoofing:** One-click toggles for unlimited **Google Photos** storage and **Snapchat** optimization.

### 4. Secure Window Capture (`FLAG_SECURE` Bypass)
- Screenshot and screen recording restrictions (`FLAG_SECURE`) can be completely bypassed via **BlazeHouse -> Miscellaneous**.
- Tested and verified on **Firefox Private Browsing (Incognito)**, banking previews, and DRM protected layers without black screen artifacts.

### 5. Modern Pixel 9 Pro Sound Pack
- Outdated legacy KitKat UI sounds replaced with modern, crisp audio effects from the Pixel 9 Pro (`camera_click.ogg`, `Lock.ogg`, `Unlock.ogg`, `ChargingStarted.ogg`, `Effect_Tick.ogg`, `VideoRecord.ogg`).

---

## How to Reassemble the GSI Image

Because GitHub enforces a 100 MB per-file upload limit, the compressed `system.img.gz` (1.7 GB) is split into 20 chunks of 90 MB each (`system.img.gz.part00` through `system.img.gz.part19`).

### Option A: Using the Included Script (Automated)

**On Linux / macOS:**
```bash
chmod +x rejoin.sh
./rejoin.sh
```

**On Windows:**
Double-click `rejoin.bat` or run:
```cmd
rejoin.bat
```

### Option B: Manual Command

**Linux / macOS / Android Termux:**
```bash
cat system.img.gz.part* > system.img.gz
sha256sum system.img.gz
# Should output: 21631948647bcc38bf1de100004b399f9f2e057521f2b343bf1b5fe6d4cc01d1
```

**Windows (Command Prompt):**
```cmd
copy /b system.img.gz.part* system.img.gz
certutil -hashfile system.img.gz SHA256
```

**Windows (PowerShell):**
```powershell
Get-Content system.img.gz.part* -ReadCount 0 -AsByteStream | Set-Content system.img.gz -AsByteStream
Get-FileHash system.img.gz -Algorithm SHA256
```

---

## Installation Instructions

### Method 1: DSU (Dynamic System Update) — Recommended (Safe & Reversible)
DSU allows booting and testing BlazeAOSP without wiping your current ROM or modifying physical partitions.

1. Connect your device with USB Debugging enabled:
   ```bash
   adb devices
   ```
2. Push `system.img.gz` to your device storage:
   ```bash
   adb push system.img.gz /sdcard/Download/system.img.gz
   ```
3. Trigger the DSU installation service:
   ```bash
   RAW_SIZE=3408465920
   adb shell am start-activity \
     -n com.android.dynsystem/com.android.dynsystem.VerificationActivity \
     -a android.os.image.action.START_INSTALL \
     -d file:///storage/emulated/0/Download/system.img.gz \
     --el KEY_SYSTEM_SIZE $RAW_SIZE \
     --el KEY_USERDATA_SIZE 8589934592 \
     --ez KEY_ENABLE_WHEN_COMPLETED true
   ```
4. Confirm your screen lock when prompted on your phone.
5. Once the notification shows **Dynamic System is ready**, tap **Restart** to boot into BlazeAOSP.

---

### Method 2: FastbootD Flashing (Permanent)
*Requirements: Unlocked bootloader and Treble-compatible device.*

1. Decompress `system.img.gz`:
   ```bash
   gzip -d system.img.gz
   ```
2. Reboot your device into Fastboot mode:
   ```bash
   adb reboot bootloader
   ```
3. Enter `fastbootd` (Userspace Fastboot):
   ```bash
   fastboot reboot fastboot
   ```
4. Flash the system partition:
   ```bash
   fastboot flash system system.img
   ```
5. *(Recommended on first install)* Wipe userdata:
   ```bash
   fastboot -w
   ```
6. Reboot to system:
   ```bash
   fastboot reboot
   ```

---

## 🛠️ Source Code & Repositories

- Organization: [ProjectBlaze-Staging](https://github.com/ProjectBlaze-Staging)
- Framework: [android_frameworks_base](https://github.com/ProjectBlaze-Staging/android_frameworks_base)
- Settings & BlazeHouse: [android_packages_apps_Settings](https://github.com/ProjectBlaze-Staging/android_packages_apps_Settings)
- Vendor: [android_vendor_blaze](https://github.com/ProjectBlaze-Staging/android_vendor_blaze)
- Manifest: [blaze_manifest](https://github.com/ProjectBlaze-Staging/blaze_manifest)
