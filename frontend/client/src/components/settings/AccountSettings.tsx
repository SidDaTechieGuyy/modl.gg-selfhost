import React, { useState, useEffect } from 'react';
import { Loader2, LogOut, Monitor, Smartphone } from 'lucide-react';
import PasskeySettings from './PasskeySettings';
import { Button } from '@modl-gg/shared-web/components/ui/button';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@modl-gg/shared-web/components/ui/alert-dialog';
import { Input } from '@modl-gg/shared-web/components/ui/input';
import { Label } from '@modl-gg/shared-web/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@modl-gg/shared-web/components/ui/select';
import { useToast } from '@modl-gg/shared-web/hooks/use-toast';
import { useAuth } from '@/hooks/use-auth';
import { useTranslation } from 'react-i18next';
import { apiFetch } from '@/lib/api';
import { SUPPORTED_LANGUAGES } from '@/lib/languages';
import { PRETTY_DATE_FORMAT, formatPrettyDateTime } from '@/utils/date-utils';

interface AccountSettingsProps {
  profileUsername: string;
  setProfileUsername: (value: string) => void;
  minecraftUsername?: string;
  userRole?: string;
  language: string;
  setLanguage: (value: string) => void;
  dateFormat: string;
  setDateFormat: (value: string) => void;
}

interface SessionInfo {
  id: string;
  ipAddress: string | null;
  userAgent: string | null;
  createdAt: string | null;
  expiresAt: string | null;
  isCurrent: boolean;
}

function parseUserAgent(ua: string): { browser: string; os: string } {
  const browser = /Edg/.test(ua) ? 'Edge'
    : /Chrome/.test(ua) ? 'Chrome'
    : /Firefox/.test(ua) ? 'Firefox'
    : /Safari/.test(ua) ? 'Safari'
    : 'Unknown Browser';
  const os = /Windows/.test(ua) ? 'Windows'
    : /Mac/.test(ua) ? 'macOS'
    : /Linux/.test(ua) ? 'Linux'
    : /Android/.test(ua) ? 'Android'
    : /iPhone|iPad/.test(ua) ? 'iOS'
    : 'Unknown OS';
  return { browser, os };
}

function isMobileUserAgent(ua: string): boolean {
  return /Android|iPhone|iPad|Mobile/.test(ua);
}

