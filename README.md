<h1 align="center">Argus 🛡️</h1>

<p align="center">
  <a href="https://github.com/tatselkrik/argus/actions/workflows/quality.yml"><img src="https://github.com/tatselkrik/argus/actions/workflows/quality.yml/badge.svg" alt="Quality checks"></a>
  <img src="https://img.shields.io/badge/version-1.0.0-2563eb" alt="Version 1.0.0">
  <img src="https://img.shields.io/badge/platform-Android%207.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 7.0 or newer">
  <img src="https://img.shields.io/badge/license-PolyForm%20Noncommercial-6b7280" alt="PolyForm Noncommercial License 1.0.0">
</p>

<p align="center">
  Account-free, multi-phone power and connectivity monitoring using old Android phones.
</p>

<p align="center">
  <strong>Version 1.0.0</strong> · Android 7.0+ · No subscription · Personal project
</p>

> [!IMPORTANT]
> Argus is a personal project, but feel free to use, adapt, or build on it for
> personal and other noncommercial purposes. It is an alerting aid, not a
> life-safety system. See the [license](#license) and
> [privacy and security](#privacy-and-security) notes before using it.

## What Argus does

Argus turns one or more spare Android phones into named **Home** monitors. An
**Away** phone receives alerts when mains power is lost or restored, when a
Home phone reboots, or when a selected Home phone stops checking in.

The same APK supports both roles. Monitoring runs only after **Start** is
pressed and can be stopped explicitly on either role.

~~~mermaid
flowchart LR
    A[Home phone 1] --> C[Private shared ntfy channel]
    B[Home phone 2+] --> C
    C --> D[Away phone]
    D --> E[Named alerts and per-phone status]
~~~

## Version 1 features

- Immediate charger-disconnect and power-restoration detection while a network
  path is available.
- Named Home and Away phones.
- Multiple Home phones on one channel.
- Away-phone monitoring of all discovered Home phones or a selected subset.
- Dedicated power-loss, power-restored, reboot, test, and offline alerts.
- Silent 30-minute check-ins with an offline warning only after two expected
  check-ins are missed.
- Intentional **Stop** state so planned pauses do not normally become false
  offline warnings.
- Persistent event history with swipe-left or swipe-right deletion per entry.
- Background monitoring across app closure and device reboot while monitoring
  remains started.
- No Argus account, subscription, analytics, advertising, location access, SMS,
  or phone-call permission.

## Install

When a GitHub release is available, download its signed
<code>argus-v1.0.0.apk</code> on each phone and allow installation from that
browser or file manager when Android asks. Install the same APK on every Home
and Away phone.

The V1 package identity is <code>io.github.tatselkrik.argus</code>. Earlier
development builds used a different identity, so remove the old development
copy after confirming that V1 appears as a separate app. Settings do not
migrate between the two.

## Set up the phones

### Home phone

1. Choose **Home** and save a unique name such as <code>S10 Plus</code>.
2. Generate and save a channel, then share it privately with the Away phone.
3. Plug the phone into the charger and connect it to Wi-Fi.
4. Use the in-app battery-optimization button. On Samsung, also add Argus under
   **Settings → Battery → Background usage limits → Never sleeping apps**.
5. Press **Start**. The screen may then remain off.

Repeat these steps for any additional Home phones, using a different name but
the same channel.

### Away phone

1. Choose **Away**, save its name, and enter the same channel.
2. Press **Start** and allow notifications.
3. Keep **Monitor all home phones** enabled, or turn it off and select only the
   discovered Home phones you want to monitor.

Use **Switch role** if a phone needs a different role. The role picker includes
a Back button, so opening it does not force a change.

## Notifications and status

Examples:

~~~text
S10 Plus: Power lost at home
S10 Plus: Power is back
S10 Plus: Phone has rebooted
S10 Plus is offline
~~~

| Home-phone condition | Away-phone status |
|---|---|
| Contact within 30 minutes | Green — all good |
| Silent for 30 to under 60 minutes | Amber — one expected check-in missed |
| Silent for at least 60 minutes | Red — two expected check-ins missed |
| Never contacted | Neutral — waiting |
| Monitoring stopped intentionally | Paused, without an offline alert when the stop message is delivered |

Check-ins are silent and do not fill the live log. Swipe an individual visible
log entry left or right to remove it permanently.

## Important behavior

- Power alerts are immediate when Home monitoring is started and the phone
  still has an internet path.
- If an outage also turns off the router, the Home phone queues its power event
  and sends it after connectivity returns.
- Wi-Fi or internet loss alone is reported as **&lt;phone name&gt; is offline**
  after the two-missed-check-in threshold. Silence cannot prove that mains
  power was lost, so Argus does not label it as a power cut.
- If a Home phone is stopped while already offline, its silent pause message
  cannot reach the Away phone and an offline warning may still appear.
- Android manufacturer battery management can delay background work. The
  battery-optimization exemption is therefore part of setup, especially on
  Samsung phones.

## Privacy and security

Argus requires no account and has no server for you to deploy or maintain.
Messages travel over HTTPS through the public [ntfy.sh](https://ntfy.sh/)
service.

The generated channel name is a high-entropy **shared secret**, not a private
encryption key. Argus does not provide end-to-end encryption or cryptographic
sender authentication. Anyone who learns the channel can potentially read its
messages or publish forged messages to it, and the ntfy service processes the
messages on its infrastructure.

- Keep the generated channel private.
- Do not include it in screenshots, issues, logs, or public documentation.
- Generate a new channel on every phone if the existing one is exposed.
- Do not send sensitive personal information in phone names or alert text.

## Build and verify

Requirements: JDK 17+, Android SDK Platform 36.1, and Android Build Tools 36.0.0.

On Windows:

~~~powershell
gradlew.bat testDebugUnitTest lintDebug assembleDebug --no-daemon
~~~

The GitHub quality workflow runs the same unit-test, lint, and debug-build gate.
Release signing uses a private local keystore and
<code>keystore.properties</code>; both are excluded from Git and must never be
committed. Back up those two local signing files together: future Argus updates
must be signed with the same key.

## Project structure

~~~text
app/src/main/java/com/experiment/argus/
  MainActivity.kt              role picker, Home/Away screens, swipe log UI
  MainViewModel.kt             Start/Stop, names, selection, service control
  DeviceMessage.kt             named Home-phone message envelope
  HomeDeviceStore.kt           per-Home status and monitoring selection
  EventLogStore.kt             durable, individually removable alert log
  Ntfy.kt                      publish and JSON-stream subscribe
  RoleStore.kt                 role, channel, name, identity, Start persistence
  BatteryInfo.kt               battery level, temperature, and charging reads
  push/EventStreamService.kt   Away foreground stream and notifications
  sentinel/SentinelService.kt  Home power and network watchdog
  sentinel/                    power/boot receivers, retries, fallback worker
~~~

Argus uses Kotlin, Jetpack Compose Material 3, OkHttp, WorkManager, and Android
foreground services. Its minimum Android API level is 24.

## License

Argus is available under the
[PolyForm Noncommercial License 1.0.0](LICENSE). Personal, educational,
research, public-interest, and other qualifying noncommercial use is permitted;
commercial use is not.
