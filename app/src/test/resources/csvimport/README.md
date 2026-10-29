# RC-Timing CSV fixtures

Inputs for `CsvImportIT` (#39). `{{run}}` is replaced by a value unique to each test, so BRCA
numbers, names and transponder numbers don't collide between tests sharing one database. Classes
match by name against racing classes named `CSV Buggy {{run}}`, `CSV Truck {{run}}` and
`CSV 4WD {{run}}`, created in that order.

| File | Case |
|------|------|
| `initial.csv` | The full RC-Timing 2025 header. A driver in two classes (Alan), a driver with BRCA number 0 (Grace, matched by name), a junior (Katherine), and an `update` row (Tim) that reuses Ada's transponder. |
| `changed.csv` | Ada's transponder changes, Grace gains a second transponder, Alan is renamed. |
| `missing.csv` | Katherine is left out. Columns in another order, and only some of them. |
| `duplicate-transponder.csv` | Two booked drivers with the same transponder. |
| `unmapped-class.csv` | A class the event doesn't have. |
| `class-numbers.csv` | Classes given only by Class Number (the event's first and second class). |
| `class-names.csv` | The same drivers as `class-numbers.csv`, with their classes given by name instead. |
| `bad-names.csv` | A quoted name with a comma, and an unquoted name with a comma. |
| `pictures.csv` | The `Headshot URL`, `Car Photo URL` and `Car Colours` columns (#699). Row 2 has an http address and a colour that is not `#rrggbb`, which are warned about and ignored. |
