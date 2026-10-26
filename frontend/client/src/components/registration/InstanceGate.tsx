import { type ReactNode, useEffect, useState } from 'react';
import Registration from '@/pages/Registration';
import { getApiUrl } from '@/lib/api';

export default function InstanceGate({ children }: { children: ReactNode }) {
  const [registrationOpen, setRegistrationOpen] = useState<boolean | null>(null);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setError('');
    fetch(getApiUrl('/v1/public/registration/status'), { signal: controller.signal })
      .then(async response => {
        if (!response.ok) throw new Error('Unable to check instance setup.');
        const data = await response.json();
        if (typeof data.registrationOpen !== 'boolean') throw new Error('Invalid setup response.');
        if (!data.registrationOpen && window.location.pathname === '/register') window.location.replace('/auth');
        setRegistrationOpen(data.registrationOpen);
      }).catch(failure => {
        if (!controller.signal.aborted) setError(failure instanceof Error ? failure.message : 'Unable to reach the server.');
      });
    return () => controller.abort();
  }, [attempt]);
  if (error) return <main className="p-8 text-center"><p role="alert">{error}</p><button onClick={() => setAttempt(value => value + 1)}>Retry</button></main>;
  if (registrationOpen === null) return <main className="p-8 text-center" role="status">Connecting to your server…</main>;
  return registrationOpen ? <Registration /> : <>{children}</>;
}
