import { v2 as cloudinary } from 'cloudinary';
import { z } from 'zod';
import type { Environment } from '../../config/env.js';
import { AppError } from '../../lib/app-error.js';

export class CloudinaryStorage {
  constructor(private readonly env: Environment) {}
  private credentials() {
    if (!this.env.CLOUDINARY_CLOUD_NAME || !this.env.CLOUDINARY_API_KEY || !this.env.CLOUDINARY_API_SECRET) throw new AppError('storage_unavailable', 503);
    return { cloud_name: this.env.CLOUDINARY_CLOUD_NAME, api_key: this.env.CLOUDINARY_API_KEY, api_secret: this.env.CLOUDINARY_API_SECRET, secure: true };
  }
  sign(storageKey: string, now: Date) {
    const credentials = this.credentials();
    const params = { public_id: storageKey, timestamp: Math.floor(now.getTime() / 1000), overwrite: false, allowed_formats: 'jpg,png,webp', transformation: 'c_limit,w_1280,h_1280/q_80/f_jpg/fl_strip_profile' };
    return {
      uploadUrl: `https://api.cloudinary.com/v1_1/${credentials.cloud_name}/image/upload`,
      fields: { ...Object.fromEntries(Object.entries(params).map(([key, value]) => [key, String(value)])), api_key: credentials.api_key, signature: cloudinary.utils.api_sign_request(params, credentials.api_secret) },
    };
  }
  async verify(storageKey: string) {
    const credentials = this.credentials();
    const raw: unknown = await cloudinary.api.resource(storageKey, { ...credentials, resource_type: 'image', type: 'upload' });
    const photo = z.object({ public_id: z.string(), format: z.string(), resource_type: z.string(), width: z.number(), height: z.number(), bytes: z.number(), secure_url: z.url() }).parse(raw);
    if (photo.public_id !== storageKey || photo.resource_type !== 'image' || photo.format !== 'jpg' || photo.width > 1280 || photo.height > 1280 || photo.bytes > 5 * 1024 * 1024) throw new AppError('invalid_photo', 400);
    return photo.secure_url;
  }
  async remove(storageKey: string) {
    const result: unknown = await cloudinary.uploader.destroy(storageKey, { ...this.credentials(), resource_type: 'image', type: 'upload', invalidate: true });
    const response = z.object({ result: z.string() }).parse(result);
    if (!['ok', 'not found'].includes(response.result)) throw new AppError('storage_unavailable', 503);
  }
}
