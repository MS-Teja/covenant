# Covenant build spec

Oct 23, 2026 · @Teja

Covenant is an open-source Android launcher that turns your phone into a different phone at each time of day. You split the day into blocks, each with its own apps, and any rule that gives an app more time waits 72 hours. Everything below is decided; v0.1 is the daily-driver build for a Motorola or Pixel phone.

## Decisions

Every choice below came from our Q&A or the research; change it here before any code depends on it.

| Area | Decision |
| --- | --- |
| Name | Covenant. Package `io.github.<your-github-username>.covenant`, so no domain is needed |
| License | GPLv3 |
| Stack | Kotlin and Jetpack Compose, written from scratch. Study Kvaesitso and Olauncher, but don't fork them |
| Android versions | Minimum Android 11, which wireless-debugging pairing needs. Target the latest SDK |
| Devices | Develop on the emulator with a Pixel image. Daily-drive on your Motorola or Pixel |
| Enforcement | Accessibility service, notification access and device admin, plus a secure-settings permission granted once over wireless debugging |
| Internet | None: no INTERNET permission, analytics or crash SDKs |
| Cooldown | 72 hours for any change that gives an app more time. Stricter changes apply instantly |
| Calibration | For the first 7 days, every change applies instantly |
| Borrows | 2 a day, 10 minutes each, after a 1-minute wait |
| Emergency exits | Partner code and weekly emergency hour, each optional. You choose which to turn on |
| Calls | Only apps you choose ring through outside their block |
| Block extras | Grayscale per block. Websites follow their apps |
| Daily tools | Special days, reading held messages, weekly look-back, focus timer |
| Opening a limited app | A 3-second themed pause showing the minutes left |
| Progress | The theme shows it. Numbers are one tap away |
| App style | Text in Ink Tide. Small tinted icons in Garden |
| Theme order | Ink Tide ships in v0.1, Garden in v0.2 |
| Debugging | Off on the daily phone except during a weekly maintenance window |
| Distribution | GitHub Releases and Obtainium first, then IzzyOnDroid and F-Droid |
| Not in v1 | AI, widgets, icon packs, Do Not Disturb per block, blocking Reels and Shorts |

## Vocabulary

Use these names in the code and on screen so the two never drift apart.

| Term | Meaning |
| --- | --- |
| Block | A named time range with its own apps, time limits and grayscale setting |
| Timeline | All 24 hours split into blocks. There is one for weekdays and one for weekends |
| Special day | A date that uses another timeline, such as a holiday or an exam day |
| Essentials | Apps available in every block |
| System allowlist | Parts of Android that Covenant never blocks: dialer, keyboard, system UI, alarms and permission dialogs |
| Outside app | Any app not in the current block. It is hidden and blocked |
| Time limit | Minutes an app gets inside one block |
| Borrow | Bringing one outside app into the current block for 10 minutes |
| Emergency exit | A partner code or the weekly emergency hour. Either opens everything for 30 minutes |
| Cooldown | The 72-hour wait before a looser change applies |
| Pending change | A looser change waiting out its cooldown. It can be cancelled |
| Held notification | A notification from an outside app, delivered when that app's next block starts |
| Ring-through list | Apps whose calls ring even outside their block |
| Quiet now | Ending the current block early and staying on essentials until the next one |
| Focus timer | Essentials only, for 25–90 minutes |
| Clean block | A block finished with no borrow, emergency exit or unguarded time |
| Calibration week | The first 7 days, when every change applies instantly |
| Maintenance window | A weekly hour when Covenant updates install and debugging may be on |
| Unguarded | A protection is off. The rules still run but are easy to bypass |

## Setup

Setup takes about 10 minutes on the phone, with no computer and no factory reset.

