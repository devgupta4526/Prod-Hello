import { v7 as uuid } from 'uuid';
import type { Database } from '../../lib/database.js';
import type { CloudinaryStorage } from './cloudinary.js';

/** Durable deletion retries survive provider outages and server restarts. */
export class MediaCleanup {
  constructor(private readonly db: Database, private readonly storage: CloudinaryStorage) {}

  async run(now: Date) {
    // Two hours exceeds upload confirmation/signature lifetime; late confirmed uploads are excluded.
    const expired = await this.db.photoUpload.findMany({ where: { confirmedAt: null, createdAt: { lt: new Date(now.getTime() - 7200000) } }, take: 25 });
    for (const upload of expired) {
      await this.db.$transaction(async (tx) => {
        await tx.$queryRaw`SELECT id FROM "User" WHERE id = ${upload.userId}::uuid FOR UPDATE`;
        const current = await tx.photoUpload.findUnique({ where: { id: upload.id } });
        if (!current || current.confirmedAt) return;
        await tx.mediaDeletion.upsert({ where: { storageKey: upload.storageKey }, create: { id: uuid(), storageKey: upload.storageKey, nextAttemptAt: now }, update: {} });
        await tx.photoUpload.delete({ where: { id: upload.id } });
      });
    }
    const pending = await this.db.mediaDeletion.findMany({ where: { nextAttemptAt: { lte: now } }, orderBy: { nextAttemptAt: 'asc' }, take: 25 });
    let failures = 0;
    for (const job of pending) {
      const claimed = await this.db.mediaDeletion.updateMany({ where: { id: job.id, nextAttemptAt: job.nextAttemptAt }, data: { attempts: { increment: 1 }, nextAttemptAt: new Date(now.getTime() + 300000) } });
      if (!claimed.count) continue;
      try {
        await this.storage.remove(job.storageKey);
        await this.db.mediaDeletion.deleteMany({ where: { id: job.id } });
      } catch { failures++; }
    }
    return { failures };
  }
}
