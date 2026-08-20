# CrewFit — Report sections 4.2, 5 and 6

Written to match `_report_template_v1.0`. Facts (dependency versions, dates,
counts) are taken from the repository, not from memory. Places where you have to
fill something in yourself are marked **[FILL IN]**.

State of the repository this describes: 71 commits, 47 Kotlin files (~6,600
lines), 14 test classes with 102 unit tests, 15 Activities, `minSdk 26`,
`targetSdk 36`, Android Gradle Plugin 9.1.1.

---

## 4.2 Third-Party Libraries

The course rule is to stay on native Android and avoid external libraries. We
kept to that: every library below is either part of the Android platform stack
(AndroidX / Material), or it is the only practical way to reach a system we were
explicitly allowed to use (backend, Health Connect, QR codes).

Where a library would have been convenient but not necessary, we wrote the code
ourselves — see "Deliberately not used" below.

### Platform libraries (Google / AndroidX)

| Library | Version | Purpose | Why this one (over alternatives)? |
| --- | --- | --- | --- |
| `androidx.core:core-ktx` | 1.10.1 | Kotlin extensions for the framework | Part of the standard Android toolchain; the alternative is calling the same APIs more verbosely. |
| `androidx.appcompat` | 1.6.1 | `AppCompatActivity`, backported theming | Needed for a consistent Material theme down to `minSdk 26`. |
| `androidx.activity:activity-ktx` | 1.8.0 | Activity Result API, `by viewModels` style scopes | The Result API replaces the deprecated `onActivityResult` and is the current way to start camera and file pickers. |
| `androidx.constraintlayout` | 2.1.4 | Layouts for all screens | Lets one layout file serve phone and tablet by changing only dimensions; the alternative (nested `LinearLayout`s) would have meant a second layout tree per screen. |
| `androidx.swiperefreshlayout` | 1.1.0 | Pull down to reload the screen | The gesture has never existed in the framework itself — it has lived in the support library since it was introduced, so there is nothing built in to fall back on. Writing it by hand means intercepting touch events on a `ScrollView`, driving the spinner animation and handling nested scrolling, for a worse copy of a widget Google ships. Same reasoning as Material and ConstraintLayout: AndroidX is the platform stack, not a third-party convenience. |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.6.2 | `lifecycleScope` for coroutines | Ties background work to the screen's lifetime, so a request cannot outlive the Activity and write into a dead view. |
| `com.google.android.material` | 1.12.0 | Material Design 3 components and theming | The course asks for proper Android UI; MD3 is Google's own implementation. Building buttons, text fields, dialogs and navigation bars by hand would have been weeks of work for a worse result. |
| `androidx.health.connect:connect-client` | 1.1.0 | Reading the daily step count | **Unavoidable.** Health Connect is reached through a system service over AIDL, not through an open HTTP or content-provider interface. There is no way to talk to it without this client. It also forced `minSdk` from 24 to 26. |

### Backend libraries

The course allows a backend as long as it is not the focus of development. We
use Supabase (hosted PostgreSQL with authentication and file storage) so that
several devices can share one crew — which is the entire point of the app.

| Library | Version | Purpose | Why this one (over alternatives)? |
| --- | --- | --- | --- |
| `supabase-kt` (`postgrest-kt`, `auth-kt`, `storage-kt`) | 3.1.0 | Database access, e-mail authentication, photo and voice-note storage | The official Kotlin client. Writing our own REST client against PostgREST plus the GoTrue token refresh, session persistence and PKCE flow would have been a project of its own with worse security properties. Only the three modules we actually use are included. |
| `io.ktor:ktor-client-android` | 3.0.3 | HTTP engine underneath supabase-kt | Transitively required; we pin the Android engine explicitly so no second engine is pulled in. |
| `kotlinx-serialization-json` | 1.7.3 | JSON for the data models | Required by supabase-kt; also used for handing a workout to the detail screen through an Intent. |

### Feature libraries

| Library | Version | Purpose | Why this one (over alternatives)? |
| --- | --- | --- | --- |
| `com.journeyapps:zxing-android-embedded` + `com.google.zxing:core` | 4.3.0 / 3.5.3 | Generating and scanning the crew QR code | Android has no built-in QR encoder or decoder. The alternative is ML Kit, which is larger and pulls in Play Services. ZXing is the reference implementation and is used for both directions. |

