-- Phase 0 infrastructure guard. Business schema starts at V1 in Phase 1.
DO
$$
BEGIN
    IF current_setting('server_version_num')::integer < 180000 THEN
        RAISE EXCEPTION 'StoryWeaver requires PostgreSQL 18 or newer';
    END IF;
END
$$;
