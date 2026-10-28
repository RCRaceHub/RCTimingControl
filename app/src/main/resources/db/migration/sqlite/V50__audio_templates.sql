-- Audio templates: a named copy of the club's audio settings (which sounds and calls are on, their wording and
-- timings) that race control can apply in one go, for example BRCA meeting, Club night or Practice. settings is the
-- same JSON as club_profiles.audio_settings, without the club's own sound files, which stay as they are when a
-- template is applied. Names are unique, ignoring case.
CREATE TABLE audio_templates (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(80) NOT NULL,
    settings TEXT NOT NULL CHECK (json_valid(settings)),
    created_at BIGINT NOT NULL DEFAULT (CAST(unixepoch('subsec') * 1000000 AS INTEGER)),
    updated_at BIGINT NOT NULL DEFAULT (CAST(unixepoch('subsec') * 1000000 AS INTEGER))
);
CREATE UNIQUE INDEX audio_templates_name_key ON audio_templates (name COLLATE NOCASE);

INSERT INTO audio_templates (name, settings) VALUES
('Club night',
 '{"announceCountdown":true,"announceStagger":true,"announceLapBeep":true,"announceFinish":true,"announceRunningOrder":true,"runningOrderDepth":3,"countdownIntervals":[600,300,120,60,30],"startHorn":true,"finishHorn":true,"announceRaceCalls":true,"announceNextRace":true,"startCountdownSeconds":10,"announceCarFinish":true,"tickSound":true,"finalBeeps":true,"paceBeeps":true}'),
('BRCA meeting',
 '{"announceCountdown":true,"announceStagger":false,"announceLapBeep":true,"announceFinish":true,"announceRunningOrder":false,"runningOrderDepth":3,"countdownIntervals":[600,300,120,60,30],"startHorn":true,"finishHorn":true,"announceRaceCalls":true,"announceNextRace":true,"startCountdownSeconds":10,"announceCarFinish":false,"tickSound":true,"finalBeeps":true,"paceBeeps":false}'),
('Practice',
 '{"announceCountdown":false,"announceStagger":false,"announceLapBeep":true,"announceFinish":false,"announceRunningOrder":false,"runningOrderDepth":3,"countdownIntervals":[600,300,120,60,30],"startHorn":false,"finishHorn":false,"announceRaceCalls":false,"announceNextRace":false,"startCountdownSeconds":0,"announceCarFinish":false,"tickSound":false,"finalBeeps":false,"paceBeeps":true}');
