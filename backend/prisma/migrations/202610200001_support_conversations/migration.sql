CREATE TABLE "SupportCase" (
  "id" UUID NOT NULL,
  "userId" UUID NOT NULL,
  "ticketId" UUID,
  "issue" TEXT NOT NULL,
  "status" TEXT NOT NULL DEFAULT 'OPEN',
  "createdAt" TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" TIMESTAMPTZ(3) NOT NULL,
  CONSTRAINT "SupportCase_pkey" PRIMARY KEY ("id"),
  CONSTRAINT "SupportCase_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT "SupportCase_status_check" CHECK ("status" IN ('OPEN', 'CLOSED'))
);
CREATE INDEX "SupportCase_userId_createdAt_idx" ON "SupportCase"("userId", "createdAt");
CREATE TABLE "SupportMessage" (
  "id" UUID NOT NULL,
  "caseId" UUID NOT NULL,
  "authorId" UUID NOT NULL,
  "fromSupport" BOOLEAN NOT NULL DEFAULT false,
  "body" TEXT NOT NULL,
  "attachment" BYTEA,
  "createdAt" TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "SupportMessage_pkey" PRIMARY KEY ("id"),
  CONSTRAINT "SupportMessage_caseId_fkey" FOREIGN KEY ("caseId") REFERENCES "SupportCase"("id") ON DELETE CASCADE ON UPDATE CASCADE
);
CREATE INDEX "SupportMessage_caseId_createdAt_idx" ON "SupportMessage"("caseId", "createdAt");
