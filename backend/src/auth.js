import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import { config } from './config.js';

export async function hashPassword(password) {
  return bcrypt.hash(password, 12);
}

export async function verifyPassword(password, passwordHash) {
  return bcrypt.compare(password, passwordHash);
}

export function signJwt(payload) {
  return jwt.sign(payload, config.jwtSecret, { expiresIn: '7d' });
}

export function verifyJwt(token) {
  return jwt.verify(token, config.jwtSecret);
}

export function serializeUser(user) {
  if (!user) return null;
  return {
    id: user._id?.toString?.() ?? user.id,
    username: user.username,
    email: user.email,
    role: user.role,
    createdAt: user.createdAt,
  };
}

export function buildAuthRouter({ User }) {
  const router = {
    async register({ username, email, password }) {
      if (!username || !password) {
        throw new Error('Username and password are required.');
      }

      const normalizedUsername = username.trim();
      const normalizedEmail = (email ?? '').trim().toLowerCase();

      if (!normalizedUsername || !normalizedEmail) {
        throw new Error('Email is required.');
      }

      const existingUser = await User.findOne({
        $or: [{ username: normalizedUsername }, { email: normalizedEmail }],
      });

      if (existingUser) {
        throw new Error('A user with that username or email already exists.');
      }

      const totalUsers = await User.countDocuments();
      if (totalUsers > 0) {
        throw new Error('Registration is closed after the first owner is created.');
      }

      const passwordHash = await hashPassword(password);
      const user = await User.create({
        username: normalizedUsername,
        email: normalizedEmail,
        passwordHash,
        role: 'owner',
      });

      const token = signJwt({ sub: user._id.toString(), role: user.role, username: user.username });
      return {
        token,
        user: serializeUser(user),
      };
    },

    async login({ usernameOrEmail, password }) {
      if (!usernameOrEmail || !password) {
        throw new Error('Username or email and password are required.');
      }

      const user = await User.findOne({
        $or: [{ username: usernameOrEmail }, { email: usernameOrEmail.toLowerCase() }],
      });

      if (!user) {
        throw new Error('Invalid username or password.');
      }

      const passwordMatches = await verifyPassword(password, user.passwordHash);
      if (!passwordMatches) {
        throw new Error('Invalid username or password.');
      }

      const token = signJwt({ sub: user._id.toString(), role: user.role, username: user.username });
      return {
        token,
        user: serializeUser(user),
      };
    },

    async getOwnerExists() {
      const count = await User.countDocuments();
      return count > 0;
    },
  };

  return router;
}
