// Adapted from modl-gg/landing RegistrationForm.tsx (AGPL-3.0-only).
import { useState } from 'react';
import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@modl-gg/shared-web/components/ui/form';
import { Input } from '@modl-gg/shared-web/components/ui/input';
import { Button } from '@modl-gg/shared-web/components/ui/button';
import { getApiUrl } from '@/lib/api';

const registrationSchema = z.object({
  serverName: z.string().trim().min(3, 'Server name must contain at least 3 characters').max(100),
  username: z.string().regex(/^[A-Za-z0-9_-]{3,32}$/, 'Use 3–32 letters, numbers, underscores or hyphens'),
  email: z.string().trim().email('Please enter a valid email address').max(254),
  password: z.string().min(12, 'Use at least 12 characters').refine(value => new TextEncoder().encode(value).length <= 72, 'Use at most 72 UTF-8 bytes'),
});
type RegistrationValues = z.infer<typeof registrationSchema>;
const fields = [
  { name: 'serverName', label: 'Server name', placeholder: 'My Awesome Server', type: 'text', autoComplete: 'organization' },
  { name: 'username', label: 'Owner username', placeholder: 'owner', type: 'text', autoComplete: 'username' },
  { name: 'email', label: 'Owner email', placeholder: 'you@example.com', type: 'email', autoComplete: 'email' },
  { name: 'password', label: 'Password', placeholder: 'At least 12 characters', type: 'password', autoComplete: 'new-password' },
] as const;

export default function RegistrationForm() {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState('');
  const form = useForm<RegistrationValues>({ resolver: zodResolver(registrationSchema), mode: 'onChange',
    defaultValues: { email: '', serverName: '', username: '', password: '' } });
  async function onSubmit(values: RegistrationValues) {
    setError('');
    setIsSubmitting(true);
    try {
      const response = await fetch(getApiUrl('/v1/public/registration'), {
        method: 'POST', credentials: 'include', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(values),
      });
      if (!response.ok) {
        const data = await response.json().catch(() => ({}));
        if (response.status === 409) { window.location.assign('/auth'); return; }
        throw new Error(data.message || data.error || 'Registration failed. Please try again.');
      }
      window.location.assign('/panel');
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : 'Unable to reach the server. Please try again.');
    } finally { setIsSubmitting(false); }
  }
  return (
    <div className="min-h-screen flex flex-col">
      <nav className="bg-background/90 backdrop-blur border-b p-4"><span className="font-brand text-2xl text-primary">modl</span></nav>
      <div className="flex items-center justify-center p-4 sm:p-8 lg:p-12">
        <div className="w-full max-w-md">
          <div className="text-center mb-8"><h1 className="text-3xl font-bold mb-2">Register your server</h1>
            <p className="text-muted-foreground">Create the owner account for this instance.</p></div>
          <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6">
              {fields.map(item => <FormField key={item.name} control={form.control} name={item.name} render={({ field }) => (
                <FormItem><FormLabel>{item.label}</FormLabel><FormControl>
                  <Input {...field} type={item.type} placeholder={item.placeholder} autoComplete={item.autoComplete}
                    className="px-4 py-3 rounded-md bg-card/50 border text-foreground" />
                </FormControl><FormMessage /></FormItem>
              )} />)}
              {error && <p role="alert" className="text-destructive">{error}</p>}
              <Button type="submit" className="w-full" disabled={isSubmitting}>{isSubmitting ? 'Registering…' : 'Register'}</Button>
            </form>
          </Form>
        </div>
      </div>
    </div>
  );
}
