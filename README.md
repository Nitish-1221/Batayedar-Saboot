# Batayedar Saboot
### Tenant Farmer Crop Evidence Android Application

> **"Your Hard Work, Your Independent Evidence"**
> *Empowering tenant farmers (Bataidars/Sharecroppers) to independently document and verify the crops they sow and cultivate using tamper-evident GPS-geotagged photographs, offline-first Room database, and verifiable PDF/ZIP legal dossiers.*

---

## 🌾 The Problem Statement
In many regions, government land records (such as *Khatauni*, *Khasra*, or *Patta*) list only the official landowner's name. Tenant farmers (*Bataidars*) who invest their labor, seeds, fertilizer, and sweat often lack formal documentation to prove that they cultivated the standing crops. This absence of verifiable evidence leaves them vulnerable during crop loss assessments, disaster relief claims, insurance compensations, and landlord disputes.

**Batayedar Saboot** solves this critical problem by providing tenant farmers with an independent, verifiable, 100% offline-capable tool to document their crop cultivation on-site with mandatory hardware GPS satellite verification and cryptographic fingerprints.

---

## Our solution APK link "https://drive.google.com/file/d/1eCTo3Ur1iq4QTGpjgsUO1ORcPd4nA5jZ/view?usp=sharing"
## Batyedar Login detail 
email:- "tech65262@gmail.com"
password:- "123456"

## Landlord Login Details 
email:- "nk2630096@gmail.com"
Password:- "123456"


