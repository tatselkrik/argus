# Argus 🛡️

Named for Argus Panoptes, the hundred-eyed guardian. One or more old Android
phones become home watchdogs; your daily phone gets real push notifications.

No accounts. No server. No subscription. Events travel over ntfy.sh (free
public pub/sub); a random channel name is your private key.

## Install

Same APK on both phones:

```
adb install app/build/outputs/apk/debug/app-debug.apk
```

First launch asks for a role.

| Role | Phone | Behavior |
|---|---|---|
| **Home** | old phone(s) | Each has its own saved name. After **Start**, it monitors charging, sends 30-minute check-ins, and reports reboots. |
| **Away** | daily phone | After **Start**, it listens for named home phones and raises **system notifications** for all or only the phones you select. |

Monitoring is deliberately user-controlled. **Start** keeps the selected role
active in the background and across reboot. **Stop** cancels its services,
heartbeats, queued alerts, and offline detection.

## Notifications - how they work

- The away phone runs an **Android foreground service** holding the ntfy stream.
- Each home phone runs its own **foreground watchdog service**, which keeps
  Samsung/modern Android charger monitoring active after the app is swiped away.
- While started, you will see a quiet, permanent Argus notification.
  That is the trade Android requires for background work - and it doubles as a
  heartbeat you can glance at.
- Every selected home phone is identified by name. Notifications are labeled,
  for example, **S10 Plus: Power lost at home** or **S10 Plus is offline**.
- **[Heartbeat]** check-ins every 30 minutes never buzz your phone or appear
  in the live log;
  they silently refresh the status banner and enable offline detection.
  An hourly WorkManager heartbeat remains as a fallback.
- One missed check-in is tolerated. If no contact arrives for one hour—two
  consecutive expected check-ins—the away phone warns
  **<phone name> is offline** and records it in the live log. This means power,
  Wi-Fi, or internet may be down; silence alone cannot distinguish which one.
- Accept the notification permission prompt when pressing **Start** (Android 13+).

## Per-phone status logic (away phone)

| Condition | Status |
|---|---|
| Contact < 30 min | green - all good |
| Silent 30-<60 min | amber - one expected check-in missed |
| Silent >= 60 min | red - two expected check-ins missed |
| Never contacted | neutral - waiting |

The away phone discovers named home phones from their messages. Leave
**Monitor all home phones** checked, or turn it off and check only the phones
whose logs, event notifications, and offline warnings you want. Swipe any log
entry left or right to delete that entry permanently.

## Setup checklist (home phone)

1. Save a unique phone name, such as `S10 Plus`.
2. Generate + save the channel; share it to every home and away phone.
3. Plug into the charger and join Wi-Fi.
4. Exempt from battery optimization (button inside the app). Samsung users:
   also Settings > Battery > Background limits > Never sleeping apps.
5. Press **Start**. Screen off is fine.

On the away phone, save its name and the same channel, press **Start**, then
choose **Monitor all home phones** or a subset of discovered phones.

## Alerts reference

```
S10 Plus: Power lost at home  battery 84% at 27.5C, charging=false
S10 Plus: Power is back       battery 91% at 29.0C, charging=true
[Heartbeat]                     named 30-minute proof of life (hidden)
S10 Plus: Phone has rebooted  home phone restarted and resumed monitoring
S10 Plus: Test alert          end-to-end check from the home phone
```

## Honest notes

- The channel name IS the password - keep the generated random one.
- Nothing is monitored until **Start** is pressed; **Stop** turns it off.
- Power-cut alerts are immediate while a network path exists and monitoring is started.
- If the router dies with the outage, the alert is stored locally and sent as
  soon as connectivity returns.
- Wi-Fi loss by itself is reported as **<phone name> is offline**, never falsely
  labeled as a power cut.
- Aggressive OEM battery managers are the main enemy; the in-app exemption +
  Samsung never-sleep setting handles the common cases.
- Roadmap: camera/mic event clips, BLE thermometer drift alerts,
  light-sensor intrusion hint, barometer door-open blips.

## Building

JDK 17+, Android SDK platform 36.

```
gradlew.bat :app:assembleDebug
```

## Layout

```
app/src/main/java/com/experiment/argus/
  MainActivity.kt              role picker, Home/Away screens, swipe log UI
  MainViewModel.kt             Start/Stop, names, selection, service control
  DeviceMessage.kt             named home-phone message envelope
  HomeDeviceStore.kt           per-home status and monitoring selection
  EventLogStore.kt             durable individually removable alert log
  StreamBus.kt                 service <-> UI bridge (events + running flag)
  Ntfy.kt                      publish + JSON-stream subscribe
  RoleStore.kt                 role/topic/name/identity/Start persistence
  BatteryInfo.kt               level/temp/charging reads
  push/EventStreamService.kt   foreground service: stream + notifications
  sentinel/SentinelService.kt  live power/network watchdog
  sentinel/                    power/boot receivers, durable retries, fallback worker
```

minSdk 24 - Kotlin 2.2 - Compose M3 - OkHttp - WorkManager - foreground service
