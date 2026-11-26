# Importing from RaceHub: how it works end to end

This is the full guide to the RCTC side of the RaceHub link. It joins up three pages that each cover a part: [what RCTC expects from RaceHub](racehub-expectations.md) (every field and what RCTC does with it), [Results Export v1](results-export-v1.md) (what RCTC sends back) and `docs/api.md` (RCTC's own admin endpoints). The RaceHub side is in RaceHub's `docs/rctc/API.md`; the machine-readable contract for it is [racehub-api/openapi.yaml](racehub-api/openapi.yaml) (OpenAPI 3.1, kept in step with the schemas in `app/src/main/resources/racehub/` and `resultsexport/`).

RCTC only ever calls out to RaceHub. It never needs an address of its own on the internet.

## The pieces

| Step | RCTC | RaceHub call |
|---|---|---|
| Save RaceHub's address and the club's key | **RaceHub** page (the same address field also shows in **Find RaceHub events** and on the classes screen and the setup wizard step), or `rctiming.racehub.url` and `rctiming.racehub.token` in the settings file. An admin-saved address or key overrides the file. The address is set once and used for results, events, classes, formats and imports. The key is stored encrypted and never shown back. | none |
| Link classes once per club | **RaceHub → Classes and formats** tab, `GET /admin/racehub/classes` | `GET /api/rctc/v1/classes` |
| Link race formats once per club | same page, `GET /admin/racehub/formats` | `GET /api/rctc/v1/formats` |
| Find a meeting | **Find RaceHub events**, `GET /admin/racehub/events` | `GET /api/rctc/v1/events?state=ready` |
| Pull entries | entry feed (automatic every 5 minutes, or **Fetch now**), or **Import entries from RaceHub** by file | `GET /api/rctc/v1/events/{id}/entries` |
| Send results | results outbox, queued on finish, correction and day close | `POST /api/rctc/v1/results` |

## What an entry import does

1. The file or feed is checked against `entry-export-v1.schema.json`.
2. Entries are matched by `entry_id`; a higher `entry_version` wins; `WITHDRAWN` withdraws and never deletes.
3. Each `club_class_id` must have a club link to an RCTC racing class. A file with an unlinked class is **held whole** for an official; nothing is saved and nothing is placed by name.
4. Drivers become **competitors** (matched by RaceHub driver id), entries carry the primary and secondary transponder.
5. The preview (dry run) shows what will change. The official applies it. Entry feed revisions that import cleanly are applied automatically.

## Headshots, car photos and car colours

Verified against the code on `main` (PR #702, issue #699):

| Question | Answer | Where |
|---|---|---|
| Does RCTC read the three fields? | Yes. `headshot_url`, `car_photo_url`, `car_colours` are in the schema and the parser, for the RaceHub import and the CSV import. | `RaceHubEntryExport`, `RaceHubImportService` (`mediaImport.apply`/`check`), `RcTimingCsvParser` |
| Is the picture downloaded and saved, or just linked? | **Downloaded and saved.** The import only records the link (`photoLinks` in its answer). The official then presses **Fetch the pictures**, which calls `POST /admin/events/{id}/photo-import`; `PhotoFetcher` downloads the bytes, `DriverMediaService` decodes and re-encodes them (PNG or JPEG, up to 2 MB, metadata dropped) and saves them to RCTC's own storage folder under a random name. RCTC stores its own address, **not** RaceHub's. | `PhotoImportService`, `PhotoFetcher`, `DriverMediaService.importHeadshot/importCarPhoto` |
| Colours | Saved by the import itself (no fetch step). | `EntryMediaImport` |
| Safety | https only, public hosts only, no redirects, 10 s and 20 s limits, never fetched for a driver whose name is not shown in full (#265), existing pictures kept unless **Replace** is ticked. | `PhotoLink.problem`, `PhotoFetcher.isPublic` |

### What RaceHub sends

RaceHub now fills `headshot_url` and `car_photo_url` (RaceHub PR #399, not yet merged or tested end to end as of 2026-11-24), but only for a driver who opted in: their **Photo visibility** is Event media / commentators or Public (Private, the default, sends nothing, and the field is left out so RCTC keeps what it has).

- The links are signed, last 12 hours and need no RaceHub login, so RCTC's fetch works as it is. RCTC fetches right after the import, well inside that time. A link older than 12 hours (an import left unfinished overnight) fails with a 403; import again to get fresh links.
- RaceHub re-checks consent on every download, so a driver who withdraws consent stops the next fetch.
- `car_photo_url` is the photo of the paint scheme on the car the entry uses.
- **RaceHub sends no `car_colours`.** It holds a photo of the paintwork, not colour values, so RCTC's colours stay as an official set them.

### Remaining gaps

1. **The automatic feed only fills missing pictures.** When it applies a revision it now fetches the pictures that revision names straight away (#811), but never replaces a picture already held, so a headshot changed later in RaceHub does not refresh by itself, and a picture an official uploaded is safe. If some cannot be fetched, the feed says how many; import the file by hand to retry or to replace.
2. **No colours from RaceHub** (above). Colours still come from the CSV import or an official.
3. The pictures have not yet been tried with a real RaceHub export; the tests use fixture files (`entries-v1-media.json`).

## Versions

RCTC currently calls `/api/rctc/v1` only, with the version in the saved address. The agreed policy is in RaceHub's `docs/rctc/VERSIONING.md`: at most two major versions live at once, deprecated versions answer with `Deprecation` and `Sunset` headers for at least six months, then `410`. RCTC's side of it:

- RCTC will support the current and previous versions together, and use the highest one both sides list (from `GET /api/rctc/versions`).
- A deprecation or sunset date shows as a warning on **RaceHub** (Connection and Classes and formats), once in the audit log, never as a blocking error.
- A `410` leaves the results queue and the entries untouched; sending resumes after the club upgrades.
- A new optional field in a document is not a version change, and RCTC ignores fields it does not know.

**Not built yet** in RCTC: the version check and the warning. Today nothing in RCTC reads the headers.