1. Install Covenant and set it as the home app.
2. Pick a theme: Ink Tide or Garden. You can switch anytime.
3. Pick essentials. Covenant suggests phone, messages, maps, camera, clock, your UPI apps and your authenticator.
4. Build the weekday timeline. Add up to 5 blocks, name them and set their times, then drop in apps, time limits and grayscale. A starter offers Work, Evening and Night.
5. Choose your weekend days and build the weekend timeline, which starts as a copy of the weekday one.
6. Choose which apps can ring through with calls.
7. Choose emergency exits: partner code, weekly emergency hour, both or none. Partner codes are shown once, to print.
8. Set the maintenance window. The default is Sunday, 10–11 am.
9. Grant accessibility, notification access, device admin and the battery-optimization exemption.
10. Lock in. Turn on wireless debugging and pair; Covenant grants itself the secure-settings permission once, then turns debugging off.
11. Calibration week starts. After 7 days, the 72-hour rule takes over.

In v0.1, step 10 goes through Shizuku: install it, start it over wireless debugging, and Covenant sends one grant command. You can uninstall Shizuku afterwards. Built-in pairing replaces it in v0.3.

## A weekday

This example day runs on three blocks. Work, 8 am–4 pm: Gmail, Slack, Calendar, Chrome. Evening, 4–10 pm: Instagram (30 min), YouTube (45 min), Spotify, Chrome. Night, 10 pm–8 am: essentials only, in grayscale.

1. **8:00 am.** Night ends and Work starts. Grayscale turns off, and notifications held overnight for Work apps arrive as one summary per app.
2. **8:01 am.** Home shows the date, "Work until 4:00 pm", the block's apps with essentials, and "Evening at 4:00 pm". Instagram and YouTube appear nowhere.
3. **11:20 am.** An Instagram notification goes silently into Held. You can read its text there, but you can't open Instagram.
4. **11:40 am.** A friend's instagram.com link opens in Chrome, and the page is blocked because Instagram is outside Work.
5. **1:00 pm.** You need Uber, which isn't in Work. You borrow it: a one-minute wait, then 10 minutes. Work is no longer a clean block.
6. **2:00 pm.** You start a 50-minute focus timer. Only essentials open until 2:50 pm, and it can't be stopped early.
7. **3:59 pm.** You're in Slack, which isn't in Evening. A one-minute warning appears, and Slack closes at 4:00 pm.
8. **4:00 pm.** Evening starts. Instagram's held notifications arrive as one summary.
9. **4:30 pm.** Opening Instagram shows a 3-second pause with "30 min left". Warnings come at 5 and 1 minutes left; at zero it closes until tomorrow evening.
10. **9:00 pm.** You raise Instagram to 60 minutes. It becomes a pending change that lands in 72 hours unless you cancel it.
11. **10:00 pm.** Night starts: essentials only, grayscale on. Spotify music already playing keeps going, but its screen won't open.
12. **Overnight.** The theme records the day. Work ripples or wilts from the borrow, Evening stays clean, and one tap shows minutes per app.

## Opening an app

&#91;embedded content: app open decision · 6 checks\]

Covenant runs these checks on every window change, so a notification tap or a link meets the same wall as the home screen.

## Rules: timeline and access

Each rule has an ID to use in code, commits and tests. Ships in is the milestone that builds it.

| ID | Rule | Ships in |
| --- | --- | --- |
| R1 | The timeline covers all 24 hours. Time outside every block is essentials-only. | v0.1 |
| R2 | A timeline holds at most 5 blocks. Blocks can't overlap and last at least 30 minutes. | v0.1 |
| R3 | Weekdays and weekends have separate timelines. You choose which days count as weekend. | v0.1 |
| R4 | A block that crosses midnight belongs to the day it starts. | v0.1 |
| R5 | A special day swaps in another timeline for one date. A looser one must be set 72 hours ahead; a stricter one applies instantly. | v0.3 |
| R6 | Only the current block's apps, essentials and the system allowlist are visible and can open. | v0.1 |
| R7 | Outside apps are blocked from every entry point: home, search, notifications, links, recents, share sheets, split screen and picture-in-picture. | v0.1 |
| R8 | A newly installed app belongs to no block. Adding it to one follows the cooldown. | v0.1 |
| R9 | Websites follow their apps: a domain is blocked in browsers whenever its app is outside the block. Adding a domain is instant; removing one follows the cooldown. | v0.3 |
| R10 | A browser whose address bar Covenant can't read counts as an ordinary app and must be in a block to open. | v0.3 |

