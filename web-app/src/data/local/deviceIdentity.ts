const DEVICE_UUID_KEY = 'pao_device_uuid';
const DEVICE_NICKNAME_KEY = 'pao_device_friendly_name';

function fallbackUuid(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return 'dev-' + Math.random().toString(16).slice(2, 10);
}

function defaultBrowserLabel(): string {
  if (typeof navigator === 'undefined') return 'Web Terminal';
  const ua = navigator.userAgent || '';
  if (/iPhone/i.test(ua)) return 'iPhone Web';
  if (/iPad/i.test(ua)) return 'iPad Web';
  if (/Android/i.test(ua)) return 'Android Web';
  if (/Windows/i.test(ua)) return 'PC Windows';
  if (/Macintosh/i.test(ua)) return 'Mac Web';
  return 'Navegador Web';
}

export const deviceIdentity = {
  getDeviceUuid(): string {
    if (typeof localStorage === 'undefined') return 'web-test-device';
    const existing = localStorage.getItem(DEVICE_UUID_KEY);
    if (existing && existing.trim()) return existing;
    const generated = fallbackUuid();
    localStorage.setItem(DEVICE_UUID_KEY, generated);
    return generated;
  },

  getFriendlyName(): string | null {
    if (typeof localStorage === 'undefined') return null;
    const raw = localStorage.getItem(DEVICE_NICKNAME_KEY);
    return raw && raw.trim() ? raw.trim() : null;
  },

  setFriendlyName(name: string): void {
    if (typeof localStorage === 'undefined') return;
    const trimmed = name.trim();
    if (!trimmed) {
      localStorage.removeItem(DEVICE_NICKNAME_KEY);
    } else {
      localStorage.setItem(DEVICE_NICKNAME_KEY, trimmed);
    }
  },

  getDeviceDisplayName(): string {
    const custom = this.getFriendlyName();
    if (custom) return custom;
    const shortId = this.getDeviceUuid().replace(/-/g, '').slice(0, 6).toUpperCase();
    return `${defaultBrowserLabel()} (${shortId})`;
  },
};
