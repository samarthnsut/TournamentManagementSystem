-- The two MVP sports from 10_DEVELOPMENT_ROADMAP Sprint 3. Between them they exercise both
-- ends of the strategy engine: a team league (ROUND_ROBIN/POINTS/POINTS_TABLE) and an
-- individual measured event (NONE/TIME/LOWEST_TIME).
insert into sport (id, code, name, description)
values
    (gen_random_uuid(), 'FOOTBALL', 'Football', 'Association football, played as a team league.'),
    (gen_random_uuid(), 'ATHLETICS_100M', 'Athletics - 100m', 'Individual 100 metre sprint, ranked on recorded time.')
on conflict do nothing;
