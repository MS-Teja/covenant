# Week-1 spikes

Each spike runs on the Moto Edge 50 Fusion (Android 16) from the throwaway `spikes/` module, worst-if-it-fails first. Record pass or fail with notes; any failure changes `spec.md` before the real build starts.

| # | Spike | What passes | Result |
| --- | --- | --- | --- |
| 1 | Payment apps | Paytm, Navi and slice each open and complete a ₹1 payment with a bare accessibility service on. Record any warning, block or "Close all" dialog | **Pass** (Oct 31): all three paid with Covenant installed over ADB and its accessibility service on. Retest from a Play install |
| 2 | Install and grants | Installs through ADB and Obtainium without Play Protect blocks. One Shizuku session grants accessibility, notification access, device admin, battery exemption, notifications, the home role and WRITE_SECURE_SETTINGS. Grants survive a reboot. Shizuku's server is gone once debugging is off | — |
| 3 | Alert ownership | `requestListenerHints(HINT_HOST_DISABLE_NOTIFICATION_EFFECTS)` silences other apps' notifications. Replayed alerts for an allowed app respect ringer mode, Do Not Disturb and the channel's sound and vibration. Calls still ring. Sounds return when the listener dies | — |
| 4 | Blocking primitives | Window-change latency; `GLOBAL_ACTION_HOME`; a Compose accessibility overlay running the ink-bloom shader; Moto launcher recents (record class names); split screen; dismissing YouTube's PiP with a gesture; bubbles; haptic primitive support | — |
| 5 | Settings-guard map | For every guarded page (R33): package, class, view IDs and title, and whether Settings search reaches it | — |
| 6 | Secure-settings writes | Grayscale, adb and developer options off, automatic time on, user switcher off, accessibility re-enabled; the service rebinding after a crash; Home bringing Covenant back after a force stop | — |
| 7 | Moto extras | Secure folder, Private space, Smart Connect, Moto gestures; overnight battery behaviour | — |

## Notes

Add findings per spike below as they come in.
