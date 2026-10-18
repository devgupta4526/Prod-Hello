import { createHmac } from "node:crypto";

export function haversineDistanceKm(
  lat1: number,
  lon1: number,
  lat2: number,
  lon2: number,
): number {
  const R = 6371; // Earth's radius in km
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLon = ((lon2 - lon1) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos((lat1 * Math.PI) / 180) *
      Math.cos((lat2 * Math.PI) / 180) *
      Math.sin(dLon / 2) *
      Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c;
}

export function toDistanceBucket(distanceKm: number): string {
  if (distanceKm < 1.0) return "<1km";
  if (distanceKm < 2.0) return "1-2km";
  if (distanceKm < 5.0) return "2-5km";
  if (distanceKm < 10.0) return "5-10km";
  return "10-25km";
}

// ~500m grid cell resolution is approximately 0.0045 degrees
export const CELL_SIZE_DEG = 0.0045;

export function computeCellId(lat: number, lng: number): string {
  const cellLat = Math.floor(lat / CELL_SIZE_DEG);
  const cellLng = Math.floor(lng / CELL_SIZE_DEG);
  return `${cellLat}:${cellLng}`;
}

export function computeFuzzedMarker(
  secret: string,
  userId: string,
  lat: number,
  lng: number,
  date: Date,
): { lat: number; lng: number } {
  const dateStr = date.toISOString().slice(0, 10);
  const hmac = createHmac("sha256", secret)
    .update(`${userId}:${dateStr}`)
    .digest();

  // Map first 4 bytes to [-0.4, 0.4] * CELL_SIZE_DEG
  const latOffsetFactor = ((hmac.readUInt16BE(0) / 65535) - 0.5) * 0.8;
  const lngOffsetFactor = ((hmac.readUInt16BE(2) / 65535) - 0.5) * 0.8;

  const cellLat = (Math.floor(lat / CELL_SIZE_DEG) + 0.5) * CELL_SIZE_DEG;
  const cellLng = (Math.floor(lng / CELL_SIZE_DEG) + 0.5) * CELL_SIZE_DEG;

  const fuzzedLat = Number((cellLat + latOffsetFactor * CELL_SIZE_DEG).toFixed(6));
  const fuzzedLng = Number((cellLng + lngOffsetFactor * CELL_SIZE_DEG).toFixed(6));

  return { lat: fuzzedLat, lng: fuzzedLng };
}