### Testing

| Library | Version | Purpose |
| --- | --- | --- |
| `junit` | 4.13.2 | 102 unit tests over the pure logic (points, medals, step goal, statistics, names, dates) |
| `androidx.test.ext:junit`, `espresso-core` | 1.1.5 / 3.5.1 | Instrumentation test scaffolding (default template) |

### Deliberately not used

These were removed or never added on purpose, and the functionality was written
by hand instead:

| Not used | What we did instead | Why |
| --- | --- | --- |
| **Coil** (was in the project until 13 Aug) | `ImageLoader.kt` — `HttpURLConnection`, an `LruCache` in memory, a disk cache, `inSampleSize` downsampling and circle cropping, ~200 lines | Removed to stay closer to the course rule. Our version does exactly what the app needs, including a per-URL `Mutex` so two screens requesting the same avatar cannot corrupt each other's temp file. |
| **Supabase Realtime** (removed 13 Aug) | Reload on `onResume`, and pull-to-refresh since 20 Aug | It kept a WebSocket open and cost battery, while no screen listened for live updates. Push notifications tell people that something happened; the gesture fetches it when they want it. |
| **Two redundant Ktor artifacts** (removed 13 Aug) | `ktor-client-android` only | `ktor-client-core` and `ktor-client-serialization` were already pulled in transitively. |
| **A charting library** (e.g. MPAndroidChart) | `DonutChartView.kt` — ~30 lines of `Canvas.drawArc`; week bars as weighted views with computed heights | Several hundred kilobytes for one ring is not a trade we wanted to make. |
| **A map SDK** (e.g. Google Maps) | `StaticMap.kt` — stitches OpenStreetMap raster tiles and draws a marker | We only ever show a static picture of one location. A full map SDK would need an API key, Play Services and far more code. Attribution is displayed as OSM requires. |
| **Glide / Picasso, Retrofit, Room, Dagger/Hilt** | Not needed | The app has 15 screens and one repository; a DI framework and an ORM would add indirection without solving a problem we have. |

---

## 5. Developer Diary

### 5.1 Development Milestones

Dates are the commit dates in the Git repository.

