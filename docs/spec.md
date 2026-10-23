# Covenant build spec

Oct 23, 2026 · @Teja · revised after review the same day

Covenant is an open-source Android launcher that turns your phone into a different phone at each time of day. You split the day into blocks, each with its own apps, and any rule that gives an app more time waits 72 hours. Everything below is decided; v0.1 is the daily-driver build for the Moto Edge 50 Fusion. This file is the source of truth: change it here before any code depends on it. The original is in `archive/2026-10-23-build-spec.md`.

## Decisions

| Area | Decision |
| --- | --- |
| Name | Covenant. Package `io.github.msteja.covenant`, so no domain is needed |
| License | GPLv3 |
| Stack | Kotlin and Jetpack Compose, written from scratch. Study Kvaesitso and Olauncher, but don't fork them. Built with the command-line SDK and Gradle; Android Studio is optional |
| Android versions | Minimum Android 11 (API 30), which wireless-debugging pairing needs. Target the latest stable SDK |
| Devices | Develop on the emulator with a Pixel image. The Moto Edge 50 Fusion (Android 16) is the daily phone and the only real test device |
| Enforcement | Accessibility service, notification access and device admin, plus the secure-settings permission granted once through Shizuku. Enforcement sits behind an interface so the device-owner backend (Fortress) plugs in at v0.3 |
| Setup grants | One Shizuku step grants every permission. Shizuku stays the method for good; there is no built-in pairing, because pairing needs the INTERNET permission |
| Internet | None: no INTERNET permission, analytics, crash SDKs or cloud backups |
| Cooldown | 72 hours by default for any change that gives an app more time. Stricter changes apply instantly |
| Calibration | For the first 7 days after the seal, every change applies instantly |
| Day | One day start, 4 am, for timelines, pages, borrows and stats |
| Borrows | 2 a day, 10 minutes each, after a 1-minute wait |
| Emergency exits | Weekly emergency pass (v0.1) and partner code (v0.2), each optional. Either beats focus and quiet now |
| Notifications | Covenant owns alert sounds: it mutes notification sound and vibration system-wide and replays them only for allowed apps |
| Calls | Only apps you choose ring through outside their block |
| Block extras | Grayscale per block. Websites follow their apps (v0.3) |
| Daily tools | Special days, reading held messages, weekly look-back, focus timer |
| Opening a limited app | A 3-second ink bloom showing the minutes left, once per visit |
| Progress | Today's page shows it, one swipe from home. Numbers are one tap from there |
| Storage | No app-level encryption. DataStore for settings, Room for history and held text. Backups off |
| App style | Text in Ink Tide. Small tinted icons in Garden |
| Theme order | Ink Tide ships in v0.1, Garden in v0.2 |
| Before the seal | During development the phone runs Unguarded: rules apply, tamper protection is off and debugging is allowed. You seal once v0.1 passes the test checklist |
| Debugging | After the seal, off except during the weekly maintenance window |
| Install path | Obtainium or ADB. Never a browser or file manager: Play Protect in India blocks those installs for accessibility apps |
| Distribution | GitHub Releases and Obtainium first, then IzzyOnDroid and F-Droid. Register the package for developer verification in Play Console before the 2027 rollout |
| Not in v1 | AI, widgets, icon packs, Do Not Disturb per block, blocking Reels and Shorts |

## Design

Ink Tide is the v0.1 theme. Every screen, editors included, belongs to the same crafted world.

