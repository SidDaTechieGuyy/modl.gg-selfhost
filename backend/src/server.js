import fs from 'node:fs';
import path from 'node:path';
import multer from 'multer';
import { config } from './config.js';
import { buildAuthRouter } from './auth.js';

if (!fs.existsSync(config.uploadDir)) {
  fs.mkdirSync(config.uploadDir, { recursive: true });
}

const storage = multer.diskStorage({
  destination(req, file, cb) {
    cb(null, config.uploadDir);
  },
  filename(req, file, cb) {
    const safeName = file.originalname.replace(/[^a-zA-Z0-9._-]/g, '_');
    const suffix = `${Date.now()}-${Math.random().toString(16).slice(2)}`;
    cb(null, `${Date.now()}-${safeName}-${suffix}`);
  },
});

export const upload = multer({
  storage,
  limits: {
    fileSize: 10 * 1024 * 1024,
  },
});

export function buildUploadRouter({ User, Upload, requireAuth }) {
  const router = {
    async handleUpload(req, res) {
      if (!req.file) {
        return res.status(400).json({ error: 'A file is required.' });
      }

      const user = req.user;
      const url = `/uploads/${req.file.filename}`;
      const uploadDoc = await Upload.create({
        filename: req.file.filename,
        originalName: req.file.originalname,
        size: req.file.size,
        url,
        ownerId: user.id,
      });

      return res.status(201).json({
        id: uploadDoc._id,
        filename: uploadDoc.filename,
        originalName: uploadDoc.originalName,
        size: uploadDoc.size,
        url,
      });
    },
  };

  return router;
}

export function buildUploadRoutes({ app, requireAuth, Upload }) {
  app.use('/uploads', (req, res, next) => {
    const filePath = path.join(config.uploadDir, path.basename(req.path));
    if (!fs.existsSync(filePath)) {
      return res.status(404).json({ error: 'Upload not found.' });
    }
    return res.sendFile(filePath);
  });

  app.post('/api/uploads', requireAuth, upload.single('file'), async (req, res) => {
    if (!req.file) {
      return res.status(400).json({ error: 'A file is required.' });
    }

    const uploadDoc = await Upload.create({
      filename: req.file.filename,
      originalName: req.file.originalname,
      size: req.file.size,
      url: `/uploads/${req.file.filename}`,
      ownerId: req.user.id,
    });

    return res.status(201).json({
      id: uploadDoc._id,
      filename: uploadDoc.filename,
      originalName: uploadDoc.originalName,
      size: uploadDoc.size,
      url: uploadDoc.url,
    });
  });
}
