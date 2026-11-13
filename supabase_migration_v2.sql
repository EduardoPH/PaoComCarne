-- ==============================================================================
-- PÃO COM CARNE — MIGRAÇÃO SEGURA V2 (SEM PERDA DE DADOS)
-- Adiciona: Soft Delete em attendances, app_releases, system_logs e Realtime.
-- ==============================================================================

-- 1. Soft delete em public.attendances
ALTER TABLE public.attendances
    ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS deleted_by TEXT,
    ADD COLUMN IF NOT EXISTS deletion_reason TEXT;

CREATE INDEX IF NOT EXISTS idx_attendances_is_deleted
    ON public.attendances(is_deleted);

-- Garante política de exclusão física bloqueada
DROP POLICY IF EXISTS "Prevent delete attendances" ON public.attendances;
CREATE POLICY "Prevent delete attendances"
ON public.attendances FOR DELETE
TO anon, authenticated
USING (false);

-- 2. Tabela app_releases (Versionamento centralizado e APK)
CREATE TABLE IF NOT EXISTS public.app_releases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    version_name TEXT NOT NULL,
    version_code INTEGER NOT NULL,
    min_supported_code INTEGER NOT NULL,
    mandatory BOOLEAN NOT NULL DEFAULT false,
    apk_path TEXT NOT NULL,
    sha256 TEXT NOT NULL,
    changelog TEXT NOT NULL DEFAULT '',
    release_reason TEXT NOT NULL DEFAULT '',
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);

ALTER TABLE public.app_releases
    ADD COLUMN IF NOT EXISTS release_reason TEXT NOT NULL DEFAULT '';

CREATE INDEX IF NOT EXISTS idx_app_releases_active_code
    ON public.app_releases(active, version_code DESC);

ALTER TABLE public.app_releases ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Allow read active app releases" ON public.app_releases;
CREATE POLICY "Allow read active app releases"
ON public.app_releases FOR SELECT
TO anon, authenticated
USING (true);

-- Insere release base se não houver nenhum registro ativo
INSERT INTO public.app_releases (version_name, version_code, min_supported_code, mandatory, apk_path, sha256, changelog, release_reason, active)
SELECT
    '1.0',
    1,
    1,
    false,
    'app-releases/pao-com-carne-v1.0.apk',
    '',
    'Versão inicial com controle de versão, exclusão lógica de atendimentos e auditoria centralizada.',
    '',
    true
WHERE NOT EXISTS (SELECT 1 FROM public.app_releases WHERE active = true);

-- 3. Tabela system_logs (Auditoria operacional append-only)
CREATE TABLE IF NOT EXISTS public.system_logs (
    id TEXT PRIMARY KEY,
    user_id TEXT,
    user_name TEXT,
    action TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id TEXT,
    device_name TEXT NOT NULL,
    result TEXT NOT NULL CHECK (result IN ('SUCCESS', 'FAILURE')),
    description TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    synced_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_system_logs_created_at ON public.system_logs(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_system_logs_user ON public.system_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_system_logs_action ON public.system_logs(action);
CREATE INDEX IF NOT EXISTS idx_system_logs_device ON public.system_logs(device_name);
CREATE INDEX IF NOT EXISTS idx_system_logs_result ON public.system_logs(result);

ALTER TABLE public.system_logs ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Allow read system logs" ON public.system_logs;
CREATE POLICY "Allow read system logs"
ON public.system_logs FOR SELECT
TO anon, authenticated
USING (true);

DROP POLICY IF EXISTS "Allow insert system logs" ON public.system_logs;
CREATE POLICY "Allow insert system logs"
ON public.system_logs FOR INSERT
TO anon, authenticated
WITH CHECK (true);

DROP POLICY IF EXISTS "Prevent update system logs" ON public.system_logs;
CREATE POLICY "Prevent update system logs"
ON public.system_logs FOR UPDATE
TO anon, authenticated
USING (false);

DROP POLICY IF EXISTS "Prevent delete system logs" ON public.system_logs;
CREATE POLICY "Prevent delete system logs"
ON public.system_logs FOR DELETE
TO anon, authenticated
USING (false);

-- 4. Bucket de Storage para APKs (se ainda não existir)
INSERT INTO storage.buckets (id, name, public)
VALUES ('app-releases', 'app-releases', true)
ON CONFLICT (id) DO NOTHING;

DROP POLICY IF EXISTS "Allow public read app-releases APKs" ON storage.objects;
CREATE POLICY "Allow public read app-releases APKs"
ON storage.objects FOR SELECT
TO anon, authenticated
USING (bucket_id = 'app-releases');

-- 5. Habilita Realtime nas tabelas principais
DO $$
BEGIN
    ALTER PUBLICATION supabase_realtime ADD TABLE public.attendances;
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
    ALTER PUBLICATION supabase_realtime ADD TABLE public.shifts;
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
    ALTER PUBLICATION supabase_realtime ADD TABLE public.queue_events;
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
    ALTER PUBLICATION supabase_realtime ADD TABLE public.app_releases;
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
    ALTER PUBLICATION supabase_realtime ADD TABLE public.system_logs;
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;
