export interface Clock { now(): Date }
export const systemClock: Clock = { now: () => new Date() };
export function isAdult(dob: Date, now: Date): boolean {
  const birthday = new Date(Date.UTC(dob.getUTCFullYear() + 18, dob.getUTCMonth(), dob.getUTCDate()));
  return dob <= now && birthday <= now;
}
