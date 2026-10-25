import mongoose from 'mongoose';
import { config } from './config.js';

export async function connectDatabase() {
  await mongoose.connect(config.mongoUri, {
    serverSelectionTimeoutMS: 5000,
  });

  const userSchema = new mongoose.Schema(
    {
      username: { type: String, required: true, unique: true, trim: true },
      email: { type: String, required: true, unique: true, trim: true, lowercase: true },
      passwordHash: { type: String, required: true },
      role: { type: String, enum: ['owner'], default: 'owner' },
    },
    { timestamps: true },
  );

  const uploadSchema = new mongoose.Schema(
    {
      filename: { type: String, required: true },
      originalName: { type: String, required: true },
      size: { type: Number, required: true },
      url: { type: String, required: true },
      ownerId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true },
    },
    { timestamps: true },
  );

  return {
    User: mongoose.models.User ?? mongoose.model('User', userSchema),
    Upload: mongoose.models.Upload ?? mongoose.model('Upload', uploadSchema),
  };
}
