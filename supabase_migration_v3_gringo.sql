-- ==============================================================================
-- PÃO COM CARNE — MIGRAÇÃO v1.3: ADICIONAR ORIGEM 'Gringo'
-- Execute este script no SQL Editor do Supabase antes de utilizar a opção 'Gringo'
-- para que a sincronização não falhe na constraint CHECK da tabela attendance_members.
-- ==============================================================================

ALTER TABLE public.attendance_members
DROP CONSTRAINT IF EXISTS attendance_members_origin_check;

DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN (
        SELECT c.conname
        FROM pg_constraint c
        JOIN pg_attribute a
          ON a.attrelid = c.conrelid
         AND a.attnum = ANY (c.conkey)
        WHERE c.conrelid = 'public.attendance_members'::regclass
          AND c.contype = 'c'
          AND a.attname = 'origin'
    ) LOOP
        EXECUTE format('ALTER TABLE public.attendance_members DROP CONSTRAINT IF EXISTS %I', r.conname);
    END LOOP;
END $$;

ALTER TABLE public.attendance_members
ADD CONSTRAINT attendance_members_origin_check CHECK (
    origin IS NULL OR
    origin IN ('Instagram', 'TikTok', 'Indicação', 'iFood', 'Google', 'Já foi na antiga loja', 'Gringo')
);
