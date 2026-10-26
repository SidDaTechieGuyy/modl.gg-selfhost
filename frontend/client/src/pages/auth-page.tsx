import { useState, type FormEvent } from 'react';
import { Button } from '@modl-gg/shared-web/components/ui/button';
import { Input } from '@modl-gg/shared-web/components/ui/input';
import { Label } from '@modl-gg/shared-web/components/ui/label';
import { useAuth } from '@/hooks/use-auth';

export default function AuthPage() {
  const { login, loginWithDiscoverablePasskey } = useAuth();
  const [identifier, setIdentifier] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    try { await login(identifier, password); } finally { setBusy(false); }
  }
  return (
    <main className="min-h-screen flex items-center justify-center bg-background p-6">
      <form onSubmit={submit} className="w-full max-w-md space-y-6 rounded-xl border bg-card p-8">
        <h1 className="text-3xl font-bold">Log in to your panel</h1>
        <div className="space-y-2">
          <Label htmlFor="identifier">Username or email</Label>
          <Input id="identifier" autoComplete="username" required maxLength={254} value={identifier} onChange={e => setIdentifier(e.target.value)} />
        </div>
        <div className="space-y-2">
          <Label htmlFor="password">Password</Label>
          <Input id="password" type="password" autoComplete="current-password" required value={password} onChange={e => setPassword(e.target.value)} />
        </div>
        <Button className="w-full" type="submit" disabled={busy}>{busy ? 'Logging in…' : 'Log in'}</Button>
        {window.isSecureContext && typeof PublicKeyCredential !== 'undefined' && <Button type="button" variant="outline" className="w-full" disabled={busy}
          onClick={async () => { setBusy(true); try { await loginWithDiscoverablePasskey(); } finally { setBusy(false); } }}>Log in with a passkey</Button>}
      </form>
    </main>
  );
}
