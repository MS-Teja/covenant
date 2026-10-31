# Covenant build spec

Oct 23, 2026 · @Teja · revised after review the same day, and again on Oct 31 after the product rethink

Covenant is an open-source digital-detox launcher for Android. It makes your phone calm by default: you split the day into blocks, each with its own apps, and any rule that gives an app more time waits 72 hours. It's built for the people described in [product.md](product.md). Everything below is decided; v0.1 is the daily-driver build for the Moto Edge 50 Fusion. This file is the source of truth: change it here before any code depends on it. The original is in `archive/2026-10-23-build-spec.md`.

## Decisions

| Area | Decision |
| --- | --- |
| Name | Covenant. Package `io.github.msteja.covenant`, so no domain is needed |
| Target users | Reclaimers: adults aged 18–30 in India who lose 3 hours or more a day to feeds but need UPI, OTPs and maps, so a dumbphone won't do. Not developers. See [product.md](product.md) |
| License | GPLv3 |
| Stack | Kotlin and Jetpack Compose, written from scratch. Study Kvaesitso and Olauncher, but don't fork them. Built with the command-line SDK and Gradle; Android Studio is optional |
| Android versions | Minimum Android 11 (API 30). Target the latest stable SDK. Grayscale through Android Modes needs Android 15 |
| Devices | Develop on the emulator with a Pixel image. The Moto Edge 50 Fusion (Android 16) is the daily phone and the only real test device |
| Enforcement | Accessibility service and notification access, granted in Settings during setup. Enforcement sits behind an interface so a device-owner backend can plug in later |
| Setup | About 5 minutes on the phone, with no computer. Shizuku or ADB is optional, only for the secure-settings fallback |
| Internet | None: no INTERNET permission, analytics, crash SDKs or cloud backups |
| Cooldown | 72 hours by default for any change that gives an app more time, adjustable between 72 hours and 7 days. Stricter changes apply instantly |
| Calibration | For the first 7 days after the seal, every change applies instantly |
| Day | One day start, 4 am, for timelines, pages, passes and stats |
| Pass | One a week by default (0–2). Each opens one outside app for 10 minutes after a 1-minute wait. It replaced daily borrows and the weekly emergency pass on Oct 31 |
| Notifications | Covenant owns alert sounds: it mutes notification sound and vibration system-wide and replays them only for allowed apps |
| Calls | Only apps you choose ring through outside their block |
| Block extras | Grayscale per block. Websites follow their apps (v0.3) |
| Daily tools | Quiet (essentials only for the rest of the block or 30–90 minutes), special days (v0.3), weekly look-back (v0.3) |
| Opening a limited app | A 3-second ink bloom showing the minutes left, then Open or Let go, once per visit |
| Progress | Today's page, one swipe from home: time in time-limited apps against the week before install, and how often you let go |
| Storage | No app-level encryption. One DataStore file in v0.1; Room arrives with stats. Backups off |
| App style | Text in Ink Tide |
| Theme order | Ink Tide only. Garden waits until after Gate 1 |
| Before the seal | During development the phone runs Unguarded: rules apply, tamper protection is off and debugging is allowed. Nothing is enforced until setup is finished. You seal once v0.1 passes the test checklist |
| Debugging | After the seal, off except during the weekly maintenance window |
| Install path | The Play Store; ADB during development. Never a browser or file manager: Play Protect in India blocks those installs for accessibility apps |
| Distribution | The Play Store first, with the accessibility declaration and an in-app disclosure, then GitHub Releases, Obtainium, IzzyOnDroid and F-Droid. Register for developer verification in Play Console before the 2027 rollout |
| Exact alarms | `SCHEDULE_EXACT_ALARM`, granted during setup. Play reserves `USE_EXACT_ALARM` for alarm and calendar apps |
| Not in v1 | AI, widgets, icon packs, Do Not Disturb per block, blocking Reels and Shorts, partner codes |

## Design

Ink Tide is the v0.1 theme. Every screen, editors included, belongs to the same crafted world.

