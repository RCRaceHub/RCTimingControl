# Personal data in RCTC: data map and retention proposal

> This is an engineering summary of what RCTC stores, checked against the code (#153). It is **not legal advice**. Where an answer is a legal one, the club (or the ICO's guidance for small organisations) has to confirm it.

Each club runs its own copy of RCTC with its own database, even where clubs share a venue. So there is one data controller per installation: the club. RaceHub, and the relay if the live feed is on, are separate organisations that receive some of the data.

## What is held

| Data | Whose | Where | Notes |
|------|-------|-------|-------|
| Name, email, password hash, roles | Officials | `users`, `user_roles` | Officials are the only people with accounts. |
| Sign-in sessions | Officials | `refresh_tokens` | Hashed token, expiry and revoked flag. **Nothing deletes expired or revoked rows today.** |
| Display name, RaceHub driver ID, BRCA number, home club, spoken name | Competitors | `competitors`, `entries` | From RaceHub, the CSV, or typed as a walk-in. No contact, date of birth, guardian or payment data. The CSV `Junior` column is read by the parser but not stored. |
| Transponder numbers | Competitors, per event | `entries`, practice laps | A number can identify a person across events. |
| Laps, positions, results, penalties, incident reports, marshal adjustments | Competitors | race and result tables | Incident reports and penalties describe behaviour. |
| Who did what and when | Officials; anyone who tried to sign in | `audit_log` and the entry, official and competitor audit tables | Append-only. A failed sign-in stores **the email that was typed**, which may belong to someone who is not an official (`LOGIN_FAILED`). |
| Copies of the database | All of the above | Backup folder | The newest 14 are kept by default (`keep`); a person deleted later still exists in older backups. |
| Names sent out | Competitors | Results Export v1 to RaceHub; Live Feed v1 to a relay (display names only, per-event switch) | Received by other organisations. |
| Names shown on screens | Competitors | Boards, overlay, results and championship pages | Anonymous access on the venue network, and to remote viewers when the live feed is on. |
| Application log | Anyone | `logs/rctiming.log` in the data folder | Rolled by Spring Boot's defaults. Contents not yet audited for names and emails. |

## Gaps found so far

1. `refresh_tokens` rows are never purged.
2. Failed sign-in emails are kept for as long as the audit log is, with no limit.
3. The audit log has no retention limit.
4. No way to anonymise or export one competitor.
5. No "initials only" or "hide" option for juniors on boards, the overlay, public pages or the live feed.
6. Log file contents have not been checked for personal data.

## Proposed child tickets

1. Retention job: purge expired and revoked refresh tokens; drop failed-sign-in rows after a set period (suggest 90 days) and keep other audit rows for a longer, admin-visible period.
2. Anonymise a competitor (keep results and standings, remove name, BRCA number, RaceHub ID, spoken name; audit row without the removed data) and export everything held about one person.
3. Failed sign-in: stop storing the typed email, or store a hash.
4. Junior display: initials-only or hidden on every public surface and the live feed.
5. Check the log for personal data and keep it out.
6. Privacy notices: officials at sign-in, drivers at entry (RaceHub terms or the check-in desk), a footer link on public pages, and an admin-editable notice page holding the club's own name and contact.

Open for the club: lawful basis for showing names to remote viewers, whether a record of processing or ICO registration applies, and who answers access and erasure requests.
