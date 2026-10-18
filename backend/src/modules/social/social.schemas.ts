import { z } from "zod";
export const promptSchema = z.object({ id: z.enum(["joy", "learn", "friendship"]), answer: z.string().trim().min(1).max(240) }).strict();
export const personalitySchema = z.object({
  prompts: z.array(promptSchema).length(3).refine(p => new Set(p.map(x => x.id)).size === 3),
  idealWeekend: z.string().trim().min(1).max(400),
}).strict();
export const upForSchema = z.object({ activity: z.string().trim().min(1).max(60).nullable(), hours: z.number().int().min(1).max(24).default(3) }).strict();
export const planInputSchema = z.object({ durationMinutes: z.number().int().min(15).max(480).default(60), activity: z.string().trim().min(2).max(100), venue: z.string().trim().min(3).max(200), publicVenue: z.literal(true), timeOptions: z.array(z.iso.datetime()).min(1).max(3).refine(x => new Set(x).size === x.length) }).strict();
export const contextSchema = z.object({ type: z.enum(["PROMPT", "PHOTO"]), id: z.string().min(1).max(50), text: z.string().max(300).optional() }).strict();
