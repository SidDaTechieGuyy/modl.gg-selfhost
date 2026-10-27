import { z } from 'zod';

export const registrationSchema = z.object({
  serverName: z.string().trim().min(3, 'Server name must contain at least 3 characters').max(100),
  username: z.string().regex(/^[A-Za-z0-9_-]{3,32}$/, 'Use 3–32 letters, numbers, underscores or hyphens'),
  password: z.string().min(8, 'Use at least 8 characters').refine(value => new TextEncoder().encode(value).length <= 72, 'Use at most 72 UTF-8 bytes'),
  confirmPassword: z.string().min(1, 'Please confirm your password'),
}).refine(values => values.password === values.confirmPassword, {
  message: 'Passwords do not match', path: ['confirmPassword'],
});

export type RegistrationValues = z.infer<typeof registrationSchema>;