| Element | Decision |
| --- | --- |
| Feeling | Calm and monastic: quiet, slow, lots of empty space. Nothing asks for attention |
| Palette | Blue-black ink `#0A0E14` background, text `#D8DEE6`. A tide-blue accent appears only in ripples |
| Type | Literata (variable, optical sizes) for everything, with tabular figures for times. Fallback: Source Serif 4. Fonts are bundled, never downloaded |
| Home | Reads like a book page. The date is a small-caps running head, the current block is the chapter title with "until 4:00" in italics, and its apps sit centred in large type. Essentials follow as a small-caps footnote under a tide-wave ornament, and "Evening at 4:00" closes the page. No counts or durations |
| Atmosphere | Each block lays a soft tide wash in its hue along the bottom of every screen, drifting slowly, so each block feels like a different phone. A faint paper grain and a lit top edge sit over the ink |
| Today's page | One swipe from home. The day is one continuous ink ribbon with hours in the margin. Hours already lived are inked and the rest of the day stays faint, with "now" where the ink has reached. Clean blocks lie still, slips ripple, and one written line sums up the day: time in feeds against your baseline, and how often you let go |
| Block tints | Hue follows the hour: dawn blue, pale midday, dusk amber, night indigo. Tone shifts too, so the page still reads in grayscale |
| Motion | Rich and fluid but unhurried: springs without overshoot, ink shaders, parallax, textured haptics. Honours Android's Remove animations setting |
| Opening pause | Ink bloom: a drop spreads from the centre over 3 seconds with "30 min" and "left in Evening", then two words appear: Open and Let go |
| Voice | Factual and terse: "Instagram opens at 4:00 pm." |
| Search | Allowed apps only. Outside apps don't exist until their block |
| Seal | Your covenant written out as a short vow ("On weekdays, Work runs 8 to 4…"), then hold for 3 seconds while the seal soaks with ink. It stamps down, slightly askew, with a deep haptic thud |
| App icon | A seal mark: two rings, one tide line and a point for now. It has a monochrome layer for themed icons, and small sizes keep both rings with heavier strokes |
| Design frames | [Ink Tide frames](https://claude.ai/artifact/VhFyCPca3LiTX9QEDzWrEy) (private canvas) |
| Controls | Words only. No icons anywhere in Covenant's own UI |
| Garden (after Gate 1) | Plants drawn in code, different every day. Clean blocks branch and bloom; slips droop until morning |

## Vocabulary

Use these names in the code and on screen so the two never drift apart.

| Term | Meaning |
| --- | --- |
| Day | Starts at 4 am. The date at 4 am decides whether it's a weekday or a weekend day |
| Block | A named time range with its own apps, time limits and grayscale setting |
| Timeline | All 24 hours of a day split into blocks. There is one for weekdays and one for weekends |
| Special day | A date that uses another timeline, such as a holiday or an exam day |
| Essentials | Apps available in every block |
| System allowlist | Parts of Android that Covenant never blocks and whose notifications it never holds: phone and in-call screen, the current keyboard, system UI, Settings (except guarded pages), permission dialogs, the package installer, Google Play services dialogs, the file and photo pickers, Contacts, emergency SOS, dialer and alerts, the clock app, the system launcher's recents screen (not its home screen) and Covenant itself. It is an explicit package list, never "any system app": YouTube and Chrome are system apps too |
| Outside app | Any app not in the current block. It is hidden and blocked |
| Visit | An app's stay on screen. Leaving it for less than 30 seconds doesn't end the visit |
| Time limit | Minutes an app gets inside one block |
| Pass | Opening one outside app for 10 minutes after a 1-minute wait. One a week by default |
| Quiet | Essentials only, starting now: for the rest of the block or 30, 60 or 90 minutes. It can't be ended early |
| Let go | Choosing not to open an app at the pause. Today's page counts them |
| Baseline | Time per app in the 7 days before install, read from usage access during setup |
| Cooldown | The wait before a looser change applies. 72 hours by default |
| Pending change | A looser change waiting out its cooldown. It can be cancelled |
| Held notification | A notification from an outside app, delivered when that app's next block starts |
| Ring-through list | Apps whose calls ring even outside their block |
| Clean block | A block finished with no pass or unguarded time |
| Seal | The moment you lock in. Tamper protection and calibration week start here |
| Calibration week | The first 7 days after the seal, when every change applies instantly |
| Maintenance window | A weekly hour when Covenant updates install and debugging may be on. Nothing else loosens |
| Unguarded | A protection is off. The rules still run but are easy to bypass |
| Today's page | The day drawn as bands, one per block, one swipe from home |
| Fortress | Device-owner mode (later). Android itself suspends outside apps and locks safe mode |

Retired on Oct 31: borrow, emergency exit, weekly emergency pass, focus timer and partner code. Don't use them in new code or on screen.

## Setup

Setup takes about 5 minutes on the phone, with no computer.

1. Install Covenant from the Play Store (over ADB during development).
2. See where your time went. With usage access, Covenant shows the last 7 days per app and keeps them as your baseline. You can skip this step.
3. Sort your apps. Covenant suggests essentials (phone, messages, maps, camera, clock, your UPI apps, your authenticator and your password manager) and tempting apps (your top apps by time, plus apps Android lists as social, video, games or news). Everything else stays hidden until you place it.
4. Pick a starter plan, then adjust it: Evenings (tempting apps 7–9 pm, 45 minutes), Weekends (tempting apps on weekend afternoons) or Strict (tempting apps never). The weekend timeline starts as a copy of the weekday one.
5. Choose passes: 0, 1 or 2 a week. One is the default.
6. Grant access: accessibility (after a plain-language disclosure), notification access, notifications, exact alarms, the battery exemption and the home role.
7. Start. Covenant enforces from here, and stays Unguarded until you seal.
8. Seal when you're ready: review your covenant as a vow and hold to seal. Calibration week starts, and after 7 days the cooldown takes over.

Ring-through apps and the cooldown live in Settings. While v0.1 is in development, the phone runs Unguarded and seals once v0.1 passes the test checklist.

## A weekday

This example day runs on three blocks. Work, 8 am–4 pm: Gmail, Slack, Calendar, Chrome. Evening, 4–10 pm: Instagram (30 min), YouTube (45 min), Spotify, Chrome. Night, 10 pm–8 am: essentials only, in grayscale. Steps marked with a version need features from that release.

1. **8:00 am.** Night ends and Work starts. Grayscale turns off, and notifications held overnight for Work apps arrive as one summary per app.
2. **8:01 am.** Home shows "Thursday 23", "Work · until 4:00", the block's apps with essentials, and "Evening at 4:00". Instagram and YouTube appear nowhere.
3. **11:20 am.** An Instagram notification goes silently into Held. You can read its text there (v0.2), but you can't open Instagram.
4. **11:40 am.** A friend's instagram.com link opens in Chrome, and the page is blocked because Instagram is outside Work (v0.3).
5. **1:00 pm.** You need Uber, which isn't in Work. You use this week's pass: a one-minute wait, then 10 minutes. Work is no longer a clean block, and the pass comes back next Thursday.
6. **2:00 pm.** You start Quiet for an hour. Only essentials open until 3:00 pm, and it can't be stopped early.
7. **3:59 pm.** You're in Slack, which isn't in Evening. A one-minute warning appears, and Slack closes at 4:00 pm.
8. **4:00 pm.** Evening starts. Instagram's held notifications arrive as one summary.
9. **4:30 pm.** Opening Instagram shows a 3-second ink bloom with "30 min left", then Open or Let go. You let go. When you open it later, warnings come at 5 and 1 minutes left; at zero it closes until tomorrow evening.
10. **9:00 pm.** You raise Instagram to 60 minutes. It becomes a pending change that lands in 72 hours unless you cancel it.
11. **10:00 pm.** Night starts: essentials only, grayscale on. Spotify music already playing keeps going, but its screen won't open.
12. **Overnight.** Today's page records the day. The Work band ripples from the pass, Evening lies still, and one line reads "Feeds today: 35 min (before Covenant: 3 h 10 min). Let go 4 times."

## Opening an app

Covenant runs these checks whenever a different app comes to the front, so a notification tap or a link meets the same wall as the home screen. The first check that matches decides. Before setup is finished, everything opens (R58).

1. **System allowlist or Covenant itself:** opens.
2. **Guarded settings page:** blocked, unless Covenant opened it to repair a permission.
3. **The app is in a call or navigating:** opens. Calls and navigation are never cut.
4. **Essential:** opens, with no checks and no pause.
5. **Quiet is on:** blocked. Only essentials open until it ends.
6. **A pass is running for this app:** opens, and the pass timer keeps running.
7. **Not in the current block:** blocked screen, which offers the pass.
8. **No time limit in this block:** opens.
9. **No minutes left:** hidden until its next block.
10. **Otherwise:** the ink bloom, once per visit, then Open or Let go. Warnings come at 5 and 1 minutes left.

If Covenant isn't the home app, blocking falls back to an overlay that offers to restore it (R53).

## Rules: timeline and access

Each rule has an ID to use in code, commits and tests. Ships in is the milestone that builds it. IDs never change; new rules are added at the end.

| ID | Rule | Ships in |
| --- | --- | --- |
| R1 | The timeline covers all 24 hours of the day. Time outside every block is essentials-only. | v0.1 |
| R2 | A timeline holds at most 5 blocks. Blocks can't overlap and last at least 30 minutes. | v0.1 |
| R3 | Weekdays and weekends have separate timelines. You choose which days count as weekend. | v0.1 |
| R4 | The day starts at 4 am, and the date at 4 am decides weekday or weekend. A block still running at 4 am finishes first; the new day's blocks start after it ends. | v0.1 |
| R5 | A special day swaps in another timeline for one date. A looser one must be set 72 hours ahead; a stricter one applies instantly. | v0.3 |
| R6 | Only the current block's apps, essentials and the system allowlist are visible and can open. | v0.1 |
| R7 | Outside apps are blocked from every entry point: home, search, notifications, links, recents, share sheets, split screen, picture-in-picture and bubbles. Closing an app sends you home and blocks re-entry; a picture-in-picture window is dismissed with a gesture. | v0.1 |
| R8 | A newly installed app belongs to no block. Adding it to one follows the cooldown. | v0.1 |
| R9 | Websites follow their apps: a domain is blocked in browsers whenever its app is outside the block. Adding a domain is instant; removing one follows the cooldown. | v0.3 |
| R10 | A browser whose address bar Covenant can't read counts as an ordinary app and must be in a block to open. | v0.3 |

## Rules: time limits, block changes, notifications and calls

Limits count per app per block, and anything held arrives when its app's next block starts.

| ID | Rule | Ships in |
| --- | --- | --- |
| R11 | A time limit is minutes per app per block, counted while the app is on screen, including split screen and picture-in-picture. | v0.1 |
| R12 | The first time a time-limited app comes to the front in a visit, a 3-second ink bloom shows the minutes left, then asks Open or Let go. The bloom can't be skipped, and it holds audio focus so the app stays quiet underneath. Let go sends you home and is counted (R57). | v0.1 |
| R13 | Warnings come at 5 minutes and 1 minute left. At zero the app closes and stays hidden until its next block. | v0.1 |
| R14 | Unused minutes don't carry over. | v0.1 |
| R15 | At a block boundary, an open app that's leaving gets a 1-minute warning, then closes. Calls and navigation are never cut. | v0.1 |
| R16 | Grayscale switches on or off with the block, through an Android Modes rule on Android 15 and later, or through secure settings where that permission was granted. | v0.1 |
| R17 | Notifications from outside apps are held and delivered when that app's next block starts, one summary per app. Covenant mutes notification sound and vibration system-wide and replays them only for allowed apps, so held ones stay silent. A held notification's heads-up may flash for a split second. If Covenant stops, Android's normal sounds return. | v0.1 |
| R18 | Notifications from apps in no block are dismissed and counted in Held. | v0.1 |
| R19 | Held shows held notifications as text. It never opens an outside app. | v0.2 |
| R20 | Calls from apps on the ring-through list ring and open their call screen in every block. Calls from other outside apps are silenced and held. Their messages still wait. | v0.1 |
| R21 | Audio already playing keeps playing after its app's block ends; only the screen is blocked. | v0.1 |

## Rules: changes, cooldown and exits

Stricter changes apply now and looser ones wait out the cooldown; the one way out is rare and visible on today's page.

| ID | Rule | Ships in |
| --- | --- | --- |
| R22 | A change that gives any app less time applies instantly. One that gives any app more time waits out the cooldown. A mixed edit is split per app and per minute of the week: where the new rules are tighter they apply now, and where they are looser the old rules stay until the cooldown ends. Moving Evening from 4–10 pm to 5–11 pm closes 4–5 pm now and opens 10–11 pm in 72 hours. | v0.1 |
| R23 | Looser changes: adding apps or essentials, raising limits, growing a looser block, turning grayscale off. Also adding ring-through apps, raising passes per week, removing a website block, changing weekend days or the maintenance window. | v0.1 |
| R24 | Pending changes show a countdown and can be cancelled anytime. | v0.1 |
| R25 | Calibration week: every change, Release included, applies instantly for the first 7 days after the seal. You can end it early, and it never restarts. | v0.1 |
| R26 | The cooldown can be lengthened instantly. Shortening it waits out the current cooldown. It stays between 72 hours and 7 days. | v0.1 |
| R27 | Quiet keeps you on essentials, starting now, for the rest of the current block or for 30, 60 or 90 minutes. It can't be ended early, and you can never enter a looser block early. | v0.1 |
| R28 | Retired Oct 31: the focus timer merged into Quiet (R27). | Retired |
| R29 | Retired Oct 31: daily borrows became the weekly pass (R55). | Retired |
| R30 | Retired Oct 31: partner codes were dropped. | Retired |
| R31 | Retired Oct 31: the weekly emergency pass merged into the pass (R55). | Retired |
| R32 | Release: asking to uninstall starts a 72-hour countdown that you can cancel. After it, Covenant switches its protections off, turns grayscale off, gives back the user switcher, asks you to pick another home app and uninstalls normally. | v0.1 |

## Rules: tamper protection

Each protection closes one bypass. Safe mode stays open until Fortress (see Gaps). None of these apply before the seal.

| ID | Rule | Ships in |
| --- | --- | --- |
| R33 | Guarded settings pages stay blocked, the maintenance window included. They cover Covenant's accessibility, notification access, device admin and app info, plus default home app, developer options, date and time, battery optimization, multiple users and reset options. Covenant recognises them by view IDs and class names, never by text, so changing the phone's language can't slip past. Covenant can still open them itself to repair a permission. | v0.1 |
| R34 | If accessibility is switched off or stops responding, Covenant switches it back on within seconds through secure settings, and a watchdog re-toggles a service that's on but disconnected. Notification access can't be restored this way: if it's lost, home shows Unguarded and asks you to restore it. | v0.1 |
| R35 | Device admin blocks uninstalling Covenant. | v0.1 |
| R36 | USB debugging, wireless debugging and developer options stay off outside the maintenance window. Covenant switches them off if it finds them on. | v0.1 |
| R37 | Covenant's own updates install only during the maintenance window. Outside it, Covenant blocks the installer's update screen for itself, and Obtainium's auto-update for Covenant stays off. | v0.2 |
| R38 | Covenant switches automatic date, time and time zone back on if they're turned off. A manual change that slips through keeps you in the current or a stricter block, and pending changes pause until automatic time is back. Automatic time-zone changes while travelling are followed. | v0.1 |
| R39 | If any protection is off, home shows Unguarded and that time can't count as clean. Unguarded means any of: accessibility not connected, notification access missing, device admin inactive, Covenant not the home app, the secure-settings permission missing, debugging on outside the window, Shizuku running, another user on the phone, or Advanced Protection on. Before the seal, Covenant is always Unguarded. | v0.1 |
| R51 | The user switcher stays off and guest mode is blocked, because Covenant doesn't exist inside another user. | v0.1 |
| R52 | Shizuku must not be running after the seal. If it is, home shows Unguarded. | v0.1 |
| R53 | Covenant must stay the home app. If another launcher takes over, Covenant blocks with an overlay and offers to restore itself. | v0.1 |
| R54 | Covenant runs no foreground service, and enforcement runs in its own process, so a crash in the launcher can't take it down. | v0.1 |

## Rules: themes, home and data

Both themes draw the same record of clean blocks, and every number stays on the phone.

| ID | Rule | Ships in |
| --- | --- | --- |
| R40 | A clean block has no pass or unguarded time. Hitting a time limit doesn't count against it. | v0.1 |
| R41 | Ink Tide: dark and text-only, in blue-black ink. Today's page draws each block as a band whose hue follows the hour; clean bands stay still and slips ripple. The page turns at 4 am and sits one swipe from home. | v0.1 |
| R42 | Garden: light, with small tinted icons and plants drawn in code. The light follows your blocks, and each clean block grows one thing. A slip wilts it until morning, and nothing dies. | After Gate 1 |
| R43 | Retired Oct 31 with the focus timer. | Retired |
| R44 | Numbers are one tap from today's page: minutes per app and block, passes used and held count. Home shows none. | v0.2 |
| R45 | Weekly look-back: Ink Tide stacks the week's seven pages; Garden shows the week's growth. | v0.3 |
| R46 | Home shows the date, the current block and when it ends, its apps centred in large type, essentials in a smaller row, and when the next block starts. | v0.1 |
| R47 | Gestures: swipe up to search allowed apps, swipe sideways for today's page, swipe down for notifications, double-tap to lock, long-press for Covenant's menu. Double-tap uses accessibility's lock action, which keeps fingerprint unlock working. | v0.1 |
| R48 | v0.1 keeps everything in one DataStore file: the covenant, pending changes, today's usage and held text. Room arrives with stats in v0.2, keeping 90 days of history. There is no app-level encryption. Held text is deleted once delivered. | v0.1 |
| R49 | Export and import the timeline as a file. Importing a looser timeline follows the cooldown. | v0.3 |
| R50 | No INTERNET permission, analytics or crash SDKs, and backups are off (`allowBackup=false`, data-extraction rules exclude everything). Crash logs stay on the phone and are shared by hand. | v0.1 |

## Rules: passes, the mirror and setup

Added on Oct 31 for the people in [product.md](product.md).

| ID | Rule | Ships in |
| --- | --- | --- |
| R55 | One pass a week by default, settable from 0 to 2. A pass opens one outside app for 10 minutes after a 1-minute wait, including an app whose time ran out or an app in no block. It comes back 7 days after it was used. One runs at a time, it outlasts a block change, and Quiet beats it. Raising the number of passes waits out the cooldown. | v0.1 |
| R56 | With usage access, setup reads the last 7 days of time per app and keeps it as the baseline. Without usage access there is no baseline, and today's page shows today's time only. | v0.1 |
| R57 | Today's page shows time spent in time-limited apps today against the baseline's daily average, and how many times you let go. Home shows neither. | v0.1 |
| R58 | Nothing is enforced and no notification is held until setup is finished, so a fresh install never blocks the phone it lands on. | v0.1 |

## Gaps

Every hole found while planning has a rule, a test or a known limit. Safe mode and browsers before v0.3 are the open ones.

| Gap | How it's closed |
| --- | --- |
| Alarms must ring in every block | The clock app is on the system allowlist |
| Logins need OTPs and authenticator codes | Setup suggests SMS, your authenticator and your password manager as essentials. Android 15+ hides OTPs from Covenant, so Held may show "content hidden" for codes |
| Payments can't wait for a block | Setup suggests your UPI apps as essentials. Payment apps can flag any accessibility app installed outside Play as a risk, so Paytm, Navi and slice are tested before anything else (spike 1), and again from a Play install |
| Keyboard, permission dialogs, share sheet, pickers and sign-in | On the system allowlist, so never blocked |
| WhatsApp or Telegram calls outside their block | Ring-through list (R20) |
| Opening a blocked app's website instead | Websites follow apps (R9, R10) from v0.3. Until then any allowed browser opens every blocked site: accepted |
| In-app browsers (Instagram, Gmail, Telegram, the Google app) | Known limit: they never follow R9 |
| Hand-offs from essentials at night (Camera to Photos, a link in Messages, a bank's card check) | Open question below |
| Installing a new app as a loophole | New apps belong to no block (R8) |
| Setting the clock forward to reach Evening | Automatic time is switched back on (R38) |
| Travelling across time zones | Automatic time-zone changes are followed (R38) |
| A bad first timeline locking you in for 72 hours | Calibration week (R25) |
| Installing a patched build of Covenant over the real one | Updates install only in the maintenance window (R37). A build signed with another key can't update in place, and uninstalling is blocked. Keep the release keystore off the phone |
| USB debugging as a one-minute bypass | Debugging stays off outside the maintenance window (R36) |
| Switching off accessibility or notification access | Accessibility comes back on by itself; lost notification access shows Unguarded (R34) |
| Force stop | It switches accessibility off, but pressing Home restarts Covenant, which switches it back on. App info is guarded (R33) |
| Crashes | Watchdog (R34) and a separate enforcement process (R54) |
| "Active apps → Stop" in quick settings | Covenant runs no foreground service, so it never appears there (R54) |
| Guest user or another user | User switcher off (R51) |
| Moto Secure folder, Private space, Smart Connect | Apps keep their package names, so window checks still apply. Tested in spike 7 |
| Recents | The system launcher's recents screen is allowlisted; its home screen isn't |
| Another launcher becoming home | Overlay and restore (R53) |
| Shizuku left running | Unguarded until it stops (R52) |
| Cloud backups copying held messages | Backups off (R50) |
| Installing from a browser in India | Install from the Play Store |
| Restricted settings on sideloaded apps | Play and ADB installs are exempt |
| Play's accessibility policy | Covenant isn't an accessibility tool, so it files the accessibility declaration and shows a disclosure with consent before asking (policy clarified Oct 29, 2026) |
| Needing a rarely used utility (a ticket or airline app) | Calibration week is when utilities get placed. After it, the pass covers the rare ones |
| Play Protect live threat detection | It may warn about Covenant's behaviour. Watched during the spikes; developer verification helps |
| Reboots | Rules rebuild from stored state at boot, and alarms are re-armed |
| Battery savers killing Covenant | Covenant asks for the battery exemption; overnight behaviour is tested in spike 7 |
| A notification flood when a block starts | One summary per app (R17) |
| Safe mode | Known escape: it lets you remove device admin and uninstall, which wipes your setup. Fortress closes it in v0.3 |
| Android's Advanced Protection | It switches off accessibility for apps that aren't accessibility tools, launchers included ([source](https://www.androidauthority.com/android-17-beta-2-advanced-protection-mode-accessibility-apps-3648860/)). Leave it off; Covenant shows Unguarded if it's on |
| Work-profile apps | Placed in blocks like any other app. Fortress needs the work profile removed |

## Later

These ideas wait until v1 ships, listed by value.

- **Do Not Disturb per block.**
- **Blocking Reels, Shorts and Status** inside allowed apps.
- **Shared limits for app groups**, such as 30 minutes across Instagram and YouTube together.
- **Morning intentions.** Up to three lines you write each morning that sit on home all day.
- **Garden.** The light theme with plants drawn in code (R42).

## Screens

Nineteen screens make up v1, and 14 of them ship in v0.1.

| Screen | What it does | Ships in |
| --- | --- | --- |
| Setup wizard | The usage mirror, sorting apps, a starter plan, passes and access; the seal comes later | v0.1 |
| Home | Current block, its apps, the next block | v0.1 |
| Today's page | The day's bands and one line against the baseline, one swipe from home | v0.1 |
| Search | Swipe up to find allowed apps | v0.1 |
| Timeline list editor | Add, name and time blocks, then drop apps in | v0.1 |
| Block detail | Apps, time limits and grayscale for one block | v0.1 |
| Essentials and ring-through | The two always-on lists | v0.1 |
| Pending changes | Countdowns, with cancel | v0.1 |
| Opening pause | The 3-second ink bloom, then Open or Let go | v0.1 |
| Blocked screen | Why it's blocked, when it opens, and the pass | v0.1 |
| Block-change warning | The 1-minute warning before an app closes | v0.1 |
| Pass sheet | Pick an outside app, then wait a minute. Reached from the menu | v0.1 |
| Quiet sheet | The rest of the block, or 30, 60 or 90 minutes | v0.1 |
| Settings | Passes, cooldown, ring-through, alert sounds, protection health and the seal | v0.1 |
| 24-hour dial | Drag blocks around a clock face; replaces the list editor | v0.2 |
| Held | Held notifications as text | v0.2 |
| Stats | Minutes per app and block | v0.2 |
| Special days | Pick dates and their timeline | v0.3 |
| Weekly look-back | The week as pages | v0.3 |

## Architecture

The rule engine sits at the centre and never touches Android, so every rule can be unit-tested with a fake clock. A coordinator owns all input and output around it.

| Module | Job |
| --- | --- |
| `:core:model` | The vocabulary as types: Block, Timeline, Allowance, PendingChange and the rest. Pure Kotlin |
| `:core:rules` | The rule engine. Answers what's allowed now, the effective rules (the overlap of current rules and pending changes), the next moment to wake, and how to split a change into now and pending. Pure Kotlin with an injected clock |
| `:app` | Everything Android, in packages. `data`: the state file and the installed-app catalog. `design`: Ink Tide tokens, Literata, the tide wash, grain and ornaments. `home`: home, today's page, search, the blocked screen and the menu. `editor`: timeline, block detail, essentials and ring-through, pending changes, settings. `setup`: the wizard, permissions and the seal. `guard`: the accessibility service with its overlays, the notification listener that owns alert sounds, and the scheduler. Split into modules when it grows |

## Build order

The spikes in `spikes.md` run on the Moto before features that depend on them. Any failed spike changes this spec first.

| Milestone | Contents | Gate to the next |
| --- | --- | --- |
| v0.1 Daily driver | Launcher shell and Ink Tide, rule engine and list editor, blocking, held notifications and alert ownership, limits with the pause and Let go, cooldown and calibration, the weekly pass and Quiet, the usage mirror, five-minute setup and the seal | The test checklist passes Unguarded on the Moto and five people from the target group get through setup unaided; then seal and live 7 days with it as your only launcher |
| v0.2 Feels like Covenant | 24-hour dial, Held view, stats, Play internal testing, updates in the maintenance window | The checklist passes, and testers' feed time falls against their baselines |
| v0.3 Life-proof | Websites follow apps, special days, weekly look-back, export and import, Fortress, Advanced Protection check | The checklist passes on a second, borrowed phone |
| v1.0 Public | The Play Store release, reproducible builds, GitHub Releases and Obtainium, IzzyOnDroid then F-Droid | — |

Develop on the emulator, then test on the Moto over ADB (in the maintenance window once sealed). Register the package and signing key for developer verification in Play Console well before it reaches India in 2027 ([source](https://android-developers.googleblog.com/2026/03/android-developer-verification-rolling-out-to-all-developers.html)).

## Test checklist

Run these on the Moto before calling a milestone done.

Entry points, with Instagram outside the current block:

- [ ] Home and search don't show it
- [ ] Tapping its notification
- [ ] A link from WhatsApp or Gmail
- [ ] Recents
- [ ] The Play Store's Open button
- [ ] Sharing into it from another app
- [ ] Split screen and picture-in-picture (YouTube going to PiP when sent home)
- [ ] Chat bubbles
- [ ] Asking Google Assistant to open it
- [ ] Its copy inside Moto Secure folder or Private space
- [ ] Smart Connect on a PC or TV
- [ ] Its website in Chrome (v0.3)

Tampering:

- [ ] Turning off accessibility or notification access
- [ ] Force stop and uninstall from app info
- [ ] Changing the default home app
- [ ] Changing date, time and time zone by hand
- [ ] Turning on developer options and wireless debugging
- [ ] Adding a guest user or switching users
- [ ] Reset app preferences
- [ ] Reaching a guarded page through Settings search, and with the phone's language changed
- [ ] Shizuku left running after the seal
- [ ] Crashing the launcher process, then the enforcement process
- [ ] Rebooting mid-block
- [ ] Safe mode, to record exactly what it allows

Setup and exits:

- [ ] A fresh install enforces nothing until setup finishes
- [ ] Setup finishes in about 5 minutes without a computer
- [ ] The usage mirror matches Digital Wellbeing's numbers
- [ ] The pass: one a week, a one-minute wait, ten minutes, and it outlasts a block change
- [ ] Let go at the pause sends you home and shows on today's page
- [ ] Quiet for 30 minutes can't be ended early

Timing:

- [ ] A block change while inside a leaving app
- [ ] A block that crosses 4 am
- [ ] Limit warnings at 5 and 1 minutes
- [ ] The pause firing once per visit, not on every return within 30 seconds
- [ ] Held notifications arriving in the right block, one summary per app
- [ ] Held notifications staying silent, while allowed apps still alert, respecting silent mode and Do Not Disturb
- [ ] A call from a ring-through app outside its block, and from an app that isn't on the list
- [ ] The phone switched off across a block boundary
- [ ] A pending change surviving a reboot
- [ ] Paytm, Navi and slice making a payment with every service on
- [ ] Battery drain over 24 hours with every service on

## Open questions

None of these block v0.1.

- Which hand-offs from essentials join the allowlist: Photos from Camera, a bank's card check in a browser, the password manager's unlock screen?
- Which starter plans should setup offer beyond Evenings, Weekends and Strict?
- The name sits close to "Covenant Eyes" (now Victory Shield), a partner-monitoring app in the same space.

## Sources

- [Advanced Protection in Android 17 and accessibility services](https://securityaffairs.com/189497/security/advanced-protection-mode-in-android-17-prevents-apps-from-misusing-accessibility-services.html)
- [Android 17 Advanced Protection and launchers](https://www.androidauthority.com/android-17-beta-2-advanced-protection-mode-accessibility-apps-3648860/)
- [Shizuku setup guide](https://shizuku.rikka.app/guide/setup/)
- [Android developer verification rollout](https://android-developers.googleblog.com/2026/03/android-developer-verification-rolling-out-to-all-developers.html)
- [Play Protect's sideloading pilot in India](https://techcrunch.com/2024/10/03/google-pilots-blocking-some-sideloaded-apps-in-india)
- [Restricted settings in Android 15](https://androidauthority.com/android-15-restricted-settings-sideloading-3481098)
- [Play Integrity app access risk](https://developer.android.com/google/play/integrity/app-access-risk)
- [Android 15 hides OTPs from notification listeners](https://www.androidauthority.com/android-15-sensitive-notifications-3416414)
- [Secure-setting route for notification listeners deprecated (AOSP CTS)](https://android.googlesource.com/platform/cts/+/32542b2%5E%21/)
- [Stopping apps from "Active apps"](https://developer.android.com/about/versions/13/changes/fgs-manager)
- [F-Droid inclusion policy](https://f-droid.org/en/docs/Inclusion_Policy/)
