# CrewFit — Report sections 4.2, 5 and 6

Written to match `_report_template_v1.0`. Facts (dependency versions, dates,
counts) are taken from the repository, not from memory. Places where you have to
fill something in yourself are marked **[FILL IN]**.

State of the repository this describes: 107 commits in two modules — `app`
(phone and tablet) and `wear` (the watch). 76 Kotlin files (~12,400 lines), 22
test classes with 209 unit tests, 18 Activities on the phone and 2 on the
watch, `minSdk 26`, `targetSdk 36`, Android Gradle Plugin 9.1.1.

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

Two libraries below come from Google Play services rather than AndroidX. They
are not part of the platform in the strict sense — a device without Play
services cannot run them — but each is the only interface Android offers for
what it does.

| Library | Version | Purpose | Why this one (over alternatives)? |
| --- | --- | --- | --- |
| `com.google.android.gms:play-services-wearable` | 18.2.0 | The link between watch and phone (Wearable Data Layer), in both modules | **Unavoidable.** Wear OS gives no other interface for it. Talking to the watch over raw Bluetooth ourselves would mean writing a pairing, transport and retry protocol that Android already runs — and it would stop working the moment the system reconnected the devices itself. |
| `com.google.firebase:firebase-messaging` (via `firebase-bom` 33.7.0) | BOM 33.7.0 | Push notifications for new workouts and changes in the ranking | **Unavoidable for push.** A notification that reaches a phone whose app is closed has to come through the system's push channel, and on Android that is Firebase Cloud Messaging. The alternative — keeping our own connection open — is what we removed with Supabase Realtime, for battery reasons. |

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
| `junit` | 4.13.2 | 209 unit tests over the pure logic (points, medals, step goal, statistics, names, dates, input rules) |
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
| 12.10 | 2026-08-20 | The join requests section stays visible for the captain even with nothing pending; the crew's founder is labelled "Captain" in the member list | A captain who has never had a request still knows the section exists and where it will appear | Name, founder and picture were three queries against the same row; folded into one `getCrew()` |
| 12.11 | 2026-08-20 | Crew members can react to a workout with one of five emoji and leave comments — five ready-made ones that post on a tap, or their own text | A workout is no longer a number in a list that nobody answers | Needs two tables. The comment time is text written by the app: as `timestamptz` PostgREST returns an offset the app's formatter does not read, and the time would have been hours out |
| 12.12 | 2026-08-20 | Levels 1–100 with prestige, from the points earned across all crews; level and total shown in one's own profile and in every member's | A goal beyond the current crew's weekly ranking | The per-person point calculation was pulled out of `Scoreboard` so ranking and level cannot drift apart. No new table: the total is derived from data that already exists |
| 12.13 | 2026-08-20 | The three latest activities on the home screen open the workout; the podium's top three open their profiles; the bottom bar's last tab leads to the settings, with the profile as its first entry | The same row and the same face behave the same wherever they appear | The gear in the profile's top bar had to go — with the settings as the parent it would have been a loop between two screens |
| 12.14 | 2026-08-20 | Home header no longer clips a two-line crew name; the activity list shows only the crew being viewed, in both its scopes | Two lists side by side now mean the same thing | The name was anchored to the 44dp logo top and bottom, so a wrapped name was squeezed to the logo's height |
| 12.15 | 2026-08-20 | The challenges tab makes way for a personal "Me" screen: points, level, streak, workouts filterable by crew, medals. Running challenges moved to the home screen, creating them to the crew screen | Everything personal in one place, across all crews | Lint caught two ids missing from the tablet-landscape layout — `findViewById` would have returned null and crashed there |
| 13.0 | 2026-08-20 | Wear OS app as its own module: pick a sport on the watch, it counts the time and reads the heart rate, the phone finishes the entry | A workout can be started without carrying the phone | The watch and the phone must share an `applicationId`, or the system does not treat them as one app |
| 13.1 | 2026-08-20 | A workout from the watch can be discarded with a long press | Starting the watch by accident no longer forces an entry | Until then the card could only leave the queue by being logged |
| 14.0 | 2026-08-21 | Co-location over Bluetooth Low Energy, written against the platform API: each phone advertises an 8-byte identifier under a 16-bit service UUID and scans for the others. Only members of the same crew are shown | Double points can no longer be claimed by typing a name — the phones have to be in the same room | The identifier had to be shortened to 8 bytes and the service UUID to its 16-bit form: an advertising packet holds 31 bytes, and a full 128-bit UUID alone eats 16 of them |
| 14.1 | 2026-08-21 | Not just discovery but a real connection: `createBond()` for system-level pairing, then a GATT link over which the phones exchange their identifiers | "Paired" now means what it says — the pairing is visible in the Android Bluetooth settings and can be undone there | Android's GATT stack processes one operation at a time. `requestMtu()` followed straight away by `discoverServices()` looks like it works and drops the link seconds later; the second call has to wait for `onMtuChanged` |
| 14.2 | 2026-08-21 | The joint workout is timed instead of typed: one picks the sport, both see a running clock, and whoever stops it stops it for everyone. Sport and duration are then locked in the form | Two people who trained together cannot end up filing two different workouts | The clock stops when the connection drops and carries on when it is back. Photo, place and voice note are filled in afterwards, each for themselves — the connection is not needed for that |
| 14.3 | 2026-08-21 | Group training: any number of phones can join one session. The one who connects broadcasts the roster so every device knows the whole group | A third person no longer has to be left out of a run | Still double points and not triple: the multiplier rewards training together, not the size of the group |
| 14.4 | 2026-08-21 | A joint workout is marked as such for the whole crew — the participants are named in the crew feed and in the workout, and the history marks the row with an icon | The point of training together is that the crew sees it | `Activity.partner_id` became `partner_ids uuid[]`. Postgres cannot put a foreign key on array elements, so a departed member shows as "Unknown" rather than the row vanishing |
| 14.5 | 2026-08-21 | The "Me" tab carries one's own picture and short name in its header instead of the word "Me" | The screen that is about you looks like it | Built into the shared top bar next to the existing action slot, so it is available to any screen and the sub-screens stay identical |
| 15.0 | 2026-08-22 | Crew battles: a challenge can be aimed at another crew. It lives as one row carrying the challenged crew in `opponent_crew_id`, and nothing counts until that crew has accepted. Winning also lifts the crew's own level | A challenge stops being only an internal affair — a crew now has something to play for against another one | The winner is written nowhere: it follows from `challenge_rewards`. A column for it would be a second truth that can fall out of step with the first. The crew level counts the members' average and not their sum, or it would mostly show how many people a crew has |
| 15.1 | 2026-08-22 | A battle still waiting on the other crew no longer reads "Challenge Completed!" | A battle that has not started cannot have been won | Our own crew being past the goal was being read as the battle being over |
| 15.2 | 2026-08-22 | Push when a crew is challenged, on its own notification channel | The challenged crew learns of the question without having to open the app | The same edge function serves both triggers and tells them apart by the row itself — a challenge with an opponent is not an activity. A second function would have meant a second deployment and a second place holding the same Firebase key |
| 15.3 | 2026-08-22 | The device registers for push on every app start, not only while signing in | A reinstall no longer silences a device for good | Whoever stayed signed in and reinstalled got a fresh token from Firebase: the old one still stood in the database, the new one nowhere, and nothing was ever delivered again — without any error to see |
| 15.4 | 2026-08-22 | Two labels drawn on top of each other in the crew search | — | The field takes its label from the `TextInputLayout` around it; a second one set on the input itself made Material draw both |
| 15.5 | 2026-08-22 | A completed challenge pays out by share of the work instead of evenly across the crew. Each member's share stands next to their contribution on the card | In a crew of two where one person carried it alone, half the points went to someone who watched | The pot is spent to the point: everybody gets their floor, and what is left over goes to the largest cut-off remainders. Rounding each share on its own would have handed out one or two points too many or too few |
| 15.6 | 2026-08-23 | The workout on the watch moves into a foreground service of type `health`; the activity is only the display in front of it. Pause and resume, step count alongside the pulse, a warning when the phone is out of reach, and the phone answers back once the activity is really saved | A palm on the display or a swipe to the watch face no longer loses a training — and it always hit the long ones, because the short ones survive anyway | A service is no promise either, so the state goes into the preferences on every change and every thirty seconds. A workout killed with `kill -9` carried on as if nothing had happened; only a restart of the watch itself is given up, because elapsed time and step counter both start over |
| 16.0 | 2026-08-27 | Full device pass over every screen, driven from the outside: 20 activities, rotation on five screens, the form with photo, voice note and place carried through a rotation | Zero crashes across phone, tablet and watch | Four defects came out of it, three of them in the joint training — see 16.1 to 16.3 |
| 16.1 | 2026-08-27 | **Bug.** A failed Bluetooth connection left its registration in the GATT stack behind: `close()` was only called for connections that had succeeded | The feature stopped working after a few attempts and only a restart of the app brought it back | Proven with `dumpsys`: the phone held four interfaces at once, two of them stranded. Android hands out a fixed number per process, and once they are gone every further connect ends in status 133 |
| 16.2 | 2026-08-27 | **Bug.** The two error paths of the connection reported the problem and returned — without clearing `connecting` | After one failure the device swallowed every further attempt in silence until the app was restarted. The old message stayed on screen, so it looked as if tapping did nothing |
| 16.3 | 2026-08-27 | **Bug.** Switching Bluetooth off locally was not noticed: only the peer saw it | Two people training together ended up with different durations — exactly what the shared session exists to prevent. One clock stopped at 01:02 while the other ran on to 03:24 | `PartnerLink` listened for the bond state but not for `BluetoothAdapter.ACTION_STATE_CHANGED`, and the stack's callbacks die with the radio |
| 16.4 | 2026-08-27 | **Bug.** The device's own GATT server reported the very link the device was building itself, and the app took it for an incoming call and dropped its own attempt | This was the root of the flakiness: sometimes it connected, sometimes the connection hung until status 22 after thirty seconds — a race between two callbacks | A radio link is not one-way. Comparing the address tells an incoming call from one's own |
| 16.5 | 2026-08-27 | The joint training pairs differently: **both** pick each other, both land on a connecting screen, and the workout screen follows once the link stands | The race is gone by construction | Who calls is decided by comparing the identifiers - the smaller one calls, the other waits. Both devices compute the same answer without agreeing on anything. A retry every five seconds covers the case where the other taps later |
| 16.6 | 2026-08-27 | **Bug.** Leaving the workout form without saving kept the joint session in memory | The next recording did not ask whether you trained together and silently filed the old partner and the old duration - a workout that never happened, with double points | `Cancel` and the back arrow only called `finish()`. Cleared in `onDestroy` now, but only when actually finishing: a rotation destroys the activity too, and there the session has to survive |
| 16.7 | 2026-08-27 | **Bug.** A challenge counted every activity the crew had ever logged | It was part-way finished the moment it was created, and a battle began with a lead nobody had run | The model had a deadline but no start. `starts_at` now holds the moment of creation for a challenge and the moment of **acceptance** for a battle |
| 16.8 | 2026-08-27 | Crew battles get their own screen, reachable from the crew tab beside the challenges. "Start Crew Battle" plus the pending invitations, split into the ones you sent and the ones you received | A battle nobody has accepted no longer sits among the running goals with a full progress bar | Declining deletes the row instead of marking it. It is one row for both crews, so a "declined" note stayed with the challenger forever and could not be cleared away |
| 16.9 | 2026-08-27 | The weekday letters in the analytics are fixed to English | The rest of the app is English; a German "D" next to "Kilometres" read like an oversight | They came from the device locale, which is right in principle and wrong in effect |

