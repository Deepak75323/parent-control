/** WebAuthn platform authenticators for parent portal camera gate. */

const DB_NAME = 'pcp-parent-webauthn';
const STORE = 'credentials';

export type ParentWebAuthnCredential = {
  credentialId: string;
  name: string;
  createdAt: string;
};

export type ParentWebAuthnRecord = {
  userId: string;
  credentials: ParentWebAuthnCredential[];
};

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, 1);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(STORE)) {
        db.createObjectStore(STORE, {keyPath: 'userId'});
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error ?? new Error('IndexedDB open failed'));
  });
}

async function readRecord(userId: string): Promise<ParentWebAuthnRecord | null> {
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE, 'readonly');
    const req = tx.objectStore(STORE).get(userId);
    req.onsuccess = () => resolve((req.result as ParentWebAuthnRecord | undefined) ?? null);
    req.onerror = () => reject(req.error ?? new Error('IndexedDB read failed'));
  });
}

async function writeRecord(record: ParentWebAuthnRecord): Promise<void> {
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE, 'readwrite');
    tx.objectStore(STORE).put(record);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error ?? new Error('IndexedDB write failed'));
  });
}

function bufferToB64Url(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  let binary = '';
  bytes.forEach(b => {
    binary += String.fromCharCode(b);
  });
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function b64UrlToBuffer(value: string): ArrayBuffer {
  const padded = value.replace(/-/g, '+').replace(/_/g, '/');
  const pad = padded.length % 4 === 0 ? '' : '='.repeat(4 - (padded.length % 4));
  const binary = atob(padded + pad);
  const out = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) {
    out[i] = binary.charCodeAt(i);
  }
  return out.buffer;
}

export function isWebAuthnAvailable(): boolean {
  return (
    typeof window !== 'undefined' &&
    typeof window.PublicKeyCredential !== 'undefined' &&
    typeof navigator.credentials?.create === 'function'
  );
}

export async function listParentWebAuthnCredentials(
  userId: string,
): Promise<ParentWebAuthnCredential[]> {
  const record = await readRecord(userId);
  return record?.credentials ?? [];
}

export async function hasParentWebAuthnCredentials(userId: string): Promise<boolean> {
  return (await listParentWebAuthnCredentials(userId)).length > 0;
}

/** Register a platform authenticator (fingerprint / Face ID / Windows Hello). */
export async function registerParentWebAuthn(
  userId: string,
  displayName: string,
  credentialName = 'Primary',
): Promise<ParentWebAuthnCredential> {
  if (!isWebAuthnAvailable()) {
    throw new Error('Fingerprint / biometric login is not supported in this browser');
  }
  const challenge = crypto.getRandomValues(new Uint8Array(32));
  const userIdBytes = new TextEncoder().encode(userId);
  const credential = (await navigator.credentials.create({
    publicKey: {
      challenge,
      rp: {name: 'Parent Control Portal', id: window.location.hostname},
      user: {
        id: userIdBytes,
        name: displayName || userId,
        displayName: displayName || 'Parent',
      },
      pubKeyCredParams: [
        {type: 'public-key', alg: -7},
        {type: 'public-key', alg: -257},
      ],
      authenticatorSelection: {
        authenticatorAttachment: 'platform',
        userVerification: 'required',
        residentKey: 'preferred',
      },
      timeout: 60_000,
      attestation: 'none',
    },
  })) as PublicKeyCredential | null;
  if (!credential) {
    throw new Error('Fingerprint registration was cancelled');
  }
  const entry: ParentWebAuthnCredential = {
    credentialId: bufferToB64Url(credential.rawId),
    name: credentialName.trim() || 'Fingerprint',
    createdAt: new Date().toISOString(),
  };
  const existing = (await readRecord(userId)) ?? {userId, credentials: []};
  existing.credentials = [...existing.credentials.filter(c => c.credentialId !== entry.credentialId), entry];
  await writeRecord(existing);
  return entry;
}

/** Assert any registered credential (user touches fingerprint). */
export async function assertParentWebAuthn(userId: string): Promise<boolean> {
  if (!isWebAuthnAvailable()) {
    return false;
  }
  const list = await listParentWebAuthnCredentials(userId);
  if (list.length === 0) {
    return false;
  }
  const challenge = crypto.getRandomValues(new Uint8Array(32));
  const allowCredentials = list.map(c => ({
    type: 'public-key' as const,
    id: b64UrlToBuffer(c.credentialId),
  }));
  const assertion = await navigator.credentials.get({
    publicKey: {
      challenge,
      allowCredentials,
      userVerification: 'required',
      timeout: 60_000,
      rpId: window.location.hostname,
    },
  });
  return Boolean(assertion);
}

export async function removeParentWebAuthnCredential(
  userId: string,
  credentialId: string,
): Promise<void> {
  const existing = await readRecord(userId);
  if (!existing) {
    return;
  }
  existing.credentials = existing.credentials.filter(c => c.credentialId !== credentialId);
  await writeRecord(existing);
}

/** Test helpers — clear store for a user. */
export async function clearParentWebAuthnForTests(userId: string): Promise<void> {
  const db = await openDb();
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction(STORE, 'readwrite');
    tx.objectStore(STORE).delete(userId);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error ?? new Error('delete failed'));
  });
}
