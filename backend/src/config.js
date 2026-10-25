import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

export const config = {
  port: Number(process.env.PORT ?? '3001'),
  baseDomain: process.env.BASE_DOMAIN ?? 'localhost',
  mongoUri: process.env.MONGODB_URI ?? 'mongodb://mongodb:27017/modlgg',
  jwtSecret: process.env.JWT_SECRET ?? 'dev-secret-change-me',
  sessionSecret: process.env.SESSION_SECRET ?? 'dev-session-secret',
  corsAllowedOrigins: process.env.CORS_ALLOWED_ORIGINS ?? '*',
  uploadDir: process.env.UPLOAD_DIR ?? path.join(__dirname, '../uploads'),
};