| 17.0 | 2026-08-28 | The analytics week runs Monday to Sunday and always marks the current day | The week used to start on whatever day it happened to be — Friday, in the screenshot that made us look | The bars came from the last seven entries in a map instead of from a calendar. Now the week is built from its Monday, and days without a workout are drawn empty rather than left out |
| 17.1 | 2026-08-28 | **Bug, and the one that mattered.** Joint training only ever worked right after a fresh system pairing. Once the two devices stayed bonded, the connection came up in about a second and then nothing happened at all | Two weeks of "sometimes it works" ended. For a marker who tries the feature a few times, one success in ten is worse than none | With a bonded peer, `discoverServices()` returns `true` and never calls back — twice over, six seconds each, until the link times out after thirty. Without a bond the same lookup answers in a second. `refresh()` does not help: the second lookup comes back unchanged after eleven milliseconds. An existing bond is therefore dropped before connecting, and the pair is made fresh |
| 17.2 | 2026-08-28 | The scan stops while a connection is being built — on the caller when they pick someone, on the other side as soon as somebody knocks | Whoever tapped first used to call into a device that was still scanning at full power. **That order failed reproducibly; the reverse order worked** | A scan in `SCAN_MODE_LOW_LATENCY` listens practically without pause, and the new connection gets almost no radio time. Advertising continues, or the other side could not accept at all |
| 17.3 | 2026-08-28 | Service discovery has a deadline and one retry instead of waiting for a callback that may never come | The screen used to sit still for thirty seconds and then report a dropped connection | `discoverServices()` returns `false` when the stack is busy and sometimes `true` without ever calling back. Both looked identical from the outside: nothing |
| 17.4 | 2026-08-28 | `requestMtu` removed entirely | Writes reported success and arrived nowhere | An unanswered MTU request blocks the GATT queue, which handles one operation at a time. Twenty bytes per message is enough for sport, start and stop |
| 17.5 | 2026-08-28 | **Bug.** The pairing broadcast was matched against the advertised address | The app waited out the full twelve-second deadline although the pairing had long been confirmed on both devices | `ACTION_BOND_STATE_CHANGED` carries the device's real address, while the request went to the random one from the advertisement. A strict comparison threw the good news away |
| 17.6 | 2026-08-28 | The watch app builds against Android 8 instead of Android 11 | Android 11 means Wear OS 3 and newer — which ruled out most watches people actually wear | Nothing needed the higher bar: lint reports no call above the new minimum, and the three places that do use something newer already ask for the version first. The foreground service now declares `dataSync` beside `health`, so older systems get a type they understand |
| 17.7 | 2026-08-28 | Fourteen full runs of the joint training in a row, in both directions, with the app kept open and with it closed | Connect, sport, start, and stop from the *other* side — the whole chain, every time | Each run pairs freshly, so every attempt is the same as the first. That is also what it looks like to the people using it: the system asks on both devices, both confirm |

