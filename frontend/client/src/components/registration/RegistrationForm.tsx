// Adapted from modl-gg/landing RegistrationForm.tsx (AGPL-3.0-only).
import { useState } from 'react';
import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { registrationSchema, type RegistrationValues } from './registration-schema';
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@modl-gg/shared-web/components/ui/form';
import { Input } from '@modl-gg/shared-web/components/ui/input';
import { Button } from '@modl-gg/shared-web/components/ui/button';
import { getApiUrl } from '@/lib/api';
import { cn } from '@modl-gg/shared-web/lib/utils';

const fields = [
  { name: 'serverName', label: 'Server Name', placeholder: 'My Awesome Server', type: 'text', autoComplete: 'organization' },
  { name: 'username', label: 'Owner username', placeholder: 'owner', type: 'text', autoComplete: 'username' },
  { name: 'password', label: 'Password', placeholder: 'At least 8 characters', type: 'password', autoComplete: 'new-password' },
  { name: 'confirmPassword', label: 'Confirm Password', placeholder: 'Enter your password again', type: 'password', autoComplete: 'new-password' },
] as const;

export default function RegistrationForm() {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState('');
  const form = useForm<RegistrationValues>({ resolver: zodResolver(registrationSchema), mode: 'onChange',
    defaultValues: { serverName: '', username: '', password: '', confirmPassword: '' } });
  async function onSubmit(values: RegistrationValues) {
    setError('');
    setIsSubmitting(true);
    try {
      const response = await fetch(getApiUrl('/v1/public/registration'), {
        method: 'POST', credentials: 'include', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ serverName: values.serverName, username: values.username, password: values.password }),
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
      <nav className="bg-background/90 backdrop-blur border-b border-gray-800 p-4">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between items-center">
            <a href="/" className="font-brand text-2xl tracking-tight shrink-0" aria-label="modl home">
              <span className="text-primary">modl</span><span className="text-foreground/70">.gg</span>
            </a>
          </div>
        </div>
      </nav>
      <div className="flex items-center justify-center p-4 sm:p-8 lg:p-12">
        <div className="w-full max-w-md">
          <div className="text-center mb-8"><h1 className="text-3xl font-bold mb-2">Register your server</h1>
            <p className="text-muted-foreground">Create your owner account to finish setting up your self-hosted moderation panel.</p></div>
          <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6">
              {fields.map(item => <FormField key={item.name} control={form.control} name={item.name} render={({ field, fieldState }) => (
                <FormItem><FormLabel>{item.label} <span className="text-red-500">*</span></FormLabel><FormControl>
                  <Input {...field} type={item.type} placeholder={item.placeholder} autoComplete={item.autoComplete}
                    disabled={isSubmitting}
                    className={cn('px-4 py-3 rounded-md bg-card/50 border text-foreground focus:outline-none',
                      fieldState.error ? 'border-red-500 focus:border-red-500' : 'border-gray-700 focus:border-primary')} />
                </FormControl><FormMessage /></FormItem>
              )} />)}
              {error && <p role="alert" className="text-destructive">{error}</p>}
              <Button type="submit" className="w-full bg-primary hover:bg-primary/90 text-primary-foreground py-3" disabled={isSubmitting}>{isSubmitting ? 'Registering…' : 'Register'}</Button>
            </form>
          </Form>
        </div>
      </div>
    </div>
  );
}
