-- ==============================================================================
-- PÃO COM CARNE — SISTEMA DE RECEPÇÃO
-- SCRIPT DE CRIAÇÃO DO BANCO DE DADOS POSTGRESQL / SUPABASE COM RLS E POLÍTICAS
-- ==============================================================================

-- Habilita extensão de UUID
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ------------------------------------------------------------------------------
-- 1. TABELA: hosters
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.hosters (
    id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
    name TEXT NOT NULL,
    auth_user_id UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    pin_hash TEXT,
    pin_salt TEXT,
    is_activated BOOLEAN NOT NULL DEFAULT false,
    active BOOLEAN NOT NULL DEFAULT true,
    is_admin BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);

-- ------------------------------------------------------------------------------
-- 2. TABELA: shifts (Turnos)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.shifts (
    id TEXT PRIMARY KEY,
    hoster_id TEXT NOT NULL REFERENCES public.hosters(id),
    hoster_name TEXT NOT NULL,
    period TEXT NOT NULL CHECK (period IN ('MANHA', 'NOITE')),
    opened_at TIMESTAMPTZ NOT NULL,
    closed_at TIMESTAMPTZ,
    status TEXT NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'CLOSED')),
    device_created_at TIMESTAMPTZ NOT NULL,
    synced_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now())
);

CREATE INDEX IF NOT EXISTS idx_shifts_hoster ON public.shifts(hoster_id);
CREATE INDEX IF NOT EXISTS idx_shifts_status ON public.shifts(status);
CREATE INDEX IF NOT EXISTS idx_shifts_opened_at ON public.shifts(opened_at);

-- ------------------------------------------------------------------------------
-- 3. TABELA: attendances (Atendimentos)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.attendances (
    id TEXT PRIMARY KEY,
    shift_id TEXT NOT NULL REFERENCES public.shifts(id),
    hoster_id TEXT NOT NULL REFERENCES public.hosters(id),
    hoster_name TEXT NOT NULL,
    type TEXT NOT NULL CHECK (type IN ('SOLO', 'DUPLA', 'GRUPO')),
    total_members INTEGER NOT NULL CHECK (total_members >= 1),
    new_members INTEGER NOT NULL CHECK (new_members >= 0),
    returning_members INTEGER NOT NULL CHECK (returning_members >= 0),
    is_preferential BOOLEAN NOT NULL DEFAULT false,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMPTZ,
    deleted_by TEXT,
    deletion_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT check_members_sum CHECK (new_members + returning_members = total_members),
    CONSTRAINT check_solo_members CHECK (type != 'SOLO' OR total_members = 1),
    CONSTRAINT check_dupla_members CHECK (type != 'DUPLA' OR total_members = 2),
    CONSTRAINT check_grupo_members CHECK (type != 'GRUPO' OR total_members >= 3)
);

CREATE INDEX IF NOT EXISTS idx_attendances_shift ON public.attendances(shift_id);
CREATE INDEX IF NOT EXISTS idx_attendances_hoster ON public.attendances(hoster_id);
CREATE INDEX IF NOT EXISTS idx_attendances_created_at ON public.attendances(created_at);
CREATE INDEX IF NOT EXISTS idx_attendances_is_deleted ON public.attendances(is_deleted);