| # | Date | Activity | Result | Issues |
| --- | --- | --- | --- | --- |
| 1.0 | 2026-07-05 – 07-06 | First prototype: screens and navigation sketched out | Runnable skeleton | No persistence yet, everything local and in memory |
| 2.0 | 2026-07-08 | Core features in one push: crew creation, joining by code and QR, workout entry, workout list, dashboard, profile picture, camera and GPS | App usable end to end for one user | Data only on the device; no validation of any input |
| 2.1 | 2026-07-08 | Dark mode and first styling pass | Consistent look | Colours hard-coded in layouts |
| 3.0 | 2026-07-15 – 07-16 | Point system, workout duration, voice notes, crew challenges | Competition mechanics work | Point formula rewarded long sessions disproportionately; one test entry of 5,000 minutes distorted the ranking |
| 4.0 | 2026-08-11 | Backend migration to Supabase (auth, database, storage); classes and screens renamed to meaningful names | Several devices can share a crew | Every screen issued its own queries; a cold start could read a session that was not restored yet |
| 5.0 | 2026-08-13 | Quality pass: queries batched into one `CrewSnapshot`, session race fixed, three dependencies dropped, own `ImageLoader` written | Home screen went from ~20 requests to 5; Coil removed | Photo preview and sport picker were broken on the workout screen |
| 5.1 | 2026-08-13 | Camera and picker fixes on the workout screen | Both usable again | Camera black screen turned out to be an emulator setting, not an app bug |
| 6.0 | 2026-08-15 | Authentication hardened: registration reported failure although the account was created; calendar for the date of birth; input rules with live password requirements | Registration and login work reliably | — |
| 6.1 | 2026-08-15 | Location: pick the place on a map, store the name instead of raw coordinates, show a static map in the history | Workouts have a readable place | Older entries have no coordinates and get no map |
| 7.0 | 2026-08-15 | Complete redesign on Material Design 3; tablet and landscape layouts through resource qualifiers | One layout set serves phone and tablet | Tablet in portrait first showed a navigation rail instead of the bottom bar |
| 8.0 | 2026-08-16 | Bugs reported by the teammate testing on real devices: invisible date picker, unsaved profile edits lost on rotation | Both fixed | The date picker bug had been introduced by us two days earlier |
| 8.1 | 2026-08-16 | Self-chosen short name shown in crew, ranking and history | Crew views readable on narrow rows | Needs a new database column; app degrades gracefully without it |
| 8.2 | 2026-08-16 | Crew top three turned into a drawn podium; lint made to pass | Home screen has a focal point; build no longer aborts on lint | Lint reported 6 errors, all the same false positive |
| 9.0 | 2026-08-17 | Health Connect integration: today's step count on the home screen | App reads what the device already records | Costs one dependency and raises `minSdk` from 24 to 26 |
| 9.1 | 2026-08-17 | Daily step goal with ring, bonus points, step rings for every crew member | Steps feed into the competition | Needs the `step_days` table; without it rings stay empty |
| 10.0 | 2026-08-17 | Medals (7, derived from existing data) and a short profile for every crew member | Long-term motivation, crew members inspectable | — |
| 10.1 | 2026-08-17 | Activity list rewritten: compact rows plus a full detail screen | Ten workouts fit on screen instead of one and a half | — |
| 11.0 | 2026-08-17 | Password reset by e-mail with deep link and code | Users can recover their account | Deep link from a mail program lands on a blank page — see incident 6 |
| 11.1 | 2026-08-17 | Ranking screen extended with crew statistics, week chart, sport ring and per-member point breakdown | Ranking answers *how* someone got their points | — |
| 12.0 | 2026-08-20 | Device testing round on a Galaxy S9+ (Android 10) and a Lenovo TB311FU (Android 15); list of change requests written from it | Both ends of the supported range covered on real hardware | — |
| 12.1 | 2026-08-20 | Workout screen given the shared top bar with a back arrow | Navigation identical on every sub-screen | Going back needed "Cancel" at the foot of a long form; on a phone that meant scrolling past the whole thing |
| 12.2 | 2026-08-20 | Permission overview in the settings: camera, microphone, location and photos, each with its purpose and current state | Permissions inspectable and changeable at any time | An app cannot withdraw its own permission — a granted row therefore leads into the system settings |
| 12.3 | 2026-08-20 | Gallery access asked for before the picker opens, for the profile picture and the crew meme | Picking a picture now behaves like camera, microphone and location | Android does not require a permission here; we chose it deliberately — see incident 11 |
| 12.4 | 2026-08-20 | Profiles can be set to private, and they then stay out of the search | People who do not want to be found are not | Filtered in the database, not in the app: a profile that does not want to be found should not be transmitted either |
| 12.5 | 2026-08-20 | Crews are joined by asking: the founder accepts or declines under Members. Joining by code and QR stays direct | Nobody lands in a crew unannounced | Needs the `crew_join_requests` table and one extra rule on `crew_members` — accepting writes a row for *another* person, which an "own rows only" rule forbids |
| 12.6 | 2026-08-20 | Crew picture, built like the profile picture; only the founder can change it | Crews are recognisable at a glance | Reuses the existing `avatars` bucket instead of asking for a new one |
| 12.7 | 2026-08-20 | Tablet brought level with the phone: the 14 dimensions added after the tablet pass (analytics charts, sport ring, member bars, the number one's picture) now have tablet and landscape values | Every one of the 46 dimensions has a tablet value; the analytics section no longer sits phone-sized inside grown cards | Found by comparing the qualifier files against each other rather than by looking — the gap was invisible on a phone |
| 12.8 | 2026-08-20 | The home screen header shows the crew picture instead of the CrewFit logo once a crew has one | The crew you are looking at is recognisable without reading | Carried in the existing `CrewSnapshot` rather than as a sixth query, so the batching from milestone 5.0 is not undone |
| 12.9 | 2026-08-20 | Pull down to reload, on the five screens that read from the backend | A screen left lying open can be brought up to date without leaving it | Costs one AndroidX dependency; the alternative was re-implementing a Google widget by hand. This is the answer to dropping Realtime in 5.0: no permanent connection, but a way to ask |

**[FILL IN]** — add your own and Timo's milestones from before 5 August if you
want the early phase in more detail; the table above is reconstructed from
commit messages and does not know who did what.

### 5.2 AI Interaction Log — Critical Incidents

We used Claude (Anthropic) as a coding assistant, driven through Claude Code
with access to the repository, Gradle and a connected emulator. The assistant
could build, install and drive the app itself, which changed the nature of the
collaboration: most of these incidents were found by the assistant *running* the
app, and several were caused by it as well.

| # | Milestone | What happened | AI's output (brief) | Our action / validation |
| --- | --- | --- | --- | --- |
| 1 | 5.0 | Asked to improve performance, the assistant replaced ~20 per-screen queries with one batched `CrewSnapshot`. It also claimed the app still worked against Supabase after removing three dependencies. | "Removed Coil, Realtime and two Ktor artifacts; the backend is unaffected." | We did not take that on trust: we had it inspect the dependency tree, clone the repository fresh and build it, and query `/auth/v1/settings`. It held up. |
| 2 | 6.0 | Registration showed "Registration failed" although the account had been created and the confirmation mail had gone out. | The assistant first proposed changing the error message. | We insisted on a cause. It then read the supabase-kt sources and found the return value of `signUpWith` had been interpreted backwards — `null` means auto-confirmed, not failure. Fixing the interpretation fixed the bug. |
| 3 | 8.0 | **AI-caused.** During the Material 3 dialog rework the assistant gave the date picker a `ThemeOverlay` meant for `MaterialAlertDialog`, including a transparent `windowBackground`. On our emulator it looked fine; on Timo's real phone and tablet the calendar was invisible and unusable. | "Dialogs now follow the Material 3 theme." | Timo found it in device testing. The assistant reproduced it by rebuilding the previous commit on the tablet emulator, then replaced the framework dialog with `MaterialDatePicker` — which cannot end up without a background. Lesson: a theme override that looks right on one device is not verified. |
| 4 | 8.2 | The assistant reported that `gradlew lintDebug` fails because of an Android Gradle Plugin bug with the configuration cache. | "`AndroidLintAnalysisTask` … error writing value — this is the AGP version, not our change." | Wrong, and it corrected itself when asked to re-check: the failure came from its own `--offline` flag, which stops lint from resolving its tooling. Without the flag lint ran and reported **6 real errors and 81 warnings** — including three fields that were unreachable by keyboard. |
| 5 | 9.1 | While writing tests for the step-goal ring, a test the assistant had written failed. | Expected 100, got 0 for `Long.MAX_VALUE`. | The test was right and the implementation wrong: `steps * 100 / goal` overflows, turns negative, and left the ring empty at absurd step counts. Capping now happens before the multiplication. This is the clearest example of tests paying for themselves. |
| 6 | 11.0 | The password-reset link from the mail led to a blank page. The assistant had assumed a `crewfit://` deep link would work from an e-mail. | "The address is probably not allow-listed in Supabase." | It measured instead of guessing: the app sends the correct `redirect_to`, and Supabase answers a verify call with `Location: crewfit://reset-password#…`, so the address *is* allowed. The real cause is that mail programs open links in their own built-in view, which does not know custom schemes. Nothing on our side can fix that, so we added a code in the mail as a second path. |
| 7 | 11.0 | Following on from 6, the assistant told us to add `{{ .Token }}` to the Supabase mail template. | "Authentication → Email Templates → Reset Password, add the token." | Not possible in our project: without a custom SMTP server Supabase locks the templates ("Set up custom SMTP to edit templates"). The instruction was written from a general assumption, not from our dashboard. We are setting up Mailjet, which also removes the hourly mail limit that had been blocking our teammate's confirmation mails. |
| 8 | 8.1 | The short name could be set and changed but never removed — emptying the field brought the old value back. | The assistant first "fixed" it and reported success. | Its own device test was flawed: it had used the Back key to close the keyboard, which left the screen and discarded the edit, so the run proved nothing. Repeated with Escape and a check after every step, the real cause showed: supabase-kt serializes with `encodeDefaults = false`, so a `null` equal to the property default is not sent at all and Postgrest leaves the column untouched. |
| 9 | 5.1 / 6.x | Several bugs blamed on the app turned out to be environment: the camera black screen was an emulator webcam setting, a rejected query was clock drift between emulator and server, and a "not rotating" test was auto-rotate overriding the forced rotation. | In each case the first hypothesis pointed at our code. | We made a habit of asking for evidence before accepting a diagnosis — `dumpsys` output, the server's `Date` header, a screenshot at the right size. Roughly a third of the "bugs" this month were not in the app. |
| 10 | throughout | The assistant proposed features we did not ask for (a second ring, weekly trends, more medals). | Suggestions listed at the end of each change. | We picked what fitted the course scope and rejected the rest. Keeping the decision on our side mattered: the assistant will happily grow the app indefinitely. |
| 11 | 12.3 | We asked for an "Allow access to gallery?" prompt, so that picking a picture behaves like the camera, the microphone and the location. | The assistant advised against it: the system picker hands the app exactly one file and needs no permission at all, whereas `READ_MEDIA_IMAGES` asks for every photo on the device. It recommended keeping the picker as it was. | We overruled it — for the people using the app, one rule for all media is worth more than the smaller scope, and the inconsistency was what we had noticed in testing. The assistant then implemented it with the Android 14 partial access (`READ_MEDIA_VISUAL_USER_SELECTED`), so single pictures can be released instead of the whole gallery. Worth recording as the case where the assistant's objection was technically sound and we still decided against it. |
| 12 | 12.2 | Asked for permissions that can be "managed manually at any time" in the settings. | The assistant stated plainly that an app cannot revoke its own permission, and built the row so that a granted permission opens the system settings instead. | Accepted. The alternative would have been a switch that silently does nothing when turned off. |

**Working style, and how it changed.** In the first weeks AI was used for
isolated pieces of code that we pasted in. From August onwards the assistant
worked in the repository directly and could build, install, and drive the app on
the emulator. That made it much faster, and it made verification the bottleneck
rather than typing: the useful question stopped being "does it compile" and
became "what did you actually observe". Incidents 3, 4, 6 and 8 are all cases
where the first answer sounded plausible and was wrong, and asking for the
evidence changed the outcome.

**[FILL IN]** — add any incidents from your own or Timo's AI use (ChatGPT for
mockups, etc.) that are not visible in this repository.