function SessionsSection() {
  const { t } = useTranslation();
  const { toast } = useToast();
  const { logout, signOutAllSessions } = useAuth();
  const [sessions, setSessions] = useState<SessionInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [pendingSession, setPendingSession] = useState<SessionInfo | null>(null);
  const [removingId, setRemovingId] = useState<string | null>(null);
  const [signAllOutOpen, setSignAllOutOpen] = useState(false);

  useEffect(() => {
    apiFetch('/v1/panel/auth/sessions')
      .then(r => {
        if (!r.ok) throw new Error('Failed to load sessions');
        return r.json();
      })
      .then((data) => {
        setSessions(Array.isArray(data?.sessions) ? data.sessions : []);
        setLoadError(false);
      })
      .catch(() => {
        setSessions([]);
        setLoadError(true);
      })
      .finally(() => setLoading(false));
  }, []);

  const confirmRemoveSession = async () => {
    const session = pendingSession;
    setPendingSession(null);
    if (!session) return;

    if (session.isCurrent) {
      logout();
      return;
    }

    setRemovingId(session.id);
    try {
      const response = await apiFetch(`/v1/panel/auth/sessions/${encodeURIComponent(session.id)}`, { method: 'DELETE' });
      if (!response.ok && response.status !== 404) throw new Error('Failed to remove session');
      setSessions(prev => prev.filter(s => s.id !== session.id));
      if (response.ok) {
        toast({ title: t('settings.sessions.removed') });
      }
    } catch {
      toast({ title: t('settings.sessions.removeFailed'), variant: 'destructive' });
    } finally {
      setRemovingId(null);
    }
  };

  return (
    <div className="space-y-3">
      <div className="flex items-center gap-3">
        <Monitor className="h-4 w-4 text-muted-foreground shrink-0" />
        <h3 className="text-base font-medium">{t('settings.sessions.title')}</h3>
      </div>

      {loading ? (
        <p className="text-sm text-muted-foreground">{t('settings.sessions.loading')}</p>
      ) : loadError ? (
        <p className="text-sm text-destructive">{t('settings.sessions.loadFailed')}</p>
      ) : sessions.length === 0 ? (
        <p className="text-sm text-muted-foreground">{t('settings.sessions.empty')}</p>
      ) : (
        <div className="space-y-2">
          {sessions.map((s) => {
            const ua = s.userAgent ?? '';
            const { browser, os } = parseUserAgent(ua);
            const mobile = isMobileUserAgent(ua);
            const DeviceIcon = mobile ? Smartphone : Monitor;
            const added = s.createdAt
              ? new Date(s.createdAt).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' })
              : '—';
            return (
              <div key={s.id} className="flex items-center gap-3 rounded-md border p-3">
                <DeviceIcon className="h-4 w-4 text-muted-foreground shrink-0" />
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium">
                    {browser} on {os}
                    {s.isCurrent && (
                      <span className="ml-2 text-xs bg-primary/10 text-primary rounded px-1.5 py-0.5">{t('settings.sessions.current')}</span>
                    )}
                  </p>
                  <p className="text-xs text-muted-foreground truncate">
                    {s.ipAddress ?? 'Unknown IP'} · Added {added}
                  </p>
                </div>
                <Button
                  variant="ghost"
                  size="icon"
                  className="h-8 w-8 shrink-0 text-muted-foreground hover:text-destructive"
                  aria-label={t('settings.sessions.signOutSession')}
                  title={t('settings.sessions.signOutSession')}
                  disabled={removingId === s.id}
                  onClick={() => setPendingSession(s)}
                >
                  {removingId === s.id ? <Loader2 className="h-4 w-4 animate-spin" /> : <LogOut className="h-4 w-4" />}
                </Button>
              </div>
            );
          })}
        </div>
      )}

      <Button
        variant="outline"
        size="sm"
        onClick={() => setSignAllOutOpen(true)}
        disabled={loading || sessions.length === 0}
      >
        <LogOut className="h-4 w-4 mr-2" />
        {t('settings.sessions.signAllOut')}
      </Button>

      <AlertDialog open={pendingSession !== null} onOpenChange={(open) => { if (!open) setPendingSession(null); }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t('settings.sessions.removeTitle')}</AlertDialogTitle>
            <AlertDialogDescription>
              {pendingSession?.isCurrent
                ? t('settings.sessions.removeCurrentDescription')
                : t('settings.sessions.removeDescription')}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{t('common.cancel')}</AlertDialogCancel>
            <AlertDialogAction onClick={confirmRemoveSession}>{t('settings.sessions.removeAction')}</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <AlertDialog open={signAllOutOpen} onOpenChange={setSignAllOutOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t('settings.sessions.signAllOutTitle')}</AlertDialogTitle>
            <AlertDialogDescription>{t('settings.sessions.signAllOutDescription')}</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{t('common.cancel')}</AlertDialogCancel>
            <AlertDialogAction onClick={() => signOutAllSessions()}>{t('settings.sessions.signAllOut')}</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}

const AccountSettings = ({
  profileUsername,
  setProfileUsername,
  minecraftUsername,
  userRole,
  language,
  setLanguage,
  dateFormat,
  setDateFormat
}: AccountSettingsProps) => {
  const { logout } = useAuth();
  const { t } = useTranslation();
  return (
    <div className="space-y-6 p-2">
      <div className="flex items-center justify-between mb-4">
        <h3 className="text-base font-large">{t('settings.profileSettings')}</h3>
        <Button variant="destructive" size="sm" onClick={logout}>
          <LogOut className="h-4 w-4 mr-2" />
          {t('common.signOut')}
        </Button>
      </div>

      <div className="grid grid-cols-[1fr_1fr_8rem] gap-0">
        {/* LEFT: form fields */}
        <div className="space-y-5 pr-6">
          <div className="flex gap-3">
            <Label htmlFor="username" className="w-36 text-sm pt-2.5 shrink-0">Username</Label>
            <div className="flex-1 max-w-xs">
              <Input
                id="username"
                type="text"
                value={profileUsername}
                onChange={(e) => setProfileUsername(e.target.value)}
                placeholder="Enter username"
                autoComplete="username"
                maxLength={32}
              />
              <p className="text-xs text-muted-foreground mt-1.5">
                Use this username to log in. Use 3–32 letters, numbers, underscores or hyphens.
              </p>
            </div>
          </div>

          {minecraftUsername && (
            <div className="flex gap-3">
              <Label htmlFor="minecraft-username" className="w-36 text-sm pt-2.5 shrink-0">{t('settings.minecraftUsername')}</Label>
              <div className="flex-1 max-w-xs">
                <Input
                  id="minecraft-username"
                  type="text"
                  value={minecraftUsername}
                  disabled
                  className="bg-muted text-muted-foreground"
                />
                <p className="text-xs text-muted-foreground mt-1.5">
                  Your linked Minecraft account. {userRole === 'Super Admin' || userRole === 'Admin' ? t('settings.minecraftChangeAdmin') : t('settings.minecraftChangeContact')}
                </p>
              </div>
            </div>
          )}


          <div className="flex gap-3">
            <Label htmlFor="language" className="w-36 text-sm pt-2.5 shrink-0">{t('settings.language')}</Label>
            <div className="flex-1 max-w-xs">
              <Select value={language} onValueChange={setLanguage}>
                <SelectTrigger id="language">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {SUPPORTED_LANGUAGES.map((language) => (
                    <SelectItem key={language.code} value={language.code}>{language.nativeName}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <p className="text-xs text-muted-foreground mt-1.5">
                {t('settings.languageDescription')}
              </p>
            </div>
          </div>

          <div className="flex gap-3">
            <Label htmlFor="date-format" className="w-36 text-sm pt-2.5 shrink-0">{t('settings.dateFormat')}</Label>
            <div className="flex-1 max-w-xs">
              <Select value={dateFormat} onValueChange={setDateFormat}>
                <SelectTrigger id="date-format">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={PRETTY_DATE_FORMAT}>{formatPrettyDateTime(new Date(2026, 0, 15, 12, 0))}</SelectItem>
                  <SelectItem value="MM/DD/YYYY">MM/DD/YYYY HH:mm</SelectItem>
                  <SelectItem value="DD/MM/YYYY">DD/MM/YYYY HH:mm</SelectItem>
                  <SelectItem value="YYYY-MM-DD">YYYY-MM-DD HH:mm</SelectItem>
                </SelectContent>
              </Select>
              <p className="text-xs text-muted-foreground mt-1.5">
                {t('settings.dateFormatDescription')}
              </p>
            </div>
          </div>
        </div>

        {/* MIDDLE: passkeys + sessions */}
        <div className="border-l border-border px-6 space-y-6">
          <PasskeySettings />
          <SessionsSection />
        </div>

        {/* RIGHT: small offset column aligned with sign-out button */}
        <div />
      </div>
    </div>
  );
};

export default AccountSettings;
