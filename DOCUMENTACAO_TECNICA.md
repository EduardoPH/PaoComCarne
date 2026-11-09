# Pão com Carne — Sistema de Recepção
## Documentação Técnica, Arquitetura e Guia Operacional

Este documento detalha a arquitetura, persistência local, integração com Supabase, políticas de segurança e guia de execução do aplicativo nativo Android **Pão com Carne — Sistema de Recepção**.

---

### 1. Arquitetura do Sistema
O aplicativo foi desenvolvido utilizando a arquitetura **MVVM (Model-View-ViewModel)** com **Clean Architecture**, seguindo os padrões recomendados pelo Google para Jetpack Compose:

- **Camada de Apresentação (`presentation/`)**:
  - Telas em Jetpack Compose: `LoginScreen`, `ReceptionScreen`, `InspectionScreen`, `AdminScreen`, `ShiftScreen`.
  - Componentes reutilizáveis em Material 3 com a identidade visual da marca (Vermelho `#A62B2B`, Creme `#F5E8C7`, Antracite `#292421`, Branco e neutros).
  - Estados reativos utilizando `StateFlow` e `collectAsState()`.
- **Camada de Domínio (`domain/`)**:
  - Modelos puros: `Hoster`, `Shift`, `Attendance`, `AttendanceMember`, `QueueEvent`, `AttendanceAudit`.
  - Utilitários: `SecurityUtils` (hashing criptográfico PBKDF2), `DateTimeUtils` e `XlsxGenerator` (gerador nativo de planilhas OpenXML `.xlsx` em 2 abas sem dependências externas pesadas).
- **Camada de Dados (`data/`)**:
  - **Local (`data/local/`)**: Room Database com entidades, DAOs e transações offline.
  - **Remoto (`data/remote/`)**: Retrofit + OkHttp + Moshi para comunicação com a API REST PostgREST do Supabase.
  - **Sincronização (`data/sync/`)**: `SyncManager` com monitoramento contínuo de conectividade (`ConnectivityManager.NetworkCallback`), fila de persistência FIFO, retentativas e idempotência.

---

### 2. Estrutura de Pastas e Arquivos
```
app/src/main/
├── AndroidManifest.xml
├── java/com/example/
│   ├── PaoComCarneApp.kt                  # Classe Application com singletons
│   ├── MainActivity.kt                    # Scaffold principal e navegação persistente (4 abas)
│   ├── data/
│   │   ├── local/
│   │   │   ├── AppDatabase.kt             # Banco de dados Room
│   │   │   ├── entity/Entities.kt         # Entidades Room
│   │   │   └── dao/Daos.kt                # Interfaces DAO Room com Kotlin Flow
│   │   ├── remote/
│   │   │   ├── SupabaseModels.kt          # DTOs Moshi para PostgREST
│   │   │   ├── SupabaseService.kt         # Endpoints Retrofit REST
│   │   │   └── SupabaseClient.kt          # Cliente HTTP com interceptors e chaves
│   │   ├── repository/
│   │   │   ├── AuthRepository.kt          # Autenticação e primeiro acesso
│   │   │   ├── ReceptionRepository.kt     # Atendimentos, turnos, fila e auditoria
│   │   │   └── ExportRepository.kt        # Exportação XLSX com validações
│   │   └── sync/
│   │       └── SyncManager.kt             # Orquestrador offline-first
│   ├── domain/
│   │   ├── model/Models.kt                # Modelos de domínio
│   │   └── util/
│   │       ├── DateTimeUtils.kt           # Formatação de datas em pt-BR
│   │       ├── SecurityUtils.kt           # PBKDF2 com Salt
│   │       └── XlsxGenerator.kt           # Gerador OpenXML XLSX nativo
│   ├── presentation/
│   │   ├── components/CommonComponents.kt # Botões, Steppers, Pílula de Sincronização
│   │   ├── login/LoginScreen.kt & ViewModel
│   │   ├── reception/ReceptionScreen.kt & ViewModel
│   │   ├── attendance/AttendanceDialogs.kt & CorrectionDialog.kt
│   │   ├── inspection/InspectionScreen.kt
│   │   ├── admin/AdminScreen.kt & ViewModel
│   │   ├── shift/ShiftScreen.kt, ShiftSelectDialog.kt & CloseShiftDialog.kt
│   │   └── queue/UpdateQueueDialog.kt
│   └── ui/theme/                          # Cores da marca, tema claro e tipografia
└── res/
    ├── drawable/                          # Ícone adaptativo artesanal
    ├── mipmap-*/                          # Bitmaps do launcher icon
    └── xml/file_paths.xml                 # Provedor seguro FileProvider para XLSX
```

