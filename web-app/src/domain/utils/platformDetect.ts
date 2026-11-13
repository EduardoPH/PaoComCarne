export type ClientOs = 'IOS' | 'ANDROID' | 'WINDOWS' | 'MACOS' | 'LINUX' | 'UNKNOWN';
export type ClientBrowser = 'SAFARI' | 'CHROME' | 'EDGE' | 'FIREFOX' | 'SAMSUNG' | 'OTHER';

export interface PlatformInfo {
  os: ClientOs;
  osLabel: string;
  browser: ClientBrowser;
  browserLabel: string;
  isStandalone: boolean;
  isIosNonSafari: boolean;
}

export function detectClientPlatform(
  userAgent: string,
  platform = '',
  maxTouchPoints = 0,
  isStandalone = false
): PlatformInfo {
  const ua = userAgent || '';
  const plat = platform || '';

  const isIos =
    /iPad|iPhone|iPod/i.test(ua) ||
    (/Mac/i.test(plat) && maxTouchPoints > 1);

  const isAndroid = !isIos && /Android/i.test(ua);
  const isWindows = !isIos && !isAndroid && /Windows|Win32|Win64/i.test(ua + ' ' + plat);
  const isMacOs = !isIos && !isAndroid && !isWindows && /Macintosh|MacIntel|Mac OS/i.test(ua + ' ' + plat);
  const isLinux = !isIos && !isAndroid && !isWindows && !isMacOs && /Linux|X11/i.test(ua + ' ' + plat);

  let os: ClientOs = 'UNKNOWN';
  let osLabel = 'Navegador Web';
  if (isIos) {
    os = 'IOS';
    osLabel = /iPad/i.test(ua) || (maxTouchPoints > 1 && /Mac/i.test(plat)) ? 'iPad (iOS)' : 'iPhone (iOS)';
  } else if (isAndroid) {
    os = 'ANDROID';
    osLabel = 'Android';
  } else if (isWindows) {
    os = 'WINDOWS';
    osLabel = 'Windows';
  } else if (isMacOs) {
    os = 'MACOS';
    osLabel = 'macOS';
  } else if (isLinux) {
    os = 'LINUX';
    osLabel = 'Linux';
  }

  let browser: ClientBrowser = 'OTHER';
  let browserLabel = 'Navegador';
  if (/SamsungBrowser/i.test(ua)) {
    browser = 'SAMSUNG';
    browserLabel = 'Samsung Internet';
  } else if (/Edg|EdgiOS|EdgA/i.test(ua)) {
    browser = 'EDGE';
    browserLabel = 'Edge';
  } else if (/FxiOS|Firefox/i.test(ua)) {
    browser = 'FIREFOX';
    browserLabel = 'Firefox';
  } else if (/CriOS|Chrome/i.test(ua)) {
    browser = 'CHROME';
    browserLabel = 'Chrome';
  } else if (/Safari/i.test(ua)) {
    browser = 'SAFARI';
    browserLabel = 'Safari';
  }

  const isIosNonSafari = isIos && browser !== 'SAFARI';

  return {
    os,
    osLabel,
    browser,
    browserLabel,
    isStandalone,
    isIosNonSafari,
  };
}

export function getBrowserPlatformInfo(): PlatformInfo {
  if (typeof window === 'undefined' || typeof navigator === 'undefined') {
    return detectClientPlatform('', '', 0, false);
  }
  const standalone =
    (navigator as Navigator & { standalone?: boolean }).standalone === true ||
    (typeof window.matchMedia === 'function' &&
      window.matchMedia('(display-mode: standalone)').matches);

  return detectClientPlatform(
    navigator.userAgent || '',
    navigator.platform || '',
    navigator.maxTouchPoints || 0,
    standalone
  );
}
