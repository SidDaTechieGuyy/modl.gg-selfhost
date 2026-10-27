import { useEffect, useState } from 'react';
import { Button } from '@modl-gg/shared-web/components/ui/button';
import { Input } from '@modl-gg/shared-web/components/ui/input';
import { Label } from '@modl-gg/shared-web/components/ui/label';
import { AlertDialog, AlertDialogContent, AlertDialogHeader, AlertDialogTitle, AlertDialogDescription,
  AlertDialogFooter, AlertDialogCancel } from '@modl-gg/shared-web/components/ui/alert-dialog';
import { getApiUrl } from '@/lib/api';

export default function FactoryResetSettings() {
  const [owner, setOwner] = useState(false);
  const [step, setStep] = useState(0);
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [busy, setBusy] = useState(false);
  const [waiting, setWaiting] = useState(false);
  const [error, setError] = useState('');
  useEffect(() => {
    const controller = new AbortController();
    fetch(getApiUrl('/v1/panel/settings/factory-reset'), { credentials: 'include', signal: controller.signal })
      .then(async response => { if (response.ok) setOwner((await response.json()).owner === true); })
      .catch(() => { /* Never expose a destructive action when authorization cannot be checked. */ });
    return () => controller.abort();
  }, []);

  useEffect(() => {
    if (!waiting) return;
    let stopped = false;
    let timer: ReturnType<typeof setTimeout>;
    const deadline = Date.now() + 180_000;
    async function poll() {
      if (stopped) return;
      try {
        const response = await fetch(getApiUrl('/v1/public/registration/status'), { cache: 'no-store', signal: AbortSignal.timeout(5000) });
        if (response.ok) {
          const status = await response.json();
          if (status.registrationOpen === true) { window.location.assign('/'); return; }
        }
      } catch { /* The backend is expected to be unavailable while restarting. */ }
      if (stopped) return;
      if (Date.now() >= deadline) {
        setError('The backend has not finished restarting. Check Docker logs, then reopen the site. Do not submit another reset.');
        return;
      }
      timer = setTimeout(poll, 2000);
    }
    timer = setTimeout(poll, 2000);
    return () => { stopped = true; clearTimeout(timer); };
  }, [waiting]);

  function close() {
    if (busy || waiting) return;
    setStep(0); setPassword(''); setConfirmation(''); setError('');
  }
  async function reset(event: React.FormEvent) {
    event.preventDefault();
    if (confirmation !== 'RESET EVERYTHING' || !password || busy || waiting) return;
    setBusy(true); setError('');
    try {
      const response = await fetch(getApiUrl('/v1/panel/settings/factory-reset'), {
        method: 'POST', credentials: 'include', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ password, confirmation }), signal: AbortSignal.timeout(15000),
      });
      if (!response.ok) {
        const body = await response.json().catch(() => ({}));
        throw new Error(body.message || body.error || `Reset was rejected (${response.status}).`);
      }
      setPassword(''); setWaiting(true);
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : 'Unable to contact the server.');
    } finally { setBusy(false); }
  }
  if (!owner) return null;
  return (
    <section className="mt-8 rounded-lg border border-destructive/50 p-6 space-y-3">
      <h2 className="text-lg font-semibold text-destructive">Factory reset</h2>
      <p className="text-sm text-muted-foreground">Permanently delete all instance data and return to first-boot registration. Back up your database and uploads first.</p>
      <Button type="button" variant="destructive" onClick={() => setStep(1)}>Reset everything</Button>
      <AlertDialog open={step > 0} onOpenChange={open => { if (!open) close(); }}>
        <AlertDialogContent className="w-[calc(100%-2rem)] max-h-[90dvh] overflow-y-auto rounded-xl sm:rounded-xl">
          <AlertDialogHeader>
            <AlertDialogTitle>{waiting ? 'Resetting your instance' : step === 1 ? 'Delete everything on this instance?' : 'Confirm permanent factory reset'}</AlertDialogTitle>
            <AlertDialogDescription>
              {waiting ? 'The backend is restarting. This page will return to registration when it is ready.' :
                'This removes the owner, staff accounts, sessions, API keys, settings, players, punishments, tickets, audit logs, replays, training data, and all uploads. This cannot be undone. Docker configuration and backups are kept.'}
            </AlertDialogDescription>
          </AlertDialogHeader>
          {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
          {!waiting && (step === 1 ? <AlertDialogFooter className="gap-3 sm:space-x-0">
            <AlertDialogCancel className="mt-0" onClick={close}>Cancel</AlertDialogCancel>
            <Button variant="destructive" onClick={() => setStep(2)}>Continue to confirmation</Button>
          </AlertDialogFooter> : <form onSubmit={reset} className="space-y-4">
            <div className="space-y-2"><Label htmlFor="reset-confirmation">Type RESET EVERYTHING</Label>
              <Input id="reset-confirmation" value={confirmation} onChange={event => setConfirmation(event.target.value)} autoComplete="off" disabled={busy} /></div>
            <div className="space-y-2"><Label htmlFor="reset-password">Owner password</Label>
              <Input id="reset-password" type="password" autoComplete="current-password" value={password} onChange={event => setPassword(event.target.value)} disabled={busy} /></div>
            <AlertDialogFooter className="gap-3 sm:space-x-0">
              <Button type="button" variant="outline" disabled={busy} onClick={close}>Cancel</Button>
              <Button type="submit" variant="destructive" disabled={busy || confirmation !== 'RESET EVERYTHING' || !password}>{busy ? 'Requesting reset…' : 'Permanently reset everything'}</Button>
            </AlertDialogFooter>
          </form>)}
        </AlertDialogContent>
      </AlertDialog>
    </section>
  );
}
