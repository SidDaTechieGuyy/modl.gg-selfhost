// Adapted from the original modl-gg/panel staff login layout.
import { useState, type FormEvent } from 'react';
import { Fingerprint, KeyRound, Loader2, UserRound } from 'lucide-react';
import { Button } from '@modl-gg/shared-web/components/ui/button';
import { Input } from '@modl-gg/shared-web/components/ui/input';
import { Label } from '@modl-gg/shared-web/components/ui/label';
import { Card, CardContent } from '@modl-gg/shared-web/components/ui/card';
import { useAuth } from '@/hooks/use-auth';
import { usePublicSettings } from '@/hooks/use-public-settings';

export default function AuthPage() {
  const { login, loginWithDiscoverablePasskey } = useAuth();
  const { data: publicSettings } = usePublicSettings();
  const [identifier, setIdentifier] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError('');
    try {
      if (!await login(identifier.trim(), password)) setError('Unable to log in. Check your credentials and try again.');
    } catch { setError('Unable to reach the server. Please try again.'); }
    finally { setBusy(false); }
  }
  async function passkeyLogin() {
    setBusy(true);
    setError('');
    try { await loginWithDiscoverablePasskey(); }
    catch { setError('Passkey login failed. Please try again.'); }
    finally { setBusy(false); }
  }
  return (
    <div className="min-h-screen flex items-center justify-center bg-background">
      <div className="max-w-md w-full p-4 md:p-10">
        <div className="flex flex-col justify-center">
          <div className="flex flex-col space-y-2 mb-8 text-center">
            <h1 className="text-3xl font-bold">Staff login</h1>
            <p className="text-muted-foreground">{publicSettings?.serverDisplayName || 'modl'} staff panel. Authorized personnel only.</p>
          </div>
          <Card className="shadow-card">
            <CardContent className="pt-6">
              <form onSubmit={submit} className="space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="identifier">Username</Label>
                  <div className="relative">
                    <UserRound className="absolute left-3 top-3 h-4 w-4 text-muted-foreground" />
                    <Input id="identifier" className="pl-10" placeholder="Username" autoComplete="username" required maxLength={32} disabled={busy} value={identifier} onChange={e => setIdentifier(e.target.value)} />
                  </div>
                </div>
                <div className="space-y-2">
                  <Label htmlFor="password">Password</Label>
                  <div className="relative">
                    <KeyRound className="absolute left-3 top-3 h-4 w-4 text-muted-foreground" />
                    <Input id="password" className="pl-10" placeholder="Password" type="password" autoComplete="current-password" required disabled={busy} value={password} onChange={e => setPassword(e.target.value)} />
                  </div>
                </div>
                {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
                <Button className="w-full mt-6" type="submit" disabled={busy}>{busy && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}{busy ? 'Logging in…' : 'Log in'}</Button>
                {window.isSecureContext && typeof PublicKeyCredential !== 'undefined' && <>
                  <div className="relative my-4">
                    <div className="absolute inset-0 flex items-center"><span className="w-full border-t" /></div>
                    <div className="relative flex justify-center text-xs uppercase"><span className="bg-card px-2 text-muted-foreground">or</span></div>
                  </div>
                  <Button type="button" variant="outline" className="w-full" disabled={busy} onClick={passkeyLogin}><Fingerprint className="mr-2 h-4 w-4" />Log in with a passkey</Button>
                </>}
              </form>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
}