## Rules: time limits, block changes, notifications and calls

Limits count per app per block, and anything held arrives when its app's next block starts.

| ID | Rule | Ships in |
| --- | --- | --- |
| R11 | A time limit is minutes per app per block, counted only while the app is on screen. | v0.1 |
| R12 | Opening a time-limited app shows a 3-second themed pause with the minutes left. It can't be skipped. | v0.1 |
| R13 | Warnings come at 5 minutes and 1 minute left. At zero the app closes and stays hidden until its next block. | v0.1 |
| R14 | Unused minutes don't carry over. | v0.1 |
| R15 | At a block boundary, an open app that's leaving gets a 1-minute warning, then closes. Calls and navigation are never cut. | v0.1 |
| R16 | Grayscale switches on or off with the block. | v0.1 |
| R17 | Notifications from outside apps are held silently and delivered when that app's next block starts, one summary per app. | v0.1 |
| R18 | Notifications from apps in no block are dismissed and counted in Held. | v0.1 |
| R19 | Held shows held notifications as text. It never opens an outside app. | v0.2 |
| R20 | Calls from apps on the ring-through list ring in every block. Their messages still wait. | v0.1 |
| R21 | Audio already playing keeps playing after its app's block ends; only the screen is blocked. | v0.1 |

## Rules: changes, cooldown and exits

Stricter changes apply now and looser ones wait 72 hours; every way out is limited and visible in the theme.

| ID | Rule | Ships in |
| --- | --- | --- |
| R22 | A change that gives any app less time applies instantly. One that gives any app more time waits 72 hours. Mixed edits are split. | v0.1 |
| R23 | Looser changes: adding apps or essentials, raising limits, growing a looser block, turning grayscale off. Also adding ring-through apps, turning on an exit, removing a website block, changing weekend days or the maintenance window. | v0.1 |
| R24 | Pending changes show a countdown and can be cancelled anytime. | v0.1 |
| R25 | Calibration week: every change applies instantly for the first 7 days. You can end it early, and it never restarts. | v0.1 |
| R26 | The cooldown can be lengthened instantly. Shortening it waits out the current cooldown. | v0.1 |
| R27 | Quiet now ends the current block early and keeps you on essentials until the next one. You can never enter a looser block early. | v0.1 |
| R28 | Focus timer: essentials only, for 25–90 minutes. It can't be ended early. | v0.2 |
| R29 | Borrows: 2 a day, 10 minutes each, after a 1-minute wait. One runs at a time and outlasts a block change. The count resets at 4 am. | v0.2 |
| R30 | Partner code, if turned on: a friend holds 10 printed one-time codes. Each opens everything for 30 minutes; regenerating voids the old set. | v0.2 |
| R31 | Weekly emergency hour, if turned on: once a week, everything opens for 30 minutes after a 10-minute wait. | v0.2 |
| R32 | Release: asking to uninstall starts a 72-hour countdown. After it, protections switch off and Covenant uninstalls normally. | v0.1 |

## Rules: tamper protection

Each protection closes one bypass, which leaves safe mode as the known escape (see Gaps).

| ID | Rule | Ships in |
| --- | --- | --- |
| R33 | Guarded settings pages stay blocked outside the maintenance window. They cover Covenant's accessibility, notification access, device admin and app info, plus default home app, developer options, date and time, and battery optimization. Covenant can still open them itself to repair a permission. | v0.1 |
| R34 | If accessibility or notification access is switched off, Covenant switches it back on within seconds. | v0.1 |
| R35 | Device admin blocks uninstalling Covenant. | v0.1 |
| R36 | USB and wireless debugging stay off outside the maintenance window. Covenant switches them off if it finds them on. | v0.1 |
| R37 | Covenant's own updates install only during the maintenance window. | v0.2 |
| R38 | A manual change to date, time or time zone keeps you in the current or a stricter block. Pending changes pause until automatic time is back on; automatic time-zone changes while travelling are followed. | v0.1 |
| R39 | If any protection is off, home shows Unguarded and that time can't count as clean. | v0.1 |

