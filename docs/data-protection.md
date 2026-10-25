# Personal data in RCTC: data map and retention proposal

> This is an engineering summary of what RCTC stores, checked against the code (#153). It is **not legal advice**. Where an answer is a legal one, the club (or the ICO's guidance for small organisations) has to confirm it.

Each club runs its own copy of RCTC with its own database, even where clubs share a venue. So there is one data controller per installation: the club. RaceHub, and the relay if the live feed is on, are separate organisations that receive some of the data.

## What is held

| Data | Whose | Where | Notes |
|------|-------|-------|-------|
| Name, email, password hash, roles | Officials | `users`, `user_roles` | Officials are the only people with accounts. |
| Sign-in sessions | Officials | `refresh_tokens` | Hashed token, expiry and revoked flag. A nightly clean-up deletes tokens once they have expired (#262), and also runs when the app starts. |
| Display name, RaceHub driver ID, BRCA number, home club, spoken name | Competitors | `competitors`, `entries` | From RaceHub, the CSV, or typed as a walk-in. No contact, date of birth, guardian or payment data. Results saved before competitors existed have no competitor to look up, so they keep their stored name. Merging two competitors keeps the more restrictive setting. The CSV `Junior` column is read by the parser but not stored. |
| Transponder numbers | Competitors, per event | `entries`, practice laps | A number can identify a person across events. |
| Laps, positions, results, penalties, incident reports, marshal adjustments | Competitors | race and result tables | Incident reports and penalties describe behaviour. |
| Who did what and when | Officials; anyone who tried to sign in | `audit_log` and the entry, official and competitor audit tables | Append-only. A failed sign-in no longer stores the email that was typed: an attempt on a real account is tied to the official by id, and an unknown email leaves no trace of what was typed (#264). Backups taken before that change still hold the old addresses until they age out. |
| Copies of the database | All of the above | Backup folder | The newest 14 are kept by default (`keep`); a person deleted later still exists in older backups. |
| Names sent out | Competitors | Results Export v1 to RaceHub; Live Feed v1 to a relay (display names only, per-event switch) | Received by other organisations. |
| Names shown on screens | Competitors | Boards, overlay, results and championship pages | Anonymous access on the venue network, and to remote viewers when the live feed is on. |
| Application log | Anyone | `logs/rctiming.log` in the data folder | Rolled by Spring Boot's defaults. Log calls carry ids, transponder numbers and session titles, never an email, a display name, a spoken name, a BRCA number, a password or a token; `LogContentTest` fails when a log call names one (#266). Request bodies are not logged. |

## Gaps found so far

1. ~~`refresh_tokens` rows are never purged.~~ Fixed in #262.
2. ~~Failed sign-in emails are kept for as long as the audit log is, with no limit.~~ Fixed in #264.
3. ~~The audit log has no retention limit.~~ Fixed in #262: a nightly clean-up deletes refused sign-ins after 90 days and, unless set to 0, other audit rows after two years. The entry, official and competitor audit tables are not covered yet.
4. ~~No way to anonymise or export one competitor.~~ Fixed in #263; see below.
5. ~~No "initials only" or "hide" option for juniors on boards, the overlay, public pages or the live feed.~~ Fixed in #265; see below.
6. ~~Log file contents have not been checked for personal data.~~ Checked, and guarded by `LogContentTest` (#266).

## Export and anonymise one competitor (#263)

An admin can, from the Competitors screen, download everything held about one competitor as JSON, and anonymise them. They are two separate actions: a person may ask for a copy, for erasure, or both, and an anonymise cannot be undone, so download first if a copy was asked for. Both are admin only and both are audited: `COMPETITOR_EXPORTED` and `COMPETITOR_ANONYMISED`, with who did it and for which competitor id, and no name or other removed detail.

**Anonymise removes** the display name (it becomes `Anonymised competitor #id`), BRCA number, RaceHub driver ID and link, home club, spoken name and category tags (Junior, Vet and the like); the name inside every stored result snapshot and every queued or sent RaceHub export in RCTC's own database; and the details of the competitor's change history, their entries' history and the audit rows about them (the rows stay, reading "Details removed", so the log still shows something happened). Laps, results, positions and championship points stay, shown under the new name.

**It does not remove:**
- Free text an official typed: incident reports, penalty reasons, marshal notes. An admin should read these and edit them if they name the person.
- A held RaceHub document waiting to be applied.
- The export file includes officials' notes and names, which may mention other people; read it before handing it over.
- Copies already sent to RaceHub (their results export) or to the live feed relay, and older backups. Those are other organisations' copies, or older snapshots, and have to be dealt with there.

Audit rows are found by the competitor and entry they are about, and by their old name appearing in the row's text, so penalty, incident, exclusion and practice rows that name them are blanked too. The RaceHub driver id is removed from queued and sent export payloads in RCTC's database as well.

**Re-imports can undo it.** The entries keep their RaceHub entry ids, so importing a newer version of the same booking attaches the entry to a new competitor with the real name. Remove or amend the booking in RaceHub first. Finished races held in memory, boards or announcer clips refresh on their own; anonymising is refused while the competitor is in a race that is on the grid, running or stopped.

## Privacy notice (#267)

The club's privacy notice is at `/privacy`, open to anyone. It is linked from the events, results and championship pages, the About page, and the sign-in page. The boards and the OBS overlay carry no link, since they are screens and not pages people read.

Until the club writes its own, the page shows a standard notice with the club's name, email and phone filled in from the club profile. It says what RCTC holds, why, who sees it (including the junior display setting), how long results are kept, and how to ask for a copy or removal. An admin edits it under Club, "Privacy notice"; saving a blank text goes back to the standard one. Saving the standard text after editing it makes it the club's own text, so its name and contact details stop following the club profile; use "Use the standard text" to go back. Changes are audited as `PRIVACY_NOTICE_CHANGED` with whether it is the club's own text and its length, not the text. The standard text is a starting point, not legal advice: the club should read it and change what does not fit.

RaceHub holds the booking, contact and payment details and has its own notice; this one covers only what the timing program holds.

## Junior display: initials or hidden (#265)

An admin or race director can set any competitor to show as **Full name**, **Initials only** (`A. L.`) or **Hidden** (`Driver 12`, from the car number) on the Competitors screen. The choice is stored on the competitor, is audited (`COMPETITOR_NAME_VISIBILITY_CHANGED`, with the competitor id and the two settings, not the name), and a RaceHub or CSV import never changes it. It applies to the public results page, the boards and overlay, championship standings, live timing sent to browsers and the live feed. The stored results keep the real name, so setting it back to Full name changes the screens straight away.

A change made during a race reaches the live timing at once. Race control's live timing panel shows the public name too, as it shares the stream the public screens use; the other official screens (the competitor lists, the grid) and the announcer use the real name, so an announcer will say a junior's name aloud at the venue; give them a spoken name or ask them to skip it if that matters. The CSV `Junior` column is still only shown in the import preview; it does not set this. Results sent to RaceHub carry the real name, as before.

## Headshots, car photos and driver sponsors (#520)

An official can add three optional extras for the boards and the commentator view: a **headshot** and the driver's own **sponsors** (Admin, Competitors, Photo…), and a **car photo** for one entry (the event's Entries tab, Car photo). A headshot is a picture of a person and so personal data; a car photo can show the driver, so the screen asks for the driver to be kept out of it, and the driver sponsors are just names.

- Nothing is requested from drivers or RaceHub: RCTC only holds what an official adds, and the screen says to add a headshot only with the driver's agreement.
- Only PNG, JPEG and WebP up to 2 MB are accepted, never SVG. Each file is stored with a random name in the app's storage folder, so its address cannot be guessed, but anyone who has the address can open it (storage is served to the venue network without sign-in, like the club logo).
- A public screen never gets a competitor's headshot, car photo or sponsors while their name is set to initials only or hidden (#265).
- Replacing or removing a picture deletes the file. **Anonymise** deletes the headshot file, the sponsors and the car photos of all the competitor's entries; merging two competitors deletes the duplicate's. Older database backups and the storage folder in a file-level backup can still hold earlier copies, as with names.
- Every change is audited (`DRIVER_HEADSHOT_CHANGED`, `DRIVER_HEADSHOT_REMOVED`, `DRIVER_SPONSORS_CHANGED`, `ENTRY_CAR_PHOTO_CHANGED`, `ENTRY_CAR_PHOTO_REMOVED`) by competitor or entry id, with no name.
- Not sent to the live feed relay or RaceHub.

## Proposed child tickets

1. Retention job: purge expired and revoked refresh tokens; drop failed-sign-in rows after a set period (suggest 90 days) and keep other audit rows for a longer, admin-visible period.
2. Anonymise a competitor (keep results and standings, remove name, BRCA number, RaceHub ID, spoken name; audit row without the removed data) and export everything held about one person.
3. Failed sign-in: stop storing the typed email, or store a hash.
4. Junior display: initials-only or hidden on every public surface and the live feed.
5. Check the log for personal data and keep it out.
6. Privacy notices: officials at sign-in, drivers at entry (RaceHub terms), a footer link on public pages, and an admin-editable notice page holding the club's own name and contact.

Open for the club: lawful basis for showing names to remote viewers, whether a record of processing or ICO registration applies, and who answers access and erasure requests.
