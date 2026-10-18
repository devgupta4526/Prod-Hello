CREATE TABLE "OtpChallenge" (
  "id" UUID PRIMARY KEY,
  "requestId" UUID NOT NULL UNIQUE,
  "phoneE164" TEXT NOT NULL,
  "deviceId" TEXT NOT NULL,
  "codeHash" TEXT NOT NULL,
  "ipHash" TEXT NOT NULL,
  "sandbox" BOOLEAN NOT NULL,
  "provider" TEXT,
  "providerMessageId" TEXT,
  "deliveryStatus" TEXT NOT NULL DEFAULT 'pending',
  "attempts" INTEGER NOT NULL DEFAULT 0 CHECK ("attempts" BETWEEN 0 AND 5),
  "resendAfterSec" INTEGER NOT NULL CHECK ("resendAfterSec" >= 0),
  "createdAt" TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "expiresAt" TIMESTAMPTZ(3) NOT NULL,
  "consumedAt" TIMESTAMPTZ(3)
);
-- Rate-limit lookups, verification and bounded retention cleanup.
CREATE INDEX "OtpChallenge_phoneE164_createdAt_idx" ON "OtpChallenge"("phoneE164", "createdAt");
CREATE INDEX "OtpChallenge_ipHash_createdAt_idx" ON "OtpChallenge"("ipHash", "createdAt");
CREATE INDEX "OtpChallenge_deviceId_createdAt_idx" ON "OtpChallenge"("deviceId", "createdAt");
CREATE INDEX "OtpChallenge_expiresAt_idx" ON "OtpChallenge"("expiresAt");
CREATE INDEX "OtpChallenge_provider_providerMessageId_idx" ON "OtpChallenge"("provider", "providerMessageId");
CREATE INDEX "OtpChallenge_createdAt_idx" ON "OtpChallenge"("createdAt");
CREATE TABLE "ProfilePhoto" (
  "id" UUID PRIMARY KEY,
  "userId" UUID NOT NULL REFERENCES "User"("id") ON DELETE RESTRICT ON UPDATE CASCADE,
  "storageKey" TEXT NOT NULL UNIQUE,
  "url" TEXT NOT NULL,
  "position" INTEGER NOT NULL CHECK ("position" BETWEEN 0 AND 5),
  "createdAt" TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE("userId", "position")
);
CREATE TABLE "PhotoUpload" (
  "id" UUID PRIMARY KEY,
  "userId" UUID NOT NULL REFERENCES "User"("id") ON DELETE RESTRICT ON UPDATE CASCADE,
  "storageKey" TEXT NOT NULL UNIQUE,
  "createdAt" TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "confirmedAt" TIMESTAMPTZ(3)
);
-- Owner lookup for signed-upload authorization; confirmed assets have unique storage keys.
CREATE INDEX "PhotoUpload_userId_idx" ON "PhotoUpload"("userId");