---

### 3. Modelo Relacional e Segurança no Supabase
O script SQL completo está no arquivo `/supabase_schema.sql`.

#### Tabelas:
1. `hosters`: Identificação dos membros da equipe de recepção, hash e salt do PIN criptografado, status de ativação.
2. `shifts`: Turnos (Manhã e Noite), timestamps de abertura e fechamento, integridade por hoster.
3. `attendances`: Atendimentos vinculados a um turno ativo, total de integrantes, quantidade de novos e recorrentes (validados por constraints no PostgreSQL).
4. `attendance_members`: Integrantes individuais (1 linha por pessoa) com classificação (`NOVO` ou `RECORRENTE`) e a origem individual para novos clientes (Instagram, TikTok, Indicação, iFood, Google, Já foi na antiga loja).
5. `attendance_audit`: Histórico imutável de correções do último atendimento (valores anteriores e novos).
6. `queue_events`: Histórico cumulativo de duplas e grupos aguardando mesa.

#### Políticas de Segurança (Row Level Security - RLS):
- Todas as 6 tabelas possuem RLS habilitado (`ALTER TABLE ... ENABLE ROW LEVEL SECURITY`).
- Proibição estrita de exclusão na tabela `attendances` (Regra Inegociável #11: `Prevent delete attendances USING (false)`).
- Chave anon pública autorizada apenas para operações necessárias da recepção interna.

---

### 4. Funcionamento Offline e Estratégia de Sincronização
1. **Prioridade Local Imediata**: Toda operação (login de hoster conhecido, abertura de turno, novo atendimento, correção, atualização de fila e encerramento) é gravada **instantaneamente** no banco Room SQLite no dispositivo.
2. **Fila de Sincronização (`sync_queue`)**: Cada operação gera um registro na fila com identificador da entidade, operação (`INSERT`/`UPDATE`), payload JSON e status `PENDING`.
3. **Monitoramento e Execução Automática**: O `SyncManager` monitora o estado da rede via `ConnectivityManager.NetworkCallback`. Ao detectar sinal de internet, processa os itens em ordem FIFO idempotente.
4. **Idempotência**: Requisições de inserção utilizam cabeçalho `resolution=merge-duplicates` do Supabase para evitar qualquer duplicidade caso uma tentativa seja retransmitida após instabilidade.
5. **Indicadores de Status**: A pílula de sincronização no topo exibe em tempo real:
   - Verde: Conectado e sincronizado
   - Âmbar: Modo Offline / Sincronização pendente
   - Azul: Sincronizando...
   - Vermelho: Erro de sincronização

---

### 5. Exportação de Relatórios Gerenciais (.xlsx)
O arquivo gerado segue rigorosamente o formato OpenXML SpreadsheetML com 2 abas:
- **Aba 1 — Atendimentos**: ID do atendimento, Data, Turno, Hoster, Tipo, Total de integrantes, Clientes novos, Clientes recorrentes.
- **Aba 2 — Clientes**: ID do atendimento, Hoster, Identificador do integrante, Classificação, Origem.
- **Regras de Bloqueio**: A exportação é automaticamente bloqueada caso existam turnos abertos no período selecionado ou se houver registros locais pendentes de sincronização.

---

### 6. Como Executar e Gerar APK

#### Executar o aplicativo no ambiente de desenvolvimento:
Para compilar e verificar o aplicativo:
- O build é validado através da ferramenta de compilação do Android Gradle Plugin.

#### Gerar APK de Release:
Execute no terminal:
```bash
gradle assembleRelease
```
O APK será gerado em: `app/build/outputs/apk/release/app-release.apk`.

#### Gerar APK de Debug:
```bash
gradle assembleDebug
```
O APK será gerado em: `app/build/outputs/apk/debug/app-debug.apk`.
