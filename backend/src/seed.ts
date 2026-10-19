import { v5 as uuid } from "uuid";
import { createDatabase } from "./lib/database.js";
import { computeCellId } from "./lib/location.js";

async function main() {
  if (!["local", "staging"].includes(process.env.APP_ENV ?? ""))
    throw new Error("Set APP_ENV=local or staging to seed demo data");
  if (!process.env.DATABASE_URL) throw new Error("DATABASE_URL is required");
  const client = createDatabase(process.env.DATABASE_URL);
  const seedId = (name: string) => uuid(`hello-demo-v1:${name}`, uuid.URL);
  const photo = (index: number) => ({
    id: seedId(`photo:${String(index)}`),
    storageKey: `hello/demo/avatar-${String(index)}`,
    url: process.env.SEED_AVATAR_BASE
      ? `${process.env.SEED_AVATAR_BASE}/avatar-${String(index)}.jpg`
      : "https://res.cloudinary.com/demo/image/upload/sample.jpg",
    position: 0,
  });

  await client.$connect();
  try {
    await client.$transaction(
      async (db) => {
        console.log("Seeding test data...");

        // 1. Organizer User: Nagpur Culture Collective
        const organizerId = seedId("organizer");
        const organizer = await db.user.upsert({
          where: { id: organizerId },
          create: {
            id: organizerId,
            phoneE164: "+999000000001",
            phoneVerifiedAt: new Date(),
            status: "ACTIVE",
            role: "USER",
            isOrganizer: true,
            dateOfBirth: new Date("1995-05-15"),
            adultVerifiedAt: new Date(),
            guidelinesAcceptedAt: new Date(),
            profile: {
              create: {
                displayName: "Demo Nagpur Culture Club",
                bio: "Fictional test organizer for music, mixers, and heritage walks.",
                interests: ["Music", "Food", "Travel", "Art"],
                showAge: false,
                discoveryRadiusKm: 25,
              },
            },
            photos: { create: photo(0) },
          },
          update: {},
        });

        // 2. Regular active users for Discovery
        const testUsers = [
          {
            phone: "+999000000002",
            name: "Aarav Sharma",
            bio: "Coffee enthusiast, amateur guitarist, always up for badminton.",
            interests: ["Coffee", "Music", "Sports", "Technology"],
            lat: 21.1458, // Dharampeth
            lng: 79.0882,
            availability: "OPEN" as const,
          },
          {
            phone: "+999000000003",
            name: "Priya Nair",
            bio: "Product designer & book lover. Exploring Nagpur's street food and cafes.",
            interests: ["Art", "Books", "Food", "Travel"],
            lat: 21.1532, // Civil Lines
            lng: 79.0731,
            availability: "OPEN" as const,
          },
          {
            phone: "+999000000004",
            name: "Rohan Verma",
            bio: "Runner and cinema nerd. Morning runs near Ambazari Lake.",
            interests: ["Outdoors", "Music", "Photography", "Fitness"],
            lat: 21.1368, // Ramdaspeth
            lng: 79.0795,
            availability: "BROWSING" as const,
          },
          {
            phone: "+999000000005",
            name: "Ananya Iyer",
            bio: "Architect & weekend painter. Looking for art gallery companions.",
            interests: ["Art", "Photography", "Music", "Fitness"],
            lat: 21.1594, // Sadar
            lng: 79.0806,
            availability: "OPEN" as const,
          },
        ];

        for (const [index, u] of testUsers.entries()) {
          const userId = seedId(`user:${String(index)}`);
          const user = await db.user.upsert({
            where: { id: userId },
            create: {
              id: userId,
              phoneE164: u.phone,
              phoneVerifiedAt: new Date(),
              status: "ACTIVE",
              dateOfBirth: new Date("1998-08-20"),
              adultVerifiedAt: new Date(),
              guidelinesAcceptedAt: new Date(),
              profile: {
                create: {
                  displayName: `Demo ${u.name}`,
                  bio: `Fictional test profile. ${u.bio}`,
                  interests: u.interests,
                  showAge: true,
                  discoveryRadiusKm: 15,
                },
              },
              photos: { create: photo(index + 1) },
            },
            update: {},
          });

          // Create or update Presence
          const cellId = computeCellId(u.lat, u.lng);
          const expiresAt = new Date(Date.now() + 24 * 60 * 60 * 1000); // 24 hours
          await db.presence.upsert({
            where: { userId: user.id },
            create: {
              userId: user.id,
              availability: u.availability,
              lat: u.lat,
              lng: u.lng,
              accuracyM: 100,
              cellId,
              updatedAt: new Date(),
              expiresAt,
            },
            update: {
              availability: u.availability,
              lat: u.lat,
              lng: u.lng,
              cellId,
              updatedAt: new Date(),
              expiresAt,
            },
          });
        }

        // 3. Seed Pools (Happening Tab)
        const poolsData = [
          {
            title: "Sunday Badminton & Breakfast",
            description:
              "Friendly doubles match followed by South Indian breakfast at Sagar Ratna.",
            category: "Sports",
            capacity: 4,
            venueName: "Dhanwate National College Sports Ground",
            venueLat: 21.132,
            venueLng: 79.094,
            areaLabel: "Congress Nagar, Nagpur",
            startsAt: new Date(Date.now() + 2 * 24 * 60 * 60 * 1000), // 2 days later
          },
          {
            title: "Board Games & Pour-Over Coffee",
            description:
              "Bringing Catan and Wingspan. All experience levels welcome!",
            category: "Social",
            capacity: 6,
            venueName: "Demo Cafe, Dharampeth",
            venueLat: 21.1458,
            venueLng: 79.0882,
            areaLabel: "Dharampeth, Nagpur",
            startsAt: new Date(Date.now() + 1 * 24 * 60 * 60 * 1000), // tomorrow
          },
          {
            title: "Sunset Heritage Photography Walk",
            description:
              "Capturing the evening golden hour around the tombs and gardens.",
            category: "Creative",
            capacity: 8,
            venueName: "Ambazari Lake Garden Entrance",
            venueLat: 21.1335,
            venueLng: 79.0453,
            areaLabel: "Ambazari, Nagpur",
            startsAt: new Date(Date.now() + 3 * 24 * 60 * 60 * 1000), // 3 days later
          },
        ];

        for (const [index, p] of poolsData.entries()) {
          const poolId = seedId(`pool:${String(index)}`);
          const cell = computeCellId(p.venueLat, p.venueLng);
          await db.pool.upsert({
            where: { id: poolId },
            update: {
              venueName: p.venueName,
              venueLat: p.venueLat,
              venueLng: p.venueLng,
              areaLabel: p.areaLabel,
              areaCell: cell,
              startsAt: p.startsAt,
              status: "OPEN",
            },
            create: {
              id: poolId,
              organizerId: organizer.id,
              title: `[Demo] ${p.title}`,
              description: `Fictional test activity. ${p.description}`,
              category: p.category,
              capacity: p.capacity,
              venueName: p.venueName,
              venueLat: p.venueLat,
              venueLng: p.venueLng,
              areaLabel: p.areaLabel,
              areaCell: cell,
              startsAt: p.startsAt,
              status: "OPEN",
              members: {
                create: {
                  id: seedId(`pool-member:${String(index)}`),
                  userId: organizer.id,
                },
              },
              conversation: {
                create: {
                  id: seedId(`pool-conversation:${String(index)}`),
                  type: "POOL",
                },
              },
            },
          });
        }

        // 4. Seed Events & Ticket Tiers (Tickets Tab)
        const eventsData = [
          {
            title: "Nagpur Indie Sunset Sessions",
            description:
              "Live acoustic performances by top indie singer-songwriters under the autumn sky with artisan food and craft brews.",
            category: "Live Music",
            venueName: "Demo Rooftop, Dharampeth",
            address: "Dharampeth, Nagpur, Maharashtra",
            lat: 21.1458,
            lng: 79.0882,
            startsAt: new Date(Date.now() + 4 * 24 * 60 * 60 * 1000),
            endsAt: new Date(
              Date.now() + 4 * 24 * 60 * 60 * 1000 + 4 * 60 * 60 * 1000,
            ),
            tiers: [
              { name: "Early Bird", pricePaise: 49900, capacity: 50 },
              { name: "General Admission", pricePaise: 79900, capacity: 150 },
              {
                name: "VIP + Artist Meet & Greet",
                pricePaise: 149900,
                capacity: 20,
              },
            ],
          },
          {
            title: "Nagpur Founders & Creators Breakfast",
            description:
              "An intimate gathering of tech founders, AI builders, and consumer product designers sharing real war stories.",
            category: "Tech & Networking",
            venueName: "Demo Social, Civil Lines",
            address: "Civil Lines, Nagpur, Maharashtra",
            lat: 21.1532,
            lng: 79.0731,
            startsAt: new Date(Date.now() + 6 * 24 * 60 * 60 * 1000),
            endsAt: new Date(
              Date.now() + 6 * 24 * 60 * 60 * 1000 + 3 * 60 * 60 * 1000,
            ),
            tiers: [
              {
                name: "Founder Pass (Includes Breakfast)",
                pricePaise: 35000,
                capacity: 60,
              },
            ],
          },
        ];

        for (const [index, e] of eventsData.entries()) {
          const eventId = seedId(`event:${String(index)}`);
          await db.event.upsert({
            where: { id: eventId },
            update: {
              title: `[Demo] ${e.title}`,
              venueName: e.venueName,
              address: e.address,
              lat: e.lat,
              lng: e.lng,
              startsAt: e.startsAt,
              endsAt: e.endsAt,
              salesEndAt: e.startsAt,
              status: "PUBLISHED",
            },
            create: {
              id: eventId,
              organizerId: organizer.id,
              title: `[Demo] ${e.title}`,
              description: `Fictional test event; no real booking. ${e.description}`,
              category: e.category,
              venueName: e.venueName,
              address: e.address,
              lat: e.lat,
              lng: e.lng,
              startsAt: e.startsAt,
              endsAt: e.endsAt,
              status: "PUBLISHED",
              publishedAt: new Date(),
              salesStartAt: new Date(),
              salesEndAt: e.startsAt,
              tiers: {
                create: e.tiers.map((t, tierIndex) => ({
                  id: seedId(
                    `event-tier:${String(index)}:${String(tierIndex)}`,
                  ),
                  name: t.name,
                  pricePaise: t.pricePaise,
                  capacity: t.capacity,
                  sortOrder: tierIndex,
                })),
              },
            },
          });
        }
      },
      { timeout: 60000 },
    );
    console.log(
      "Demo seed ready: 5 profiles with photos, 4 presences (24h), 3 pools with conversations, 2 events, 4 ticket tiers.",
    );
  } finally {
    await client.$disconnect();
  }
}

main().catch(() => {
  // Database errors can contain connection details. Keep credentials out of logs.
  console.error(
    "Seeding failed. Check APP_ENV, DATABASE_URL, migrations, and connectivity.",
  );
  process.exitCode = 1;
});