| Element | Decision |
| --- | --- |
| Feeling | Calm and monastic: quiet, slow, lots of empty space. Nothing asks for attention |
| Palette | Blue-black ink `#0A0E14` background, text `#D8DEE6`. A tide-blue accent appears only in ripples |
| Type | Literata (variable, optical sizes) for everything, with tabular figures for times. Fallback: Source Serif 4. Fonts are bundled, never downloaded |
| Home | Centred, large app names. Top: "Thursday 23" over "Work · until 4:00". Essentials in a smaller row. Bottom: "Evening at 4:00". No counts or durations |
| Today's page | One swipe from home. Each block is a band; clean bands lie still and slips ripple |
| Block tints | Hue follows the hour: dawn blue, pale midday, dusk amber, night indigo. Tone shifts too, so the page still reads in grayscale |
| Motion | Rich and fluid but unhurried: springs without overshoot, ink shaders, parallax, textured haptics. Honours Android's Remove animations setting |
| Opening pause | Ink bloom: a drop spreads from the centre over 3 seconds with "30 min" and "left in Evening", then the page lifts off the app |
| Voice | Factual and terse: "Instagram opens at 4:00 pm." |
| Search | Allowed apps only. Outside apps don't exist until their block |
| Seal | A calm summary of your covenant, then hold for 3 seconds. An ink seal stamps down with a deep haptic thud |
| App icon | A seal mark, with a monochrome layer for themed icons |
| Controls | Words only. No icons anywhere in Covenant's own UI |
| Garden (v0.2) | Plants drawn in code, different every day. Clean blocks branch and bloom; slips droop until morning |

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
| Borrow | Bringing one outside app into the current block for 10 minutes |
| Emergency exit | The weekly emergency pass or a partner code. Either opens everything for 30 minutes |
| Weekly emergency pass | Once a week, everything opens for 30 minutes after a 10-minute wait |
| Cooldown | The wait before a looser change applies. 72 hours by default |
| Pending change | A looser change waiting out its cooldown. It can be cancelled |
| Held notification | A notification from an outside app, delivered when that app's next block starts |
| Ring-through list | Apps whose calls ring even outside their block |
| Quiet now | Ending the current block early and staying on essentials until the next one |
| Focus timer | Essentials only, for 25–90 minutes |
| Clean block | A block finished with no borrow, emergency exit or unguarded time |
| Seal | The moment you lock in. Tamper protection and calibration week start here |
| Calibration week | The first 7 days after the seal, when every change applies instantly |
| Maintenance window | A weekly hour when Covenant updates install and debugging may be on. Nothing else loosens |
| Unguarded | A protection is off. The rules still run but are easy to bypass |
| Today's page | The day drawn as bands, one per block, one swipe from home |
| Fortress | Device-owner mode (v0.3). Android itself suspends outside apps and locks safe mode |

## Setup

Setup takes 20–30 minutes on the phone, with no computer and no factory reset. Wireless debugging needs a Wi-Fi network.

1. Install Covenant through Obtainium or ADB and set it as the home app.
2. Pick a theme: Ink Tide or Garden. You can switch anytime. Until v0.2 this step is skipped.
3. Pick essentials. Covenant suggests phone, messages, maps, camera, clock, your UPI apps, your authenticator and your password manager.
4. Build the weekday timeline. Add up to 5 blocks, name them and set their times, then drop in apps, time limits and grayscale. A starter offers Work, Evening and Night.
5. Choose your weekend days and build the weekend timeline, which starts as a copy of the weekday one.
6. Choose which apps can ring through with calls.
7. Choose emergency exits: the weekly emergency pass, a partner code (v0.2), both or none. Partner codes are shown once, to print.
8. Set the maintenance window. The default is Sunday, 10–11 am.
9. Grant everything in one step. Install Shizuku, turn on wireless debugging and start Shizuku. Covenant then grants itself accessibility, notification access, device admin, the battery-optimization exemption, notifications, the home role and the secure-settings permission.
10. Seal. Review the summary and hold to seal. Covenant turns debugging off and confirms Shizuku has stopped. You can uninstall Shizuku afterwards.
11. Calibration week starts. After 7 days, the cooldown takes over.

While v0.1 is in development, the phone stops after step 9 and runs Unguarded. Step 10 happens once v0.1 passes the test checklist.

## A weekday

This example day runs on three blocks. Work, 8 am–4 pm: Gmail, Slack, Calendar, Chrome. Evening, 4–10 pm: Instagram (30 min), YouTube (45 min), Spotify, Chrome. Night, 10 pm–8 am: essentials only, in grayscale. Steps marked with a version need features from that release.

