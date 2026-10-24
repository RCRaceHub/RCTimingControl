# RaceHub integration: organise once, run locally

Status: proposed roadmap, not implemented behaviour.

The whole-system view, decisions made since this plan and the added packages C13 to C17 are in the [design](design.md).

Planning baseline: 19 October 2026.

This is the durable, public-safe product plan. It contains the requirements,
contract proposals and work packages used by the milestone and its issues.
Private implementation research is deliberately excluded. Do not infer another
application's current implementation from this target design.

## Product boundary

**RaceHub organises the meeting; RCTimingControl runs it.**

RaceHub owns event organisation, class definitions, racer identity, bookings and
entries. RCTimingControl (RCTC) creates a local execution copy, assigns its own
race rules, generates the run order, operates timing and publishes results back.
Officials should not recreate the same meeting and classes in both products.

Both products remain independent. RCTC must run a full meeting without RaceHub
or internet access. RaceHub's booking workflows must not require a paired timing
installation. No shared database, mandatory shared human login, or remotely
issued race-control commands are proposed.

## Setup and meeting ownership

Ship one application, not separate standalone and integrated editions.

| Setup choice | Experience |
|---|---|
| Use independently | Configure the local club, track, decoder, officials and rules; create local meetings or import files. |
| Connect to RaceHub | Complete the same local essentials, then pair with an authorised organisation and select meetings. |
| Connect later | Finish local setup now; connect from integration settings without reinstalling or resetting data. |

A paired installation can still run local meetings. A linked meeting remains
linked during an outage; connectivity does not determine organisational ownership.
Mark source-owned fields "Managed in RaceHub". Keep decoder, track execution
configuration, rules, audio, officials, backups and local privacy operations local.

Disconnect pauses polling, queued result delivery and live publishing without
deleting data. An in-flight upload may already have reached the receiver; show
and reconcile that uncertainty. Resume verifies the destination and authorised
publisher before releasing queued data.

Making a linked meeting local or linking an existing local meeting is an explicit
reviewed transition, not a name/date match. Preserve identity and history.
Replacing a laptop requires an explicit publisher takeover.

## Model alignment and import recommendation

The terms below describe target concepts, not a requirement for identical database
models on both sides.

| Organising concept | Timing concept | Policy |
|---|---|---|
| Organisation | Local club installation | Pair to an authorised organisation; do not import account membership. |
| Venue | Configured local track | Map explicitly; a location does not configure decoder loops or lap thresholds. |
| Event/meeting | Local execution event | Import identity, name, local date and agreed schedule metadata; keep execution state separate. |
| Shared class catalogue | Optional descriptive context | Discipline, scale and catalogue identity may aid suggestions, not choose timing rules. |
| Reusable club class | Local `RacingClass` association | Remember a reviewed identity mapping and separate default format selection. |
| Class in this meeting | Local `EventClass` | Preserve source identity, select local rules and copy a format snapshot. Include empty classes. |
| Booking capacity/price | No equivalent execution rule | Booking capacity is not heat size; financial data stays outside timing. |
| Audience/designation | Optional meeting metadata | Local/national or championship/series labels do not determine race formats or points. |
| No organising rule requirement | `RaceFormatTemplate` and class snapshot | Rule content remains local. Never silently update prepared meetings when templates change. |
| No organising race-control requirement | Rounds, grids, races and corrections | Generated/operated by RCTC; publish projections/results outward. |

RCTC format choices include timed, bump-up, points-finals and Shikabane.
Practice/qualifier/final describes a round's purpose, not the meeting's audience.
There is no proposed automatic conversion from an organising label into either.
A reusable whole-meeting execution preset would be separate future design, not
an assumption of this handoff.

First-use class import asks the official to map or create a local racing class
and choose a local format. Later meetings suggest established defaults.
Preserve separate sporting classes when racing them together through local
combined-race grouping. Collapsing two source classes into one classification
requires explicit review; it is not equivalent to sharing a heat.

A source rename preserves identity. Do not blindly rename a shared local class
and thereby change unrelated meetings. Removal with race history is held or
retired, not destructive. Optional labels/colours need deliberate presentation
support, not assumed existing fields.

## Identity and ownership rules

Keep each application's internal primary keys. Link them with stable external
references scoped by an immutable provider identity and authorised organisation.
Do not use URL, editable slug, name, transponder, optional membership number or
event date as identity.