---

## 6. Android Features Report

### 6.1 Features Used

| Feature | Where it's used (screen/class) | Why this feature was needed |
| --- | --- | --- |
| **Activities & explicit navigation** | 15 Activities, e.g. `MainHubActivity`, `WorkoutTrackingActivity`, `LeaderboardActivity`, `MemberProfileActivity`, `WorkoutDetailActivity` | Each task is a screen with its own lifecycle. Navigation is by explicit `Intent`, with factory methods (`MemberProfileActivity.intent(...)`) so extras keys exist in exactly one place. |
| **Activity lifecycle** | `MainHubActivity.onResume` reloads the crew; `onStop` releases the `MediaPlayer`; `WorkoutDetailActivity.onStop` | Data must be current when a screen is returned to, and a media player must not keep playing or holding resources after the screen is gone. |
| **Configuration changes** | `WorkoutTrackingActivity.onSaveInstanceState`, `ProfileActivity` (`savedInstanceState == null` guard), `BirthDatePicker.reattach` | Rotating the device recreates the Activity. Without this a taken photo, a recorded voice note, or unsaved profile edits were lost. |
| **Runtime permissions** | `AppPermission` (the catalogue), `WorkoutTrackingActivity` (camera, microphone, location), `GalleryPicker` (photos), `SettingsActivity` (overview and management), `MainHubActivity` (Health Connect) | Android requires these to be requested at runtime, and every one of them can be refused. The permissions the app knows about are listed once in an enum; the settings screen builds its rows from it, so a new permission cannot be forgotten there. |
| **Camera via `ACTION_IMAGE_CAPTURE`** | `WorkoutTrackingActivity` | A photo is the proof that a workout happened — the core idea of the app. We hand off to the system camera rather than embedding a viewfinder. |
| **`FileProvider`** | `com.example.mobilese.fileprovider`, `res/xml/file_paths.xml` | Since Android 7 a `file://` URI may not be passed to another app. The camera writes into our app directory through a content URI. |
| **`MediaRecorder` / `MediaPlayer`** | `WorkoutTrackingActivity` (record), `VoicePlayer` (play, used by home screen and workout detail) | Voice notes. Playback uses `prepareAsync()` because the notes are URLs and a synchronous `prepare()` on the main thread risks an ANR. |
| **`LocationManager` + `Geocoder`** | `WorkoutTrackingActivity`, `LocationNames` | The training location. `Geocoder` turns coordinates into a readable place name; the API 33 listener variant is used where available, the deprecated blocking call below it. |
| **Health Connect** | `HealthSteps`, `MainHubActivity`, `HealthPrivacyActivity` | Today's step count, read from the platform health database rather than counted by us. |
| **`SharedPreferences`** | `AppRepository` (session marker, joined crew code) | Small key-value state that must survive a process restart. |
| **Internal storage** | `ProfileActivity.storeProfilePicture`, `WorkoutTrackingActivity` | Photos and recordings are written to app-private storage before upload. |
| **Deep links (`intent-filter`)** | `ResetPasswordActivity` (`crewfit://reset-password`) | The password-reset link from the e-mail opens the app directly. |
| **Activity Result API** | `registerForActivityResult` in 5 places: camera, image picker, permissions, Health Connect permissions | The current replacement for `onActivityResult`; type-safe contracts per use case. |
| **Custom `View` and `Canvas`** | `DonutChartView`; vector drawables for podium, medals and icons | The sport distribution ring is drawn with `drawArc`. Podium and medals are hand-written vector drawables with gradients. |
| **`GridLayout`** | Medal grid in `ProfileActivity` and `MemberProfileActivity` | Wraps by itself; no `RecyclerView` needed for seven fixed entries. |
| **Resource qualifiers** | `values-sw600dp`, `values-land`, `values-sw600dp-land`, `layout-sw600dp-land` | One layout set serves phone and tablet; only dimensions change, except in tablet landscape where a two-column layout with a `NavigationRailView` replaces the bottom bar. |
| **Material 3 theming** | `themes.xml` — colour roles, type scale, shape appearances | Consistent dark theme; components take their colours from roles rather than hard-coded values. |
| **Coroutines with `lifecycleScope`** | Every screen that loads data | Network work off the main thread, cancelled with the screen. |
| **Accessibility** | `contentDescription` set in code for podium places and medals; `focusable` + `focusableInTouchMode` on picker fields | The podium shows only pictures, so the name has to reach a screen reader some other way. |

