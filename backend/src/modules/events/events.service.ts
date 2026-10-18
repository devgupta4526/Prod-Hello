import { v7 as uuid } from "uuid";
import { AppError } from "../../lib/app-error.js";
import type { Clock } from "../../lib/clock.js";
import type { EventsRepository } from "./events.repo.js";
import type {
  organizerApplicationSchema,
  createEventSchema,
  updateEventSchema,
  createTierSchema,
  updateTierSchema,
  checkinRequestSchema,
} from "./events.schemas.js";
import type { z } from "zod";

export class EventsService {
  constructor(
    private readonly repo: EventsRepository,
    private readonly clock: Clock,
  ) {}

  async applyForOrganizer(
    userId: string,
    input: z.infer<typeof organizerApplicationSchema>,
  ) {
    const existing = await this.repo.getOrganizerApplication(userId);
    if (existing && existing.status === "PENDING") {
      throw new AppError("application_already_pending", 409);
    }
    const now = this.clock.now();
    return this.repo.createOrganizerApplication({
      userId,
      orgName: input.orgName,
      description: input.description,
      links: input.links,
      now,
    });
  }

  async createEvent(
    organizerId: string,
    input: z.infer<typeof createEventSchema>,
  ) {
    const now = this.clock.now();
    const startsAt = new Date(input.startsAt);
    const endsAt = new Date(input.endsAt);
    if (startsAt <= now) {
      throw new AppError("starts_at_must_be_future", 400);
    }
    if (endsAt <= startsAt) {
      throw new AppError("ends_at_must_be_after_starts_at", 400);
    }

    const event = await this.repo.createEvent({
      id: uuid(),
      organizerId,
      title: input.title,
      description: input.description,
      category: input.category,
      venueName: input.venueName,
      address: input.address,
      lat: input.lat,
      lng: input.lng,
      coverKey: input.coverKey,
      startsAt,
      endsAt,
      salesStartAt: input.salesStartAt ? new Date(input.salesStartAt) : undefined,
      salesEndAt: input.salesEndAt ? new Date(input.salesEndAt) : undefined,
      now,
    });

    return this.getEventDetail(event.id);
  }

  async updateEvent(
    organizerId: string,
    eventId: string,
    input: z.infer<typeof updateEventSchema>,
  ) {
    const data: any = { ...input };
    if (input.startsAt) data.startsAt = new Date(input.startsAt);
    if (input.endsAt) data.endsAt = new Date(input.endsAt);
    if (input.salesStartAt) data.salesStartAt = new Date(input.salesStartAt);
    if (input.salesEndAt) data.salesEndAt = new Date(input.salesEndAt);

    await this.repo.updateEvent(eventId, organizerId, data);
    return this.getEventDetail(eventId);
  }

  async publishEvent(organizerId: string, eventId: string) {
    const now = this.clock.now();
    await this.repo.publishEvent(eventId, organizerId, now);
    return this.getEventDetail(eventId);
  }

  async cancelEvent(organizerId: string, eventId: string) {
    await this.repo.cancelEvent(eventId, organizerId);
    return { ok: true as const };
  }

  async createTier(
    organizerId: string,
    eventId: string,
    input: z.infer<typeof createTierSchema>,
  ) {
    return this.repo.createTier(eventId, organizerId, input);
  }

  async updateTier(
    organizerId: string,
    eventId: string,
    tierId: string,
    input: z.infer<typeof updateTierSchema>,
  ) {
    return this.repo.updateTier(tierId, eventId, organizerId, input);
  }

  async deleteTier(organizerId: string, eventId: string, tierId: string) {
    await this.repo.deleteTier(tierId, eventId, organizerId);
    return { ok: true as const };
  }

  async listPublicEvents(category?: string) {
    const now = this.clock.now();
    const events = await this.repo.listPublicEvents(now, category);

    return events.map((e) => {
      const minPricePaise =
        e.tiers.length > 0
          ? Math.min(...e.tiers.map((t) => t.pricePaise))
          : null;

      return {
        id: e.id,
        title: e.title,
        description: e.description,
        category: e.category,
        venueName: e.venueName,
        address: e.address,
        lat: e.lat,
        lng: e.lng,
        coverUrl: e.coverKey ? `https://res.cloudinary.com/dummy/${e.coverKey}` : null,
        startsAt: e.startsAt.toISOString(),
        endsAt: e.endsAt.toISOString(),
        status: e.status,
        minPricePaise,
        organizer: {
          id: e.organizer.id,
          displayName: e.organizer.profile?.displayName ?? "Organizer",
          photoUrl: e.organizer.photos[0]?.url ?? null,
        },
      };
    });
  }

  async getEventDetail(eventId: string) {
    const e = await this.repo.getEvent(eventId);
    if (!e) throw new AppError("not_found", 404);

    const minPricePaise =
      e.tiers.length > 0 ? Math.min(...e.tiers.map((t) => t.pricePaise)) : null;

    return {
      id: e.id,
      title: e.title,
      description: e.description,
      category: e.category,
      venueName: e.venueName,
      address: e.address,
      lat: e.lat,
      lng: e.lng,
      coverUrl: e.coverKey ? `https://res.cloudinary.com/dummy/${e.coverKey}` : null,
      startsAt: e.startsAt.toISOString(),
      endsAt: e.endsAt.toISOString(),
      status: e.status,
      minPricePaise,
      organizer: {
        id: e.organizer.id,
        displayName: e.organizer.profile?.displayName ?? "Organizer",
        photoUrl: e.organizer.photos[0]?.url ?? null,
      },
      tiers: e.tiers.map((t) => ({
        id: t.id,
        eventId: t.eventId,
        name: t.name,
        pricePaise: t.pricePaise,
        capacity: t.capacity,
        available: Math.max(0, t.capacity - t.reserved - t.sold),
        maxPerOrder: t.maxPerOrder,
        sortOrder: t.sortOrder,
      })),
    };
  }

  async listOrganizerEvents(organizerId: string) {
    const events = await this.repo.listOrganizerEvents(organizerId);
    return events.map((e) => ({
      id: e.id,
      title: e.title,
      status: e.status,
      startsAt: e.startsAt.toISOString(),
      endsAt: e.endsAt.toISOString(),
      totalCapacity: e.tiers.reduce((acc, t) => acc + t.capacity, 0),
      totalSold: e.tiers.reduce((acc, t) => acc + t.sold, 0),
    }));
  }

  async listAttendees(organizerId: string, eventId: string, search?: string) {
    const attendees = await this.repo.listAttendees(organizerId, eventId, search);
    return attendees.map((a) => ({
      id: a.id,
      bookingRef: a.bookingRef,
      tierName: a.tier.name,
      checkedInAt: a.checkedInAt?.toISOString() ?? null,
      holder: {
        displayName: a.owner.profile?.displayName ?? "Attendee",
      },
    }));
  }

  async getSummary(organizerId: string, eventId: string) {
    return this.repo.getEventSummary(eventId, organizerId);
  }

  async getLedger(organizerId: string) {
    const entries = await this.repo.getOrganizerLedger(organizerId);
    return entries.map((e) => ({
      id: e.id,
      eventId: e.eventId,
      eventTitle: e.event.title,
      type: e.type,
      amountPaise: e.amountPaise,
      memo: e.memo,
      createdAt: e.createdAt.toISOString(),
    }));
  }

  async checkin(
    organizerId: string,
    eventId: string,
    input: z.infer<typeof checkinRequestSchema>,
  ) {
    const now = this.clock.now();
    return this.repo.checkin({
      eventId,
      organizerId,
      token: input.token,
      clientScanId: input.clientScanId,
      now,
    });
  }
}
