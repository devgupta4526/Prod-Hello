CREATE TABLE "VoiceClip" (
  "id" UUID NOT NULL,
  "userId" UUID NOT NULL,
  "conversationId" UUID,
  "audio" BYTEA NOT NULL,
  "durationMs" INTEGER NOT NULL,
  "createdAt" TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "VoiceClip_pkey" PRIMARY KEY ("id"),
  CONSTRAINT "VoiceClip_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT "VoiceClip_duration_check" CHECK ("durationMs" BETWEEN 500 AND 15500),
  CONSTRAINT "VoiceClip_size_check" CHECK (octet_length("audio") BETWEEN 100 AND 100000)
);
CREATE INDEX "VoiceClip_userId_createdAt_idx" ON "VoiceClip"("userId", "createdAt");
