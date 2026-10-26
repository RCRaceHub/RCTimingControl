# Installing on a venue laptop

RC Timing Control installs as one package on the laptop that sits by the decoder. The package carries its own Java runtime, so the laptop needs no Java and no Docker. The app runs as a background service with no window and starts when the laptop boots. Officials' phones and tablets and the spectator boards open it in a browser over the venue network.

## What you need

- A laptop running Windows 10 or 11 (64-bit), macOS 12 or later, or a Debian-based Linux such as Ubuntu.
- The installer for that system:

| System | File |
|--------|------|
| Windows | `RCTimingControl-<version>.msi` |
| macOS | `RCTimingControl-<version>.pkg` |
| Linux | `rctimingcontrol_<version>_amd64.deb`, or `_arm64.deb` on ARM |

To build an installer yourself, see [Building the installers](#building-the-installers).

## Install

**Windows.** Double-click the `.msi` file and follow the prompts. Windows may say it protected your PC, because the installer is not signed yet. Choose **More info**, then **Run anyway**. The installer also adds a Windows Firewall rule so other devices can connect on private networks. When the laptop joins the venue network, set that network to **Private** (**Settings → Network & internet → Wi-Fi** or **Ethernet → the network → Private network**). Windows treats new networks as public, and blocks phones from reaching the app on a public network.

**macOS.** The package is not signed yet, so double-clicking it shows a warning. Right-click the `.pkg` file, choose **Open**, then **Open** again. Or open **System Settings → Privacy & Security** and choose **Open Anyway**.

**Linux.** On Ubuntu, Debian or another Debian-based system (64-bit Intel or AMD, or 64-bit ARM such as a Raspberry Pi 4 or 5), install from the club's apt repository, so later releases arrive with the laptop's normal updates:

```bash
sudo install -d -m 0755 /etc/apt/keyrings
sudo curl -fsSL -o /etc/apt/keyrings/rctc-archive-keyring.gpg https://rcracehub.github.io/RCTimingControl/apt/rctc-archive-keyring.gpg
sudo curl -fsSL -o /etc/apt/sources.list.d/rctimingcontrol.sources https://rcracehub.github.io/RCTimingControl/apt/rctimingcontrol.sources
sudo apt update
sudo apt install rctimingcontrol
```

The packages are signed, and apt checks the signature against that key. The service starts as soon as the install finishes. To update later, run `sudo apt update && sudo apt upgrade`; the service restarts and keeps its data. The repository holds the newest release only; older ones stay on the GitHub releases page. The laptop needs the internet only to install and update, not for racing.

With no internet at the track, or to install one particular release, download its `.deb` from the releases page and run:

```bash
sudo apt install ./rctimingcontrol_<version>_amd64.deb
```

(Use the `_arm64.deb` on ARM.)

## First run

1. On the laptop, open **http://localhost:8080** in a browser. The setup wizard asks for the first official's account, then the club, track and decoder details.
2. Open **http://localhost:8080/about** to see the addresses other devices should use, for example `http://192.168.1.50:8080/`. The same addresses are written to the log each time the app starts.
3. On a phone connected to the same Wi-Fi or network, open one of those addresses and sign in.

These guides are also served by the app at `/docs` on the same address (for example `http://localhost:8080/docs`), with a menu and search, so they can be read on the laptop or a phone without the internet.

The address by the laptop's name, such as `http://timing-laptop:8080/`, works on most phones and keeps working if the laptop gets a different IP address. If it does not work on a phone, use the IP address instead.

If a phone cannot connect, check that it is on the same network as the laptop and not on mobile data. Then check the laptop's firewall. On Windows, check the venue network is set to Private. On macOS with the firewall turned on, allow incoming connections for RC Timing Control in **System Settings → Network → Firewall → Options**. On Linux with `ufw` enabled, run `sudo ufw allow 8080/tcp`.

## Where things are kept

The data lives outside the install folder, so installing a newer version keeps it.

| System | Data folder |
|--------|-------------|
| Windows | `C:\ProgramData\RCTimingControl` |
| macOS | `/Library/Application Support/RCTimingControl` |
| Linux | `/var/lib/rctimingcontrol` |

On Linux the service runs as its own unprivileged user, `rctiming`, so use `sudo` to look in the folder. The folder holds:

- `rctiming.db`, the database;
- `backups/`, the automatic backups;
- `uploads/`, the club logo and announcer clips;
- `logs/rctiming.log`, the app's log;
- `jwt-secret`, the key that signs officials' sign-ins, created on first start. Keep it private.

## Changing settings

To change a setting such as the port, create a file called `application.properties` in the data folder, then restart the service. For example:

```properties
# Use port 80, so devices can open http://timing-laptop/ with no port number
server.port=80
# Keep backups on a USB stick
rctiming.backup.directory=E:/rctiming-backups
# Announce the laptop on the venue network as timing.local
rctiming.network.mdns-name=timing
```

With `rctiming.network.mdns-name` set, the app announces itself by that name over mDNS, so devices can open `http://timing.local:8080/` without knowing the laptop's address. Windows 10 and later, macOS, iOS and Android look up `.local` names themselves; the About page lists the name first. The name is announced on IPv4 only, and the app looks at the laptop's network every 30 seconds, so a laptop that boots before its network is up, or moves to another network, is picked up without a restart. If another device already uses the name, the app logs the name it got instead. It is off unless you set it, and it does not work from inside Docker, because multicast cannot cross Docker's network (use host networking, or leave it off and set `CLUB_ADDRESS`). Because `http://timing.local` is plain HTTP, browsers treat it as not secure, so the sound card list on the audio output page works only at `http://localhost:8080` on the laptop itself, unless you turn on the secure connection below.

## Secure connection (HTTPS)

Browsers only offer the sound card list on the audio output page, and some other device features, on a secure page. A public authority cannot sign `.local` names or private addresses, so the app can act as the club's own certificate authority instead. HTTPS is off unless you turn it on, and it runs beside plain HTTP, so existing bookmarks keep working:

```properties
rctiming.https.enabled=true
# The default; change it if 8443 is taken
rctiming.https.port=8443
```

You can also switch it on from **Club profile → Secure connection**, which writes these lines for you; it still takes a restart. After a restart, devices open `https://timing.local:8443/` (the About page lists every secure address). The app creates a club certificate authority valid for 10 years and a server certificate valid for 1 year that covers the laptop's names and addresses. The server certificate is renewed automatically within 30 days of expiry, and re-issued when the laptop gains a new address or name; the app checks every 10 minutes and picks the change up without a restart. The certificates live in the `tls` folder of the data folder. Back that folder up with the rest of the data folder, and keep it private, because whoever holds the authority's key can issue certificates the club's devices trust.

Each device warns about the certificate until it trusts the club authority. On the About page, download the club certificate and follow the steps shown there for Windows, macOS, iOS, Android or Linux. This is needed once per device, and the 10-year lifetime means it will not need repeating soon.

If something goes wrong on the day, an administrator can open **Club profile → Secure connection** and press **Make a new server certificate** (devices keep trusting it) or **Make a new club certificate** (a new authority and a new server certificate; every device must download and trust the new certificate again, so the app asks first). Both are recorded in the audit log.

On Linux the backup folder must be one the `rctiming` user can write to. For a folder on the laptop's own disk, create it with `sudo install -d -o rctiming -g rctiming /srv/rctiming-backups`. A USB stick or network share has to be mounted so that user can write to it, for example with `uid=rctiming,gid=rctiming` in its mount options. The desktop's automatic mount under `/media/<your name>` is private to you, so the service can't use it.

## Backing up the settings on their own

**Club profile → Settings file** downloads the club's configuration as one small JSON file: the club's details, announcer settings and default voice, live timing thresholds, decoder address, where results are published, the privacy notice and the governing bodies. It leaves out race history, officials and passwords, the sign-in key, the logo, uploaded horn sounds and the RaceHub address (which can carry a secret), so it is safe to keep with the club's papers. It does hold the club's contact details (email, phone, website), which you may not want to share widely. On a new install, or after a reset, choose the file under **Use a settings file**; the page lists what it holds and which parts would change anything, and nothing is changed until you press Import. Governing bodies are added or updated by code and never removed. This does not replace backups: the full data folder and the scheduled backups still hold everything else, and every setting changed in the app is already kept in the database and survives a restart.

## Sending results to RaceHub

For events whose entries were imported from RaceHub, the app sends the results back as each race finishes, when a finished race is corrected, and when the event is completed, which closes the race day (see [results-export-v1.md](results-export-v1.md)). Give it RaceHub's address and the club's key. The address can be typed into **Results to RaceHub** in the admin panel; the key goes in `application.properties`, and so can the address, which is then the default used while the admin panel's is empty:

```properties
rctiming.racehub.results-url=https://racehub.example/api/results
rctiming.racehub.token=the-key-from-racehub
```

The address must start with `https://`, since the key goes with every request. Without both settings the results wait in the app, and are sent once they are set and the service restarted. The laptop doesn't need to be online while racing: results queue while the network is down and go when it is back. **Results to RaceHub** in the admin panel shows what has been sent, and each event's page has a **Download results** button for taking the file across by hand.

## Publishing a results site

A club without RaceHub can still put its results on the web. Choose **A results site** (or both) under **Publish results to** in **Results to RaceHub**, then press **Download results site (zip)** and upload the files to the club's web space; they are plain pages with no scripts. To have the app keep a folder up to date by itself after each race, correction and day close, set the folder in `application.properties` and point a sync tool at it:

```
rctiming.results-site.directory=C:/Users/club/results-site
```

The folder is also rewritten every 5 minutes (`rctiming.results-site.refresh-minutes`), which picks up changes that raise no signal of their own, such as a restarted race or a competitor's public name setting.

The pages show the same names as the public screens (each competitor's public name setting) and no lap-by-lap times or penalty reasons.

## Sending a live feed

The app can send each race as it runs to a relay on the internet, so people away from the track can follow it (see [live-feed-v1.md](live-feed-v1.md)). The laptop connects out to the relay; nothing connects in to it. Give it the relay's address and the club's key in `application.properties`:

```properties
rctiming.livefeed.relay-url=wss://relay.example/publish
rctiming.livefeed.token=the-key-from-the-relay
```

The address must start with `wss://`, since the key goes with the connection. Restart the service after setting them. Race control then shows the live feed's state in its status bar, with a **Send this event** switch that the race director turns on for each event to be sent. If the network or the relay drops out, racing carries on and the feed picks up again when it is back.

## Starting and stopping the service

| System | Stop | Start |
|--------|------|-------|
| Windows | `sc stop RCTimingControl` (or the Services app) | `sc start RCTimingControl` |
| macOS | `sudo launchctl bootout system/dev.monkeypatch.rctiming-RCTimingControl` | `sudo launchctl bootstrap system /Library/LaunchDaemons/dev.monkeypatch.rctiming-RCTimingControl.plist` |
| Linux | `sudo systemctl stop rctimingcontrol-RCTimingControl` | `sudo systemctl start rctimingcontrol-RCTimingControl` |

Run Windows commands from an administrator Command Prompt.

## Restoring a backup

Stop the service, then run the restore command as an administrator (Windows) or with `sudo` (macOS, Linux):

| System | Command |
|--------|---------|
| Windows | `"C:\Program Files\RCTimingControl\RCTimingControl.exe" restore "C:\ProgramData\RCTimingControl\backups\<file>"` |
| macOS | `sudo /Applications/RCTimingControl.app/Contents/MacOS/RCTimingControl restore "/Library/Application Support/RCTimingControl/backups/<file>"` |
| Linux | `sudo /opt/rctimingcontrol/bin/RCTimingControl restore /var/lib/rctimingcontrol/backups/<file>` |

Then start the service again. The restored database is given the data folder's owner, so on Linux the service can still write to it after a restore run with `sudo`.

## Locked out

If no admin can sign in (the only admin forgot their password, or was disabled), reset an admin's password from the laptop itself. Stop the service, then run the command as an administrator (Windows) or with `sudo` (macOS, Linux), giving the admin's email:

| System | Command |
|--------|---------|
| Windows | `"C:\Program Files\RCTimingControl\RCTimingControl.exe" reset-admin-password admin@club.example` |
| macOS | `sudo /Applications/RCTimingControl.app/Contents/MacOS/RCTimingControl reset-admin-password admin@club.example` |
| Linux | `sudo /opt/rctimingcontrol/bin/RCTimingControl reset-admin-password admin@club.example` |

It asks for the new password twice (at least 8 characters). Run it with no email to list the admins. The command also enables the account if it was disabled, gives it the Admin role if it had lost it, and signs it out everywhere. The change shows in **Admin → Officials** under recent changes, as made from the command line. Then start the service again.

Anyone who can run commands as an administrator on the laptop can do this, so keep the laptop's own login safe.

## Upgrading

Install the newer package over the old one, the same way as the first install. The service stops, the new version is put in place, and the service starts again. The new version updates the database to its format when it starts.

## Uninstalling

Uninstall it the usual way for the system: **Settings → Apps** on Windows, or `sudo apt remove rctimingcontrol` on Linux. On macOS, run `sudo launchctl bootout system/dev.monkeypatch.rctiming-RCTimingControl`, then delete `/Library/LaunchDaemons/dev.monkeypatch.rctiming-RCTimingControl.plist` and `/Applications/RCTimingControl.app`. Uninstalling leaves the data folder in place. Delete it as well to remove everything.

## Building the installers

Each installer is built on the system it is for. You need JDK 21 and Node 22.12 or newer, and:

- **Windows:** the WiX Toolset 3, and the 64-bit `nssm.exe` from [NSSM 2.24](https://nssm.cc/download), which registers the service.
- **macOS:** the Xcode command line tools.
- **Linux:** `fakeroot` (`sudo apt install fakeroot`).

Then run:

```bash
./gradlew -PbundleFrontend :app:installer
# Windows also needs the path to nssm.exe:
#   gradlew -PbundleFrontend -PwindowsServiceInstaller=C:\tools\nssm\win64\nssm.exe :app:installer
```

The installer is written to `app/build/installer/out/`. The installer's version is the numeric part of `VERSION` (`0.1.0-alpha.1` builds `0.1.0`); pass `-PinstallerVersion=0.1.1` to set it. macOS does not accept versions starting with 0, so pre-1.0 macOS packages show a leading 1 (`0.1.0` shows as `1.1.0`). The About page always shows the real version.

`-PbundleFrontend` on its own builds a jar with the UI inside: `./gradlew -PbundleFrontend :app:bootJar`, then `java -jar app/build/libs/app.jar` serves everything on port 8080.
