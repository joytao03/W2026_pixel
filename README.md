# CPEN321_26W1_ProjectName

_Keep this README up to date with the steps required to build and run the frontend and backend (including any scripts, config files, and environment variables). TAs ill follow these instructions._

## Requirements

Install the following before the frontend or backend setup steps:

- [git](https://git-scm.com/install/)


--- 

## Frontend Setup

### Requirements

- [Android Studio](https://developer.android.com/studio) (latest version)
- [Java 17](https://adoptium.net/temurin/releases/?version=17)
- [Android SDK](https://developer.android.com/studio#command-tools) with API level 36+ (Android 16)

### Setup

1. **Open project**: Open the `frontend/` directory in Android Studio
2. **Sync Gradle**: Android Studio will automatically prompt you to sync the project. Click "Sync Now". You can also manually run `cd frontend && ./gradlew build` to trigger the sync and download the necessary dependencies.
3. **Configure Android SDK**: Ensure you have Android SDK 36 installed.
4. **Set up emulator/device**:
   - Create a new AVD (Android Virtual Device) by selecting Pixel 9 as the device and Android Baklava (API level 36) as the system image.
   - Alternatively, connect a physical Android device running Android 16 (API level 36).
5. **Setup app config**: Copy the example file, then fill in local values:
   ```bash
   cp frontend/local.properties.example frontend/local.properties
   ```
   Set at least:
   - `sdk.dir`: path to your Android SDK. Android Studio usually writes this the first time you open `frontend/`. On Mac it is often `sdk.dir=/Users/<username>/Library/Android/sdk`.
   - `API_BASE_URL`: backend URL baked into the APK. Use `http://10.0.2.2:3000` for the emulator (`10.0.2.2` is the host machine). For a physical device on the same Wi-Fi, use `http://<your-lan-ip>:3000`.


### Build and Run

- **Debug build**: Click the green play button in the toolbar, to compile the code, package a debug APK, and install it on the connected device or running emulator. Alternatively, from the project root, run `./scripts/run-frontend.sh`.
- **Release build**: Go to Build -> Generate Signed App Bundle or APK -> APK. Follow the on-screen instructions to create a key, and select the "release" build variant. You will then have to manually install the generated APK on your device or the running emulator.


### Backend Configuration

Ensure the backend server is running and update the base URL in the app configuration if needed.

---
## Backend Setup

You can run the backend in one of two ways:
* Locally via Node.js 
* Via Docker Compose

Both ways use the same `backend/.env` file (see below).

### Environment configuration

From the project root:

```bash
cp backend/.env.example backend/.env
```

Set at least:
- `JWT_SECRET`: a long random string used to sign auth tokens.
- `MONGODB_URI`: only needed for local development (default in `.env.example` assumes MongoDB on `localhost:27017`). Ignored when running via Docker Compose.
- `PORT` (optional): defaults to `3000` if unset.


### Option 1: Run locally

**Requirements:** 
- [Node.js](https://nodejs.org/en/download/) 22+
- [npm](https://docs.npmjs.com/downloading-and-installing-node-js-and-npm) 10+

**Setup:** 
1. Install dependencies:

   ```bash
   cd backend
   npm install
   ```

2. **Development** (TypeScript with auto-reload):

   ```bash
   npm run dev
   ```

3. **Production build** (optional):

   ```bash
   npm run build
   npm start
   ```

### Option 2: Run with Docker Compose

**Requirements:** 
- [Docker](https://docs.docker.com/desktop/setup/install) and [Docker Compose](https://docs.docker.com/desktop/setup/install) v2.24+
- [curl](https://curl.se/download.html)

**Setup**
1. **Start** (from the project root):

   ```bash
   ./scripts/run-backend.sh
   ```

   Or run Compose directly:

   ```bash
   docker compose up --build -d
   ```

2. **Stop**:

   ```bash
   docker compose down
   ```

## Additional Setup

_Please specify any other additional setup steps non-specific to either frontend nor backend_
## M1 local development progress

The current local milestone adds three home buttons, server information, and a timer surprise.
This is a development build, not the final M1 submission:
- Google sign-in uses Credential Manager and backend Google ID token verification. Configure both OAuth clients before testing sign-in. Server information is only requested by the UI after successful verification.
- Live Updates connects through Socket.IO over WebSocket to this backend. The backend uses the Node.js built-in WebSocket client to receive the course pixel stream and forwards the original JSON text immediately.
- Timer accepts minutes and seconds, supports cancellation, and opens a three-question food quiz when it expires. The app scores sampled recipes against category, cuisine and pantry preferences, shows the top three with reasons and missing ingredients, and lets users expand full recipes or change their answers.
- The timer continues across in-app navigation and activity rotation. It is not a background alarm service; do not rely on it after force-stop or device reboot.

### Run locally on Windows

1. In `backend/`, create `.env` with `PORT=3000`, `OWNER_FIRST_NAME=Joy`, and `OWNER_LAST_NAME=Tao`. Do not commit `.env`.
2. Run `npm.cmd install` if dependencies are missing, then `npm.cmd run dev`. Restart this process after editing `.env`.
3. Open `frontend/` in Android Studio. Keep `API_BASE_URL=http://10.0.2.2:3000` in `frontend/local.properties` for the emulator.
4. Build and run. Check **Login + Server**, then start a 5-second timer and navigate home; the surprise should still open when time is up.
5. For a physical device on the same Wi-Fi, use the development computer's LAN IP instead of `10.0.2.2`, then rebuild the APK. Allow the backend port through the local firewall if needed.

Local API routes:
- `GET /health`: backend health.
- `GET /api/server/ip`: IP address and its source. Without `SERVER_PUBLIC_IP`, returns a clearly labelled local development address, not a claimed public address.
- `GET /api/server/time`: server time in `HH:mm:ss GMT+HH:mm` / `GMT-HH:mm` format and its ISO timestamp.
- `GET /api/server/name`: configured first and last name; missing configuration returns 503.

Before cloud submission, configure the real `SERVER_PUBLIC_IP`, HTTPS/WSS, Google sign-in, verify the live relay, rebuild with the cloud base URL, and test the actual submitted APK on the required Pixel 9 API 36 emulator and a real device.

Backend checks: `npm.cmd run typecheck`, `npm.cmd run build`, and `npm.cmd test -- --runInBand`.

### Live pixel stream

- Course upstream defaults to wss://8.229.22.124. Override COURSE_WS_URL only when testing a different upstream.
- The Socket.IO Java client uses WebSocket transport only (no HTTP polling). Its Android org.json dependency is excluded in favor of the Android platform implementation.
- One course connection is shared by current viewers. It closes after the last viewer leaves; reconnects use bounded exponential backoff.
- Pixel JSON is carried unchanged in the pixel event. Status messages use a separate stream-status event.
- The course protocol has no explicit frame marker. Android infers a new picture when the next pixel follows a gap of at least 3.5 seconds, based on the documented five-second pause. A long network stall can therefore also clear the canvas. An initial connection may join a picture in progress; wait for the next cycle to see a full picture.
- Leaving the page or putting the app in the background disconnects the viewer. Reopening resumes from a blank canvas. Reconnect is available for manual recovery.
- TLS verification is enabled for the course upstream; do not disable it globally. Cloud reverse-proxy configuration must forward Socket.IO WebSocket upgrades at /socket.io/.
- Template engine requirement is Node 22.x. Current local checks ran with the installed Node 24.11.1, so verify Node 22.x before deployment.

### Google sign-in setup

Use the same Google Cloud project for both OAuth clients. Configure Google Auth Platform branding/audience first (add permitted test users if the console requires them).

1. Create an Android OAuth client with package com.example.cpen321application and the SHA-1 of the key signing the APK you will run.
2. For the current local debug keystore, SHA-1 is 5A:00:A8:42:E2:BA:73:55:4E:47:3C:71:BC:B2:EF:FE:64:D0:FB:B1. A different keystore requires another Android client registration.
3. Create a Web application OAuth client in the same project. Its Client ID (not Client Secret, and not the Android client ID) is GOOGLE_CLIENT_ID in both frontend/local.properties and backend/.env.
4. Restart the backend, sync Gradle and rebuild the APK. Run on an emulator with Google Play services and a Google account. Opening Login + Server launches Google sign-in.
5. Credential Manager returns an ID token. POST /api/auth/google validates it using google-auth-library (signature, audience, issuer and expiry), then returns only verified identity fields and expiration. The token is not saved or logged.
6. The three server-info endpoints expose only public server/author information. This milestone does not create an application session or protect private user data. The UI gates these requests on Google verification. Leaving the screen may require signing in again; sign-out clears the credential provider state.
7. Local HTTP credential transport is restricted to debug builds. Use HTTPS for the final cloud APK. No Firebase, OAuth client secret, or database is required for this flow.

Official references: https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation and https://developers.google.com/identity/sign-in/android/backend-auth

### Timer recipe surprise (TheMealDB)

When the countdown expires, the user answers three quick questions: preferred meal category, cuisine and ingredients on hand. Each question permits no preference or an empty selection. The frontend fetches candidate IDs from TheMealDB's category/area `filter.php` endpoints and full details from `lookup.php?i=...`, using public educational key 1. It samples at most 12 unique candidates and loads details with at most three concurrent requests. When category and cuisine are both unrestricted, it samples one of five meal categories. Results are explicitly a sample, not a global search. No backend update, new library, database, or private API key is required. Official API details: https://www.themealdb.com/api.php.

Our Kotlin ranking algorithm awards 40 points for matching category, 30 for matching cuisine and up to 30 for pantry coverage (`floor(30 * matched unique ingredients / listed unique ingredients)`). Rankings break ties by name and ID. Scores are not probabilities. Each top-three card explains its score and lists recipe ingredients not selected on hand. Category/cuisine are soft preferences, not exclusion filters. Conservative aliases match common forms such as eggs/egg or basmati rice/rice, without treating rice vinegar as rice. Other ingredient variants may be missed; this is not an allergy or dietary-safety checker. No cooking-time or nutrition estimates are invented.

Users can expand any recommended meal for its photo, measured ingredients, original instructions and source link, or change their answers and generate a new sample. There is no feedback-learning feature or persistent user profile. Quiz choices survive activity recreation; candidates reload instead of storing large recipe payloads in Android's saved-state Bundle. Partial fetch failures show the available results with a notice; a complete failure offers retry. Missing photos do not hide recipe text. Internet access and TheMealDB availability are required.

Manual acceptance: set a 5-second timer; complete the three questions; check top-three scores, reasons and pantry lists; expand a recipe and its source link; change answers; try unrestricted/empty choices; test offline retry and rotation. Confirm Google sign-in and Live Updates still work. Run `:app:testDebugUnitTest` for ranking tests (preference changes, ingredient alias boundaries, deduplication, empty data and deterministic ties). Generate a new signed release APK after accepting this feature; the previous APK contains the old random-recipe surprise.
