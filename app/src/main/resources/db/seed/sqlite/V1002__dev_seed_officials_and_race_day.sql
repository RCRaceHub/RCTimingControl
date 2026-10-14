-- V1002: Dev seed — officials and a race day of competitors.
--
-- Passwords: admin1@example.com / Admin1Pass!  |  director@example.com / Racer1Pass!

-- ── Admin account ────────────────────────────────────────────────────────────
insert into users (email, password_hash, first_name, last_name)
values ('admin1@example.com', '$2b$10$xEdd1B0miFakxIY2TUpCbOzdjzMluN1h0QsGw2zGspn7yTyYmeBwa', 'Admin', 'One');

insert into user_roles (user_id, role)
select id, 'ADMIN' from users where email = 'admin1@example.com';

-- ── Race director account ────────────────────────────────────────────────────
insert into users (email, password_hash, first_name, last_name)
values ('director@example.com', '$2b$10$QWNPqLhXElyx9PhFCXKZsOWMudKPFrvHBdP.wnQa92WYoP5Trg9oe', 'Race', 'Director');

insert into user_roles (user_id, role)
select id, 'RACE_DIRECTOR' from users where email = 'director@example.com';

insert into user_roles (user_id, role)
select id, 'REFEREE' from users where email = 'director@example.com';

-- ── Club profile ─────────────────────────────────────────────────────────────
insert into club_profiles (name, email, timezone)
values ('Glasgow RC', 'info@glasgowrc.example.com', 'Europe/London');

-- ── Event ────────────────────────────────────────────────────────────────────
insert into events (name, event_date, status, track_id)
values ('Club Championship Round 1', date('now'), 'IN_PROGRESS',
        (select id from tracks where name = 'Main Circuit'));

-- ── Event class (Mod Buggy, Standard Timed — 5 min) ─────────────────────────
insert into event_classes (event_id, config_snapshot, template_id, racing_class_id, finals_count, cars_per_final, bump_count)
select
    (select id from events where name = 'Club Championship Round 1'),
    t.config,
    t.id,
    (select id from racing_classes where name = 'Mod Buggy'),
    1, 8, 0
from race_format_templates t
where t.name = 'Standard Timed — 5 min';

-- ── Competitors (six walk-in drivers; racers have no accounts) ───────────────
insert into competitors (display_name) values
    ('Racer One'),
    ('Racer Two'),
    ('Dave Harris'),
    ('Chris Webb'),
    ('Tom Clarke'),
    ('Phil Evans');

-- ── Entries (one per competitor, transponders 101–106) ──────────────────────
insert into entries (competitor_id, event_id, event_class_id, transponder_number, status)
select
    c.id,
    ec.event_id,
    ec.id,
    cast(100 + row_number() over (order by c.id) as text),
    'CONFIRMED'
from competitors c
cross join event_classes ec
where c.display_name in ('Racer One', 'Racer Two', 'Dave Harris', 'Chris Webb', 'Tom Clarke', 'Phil Evans')
  and c.external_source is null
  and ec.event_id = (select id from events where name = 'Club Championship Round 1');

-- ── Rounds: P1, P2, Q1, Q2, Q3, Final ───────────────────────────────────────
insert into rounds (event_id, type, round_number, sequence_in_event, status)
select e.id, r.type, r.round_number, r.sequence_in_event, r.status
from events e
cross join (
    select 'PRACTICE' as type, 1 as round_number, 1 as sequence_in_event, 'COMPLETED' as status
    union all select 'PRACTICE',  2, 2, 'COMPLETED'
    union all select 'QUALIFIER', 1, 3, 'COMPLETED'
    union all select 'QUALIFIER', 2, 4, 'COMPLETED'
    union all select 'QUALIFIER', 3, 5, 'RUNNING'
    union all select 'FINAL',     1, 6, 'PENDING'
) r
where e.name = 'Club Championship Round 1';

-- ── Races: one heat per round (all 6 fit in a single heat of 8) ─────────────
-- Q3 Heat 1 is the next race to run
insert into races (round_id, event_class_id, heat_number, sequence_in_round, final_letter, start_type, status)
select
    r.id,
    ec.id,
    1,
    1,
    case when r.type = 'FINAL' then 'A' end,
    'STAGGER',
    case when r.sequence_in_event < 5 then 'FINISHED' else 'PENDING' end
from rounds r
join event_classes ec on ec.event_id = r.event_id
where r.event_id = (select id from events where name = 'Club Championship Round 1')
order by r.sequence_in_event;

-- ── Race entries: put all 6 drivers in every race ───────────────────────────
insert into race_entries (race_id, entry_id, grid_position, bumped)
select
    ra.id,
    e.id,
    row_number() over (partition by ra.id order by e.id),
    0
from races ra
join entries e on e.event_class_id = ra.event_class_id;