1. **8:00 am.** Night ends and Work starts. Grayscale turns off, and notifications held overnight for Work apps arrive as one summary per app.
2. **8:01 am.** Home shows "Thursday 23", "Work · until 4:00", the block's apps with essentials, and "Evening at 4:00". Instagram and YouTube appear nowhere.
3. **11:20 am.** An Instagram notification goes silently into Held. You can read its text there (v0.2), but you can't open Instagram.
4. **11:40 am.** A friend's instagram.com link opens in Chrome, and the page is blocked because Instagram is outside Work (v0.3).
5. **1:00 pm.** You need Uber, which isn't in Work. You borrow it: a one-minute wait, then 10 minutes. Work is no longer a clean block.
6. **2:00 pm.** You start a 50-minute focus timer (v0.2). Only essentials open until 2:50 pm, and it can't be stopped early.
7. **3:59 pm.** You're in Slack, which isn't in Evening. A one-minute warning appears, and Slack closes at 4:00 pm.
8. **4:00 pm.** Evening starts. Instagram's held notifications arrive as one summary.
9. **4:30 pm.** Opening Instagram shows a 3-second ink bloom with "30 min left". Warnings come at 5 and 1 minutes left; at zero it closes until tomorrow evening.
10. **9:00 pm.** You raise Instagram to 60 minutes. It becomes a pending change that lands in 72 hours unless you cancel it.
11. **10:00 pm.** Night starts: essentials only, grayscale on. Spotify music already playing keeps going, but its screen won't open.
12. **Overnight.** Today's page records the day. The Work band ripples from the borrow, Evening lies still, and one tap shows minutes per app (v0.2).

## Opening an app

Covenant runs these checks whenever a different app comes to the front, so a notification tap or a link meets the same wall as the home screen. The first check that matches decides.

1. **System allowlist or Covenant itself:** opens.
2. **Guarded settings page:** blocked, unless Covenant opened it to repair a permission.
3. **The app is in a call or navigating:** opens. Calls and navigation are never cut.
4. **Essential:** opens, with no checks and no pause.
5. **An emergency exit is running:** opens.
6. **Focus timer or quiet now is on:** blocked. Only essentials open until it ends.
7. **A borrow is running for this app:** opens, and the borrow timer keeps running.
8. **Not in the current block:** blocked screen, which offers a borrow or an emergency exit.
9. **No time limit in this block:** opens.
10. **No minutes left:** hidden until its next block.
11. **Otherwise:** the ink bloom, once per visit, then it opens. Warnings come at 5 and 1 minutes left.

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
| R12 | The first time a time-limited app comes to the front in a visit, a 3-second ink bloom shows the minutes left. It can't be skipped, and it holds audio focus so the app stays quiet underneath. | v0.1 |
| R13 | Warnings come at 5 minutes and 1 minute left. At zero the app closes and stays hidden until its next block. | v0.1 |
| R14 | Unused minutes don't carry over. | v0.1 |
| R15 | At a block boundary, an open app that's leaving gets a 1-minute warning, then closes. Calls and navigation are never cut. | v0.1 |
| R16 | Grayscale switches on or off with the block. | v0.1 |
| R17 | Notifications from outside apps are held and delivered when that app's next block starts, one summary per app. Covenant mutes notification sound and vibration system-wide and replays them only for allowed apps, so held ones stay silent. A held notification's heads-up may flash for a split second. If Covenant stops, Android's normal sounds return. | v0.1 |
| R18 | Notifications from apps in no block are dismissed and counted in Held. | v0.1 |
| R19 | Held shows held notifications as text. It never opens an outside app. | v0.2 |
| R20 | Calls from apps on the ring-through list ring and open their call screen in every block. Calls from other outside apps are silenced and held. Their messages still wait. | v0.1 |
| R21 | Audio already playing keeps playing after its app's block ends; only the screen is blocked. | v0.1 |

## Rules: changes, cooldown and exits

Stricter changes apply now and looser ones wait out the cooldown; every way out is limited and visible on today's page.

