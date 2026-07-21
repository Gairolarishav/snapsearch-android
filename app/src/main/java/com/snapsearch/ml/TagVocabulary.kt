package com.snapsearch.ml

/**
 * Curated label vocabulary for Phase 2 zero-shot tagging (SnapSearch_Implementation_Plan.md
 * §3, step 4). Each entry has a short [Label.tag] (stored as-is in `ImageEntity.captionText`
 * and shown to the user) and a longer [Label.prompt] used only for embedding — Phase 1.5's
 * cross-modal sanity check found MobileCLIP S0 int8 discriminates poorly between bare,
 * generic short phrases, so prompts here are deliberately more descriptive than the tags
 * they represent.
 *
 * This is a first-pass ~130-label vocabulary, intentionally scoped down from the
 * aspirational 1,000-2,000 label target in the planning docs so tag quality can be
 * spot-checked on real photos before investing in a much larger set — expanding/pruning
 * this list is expected Phase 5 tuning work, not a correctness bug.
 */
object TagVocabulary {

    data class Label(val tag: String, val prompt: String)

    val LABELS: List<Label> = listOf(
        // Screenshots & phone UI
        Label("app screenshot", "a screenshot of a smartphone app interface"),
        Label("chat conversation", "a screenshot of a text message or chat conversation"),
        Label("error message", "a screenshot showing an error message or warning dialog"),
        Label("social media post", "a screenshot of a social media post or feed"),
        Label("map navigation", "a screenshot of a map or navigation app showing directions"),
        Label("web page", "a screenshot of a website in a web browser"),
        Label("video call", "a screenshot of a video call or virtual meeting"),
        Label("code editor", "a screenshot of programming code in an editor or terminal"),
        Label("spreadsheet", "a screenshot of a spreadsheet with rows and columns of data"),
        Label("calendar app", "a screenshot of a calendar or scheduling app"),
        Label("shopping app", "a screenshot of an online shopping or e-commerce app"),
        Label("weather app", "a screenshot of a weather forecast app"),
        Label("music player", "a screenshot of a music or podcast player app"),
        Label("settings menu", "a screenshot of a phone settings menu"),
        Label("notification", "a screenshot of a phone notification banner"),
        Label("email", "a screenshot of an email inbox or message"),
        Label("phone call log", "a screenshot of a phone call log or contacts list"),
        Label("video game", "a screenshot from a video game"),
        Label("movie or show", "a screenshot from a movie or TV show"),
        Label("meme", "a screenshot of an internet meme with text overlay"),

        // Documents
        Label("receipt", "a photo of a printed store receipt with prices"),
        Label("invoice", "a photo of a printed invoice or bill"),
        Label("boarding pass", "a photo of an airline boarding pass or travel ticket"),
        Label("id card", "a photo of an identification card or passport"),
        Label("business card", "a photo of a business card"),
        Label("handwritten note", "a photo of a handwritten note on paper"),
        Label("printed document", "a photo of a printed page of text or a document"),
        Label("whiteboard", "a photo of a whiteboard with writing or diagrams"),
        Label("presentation slide", "a photo of a presentation slide projected on a screen"),
        Label("restaurant menu", "a photo of a restaurant menu"),
        Label("street sign", "a photo of a street sign or storefront sign"),
        Label("price tag", "a photo of a price tag or product label"),
        Label("barcode or QR code", "a photo of a barcode or QR code"),
        Label("certificate", "a photo of a certificate or diploma"),
        Label("medical report", "a photo of a medical report or prescription"),
        Label("x-ray or scan", "a photo of a medical x-ray or scan image"),
        Label("chart or graph", "a photo of a chart or graph with data"),
        Label("book page", "a photo of a page from a book"),
        Label("newspaper", "a photo of a newspaper or magazine page"),
        Label("form", "a photo of a printed form with fields to fill in"),

        // People
        Label("selfie", "a selfie photo of one person"),
        Label("portrait", "a portrait photo of a person's face"),
        Label("group photo", "a group photo of several people together"),
        Label("baby or child", "a photo of a baby or young child"),
        Label("wedding", "a photo from a wedding ceremony"),
        Label("birthday party", "a photo from a birthday party with a cake"),
        Label("graduation", "a photo from a graduation ceremony"),
        Label("concert or crowd", "a photo of a concert or a crowd of people"),
        Label("sports event", "a photo of people playing or watching a sports event"),
        Label("dancing", "a photo of people dancing"),

        // Nature & outdoors
        Label("sunset or sunrise", "a photo of a sunset or sunrise sky"),
        Label("beach or ocean", "a photo of a beach or ocean"),
        Label("mountain", "a photo of mountains or a hiking trail"),
        Label("forest", "a photo of a forest or trees"),
        Label("garden or flowers", "a photo of a garden or flowers"),
        Label("snow", "a photo of snow or a winter scene"),
        Label("night sky", "a photo of a night sky or stars"),
        Label("rain or storm", "a photo of rain or a storm"),
        Label("lake or river", "a photo of a lake or river"),
        Label("desert", "a photo of a desert landscape"),
        Label("waterfall", "a photo of a waterfall"),
        Label("fireworks", "a photo of fireworks in the sky"),

        // Animals
        Label("dog", "a photo of a pet dog"),
        Label("cat", "a photo of a pet cat"),
        Label("bird", "a photo of a bird"),
        Label("wildlife", "a photo of wild animals"),
        Label("fish or aquarium", "a photo of fish or an aquarium"),
        Label("insect", "a photo of an insect or bug"),
        Label("farm animal", "a photo of a farm animal such as a horse or cow"),

        // Food & drink
        Label("restaurant meal", "a photo of a plated restaurant meal"),
        Label("home cooked food", "a photo of home-cooked food"),
        Label("dessert", "a photo of a dessert or sweet treat"),
        Label("coffee or drink", "a photo of a coffee or a beverage"),
        Label("fruit or produce", "a photo of fresh fruit or vegetables"),
        Label("baked goods", "a photo of baked goods like bread or cake"),
        Label("alcoholic drink", "a photo of wine, beer, or a cocktail"),

        // Places & architecture
        Label("city skyline", "a photo of a city skyline or cityscape"),
        Label("building exterior", "a photo of a building's exterior"),
        Label("interior room", "a photo of the interior of a room"),
        Label("bedroom", "a photo of a bedroom"),
        Label("kitchen", "a photo of a kitchen"),
        Label("office", "a photo of an office workspace"),
        Label("street scene", "a photo of a busy street scene"),
        Label("historic landmark", "a photo of a historic landmark or monument"),
        Label("bridge", "a photo of a bridge"),
        Label("airport", "a photo of an airport terminal"),
        Label("temple or church", "a photo of a temple, church, or place of worship"),
        Label("museum or gallery", "a photo of a museum or art gallery interior"),
        Label("stadium or arena", "a photo of a stadium or sports arena"),
        Label("farm or field", "a photo of a farm or open field"),

        // Vehicles
        Label("car", "a photo of a car"),
        Label("motorcycle or bicycle", "a photo of a motorcycle or bicycle"),
        Label("airplane", "a photo of an airplane"),
        Label("boat or ship", "a photo of a boat or ship"),
        Label("train", "a photo of a train"),
        Label("bus", "a photo of a bus"),

        // Art, objects & media
        Label("painting", "a photo of a painting or artwork"),
        Label("sculpture", "a photo of a sculpture or statue"),
        Label("sketch or drawing", "a photo of a pencil sketch or drawing"),
        Label("clothing or fashion", "a photo of clothing or a fashion outfit"),
        Label("electronics or gadget", "a photo of an electronic device or gadget"),
        Label("furniture", "a photo of a piece of furniture"),
        Label("toy", "a photo of a toy"),
        Label("jewelry", "a photo of jewelry or an accessory"),
        Label("tool or equipment", "a photo of a tool or piece of equipment"),
        Label("plant", "a photo of a potted plant or houseplant"),
        Label("book cover", "a photo of a book cover"),
        Label("musical instrument", "a photo of a musical instrument"),
        Label("sports equipment", "a photo of sports equipment or gear"),
        Label("packaging or box", "a photo of a product box or package"),
    )
}