-- ------------------------------------------------------------------------------
-- 4. TABELA: attendance_members (Integrantes individuais dos atendimentos)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.attendance_members (
    id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
    attendance_id TEXT NOT NULL REFERENCES public.attendances(id) ON DELETE CASCADE,
    member_number INTEGER NOT NULL CHECK (member_number >= 1),
    classification TEXT NOT NULL CHECK (classification IN ('NOVO', 'RECORRENTE')),
    origin TEXT CHECK (
        origin IS NULL OR 
        origin IN ('Instagram', 'TikTok', 'Indicação', 'iFood', 'Google', 'Já foi na antiga loja')
    ),
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    CONSTRAINT check_new_has_origin CHECK (classification != 'NOVO' OR origin IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_members_attendance ON public.attendance_members(attendance_id);
CREATE INDEX IF NOT EXISTS idx_members_classification ON public.attendance_members(classification);

-- ------------------------------------------------------------------------------
-- 5. TABELA: attendance_audit (Auditoria de alterações)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.attendance_audit (
    id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
    attendance_id TEXT NOT NULL REFERENCES public.attendances(id),
    hoster_id TEXT NOT NULL REFERENCES public.hosters(id),
    hoster_name TEXT NOT NULL,
    previous_data TEXT NOT NULL,
    new_data TEXT NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);

CREATE INDEX IF NOT EXISTS idx_audit_attendance ON public.attendance_audit(attendance_id);

-- ------------------------------------------------------------------------------
-- 6. TABELA: queue_events (Histórico da fila de espera)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.queue_events (
    id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
    shift_id TEXT NOT NULL REFERENCES public.shifts(id),
    hoster_id TEXT NOT NULL REFERENCES public.hosters(id),
    waiting_duos INTEGER NOT NULL CHECK (waiting_duos >= 0),
    waiting_groups INTEGER NOT NULL CHECK (waiting_groups >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);

CREATE INDEX IF NOT EXISTS idx_queue_shift ON public.queue_events(shift_id);
CREATE INDEX IF NOT EXISTS idx_queue_created_at ON public.queue_events(created_at);

-- ------------------------------------------------------------------------------
-- 7. TABELA: app_releases (Controle de versões e distribuição de APK)
-- ------------------------------------------------------------------------------
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

CREATE INDEX IF NOT EXISTS idx_app_releases_active_code
    ON public.app_releases(active, version_code DESC);

-- ------------------------------------------------------------------------------
-- 8. TABELA: system_logs (Logs centralizados de auditoria operacional)
-- ------------------------------------------------------------------------------
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

-- ==============================================================================
-- ROW LEVEL SECURITY (RLS) E POLÍTICAS DE ACESSO
-- ==============================================================================

ALTER TABLE public.hosters ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.shifts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.attendances ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.attendance_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.attendance_audit ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.queue_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.app_releases ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.system_logs ENABLE ROW LEVEL SECURITY;

-- Regras para chave anon pública do app da recepção:
-- 1. Leitura de hosters ativos para a tela de login
CREATE POLICY "Allow public read active hosters"
ON public.hosters FOR SELECT
TO anon, authenticated
USING (active = true);

-- 2. Atualização de hosters (primeiro acesso e ativação com hash/salt)
CREATE POLICY "Allow update hosters on activation"
ON public.hosters FOR UPDATE
TO anon, authenticated
USING (true)
WITH CHECK (true);

-- 3. Inserção de hosters pelo painel do Administrador
CREATE POLICY "Allow insert hosters"
ON public.hosters FOR INSERT
TO anon, authenticated
WITH CHECK (true);

-- 4. Exclusão de hosters pelo painel do Administrador
CREATE POLICY "Allow delete hosters"
ON public.hosters FOR DELETE
TO anon, authenticated
USING (id != 'admin');

-- 3. Turnos (shifts): Leitura, Inserção e Atualização
CREATE POLICY "Allow read shifts"
ON public.shifts FOR SELECT
TO anon, authenticated
USING (true);

CREATE POLICY "Allow insert shifts"
ON public.shifts FOR INSERT
TO anon, authenticated
WITH CHECK (true);

CREATE POLICY "Allow update shifts"
ON public.shifts FOR UPDATE
TO anon, authenticated
USING (true)
WITH CHECK (true);

-- 4. Atendimentos (attendances): Leitura, Inserção e Atualização (Exclusão física proibida - apenas Soft Delete via UPDATE!)
CREATE POLICY "Allow read attendances"
ON public.attendances FOR SELECT
TO anon, authenticated
USING (true);

CREATE POLICY "Allow insert attendances"
ON public.attendances FOR INSERT
TO anon, authenticated
WITH CHECK (true);

CREATE POLICY "Allow update attendances"
ON public.attendances FOR UPDATE
TO anon, authenticated
USING (true)
WITH CHECK (true);

-- Previne qualquer deleção física de atendimento (preserva auditoria de soft delete)
CREATE POLICY "Prevent delete attendances"
ON public.attendances FOR DELETE
TO anon, authenticated
USING (false);

-- 5. Integrantes (attendance_members): Leitura, Inserção, Atualização e Deleção (apenas em recálculo de correção)
CREATE POLICY "Allow read attendance members"
ON public.attendance_members FOR SELECT
TO anon, authenticated
USING (true);

CREATE POLICY "Allow insert attendance members"
ON public.attendance_members FOR INSERT
TO anon, authenticated
WITH CHECK (true);

CREATE POLICY "Allow update attendance members"
ON public.attendance_members FOR UPDATE
TO anon, authenticated
USING (true)
WITH CHECK (true);

CREATE POLICY "Allow delete attendance members on correction"
ON public.attendance_members FOR DELETE
TO anon, authenticated
USING (true);

-- 6. Auditoria (attendance_audit): Leitura e Inserção apenas (Imutável)
CREATE POLICY "Allow read audit"
ON public.attendance_audit FOR SELECT
TO anon, authenticated
USING (true);

CREATE POLICY "Allow insert audit"
ON public.attendance_audit FOR INSERT
TO anon, authenticated
WITH CHECK (true);

-- 7. Fila de espera (queue_events): Leitura e Inserção de novos eventos (Imutável)
CREATE POLICY "Allow read queue events"
ON public.queue_events FOR SELECT
TO anon, authenticated
USING (true);

CREATE POLICY "Allow insert queue events"
ON public.queue_events FOR INSERT
TO anon, authenticated
WITH CHECK (true);

-- 8. Políticas de versão (app_releases): Leitura pública para verificação de versão
CREATE POLICY "Allow read active app releases"
ON public.app_releases FOR SELECT
TO anon, authenticated
USING (true);

-- 9. Logs do sistema (system_logs): Append-only (Leitura e Inserção; Proibido alterar/excluir)
CREATE POLICY "Allow read system logs"
ON public.system_logs FOR SELECT
TO anon, authenticated
USING (true);

CREATE POLICY "Allow insert system logs"
ON public.system_logs FOR INSERT
TO anon, authenticated
WITH CHECK (true);

CREATE POLICY "Prevent update system logs"
ON public.system_logs FOR UPDATE
TO anon, authenticated
USING (false);

CREATE POLICY "Prevent delete system logs"
ON public.system_logs FOR DELETE
TO anon, authenticated
USING (false);

-- ==============================================================================
-- REALTIME E BUCKET DE RELEASES
-- ==============================================================================
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

-- ==============================================================================
-- DADOS INICIAIS (SEED) — USUÁRIO ADMINISTRADOR COM SENHA 'admin' E VERSÃO INICIAL
-- ==============================================================================
DELETE FROM public.hosters WHERE id != 'admin';

INSERT INTO public.hosters (id, name, pin_hash, pin_salt, is_activated, active, is_admin)
VALUES 
    (
        'admin',
        'Administrador',
        'e1ef788d028b746c3594f232fd4a5213c3901c45acfc5ff3f552119d89d554f9',
        'c6bca501f28b49e6',
        true,
        true,
        true
    )
ON CONFLICT (id) DO UPDATE 
SET 
    name = EXCLUDED.name,
    pin_hash = EXCLUDED.pin_hash,
    pin_salt = EXCLUDED.pin_salt,
    is_activated = EXCLUDED.is_activated,
    active = EXCLUDED.active,
    is_admin = EXCLUDED.is_admin;

INSERT INTO public.app_releases (version_name, version_code, min_supported_code, mandatory, apk_path, sha256, changelog, release_reason, active)
SELECT
    '1.0',
    1,
    1,
    false,
    'app-releases/pao-com-carne-v1.0.apk',
    '',
    'Versão inicial de operação com versionamento centralizado, soft delete e logs de auditoria.',
    '',
    true
WHERE NOT EXISTS (SELECT 1 FROM public.app_releases WHERE active = true);
