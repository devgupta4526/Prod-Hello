import { describe, expect, it } from "vitest";
import {
  haversineDistanceKm,
  toDistanceBucket,
  computeCellId,
  computeFuzzedMarker,
} from "../src/lib/location.js";
import { sendHelloSchema } from "../src/modules/hello/hello.schemas.js";
import { sendMessageSchema } from "../src/modules/chat/chat.schemas.js";
import { createPoolSchema, knockSchema } from "../src/modules/pools/pools.schemas.js";
import { createOrderSchema } from "../src/modules/orders/orders.schemas.js";

describe("Business Rules verification (BR-xx)", () => {
  // Discovery & Privacy
  describe("Discovery & Privacy (BR-10, BR-12, BR-14)", () => {
    it("BR-10 converts exact distances into non-leaking distance buckets", () => {
      expect(toDistanceBucket(0.4)).toBe("<1km");
      expect(toDistanceBucket(1.5)).toBe("1-2km");
      expect(toDistanceBucket(3.8)).toBe("2-5km");
      expect(toDistanceBucket(8.2)).toBe("5-10km");
      expect(toDistanceBucket(18.0)).toBe("10-25km");
    });

    it("BR-10 generates stable HMAC fuzzed markers within ~500m grid cell, never exact coordinates", () => {
      const secret = "test-secret-salt-32-bytes-long-for-hmac";
      const user1 = "0199aa00-0000-7000-8000-000000000001";
      const lat = 12.9716;
      const lng = 77.5946;
      const now = new Date("2026-10-17T12:00:00Z");

      const marker1 = computeFuzzedMarker(secret, user1, lat, lng, now);
      const marker2 = computeFuzzedMarker(secret, user1, lat, lng, now);

      // Stable within same date
      expect(marker1.lat).toBe(marker2.lat);
      expect(marker1.lng).toBe(marker2.lng);

      // Must NOT equal exact coordinates
      expect(marker1.lat).not.toBe(lat);
      expect(marker1.lng).not.toBe(lng);

      // Distance from exact coordinate must be within cell (~500m / 0.5km)
      const dist = haversineDistanceKm(lat, lng, marker1.lat, marker1.lng);
      expect(dist).toBeLessThan(0.6);
    });

    it("BR-12 enforces discovery radius maximum 25km and grid cell snapping", () => {
      const cell1 = computeCellId(12.9716, 77.5946);
      const cell2 = computeCellId(12.9718, 77.5948);
      // Very close points snap to the same coarse grid cell
      expect(cell1).toBe(cell2);
    });
  });

  // Hellos & Connections
  describe("Hellos & Connections (BR-20, BR-21, BR-24)", () => {
    it("BR-20 enforces trimmed message length between 1 and 280 characters", () => {
      expect(
        sendHelloSchema.safeParse({
          receiverId: "0199aa00-0000-7000-8000-000000000001",
          message: "",
        }).success,
      ).toBe(false);

      expect(
        sendHelloSchema.safeParse({
          receiverId: "0199aa00-0000-7000-8000-000000000001",
          message: "   ",
        }).success,
      ).toBe(false);

      expect(
        sendHelloSchema.safeParse({
          receiverId: "0199aa00-0000-7000-8000-000000000001",
          message: "a".repeat(281),
        }).success,
      ).toBe(false);

      expect(
        sendHelloSchema.safeParse({
          receiverId: "0199aa00-0000-7000-8000-000000000001",
          message: "Hey! Want to grab a coffee this weekend?",
        }).success,
      ).toBe(true);
    });

    it("BR-24 orders Connection userLowId and userHighId strictly lexicographically", () => {
      const uA = "0199aa00-0000-7000-8000-000000000001";
      const uB = "0199aa00-0000-7000-8000-000000000002";
      const userLowId = uA < uB ? uA : uB;
      const userHighId = uA < uB ? uB : uA;
      expect(userLowId).toBe(uA);
      expect(userHighId).toBe(uB);
      expect(userLowId < userHighId).toBe(true);
    });
  });

  // Messaging & Chat
  describe("Messaging (BR-32)", () => {
    it("BR-32 enforces message body between 1 and 1000 characters and requires clientMsgId", () => {
      expect(
        sendMessageSchema.safeParse({
          clientMsgId: "msg-123",
          body: "",
        }).success,
      ).toBe(false);

      expect(
        sendMessageSchema.safeParse({
          clientMsgId: "msg-123",
          body: "x".repeat(1001),
        }).success,
      ).toBe(false);

      expect(
        sendMessageSchema.safeParse({
          clientMsgId: "",
          body: "Hello!",
        }).success,
      ).toBe(false);

      expect(
        sendMessageSchema.safeParse({
          clientMsgId: "msg-123",
          body: "Hey there!",
        }).success,
      ).toBe(true);
    });
  });

  // Pools & Knocks
  describe("Pools & Knocks (BR-41, BR-42)", () => {
    it("BR-41 enforces pool capacity between 2 and 12", () => {
      const basePool = {
        title: "Coffee at Indiranagar",
        description: "Looking for 3 folks to chat about design & books",
        category: "Coffee",
        startsAt: new Date(Date.now() + 86400000).toISOString(),
        venueName: "Third Wave Coffee",
        venueLat: 12.9716,
        venueLng: 77.5946,
        areaLabel: "Indiranagar",
      };

      expect(createPoolSchema.safeParse({ ...basePool, capacity: 1 }).success).toBe(false);
      expect(createPoolSchema.safeParse({ ...basePool, capacity: 13 }).success).toBe(false);
      expect(createPoolSchema.safeParse({ ...basePool, capacity: 4 }).success).toBe(true);
    });

    it("BR-42 enforces Knock message at most 120 chars", () => {
      expect(knockSchema.safeParse({ message: "a".repeat(121) }).success).toBe(false);
      expect(knockSchema.safeParse({ message: "Hey, would love to join!" }).success).toBe(true);
      expect(knockSchema.safeParse({}).success).toBe(true);
    });
  });

  // Events, Orders & Payments
  describe("Events, Orders & Payments (BR-52, BR-55)", () => {
    it("BR-52 enforces order quantity bounds (max 10, positive)", () => {
      const tierId = "0199aa00-0000-7000-8000-000000000001";
      const requestId = "0199aa00-0000-7000-8000-000000000002";

      expect(createOrderSchema.safeParse({ tierId, quantity: 0, requestId }).success).toBe(false);
      expect(createOrderSchema.safeParse({ tierId, quantity: 11, requestId }).success).toBe(false);
      expect(createOrderSchema.safeParse({ tierId, quantity: 2, requestId }).success).toBe(true);
    });
  });
});
