# 11. Connect to RaceHub

Part of the [walkthroughs](../walkthroughs.md), which follow one imaginary club through a race day on the demo data.

Wyvern RC Club takes bookings on RaceHub and times its meetings with RCTC. This walkthrough links the two: the club's key, its classes and formats, a meeting's entries, and the results going back. [racehub-integration.md](../racehub-integration.md) explains who owns what; this page shows the order to do things in.

**You need:** an administrator account in RCTC, a **club-level key** from RaceHub (one club per key, holding `entries:read` and `results:write`), and RaceHub's address. The laptop needs the internet while you do this, but not while racing.

## 1. Get the key

In RaceHub, make a key for the club and copy it. It is shown once. RaceHub's own guide covers where; RCTC only needs the text.

## 2. Give RCTC the key

There are two places, and either works.

- **The settings file.** Add these to `application.properties` in the data folder ([installing.md](../installing.md) says where it is), or the matching environment variables in Docker, then restart the app. This is how a [provisioned install](10-config-file.md) does it.

  ```properties
  rctiming.racehub.token=the-key-from-racehub
  rctiming.racehub.results-url=https://racehub.example/api/rctc/v1/results
  ```

- **The admin page.** Open **Admin → RaceHub → Connection**. A standalone club does not see **RaceHub** in the menu until a key is set; **Club profile** has a link to the page. Enter the **RaceHub address**, paste the key into **RaceHub key** and press **Save key**. It applies at once, no restart.

  ![The Connection tab: results address and RaceHub key](../images/racehub-connection.png)

A key saved on this page is kept encrypted, is never shown again, and **overrides the settings file's key** until you press **Clear**. To rotate a key later, make the new one in RaceHub, paste it here, check **Find RaceHub events** (step 4) still lists your meetings, then revoke the old one in RaceHub.

## 3. Link the classes and formats once

In the first-run wizard this is the optional **RaceHub Classes** step (the key can wait: the wizard says to put it in the settings file or the RaceHub page afterwards).

![The wizard's RaceHub classes step](../images/setup-racehub.png)

To do it later, or to change links, open **Admin → RaceHub → Classes and formats**.

![Classes and formats: link each RaceHub class to one of yours](../images/racehub-classes.png)

- **Link the matching names** links every class that has the same name as one of yours.
- **Create or link all** makes a class of your own for each one that is left. Handy on a new install.
- Otherwise pick a class in the row and press **Link**.
- Lower on the page, **Race formats** links each format RaceHub lists to one of your format templates. The organiser picks one format per meeting on RaceHub, and an imported meeting adds its classes with the linked format. A format you leave unlinked is asked for at import, as before.

Links are for the whole club and made once. Every meeting after that finds its classes through them, and a class is never matched by name when an entry arrives. The tab shows how many classes are still unlinked.

## 4. Find the meeting and take it

Open **Events** and press **Find RaceHub events**. The list shows the meetings the club has released to timing.

![Find RaceHub events](../images/racehub-find-events.png)

Press **Take** next to a meeting. RCTC creates the event (name and date from RaceHub), sets up its entry feed, fetches the entries straight away and opens the event's **Entries** tab. A meeting already taken shows **Taken, open**.

## 5. Import the entries

The Entries tab holds the fetched file with a preview of what will change: new entries, changed ones and withdrawn ones. Check it and apply. From then on the feed fetches every few minutes (**Fetch now** does it at once), and changes that apply cleanly go in by themselves.

- If an entry is in a class with no link, the whole import is **held**: nothing is saved, and nothing is placed by name. The preview lists the class and lets you link it, or add it to the event, in the same row, and pick its format. Save and the file is checked again.
- A transponder number used twice in the event is accepted with a warning, because one competitor may race in two classes. A swap on the day refuses a number another competitor holds.
- A driver who agreed on RaceHub may bring a headshot and car photo; press **Fetch the pictures** on the import result to save them.

Then choose the track, check the entries are all there, and press **Start meeting**. Walk-ins are added in RCTC as always ([walkthrough 2](2-entries-and-walk-ins.md)).

## 6. Results go back

Race, correct and finish as normal ([walkthrough 3](3-qualifying-and-finals.md)). RCTC sends the event's results to RaceHub when a race finishes, when one is corrected and when you complete the event. The laptop can be offline: sends queue and go when the network is back. **Admin → RaceHub → Sent results** shows each one.

![Sent results: each send and its status](../images/racehub-sent-results.png)

**Waiting to send** and **Failed, will retry** rows retry by themselves, and **Send now** tries one immediately.

To send an event again, for example after fixing a result, press **Re-upload** on its row in **Events** (administrators only, for events that came from RaceHub). It sends the whole event; RaceHub keeps the newest copy, so it is safe to repeat.

![Re-upload on the Events list](../images/racehub-reupload.png)

**Good to know**

- Without both an address and a key, results wait in the queue, and **Download results** on the event's page still gives you the file.
- Racing is never held up by sending, and the club keeps every lap and result locally whether or not RaceHub receives them.
- The same page's **Publish results to** choice can also publish a results site, for a club that wants results on the web without RaceHub.
- A club that does not use RaceHub can skip all of this: see "Using RCTC without RaceHub" in [racehub-integration.md](../racehub-integration.md).

**Next:** [Finish the meeting: results and printing](7-results-and-printing.md).

---

[← 10. Set up from a config file](10-config-file.md) · [All walkthroughs](../walkthroughs.md)