| ID | Rule | Ships in |
| --- | --- | --- |
| R22 | A change that gives any app less time applies instantly. One that gives any app more time waits out the cooldown. A mixed edit is split per app and per minute of the week: where the new rules are tighter they apply now, and where they are looser the old rules stay until the cooldown ends. Moving Evening from 4–10 pm to 5–11 pm closes 4–5 pm now and opens 10–11 pm in 72 hours. | v0.1 |
| R23 | Looser changes: adding apps or essentials, raising limits, growing a looser block, turning grayscale off. Also adding ring-through apps, turning on an exit, removing a website block, changing weekend days or the maintenance window. | v0.1 |
| R24 | Pending changes show a countdown and can be cancelled anytime. | v0.1 |
| R25 | Calibration week: every change, Release included, applies instantly for the first 7 days after the seal. You can end it early, and it never restarts. | v0.1 |
| R26 | The cooldown can be lengthened instantly. Shortening it waits out the current cooldown. | v0.1 |
| R27 | Quiet now ends the current block early and keeps you on essentials until the next one. You can never enter a looser block early. | v0.1 |
| R28 | Focus timer: essentials only, for 25–90 minutes. Only an emergency exit can end it early. | v0.2 |
| R29 | Borrows: 2 a day, 10 minutes each, after a 1-minute wait. One runs at a time and outlasts a block change. The count resets at 4 am. | v0.1 |
| R30 | Partner code, if turned on: a friend holds 10 printed one-time codes. Each opens everything for 30 minutes; regenerating voids the old set. | v0.2 |
| R31 | Weekly emergency pass, if turned on: once a week, everything opens for 30 minutes after a 10-minute wait. It beats focus and quiet now. | v0.1 |
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
| R40 | A clean block has no borrow, emergency exit or unguarded time. Hitting a time limit doesn't count against it. | v0.1 |
| R41 | Ink Tide: dark and text-only, in blue-black ink. Today's page draws each block as a band whose hue follows the hour; clean bands stay still and slips ripple. The page turns at 4 am and sits one swipe from home. | v0.1 |
| R42 | Garden: light, with small tinted icons and plants drawn in code. The light follows your blocks, and each clean block grows one thing. A slip wilts it until morning, and nothing dies. | v0.2 |
| R43 | Focus timer sessions leave a mark: a dense band in Ink Tide, a stepping stone in Garden. | v0.2 |
| R44 | Numbers are one tap from today's page: minutes per app and block, borrows used and held count. Home shows none. | v0.2 |
| R45 | Weekly look-back: Ink Tide stacks the week's seven pages; Garden shows the week's growth. | v0.3 |
| R46 | Home shows the date, the current block and when it ends, its apps centred in large type, essentials in a smaller row, and when the next block starts. | v0.1 |
| R47 | Gestures: swipe up to search allowed apps, swipe sideways for today's page, swipe down for notifications, double-tap to lock, long-press for Covenant's menu. Double-tap uses accessibility's lock action, which keeps fingerprint unlock working. | v0.1 |
| R48 | Settings live in DataStore; history and held text live in Room. There is no app-level encryption. Held text is deleted once delivered. History keeps 90 days. | v0.1 |
| R49 | Export and import the timeline as a file. Importing a looser timeline follows the cooldown. | v0.3 |
| R50 | No INTERNET permission, analytics or crash SDKs, and backups are off (`allowBackup=false`, data-extraction rules exclude everything). Crash logs stay on the phone and are shared by hand. | v0.1 |

## Gaps

Every hole found while planning has a rule, a test or a known limit. Safe mode and browsers before v0.3 are the open ones.

| Gap | How it's closed |
| --- | --- |
| Alarms must ring in every block | The clock app is on the system allowlist |
| Logins need OTPs and authenticator codes | Setup suggests SMS, your authenticator and your password manager as essentials. Android 15+ hides OTPs from Covenant, so Held may show "content hidden" for codes |
| Payments can't wait for a block | Setup suggests your UPI apps as essentials. Payment apps can flag any accessibility app installed outside Play as a risk, so Paytm, Navi and slice are tested before anything else (spike 1) |
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
| Installing from a browser in India | Install through Obtainium or ADB |
| Restricted settings on sideloaded apps | Obtainium installs are exempt, and the Shizuku step grants everything anyway |
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

## Screens

Twenty screens make up v1, and 14 of them ship in v0.1.

