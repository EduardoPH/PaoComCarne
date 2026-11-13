import { pbkdf2 } from '@noble/hashes/pbkdf2';
import { sha256 } from '@noble/hashes/sha2';

const ITERATIONS = 10000;
const KEY_LENGTH_BYTES = 32; // 256 bits

export function bytesToHex(bytes: Uint8Array): string {
  let out = '';
  for (let i = 0; i < bytes.length; i++) {
    out += bytes[i].toString(16).padStart(2, '0');
  }
  return out;
}

export function hexToBytes(hex: string): Uint8Array {
  const clean = hex.trim();
  const data = new Uint8Array(Math.floor(clean.length / 2));
  for (let i = 0; i < data.length; i++) {
    data[i] = parseInt(clean.slice(i * 2, i * 2 + 2), 16);
  }
  return data;
}

export function generateSalt(): string {
  const salt = new Uint8Array(16);
  if (typeof globalThis.crypto !== 'undefined' && typeof globalThis.crypto.getRandomValues === 'function') {
    globalThis.crypto.getRandomValues(salt);
  } else {
    for (let i = 0; i < salt.length; i++) {
      salt[i] = Math.floor(Math.random() * 256);
    }
  }
  return bytesToHex(salt);
}

export function hashPassword(password: string, saltHex: string): string {
  const passwordBytes = new TextEncoder().encode(password);
  const saltBytes = hexToBytes(saltHex);
  const derived = pbkdf2(sha256, passwordBytes, saltBytes, {
    c: ITERATIONS,
    dkLen: KEY_LENGTH_BYTES,
  });
  return bytesToHex(derived);
}

export function verifyPassword(
  password: string,
  saltHex: string,
  expectedHashHex: string
): boolean {
  if (!saltHex || !saltHex.trim() || !expectedHashHex || !expectedHashHex.trim()) {
    return false;
  }
  const computed = hashPassword(password, saltHex);
  return computed.toLowerCase() === expectedHashHex.trim().toLowerCase();
}