### 6.2 Implementation Details

#### (a) Camera, `FileProvider`, and a permission that is not obviously needed

Taking a photo hands off to the system camera with `ACTION_IMAGE_CAPTURE`. The
target file is created in the app's own directory and turned into a content URI
by `FileProvider`, because a `file://` URI would raise a
`FileUriExposedException` on Android 7 and above. The result comes back through
`registerForActivityResult(TakePicture())`.

The subtle part is the permission. We do not open a camera ourselves, so the
`CAMERA` permission looks unnecessary — but the manifest declares it (for the
`<uses-feature>` entry). Android then *requires* it for `ACTION_IMAGE_CAPTURE`
as well, and throws `SecurityException: Permission Denial … with revoked
permission android.permission.CAMERA` if it has not been granted. The app
crashed on any device where the permission had been denied. It is now requested
before the camera starts, and the launch is additionally wrapped so a permission
revoked in between cannot kill the app.

**Edge cases:** permission denied → a hint is shown and the workout can still be
completed except for the photo, which is mandatory, so the user is told what is
missing; no camera app present → the launcher's `SecurityException`/
`ActivityNotFoundException` is caught; rotation while the camera is open → the
photo path and URI are restored from `onSaveInstanceState`.

#### (b) Health Connect

`HealthSteps` is the only place in the app that knows Health Connect exists.
Availability is checked with `HealthConnectClient.getSdkStatus` *before*
`getOrCreate`, because the latter throws when the service is missing —
Health Connect is part of the system only from Android 14, and a separate app
before that (hence the `<queries>` entry in the manifest, without which it stays
invisible to us). Permissions use
`PermissionController.createRequestPermissionResultContract()`; the result is not
interpreted, the screen simply re-reads, which gives the same display whether the
user granted, denied or dismissed.

