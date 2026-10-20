CREATE TABLE "IcebreakerAnswer" (
  "day" TEXT NOT NULL,
  "userId" UUID NOT NULL,
  "answer" TEXT NOT NULL,
  "createdAt" TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "IcebreakerAnswer_pkey" PRIMARY KEY ("day", "userId"),
  CONSTRAINT "IcebreakerAnswer_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE
);
