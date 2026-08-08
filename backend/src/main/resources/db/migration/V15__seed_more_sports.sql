-- The catalog shipped with two sports, which is enough to prove the engine and not enough to run a
-- real meet. Everything added here is served by strategies that are already deployed — team sports
-- by ROUND_ROBIN/POINTS/POINTS_TABLE, timed events by NONE/TIME/LOWEST_TIME — so no new code is
-- needed for any of them. That is the extensibility claim in ARCHITECTURE_BRIEF section 7 being
-- cashed in rather than restated.

insert into sport (id, code, name, description) values
    -- Team sports: everyone plays everyone, points decide the table.
    (gen_random_uuid(), 'CRICKET', 'Cricket', 'Limited-overs cricket, league format'),
    (gen_random_uuid(), 'BASKETBALL', 'Basketball', 'Five-a-side basketball, league format'),
    (gen_random_uuid(), 'HOCKEY', 'Hockey', 'Field hockey, league format'),
    (gen_random_uuid(), 'VOLLEYBALL', 'Volleyball', 'Indoor volleyball, league format'),
    (gen_random_uuid(), 'KABADDI', 'Kabaddi', 'Standard kabaddi, league format'),
    (gen_random_uuid(), 'HANDBALL', 'Handball', 'Team handball, league format'),

    -- Individual head-to-head: still a league, just one player a side.
    (gen_random_uuid(), 'BADMINTON_SINGLES', 'Badminton (singles)', 'Singles badminton, league format'),
    (gen_random_uuid(), 'TABLE_TENNIS_SINGLES', 'Table tennis (singles)', 'Singles table tennis, league format'),
    (gen_random_uuid(), 'TENNIS_SINGLES', 'Tennis (singles)', 'Singles tennis, league format'),
    (gen_random_uuid(), 'CHESS', 'Chess', 'Chess, league format'),

    -- Measured events: no pairing, the clock separates the field.
    (gen_random_uuid(), 'ATHLETICS_200M', 'Athletics 200m', '200 metre sprint'),
    (gen_random_uuid(), 'ATHLETICS_400M', 'Athletics 400m', '400 metre sprint'),
    (gen_random_uuid(), 'ATHLETICS_800M', 'Athletics 800m', '800 metre run'),
    (gen_random_uuid(), 'ATHLETICS_1500M', 'Athletics 1500m', '1500 metre run'),
    (gen_random_uuid(), 'ATHLETICS_5000M', 'Athletics 5000m', '5000 metre run'),
    (gen_random_uuid(), 'ATHLETICS_MARATHON', 'Marathon', 'Full marathon'),
    (gen_random_uuid(), 'SWIMMING_50M_FREESTYLE', 'Swimming 50m freestyle', '50 metre freestyle'),
    (gen_random_uuid(), 'SWIMMING_100M_FREESTYLE', 'Swimming 100m freestyle', '100 metre freestyle'),
    (gen_random_uuid(), 'CYCLING_TIME_TRIAL', 'Cycling time trial', 'Individual time trial')
on conflict do nothing;