The step count itself is an aggregate query
(`StepsRecord.COUNT_TOTAL` between local midnight and now) rather than a sum over
records: a phone and a watch record the same steps, and Health Connect removes
the overlap. Summing the records ourselves would over-count.

The screen distinguishes four states rather than showing a silent zero: a number,
permission missing (with a button), Health Connect unavailable, query failed.

**Edge cases:** access withdrawn in system settings → re-read on every `onResume`;
device without Health Connect → explicit message; day boundary → local midnight
via `LocalDate.now().atStartOfDay(ZoneId.systemDefault())`, not "24 hours ago",
because a day has 23 or 25 hours around daylight saving.

#### (c) Images without an image library

`ImageLoader` replaces Coil in about 200 lines: `HttpURLConnection` for the
download, an `LruCache` sized from the app's memory class, a disk cache in
`cacheDir`, `inSampleSize` decoding so a 4000 px photo is not decoded at full
size into a 200 px view, and circle cropping for avatars. All decoding and file
I/O runs on `Dispatchers.IO`.

Two failure modes we hit and fixed: two screens requesting the same avatar
simultaneously wrote into the same temp file and produced a corrupt bitmap —
fixed with a per-URL `Mutex` and unique temp file names; and avatar upload
compressed the bitmap on the main thread, which stuttered visibly on large
photos.

