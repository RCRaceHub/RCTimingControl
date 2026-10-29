# What RCTC expects from RaceHub

This page records what RCTimingControl (RCTC) reads from a RaceHub **Entry Export v1** file and from the RC-Timing driver CSV, and what it does with each field. It is written from RCTC's side only: it says what RCTC accepts and how it behaves, not what RaceHub must build. Every field below that is marked optional can be left out, and a file without them imports exactly as before.

**Status.** The fields in [Pictures and car colours](#pictures-and-car-colours) (#699) are RCTC's proposal for RaceHub. RCTC reads them from this release; RaceHub has not agreed or built them yet, so until it does the fields are simply absent from its files. Everything else on this page is the existing Entry Export v1.

- The JSON Schema (draft 2020-12) is `app/src/main/resources/racehub/entry-export-v1.schema.json`, and a test checks every import test file against it. The same file format can be produced by any booking system (set `source`); see `docs/api.md`, "The file format for any booking system".
- The Java model is `domain/racehub/RaceHubEntryExport`, and the import is `RaceHubImportService`.
- RCTC ignores any field it does not know, so contact, date of birth, guardian and payment data sent by mistake is never read or stored.

## How the file reaches RCTC

An official either chooses the file on the event's Entries tab (**Import entries from RaceHub**), or RCTC fetches it from the event's entry feed (a web address that serves the file, optionally with a token). Either way the official sees a preview of what will change before anything is saved. RaceHub never calls RCTC: RCTC runs on a laptop at the venue that other systems usually cannot reach.

## What the file carries

One entry is one driver in one class. Required fields are `entry_id`, `entry_version`, `entry_status` (`CONFIRMED` or `WITHDRAWN`), `driver_profile_id` and `event_class_id`.

| Field | Used for |
|-------|----------|
| `entry_id` | The entry's identity in RCTC. An entry is matched by it, so it must stay the same for the life of the booking. |
| `entry_version` | Goes up each time the entry changes. A row is applied only when its version is higher than the one already imported; the same version is a replay (no change) and a lower one is ignored. |
| `entry_status` | `WITHDRAWN` marks the RCTC entry withdrawn. Entries are never deleted, so their race history stays. |
| `driver_profile_id` | The driver's identity. Becomes one RCTC **competitor**, matched by this id (and the `source`), so results and championship points follow the driver across events. |
| `driver_display_name` | The name shown. Whether it shows in full is the driver's setting in RCTC. |
| `event_class_id`, `rc_class_name`, `rc_class_number`, `class_name` | The class. Matched to an RCTC racing class by `rc_class_name` (ignoring case); otherwise an official maps `event_class_id` to a class once. |
| `primary_transponder`, `secondary_transponder` | The two transponder numbers for the event. |
| `brca_number`, `brca_source`, `home_club` | The competitor's BRCA number and home club. |
| `race_day_status` | `NOT_ARRIVED` or `ARRIVED`, kept for information. |
| `age_category`, `grade`, `club_car_requested`, `club_car_name` | Read, not used for scoring. |

## Pictures and car colours

Three optional fields on an entry let RaceHub supply the pictures the boards and the streaming overlay can show (#520, #686, #688). RCTC reads them in the same import as the rest of the entry.

| Field | Meaning | Belongs to |
|-------|---------|------------|
| `headshot_url` | An `https` address of the driver's headshot. | The driver (competitor) |
| `car_photo_url` | An `https` address of a photo of this entry's car. | The entry |
| `car_colours` | Up to four colours of the car, as `#rrggbb`, main colour first. | The entry |

```json
{
  "entry_id": "a1", "entry_version": 4, "entry_status": "CONFIRMED",
  "driver_profile_id": "drv-ada", "event_class_id": "rh-class-buggy",
  "driver_display_name": "Ada Lovelace",
  "headshot_url": "https://media.example.org/drivers/drv-ada.png",
  "car_photo_url": "https://media.example.org/entries/a1/car.jpg",
  "car_colours": ["#e11d48", "#ffffff"]
}
```

### The picture addresses

RCTC downloads each picture itself, from the laptop, and keeps its own copy. What it needs from an address:

| Rule | Why |
|------|-----|
| `https` only, with a host, no user name or password in it, at most 500 characters | Anything else is ignored with a warning in the import preview. |
| The host must resolve to **public** addresses | RCTC sits on the venue network and refuses loopback, private (10.x, 172.16-31.x, 192.168.x), link-local and IPv6 unique-local addresses, so a file cannot point it at something inside the venue. |
| Answer `200` with the picture itself; **redirects are not followed** | A redirect could lead somewhere private. Give the final address. |
| No login, cookie or header is sent | The address must work for anyone who has it. A signed or expiring address is fine, but it must still work when an official asks for the pictures, usually minutes after the import and possibly the evening before the meeting: allow at least 24 hours. |
| **PNG or JPEG**, up to **2 MB**, no longer than 10 seconds to answer | RCTC decides the type from the first bytes of the file, not from the extension or `Content-Type`. SVG, WebP, GIF and HTML are refused. |
| Any pixel size | RCTC decodes and re-encodes every picture, which drops metadata such as a phone's GPS position. About 800 to 1600 pixels on the long side is plenty for the boards; a bigger picture only risks the 2 MB limit. A PNG keeps its transparency. |

A picture that cannot be fetched or is not a usable image is reported to the official as failed and the rest carry on. It never blocks the entries.

### When pictures are fetched

Pictures are never fetched while the import runs. After the entries are saved the import dialog tells the official how many headshots and car photos the file named and offers **Fetch the pictures**, with two options: replace pictures already held, and remove each picture's background (done in the official's browser with the same tool as an upload, #698; the cut-out is saved as a PNG). **Skip the pictures** leaves them for later.

Consequences for RaceHub:

- Links are read only from rows the import creates or updates, so a changed picture must come with a **higher `entry_version`**. A replayed or older row names nothing.
- A driver's headshot is attached to the driver, not the entry. Send the **same `headshot_url` on every entry of that driver**; if two entries disagree the last one fetched wins.
- A picture already held is kept unless the official ticks **Replace**, so an official's own upload is never overwritten by accident.
- An entry that the entry feed applies automatically (an official is not there to ask for the pictures) has its car colours saved but its pictures are not fetched, and a later replay of the same version does not name them again. Until that is built, send changed pictures with a higher `entry_version` and review the file by hand, or ask an official to import it. This is recorded as a follow-up on #699.

### Whose picture RCTC will show

A headshot is personal data. RCTC fetches and shows pictures only for a driver whose name it shows in full: for a driver set to initials only or hidden (#265) the headshot and car photo are skipped, and a public screen never shows them. RaceHub should send a headshot only for a driver who has agreed to it being shown at the venue. RCTC does not keep the address, only its own downloaded copy, and the copy is deleted when it is replaced, removed or the competitor is anonymised. The audit log records the competitor or entry by id, never the address.

### Car colours

- `car_colours` is up to four values, each exactly `#` and six hexadecimal digits (any case; RCTC stores them in lower case). Blanks and repeats are dropped.
- **Absent or `null` leaves RCTC's colours alone.** A list replaces them, and an empty list clears them. So an official's colours are only changed by a file that gives colours.
- More than four, or a value that is not `#rrggbb`, ignores the whole list for that entry and shows a warning in the preview.
- Colours are not personal data, so they are saved by the import itself (no fetch step) and also show for a driver whose name is hidden.

### What the preview and result say

The preview (a dry run) shows a warning for each unusable address or colour list, worded "entry *id*: the headshot address must be an https address…". The real import's answer carries `photoLinks`: one `{ kind, competitorId, entryId, url }` per picture to fetch, which the page sends to `POST /admin/events/{eventId}/photo-import` (ADMIN only). See `docs/api.md`.

## The RC-Timing CSV

RCTC reads the same three things from three optional columns of the driver CSV, for clubs that keep their pictures in a spreadsheet instead of RaceHub:

| Column | Meaning |
|--------|---------|
| `Headshot URL` | As `headshot_url`. |
| `Car Photo URL` | As `car_photo_url`. |
| `Car Colours` | `#rrggbb` values separated by spaces, semicolons or bars (not commas, which RC-Timing files cannot contain), up to four. A blank cell leaves the colours alone. |

The CSV has no versions, so the links and colours are read from every row that is new, every changed row the official picked, and every unchanged row. An address must not contain a comma or a double quote. The rules for the addresses, the fetch step and whose picture is shown are the same as above. The other way to supply photos as files is the bulk import on the Competitors and Entries screens (#698).

## Summary of what RCTC will not do

- It will not call RaceHub to ask for a picture, or send RaceHub's address or token to the host of a picture.
- It will not fetch from a private address, follow a redirect, accept an SVG, or keep an address.
- It will not fetch a picture for a driver whose name is not shown in full.
- It will not clear a picture or colours because a file leaves them out.
- It will not let a picture problem block an import of entries.