### 5.1.1 The last two days, in before and after

The four commits of 27 and 28 August are the end of the project, and they are
where the feature that had never been dependable became dependable. The table
says what the app did before and what it does now.

| What | Before (up to 26 August) | Now | Commit |
| --- | --- | --- | --- |
| Joint training, second attempt | Worked once after a fresh pairing. Every attempt after that hung in "Connecting…" for 60 to 90 seconds and then failed | Pairs freshly every time, with the system prompt on both devices. **14 runs in a row without a failure**, phone-first and tablet-first, app open and app closed | `9dfe1b8` |
| Who taps | One person tapped the other's name | Both pick each other; who calls is decided by comparing the identifiers, so exactly one calls and there is no race | `2078226` |
| While connecting | Both devices kept scanning at `LOW_LATENCY` throughout | Both stop scanning the moment a partner is picked or somebody knocks; advertising continues | `9dfe1b8` |
| Service discovery | Waited for a callback that sometimes never came, for thirty seconds | Deadline, one retry, then a clean abort with a message | `9dfe1b8` |
| Packet size | `requestMtu(128)` in the critical path, blocking the GATT queue when unanswered | Not requested at all | `9dfe1b8` |
| Stopping a shared workout | Only the caller could stop it for both | Either side can; the other's clock stops too | `2078226` |
| Bluetooth switched off mid-session | Only the peer noticed; the two clocks drifted apart (01:02 against 03:24) | Noticed locally too, and the session ends on both | `2078226` |
| Crew battles | Sat among the running challenges with a progress bar before anyone had accepted | Own screen with "Start Crew Battle" and pending invitations split into sent and received; a battle appears under Challenges only once accepted, and declining deletes the row | `2078226` |
| Challenge points | Counted every activity the crew had ever logged, so a challenge was part-finished the moment it was created | Counted from `starts_at` — creation for a challenge, **acceptance** for a battle | `2078226` |
| Analytics week | Started on whatever weekday it happened to be, with German letters | Monday to Sunday, English letters, current day marked | `2078226`, `17.0` |
| Leaving the workout form | Kept the joint session in memory, so the next recording silently filed the old partner and duration | Cleared when the form is really finished, kept across a rotation | `2078226` |
| Watch app | Required Android 11, i.e. Wear OS 3 and newer | Runs from Android 8; checked on an emulator through sport, sensor prompt, stopwatch with heart rate, pause, resume and stop | `a67c4ea` |
| Documentation | The diagrams still showed the MTU handshake and one paragraph had lost its method names | Bluetooth section rewritten around what the devices actually did, with the trace that proved it | `dd644e8`, this commit |

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
| 13 | 14.1 | **AI-caused.** After the Bluetooth link was rebuilt for real pairing, the connection dropped a few seconds after every connect. | The assistant first suspected distance and radio interference. | Wrong twice over, and both causes were its own: it had put `discoverServices()` directly after `requestMtu()`, and it confirmed the partner before the connection existed. We made it read the logcat of *both* devices instead of guessing — status 22 on one side and 133 on the other showed the local host was terminating the link. |
| 14 | 14.1 | Pairing worked sometimes and sometimes not; occasionally a tap led straight to the workout screen with no pairing at all. | The assistant looked for the fault in the connection code. | The cause was in how we used it: both of us were tapping the other's name, so two connection attempts crossed. Timo found this himself. The assistant then locked the list as soon as an incoming connection arrives and added the hint "Only one of you taps" — the fix was interface, not protocol. |
| 15 | 14.2 | We asked for the joint workout to be checked at upload: same place, similar distance. | The assistant built the location comparison. | We dropped it ourselves after testing: two people finish a run and file it at different times from different places, and the check punished exactly the normal case. What remained is the part that matters — whoever stops the clock stops it for everyone. Recorded here as a feature we removed after seeing it work. |

