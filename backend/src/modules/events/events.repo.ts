import { v7 as uuid } from "uuid";
import type { Database } from "../../lib/database.js";
import { AppError } from "../../lib/app-error.js";

export class EventsRepository {
  constructor(private readonly db: Database) {}

  async createOrganizerApplication(input: {
    userId: string;
    orgName: string;
    description: string;
    links?: string[] | undefined;
    now: Date;
  }) {
    return this.db.organizerApplication.create({
      data: {
        id: uuid(),
        userId: input.userId,
        orgName: input.orgName,
        description: input.description,
        links: input.links as any,
        status: "PENDING",
        createdAt: input.now,
      },
    });
  }

  async getOrganizerApplication(userId: string) {
    return this.db.organizerApplication.findFirst({
      where: { userId },
      orderBy: { createdAt: "desc" },
    });
  }

  async createEvent(input: {
    id: string;
    organizerId: string;
    title: string;
    description: string;
    category: string;
    venueName: string;
    address: string;
    lat: number;
    lng: number;
    coverKey?: string | undefined;
    startsAt: Date;
    endsAt: Date;
    salesStartAt?: Date | undefined;
    salesEndAt?: Date | undefined;
    now: Date;
  }) {
    return this.db.event.create({
      data: {
        id: input.id,
        organizerId: input.organizerId,
        title: input.title,
        description: input.description,
        category: input.category,
        venueName: input.venueName,
        address: input.address,
        lat: input.lat,
        lng: input.lng,
        coverKey: input.coverKey ?? null,
        startsAt: input.startsAt,
        endsAt: input.endsAt,
        salesStartAt: input.salesStartAt ?? null,
        salesEndAt: input.salesEndAt ?? null,
        status: "DRAFT",
        createdAt: input.now,
      },
    });
  }

  async getEvent(eventId: string) {
    return this.db.event.findUnique({
      where: { id: eventId },
      include: {
        organizer: {
          include: { profile: true, photos: { orderBy: { position: "asc" } } },
        },
        tiers: { orderBy: { sortOrder: "asc" } },
      },
    });
  }

  async updateEvent(
    eventId: string,
    organizerId: string,
    data: Partial<{
      title: string;
      description: string;
      category: string;
      venueName: string;
      address: string;
      lat: number;
      lng: number;
      coverKey: string;
      startsAt: Date;
      endsAt: Date;
      salesStartAt: Date;
      salesEndAt: Date;
    }>,
  ) {
    const event = await this.db.event.findUnique({ where: { id: eventId } });
    if (!event) throw new AppError("not_found", 404);
    if (event.organizerId !== organizerId) throw new AppError("forbidden", 403);
    if (event.status === "CANCELLED" || event.status === "ENDED") {
      throw new AppError("cannot_edit_closed_event", 409);
    }

    return this.db.event.update({
      where: { id: eventId },
      data,
    });
  }

  async publishEvent(eventId: string, organizerId: string, now: Date) {
    const event = await this.db.event.findUnique({
      where: { id: eventId },
      include: { tiers: true },
    });
    if (!event) throw new AppError("not_found", 404);
    if (event.organizerId !== organizerId) throw new AppError("forbidden", 403);
    if (event.tiers.length === 0) throw new AppError("tiers_required", 400);

    return this.db.event.update({
      where: { id: eventId },
      data: { status: "PUBLISHED", publishedAt: now },
    });
  }

  async cancelEvent(eventId: string, organizerId: string) {
    const event = await this.db.event.findUnique({ where: { id: eventId } });
    if (!event) throw new AppError("not_found", 404);
    if (event.organizerId !== organizerId) throw new AppError("forbidden", 403);

    return this.db.event.update({
      where: { id: eventId },
      data: { status: "CANCELLED" },
    });
  }

  async createTier(eventId: string, organizerId: string, data: {
    name: string;
    pricePaise: number;
    capacity: number;
    maxPerOrder: number;
    sortOrder: number;
  }) {
    const event = await this.db.event.findUnique({ where: { id: eventId } });
    if (!event) throw new AppError("not_found", 404);
    if (event.organizerId !== organizerId) throw new AppError("forbidden", 403);

    return this.db.ticketTier.create({
      data: {
        id: uuid(),
        eventId,
        ...data,
      },
    });
  }

  async updateTier(tierId: string, eventId: string, organizerId: string, data: {
    name?: string | undefined;
    pricePaise?: number | undefined;
    capacity?: number | undefined;
    maxPerOrder?: number | undefined;
    sortOrder?: number | undefined;
  }) {
    const event = await this.db.event.findUnique({ where: { id: eventId } });
    if (!event) throw new AppError("not_found", 404);
    if (event.organizerId !== organizerId) throw new AppError("forbidden", 403);

    const updateData: Record<string, unknown> = {};
    if (data.name !== undefined) updateData.name = data.name;
    if (data.pricePaise !== undefined) updateData.pricePaise = data.pricePaise;
    if (data.capacity !== undefined) updateData.capacity = data.capacity;
    if (data.maxPerOrder !== undefined) updateData.maxPerOrder = data.maxPerOrder;
    if (data.sortOrder !== undefined) updateData.sortOrder = data.sortOrder;

    return this.db.ticketTier.update({
      where: { id: tierId },
      data: updateData,
    });
  }

