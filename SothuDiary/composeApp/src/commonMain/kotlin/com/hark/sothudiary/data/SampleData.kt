package com.hark.sothudiary.data

/**
 * Clearly labelled SAMPLE data around Madurai so the price check and the
 * Go / Hold / Drop summary can be demonstrated. All entries have sample = true
 * and can be removed in one tap from Settings. Prices are illustrative only.
 */
object SampleData {
    private const val DAY = 86_400_000L
    private const val HOUR = 3_600_000L

    fun build(now: Long): AppData {
        fun ago(days: Int, hours: Int = 0) = now - days * DAY - hours * HOUR
        fun ahead(days: Int) = now + days * DAY

        val bMurugan = Broker(
            id = "s-b1", name = "Murugan (Sample)", phone = "9000000001", whatsapp = "9000000001",
            platform = SourcePlatform.YOUTUBE, area = "Vilangudi, Madurai North",
            notes = "Posts plot walk-through videos on YouTube. Responds quickly.", createdAt = ago(20), sample = true,
        )
        val bSelvi = Broker(
            id = "s-b2", name = "Selvi Realty (Sample)", phone = "9000000002", whatsapp = "9000000002",
            platform = SourcePlatform.INSTAGRAM, area = "Thirumangalam",
            notes = "Instagram reels of DTCP plots.", createdAt = ago(18), sample = true,
        )
        val bKarthik = Broker(
            id = "s-b3", name = "Karthik Land Promoters (Sample)", phone = "9000000003", whatsapp = "9000000003",
            platform = SourcePlatform.FACEBOOK, area = "Othakadai, Melur",
            notes = "Facebook group posts. Raised price once already.", createdAt = ago(15), sample = true,
        )

        fun prop(
            id: String, title: String, type: PropertyType, locality: String, taluk: String,
            size: Double, unit: AreaUnit, price: Long, broker: Broker?, status: DealStatus,
            docs: Set<DocItem>, url: String, platform: SourcePlatform, caption: String,
            lat: Double?, lng: Double?, notes: String, createdDaysAgo: Int,
        ) = Property(
            id = id, title = title, type = type, locality = locality, taluk = taluk, district = "Madurai",
            lat = lat, lng = lng, size = size, unit = unit, askingPrice = price, sourceUrl = url,
            sourcePlatform = platform, sourceCaption = caption, brokerId = broker?.id, status = status,
            docs = docs, notes = notes, createdAt = ago(createdDaysAgo), updatedAt = ago(createdDaysAgo), sample = true,
        )

        val allDocs = DocItem.entries.toSet()
        val props = listOf(
            prop(
                "s-p1", "Vilangudi DTCP plot near bypass", PropertyType.PLOT, "Vilangudi", "Madurai North",
                5.0, AreaUnit.CENTS, 45_00_000, bMurugan, DealStatus.NEGOTIATING, allDocs,
                "https://www.youtube.com/watch?v=sample1", SourcePlatform.YOUTUBE,
                "5 cent DTCP approved plot, 30 ft road, Vilangudi", 9.9480, 78.0940,
                "East facing. Neighbour houses already built.", 12,
            ),
            prop(
                "s-p2", "Vilangudi corner plot, 4 cents", PropertyType.PLOT, "Vilangudi", "Madurai North",
                4.0, AreaUnit.CENTS, 36_80_000, bMurugan, DealStatus.CONTACTED,
                setOf(DocItem.PATTA, DocItem.CHITTA, DocItem.EC, DocItem.APPROVAL, DocItem.ROAD_ACCESS),
                "https://www.youtube.com/watch?v=sample2", SourcePlatform.YOUTUBE,
                "Corner plot 4 cent near Vilangudi bus stop", 9.9502, 78.0915, "", 10,
            ),
            prop(
                "s-p3", "Urgent sale – Vilangudi 5 cents", PropertyType.LAND, "Vilangudi", "Madurai North",
                5.0, AreaUnit.CENTS, 22_00_000, bKarthik, DealStatus.CONTACTED, emptySet(),
                "https://www.facebook.com/groups/madurai.plots/sample3", SourcePlatform.FACEBOOK,
                "URGENT SALE!! 5 cent only 22 lakhs, advance today, register next week", null, null,
                "Asked for advance before showing documents.", 4,
            ),
            prop(
                "s-p4", "Vilangudi premium plot, main road", PropertyType.PLOT, "Vilangudi", "Madurai North",
                4.0, AreaUnit.CENTS, 54_00_000, bMurugan, DealStatus.NEW,
                setOf(DocItem.PATTA, DocItem.EC, DocItem.APPROVAL, DocItem.ROAD_ACCESS, DocItem.ELECTRICITY),
                "https://www.youtube.com/shorts/sample4", SourcePlatform.YOUTUBE,
                "Main road facing premium plot", 9.9466, 78.0961, "", 6,
            ),
            prop(
                "s-p5", "Thirumangalam layout plot, 10 cents", PropertyType.PLOT, "Thirumangalam", "Thirumangalam",
                10.0, AreaUnit.CENTS, 36_00_000, bSelvi, DealStatus.SITE_VISIT, allDocs - DocItem.FMB_SKETCH,
                "https://www.instagram.com/reel/sample5", SourcePlatform.INSTAGRAM,
                "10 cent plot, Thirumangalam, DTCP", 9.8215, 77.9850, "Visited – quiet layout, 2 km from NH.", 14,
            ),
            prop(
                "s-p6", "Thirumangalam 6 cent plot near school", PropertyType.PLOT, "Thirumangalam", "Thirumangalam",
                6.0, AreaUnit.CENTS, 22_20_000, bSelvi, DealStatus.NEW,
                setOf(DocItem.PATTA, DocItem.CHITTA, DocItem.EC, DocItem.APPROVAL),
                "https://www.instagram.com/p/sample6", SourcePlatform.INSTAGRAM,
                "6 cent plot near school", 9.8240, 77.9871, "", 9,
            ),
            prop(
                "s-p7", "Thirumangalam 8 cents, east facing", PropertyType.LAND, "Thirumangalam", "Thirumangalam",
                8.0, AreaUnit.CENTS, 30_00_000, bSelvi, DealStatus.CONTACTED,
                setOf(DocItem.PATTA, DocItem.EC, DocItem.ROAD_ACCESS),
                "https://www.instagram.com/reel/sample7", SourcePlatform.INSTAGRAM,
                "East facing 8 cent land", null, null, "", 8,
            ),
            prop(
                "s-p8", "Thirumangalam 5 cents – price raised", PropertyType.PLOT, "Thirumangalam", "Thirumangalam",
                5.0, AreaUnit.CENTS, 24_00_000, bKarthik, DealStatus.NEGOTIATING,
                setOf(DocItem.PATTA, DocItem.ROAD_ACCESS),
                "https://www.facebook.com/share/v/sample8", SourcePlatform.FACEBOOK,
                "5 cent plot Thirumangalam ready to register", 9.8190, 77.9902,
                "Broker first said ₹21L, now ₹24L.", 11,
            ),
            prop(
                "s-p9", "2BHK independent house, Othakadai", PropertyType.HOUSE, "Othakadai", "Madurai East",
                1200.0, AreaUnit.SQFT, 62_00_000, bKarthik, DealStatus.CONTACTED,
                setOf(DocItem.PATTA, DocItem.EC, DocItem.WATER, DocItem.ELECTRICITY, DocItem.ROAD_ACCESS),
                "https://www.facebook.com/marketplace/item/sample9", SourcePlatform.FACEBOOK,
                "2BHK house 1200 sqft on 3 cents, Othakadai", 9.9530, 78.1890, "Built 2015.", 7,
            ),
            prop(
                "s-p10", "2BHK apartment, Thirupparankundram", PropertyType.APARTMENT, "Thirupparankundram", "Thirupparankundram",
                950.0, AreaUnit.SQFT, 46_00_000, null, DealStatus.NEW,
                setOf(DocItem.APPROVAL, DocItem.WATER, DocItem.ELECTRICITY),
                "https://www.youtube.com/watch?v=sample10", SourcePlatform.YOUTUBE,
                "Ready to move 2BHK flat, 950 sqft, RERA", 9.8800, 78.0710, "", 3,
            ),
            prop(
                "s-p11", "Melur farmland, 2.5 acres with well", PropertyType.FARMLAND, "Melur", "Melur",
                2.5, AreaUnit.ACRES, 70_00_000, bKarthik, DealStatus.CONTACTED,
                setOf(DocItem.PATTA, DocItem.CHITTA, DocItem.FMB_SKETCH, DocItem.WATER),
                "https://www.facebook.com/groups/madurai.farmland/sample11", SourcePlatform.FACEBOOK,
                "2.5 acre farmland with open well, Melur", 10.0320, 78.3380, "Coconut trees, open well.", 13,
            ),
        )

        val interactions = listOf(
            Interaction("s-i1", "s-p1", bMurugan.id, ago(5, 3), InteractionType.VISIT, 46_00_000,
                "Site visit with broker. Plot is level, 30 ft road laid.", "Will share EC copy on WhatsApp.",
                ago(3), followUpDone = true, sample = true),
            Interaction("s-i2", "s-p1", bMurugan.id, ago(1, 2), InteractionType.CALL, 45_00_000,
                "Negotiated. Owner agreed to reduce ₹1L.", "Owner meeting on Sunday.",
                ahead(3), sample = true),
            Interaction("s-i3", "s-p3", bKarthik.id, ago(3, 5), InteractionType.WHATSAPP, 22_00_000,
                "Very low price, wants ₹1L advance today itself. No EC / patta shown yet.", "Will send patta photo.",
                ahead(1), sample = true),
            Interaction("s-i4", "s-p8", bKarthik.id, ago(10, 4), InteractionType.CALL, 21_00_000,
                "First call. Quoted ₹21L for 5 cents.", "Will send DTCP approval copy by Sunday.",
                ago(7), sample = true),
            Interaction("s-i5", "s-p8", bKarthik.id, ago(2, 6), InteractionType.CALL, 24_00_000,
                "Now says ₹24L – 'many enquiries'. Approval copy still not sent.", "Final price after owner talk.",
                ahead(2), sample = true),
            Interaction("s-i6", "s-p5", bSelvi.id, ago(6, 1), InteractionType.VISIT, 36_00_000,
                "Visited layout. Good approach road, drainage pending.", "Will check FMB sketch.",
                ahead(5), sample = true),
            Interaction("s-i7", "s-p11", bKarthik.id, ago(6, 8), InteractionType.CALL, 70_00_000,
                "Farmland with well, EB connection applied.", "Will arrange visit with owner.",
                ago(1), sample = true),
            Interaction("s-i8", "s-p9", bKarthik.id, ago(4, 2), InteractionType.MEETING, 64_00_000,
                "Met at office. Quoted ₹64L, came down to ₹62L.", "Send house tax receipts.",
                ahead(6), sample = true),
        )

        val guidelines = listOf(
            GuidelineValue("s-g1", "Vilangudi", perCent = 6_00_000.0, perSqft = null,
                note = "Sample value – check tnreginet.gov.in", sample = true),
            GuidelineValue("s-g2", "Thirumangalam", perCent = 2_20_000.0, perSqft = null,
                note = "Sample value – check tnreginet.gov.in", sample = true),
            GuidelineValue("s-g3", "Othakadai", perCent = 3_50_000.0, perSqft = 1_800.0,
                note = "Sample value – check tnreginet.gov.in", sample = true),
            GuidelineValue("s-g4", "Thirupparankundram", perCent = null, perSqft = 2_600.0,
                note = "Sample value – check tnreginet.gov.in", sample = true),
            GuidelineValue("s-g5", "Melur", perCent = 25_000.0, perSqft = null,
                note = "Sample value – check tnreginet.gov.in", sample = true),
        )

        return AppData(
            properties = props,
            brokers = listOf(bMurugan, bSelvi, bKarthik),
            interactions = interactions,
            guidelines = guidelines,
        )
    }
}