| 16 | 16.1-16.4 | The joint training over Bluetooth had been unreliable for two weeks. The assistant had blamed distance, radio interference and a stale GATT cache in turn. | Each diagnosis sounded plausible. | All three were wrong, and it only got there by reading the logcat of both devices at once and `dumpsys bluetooth_manager`: four leaked interfaces, and a server callback that mistook the device's own link for someone else's call. Three separate defects in one feature, none of them visible from the code alone. |
| 17 | 16.8 | The assistant reported that the challenged crew could not see a pending battle at all. | "The row does not arrive - the query must be wrong." | Wrong, and it caught itself: a temporary log line showed all three rows arriving correctly. The card was simply below the fold and it had not scrolled. A bug report that would have cost an afternoon of looking in the wrong place. |
| 18 | 16.5 | We redesigned the pairing ourselves: both pick each other instead of one tapping. | The assistant had built the "only one taps" rule and defended it. | Our version is better and simpler. Because both choose, the app can decide who calls by comparing the identifiers - the same answer on both devices, no agreement needed, no race. The rule the assistant had added was a workaround for a bug it had not found yet. |
| 19 | 17.1 | **AI-caused, and the worst stretch of the project.** Over several hours the assistant tried unbonding, `refresh()`, an MTU deadline, dropping MTU, a fallback caller and a persistent GATT server on the Bluetooth problem. Some of it made things worse: unbonding via the rotating advertised address broke address resolution and every attempt ended in status 133; the fallback caller brought back the crossing calls that had been fixed days earlier. | Each step came with a confident explanation. | Timo stopped it — "I have the feeling we are going in circles" — and that was right. What ended it was not another idea but six temporary log lines through the connection path. They showed in one run what hours of reasoning had not: bonded, connected in 1.3 seconds, `discoverServices()` started, and then nothing at all. The lesson is the same as incident 13, learned again the hard way: measure the step that fails instead of replacing the steps around it. |
| 20 | 17.1 | **AI-caused.** The assistant's own test script restarted the app before every run — and so never reproduced the failure Timo was reporting, which happens when you finish a workout and go again *without* closing the app. | Four passes in a row, reported as proof that the problem was fixed. | It was not proof of anything; Timo tested by hand and it failed immediately. Several other "failures" pointed the other way and were also the harness: a fixed tap coordinate for the sport list that only fits the phone, a `Cancel` button below the fold, and screenshots taken before a screen had finished loading. A test that does not do what a person does can only mislead. |
| 21 | 17.1 | Timo had said days earlier what the fix turned out to be: "every time the pairing prompt, and unpair after finishing, so every attempt is the same." | The assistant had tried it once, hit status 133, and set the idea aside as disproven. | It was the right idea with one detail wrong — after unbonding it kept using the old advertised address, which is only resolvable while the bond exists. Making the scan report every sighting instead of only the first was all that was missing. Recorded because the user's instinct about the *behaviour* beat the assistant's reading of the *protocol*, and because a failed experiment was treated as a closed question when the fault was in the experiment. |