## 🛠️ Technology Stack & Architecture
- **Platform**: Native Android (Android 7.0+ / API 24 to Android 14+ / API 34).
- **Language**: 100% Kotlin with Coroutines and StateFlow/Flow.
- **UI Framework**: Native XML layouts with ViewBinding and Material 3 DayNight design. *(Zero Compose/Flutter/React Native/WebViews)*.
- **Local Database**: Android Jetpack Room (SQLite) with relational entities (`RecordWithEvidence`), DAOs, type converters, and cascade deletion.
- **Camera**: Android CameraX (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`) with direct shutter integration.
- **Location Services**: Dual-mode GPS utilizing Android hardware `LocationManager` (for 100% offline satellite lock) and `FusedLocationProviderClient` with strict `< 20m` accuracy threshold and `<= 10s` freshness validation.
- **Export Formats**:
  - Native Android `PdfDocument` A4 printable dossier.
  - Full `.ZIP` package containing the PDF report, original photographs, derivative watermarked photographs, and a machine-readable `metadata.json`.
- **Integrity & Auditing**: Cryptographic SHA-256 fingerprinting for every original photograph, preserving originals untouched, and maintaining an internal audit trail (`AuditLogEntity`).
- **Bilingual**: English (default) and हिन्दी (Hindi) with runtime language switching.

---

## 📱 Application Flow & Features

### 1. Welcome & Onboarding (`WelcomeActivity`)
- Clear explanation of tenant farmer documentation rights.
- Highlights: 100% offline operation, mandatory GPS geotagging, legal evidence dossiers.
- Instant bilingual language switch button (English / हिन्दी).

### 2. Farmer Profile Setup (`ProfileEditActivity`)
- Automatic generation of unique Farmer ID (e.g., `FMR-20261028-XXXX`).
- Full Name, Mobile Phone (10 digits validated), Village, District, State.
- Preferred language selection stored locally.

### 3. Cultivation Dashboard (`HomeFragment`)
- **Real-time Database Counts**:
  - Fields recorded.
  - Geotagged evidence photographs.
  - Records awaiting optional landlord confirmation.
  - Cloud sync status (`Offline Mode (Local Safe)`, `Pending Sync`, `Synced`).
- Prominent **"Add Crop Evidence"** quick action button.
- Recent records preview with crop name, field name, village, and photo thumbnail.

### 4. Cultivation Record Creation (`AddEditRecordActivity`)
- Generates unique Record ID (e.g., `REC-20261028-XXXX`).
- User-provided Field Identifier (e.g., *"Purab Tola Plot #4"*).
- Crop selection with quick chips: Paddy (धान), Wheat (गेहूँ), Mustard (सरसों), Sugarcane (गन्ना), Maize (मक्का), Vegetables (सब्जियाँ).
- Sowing / cultivation start date with `DatePickerDialog`.
- Optional field description and soil/season notes.
- *Explicitly requires NO land ownership titles or landlord permission.*
- Clearly separates user-declared information from device GPS telemetry.

### 5. Mandatory GPS-Verified Camera (`GpsCaptureActivity`)
- **Strict Satellite Locking Workflow**:
  1. Requests `CAMERA` and `ACCESS_FINE_LOCATION` permissions with explanatory prompts.
  2. Checks if device Location Services are enabled; provides one-tap shortcut to Android GPS settings if disabled.
  3. Live HUD displaying GPS Satellite Fix Status, measured accuracy radius in meters, signal age in seconds, and coordinates.
  4. **Shutter Lock**: The capture button remains **disabled and locked** until accuracy is strictly `≤ 20 meters` and fix age is `≤ 10 seconds`.
  5. Immediate pre-shutter revalidation to reject stale or degraded fixes.
  6. Helpful guidance when indoors or under dense trees to step into an open area with a clear view of the sky.

### 6. Evidence Inspection & Derivative Watermarking (`EvidencePreviewActivity`)
- Displays captured photo with read-only, non-editable GPS metadata.
- Computes SHA-256 cryptographic checksum of the untouched original photo.
- Generates a derivative watermarked image with a semi-transparent agricultural data overlay, while retaining the original photograph 100% unaltered.
- Option to retake or confirm and save.

### 7. My Records & Evidence Gallery (`RecordsListFragment` & `RecordDetailActivity`)
- Search by crop, field name, village, or date.
- Filter chips: All, With Evidence, Awaiting Landlord, Confirmed.
- Full details view, thumbnail gallery, coordinate telemetry, and delete actions with confirmation dialogs.

### 8. Optional Landlord Confirmation (`LandlordConfirmationActivity`)
- Landlord confirmation is strictly optional.
- Allows recording: Not Requested (default), Requested, Confirmed verbally, Confirmed in writing/digital, Refused, or Unresponsive.
- Explains clearly that refusal or lack of response does not invalidate the farmer's GPS evidence.

### 9. Dossier Export & Sharing (`ExportDossierActivity` & `PdfDossierExporter`)
- **Printable A4 PDF Dossier**:
  - Farmer profile & unique IDs.
  - Crop cultivation details & sowing date.
  - GPS metadata table (coordinates, accuracy, elevation, timestamps, device model).
  - High-clarity crop photograph.
  - SHA-256 checksum and audit record.
  - **Mandatory Legal Disclaimer**:
    > *"This report is a collection of farmer-provided information and supporting evidence. GPS metadata and photographs do not independently establish land ownership, legal tenancy, or legal entitlement. The legal significance of this evidence depends on applicable state laws and assessment by the relevant authorities."*
- **Digital ZIP Bundle**:
  - Contains `evidence_report_<recordId>.pdf`.
  - Machine-readable `metadata.json` (format version 1.0.0).
  - Original unedited photograph files (`photos/original_<id>.jpg`).
  - Watermarked derivative photograph files (`photos/watermarked_<id>.jpg`).
- Instant sharing via Android `FileProvider` (WhatsApp, Email, Drive, Print).

### 10. Settings & Security (`SettingsActivity`)
- GPS Accuracy Threshold overview (20 meters).
- Real-time permission status indicators for Camera and Location.
- Language switcher (English / हिन्दी).
- Local storage and offline integrity details.

---

## 🏛️ Project Directory Structure

```
d:\Codes\Bataydar\
├── app\
│   ├── src\
│   │   ├── main\
│   │   │   ├── AndroidManifest.xml
│   │   │   ├── java\org\bataydar\evidence\
│   │   │   │   ├── BataydarApp.kt
│   │   │   │   ├── camera\
│   │   │   │   ├── data\
│   │   │   │   │   ├── local\
│   │   │   │   │   │   ├── AppDatabase.kt
│   │   │   │   │   │   ├── Converters.kt
│   │   │   │   │   │   ├── FarmerProfileDao.kt
│   │   │   │   │   │   ├── CropRecordDao.kt
│   │   │   │   │   │   ├── GeotaggedEvidenceDao.kt
│   │   │   │   │   │   └── AuditLogDao.kt
│   │   │   │   │   ├── model\
│   │   │   │   │   │   ├── FarmerProfileEntity.kt
│   │   │   │   │   │   ├── CropRecordEntity.kt
│   │   │   │   │   │   ├── GeotaggedEvidenceEntity.kt
│   │   │   │   │   │   ├── AuditLogEntity.kt
│   │   │   │   │   │   ├── RecordWithEvidence.kt
│   │   │   │   │   │   ├── SyncStatus.kt
│   │   │   │   │   │   └── LandlordStatus.kt
│   │   │   │   │   └── repository\
│   │   │   │   │       ├── FarmerProfileRepository.kt
│   │   │   │   │       └── EvidenceRepository.kt
│   │   │   │   ├── export\
│   │   │   │   │   ├── ChecksumUtils.kt
│   │   │   │   │   ├── PdfDossierExporter.kt
│   │   │   │   │   └── ZipBundleExporter.kt
│   │   │   │   ├── gps\
│   │   │   │   │   ├── GpsLocationManager.kt
│   │   │   │   │   └── GpsValidationResult.kt
│   │   │   │   ├── sync\
│   │   │   │   │   ├── CloudSyncEngine.kt
│   │   │   │   │   └── SyncResult.kt
│   │   │   │   ├── ui\
│   │   │   │   │   ├── welcome\WelcomeActivity.kt
│   │   │   │   │   ├── profile\ProfileEditActivity.kt, ProfileFragment.kt
│   │   │   │   │   ├── main\MainActivity.kt, HomeFragment.kt, RecentRecordsAdapter.kt
│   │   │   │   │   ├── records\AddEditRecordActivity.kt, RecordsListFragment.kt, RecordsAdapter.kt
│   │   │   │   │   ├── capture\GpsCaptureActivity.kt, EvidencePreviewActivity.kt
│   │   │   │   │   ├── detail\RecordDetailActivity.kt, EvidenceGalleryAdapter.kt
│   │   │   │   │   ├── landlord\LandlordConfirmationActivity.kt
│   │   │   │   │   ├── export\ExportDossierActivity.kt
│   │   │   │   │   └── settings\SettingsActivity.kt
│   │   │   │   └── util\
│   │   │   │       ├── FileUtils.kt
│   │   │   │       ├── ImageWatermarkUtils.kt
│   │   │   │       └── LocaleHelper.kt
│   │   │   └── res\
│   │   │       ├── layout\
│   │   │       ├── drawable\
│   │   │       ├── values\strings.xml, colors.xml, themes.xml
│   │   │       ├── values-hi\strings.xml (Hindi)
│   │   │       └── xml\file_paths.xml
│   │   └── test\java\org\bataydar\evidence\
│   │       ├── GpsValidationTest.kt
│   │       ├── ChecksumUtilsTest.kt
│   │       └── ValidationAndIdTest.kt
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── gradlew.bat
```

---

## 🚀 Building & Running the Project

### Prerequisites
- JDK 17 or JDK 21
- Android SDK with API 34 installed (`platforms;android-34`, `build-tools;34.0.0`)

### Running via Gradle CLI
```powershell
# Run all unit tests
.\gradlew.bat testDebugUnitTest

# Assemble debug APK
.\gradlew.bat assembleDebug

# Output APK path:
# app\build\outputs\apk\debug\app-debug.apk
```

### Opening in Android Studio
1. Launch **Android Studio**.
2. Select **Open** and select the folder `d:\Codes\Bataydar`.
3. Allow Gradle sync to complete.
4. Connect an Android device or start an emulator.
5. Click **Run** (`Shift + F10`).