| Reference | Purpose |
|---|---|
| Provider + event ID | One organised meeting mapped to a local execution event. |
| Provider + driver-profile ID | One racer mapped to a competitor, independent of login/purchaser. |
| Provider + entry ID | One entry, separate from driver identity; survives withdrawal. |
| Provider + reusable club-class ID | Repeatable local class/default-rule association. |
| Provider + event-class ID | Class membership and result provenance for one meeting. |
| Authorised installation/generation + race ID | Execution/results provenance and safe publisher replacement. |

Stable string representations of provider-scoped numeric IDs can work; UUIDs
are an alternative, not a requirement to replace every primary key. Persist
unique mappings, validate parent relationships and distinguish immutable identity
from monotonic content revision. Define provider clone/recovery semantics.

Walk-ins remain local participants without fabricated booking identities.
Linking a walk-in later is reviewed. Merges and erasure must not silently retarget
source identities or allow re-import to restore removed personal data.

For linked meetings, normal event/class/profile/booking edits belong in RaceHub.
Do not delete standalone features or local tables: enforce source ownership in
backend services as well as UI, while retaining explicitly audited local race-day
exceptions, transponder swaps, pronunciation/visibility overrides and privacy
operations.

## Proposed API surfaces

Paths are illustrative, not existing APIs or agreed final routes. RCTC initiates
all cross-product requests. Credentials are scoped to organisation/event and
capability; the receiver validates ownership rather than trusting payload IDs.

| Host | Method/path | Purpose |
|---|---|---|
| RaceHub | `GET /api/integration/v1/capabilities` | Discover supported schema versions/capabilities. |
| RaceHub | `GET /api/integration/v1/events` | Discover authorised meetings with pagination/date filters. |
| RaceHub | `GET /api/integration/v1/events/{id}/manifest` | Meeting details and complete class catalogue. |
| RaceHub | `GET /api/integration/v1/events/{id}/entries` | Versioned entry snapshot and explicit withdrawals. |
| RaceHub | `POST /api/integration/v1/events/{id}/results` | Durably accept a full authoritative results snapshot. |
| RaceHub | `GET /api/integration/v1/events/{id}/results/status` | Reconcile durable receipt versus public publication. |
| RaceHub | `PUT /api/integration/v1/events/{id}/execution-status` | Accepted revisions, readiness, held changes and last reported execution state. |
| RCTC, local only | `POST /api/v1/admin/meeting-import/preview` | Preview setup, mapping and entries for a local official. |
| RCTC, local only | `POST /api/v1/admin/meeting-import/apply` | Confirm validated initial setup atomically. |
| RCTC, local only | `POST /api/v1/admin/events/{id}/integration/refresh` | Fetch and apply/hold changes under local lifecycle rules. |
| RCTC, local only | `GET /api/v1/admin/events/{id}/integration` | Show association, freshness, held changes and delivery state. |

Credential pairing, rotation, revocation and publisher takeover need explicit
control-plane contracts. A downloadable consistent meeting bundle provides offline
parity. Local endpoints are not public callbacks for RaceHub to call.