**Working style, and how it changed.** In the first weeks AI was used for
isolated pieces of code that we pasted in. From August onwards the assistant
worked in the repository directly and could build, install, and drive the app on
the emulator. That made it much faster, and it made verification the bottleneck
rather than typing: the useful question stopped being "does it compile" and
became "what did you actually observe". Incidents 3, 4, 6 and 8 are all cases
where the first answer sounded plausible and was wrong, and asking for the
evidence changed the outcome — incident 13 is the same pattern in Bluetooth,
where the logcat of both devices settled in a minute what an hour of guessing
had not.

**[FILL IN]** — add any incidents from your own or Timo's AI use (ChatGPT for
mockups, etc.) that are not visible in this repository.

---

## 6. Android Features Report

### 6.1 Features Used

| Feature | Where it's used (screen/class) | Why this feature was needed |
| --- | --- | --- |
| **Activities & explicit navigation** | 20 Activities, e.g. `MainHubActivity`, `WorkoutTrackingActivity`, `LeaderboardActivity`, `MemberProfileActivity`, `WorkoutDetailActivity` | Each task is a screen with its own lifecycle. Navigation is by explicit `Intent`, with factory methods (`MemberProfileActivity.intent(...)`) so extras keys exist in exactly one place. |
| **Activity lifecycle** | `MainHubActivity.onResume` reloads the crew; `onStop` releases the `MediaPlayer`; `WorkoutDetailActivity.onStop` | Data must be current when a screen is returned to, and a media player must not keep playing or holding resources after the screen is gone. |
| **Configuration changes** | `WorkoutTrackingActivity.onSaveInstanceState`, `ProfileActivity` (`savedInstanceState == null` guard), `BirthDatePicker.reattach` | Rotating the device recreates the Activity. Without this a taken photo, a recorded voice note, or unsaved profile edits were lost. |
| **Runtime permissions** | `AppPermission` (the catalogue), `WorkoutTrackingActivity` (camera, microphone, location), `GalleryPicker` (photos), `SettingsActivity` (overview and management), `MainHubActivity` (Health Connect) | Android requires these to be requested at runtime, and every one of them can be refused. The permissions the app knows about are listed once in an enum; the settings screen builds its rows from it, so a new permission cannot be forgotten there. |
| **Camera via `ACTION_IMAGE_CAPTURE`** | `WorkoutTrackingActivity` | A photo is the proof that a workout happened — the core idea of the app. We hand off to the system camera rather than embedding a viewfinder. |
| **`FileProvider`** | `com.example.mobilese.fileprovider`, `res/xml/file_paths.xml` | Since Android 7 a `file://` URI may not be passed to another app. The camera writes into our app directory through a content URI. |
| **`MediaRecorder` / `MediaPlayer`** | `WorkoutTrackingActivity` (record), `VoicePlayer` (play, used by home screen and workout detail) | Voice notes. Playback uses `prepareAsync()` because the notes are URLs and a synchronous `prepare()` on the main thread risks an ANR. |
| **`LocationManager` + `Geocoder`** | `WorkoutTrackingActivity`, `LocationNames` | The training location. `Geocoder` turns coordinates into a readable place name; the API 33 listener variant is used where available, the deprecated blocking call below it. |
| **Health Connect** | `HealthSteps`, `MainHubActivity`, `HealthPrivacyActivity` | Today's step count, read from the platform health database rather than counted by us. |
| **Wear OS as a second module** | `wear/` — `SportChoiceActivity`, `WorkoutActivity`, `HeartRateReader`, `PhoneLink`, `WatchProtocol` | The watch is its own device with its own APK, not a second screen. It knows what only it can know — sport, duration, heart rate — and hands that over to the phone, where the camera, the keyboard and the crew are. Both modules must carry the same `applicationId`, or the system does not treat them as one app. |
| **Wearable Data Layer** | `PhoneLink` on the watch, `WatchWorkoutService` on the phone | The workout travels as a *data item*, not as a message. A message only arrives while the phone is in range — and running without the phone is exactly what a watch is for. The item waits on the watch until the two meet again. |
| **`SensorManager` on the watch** | `HeartRateReader`, `StepCounter` | Heart rate and steps come from the watch's own sensors while the workout runs. Both go quiet during a pause — a pulse measured while standing would drag the average down, and the walk to the drinks machine is not training. |
| **Foreground service** (`health` type) | `WorkoutService` on the watch | A training must outlive the screen it was started from. The activity is only the display; the service holds the workout, and Android may restart it, so the state is written to the preferences on every change and every thirty seconds. Declared as `health` because that is what it is — the type decides which permissions the system demands. |
| **Notifications and channels** | `Notifications` and `CrewFitMessagingService` on the phone, `WatchNotifications` on the watch | Three separate channels: news from the crew, a shift in the ranking, and a challenge from another crew. Separate because they can be silenced separately — one channel would force an all-or-nothing choice. The phone's messages arrive through Firebase Cloud Messaging, sent by Supabase, not by the app. |
| **Bluetooth Low Energy** | `CoLocation`, `PartnerLink`, `TrainingPartnerActivity`, `TrainingProtocol` | Proof that two people were actually in the same place. Advertising (`BluetoothLeAdvertiser`), scanning (`BluetoothLeScanner`), system bonding (`createBond`) and a GATT server plus client for the exchange — all from the platform API, no library. The permission model splits at API 31: `BLUETOOTH`/`BLUETOOTH_ADMIN` with `ACCESS_FINE_LOCATION` below, `BLUETOOTH_SCAN`/`_ADVERTISE`/`_CONNECT` from there on. |
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

