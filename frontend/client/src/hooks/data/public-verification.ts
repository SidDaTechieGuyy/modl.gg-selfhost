export type PublicRecordResource = 'ticket' | 'appeal';

export function setCookie(name: string, value: string, days: number) {
  const expires = new Date(Date.now() + days * 864e5).toUTCString();
  document.cookie = `${name}=${encodeURIComponent(value)}; expires=${expires}; path=/; SameSite=Lax`;
}

export function getCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp('(^| )' + name + '=([^;]+)'));
  const value = match?.[2];
  return value !== undefined ? decodeURIComponent(value) : null;
}

export function publicAuthTokenKey(resource: PublicRecordResource, id: string): string {
  return `${resource}_auth_${id}`;
}

export function withPublicAuthToken(path: string, resource: PublicRecordResource, id: string): string {
  const token = getCookie(publicAuthTokenKey(resource, id)) || new URLSearchParams(window.location.search).get('token');
  if (token) setCookie(publicAuthTokenKey(resource, id), token, 30);
  return token ? `${path}?token=${encodeURIComponent(token)}` : path;
}

