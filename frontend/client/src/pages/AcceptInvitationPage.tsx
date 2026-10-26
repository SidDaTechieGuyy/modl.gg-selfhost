import { useState, type FormEvent } from 'react';
import { useLocation } from 'wouter';
import PageContainer from '@/components/layout/PageContainer';
import { Button } from '@modl-gg/shared-web/components/ui/button';
import { Input } from '@modl-gg/shared-web/components/ui/input';
import { Label } from '@modl-gg/shared-web/components/ui/label';
import { getApiUrl } from '@/lib/api';

export default function AcceptInvitationPage() {
  const [, navigate] = useLocation();
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const token = new URLSearchParams(window.location.search).get('token');
  async function accept(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError('');
    try {
      const response = await fetch(getApiUrl('/v1/public/staff/invitations/accept'), {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ token, password }),
      });
      const data = await response.json();
      if (!response.ok) throw new Error(data.message || data.error || 'Unable to accept this invitation.');
      navigate('/auth');
    } catch (failure) { setError(failure instanceof Error ? failure.message : 'Network error.'); }
    finally { setBusy(false); }
  }
  return <PageContainer><form onSubmit={accept} className="mx-auto max-w-md space-y-4 p-8">
    <h1 className="text-2xl font-bold">Accept staff invitation</h1>
    <p>Create a password, then log in using the email address your invitation was created for.</p>
    {!token && <p role="alert">This invitation link is incomplete.</p>}
    <Label htmlFor="invite-password">Password</Label>
    <Input id="invite-password" type="password" autoComplete="new-password" minLength={12} required value={password} onChange={e => setPassword(e.target.value)} />
    {error && <p role="alert" className="text-destructive">{error}</p>}
    <Button type="submit" disabled={busy || !token}>{busy ? 'Creating account…' : 'Create account'}</Button>
  </form></PageContainer>;
}