  async deleteTier(tierId: string, eventId: string, organizerId: string) {
    const event = await this.db.event.findUnique({ where: { id: eventId } });
    if (!event) throw new AppError("not_found", 404);
    if (event.organizerId !== organizerId) throw new AppError("forbidden", 403);

    const tier = await this.db.ticketTier.findUnique({ where: { id: tierId } });
    if (!tier) return;
    if (tier.sold > 0 || tier.reserved > 0) {
      throw new AppError("tier_has_orders", 409);
    }

    await this.db.ticketTier.delete({ where: { id: tierId } });
  }

  async listPublicEvents(now: Date, category?: string) {
    // BR-51: Only published, non-ended, non-cancelled
    return this.db.event.findMany({
      where: {
        status: "PUBLISHED",
        endsAt: { gt: now },
        ...(category ? { category } : {}),
      },
      include: {
        organizer: {
          include: { profile: true, photos: { orderBy: { position: "asc" } } },
        },
        tiers: { orderBy: { pricePaise: "asc" } },
      },
      orderBy: { startsAt: "asc" },
      take: 50,
    });
  }

  async listOrganizerEvents(organizerId: string) {
    return this.db.event.findMany({
      where: { organizerId },
      include: {
        tiers: { orderBy: { sortOrder: "asc" } },
        orders: { where: { status: "PAID" } },
      },
      orderBy: { startsAt: "desc" },
    });
  }

  async listAttendees(eventId: string, organizerId: string, search?: string) {
    const event = await this.db.event.findUnique({ where: { id: eventId } });
    if (!event) throw new AppError("not_found", 404);
    if (event.organizerId !== organizerId) throw new AppError("forbidden", 403);

    return this.db.ticket.findMany({
      where: {
        eventId,
        ...(search
          ? {
              OR: [
                { bookingRef: { contains: search, mode: "insensitive" } },
                {
                  owner: {
                    profile: {
                      displayName: { contains: search, mode: "insensitive" },
                    },
                  },
                },
              ],
            }
          : {}),
      },
      include: {
        owner: {
          include: { profile: true, photos: { orderBy: { position: "asc" } } },
        },
        tier: true,
      },
      orderBy: { createdAt: "desc" },
    });
  }

  async getEventSummary(eventId: string, organizerId: string) {
    const event = await this.db.event.findUnique({
      where: { id: eventId },
      include: {
        tiers: true,
        tickets: true,
        orders: { where: { status: "PAID" } },
      },
    });
    if (!event) throw new AppError("not_found", 404);
    if (event.organizerId !== organizerId) throw new AppError("forbidden", 403);

    const totalSold = event.tiers.reduce((acc, t) => acc + t.sold, 0);
    const checkedInCount = event.tickets.filter((t) => t.checkedInAt !== null).length;
    const revenuePaise = event.orders.reduce((acc, o) => acc + o.amountPaise, 0);

    return {
      eventId: event.id,
      title: event.title,
      status: event.status,
      totalCapacity: event.tiers.reduce((acc, t) => acc + t.capacity, 0),
      totalSold,
      checkedInCount,
      revenuePaise,
    };
  }

  async getOrganizerLedger(organizerId: string) {
    return this.db.ledgerEntry.findMany({
      where: { organizerId },
      orderBy: { createdAt: "desc" },
      include: { event: true },
    });
  }

  async checkin(input: {
    eventId: string;
    organizerId: string;
    token: string;
    clientScanId?: string | undefined;
    now: Date;
  }) {
    // BR-56: Check-in atomic statement
    const rawToken = input.token.startsWith("HLO1.")
      ? input.token.slice(5)
      : input.token;

    // Search by qrToken or bookingRef
    const ticket = await this.db.ticket.findFirst({
      where: {
        OR: [{ qrToken: rawToken }, { bookingRef: rawToken }],
      },
      include: {
        owner: { include: { profile: true } },
        tier: true,
        event: true,
      },
    });

    if (!ticket) {
      return { result: "INVALID" as const };
    }

    if (ticket.eventId !== input.eventId) {
      return { result: "WRONG_EVENT" as const };
    }

    if (ticket.status === "REFUNDED") {
      return { result: "REFUNDED" as const };
    }

    if (ticket.status !== "CONFIRMED") {
      return { result: "NOT_CONFIRMED" as const };
    }

    if (ticket.checkedInAt !== null) {
      return {
        result: "ALREADY_USED" as const,
        holder: { displayName: ticket.owner.profile?.displayName ?? "Attendee" },
        tier: ticket.tier.name,
        checkedInAt: ticket.checkedInAt.toISOString(),
      };
    }

    // Atomic update
    const updated = await this.db.ticket.updateMany({
      where: {
        id: ticket.id,
        eventId: input.eventId,
        status: "CONFIRMED",
        checkedInAt: null,
      },
      data: {
        checkedInAt: input.now,
        checkedInBy: input.organizerId,
      },
    });

    if (updated.count === 1) {
      return {
        result: "VALID" as const,
        holder: { displayName: ticket.owner.profile?.displayName ?? "Attendee" },
        tier: ticket.tier.name,
        checkedInAt: input.now.toISOString(),
      };
    }

    return {
      result: "ALREADY_USED" as const,
      holder: { displayName: ticket.owner.profile?.displayName ?? "Attendee" },
      tier: ticket.tier.name,
    };
  }
}
