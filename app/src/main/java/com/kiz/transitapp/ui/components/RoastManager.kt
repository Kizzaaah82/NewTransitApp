package com.kiz.transitapp.ui.components

/*
 * RoastManager.kt — Uncensored Savage Edition
 * Kieran's transit app: brutal, punny, filthy (never hateful), and non-repeating.
 *
 * Usage:
 *   val roast = RoastManager.randomRoast()
 *   val transitRoast = RoastManager.randomRoast(category = RoastManager.Category.TRANSIT)
 *
 * Notes:
 * - No slurs, no hate speech. Roast the situation and the user-as-user, not identities.
 * - Larger recent window to avoid repeats.
 * - Deterministic "roast of the day" supported via seed.
 */

import kotlin.random.Random
import kotlin.math.max

data class Roast(
    val text: String,
    val category: RoastManager.Category,
    val spice: RoastManager.Spice
)

object RoastManager {

    enum class Category { TRANSIT, WEATHER, TIME_OF_DAY, DEVICE, PERSONAL, PHILOSOPHY, VET_TECH }
    enum class Spice { MILD, MEDIUM, HELLFIRE }

    var recentWindow: Int = 50
        set(value) { field = max(8, value) }

    private val recent = ArrayDeque<Int>()

    val ROASTS: List<Roast> = listOf(
        Roast("Mind the gap — between your plans and reality.", Category.TRANSIT, Spice.MILD),
        Roast("Next arrival: eventually. Manage your expectations, legend.", Category.TRANSIT, Spice.MILD),
        Roast("Tap on, tap off, tap out.", Category.TRANSIT, Spice.MILD),
        Roast("Platform change? Surprise cardio unlocked.", Category.TRANSIT, Spice.MILD),
        Roast("Express energy, local results.", Category.TRANSIT, Spice.MILD),
        Roast("Stand clear of the doors — and your own bad choices.", Category.TRANSIT, Spice.MILD),
        Roast("Route map looks like spaghetti; so do your priorities.", Category.TRANSIT, Spice.MILD),
        Roast("Another delay? Character-building, you chaotic gremlin.", Category.TRANSIT, Spice.MEDIUM),
        Roast("You’ve stared at this stop so long you should pay property tax.", Category.TRANSIT, Spice.MEDIUM),
        Roast("Detour activated — just like your anxiety.", Category.TRANSIT, Spice.MEDIUM),
        Roast("Transit gods saw your face and added ten minutes.", Category.TRANSIT, Spice.MEDIUM),
        Roast("This timetable is written in crayon and lies.", Category.TRANSIT, Spice.MEDIUM),
        Roast("You missed it by thirty seconds — world record in almosts.", Category.TRANSIT, Spice.MEDIUM),
        Roast("Short-turning, like your attention span.", Category.TRANSIT, Spice.MEDIUM),
        Roast("Signal priority? You’re on ‘maybe later’.", Category.TRANSIT, Spice.MEDIUM),
        Roast("Your transfer window closed faster than your willpower.", Category.TRANSIT, Spice.MEDIUM),
        Roast("Headsign: Not in Service — like your motivation.", Category.TRANSIT, Spice.MEDIUM),
        Roast("This stop is a museum exhibit titled ‘Hope in Captivity’.", Category.TRANSIT, Spice.MEDIUM),
        Roast("Wayfinding tip: try finding your way out of denial.", Category.TRANSIT, Spice.MEDIUM),
        Roast("Bus ETA: unknown — much like your fucking future.", Category.TRANSIT, Spice.HELLFIRE),
        Roast("Delay so long you could knit yourself a new personality.", Category.TRANSIT, Spice.HELLFIRE),
        Roast("Even the driver’s avoiding this route like your responsibilities.", Category.TRANSIT, Spice.HELLFIRE),
        Roast("You’ve waited here longer than your parents believed in you.", Category.TRANSIT, Spice.HELLFIRE),
        Roast("If maps were honest, this station would be labeled ‘Regret’.", Category.TRANSIT, Spice.HELLFIRE),
        Roast("Detours ahead — because nothing in your life goes in a straight line.", Category.TRANSIT, Spice.HELLFIRE),
        Roast("Even the timetable is gaslighting you.", Category.TRANSIT, Spice.HELLFIRE),
        Roast("You sprinted and still lost. Athleticism by vibes.", Category.TRANSIT, Spice.HELLFIRE),
        Roast("Missed connection — congrats, you got ghosted by public infrastructure.", Category.TRANSIT, Spice.HELLFIRE),
        Roast("This route’s less reliable than your moral compass.", Category.TRANSIT, Spice.HELLFIRE),
        Roast("Tap failed, plan failed, vibes failed — triple kill.", Category.TRANSIT, Spice.HELLFIRE),
        Roast("Real-time update: HAHAHA. That’s it. That’s the update.", Category.TRANSIT, Spice.HELLFIRE),
        Roast("Platform swap again? Consider it leg day, doughboy.", Category.TRANSIT, Spice.HELLFIRE),
        Roast("You track buses like Bigfoot: lots of maps, zero sightings.", Category.TRANSIT, Spice.HELLFIRE),
        Roast("Fare evasion? Cute. You can’t even escape yourself.", Category.TRANSIT, Spice.HELLFIRE),
        Roast("You and punctuality are on different lines and neither of you transfers.", Category.TRANSIT, Spice.HELLFIRE),
        Roast("Locomoti-NO. You’re on the wrong track, star.", Category.TRANSIT, Spice.HELLFIRE),
        Roast("Nice day to pretend you’re thriving.", Category.WEATHER, Spice.MILD),
        Roast("Sunshine won’t fix your vibe, but it’s trying its best.", Category.WEATHER, Spice.MILD),
        Roast("Drizzle: technically weather, technically effort.", Category.WEATHER, Spice.MILD),
        Roast("Cloud cover, just like your clarity.", Category.WEATHER, Spice.MILD),
        Roast("Rainbow? That’s just your screen glare again.", Category.WEATHER, Spice.MILD),
        Roast("Breezy — air out those delusions.", Category.WEATHER, Spice.MILD),
        Roast("Barometer falling, standards following.", Category.WEATHER, Spice.MILD),
        Roast("UV low, output lower.", Category.WEATHER, Spice.MILD),
        Roast("Humidity up; confidence down. Frizz meets fizzled dreams.", Category.WEATHER, Spice.MEDIUM),
        Roast("Cold front moving in — your empathy relates.", Category.WEATHER, Spice.MEDIUM),
        Roast("Thunderstorm incoming like your inbox and the consequences.", Category.WEATHER, Spice.MEDIUM),
        Roast("Air quality: questionable, like every take you had this week.", Category.WEATHER, Spice.MEDIUM),
        Roast("It’s muggy — your mood is, too.", Category.WEATHER, Spice.MEDIUM),
        Roast("Pressure rising: your anxiety cosplaying as meteorology.", Category.WEATHER, Spice.MEDIUM),
        Roast("First snow and your productivity already called in sick.", Category.WEATHER, Spice.MEDIUM),
        Roast("Heat wave: perfect, your excuses can finally sweat.", Category.WEATHER, Spice.MEDIUM),
        Roast("Wind advisory: try not to get blown off course again.", Category.WEATHER, Spice.MEDIUM),
        Roast("Sunset’s stunning; you’re indoors arguing with a schedule.", Category.WEATHER, Spice.MEDIUM),
        Roast("Pollen count high — bless you and your underwhelming plans.", Category.WEATHER, Spice.MEDIUM),
        Roast("Feels like −10°C? That’s your charisma: frostbitten and brittle.", Category.WEATHER, Spice.HELLFIRE),
        Roast("Lightning nearby. Relax — nothing about you is conductive.", Category.WEATHER, Spice.HELLFIRE),
        Roast("Wind chill means you won’t be the coldest thing outside.", Category.WEATHER, Spice.HELLFIRE),
        Roast("It’s raining and you still smell like disappointment.", Category.WEATHER, Spice.HELLFIRE),
        Roast("Global warming looked at you and said, ‘hard pass’.", Category.WEATHER, Spice.HELLFIRE),
        Roast("UV index: scorched earth — like your to-do list after noon.", Category.WEATHER, Spice.HELLFIRE),
        Roast("You’re the human embodiment of drizzle: there, annoying, forgettable.", Category.WEATHER, Spice.HELLFIRE),
        Roast("Hail? Cute. Reality’s been pelting you all week.", Category.WEATHER, Spice.HELLFIRE),
        Roast("Partly cloud-fucked with a chance of ‘try again tomorrow’.", Category.WEATHER, Spice.HELLFIRE),
        Roast("Humidity? More like humi-damn-ity — even the air clings like your bad takes.", Category.WEATHER, Spice.HELLFIRE),
        Roast("Good morning, champion of mediocrity.", Category.TIME_OF_DAY, Spice.MILD),
        Roast("Morning routine: survive. Barely.", Category.TIME_OF_DAY, Spice.MILD),
        Roast("Saturday energy: chaotic neutral. Yours: chaotic tired.", Category.TIME_OF_DAY, Spice.MILD),
        Roast("Tea time. Try not to baptize your keyboard again.", Category.TIME_OF_DAY, Spice.MILD),
        Roast("Weekday mood: we’ll try again tomorrow.", Category.TIME_OF_DAY, Spice.MILD),
        Roast("Your bedtime is a rumor with trust issues.", Category.TIME_OF_DAY, Spice.MILD),
        Roast("Brunch is just breakfast with lies.", Category.TIME_OF_DAY, Spice.MILD),
        Roast("Dawn patrol? More like yawn patrol.", Category.TIME_OF_DAY, Spice.MILD),
        Roast("Lunch already? Time flies when you’re avoiding work.", Category.TIME_OF_DAY, Spice.MEDIUM),
        Roast("It’s 3 p.m. — that cursed hour where dreams go to die.", Category.TIME_OF_DAY, Spice.MEDIUM),
        Roast("Evening again. Participation trophy unlocked: adulthood.", Category.TIME_OF_DAY, Spice.MEDIUM),
        Roast("Noon: day half over, tasks at 10%.", Category.TIME_OF_DAY, Spice.MEDIUM),
        Roast("Sunset says stop; you heard ‘start panicking’.", Category.TIME_OF_DAY, Spice.MEDIUM),
        Roast("It’s prime procrastination o’clock.", Category.TIME_OF_DAY, Spice.MEDIUM),
        Roast("Alarm off, delusion on.", Category.TIME_OF_DAY, Spice.MEDIUM),
        Roast("You treat calendars like optional fiction.", Category.TIME_OF_DAY, Spice.MEDIUM),
        Roast("Late-night coding: bold move fighting bugs with half a brain.", Category.TIME_OF_DAY, Spice.MEDIUM),
        Roast("Golden hour won’t make those choices look better.", Category.TIME_OF_DAY, Spice.MEDIUM),
        Roast("Rise and whine, sunshine.", Category.TIME_OF_DAY, Spice.MEDIUM),
        Roast("It’s midnight, you insomniac raccoon — go the fuck to sleep.", Category.TIME_OF_DAY, Spice.HELLFIRE),
        Roast("You’ve been awake 18 hours and produced exactly one thought.", Category.TIME_OF_DAY, Spice.HELLFIRE),
        Roast("You greet dawn like a debt collector at your door.", Category.TIME_OF_DAY, Spice.HELLFIRE),
        Roast("Midnight thoughts again? Put them back; they’re feral.", Category.TIME_OF_DAY, Spice.HELLFIRE),
        Roast("You’re on a first-name basis with 2 a.m. and regret.", Category.TIME_OF_DAY, Spice.HELLFIRE),
        Roast("You earned a break from all that underachieving.", Category.TIME_OF_DAY, Spice.HELLFIRE),
        Roast("It’s tomorrow already; your plan is still yesterday.", Category.TIME_OF_DAY, Spice.HELLFIRE),
        Roast("You and time management are estranged parents.", Category.TIME_OF_DAY, Spice.HELLFIRE),
        Roast("Midday sun is up; your standards aren’t.", Category.TIME_OF_DAY, Spice.HELLFIRE),
        Roast("Carpe diem? Carpe nah — you seized jack shit.", Category.TIME_OF_DAY, Spice.HELLFIRE),
        Roast("Battery low — just like your standards.", Category.DEVICE, Spice.MILD),
        Roast("Signal weak, like your arguments.", Category.DEVICE, Spice.MILD),
        Roast("Clipboard empty, like your alibi.", Category.DEVICE, Spice.MILD),
        Roast("Notifications silenced. Blessed peace — for everyone else.", Category.DEVICE, Spice.MILD),
        Roast("Hotspot on — congrats, you finally provide value.", Category.DEVICE, Spice.MILD),
        Roast("Headphones connected; at least your ears made a friend.", Category.DEVICE, Spice.MILD),
        Roast("Auto-rotate: doing more turning than your career.", Category.DEVICE, Spice.MILD),
        Roast("Brightness: max. Insight: min.", Category.DEVICE, Spice.MILD),
        Roast("Storage full? Maybe delete your bad decisions first.", Category.DEVICE, Spice.MEDIUM),
        Roast("Screen time today: impressive — if failure were a metric.", Category.DEVICE, Spice.MEDIUM),
        Roast("Low storage: you hoard screenshots like regrets.", Category.DEVICE, Spice.MEDIUM),
        Roast("Fingerprint failed — even your phone’s protecting itself from you.", Category.DEVICE, Spice.MEDIUM),
        Roast("Bluetooth connected: finally, something paired successfully.", Category.DEVICE, Spice.MEDIUM),
        Roast("Wi‑Fi dropped — like your attention span.", Category.DEVICE, Spice.MEDIUM),
        Roast("Your charger cable is healthier than your habits.", Category.DEVICE, Spice.MEDIUM),
        Roast("Permissions denied. Boundaries — try them.", Category.DEVICE, Spice.MEDIUM),
        Roast("Auto-correct can’t save what you’re typing.", Category.DEVICE, Spice.MEDIUM),
        Roast("You run background tasks called ‘procrastination’.", Category.DEVICE, Spice.MEDIUM),
        Roast("Your RAM? Randomly Avoiding Meaning.", Category.DEVICE, Spice.MEDIUM),
        Roast("Battery at 1%. Fitting. You’re the human low-battery warning.", Category.DEVICE, Spice.HELLFIRE),
        Roast("Your phone’s faster than you’ll ever be — upgrade yourself.", Category.DEVICE, Spice.HELLFIRE),
        Roast("System update required: install a fucking personality.", Category.DEVICE, Spice.HELLFIRE),
        Roast("Airplane mode? Bold for someone who never takes off.", Category.DEVICE, Spice.HELLFIRE),
        Roast("Camera flipped to front-facing. Brave. Catastrophic.", Category.DEVICE, Spice.HELLFIRE),
        Roast("This app crashed to avoid your plans.", Category.DEVICE, Spice.HELLFIRE),
        Roast("Tabs open: 84. Thoughts coherent: zero.", Category.DEVICE, Spice.HELLFIRE),
        Roast("Gyroscope spinning — like your moral compass in a blender.", Category.DEVICE, Spice.HELLFIRE),
        Roast("Bluetooth? Blue-who-asked. Pair yourself with effort.", Category.DEVICE, Spice.HELLFIRE),
        Roast("You’re a pop-up ad for naps.", Category.PERSONAL, Spice.MILD),
        Roast("You’re the human shrug emoji.", Category.PERSONAL, Spice.MILD),
        Roast("You’re a vibe, and that vibe is maintenance mode.", Category.PERSONAL, Spice.MILD),
        Roast("Limited-time offer that never ends.", Category.PERSONAL, Spice.MILD),
        Roast("Confidence level: autocorrect when it’s wrong.", Category.PERSONAL, Spice.MILD),
        Roast("Doing fine — if ‘fine’ means coasting on chaos.", Category.PERSONAL, Spice.MILD),
        Roast("Main-character energy — of a printer tutorial.", Category.PERSONAL, Spice.MILD),
        Roast("Ambition not found. Try plugging it in.", Category.PERSONAL, Spice.MILD),
        Roast("Pun-derwhelming and over-caffeinated.", Category.PERSONAL, Spice.MILD),
        Roast("You’re a group project in human form.", Category.PERSONAL, Spice.MEDIUM),
        Roast("Evolution tried a patch; you’re the changelog nobody read.", Category.PERSONAL, Spice.MEDIUM),
        Roast("You radiate ‘forgot my password again’ energy.", Category.PERSONAL, Spice.MEDIUM),
        Roast("Somewhere, someone’s proud of you — probably by accident.", Category.PERSONAL, Spice.MEDIUM),
        Roast("You bring big ‘update later’ energy to everything.", Category.PERSONAL, Spice.MEDIUM),
        Roast("You’re a recurring captcha no one can solve.", Category.PERSONAL, Spice.MEDIUM),
        Roast("You’ve got the range — of a potato clock.", Category.PERSONAL, Spice.MEDIUM),
        Roast("Chaos with patchy Wi‑Fi.", Category.PERSONAL, Spice.MEDIUM),
        Roast("Your aura screams ‘left my potential in drafts’.", Category.PERSONAL, Spice.MEDIUM),
        Roast("You’re the sequel no one greenlit.", Category.PERSONAL, Spice.MEDIUM),
        Roast("You’re a walking dad joke without the dad or the joke — just the groan.", Category.PERSONAL, Spice.MEDIUM),
        Roast("You look like the before photo of a caffeine intervention.", Category.PERSONAL, Spice.HELLFIRE),
        Roast("You have the confidence of bunker Wi‑Fi.", Category.PERSONAL, Spice.HELLFIRE),
        Roast("Every time you speak, autocorrect gives up.", Category.PERSONAL, Spice.HELLFIRE),
        Roast("You’re the plot twist nobody asked for.", Category.PERSONAL, Spice.HELLFIRE),
        Roast("Your potential called. It hung up.", Category.PERSONAL, Spice.HELLFIRE),
        Roast("You could trip over a cordless phone.", Category.PERSONAL, Spice.HELLFIRE),
        Roast("Personality patch notes: fixed nothing, added drama.", Category.PERSONAL, Spice.HELLFIRE),
        Roast("You look tired in 4K and Dolby Atmos.", Category.PERSONAL, Spice.HELLFIRE),
        Roast("Your opinions arrive pre‑debunked.", Category.PERSONAL, Spice.HELLFIRE),
        Roast("Not the main plot — you’re the loading screen.", Category.PERSONAL, Spice.HELLFIRE),
        Roast("Daily routine: speedrun of bad timing.", Category.PERSONAL, Spice.HELLFIRE),
        Roast("Spoiler alert: disappointment.", Category.PERSONAL, Spice.HELLFIRE),
        Roast("Your resolve missed the bus and never caught up.", Category.PERSONAL, Spice.HELLFIRE),
        Roast("You’re a to‑do list written in disappearing ink.", Category.PERSONAL, Spice.HELLFIRE),
        Roast("Congrats, you’re bathtub toast: bold idea, shit execution.", Category.PERSONAL, Spice.HELLFIRE),
        Roast("The universe is vast; you still made it about you. Impressive.", Category.PHILOSOPHY, Spice.MILD),
        Roast("Memento mori, but first, memento coffee.", Category.PHILOSOPHY, Spice.MILD),
        Roast("Cogito ergo… never mind.", Category.PHILOSOPHY, Spice.MILD),
        Roast("Meaning of life: 42. Meaning of you: TBD.", Category.PHILOSOPHY, Spice.MILD),
        Roast("You venerate coffee, not wisdom. Valid.", Category.PHILOSOPHY, Spice.MILD),
        Roast("You meditate like a buffering gif.", Category.PHILOSOPHY, Spice.MILD),
        Roast("You pray to alarms and snooze the answers.", Category.PHILOSOPHY, Spice.MILD),
        Roast("Vanity of vanities; your to‑do list remains.", Category.PHILOSOPHY, Spice.MILD),
        Roast("You’re an unskippable ad in the simulation.", Category.PHILOSOPHY, Spice.MEDIUM),
        Roast("Entropy rises — and so does your unread email stack.", Category.PHILOSOPHY, Spice.MEDIUM),
        Roast("Free will? You can’t even choose a bedtime.", Category.PHILOSOPHY, Spice.MEDIUM),
        Roast("Time’s a flat circle; you found the dull edge.", Category.PHILOSOPHY, Spice.MEDIUM),
        Roast("Schrödinger’s motivation: there until needed.", Category.PHILOSOPHY, Spice.MEDIUM),
        Roast("You’re a paradox: loudly average.", Category.PHILOSOPHY, Spice.MEDIUM),
        Roast("You seek meaning like it’s on the next bus — spoiler: it isn’t.", Category.PHILOSOPHY, Spice.MEDIUM),
        Roast("You’re a thought experiment gone rogue.", Category.PHILOSOPHY, Spice.MEDIUM),
        Roast("Moral compass spinning — recalibrate with sleep.", Category.PHILOSOPHY, Spice.MEDIUM),
        Roast("You keep asking ‘why’ like it owes you rent.", Category.PHILOSOPHY, Spice.MEDIUM),
        Roast("I think; therefore I can’t with you.", Category.PHILOSOPHY, Spice.MEDIUM),
        Roast("You stare into the void, and it fucking sighs.", Category.PHILOSOPHY, Spice.HELLFIRE),
        Roast("Cosmic dust has more direction than your five‑year plan.", Category.PHILOSOPHY, Spice.HELLFIRE),
        Roast("You’re proof consciousness was a beta feature.", Category.PHILOSOPHY, Spice.HELLFIRE),
        Roast("Your purpose didn’t get lost; it ghosted you.", Category.PHILOSOPHY, Spice.HELLFIRE),
        Roast("You’re the punchline in a cosmic joke nobody laughed at.", Category.PHILOSOPHY, Spice.HELLFIRE),
        Roast("The abyss unfollowed you.", Category.PHILOSOPHY, Spice.HELLFIRE),
        Roast("Sisyphus saw your schedule and said, ‘damn’.", Category.PHILOSOPHY, Spice.HELLFIRE),
        Roast("You’re the heat death of enthusiasm.", Category.PHILOSOPHY, Spice.HELLFIRE),
        Roast("Knock‑knock joke with no punchline — that’s you.", Category.PHILOSOPHY, Spice.HELLFIRE),
        Roast("Destiny left you on read and blocked you.", Category.PHILOSOPHY, Spice.HELLFIRE),
        Roast("Existential crisis? More like existenti-hell, you disaster.", Category.PHILOSOPHY, Spice.HELLFIRE),
        Roast("You talk to pets better than people — correct fucking choice.", Category.VET_TECH, Spice.MILD),
        Roast("Another day, another fur‑covered battle.", Category.VET_TECH, Spice.MILD),
        Roast("You wield clippers like a therapist with scissors.", Category.VET_TECH, Spice.MILD),
        Roast("Treat‑pouch diplomacy beats your small talk.", Category.VET_TECH, Spice.MILD),
        Roast("Towel burrito master — now wrap up your excuses.", Category.VET_TECH, Spice.MILD),
        Roast("Powered by coffee, kibble, and denial.", Category.VET_TECH, Spice.MILD),
        Roast("Good kitty. Now do that to your schedule.", Category.VET_TECH, Spice.MILD),
        Roast("You’re the calming pheromone everyone else needs.", Category.VET_TECH, Spice.MILD),
        Roast("Paw‑sitive vibes? Try paw‑sibly on time.", Category.VET_TECH, Spice.MILD),
        Roast("Fur real: you need sleep.", Category.VET_TECH, Spice.MILD),
        Roast("Even the cats have boundaries you could learn from.", Category.VET_TECH, Spice.MEDIUM),
        Roast("Your scrubs are cleaner than your coping mechanisms.", Category.VET_TECH, Spice.MEDIUM),
        Roast("You smell like chlorhexidine and exhaustion.", Category.VET_TECH, Spice.MEDIUM),
        Roast("You can read a dog’s body language but miss your own red flags.", Category.VET_TECH, Spice.MEDIUM),
        Roast("You chart like a poet with trauma.", Category.VET_TECH, Spice.MEDIUM),
        Roast("More cones than achievements — woof.", Category.VET_TECH, Spice.MEDIUM),
        Roast("You nail vein shots and miss good sleep consistently.", Category.VET_TECH, Spice.MEDIUM),
        Roast("Your kennel cards are neat; your life, less so.", Category.VET_TECH, Spice.MEDIUM),
        Roast("You pour peroxide on wounds and gasoline on schedules.", Category.VET_TECH, Spice.MEDIUM),
        Roast("Your scrub pockets carry five tools and zero boundaries.", Category.VET_TECH, Spice.MEDIUM),
        Roast("The guinea pigs are squeaking about your time management.", Category.VET_TECH, Spice.MEDIUM),
        Roast("De‑fur‑nitely avoiding your inbox again.", Category.VET_TECH, Spice.MEDIUM),
        Roast("Your triage is tight; your calendar is a code blue.", Category.VET_TECH, Spice.MEDIUM),
        Roast("Great at enrichment; try enriching your rest.", Category.VET_TECH, Spice.MEDIUM),
        Roast("Fur-midable at chaos management — results pending.", Category.VET_TECH, Spice.MEDIUM),
        Roast("You’ve handled literal shit with more grace than your last relationship.", Category.VET_TECH, Spice.HELLFIRE),
        Roast("Dogs respect you more than your reflection does.", Category.VET_TECH, Spice.HELLFIRE),
        Roast("You sterilize animals; chaos still breeds around you — spay your schedule.", Category.VET_TECH, Spice.HELLFIRE),
        Roast("The guinea pigs gossip about your life choices. Loudly.", Category.VET_TECH, Spice.HELLFIRE),
        Roast("Bedside manner’s great — for creatures that can’t talk back.", Category.VET_TECH, Spice.HELLFIRE),
        Roast("You label syringes better than you label emotions.", Category.VET_TECH, Spice.HELLFIRE),
        Roast("Your empathy’s gold; your boundaries are paper.", Category.VET_TECH, Spice.HELLFIRE),
        Roast("You’re paws‑itively wrecked — catastrophe in scrubs.", Category.VET_TECH, Spice.HELLFIRE),
        Roast("Cat hair clings harder than your bad habits.", Category.VET_TECH, Spice.HELLFIRE),
        Roast("You triaged ten patients and still flatlined your day.", Category.VET_TECH, Spice.HELLFIRE),
        Roast("Flea’d your responsibilities again, didn’t you?", Category.VET_TECH, Spice.HELLFIRE),
        Roast("Mutt‑ivation low, mess high. Sit. Stay. Fix it.", Category.VET_TECH, Spice.HELLFIRE),
        Roast("You’re furiously busy and suspiciously unproductive.", Category.VET_TECH, Spice.HELLFIRE),
        Roast("The lizards think you’re cold‑blooded about mornings, and they’re right.", Category.VET_TECH, Spice.HELLFIRE),
        Roast("Claws for concern: you’re one missed lunch away from feral.", Category.VET_TECH, Spice.HELLFIRE),    )

    /**
     * Get a random roast with optional filters.
     */
    fun randomRoast(
        category: Category? = null,
        spice: Spice? = null,
        seed: Long? = null,
        avoidRecent: Boolean = true
    ): Roast {
        val pool = ROASTS.withIndex().filter { (_, r) ->
            (category == null || r.category == category) &&
                    (spice == null || r.spice == spice)
        }
        require(pool.isNotEmpty()) { "No roasts match the given filters" }

        val rng = if (seed != null) Random(seed) else Random.Default

        if (avoidRecent) {
            repeat(100) {
                val pick = pool[rng.nextInt(pool.size)]
                if (pick.index !in recent) {
                    pushRecent(pick.index)
                    return pick.value
                }
            }
        }
        val pick = pool[rng.nextInt(pool.size)]
        pushRecent(pick.index)
        return pick.value
    }

    fun randomRoastOfTheDay(
        category: Category? = null,
        spice: Spice? = null,
        daySeed: Long
    ): Roast = randomRoast(category, spice, seed = daySeed, avoidRecent = false)

    private fun pushRecent(index: Int) {
        recent.addLast(index)
        while (recent.size > recentWindow) {
            recent.removeFirst()
        }
    }
}