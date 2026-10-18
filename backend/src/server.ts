import { readEnvironment } from "./config/env.js";
import { createDatabase } from "./lib/database.js";
import { buildApp } from "./app.js";
import { SystemRepository } from "./modules/system/system.repo.js";
import { AuthRepository } from "./modules/auth/auth.repo.js";
import { CloudinaryStorage } from "./providers/storage/cloudinary.js";
import { MediaCleanup } from "./providers/storage/media-cleanup.js";

import { deliverWeeklyDigests } from "./modules/social/weekly-digest.js";

const env = readEnvironment();
const database = createDatabase(env.DATABASE_URL);
const app = await buildApp(env, database);
try {
  await database.$connect();
  await app.listen({
    port: env.PORT,
    host: env.APP_ENV === "local" ? "127.0.0.1" : "0.0.0.0",
  });
} catch {
  app.log.fatal(
    "Startup failed; verify database availability and configuration.",
  );
  await app.close();
  await database.$disconnect();
  process.exitCode = 1;
}
const repository = new SystemRepository(database);
const authRepository = new AuthRepository(database);
const mediaCleanup = new MediaCleanup(database, new CloudinaryStorage(env));
let mediaCleanupRunning = false;
const mediaTimer = setInterval(() => {
  if (mediaCleanupRunning) return;
  mediaCleanupRunning = true;
  void mediaCleanup.run(new Date()).then(({ failures }) => {
    if (failures) app.log.warn({ failures }, 'Media deletion will retry');
  }).catch(() => { app.log.error('Media cleanup failed'); }).finally(() => { mediaCleanupRunning = false; });
}, 60000);
mediaTimer.unref();
let digestRunning = false;
const digestTimer = setInterval(() => {
  if (digestRunning || closing) return;
  digestRunning = true;
  void deliverWeeklyDigests(database, new Date())
    .catch(() => app.log.error("Weekly digest delivery failed"))
    .finally(() => { digestRunning = false; });
}, 3600000);
digestTimer.unref();
const cleanup = setInterval(() => {
  void repository.cleanup().catch(() => {
    app.log.error("Rate-limit cleanup failed");
  });
}, 60000);
const otpCleanup = setInterval(() => { void authRepository.cleanup(new Date()).catch(() => { app.log.error('OTP retention cleanup failed'); }); }, 86400000);
otpCleanup.unref();
cleanup.unref();
let closing = false;
async function shutdown() {
  if (closing) return;
  closing = true;
  clearInterval(cleanup);
  clearInterval(otpCleanup);
  clearInterval(mediaTimer);
  clearInterval(digestTimer);
  await app.close();
  await database.$disconnect();
}
process.once("SIGTERM", () => {
  void shutdown();
});
process.once("SIGINT", () => {
  void shutdown();
});
