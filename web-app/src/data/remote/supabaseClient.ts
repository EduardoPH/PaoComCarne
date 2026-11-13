export interface SupabaseHosterDto {
  id: string;
  name: string;
  auth_user_id?: string | null;
  pin_hash?: string | null;
  pin_salt?: string | null;
  is_activated: boolean;
  active: boolean;
  is_admin: boolean;
  created_at?: string | null;
}

export interface SupabaseShiftDto {
  id: string;
  hoster_id: string;
  hoster_name: string;
  period: string;
  opened_at: string;
  closed_at?: string | null;
  status: string;
  device_created_at: string;
}

export interface SupabaseAttendanceDto {
  id: string;
  shift_id: string;
  hoster_id: string;
  hoster_name: string;
  type: string;
  total_members: number;
  new_members: number;
  returning_members: number;
  is_preferential: boolean;
  is_deleted?: boolean;
  deleted_at?: string | null;
  deleted_by?: string | null;
  deletion_reason?: string | null;
  created_at: string;
  updated_at: string;
}

export interface SupabaseMemberDto {
  id: string;
  attendance_id: string;
  member_number: number;
  classification: string;
  origin: string | null;
  created_at: string;
}

export interface SupabaseAuditDto {
  id: string;
  attendance_id: string;
  hoster_id: string;
  hoster_name: string;
  previous_data: string;
  new_data: string;
  changed_at: string;
}

export interface SupabaseQueueDto {
  id: string;
  shift_id: string;
  hoster_id: string;
  waiting_duos: number;
  waiting_groups: number;
  created_at: string;
}

export interface SupabaseSystemLogDto {
  id: string;
  user_id?: string | null;
  user_name?: string | null;
  action: string;
  entity_type: string;
  entity_id?: string | null;
  device_name: string;
  result: string;
  description: string;
  created_at: string;
}

export interface SupabaseAppReleaseDto {
  id: string;
  version_name: string;
  version_code: number;
  min_supported_code: number;
  mandatory: boolean;
  apk_path: string;
  sha256: string;
  changelog: string;
  release_reason?: string | null;
  active: boolean;
  created_at: string;
}

export const DEFAULT_SUPABASE_URL = 'https://iodvkbzszlrphuqvywbr.supabase.co';
export const DEFAULT_ANON_KEY = 'sb_publishable_ld0dSdRWiiFMxikrYyBNmA_VVRABrou';

const STORAGE_URL_KEY = 'pao_supabase_url';
const STORAGE_ANON_KEY = 'pao_supabase_anon_key';

export function getSupabaseConfig(): { url: string; key: string } {
  if (typeof localStorage === 'undefined') {
    return { url: DEFAULT_SUPABASE_URL, key: DEFAULT_ANON_KEY };
  }
  const savedUrl = localStorage.getItem(STORAGE_URL_KEY);
  const savedKey = localStorage.getItem(STORAGE_ANON_KEY);

  const url = savedUrl && savedUrl.trim() ? savedUrl.trim().replace(/\/+$/, '') : DEFAULT_SUPABASE_URL;
  const key =
    savedKey && savedKey.trim() && !savedKey.startsWith('eyJ') && !savedKey.includes('dudunega')
      ? savedKey.trim()
      : DEFAULT_ANON_KEY;

  return { url, key };
}

export function saveSupabaseConfig(url: string, key: string): void {
  if (typeof localStorage === 'undefined') return;
  localStorage.setItem(STORAGE_URL_KEY, url.trim().replace(/\/+$/, ''));
  localStorage.setItem(STORAGE_ANON_KEY, key.trim());
}

export function buildApkPublicUrl(apkPath: string): string {
  if (/^https?:\/\//i.test(apkPath.trim())) {
    return apkPath.trim();
  }
  const { url } = getSupabaseConfig();
  const cleaned = apkPath.trim().replace(/^\/+/, '');
  return `${url}/storage/v1/object/public/app-releases/${cleaned}`;
}

async function request<T>(
  path: string,
  options: RequestInit = {},
  extraHeaders: Record<string, string> = {}
): Promise<{ ok: boolean; status: number; data?: T; errorText?: string }> {
  const { url, key } = getSupabaseConfig();
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 10000);

  try {
    const res = await fetch(`${url}/${path}`, {
      ...options,
      signal: controller.signal,
      headers: {
        apikey: key,
        Authorization: `Bearer ${key}`,
        'Content-Type': 'application/json',
        ...extraHeaders,
        ...(options.headers || {}),
      },
    });

    if (!res.ok) {
      let errorText = `HTTP ${res.status}`;
      try {
        const body = await res.text();
        if (body) errorText = body;
      } catch {
        // ignore read error
      }
      return { ok: false, status: res.status, errorText };
    }

    const text = await res.text();
    if (!text) {
      return { ok: true, status: res.status };
    }
    return { ok: true, status: res.status, data: JSON.parse(text) as T };
  } catch (err) {
    return {
      ok: false,
      status: 0,
      errorText: err instanceof Error ? err.message : 'Erro de rede',
    };
  } finally {
    clearTimeout(timer);
  }
}

