# Running with Docker

Docker runs the same app on Windows, macOS and Linux. It's the quickest way to try the demo club, because it needs no installer and no release.

## What you need

- **Windows or macOS:** [Docker Desktop](https://www.docker.com/products/docker-desktop/), installed and running.
- **Linux:** Docker Engine with the Compose plugin (`docker compose version` should print a version).
- A copy of this repository: `git clone https://github.com/oggthemiffed/RCTimingControl.git`, or **Code → Download ZIP** on GitHub, unzipped.

The first start builds the app inside Docker, which takes several minutes. Later starts reuse the build.

## Try the demo

In a terminal, in the repository folder, run:

```bash
docker compose -f docker-compose.demo.yml up --build
```

This starts the app with the demo club (Wyvern RC Club) and a simulated decoder that sends laps for the demo club's eight transponders. When the log shows `Connected to RC-4 decoder at localhost:5100`, open **[http://localhost:8080](http://localhost:8080)** and sign in as `admin@example.com` with the password **`trial123`**. That account is an admin, race director and referee.

Then follow [Things to try](trial-quickstart.md#things-to-try) in the trial guide to run a race with live timing.

Press `Ctrl+C` to stop the demo. Run the same `up` command (without `--build`) to start it again with the same data.

### Starting again from scratch

To delete the demo's data and start with a fresh demo club:

```bash
docker compose -f docker-compose.demo.yml down -v
```

After pulling a newer version of the repository, add `--build` to the `up` command so Docker rebuilds the app.

### Opening it on other devices

Phones, tablets and other computers on the same network can use the demo too, at the address of the computer running Docker, for example `http://192.168.1.50:8080/`. The About page lists the container's own addresses, which other devices can't reach, so use the computer's address instead. On Windows and macOS, allow Docker Desktop through the firewall if a phone can't connect.

### If port 8080 is in use

Set a different port when starting:

```bash
HOST_PORT=8081 docker compose -f docker-compose.demo.yml up --build
```

On Windows Command Prompt, run `set HOST_PORT=8081` first. Then open `http://localhost:8081`.

### Voice announcements

The demo includes the announcer voice server (Piper) with one voice, so race control speaks announcements from the server. The first start downloads the voice, so it needs the internet; after that it runs offline. If the voice server is down, race control falls back to the browser's own voice.

## Running the club's server with Docker

Docker can run the club's own copy of the app on a laptop or a small server on the club network, in place of the installer. `docker-compose.club.yml` starts the app and the announcer voices (Piper) and restarts them whenever Docker starts.

### Before you start

- Give the machine a fixed address on the club network, either a reserved address in the router or a static IP, so phones and boards can find it again next time. For example `192.168.1.50`.
- **Linux server:** make sure Docker starts at boot (`sudo systemctl enable docker`).
- **Windows or macOS laptop:** in Docker Desktop's settings, turn on **Start Docker Desktop when you sign in**. The app runs only while Docker Desktop is running.
- The first start needs the internet, to download the base images and the voices. After that it runs offline.

### Start it

In the repository folder, create a file called `.env` holding the machine's address:

```properties
CLUB_ADDRESS=192.168.1.50
```

`CLUB_ADDRESS` is the address the About page shows other devices. The app can't see the machine's address from inside the container, so without it the About page lists addresses no other device can reach. Several can be given, separated by commas, for example `192.168.1.50,club-timing`. Docker reads `.env` every time, so the setting stays in place for restarts and upgrades.

Then start it:

```bash
docker compose -f docker-compose.club.yml up -d --build
```

Open **http://localhost:8080** on that machine and follow the setup wizard to create the first official and the club, and set the decoder's address. Phones, tablets and boards use `http://192.168.1.50:8080/`. If they can't connect, check the machine's firewall allows port 8080 (on Linux with `ufw`, `sudo ufw allow 8080/tcp`; on Windows and macOS, allow Docker Desktop through the firewall).

To use port 80, so devices can open `http://192.168.1.50/` with no port, add `HOST_PORT=80` to `.env` and give the address as a full URL: `CLUB_ADDRESS=http://192.168.1.50/`.

### Where the data is

Everything the app keeps is in the `club_data` volume, mounted at `/data` in the container: the database `rctiming.db`, `backups/`, `uploads/` and the `jwt-secret` sign-in key. It survives stopping, restarting and rebuilding. Only `docker compose -f docker-compose.club.yml down -v` deletes it, so never add `-v` on the club's server.

Settings that [installing.md](installing.md#changing-settings) puts in `application.properties` go in the `environment:` list of `docker-compose.club.yml` instead, in capitals with underscores. For example, to keep the backups on a USB stick mounted at `/media/usb`, add `RCTIMING_BACKUP_DIRECTORY: /backups` to `environment:` and `- /media/usb/rctiming-backups:/backups` to `volumes:`. The folder must be writable by the container's user (uid 10001): `sudo install -d -o 10001 /media/usb/rctiming-backups`.

### Upgrading

Pull the newer version of the repository, then rebuild and restart:

```bash
git pull
docker compose -f docker-compose.club.yml up -d --build
```

The data stays in the volume. Take a backup under **Admin → Backups** first.

### Restoring a backup, or a lost admin password

Stop the app, run the command in a one-off container, then start the app again:

```bash
docker compose -f docker-compose.club.yml stop app
docker compose -f docker-compose.club.yml run --rm --no-deps app restore /data/backups/<file>
docker compose -f docker-compose.club.yml start app
```

`reset-admin-password` works the same way (see [installing.md](installing.md#locked-out)).

The app listens on plain HTTP and is meant for the club network. Don't expose it to the internet as it is.
