# Trying RCTC on a real decoder

This guide is for the first time you run RCTC against your own decoder and cars, whether at the track or on the bench. It ties the other guides into one checklist: connect, record, run, and check that every lap the decoder sent was credited. It works with RC-4 text decoders (firmware below 4.5, port 5100), such as the AMB RC3 and RC4.

Do a dry run first. [trial-quickstart.md](trial-quickstart.md) loads a demo club with a simulated decoder, so you know what a healthy screen looks like before the real thing.

## Before you go

- Check the **About** page (`/about`) shows the version you expect. An older installed copy has caused false alarms before.
- Make a backup (**Admin → Backups → Back up now**) and note the decoder's IP address. Give the laptop a fixed address if you can.
- Decide whether anything else needs the decoder. Most decoders take one connection at a time, and nothing detects a second program. To run RCTC and another program together, put the recorder in front and point both at it (see "Record the whole session" below). Do not run two programs on port 5100.
- Have a paper tally ready: who drives, which transponder, roughly how many laps.

## 1. Connect

1. Open **Admin → Decoder**. Enter the decoder's address, leave the protocol on **RC4** and the port on 5100, and click **Test Connection**, then **Save**.
2. The **DECODER** badge in race control turns green. Amber means it is retrying; red means no decoder is set or it can't be reached. [decoder.md](decoder.md#troubleshooting) covers each.
3. Pass a transponder over the loop. In open practice (below) its lap should appear within a second or two.

## 2. Record the whole session

Record what the decoder sends, so you can check RCTC against it afterwards and replay it later. Run the recorder on the laptop in a second terminal and leave it running. If RCTC is also to read the decoder, share the connection:

```
RCTimingControl record --host=192.168.1.50 --listen-port=5101 --out=session.dump
```

Then set **Admin → Decoder** to `localhost` and port **5101**. Without `--listen-port` the recorder holds the decoder's only connection, so RCTC could not connect. The full guide, with the commands for each system, is [recording-the-decoder.md](recording-the-decoder.md). Keep `session.dump` and `session.dump.times` together.

## 3. Start with open practice

Open practice needs no event, heats or run order, so it is the easiest first test. In race control choose **Practice → New session → Start practice** ([trial-quickstart.md](trial-quickstart.md#open-practice)). A transponder RCTC doesn't know appears as unknown; give it a driver on the spot.

Things worth trying:

- **One car, several laps.** Count by hand and compare with the practice screen.
- **Two cars together**, and a car stopping on the loop. Note any missed or doubled laps.
- **A driver with two transponders.** Both credit the same driver.
- **Dropped link.** Unplug the decoder's network cable for 30 seconds and plug it back. The badge goes amber, then green by itself, and laps carry on.
- **Restart RCTC** (or the recorder) and check it reconnects without help.

## 4. Run a small meeting

Once practice is right, run a short meeting with a few drivers:

1. Create the event and add its classes ([user-manual.md](user-manual.md#5-create-the-event-and-bring-in-the-entries)). Bring in entries from a RaceHub export (**Find RaceHub events**, with each RaceHub class linked once under **RaceHub classes**), an RC-Timing CSV, or add walk-ins by hand.
2. Check transponders under **Race Control → Transponders**. A number shared by two entries in the same event is allowed with a warning; if both are in one race, the passing goes to the referee.
3. Generate the rounds, then for each race call the grid, start and finish it. The **Warm-up** column on the grid shows which transponders the decoder has seen since the grid was called, so a flat transponder is found before the start.
4. Try the things that go wrong on a real day: a marshal lap +1 and −1, a lap or time penalty, a stop and resume, an unknown transponder linked to an entry, and a restart.
5. Calculate the finals, check the grids, run them, and print the results ([screens-and-printouts.md](screens-and-printouts.md#printouts)).
6. Open the boards on a phone or tablet at the addresses on the About page, and the overlay in OBS if you stream ([screens-and-printouts.md](screens-and-printouts.md#the-streaming-overlay-for-obs)).

## 5. Check the laps against the recording

When you finish, stop the recorder with `Ctrl+C` and run:

```
RCTimingControl dump-summary --file=session.dump
```

It lists the passings per transponder, any gaps in the decoder's sequence numbers (a gap means the recorder lost a record) and the shortest and longest time between passings. To compare race by race, add `--windows=races.csv`, a file with a line `name,start,end` for each race in UTC, copied from the race history, or use `--split-gap=120` to split on quiet spells ([recording-the-decoder.md](recording-the-decoder.md#check-what-a-recording-holds)).

- **The counts match:** RCTC credited every passing the decoder sent.
- **The dump has a passing RCTC did not credit,** or RCTC has a lap the dump lacks: keep the dump, its `.times` file, a backup and a screenshot, and report it.
- **A sequence gap in the dump:** the recorder missed a record, so the dump is the incomplete side.

You can also replay the recording into a fresh practice session to see that the counts come out the same:

```
RCTimingControl simulate --mode=playback --file=session.dump --speed=4
```

Point **Admin → Decoder** at `localhost` for the replay and back to the real decoder afterwards ([decoder.md](decoder.md#using-the-simulator-development)).

## 6. After the session

- Take a backup (**Admin → Backups**) and copy it, and the dump files, off the laptop.
- Note anything slow, confusing or missing as you go. A screenshot taken at the time is worth more than a memory of it.
- Restoring a backup is described in [installing.md](installing.md#restoring-a-backup), if you want to prove that works too.
- The app's log is `logs/rctiming.log` in the data folder ([installing.md](installing.md#where-things-are-kept)). Keep it with anything you report.
