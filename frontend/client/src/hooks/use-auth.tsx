import { startAuthentication, type PublicKeyCredentialRequestOptionsJSON } from '@simplewebauthn/browser';
import { isWebAuthnCancellation, unwrapPublicKeyOptions, type MaybePublicKeyWrapped } from '@/utils/webauthn';
import { createContext, type ReactNode, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { useLocation } from 'wouter';
import { useToast } from '@modl-gg/shared-web/hooks/use-toast';
import { getApiUrl } from '@/lib/api';
import { DEFAULT_DATE_FORMAT, setDateLocale, setDateFormat } from '@/utils/date-utils';

interface User {
  id: string;
  accountId: string;
  username: string;
  role: 'Super Admin' | 'Admin' | 'Moderator' | 'Helper';
  minecraftUsername?: string;
  language?: string;
  dateFormat?: string;
}

type AuthContextType = {
  user: User | null;
  isLoading: boolean;
  refreshUser: () => Promise<void>;
  login: (identifier: string, password: string) => Promise<boolean>;
  loginWithDiscoverablePasskey: () => Promise<boolean>;
  logout: () => void;
  signOutAllSessions: () => void;
};
export const AuthContext = createContext<AuthContextType | null>(null);

async function authFetch(path: string, options: RequestInit = {}) {
  return fetch(getApiUrl(path), { ...options, credentials: 'include' });
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [, navigate] = useLocation();
  const { toast } = useToast();
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const fetchUser = useCallback(async () => {
    const response = await authFetch('/v1/panel/auth/me');
    if (response.status === 401 || response.status === 403) return null;
    if (!response.ok) throw new Error('Unable to check your session. Please try again.');
    const { email: accountId, ...profile } = await response.json() as Omit<User, 'accountId'> & { email: string };
    return { ...profile, accountId };
  }, []);
  const refreshUser = useCallback(async () => { setUser(await fetchUser()); }, [fetchUser]);
  useEffect(() => {
    let active = true;
    fetchUser().then(value => { if (active) setUser(value); }).catch(error => {
      toast({ title: 'Session check failed', description: String(error), variant: 'destructive' });
    }).finally(() => { if (active) setIsLoading(false); });
    return () => { active = false; };
  }, [fetchUser, toast]);
  useEffect(() => {
    setDateLocale(user?.language || 'en');
    setDateFormat(user?.dateFormat || DEFAULT_DATE_FORMAT);
  }, [user?.language, user?.dateFormat]);

  const login = useCallback(async (identifier: string, password: string) => {
    try {
      const response = await authFetch('/v1/panel/auth/login', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ identifier, password }),
      });
      if (!response.ok) {
        const data = await response.json().catch(() => ({}));
        throw new Error(data.message || data.error || 'Login failed. Please try again.');
      }
      await refreshUser();
      navigate('/panel');
      return true;
    } catch (error) {
      toast({ title: 'Unable to log in', description: error instanceof Error ? error.message : 'Network error', variant: 'destructive' });
      return false;
    }
  }, [refreshUser, navigate, toast]);

  const loginWithDiscoverablePasskey = useCallback(async () => {
    try {
      const start = await authFetch('/v1/panel/auth/webauthn/login/start', { method: 'POST' });
      if (!start.ok) throw new Error('Unable to start passkey login.');
      const data = await start.json() as { challengeId: string; options: MaybePublicKeyWrapped<PublicKeyCredentialRequestOptionsJSON> };
      const assertion = await startAuthentication({ optionsJSON: unwrapPublicKeyOptions(data.options) });
      const response = await authFetch('/v1/panel/auth/webauthn/login/verify', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ challengeId: data.challengeId, response: JSON.stringify(assertion) }),
      });
      if (!response.ok) throw new Error('Passkey login failed.');
      await refreshUser(); navigate('/panel'); return true;
    } catch (error) {
      if (!isWebAuthnCancellation(error)) toast({ title: 'Passkey login failed', description: error instanceof Error ? error.message : 'Please try again.', variant: 'destructive' });
      return false;
    }
  }, [refreshUser, navigate, toast]);

  const signOut = useCallback(async (all: boolean) => {
    try {
      const response = await authFetch(all ? '/v1/panel/auth/sessions' : '/v1/panel/auth/logout', { method: all ? 'DELETE' : 'POST' });
      if (!response.ok) throw new Error('Unable to log out. Please try again.');
      const remainingUser = await fetchUser();
      if (remainingUser) throw new Error('The session is still active. Please try again.');
      setUser(null);
      navigate('/auth');
    } catch (error) {
      toast({ title: 'Logout failed', description: error instanceof Error ? error.message : 'Network error', variant: 'destructive' });
    }
  }, [fetchUser, navigate, toast]);
  const logout = useCallback(() => { void signOut(false); }, [signOut]);
  const signOutAllSessions = useCallback(() => { void signOut(true); }, [signOut]);
  const value = useMemo(() => ({ user, isLoading, refreshUser, login, loginWithDiscoverablePasskey, logout, signOutAllSessions }),
    [user, isLoading, refreshUser, login, logout, signOutAllSessions]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within an AuthProvider');
  return context;
}
