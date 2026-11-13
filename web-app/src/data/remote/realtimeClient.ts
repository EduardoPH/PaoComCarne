import { getSupabaseConfig } from './supabaseClient';

export type RealtimeTable =
  | 'attendances'
  | 'shifts'
  | 'queue_events'
  | 'system_logs'
  | 'app_releases';

type RealtimeHandler = (table: RealtimeTable) => void;

class SupabaseRealtimeClient {
  private ws: WebSocket | null = null;
  private heartbeatTimer: ReturnType<typeof setInterval> | null = null;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private refCounter = 1;
  private started = false;
  private onTableChange: RealtimeHandler | null = null;

  start(handler: RealtimeHandler): void {
    this.onTableChange = handler;
    if (this.started) return;
    if (typeof window === 'undefined' || typeof WebSocket === 'undefined') return;
    this.started = true;
    this.connect();
  }

  stop(): void {
    this.started = false;
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    if (this.ws) {
      try {
        this.ws.close(1000, 'Client stop');
      } catch {
        // ignore close error
      }
      this.ws = null;
    }
  }

  private connect(): void {
    if (!this.started || (typeof navigator !== 'undefined' && !navigator.onLine)) return;
    const { url, key } = getSupabaseConfig();
    const wsUrl = url
      .replace(/^https:\/\//i, 'wss://')
      .replace(/^http:\/\//i, 'ws://');
    const endpoint = `${wsUrl}/realtime/v1/websocket?apikey=${encodeURIComponent(key)}&vsn=1.0.0`;

    try {
      const ws = new WebSocket(endpoint);
      this.ws = ws;

      ws.onopen = () => {
        const joinPayload = {
          topic: 'realtime:public',
          event: 'phx_join',
          payload: {
            config: {
              broadcast: { self: false },
              presence: { key: '' },
              postgres_changes: [
                { event: '*', schema: 'public', table: 'attendances' },
                { event: '*', schema: 'public', table: 'shifts' },
                { event: '*', schema: 'public', table: 'queue_events' },
                { event: '*', schema: 'public', table: 'system_logs' },
                { event: '*', schema: 'public', table: 'app_releases' },
              ],
            },
          },
          ref: String(this.refCounter++),
        };
        ws.send(JSON.stringify(joinPayload));

        if (this.heartbeatTimer) clearInterval(this.heartbeatTimer);
        this.heartbeatTimer = setInterval(() => {
          if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            this.ws.send(
              JSON.stringify({
                topic: 'phoenix',
                event: 'heartbeat',
                payload: {},
                ref: String(this.refCounter++),
              })
            );
          }
        }, 25000);
      };

      ws.onmessage = (evt) => {
        if (typeof evt.data !== 'string') return;
        try {
          const parsed = JSON.parse(evt.data) as {
            event?: string;
            payload?: { data?: { table?: string }; table?: string };
          };
          if (parsed.event === 'postgres_changes') {
            const table = parsed.payload?.data?.table || parsed.payload?.table;
            if (
              table === 'attendances' ||
              table === 'shifts' ||
              table === 'queue_events' ||
              table === 'system_logs' ||
              table === 'app_releases'
            ) {
              this.onTableChange?.(table);
            }
          }
        } catch {
          // ignore malformed ws frame
        }
      };

      ws.onclose = () => {
        this.ws = null;
        if (this.heartbeatTimer) {
          clearInterval(this.heartbeatTimer);
          this.heartbeatTimer = null;
        }
        if (this.started) {
          this.scheduleReconnect();
        }
      };

      ws.onerror = () => {
        try {
          ws.close();
        } catch {
          // ignore
        }
      };
    } catch {
      this.scheduleReconnect();
    }
  }

  private scheduleReconnect(): void {
    if (!this.started || this.reconnectTimer) return;
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      this.connect();
    }, 5000);
  }
}

export const realtimeClient = new SupabaseRealtimeClient();
