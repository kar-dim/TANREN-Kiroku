# TANREN Kiroku

![Status](https://img.shields.io/badge/status-alpha-orange)

**TANREN** (鍛錬) is a Japanese concept meaning the forging and tempering of metal, the repeated, deliberate process that turns raw material into something refined. Applied to training, it describes what happens when you show up consistently, lift with awareness, and build on what came before. Not motivation. Not inspiration. Work, recorded and repeated.

**Kiroku** (記録) means record, or documentation. This app is the record of your own reforging process.

<p align="center"><img src="app/src/main/res/drawable/kiroku.png" alt="Kiroku" width="256" height="256"/></p>

---

## What it does

TANREN Kiroku is a simple Android workout logger. No account is required.

- **Log workouts by day:** Navigate through dates, add exercises, and track sets with reps and weight
- **Built-in exercise catalog:** Organized by muscle group, covering the major compound and isolation movements
- **Custom exercises:** Add any exercise not in the catalog, assign it to a muscle group
- **Unit support:** Switch between kg and lb at any time, weight will be converted automatically.
- **Date navigation:** move day by day, or enable "skip empty days" to jump only between days that have logged workouts
- **Copy workouts:** on any empty day, copy the full exercise list from a previous session as a starting point
- **Backup and restore:** export all workout data as a zip archive and restore it on any device
- **Sync with TANREN Metsuke:** transfer your workout files to the companion desktop app via QR code for visualization and analysis

## Screenshots

<p align="center">
  <img src="readme_screenshots/pic1.png" width="30%"/>
  <img src="readme_screenshots/pic2.png" width="30%"/>
  <img src="readme_screenshots/pic3.png" width="30%"/>
</p>
<p align="center">
  <img src="readme_screenshots/pic4.png" width="30%"/>
  <img src="readme_screenshots/pic5.png" width="30%"/>
  <img src="readme_screenshots/pic6.png" width="30%"/>
</p>
<p align="center">
  <img src="readme_screenshots/pic7.png" width="30%"/>
  <img src="readme_screenshots/pic8.png" width="30%"/>
  <img src="readme_screenshots/pic9.png" width="30%"/>
</p>

## Companion app

[TANREN Metsuke](https://github.com/kar-dim/TANREN-Metsuke) is the PC companion app, the eye (目付, *metsuke*) that reads what Kiroku has recorded. It provides charts, muscle group breakdowns, and progress tracking across your full history.

Sync is done locally over your network: scan the QR code shown by Metsuke and the transfer happens directly between your phone and desktop/laptop.

Both apps must support sync protocol v2. The QR code advertises `protocolVersion: 2`, and Kiroku verifies it again at `/ping`. Sync waits for queued edits, uploads an immutable snapshot, and reports success only after the desktop acknowledges `/sync/complete`. Dropped requests retry with the same session and content.

## Desktop–Mobile Sync Protocol Specification

Synchronization occurs strictly over the local network (Wi-Fi):

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Desktop as TANREN-Metsuke (PC)
    participant Mobile as TANREN-Kiroku (Phone)

    User->>Desktop: Open Sync Tab
    Desktop->>Desktop: Load or create persistent RSA-2048 X509Cert
    Desktop->>Desktop: Bind TcpListener to dynamic port
    Desktop->>Desktop: Render QR code on screen
    User->>Mobile: Open Sync Screen & Scan QR
    Mobile->>Desktop: GET /ping (TLS Pinned, Authorization: Bearer <token>)
    Desktop-->>Mobile: 200 OK {"ok": true, "protocolVersion": 2}
    Mobile->>Mobile: Capture complete validated file snapshot
    Mobile->>Desktop: POST /sync/manifest {protocolVersion: 2, complete: true, files: [...]}
    Desktop->>Desktop: Compare hashes with local files
    Desktop->>Desktop: Plan removals: keep current files unchanged
    Desktop-->>Mobile: 200 OK {"sessionId": "...", "needed": ["2026-09-26.json"], "deleted": 0}
    loop For each file in needed
        Mobile->>Desktop: POST /sync/upload {"sessionId": "...", "filename": "...", "content": {...}}
        Desktop->>Desktop: Validate hash and JSON: stage file
        Desktop-->>Mobile: 200 OK {"ok": true}
    end
    Mobile->>Desktop: POST /sync/complete {"sessionId": "..."}
    Desktop->>Desktop: Commit snapshot: archive previous dataset
    Desktop->>Desktop: Reload UI once if data changed
    Desktop-->>Mobile: 200 OK {"ok": true}
    Mobile-->>User: "Sync Complete!"
```

## Installation

To install the app, download the APK from the [Releases](https://github.com/kar-dim/TANREN-Kiroku/releases) page, allow "Install unknown apps" on your Android device and select the downloaded APK.

## Data

All data is stored locally on your device as plain files. No cloud, no account required. You own your data and can back it up, transfer it, or inspect it at any time.

Backup imports validate every participating file before replacement and enforce limits of 10,000 ZIP entries, 8 MiB per entry, and 128 MiB of uncompressed data. Replacements are staged, with original files retained on disk until commit. Interrupted replacements are recovered before subsequent reads or sync. Storage failures appear with a Retry action and block sync or export until resolved.

Weights are stored in kg. Zero means no recorded added load and remains valid. Metsuke includes zero-load sets in rep records, history and set-distribution charts; their recorded load volume stays zero.
