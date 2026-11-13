function pad2(n: number): string {
  return String(n).padStart(2, '0');
}

export function formatTime(timestamp: number): string {
  const d = new Date(timestamp);
  return `${pad2(d.getHours())}:${pad2(d.getMinutes())}`;
}

export function formatDate(timestamp: number): string {
  const d = new Date(timestamp);
  return `${pad2(d.getDate())}/${pad2(d.getMonth() + 1)}/${d.getFullYear()}`;
}

export function formatDateTime(timestamp: number): string {
  return `${formatDate(timestamp)} ${formatTime(timestamp)}`;
}

export function formatFileDate(timestamp: number): string {
  const d = new Date(timestamp);
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;
}

export function toInputDateValue(timestamp: number): string {
  return formatFileDate(timestamp);
}

export function fromInputDateValue(value: string): number {
  const [year, month, day] = value.split('-').map(Number);
  if (!year || !month || !day) return Date.now();
  return new Date(year, month - 1, day, 12, 0, 0, 0).getTime();
}

export function toIsoUtc(timestamp: number): string {
  const d = new Date(timestamp);
  return (
    `${d.getUTCFullYear()}-${pad2(d.getUTCMonth() + 1)}-${pad2(d.getUTCDate())}` +
    `T${pad2(d.getUTCHours())}:${pad2(d.getUTCMinutes())}:${pad2(d.getUTCSeconds())}Z`
  );
}

export function fromIsoUtc(isoString?: string | null): number {
  if (!isoString || !isoString.trim()) return Date.now();
  const parsed = Date.parse(isoString);
  return Number.isNaN(parsed) ? Date.now() : parsed;
}