#### (e) Bluetooth Low Energy without a library

Double points for training together are worth having only if they cannot simply
be claimed. So the phones have to establish for themselves that they are in the
same place, and that is the platform API and nothing else.

Each phone does two things at once: it advertises an 8-byte identifier under a
16-bit service UUID, and it scans for the same UUID. Both numbers are as small
as they are because an advertising packet holds 31 bytes in total — a full
128-bit UUID would take 16 of them, and the account identifier is a UUID that
does not fit at all. `CoLocation.payloadFor` therefore sends only its upper
half, which is enough to pick one of a handful of crew members out and is
matched against the crew list rather than trusted on its own.

Discovery alone would have been "the other phone is nearby". The devices
therefore bond at system level (`createBond()`) and then open a GATT
connection, over which each writes its identifier to the other. That is what
makes the pairing real: it appears in the Android Bluetooth settings, and the
system asks on both devices before anything is shared.

**The finding that cost the most, and taught the most.** For two weeks the
feature worked right after a fresh pairing and not afterwards. Every plausible
explanation — distance, interference, a stale service cache — turned out to be
wrong, and what settled it was six temporary log lines through the connection
path:

```
beginWith bond=12          <- already bonded
connected status=0         <- link up after 1.3 seconds
lookForServices started=true
   ... six seconds, no answer
   ... six seconds, no answer
The services stayed silent
```