Use a separately versioned meeting manifest alongside
[Entry Export v1](../../api.md#admin--racehub-import),
[Results Export v1](../../results-export-v1.md) and
[Live Feed v1](../../live-feed-v1.md). Do not invent a second competing entry shape.

## Synchronisation and failure semantics

1. Select an authorised meeting; fetch a consistent manifest/entry snapshot or
   open an offline bundle. Include every class even when it has no entries.
2. Resolve track/class/rule mappings; preview and confirm setup before generating
   runnable classes. Report readiness only when requirements are resolved.
3. Poll versioned snapshots. Conditional GET is an optimisation, not the
   correctness mechanism. Version every affected entry when exported profile,
   arrival, class or entry-transponder snapshot data changes.
4. Apply safe changes; hold grid-affecting changes after preparation for an
   official. A missing row is not a withdrawal. Cancellation is a notice, not a
   remote stop command. Acknowledgements distinguish fetched from accepted data.
5. Queue result snapshots locally and send whenever reachable. No network call
   may block timing or run inside a local write transaction.

Results replace the accepted event projection, including removing races omitted
from a newer authoritative snapshot. Same revision/content is a safe retry;
older revisions cannot regress state; same revision/different content is an
explicit conflict. Return success only after durable receipt. The current sender
treats any `2xx` as delivered, so asynchronous acceptance needs a durable inbox.

Keep event results and championship ordering separate. Do not apply included
penalties twice or independently re-score imported results. Agree handling of
legacy outstanding penalties, abandoned races, ties and combined classes.

Show "last reported" and stale/offline states. Arrival is advisory, not a new
cloud-dependent admission gate. Results exports and public views are different
privacy projections: real-name authenticated receipts must not be served directly
as public payloads. Define retention, visibility and erasure handling before
public publication.

## Delivery and issue index

Milestone: [RaceHub integration: organise once, run locally](https://github.com/RCRaceHub/RCTimingControl/milestone/1).

Parent tracker: [#384](https://github.com/RCRaceHub/RCTimingControl/issues/384).

Documentation change: [#385](https://github.com/RCRaceHub/RCTimingControl/pull/385).

| Package | Child issue | Delivery |
|---|---|---|
| C01 | [#386](https://github.com/RCRaceHub/RCTimingControl/issues/386) | Ownership and versioned contracts |
| C02 | [#387](https://github.com/RCRaceHub/RCTimingControl/issues/387) | Stable identities and publisher recovery |
| C03 | [#388](https://github.com/RCRaceHub/RCTimingControl/issues/388) | Optional pairing and standalone setup |
| C04 | [#389](https://github.com/RCRaceHub/RCTimingControl/issues/389) | Meeting/class import and local rules |
| C05 | [#390](https://github.com/RCRaceHub/RCTimingControl/issues/390) | Companion meeting/entry exports |
| C06 | [#391](https://github.com/RCRaceHub/RCTimingControl/issues/391) | Safe refresh and acknowledgements |
| C07 | [#392](https://github.com/RCRaceHub/RCTimingControl/issues/392) | Timing result publication |
| C08 | [#393](https://github.com/RCRaceHub/RCTimingControl/issues/393) | Companion results receipt/publication |
| C09 | [#394](https://github.com/RCRaceHub/RCTimingControl/issues/394) | First integrated release acceptance |
| C10 | [#395](https://github.com/RCRaceHub/RCTimingControl/issues/395) | Optional public live viewing |
| C11 | [#396](https://github.com/RCRaceHub/RCTimingControl/issues/396) | Verified public sharing links |
| C12 | [#397](https://github.com/RCRaceHub/RCTimingControl/issues/397) | Reviewed operational feedback |
| C13 | [#471](https://github.com/RCRaceHub/RCTimingControl/issues/471) | Contract files, tests and mock RaceHub (see [design](design.md)) |
| C14 | [#472](https://github.com/RCRaceHub/RCTimingControl/issues/472) | Guarantee that race history is never lost |
| C15 | [#473](https://github.com/RCRaceHub/RCTimingControl/issues/473) | Off-site backups: sync-folder guide, then uploaders |
| C16 | [#474](https://github.com/RCRaceHub/RCTimingControl/issues/474) | Local network name; local HTTPS deferred |
| C17 | [#475](https://github.com/RCRaceHub/RCTimingControl/issues/475) | Passkeys for officials, deferred |

The milestone contains a parent tracker and the following child work packages.
All cards initially live in the timing repository. "Companion work" identifies
organising-side delivery that may later move to a separately linked issue.
Transfer permissions and destination support must be checked at that time;
milestones/dependencies are not assumed to migrate automatically.

The first integrated release requires C01-C09. Live viewing/sharing (C10-C11)
and selected operational feedback (C12) follow, within the full roadmap milestone.
Independent packages can proceed together only after their stated prerequisites.

### C01: Agree integration ownership and versioned contracts

Delivery: joint product/contract design. Prerequisites: none.

- Define allowlisted meeting manifest/offline bundle, reuse entry/results/feed
  contracts, and document schema evolution and compatibility fixtures.
- Agree identity, status mapping, cancellation, preparation locks, race-day
  exceptions, scoring authority, privacy and recovery policy.
- Specify capability scopes, receipt/error semantics and consistent revisions.

Acceptance:
- [ ] Both sides' responsibilities and unresolved decisions are explicit.
- [ ] Synthetic examples cover empty classes, withdrawals, walk-ins and corrections.
- [ ] Contract review resolves mandatory first-release decisions; follow-ons stay separate.
- [ ] No production deployment or private implementation details are required in public docs.

### C02: Persist stable source identities and publisher recovery

Delivery: timing, with companion identity guarantees. Prerequisites: C01.

- Persist provider/event/driver/entry/class associations with unique constraints
  and parent ownership validation; migrate existing external references safely.
- Separate reusable class identity from default rule selection.
- Define explicit publisher takeover, backup rollback and cloned-provider handling.

Acceptance:
- [ ] Replay does not duplicate an event, class, competitor or entry.
- [ ] Equal numeric IDs from different providers do not collide.
- [ ] Walk-ins, reviewed links, merges and privacy operations preserve invariants.
- [ ] Two publishers or restored revisions cannot silently overwrite accepted results.

### C03: Add optional pairing and standalone/connect-later setup

Delivery: timing UX plus companion credential/discovery API. Prerequisites: C01, C02.

- One installation supports standalone, connect-now and connect-later setup.
- Add scoped pairing, authorised event discovery and visible connection health.
- Keep local meetings available; implement disconnect/resume and explicit
  local/linked ownership transitions with audit and backend enforcement.

Acceptance:
- [ ] No integration credential is required for a local race day.
- [ ] Pairing preserves local rules, officials, decoder, audio and backups.
- [ ] Disconnect pauses queued sending; in-flight ambiguity is reported/reconciled.
- [ ] Resume verifies provider, organisation and publisher before releasing work.
- [ ] Wrong-scope and revoked credentials fail explicitly without blocking timing.

### C04: Import meeting/classes and resolve local rule snapshots

Delivery: timing handoff UI/domain workflow. Prerequisites: C01, C02.

- Add Open RaceHub meeting and offline-bundle preview/apply.
- Create the event and complete class list without duplicate data entry.
- Resolve venue/track and first-use class/rule mappings; reuse explicit defaults.
- Use existing import/domain services; preserve source labels and class provenance.

Acceptance:
- [ ] Complete setup validates before atomic apply; unresolved classes are not runnable.
- [ ] Empty classes remain represented without generating empty races.
- [ ] Each class receives its own rule snapshot; template edits do not alter it.
- [ ] Renames retain identity and do not rename unrelated local meeting history.
- [ ] Combined racing keeps distinct sporting classifications.

### C05: Provide organising-side meeting and versioned entry exports

Delivery: companion organising application; initially tracked here. Prerequisites: C01.

- Supply authenticated manifest/entry reads plus a consistent offline bundle.
- Include event and reusable class identities, full class list and entry snapshots.
- Add monotonic revisions, explicit withdrawals and dependency-change propagation.
- Preserve other standalone export/booking workflows.

Acceptance:
- [ ] Export passes shared schemas and synthetic compatibility fixtures.
- [ ] Profile-only, arrival-only and transponder-snapshot changes reach timing.
- [ ] Withdrawal/reactivation is explicit; omissions do not delete entrants.
- [ ] Organisational cancellation is distinguishable from booking closure.
- [ ] Reads enforce organisation/event scope and exclude financial/contact data.

### C06: Refresh linked meetings safely and acknowledge accepted changes

Delivery: timing refresh/review plus companion acknowledgement surface.
Prerequisites: C03, C04, C05.

- Poll consistent snapshots; support unchanged/stale responses intentionally.
- Show safe, held and rejected changes, source-owned fields and freshness.
- Define local grid/preparation locks and audited on-the-day exception semantics.
- Report monotonic accepted revisions/readiness without remote race commands.

Acceptance:
- [ ] Late class/grid-affecting changes wait for an official.
- [ ] Local swaps and explicit overrides survive newer entry snapshots.
- [ ] Cancellation is visible but cannot remotely stop an active race.
- [ ] Outages preserve ownership and local operation; delayed status cannot regress readiness.
- [ ] Permissions/ownership checks apply to backend endpoints as well as UI.

### C07: Harden timing result publication and recovery

Delivery: timing results pipeline. Prerequisites: C01, C02.

- Reuse the durable outbox and full-event export; cover every mutation that
  invalidates published results, including removal/restart and supported corrections.
- Define explicit publish/re-publish and manual-download revision semantics.
- Bind queued uploads to the intended provider/event/publisher; implement pause
  and recovery without retargeting old work.

Acceptance:
- [ ] Finish, correction and close converge on authoritative event results.
- [ ] Crash/retry, superseding snapshots and destination changes do not lose or misroute data.
- [ ] Removed/restarted races no longer remain authoritative externally.
- [ ] Recovery conflicts are visible and actionable; no timing dependency on receipt.
- [ ] Championship ordering and legacy penalty semantics match agreed contracts.

### C08: Receive and publish authoritative results on the organising side

Delivery: companion organising application; initially tracked here.
Prerequisites: C01, C02, C05.

- Add durable authenticated results receipt, idempotency/content-conflict handling,
  event projection replacement and receipt/publication status.
- Validate event/class/driver relationships; retain legitimate local walk-ins.
- Add organiser/public views with explicit visibility and retention policy.

Acceptance:
- [ ] No `2xx` precedes durable receipt; a lost reply is safely retried.
- [ ] Older snapshots cannot regress results; conflicting same revisions are refused visibly.
- [ ] A new snapshot removes obsolete races; included penalties are not applied twice.
- [ ] Public data is filtered separately from authenticated real-name receipts.
- [ ] Cross-event championship uploads do not regress authoritative standings.

### C09: Prove the first integrated release end to end

Delivery: joint acceptance and user-facing documentation.
Prerequisites: C03, C04, C05, C06, C07, C08.

- Demonstrate organise-once, local preparation/running and corrected results return.
- Exercise standalone modes, consistent offline bundle, outages, retries,
  scope failures, recovery, ownership transitions and privacy.
- Update actual user guides and developer diagrams as behaviour is implemented.

Acceptance:
- [ ] Real implementations complete a synthetic meeting round trip, not only mocks.
- [ ] Both products remain independently usable.
- [ ] No duplicate event/class entry or mandatory internet dependency remains.
- [ ] Restore/takeover, disconnect/in-flight work and late-change review are demonstrated.
- [ ] Contract fixtures and appropriate regression coverage run with normal project checks.

### C10: Add optional integrated public live viewing

Delivery: companion receiver/viewer plus timing publisher integration.
Prerequisites: C03, C06, C08, C09.

- Reuse outbound full-state feed; establish authorised event/stream routing.
- Add current-state viewing, reconnect/session ordering, cache expiry and staleness.
- Preserve per-event opt-in, public-name filtering and local timing isolation.

Acceptance:
- [ ] Late viewers receive current state; outages never appear falsely live.
- [ ] Disable, restart, takeover and event end invalidate cached state correctly.
- [ ] Private identity fields are not added to public standings to solve routing.
- [ ] Viewer load or receiver failure cannot delay local timing.

### C11: Share verified public results and standings links

Delivery: timing print/share UX plus companion stable public links.
Prerequisites: C08, C09.

- Resolve verified event/standings destinations and distinguish receipt from publication.
- Offer local versus online link selection with a safe local fallback.
- Coordinate with existing QR investigation #316; do not create a parallel implementation.

Acceptance:
- [ ] Printed/shared links resolve to the correct published event/class.
- [ ] Unpublished or unlinked meetings never receive invented online URLs.
- [ ] Local-only operation keeps working; stale/unavailable publication is visible.
- [ ] Existing #316 is linked and its remaining scope reconciled.

### C12: Add selected reviewed operational feedback

Delivery: joint follow-on design and bounded implementation.
Prerequisites: C06, C09.

- Design reviewed walk-in/profile association, optional transponder-change
  proposals and profile pronunciation defaults with local override precedence.
- Specify each field's owner, consent/visibility policy and rejection/reversal flow.
- Reuse #121 for pronunciation; NFC/check-in research remains separate unless
  explicitly agreed. This card is not a general bidirectional-sync engine.

Acceptance:
- [ ] Each selected workflow has its own approved contract and acceptance criteria before code.
- [ ] No name-only identity matching or silent garage/booking mutation.
- [ ] Local override wins; rejected feedback does not roll back sporting history.
- [ ] Offline operation and erasure/re-import policy are preserved.

## Existing work and boundaries

Coordinate rather than duplicate:

- #149: broader whole-meeting import investigation; this plan supplies the focused
  linked-meeting use case, not arbitrary historical timing-system migration.
- #97: result-history/retention agreement and real receiver interoperability.
- #316: online public links and QR fallback.
- #121: optional pronunciation default and local override.
- #181: exported penalty attribution needs an explicit privacy decision, not
  automatic inclusion of official identity.

No existing issue is silently closed, moved into the milestone or expanded by
this plan. Reconcile overlaps when the corresponding work package starts.

## Refreshing the plan

Use [the reusable research prompt](research-prompt.md). Update this plan and the
corresponding issue acceptance criteria together when decisions change. Keep
private code findings, account/service details and deployment runbooks out of
the public documents/issues.