## Rules: themes, home and data

Both themes draw the same record of clean blocks, and every number stays on the phone.

| ID | Rule | Ships in |
| --- | --- | --- |
| R40 | A clean block has no borrow, emergency exit or unguarded time. Hitting a time limit doesn't count against it. | v0.1 |
| R41 | Ink Tide: dark and text-only. Each block is a band on the day's page; clean bands stay still and slips ripple. The page turns at 4 am. | v0.1 |
| R42 | Garden: light, with small tinted icons. The light follows your blocks, and each clean block grows one thing. A slip wilts it until morning, and nothing dies. | v0.2 |
| R43 | Focus timer sessions leave a mark: a dense band in Ink Tide, a stepping stone in Garden. | v0.2 |
| R44 | Numbers are one tap from the theme: minutes per app and block, borrows used and held count. Home shows none. | v0.2 |
| R45 | Weekly look-back: Ink Tide stacks the week's seven pages; Garden shows the week's growth. | v0.3 |
| R46 | Home shows the date, the current block and when it ends, its apps, and when the next block starts. | v0.1 |
| R47 | Gestures: swipe up to search allowed apps, swipe down for notifications, double-tap to lock, long-press for Covenant's menu. | v0.1 |
| R48 | Settings live in DataStore, encrypted with Tink; history lives in Room. Held message text is encrypted before it's saved and deleted once delivered. History keeps 90 days. | v0.1 |
| R49 | Export and import the timeline as a file. Importing a looser timeline follows the cooldown. | v0.3 |
| R50 | No INTERNET permission, analytics or crash SDKs. Crash logs stay on the phone and are shared by hand. | v0.1 |

## Gaps

Every hole found while planning now has a rule or a known limit; safe mode is the only one left open.

