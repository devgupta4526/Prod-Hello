import { v4 as uuidv4 } from "uuid";
import { readEnvironment } from "./config/env.js";
import { createDatabase } from "./lib/database.js";
import { computeCellId } from "./lib/location.js";

async function main() {
  const env = readEnvironment();
  const db = createDatabase(env.DATABASE_URL);

  console.log("Connecting to database at", env.DATABASE_URL);
  await db.$connect();

  console.log("Seeding test data...");

  // 1. Organizer User: Delhi Culture Collective
  const organizerId = uuidv4();
  const organizer = await db.user.upsert({
    where: { phoneE164: "+919876543210" },
    create: {
      id: organizerId,
      phoneE164: "+919876543210",
      phoneVerifiedAt: new Date(),
      status: "ACTIVE",
      role: "USER",
      isOrganizer: true,
      dateOfBirth: new Date("1995-05-15"),
      adultVerifiedAt: new Date(),
      guidelinesAcceptedAt: new Date(),
      profile: {
        create: {
          displayName: "Delhi Culture Club",
          bio: "Curating indie music, rooftop mixers, and heritage walks across Delhi NCR.",
          interests: ["Music", "Culture", "Nightlife", "Art"],
          showAge: false,
          discoveryRadiusKm: 25,
        },
      },
    },
    update: {
      isOrganizer: true,
      status: "ACTIVE",
    },
  });

  // 2. Regular active users for Discovery
  const testUsers = [
    {
      phone: "+919811111111",
      name: "Aarav Sharma",
      bio: "Coffee enthusiast, amateur guitarist, always up for badminton.",
      interests: ["Coffee", "Music", "Badminton", "Tech"],
      lat: 28.6139, // Connaught Place
      lng: 77.2090,
      availability: "OPEN" as const,
    },
    {
      phone: "+919822222222",
      name: "Priya Nair",
      bio: "Product designer & book lover. Exploring Delhi's street food and cafes.",
      interests: ["Design", "Books", "Food", "Travel"],
      lat: 28.5535, // Hauz Khas
      lng: 77.1945,
      availability: "OPEN" as const,
    },
    {
      phone: "+919833333333",
      name: "Rohan Verma",
      bio: "Runner and cinema nerd. Morning runs at Lodhi Garden.",
      interests: ["Running", "Cinema", "Photography", "Fitness"],
      lat: 28.5933, // Lodhi Garden
      lng: 77.2197,
      availability: "BROWSING" as const,
    },
    {
      phone: "+919844444444",
      name: "Ananya Iyer",
      bio: "Architect & weekend painter. Looking for art gallery companions.",
      interests: ["Art", "Architecture", "Concerts", "Yoga"],
      lat: 28.4595, // Cyber Hub / Gurgaon
      lng: 77.0266,
      availability: "OPEN" as const,
    },
  ];

  for (const u of testUsers) {
    const userId = uuidv4();
    const user = await db.user.upsert({
      where: { phoneE164: u.phone },
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
            displayName: u.name,
            bio: u.bio,
            interests: u.interests,
            showAge: true,
            discoveryRadiusKm: 15,
          },
        },
      },
      update: {
        status: "ACTIVE",
      },
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
      description: "Friendly doubles match followed by South Indian breakfast at Sagar Ratna.",
      category: "Sports",
      capacity: 4,
      venueName: "Siri Fort Sports Complex",
      venueLat: 28.5524,
      venueLng: 77.2185,
      areaLabel: "Siri Fort, New Delhi",
      startsAt: new Date(Date.now() + 2 * 24 * 60 * 60 * 1000), // 2 days later
    },
    {
      title: "Board Games & Pour-Over Coffee",
      description: "Bringing Catan and Wingspan. All experience levels welcome!",
      category: "Social",
      capacity: 6,
      venueName: "Blue Tokai Coffee Roasters",
      venueLat: 28.6328,
      venueLng: 77.2195,
      areaLabel: "Connaught Place, New Delhi",
      startsAt: new Date(Date.now() + 1 * 24 * 60 * 60 * 1000), // tomorrow
    },
    {
      title: "Sunset Heritage Photography Walk",
      description: "Capturing the evening golden hour around the tombs and gardens.",
      category: "Creative",
      capacity: 8,
      venueName: "Lodhi Garden Main Gate",
      venueLat: 28.5933,
      venueLng: 77.2197,
      areaLabel: "Lodhi Estate, New Delhi",
      startsAt: new Date(Date.now() + 3 * 24 * 60 * 60 * 1000), // 3 days later
    },
  ];

  for (const p of poolsData) {
    const poolId = uuidv4();
    const cell = computeCellId(p.venueLat, p.venueLng);
    await db.pool.create({
      data: {
        id: poolId,
        organizerId: organizer.id,
        title: p.title,
        description: p.description,
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
            id: uuidv4(),
            userId: organizer.id,
          },
        },
      },
    });
  }

  // 4. Seed Events & Ticket Tiers (Tickets Tab)
  const eventsData = [
    {
      title: "Delhi Indie Sunset Sessions",
      description: "Live acoustic performances by top indie singer-songwriters under the autumn sky with artisan food and craft brews.",
      category: "Live Music",
      venueName: "The Rooftop at Connaught Place",
      address: "Radial Road 3, Inner Circle, Connaught Place, New Delhi",
      lat: 28.6315,
      lng: 77.2167,
      startsAt: new Date(Date.now() + 4 * 24 * 60 * 60 * 1000),
      endsAt: new Date(Date.now() + 4 * 24 * 60 * 60 * 1000 + 4 * 60 * 60 * 1000),
      tiers: [
        { name: "Early Bird", pricePaise: 49900, capacity: 50 },
        { name: "General Admission", pricePaise: 79900, capacity: 150 },
        { name: "VIP + Artist Meet & Greet", pricePaise: 149900, capacity: 20 },
      ],
    },
    {
      title: "Delhi Founders & Creators Breakfast",
      description: "An intimate gathering of tech founders, AI builders, and consumer product designers sharing real war stories.",
      category: "Tech & Networking",
      venueName: "Social Cyber Hub",
      address: "DLF Cyber City, Sector 24, Gurugram",
      lat: 28.4950,
      lng: 77.0895,
      startsAt: new Date(Date.now() + 6 * 24 * 60 * 60 * 1000),
      endsAt: new Date(Date.now() + 6 * 24 * 60 * 60 * 1000 + 3 * 60 * 60 * 1000),
      tiers: [
        { name: "Founder Pass (Includes Breakfast)", pricePaise: 35000, capacity: 60 },
      ],
    },
  ];

  for (const e of eventsData) {
    const eventId = uuidv4();
    await db.event.create({
      data: {
        id: eventId,
        organizerId: organizer.id,
        title: e.title,
        description: e.description,
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
          create: e.tiers.map((t) => ({
            id: uuidv4(),
            name: t.name,
            pricePaise: t.pricePaise,
            capacity: t.capacity,
          })),
        },
      },
    });
  }

  console.log("Database seeded successfully with test users, presence, pools, and events!");
  await db.$disconnect();
}

main().catch((err) => {
  console.error("Seeding failed:", err);
  process.exit(1);
});
