-- AlterTable
ALTER TABLE "Profile" ADD COLUMN     "idealWeekend" TEXT NOT NULL DEFAULT '',
ADD COLUMN     "prompts" JSONB NOT NULL DEFAULT '[]',
ADD COLUMN     "upFor" TEXT,
ADD COLUMN     "upForUntil" TIMESTAMPTZ(3);

-- AlterTable
ALTER TABLE "Hello" ADD COLUMN     "context" JSONB;

-- CreateTable
CREATE TABLE "SocialPlan" (
    "durationMinutes" INTEGER NOT NULL DEFAULT 60,
    "participantIds" TEXT[],
    "id" UUID NOT NULL,
    "conversationId" UUID NOT NULL,
    "creatorId" UUID NOT NULL,
    "activity" TEXT NOT NULL,
    "venue" TEXT NOT NULL,
    "timeOptions" TIMESTAMPTZ(3)[],
    "confirmedTime" TIMESTAMPTZ(3),
    "status" TEXT NOT NULL DEFAULT 'PROPOSED',
    "createdAt" TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "SocialPlan_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "PlanVote" (
    "planId" UUID NOT NULL,
    "userId" UUID NOT NULL,
    "option" INTEGER NOT NULL,

    CONSTRAINT "PlanVote_pkey" PRIMARY KEY ("planId","userId")
);

-- CreateTable
CREATE TABLE "InterestCircle" (
    "id" UUID NOT NULL,
    "hostId" UUID NOT NULL,
    "name" TEXT NOT NULL,
    "interest" TEXT NOT NULL,
    "rules" TEXT NOT NULL,
    "capacity" INTEGER NOT NULL DEFAULT 30,
    "createdAt" TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "InterestCircle_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "CircleMember" (
    "circleId" UUID NOT NULL,
    "userId" UUID NOT NULL,
    "removed" BOOLEAN NOT NULL DEFAULT false,

    CONSTRAINT "CircleMember_pkey" PRIMARY KEY ("circleId","userId")
);

-- CreateTable
CREATE TABLE "SavedExperience" (
    "userId" UUID NOT NULL,
    "targetId" UUID NOT NULL,
    "kind" TEXT NOT NULL,
    "createdAt" TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "SavedExperience_pkey" PRIMARY KEY ("userId","targetId","kind")
);

-- CreateTable
CREATE TABLE "ReconnectChoice" (
    "planId" UUID NOT NULL,
    "userId" UUID NOT NULL,
    "targetId" UUID NOT NULL,
    "activity" TEXT NOT NULL,

    CONSTRAINT "ReconnectChoice_pkey" PRIMARY KEY ("planId","userId","targetId")
);

-- CreateTable
CREATE TABLE "PlanMemory" (
    "consentAt" TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "id" UUID NOT NULL,
    "planId" UUID NOT NULL,
    "ownerId" UUID NOT NULL,
    "caption" TEXT NOT NULL,
    "image" BYTEA NOT NULL,
    "audience" TEXT NOT NULL DEFAULT 'PRIVATE',
    "createdAt" TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "PlanMemory_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "SocialPlan_conversationId_createdAt_idx" ON "SocialPlan"("conversationId", "createdAt");

-- CreateIndex
CREATE INDEX "PlanMemory_planId_idx" ON "PlanMemory"("planId");

-- AddForeignKey
ALTER TABLE "PlanVote" ADD CONSTRAINT "PlanVote_planId_fkey" FOREIGN KEY ("planId") REFERENCES "SocialPlan"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "CircleMember" ADD CONSTRAINT "CircleMember_circleId_fkey" FOREIGN KEY ("circleId") REFERENCES "InterestCircle"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ReconnectChoice" ADD CONSTRAINT "ReconnectChoice_planId_fkey" FOREIGN KEY ("planId") REFERENCES "SocialPlan"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "PlanMemory" ADD CONSTRAINT "PlanMemory_planId_fkey" FOREIGN KEY ("planId") REFERENCES "SocialPlan"("id") ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "SocialPlan" ADD CONSTRAINT "SocialPlan_conversationId_fkey" FOREIGN KEY ("conversationId") REFERENCES "Conversation"("id") ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "SocialPlan" ADD CONSTRAINT "SocialPlan_creatorId_fkey" FOREIGN KEY ("creatorId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "PlanVote" ADD CONSTRAINT "PlanVote_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "InterestCircle" ADD CONSTRAINT "InterestCircle_hostId_fkey" FOREIGN KEY ("hostId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "CircleMember" ADD CONSTRAINT "CircleMember_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "SavedExperience" ADD CONSTRAINT "SavedExperience_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "ReconnectChoice" ADD CONSTRAINT "ReconnectChoice_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "ReconnectChoice" ADD CONSTRAINT "ReconnectChoice_targetId_fkey" FOREIGN KEY ("targetId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "PlanMemory" ADD CONSTRAINT "PlanMemory_ownerId_fkey" FOREIGN KEY ("ownerId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "SocialPlan" ADD CONSTRAINT "SocialPlan_status_check" CHECK ("status" IN ('PROPOSED', 'CONFIRMED', 'CANCELLED'));
ALTER TABLE "SocialPlan" ADD CONSTRAINT "SocialPlan_times_check" CHECK (cardinality("timeOptions") BETWEEN 1 AND 3);
ALTER TABLE "PlanVote" ADD CONSTRAINT "PlanVote_option_check" CHECK ("option" BETWEEN 0 AND 2);
ALTER TABLE "PlanMemory" ADD CONSTRAINT "PlanMemory_audience_check" CHECK ("audience" IN ('PRIVATE', 'GROUP'));
ALTER TABLE "PlanMemory" ADD CONSTRAINT "PlanMemory_size_check" CHECK (octet_length("image") <= 70000);
CREATE TABLE "CirclePost" (
  "id" UUID PRIMARY KEY,
  "circleId" UUID NOT NULL REFERENCES "InterestCircle"("id") ON DELETE CASCADE ON UPDATE CASCADE,
  "authorId" UUID NOT NULL REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE,
  "body" TEXT NOT NULL,
  "createdAt" TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX "CirclePost_circleId_createdAt_idx" ON "CirclePost"("circleId", "createdAt");