export const supabaseClient = {
  async getHosters() {
    return request<SupabaseHosterDto[]>('rest/v1/hosters?select=*&order=name.asc', { method: 'GET' });
  },

  async upsertHoster(dto: SupabaseHosterDto) {
    return request<SupabaseHosterDto[]>(
      'rest/v1/hosters',
      { method: 'POST', body: JSON.stringify(dto) },
      { Prefer: 'return=representation, resolution=merge-duplicates' }
    );
  },

  async updateHoster(id: string, body: Record<string, unknown>) {
    return request<void>(
      `rest/v1/hosters?id=eq.${encodeURIComponent(id)}`,
      { method: 'PATCH', body: JSON.stringify(body) },
      { Prefer: 'return=minimal' }
    );
  },

  async deleteHoster(id: string) {
    return request<void>(`rest/v1/hosters?id=eq.${encodeURIComponent(id)}`, {
      method: 'DELETE',
    });
  },

  async getShifts() {
    return request<SupabaseShiftDto[]>('rest/v1/shifts?select=*&order=opened_at.desc', { method: 'GET' });
  },

  async upsertShift(dto: SupabaseShiftDto) {
    return request<SupabaseShiftDto[]>(
      'rest/v1/shifts',
      { method: 'POST', body: JSON.stringify(dto) },
      { Prefer: 'return=representation, resolution=merge-duplicates' }
    );
  },

  async patchShift(id: string, body: Record<string, unknown>) {
    return request<void>(
      `rest/v1/shifts?id=eq.${encodeURIComponent(id)}`,
      { method: 'PATCH', body: JSON.stringify(body) },
      { Prefer: 'return=minimal' }
    );
  },

  async getAttendances() {
    return request<SupabaseAttendanceDto[]>('rest/v1/attendances?select=*&order=created_at.desc', {
      method: 'GET',
    });
  },

  async upsertAttendance(dto: SupabaseAttendanceDto) {
    return request<SupabaseAttendanceDto[]>(
      'rest/v1/attendances',
      { method: 'POST', body: JSON.stringify(dto) },
      { Prefer: 'return=representation, resolution=merge-duplicates' }
    );
  },

  async patchAttendance(id: string, body: Record<string, unknown>) {
    return request<void>(
      `rest/v1/attendances?id=eq.${encodeURIComponent(id)}`,
      { method: 'PATCH', body: JSON.stringify(body) },
      { Prefer: 'return=minimal' }
    );
  },

  async getAttendanceMembers() {
    return request<SupabaseMemberDto[]>(
      'rest/v1/attendance_members?select=*&order=created_at.asc',
      { method: 'GET' }
    );
  },

  async upsertMembers(members: SupabaseMemberDto[]) {
    return request<SupabaseMemberDto[]>(
      'rest/v1/attendance_members',
      { method: 'POST', body: JSON.stringify(members) },
      { Prefer: 'return=representation, resolution=merge-duplicates' }
    );
  },

  async upsertMember(member: SupabaseMemberDto) {
    return request<void>(
      'rest/v1/attendance_members',
      { method: 'POST', body: JSON.stringify(member) },
      { Prefer: 'return=minimal, resolution=merge-duplicates' }
    );
  },

  async deleteMembersForAttendance(attendanceId: string) {
    return request<void>(
      `rest/v1/attendance_members?attendance_id=eq.${encodeURIComponent(attendanceId)}`,
      { method: 'DELETE' }
    );
  },

  async insertAudit(dto: SupabaseAuditDto) {
    return request<void>(
      'rest/v1/attendance_audit',
      { method: 'POST', body: JSON.stringify(dto) },
      { Prefer: 'return=minimal' }
    );
  },

  async getQueueEvents() {
    return request<SupabaseQueueDto[]>('rest/v1/queue_events?select=*&order=created_at.desc', {
      method: 'GET',
    });
  },

  async insertQueueEvent(dto: SupabaseQueueDto) {
    return request<void>(
      'rest/v1/queue_events',
      { method: 'POST', body: JSON.stringify(dto) },
      { Prefer: 'return=minimal' }
    );
  },

  async getSystemLogs() {
    return request<SupabaseSystemLogDto[]>(
      'rest/v1/system_logs?select=*&order=created_at.desc&limit=500',
      { method: 'GET' }
    );
  },

  async upsertSystemLog(dto: SupabaseSystemLogDto) {
    return request<void>(
      'rest/v1/system_logs',
      { method: 'POST', body: JSON.stringify(dto) },
      { Prefer: 'return=minimal, resolution=merge-duplicates' }
    );
  },

  async getActiveAppReleases() {
    return request<SupabaseAppReleaseDto[]>(
      'rest/v1/app_releases?select=*&active=eq.true&order=version_code.desc&limit=1',
      { method: 'GET' }
    );
  },
};