| Gap | How it's closed |
| --- | --- |
| Alarms must ring in every block | The clock's alarm screen is on the system allowlist |
| Logins need OTPs and authenticator codes | Setup suggests SMS and your authenticator as essentials |
| Payments can't wait for a block | Setup suggests your UPI apps as essentials |
| Keyboard, permission dialogs and share sheet | On the system allowlist, so never blocked |
| WhatsApp or Telegram calls outside their block | Ring-through list (R20) |
| Opening a blocked app's website instead | Websites follow apps (R9, R10) |
| Installing a new app as a loophole | New apps belong to no block (R8) |
| Setting the clock forward to reach Evening | Clock-change freeze (R38) |
| Travelling across time zones | Automatic time-zone changes are followed (R38) |
| A bad first timeline locking you in for 72 hours | Calibration week (R25) |
| Installing a patched build of Covenant over the real one | Updates install only in the maintenance window (R37). A build signed with another key can't update in place, and uninstalling is blocked. Keep the release keystore off the phone |
| USB debugging as a one-minute bypass | Debugging stays off outside the maintenance window (R36) |
| Switching off accessibility or notification access | Covenant switches it back on (R34) |
| Reboots | Rules rebuild from stored state at boot, and alarms are re-armed |
| Battery savers killing Covenant | Rare for home apps on Motorola and Pixel. Covenant also asks for the battery exemption |
| A notification flood when a block starts | One summary per app (R17) |
| Safe mode | Known escape: it lets you remove device admin and uninstall, which wipes your setup. Only device-owner mode closes it (see Later) |
| Android's Advanced Protection | It switches off accessibility for apps that aren't accessibility tools, Covenant included ([source](https://securityaffairs.com/189497/security/advanced-protection-mode-in-android-17-prevents-apps-from-misusing-accessibility-services.html)). Leave it off; Covenant shows Unguarded if it's on |
| Work-profile apps | Placed in blocks like any other app |

## Later

These ideas wait until v1 ships, listed by value.

- **Fortress mode.** Device-owner setup closes the safe-mode escape, at the cost of a factory reset.
- **Do Not Disturb per block.**
- **Blocking Reels, Shorts and Status** inside allowed apps.
- **Shared limits for app groups**, such as 30 minutes across Instagram and YouTube together.
- **Morning intentions.** Up to three lines you write each morning that sit on home all day.

## Screens

Nineteen screens make up v1, and 11 of them ship in v0.1.

| Screen | What it does | Ships in |
| --- | --- | --- |
| Setup wizard | The 11 setup steps | v0.1 |
| Home | Current block, its apps, the next block | v0.1 |
| Search | Swipe up to find allowed apps | v0.1 |
| Timeline list editor | Add, name and time blocks, then drop apps in | v0.1 |
| Block detail | Apps, time limits and grayscale for one block | v0.1 |
| Essentials and ring-through | The two always-on lists | v0.1 |
| Pending changes | Countdowns, with cancel | v0.1 |
| Opening pause | The 3-second themed pause | v0.1 |
| Blocked screen | Why it's blocked, when it opens, and borrow or exit buttons | v0.1 |
| Block-change warning | The 1-minute warning before an app closes | v0.1 |
| Settings | Theme, maintenance window, protection health and release | v0.1 |
| 24-hour dial | Drag blocks around a clock face; replaces the list editor | v0.2 |
| Borrow sheet | Pick an app, then wait a minute | v0.2 |
| Emergency exit | Enter a partner code or start the 10-minute wait | v0.2 |
| Held | Held notifications as text | v0.2 |
| Focus timer | Pick 25–90 minutes and start | v0.2 |
| Stats | Minutes per app and block | v0.2 |
| Special days | Pick dates and their timeline | v0.3 |
| Weekly look-back | The week as pages or as a garden | v0.3 |

## Architecture

&#91;embedded content: module map · rule engine at the centre\]

Keeping the rule engine free of Android code means every rule in this spec can be unit-tested with a fake clock, no phone needed.

## Build order

&#91;embedded content: roadmap · 4 milestones, 3 gates\]

Develop on the emulator, then install each release on your phone as a signed APK in the maintenance window. Register for Google's developer verification before it reaches India in 2027 ([source](https://developer.android.com/blog/posts/android-developer-verification-rolling-out-to-all-developers-on-play-console-and-android-developer-console)).

## Test checklist

Run these on your Motorola or Pixel before calling a milestone done.

Entry points, with Instagram outside the current block:

- [ ] Home and search don't show it
- [ ] Tapping its notification
- [ ] A link from WhatsApp or Gmail
- [ ] Recents
- [ ] The Play Store's Open button
- [ ] Sharing into it from another app
- [ ] Split screen and picture-in-picture
- [ ] Asking Google Assistant to open it
- [ ] Its website in Chrome (v0.3)

Tampering:

- [ ] Turning off accessibility or notification access
- [ ] Force stop and uninstall from app info
- [ ] Changing the default home app
- [ ] Changing date, time and time zone by hand
- [ ] Turning on developer options and wireless debugging
- [ ] Rebooting mid-block
- [ ] Safe mode, to record exactly what it allows

Timing:

- [ ] A block change while inside a leaving app
- [ ] A block that crosses midnight
- [ ] Limit warnings at 5 and 1 minutes
- [ ] Held notifications arriving in the right block, one summary per app
- [ ] A call from a ring-through app outside its block
- [ ] The phone switched off across a block boundary
- [ ] A pending change surviving a reboot
- [ ] Battery drain over 24 hours with every service on

## Sources

- [Advanced Protection in Android 17 and accessibility services](https://securityaffairs.com/189497/security/advanced-protection-mode-in-android-17-prevents-apps-from-misusing-accessibility-services.html)
- [Shizuku setup guide](https://shizuku.rikka.app/guide/setup/)
- [Android developer verification rollout](https://developer.android.com/blog/posts/android-developer-verification-rolling-out-to-all-developers-on-play-console-and-android-developer-console)
- [F-Droid inclusion policy](https://f-droid.org/en/docs/Inclusion_Policy/)
