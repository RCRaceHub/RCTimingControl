# Reusable integration research prompt

Use this prompt to refresh the public integration plan. Research is not an
instruction to implement, deploy or modify remote issues.

---

Research the current timing application and the intended RaceHub organising
application. Obtain the companion repository location from authorised context;
do not publish private repository locations or implementation evidence.

The product goal is **organise in RaceHub, run in RCTimingControl**. Both products
must remain independent. RaceHub owns meetings, classes, identity and bookings;
RCTC owns local execution rules, preparation, timing and authoritative results.

Read repository guidance, current code/contracts/tests and the existing plan at
`docs/plans/racehub-integration/README.md`. Record exact researched revisions
in local research. Distinguish implemented, partial, proposed and not-found.
Do not assume that a contract described by one product exists in the other.
Do not confuse RCTimingControl with a separate product named RC-Timing.

Investigate:

1. Side-by-side event/class/type models, including catalogue, reusable club
   class and meeting class versus local racing class and rule snapshots.
   Classify fields as import, mapping, local execution or excluded. Audience/
   designation labels are not race formats or championship scoring.
2. Stable provider-scoped event/driver/entry/class IDs, uniqueness, migrations,
   explicit withdrawals, revision propagation and snapshot consistency.
   Identity is not a name, transponder, date or revision counter.
3. One-product standalone/connect-now/connect-later setup. Distinguish pairing,
   per-meeting ownership and connectivity. Include local meetings on a paired
   installation and offline linked operation.
4. Explicit first-use rule mapping, reusable defaults, atomic preview/apply,
   empty classes, source renames and local snapshot preservation. Combined
   racing must not accidentally collapse separate classifications.
5. Organisational lifecycle versus execution, preparation locks, late changes,
   local overrides, walk-ins and reviewed link/unlink/merge transitions.
6. Proposed API matrix: host, caller, method/path, permission, version, payload,
   receipt/error semantics and actual implementation status. Cover discovery,
   consistent manifest/entries, durable results receipt/status, monotonic
   execution acknowledgements and local-only handoff endpoints.
7. Outbound polling/upload/feed from timing; offline bundle parity, conditional
   reads, queue durability, duplicate/conflicting uploads, removed/restarted
   races, included penalties and championship ordering.
8. Optional scoped credentials, rotation/revocation, disconnect/in-flight
   ambiguity, safe resume, backup rollback and explicit publisher takeover.
9. Public projection versus authenticated receipts, visibility, retention,
   erasure/re-import and viewer cache expiry. Keep contact/payment data out
   of timing and private identifiers out of public live standings.
10. Optional live viewing and verified public sharing links as follow-ons.
    Operational feedback must have a defined owner and review/reversal flow,
    not become unrestricted bidirectional synchronisation.

Refresh the public plan's model comparison, endpoint proposals, ownership rules,
work packages C01-C12, dependencies, first-release gate and acceptance criteria.
Record what changed and decisions still requiring approval. Coordinate existing
issues instead of duplicating them; do not modify issues without permission.

Keep detailed private implementation findings in gitignored local research,
not public issue bodies or committed documents. Do not include credentials,
personal data, private repository URLs, infrastructure details or account-specific
setup instructions. Use synthetic fixtures. Mark access limitations honestly;
never bypass content exclusion or infer protected code.

Do not claim runtime compatibility from code inspection or mocks. Finish with
the durable public-safe plan, a per-product change summary and unresolved
decisions. No code changes, commits, PRs or remote writes unless separately
requested.
