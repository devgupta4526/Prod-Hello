CREATE TABLE "MediaDeletion" (
  "id" UUID NOT NULL,
  "storageKey" TEXT NOT NULL,
  "attempts" INTEGER NOT NULL DEFAULT 0,
  "nextAttemptAt" TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "createdAt" TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "MediaDeletion_pkey" PRIMARY KEY ("id"),
  CONSTRAINT "MediaDeletion_attempts_check" CHECK ("attempts" >= 0)
);
CREATE UNIQUE INDEX "MediaDeletion_storageKey_key" ON "MediaDeletion"("storageKey");
CREATE INDEX "MediaDeletion_nextAttemptAt_idx" ON "MediaDeletion"("nextAttemptAt");
