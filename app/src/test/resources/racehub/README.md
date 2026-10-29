# RaceHub Entry Export v1 fixtures

Inputs for `RaceHubImportIT` (L7, #15). `{{run}}` is replaced by a value unique to each test, so
entry ids, driver ids and transponder numbers do not collide between tests sharing one database.
Classes match by `rc_class_name` against racing classes named `RH Buggy {{run}}` and
`RH Truck {{run}}`.

| File | Case |
|------|------|
| `entries-v1-initial.json` | Two confirmed entries and one withdrawn entry never imported. Carries unknown and personal fields, which must be ignored. |
| `entries-v1-update.json` | Same event, later revision: entry 1 at a higher version, entry 2 withdrawn. |
| `entries-v1-stale.json` | Entry 1 at a lower version than `entries-v1-update.json`. |
| `entries-v1-unmapped-class.json` | An entry whose class does not exist in the event. |
| `entries-v1-duplicate-transponder.json` | Two drivers using the same transponder number. |
| `entries-v1-media.json` | Picture links and car colours (#699). Entry 1 is fully valid; entry 2 has an http address and a colour that is not `#rrggbb`, which the import must warn about, not block, so this file is not checked against the schema. |
