# Trying RC Timing Control

> **Early pre-release (v0.1)**: this is a preview build for clubs to evaluate. The features are all there, but the software has not yet been used at a full race meeting. Please report anything that doesn't work.

This guide sets up a copy of the app with a demo club and a simulated decoder, so you can explore race control with live laps. It takes about five minutes and needs no developer tools.

Use a computer you are not using for real race meetings, or uninstall afterwards. The demo club goes into the app's database alongside anything else in it.

---

## What you get

- The full app, pre-loaded with a demo club (Wyvern RC Club), its competitors, entries and transponders, and a completed past event.
- A simulated AMB decoder on the same computer, sending lap passings for the demo transponders so race control shows live timing.
- Every feature to explore: race control, the admin panel, results and championship standings.

---

With Docker on your computer, you can skip the install: follow [docker.md](docker.md) to start the demo, then go straight to [Things to try](#things-to-try).

## Step 1: Install the app

Download the installer for your computer from the [latest release](https://github.com/oggthemiffed/RCTimingControl/releases/latest) and install it as described in [installing.md](installing.md). Don't set the club up yet.

## Step 2: Turn on the demo club

Stop the service, add a setting that loads the demo club, then start the service again. The data folder and the start and stop commands for each system are listed in [installing.md](installing.md).

Create a file called `application.properties` in the data folder containing:

```properties
spring.profiles.active=demo
```

On Linux, for example:

```bash
sudo systemctl stop rctimingcontrol-RCTimingControl
echo 'spring.profiles.active=demo' | sudo tee /var/lib/rctimingcontrol/application.properties
sudo systemctl start rctimingcontrol-RCTimingControl
```

The app loads the demo club when it starts.

## Step 3: Start the simulated decoder

Open a terminal (Command Prompt on Windows) and run:

| System | Command |
|--------|---------|
| Windows | `"C:\Program Files\RCTimingControl\RCTimingControl.exe" simulate` |
| macOS | `/Applications/RCTimingControl.app/Contents/MacOS/RCTimingControl simulate` |
| Linux | `/opt/rctimingcontrol/bin/RCTimingControl simulate` |

It sends laps for the demo club's eight transponders on port 5100, where the demo club's decoder settings point. Leave the window open. Press `Ctrl+C` to stop it.

## Step 4: Open the app

Go to **[http://localhost:8080](http://localhost:8080)** and sign in as `admin@example.com` with the password **`trial123`**. That account is an admin, race director and referee, so it can use everything.

---

## Things to try

### Run a race with live timing

The demo event, **Wyvern Winter Series Round 4**, is still open for entries, so it has no races yet. These steps take it through a race day.

1. Go to **Admin** → **Events** and open **Wyvern Winter Series Round 4**.
2. Click **Close Entries** and confirm, then click **Start Event**. The event is now In Progress.
3. Go to **Race Control** in the sidebar and choose the event. The bar at the top shows **DECODER connected** while the simulated decoder is running.
4. Click **Generate Rounds**, keep the suggested practice, qualifying and finals settings, and click **Generate Rounds** again. The run order appears on the left with the first race selected.
5. Click **Call Grid**, then **Start Race**. Laps from the simulated decoder appear within about 15 seconds and the running order updates as you watch.
6. When you have seen enough, click **Finish Race** and confirm. The result is saved and shown. Pick the next race in the run order to carry on.

While a race is running, open these in other tabs or on a phone (see the About page for the address to use):

- [/boards/now-next](http://localhost:8080/boards/now-next): the spectator board with the race on track and what's next.
- [/boards/overlay](http://localhost:8080/boards/overlay): the streaming overlay for OBS.

### Admin

1. Go to **Admin** → **Championships** to see the points standings from the demo's past round.
2. Go to **Admin** → **Club Profile** to see the club's details. You can edit them to match your own club.
3. Go to **Admin** → **Decoder** to see how the decoder connection is set up.

### Open practice

When no race is running, go to **Race Control**, choose the event, then **Practice** in the top bar. Click **New Session**, give it a name, then **Start Practice** to see laps for every demo racer without a race.

### Public pages

Open [http://localhost:8080/events](http://localhost:8080/events) in a second tab. This is the public event schedule, which needs no sign-in. Phones on the same network can open it too, using the addresses listed on the About page.

---

## Finishing

To stop trying it, stop the simulated decoder with `Ctrl+C`. Then uninstall the app and delete its data folder, as described in [installing.md](installing.md). If you want to use the same computer for real race meetings, install again afterwards and set up your own club.

---

## Troubleshooting

**Nothing opens at http://localhost:8080.** The service takes a few seconds to start. If something else on the computer already uses port 8080, add `server.port=8081` (or another free port) to `application.properties`, restart the service and use that port.

**No demo club.** The setting has to be in place before the service starts. Check the file is called `application.properties` (not `application.properties.txt`) and is in the data folder, then restart the service. The app's log, `logs/rctiming.log` in the data folder, lists `wyvern demo club` among the database steps when it loads the demo club.

**No live laps.** Laps only show while a race or a practice session is running. Check the simulated decoder window is still open and shows `Client connected`. The decoder status in race control shows whether the app is connected to it.

**Race control says no events are in progress.** Close the event's entries and start it first, as in step 2 above.

---

## Feedback

This is an early pre-release (v0.1). If something doesn't work or a feature is missing, please tell us. That's what this trial is for.
