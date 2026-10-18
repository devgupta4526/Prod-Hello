import { describe, expect, it } from "vitest";
import { personalitySchema, planInputSchema, upForSchema } from "../src/modules/social/social.schemas.js";
import { memoryImage } from "../src/modules/social/memory-image.js";
import { weekKey } from "../src/modules/social/weekly-digest.js";

describe("social input and scheduling rules", () => {
  it("requires three distinct thoughtful prompts", () => {
    const prompts = [{ id: "joy", answer: "Walking by the sea" }, { id: "learn", answer: "Pottery" }, { id: "friendship", answer: "Showing up" }];
    expect(personalitySchema.safeParse({ prompts, idealWeekend: "A hike and a good book" }).success).toBe(true);
    expect(personalitySchema.safeParse({ prompts: [prompts[0], prompts[0], prompts[2]], idealWeekend: "A hike" }).success).toBe(false);
    expect(personalitySchema.safeParse({ prompts, idealWeekend: " " }).success).toBe(false);
  });
  it("bounds availability and allows explicit opt-out", () => {
    expect(upForSchema.parse({ activity: null }).activity).toBeNull();
    expect(upForSchema.safeParse({ activity: "Coffee", hours: 25 }).success).toBe(false);
    expect(upForSchema.safeParse({ activity: "Coffee", hours: 0 }).success).toBe(false);
  });
  it("requires a public venue and distinct times", () => {
    const input = { activity: "Coffee", venue: "Central cafe", publicVenue: true, timeOptions: ["2026-11-01T10:00:00Z"] };
    expect(planInputSchema.safeParse(input).success).toBe(true);
    expect(planInputSchema.safeParse({ ...input, publicVenue: false }).success).toBe(false);
    expect(planInputSchema.safeParse({ ...input, timeOptions: [input.timeOptions[0], input.timeOptions[0]] }).success).toBe(false);
  });
  it("deduplicates weekly digests across month and year boundaries", () => {
    expect(weekKey(new Date("2027-01-01T00:00:00Z"))).toBe("2026-12-28");
    expect(weekKey(new Date("2027-01-03T23:59:59Z"))).toBe("2026-12-28");
    expect(weekKey(new Date("2027-01-04T00:00:00Z"))).toBe("2027-01-04");
  });
});

describe("memory upload boundary", () => {
  it("rejects empty, oversized and non-JPEG data", () => {
    expect(() => memoryImage(Buffer.from([255,216,255,217]).toString("base64"))).toThrow("invalid_photo");
    expect(() => memoryImage(Buffer.alloc(70001).toString("base64"))).toThrow("invalid_photo");
    expect(() => memoryImage(Buffer.from("not a photo").toString("base64"))).toThrow("invalid_photo");
  });
  it("removes metadata and rejects oversized frame dimensions", () => {
    const frame = Buffer.from([255,216,255,192,0,11,8,0,1,0,1,1,1,17,0,255,218,0,8,1,1,0,0,63,0,0,255,217]);
    const metadata = Buffer.from([255,225,0,6,71,80,83,0]);
    expect(memoryImage(Buffer.concat([frame.subarray(0,2),metadata,frame.subarray(2)]).toString("base64"))).toEqual(frame);
    frame.writeUInt16BE(65000,9);
    expect(() => memoryImage(frame.toString("base64"))).toThrow("invalid_photo");
  });
});
