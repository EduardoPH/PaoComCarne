-- ==============================================================================
-- PÃO COM CARNE — PUBLICAÇÃO DA VERSÃO 1.1 (version_code = 2)
-- Execute este script no SQL Editor do Supabase APÓS enviar o APK para o bucket
-- Storage -> app-releases/pao-com-carne-v1.1.apk
-- ==============================================================================

-- 1. Desativa releases anteriores (opcional, garante que v1.1 seja a ativa)
UPDATE public.app_releases
SET active = false
WHERE version_code < 2;

-- 2. Publica a versão 1.1 como obrigatória (bloqueia versionCode = 1)
INSERT INTO public.app_releases (
    version_name,
    version_code,
    min_supported_code,
    mandatory,
    apk_path,
    sha256,
    changelog,
    release_reason,
    active
) VALUES (
    '1.1',
    2,
    2,
    true,
    'app-releases/pao-com-carne-v1.1.apk',
    '08b3d8a9f2c9a9af6bb12d3b90a5f4f5542e005acf0206a10c1cfe96aa5942c0',
    '• Planilha Atendimentos: bloco de conclusão com Total de Atendimentos, Total Solo, Total Dupla e Total Grupo.' || E'\n' ||
    '• Planilha Clientes: bloco de conclusão com Total de cliente, Novos e Recorrentes.' || E'\n' ||
    '• Planilha Resumo Diário & Origens: totais diários e consolidado final com Atendimentos, Solo, Dupla e Grupo.' || E'\n' ||
    '• Administração, Inspeção de Turno e Dados do Turno: novos cards e totais de Solo, Dupla e Grupo.',
    'Atualização obrigatória com totalizadores de conclusão nas planilhas XLSX e nas telas de Administração e Inspeção de Turno.',
    true
);
