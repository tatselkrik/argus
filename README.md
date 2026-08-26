# Argus 🛡️

Named for Argus Panoptes, the hundred-eyed guardian. One old Android phone
becomes your home's watchdog; your daily phone gets real push notifications.

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
| **Sentinel** | old phone | Plugged in at home forever. Live charger monitoring, one-minute heartbeats, reboot self-report. |
| **Companion** | daily phone | Foreground service keeps a live stream running and raises **system push notifications** (sound + vibration) even when the app is swiped away. |

## Notifications - how they work

- The companion runs an **Android foreground service** holding the ntfy stream.
- The sentinel runs its own **foreground watchdog service**, which keeps
  Samsung/modern Android charger monitoring active after the app is swiped away.
- You will see a quiet, permanent notification: *"Argus is watching home"*.
  That is the trade Android requires for background work - and it doubles as a
  heartbeat you can glance at.
- Every **Power lost at home** / **Power is back** / **Phone has rebooted** /
  **[Test]** event
  becomes a heads-up notification on channel *Home events* (high importance).
- One-minute **[Heartbeat]**s never buzz your phone; they silently refresh the
  status banner. An hourly WorkManager heartbeat remains as a fallback.
- If no contact arrives for more than 90 seconds, the companion warns
  **Home phone is offline**. This means power, Wi-Fi, or internet may be down;
  silence alone cannot distinguish which one.
- Accept the notification permission prompt on first run (Android 13+).

## Status banner logic (companion)

| Condition | Banner |
|---|---|
| Contact < 90 sec | green - all good |
| Silent 90 sec-5 min | amber - check the connection |
| Silent > 5 min | red - something is wrong |
| Never contacted | neutral - waiting |

## Setup checklist (sentinel)

1. Generate + save channel; share it to your companion phone.
2. Plug into charger, join Wi-Fi.
3. Exempt from battery optimization (button inside the app). Samsung users:
   also Settings > Battery > Background limits > Never sleeping apps.
4. Screen off is fine. Walk away.

## Alerts reference

```
Power lost at home  battery 84% at 27.5C, charging=false
Power is back       battery 91% at 29.0C, charging=true
[Heartbeat]   one-minute proof of life (no notification)
Phone has rebooted  sentinel restarted and back on duty
[Test]        end-to-end check from either phone
```

## Honest notes

- The channel name IS the password - keep the generated random one.
- Power-cut alerts are immediate while a network path exists.
- If the router dies with the outage, the alert is stored locally and sent as
  soon as connectivity returns.
- Wi-Fi loss by itself is reported as **Home phone is offline**, never falsely
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
  MainActivity.kt              role picker, Sentinel screen, Companion screen
  MainViewModel.kt             state; observes StreamBus; controls service
  StreamBus.kt                 service <-> UI bridge (events + running flag)
  Ntfy.kt                      publish + JSON-stream subscribe
  RoleStore.kt                 role/topic/status persistence
  BatteryInfo.kt               level/temp/charging reads
  push/EventStreamService.kt   foreground service: stream + notifications
  sentinel/SentinelService.kt  live power/network watchdog
  sentinel/                    power/boot receivers, durable retries, fallback worker
```

minSdk 24 - Kotlin 2.2 - Compose M3 - OkHttp - WorkManager - foreground service