With a bonded peer, `discoverServices()` returns `true` and **never calls
back**. Without a bond, the same lookup answers in about a second. The
documented remedy, the hidden `refresh()`, does nothing here: the second lookup
came back unchanged after eleven milliseconds, straight from the cache.

So the app drops an existing bond before connecting and pairs freshly each
time. One detail makes or breaks that: the address a device advertises is a
*random* one, and Android only resolves it to the real identity while the bond
exists. An earlier attempt kept using that address after unbonding and every
connect ended in status 133. The scan therefore reports every sighting instead
of only the first, so the next attempt has a current address.

The result is also the better behaviour: before every shared workout the system
asks on both devices, both confirm, and every attempt is identical to the first.
**Fourteen consecutive runs** — connect, sport, start, and stop from the other
side — in both directions, with the app kept open and with it closed.

Three more things about Android's GATT stack are not obvious. First, it
processes exactly one operation at a time, and an unanswered `requestMtu()`
blocks the queue: the link stands, writes report success, and nothing arrives.
We dropped the request entirely; the default twenty bytes carry sport, start and
stop. Second, a scan in `SCAN_MODE_LOW_LATENCY` listens almost continuously and
starves a connection being built alongside it — whoever tapped first used to
call into a device still scanning at full power, and *that* order failed
reproducibly while the reverse worked. Both sides now stop scanning and keep
advertising. Third, a connection only counts once the write has been
acknowledged in `onCharacteristicWrite`, not when `onConnectionStateChange`
reports CONNECTED; confirming earlier meant the app believed in a partner it
could not yet talk to.

Two smaller traps are worth recording because both cost real time. Every
`connectGatt()` claims a registration in the GATT stack and Android hands out a
fixed number per process — without `close()` on failure they leak, and once they
are gone every further attempt ends in status 133; one failed evening left
thirteen of them side by side. And `ACTION_BOND_STATE_CHANGED` carries the
device's *real* address while the request went to the advertised random one, so
a strict comparison discards the very message that says the pairing succeeded.

For a group, connections are opened one after another rather than all at once,
and whoever did the connecting broadcasts the roster — otherwise the third
person would know the first but not the second. `TrainingProtocol` holds the
whole message format (sport, start, stop, roster) as plain byte arrays with no
Android imports, so it is covered by unit tests.

#### (f) Degrading gracefully when the backend schema is behind

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