**Edge cases:** no network → the placeholder stays and the failure is logged, no
crash; undecodable file → logged and deleted so the next attempt is clean;
view recycled → views are inflated fresh per row rather than reused, so a late
image cannot land in the wrong row.

#### (d) Location and a map without a map SDK

The user picks the place on a map rather than having coordinates recorded
silently. `StaticMap` computes the OpenStreetMap tile numbers for a coordinate
and zoom level, downloads the 2×2 tiles around it, stitches them into one
bitmap and draws a marker in the centre; attribution is displayed as the tile
usage policy requires. `LocationNames` wraps `Geocoder` with the API 33 listener
variant and the deprecated blocking call as a fallback, so a place gets a
readable name.

**Edge cases:** location permission denied → the place can still be named by
hand; `Geocoder` unavailable or returning nothing → the user's own name for the
place is used; older entries without coordinates → no map is shown rather than
an empty frame.

#### (e) Degrading gracefully when the backend schema is behind

Three features need database columns or tables that have to be created by hand
in Supabase (`profiles.display_name`, `activities.latitude/longitude`,
`step_days`). If they are missing, Postgrest rejects the whole write. The app
detects the specific error (`PGRST204`, `PGRST205`) and falls back: a workout is
saved without coordinates, a profile without the short name, and the ranking
works without step rings. This was verified against the live project *before*
the table existed, not just reasoned about.

### 6.3 AI's Role in Android-Specific Code

**Where it was reliable.** Boilerplate that is well represented in
documentation: Activity Result contracts, `FileProvider` configuration,
coroutine scoping, resource qualifiers for tablets, Material 3 theming, vector
drawables. The MD3 redesign across 15 screens would have taken us far longer by
hand and the result is consistent.

**Android-specific mistakes it made.** These are the ones that cost us time:

1. **Theming a system dialog with the wrong overlay** (incident 3). It applied a
   `ThemeOverlay` intended for `MaterialAlertDialog` to `android:datePickerDialogTheme`
   and set a transparent `windowBackground`. This is a plausible-looking
   combination that is simply wrong, and it only showed on real devices.
2. **A permission model it did not check.** It did not anticipate that declaring
   `CAMERA` in the manifest makes it mandatory for `ACTION_IMAGE_CAPTURE` even
   though the app never opens a camera itself. The crash appeared only after we
   made the photo mandatory and started testing the denied case.
3. **An assumption about deep links from e-mail** (incident 6). It designed the
   password reset around a custom-scheme link without flagging that mail clients
   open links in an embedded view that cannot hand off to an app.
4. **A serialization default it did not know** (incident 8). That
   `encodeDefaults = false` silently drops a `null` from an upsert is exactly the
   kind of framework detail that produces a bug with no error message.
5. **A hard-coded corner radius** it wrote for the profile picture (56 dp) while
   the size varies from 88 dp to 160 dp by device, so the avatar was a rounded
   square on the tablet. Found later while touching the same file.
6. **Wrong self-diagnosis** (incident 4). Blaming a lint failure on the Gradle
   plugin rather than on its own `--offline` flag hid 6 real errors for a day.

**What we changed in how we worked.** We stopped accepting "it works" and asked
what was observed — a screenshot, a `dumpsys` line, a log entry, an HTTP status.
The assistant was noticeably better when it had to produce evidence; several
times it corrected its own diagnosis in the process of gathering it. We also
learned to be suspicious of changes that only ever ran on one device: incidents
3 and 5 were both "looks right on the emulator" bugs.

**Hallucinations in the narrow sense** (invented APIs) were rare — the failures
were about *behaviour*: assumptions about how a component behaves at runtime,
across devices, or against a real server. Those are the ones that need testing,
not reading.