| Screen | What it does | Ships in |
| --- | --- | --- |
| Setup wizard | The 11 setup steps, ending in the seal | v0.1 |
| Home | Current block, its apps, the next block | v0.1 |
| Today's page | The day's bands, one swipe from home | v0.1 |
| Search | Swipe up to find allowed apps | v0.1 |
| Timeline list editor | Add, name and time blocks, then drop apps in | v0.1 |
| Block detail | Apps, time limits and grayscale for one block | v0.1 |
| Essentials and ring-through | The two always-on lists | v0.1 |
| Pending changes | Countdowns, with cancel | v0.1 |
| Opening pause | The 3-second ink bloom | v0.1 |
| Blocked screen | Why it's blocked, when it opens, and borrow or exit buttons | v0.1 |
| Block-change warning | The 1-minute warning before an app closes | v0.1 |
| Borrow sheet | Pick an app, then wait a minute | v0.1 |
| Emergency exit | Start the weekly pass's 10-minute wait; enter a partner code from v0.2 | v0.1 |
| Settings | Theme, maintenance window, protection health and release | v0.1 |
| 24-hour dial | Drag blocks around a clock face; replaces the list editor | v0.2 |
| Held | Held notifications as text | v0.2 |
| Focus timer | Pick 25–90 minutes and start | v0.2 |
| Stats | Minutes per app and block | v0.2 |
| Special days | Pick dates and their timeline | v0.3 |
| Weekly look-back | The week as pages or as a garden | v0.3 |

## Architecture

The rule engine sits at the centre and never touches Android, so every rule can be unit-tested with a fake clock. A coordinator owns all input and output around it.

| Module | Job |
| --- | --- |
| `:core:model` | The vocabulary as types: Block, Timeline, Allowance, PendingChange and the rest. Pure Kotlin |
| `:core:rules` | The rule engine. Answers what's allowed now, the effective rules (the overlap of current rules and pending changes), the next moment to wake, and how to split a change into now and pending. Pure Kotlin with an injected clock |
| `:data` | DataStore for settings, the timeline and pending changes; Room for history and held text |
| `:guard` | Runs in its own process. The accessibility service with its overlays (pause, warnings, blocked screen), the notification listener with the alert replayer, the device-admin receiver, the secure-settings writer, the scheduler (exact alarms, re-armed at boot), the health monitor behind Unguarded, and the coordinator. Enforcement goes through an Enforcer interface: accessibility now, device owner in v0.3 |
| `:design` | Ink Tide tokens, Literata, motion specs, ink shaders and haptics |
| `:feature:*` | Home, today's page, search, editors, pending changes, setup, settings |
| `:app` | Wiring and the home activity |

## Build order

Week 1 runs the spikes in `spikes.md` on the Moto before the real build starts. Any failed spike changes this spec first.

| Milestone | Contents | Gate to the next |
| --- | --- | --- |
| v0.1 Daily driver | Launcher shell and Ink Tide, rule engine and list editor, blocking and self-heal, held notifications and alert ownership, limits with pause and warnings, cooldown and calibration, borrows and the weekly emergency pass, Shizuku setup and the seal | The test checklist passes Unguarded on the Moto; then seal and live 7 days with it as your only launcher |
| v0.2 Feels like Covenant | Garden, 24-hour dial, partner codes, Held view and focus timer, stats, updates in the maintenance window | The test checklist passes |
| v0.3 Life-proof | Websites follow apps, special days, weekly look-back, export and import, Fortress, Advanced Protection check | The checklist passes on a second, borrowed phone |
| v1.0 Public | Reproducible builds, GitHub Releases and Obtainium, IzzyOnDroid then F-Droid | — |

Develop on the emulator, then install each release on your phone through Obtainium or ADB (in the maintenance window once sealed). Register the package and signing key for developer verification in Play Console well before it reaches India in 2027 ([source](https://android-developers.googleblog.com/2026/03/android-developer-verification-rolling-out-to-all-developers.html)).

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

- Cooldown bounds for R26: the shortest and longest allowed.
- Can you borrow an app whose limit ran out, or an app in no block (R8)?
- Partner codes: printed codes are shown to you first. Consider counter-based codes that live only in the partner's authenticator app.
- Which hand-offs from essentials join the allowlist: Photos from Camera, a bank's card check in a browser, the password manager's unlock screen?
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
