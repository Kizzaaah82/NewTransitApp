package com.kiz.transitapp.ui.components

/*
 * RoastManager.kt
 * Generated for Kieran's personal transit app — savage, clever, non-repeating roasts.
 *
 * Usage:
 *   val roast = RoastManager.randomRoast()
 *   val transitRoast = RoastManager.randomRoast(category = RoastManager.Category.TRANSIT)
 *
 * Notes:
 * - No hate speech, no slurs. Mean but smart.
 * - Non-repeating window avoids the last N outputs (configurable).
 * - Deterministic option via seed for "roast of the day".
 */

import kotlin.random.Random
import kotlin.math.max

data class Roast(
    val text: String,
    val category: RoastManager.Category,
    val spice: RoastManager.Spice
)

object RoastManager {

    // Categories cover your app's context and your vibe
    enum class Category { TRANSIT, WEATHER, TIME_OF_DAY, DEVICE, PERSONAL, PHILOSOPHY, VET_TECH }

    // Spice scale. You asked for all levels, so... enjoy.
    enum class Spice { MILD, MEDIUM, HELLFIRE }

    // How many recent roasts to avoid repeating. Tune as you like.
    var recentWindow: Int = 30
        set(value) { field = max(5, value) }

    // Internal ring buffer of recent indices (not persisted by default).
    private val recent = ArrayDeque<Int>()

    // Core library — hand-curated chaos.
    val ROASTS: List<Roast> = listOf(
        Roast("Your bus is delayed again. Don’t worry, so is your personal growth.", Category.TRANSIT, Spice.MEDIUM),
        Roast("Congrats! You’ve waited longer for this bus than you did for your last life decision.", Category.TRANSIT, Spice.MEDIUM),
        Roast("This stop’s got more cancellations than your social plans.", Category.TRANSIT, Spice.MEDIUM),
        Roast("If patience is a virtue, you’re basically a monk by now.", Category.TRANSIT, Spice.MILD),
        Roast("Maybe the bus is hiding because it saw your outfit.", Category.TRANSIT, Spice.MEDIUM),
        Roast("You and the bus have one thing in common: neither of you is going anywhere fast.", Category.TRANSIT, Spice.MEDIUM),
        Roast("Bus ETA: unknown. Much like your future.", Category.TRANSIT, Spice.HELLFIRE),
        Roast("You’ve stared at this stop so long, you should start paying rent.", Category.TRANSIT, Spice.MEDIUM),
        Roast("Transit gods saw your face and added ten minutes.", Category.TRANSIT, Spice.MEDIUM),
        Roast("This route’s less reliable than your sense of direction.", Category.TRANSIT, Spice.HELLFIRE),
        Roast("Even the driver’s avoiding this route like your responsibilities.", Category.TRANSIT, Spice.MEDIUM),
        Roast("You’ve spent more time waiting here than your parents spent believing in you.", Category.TRANSIT, Spice.HELLFIRE),
        Roast("The bus is making ‘a little detour,’ also known as ‘your life story.’", Category.TRANSIT, Spice.MEDIUM),
        Roast("Real-time updates say: HAHAHA. That’s the whole update.", Category.TRANSIT, Spice.MEDIUM),
        Roast("Delay so long you could knit a new personality.", Category.TRANSIT, Spice.HELLFIRE),
        Roast("Route change: the universe decided you needed character development.", Category.TRANSIT, Spice.MILD),
        Roast("You track buses like a cryptid hunter with worse results.", Category.TRANSIT, Spice.MEDIUM),
        Roast("You missed it by thirty seconds—classic you speedrun.", Category.TRANSIT, Spice.MEDIUM),
        Roast("The bus ghosted you. Again. Take the hint.", Category.TRANSIT, Spice.HELLFIRE),
        Roast("Standing at this stop is your new part-time job. Pay: exposure to the elements.", Category.TRANSIT, Spice.MEDIUM),
        Roast("Next arrival: 'eventually.' Settle in, philosopher.", Category.TRANSIT, Spice.MILD),
        Roast("You sprinted and still lost. Athleticism by vibes.", Category.TRANSIT, Spice.MEDIUM),
        Roast("This stop is basically a museum exhibit titled ‘Hope in Captivity.’", Category.TRANSIT, Spice.HELLFIRE),
        Roast("Headsign says ‘Not In Service,’ just like your motivation.", Category.TRANSIT, Spice.HELLFIRE),
        Roast("If maps were honest, this station would be labeled ‘Regret.’", Category.TRANSIT, Spice.MEDIUM),
        Roast("Missed connection: not just dating apps anymore.", Category.TRANSIT, Spice.MEDIUM),
        Roast("You ride this line so much you should file for residency.", Category.TRANSIT, Spice.MILD),
        Roast("Cancellations rolling in like your excuses.", Category.TRANSIT, Spice.MEDIUM),
        Roast("Detours ahead—because heaven forbid anything be direct in your life.", Category.TRANSIT, Spice.HELLFIRE),
        Roast("You and punctuality are on different routes.", Category.TRANSIT, Spice.MEDIUM),
        Roast("Even the timetable is gaslighting you.", Category.TRANSIT, Spice.HELLFIRE),
        Roast("Platform shuffle: it’s cardio disguised as logistics.", Category.TRANSIT, Spice.MILD),
        Roast("If optimism were a transfer, you just missed it.", Category.TRANSIT, Spice.MEDIUM),
        Roast("Express service? Not for you.", Category.TRANSIT, Spice.HELLFIRE),
        Roast("Your tap failed. So did your plan.", Category.TRANSIT, Spice.MEDIUM),
        Roast("This vehicle is short-turning, like your attention span.", Category.TRANSIT, Spice.MEDIUM),
        Roast("It’s raining again, which perfectly matches your vibe.", Category.WEATHER, Spice.MEDIUM),
        Roast("Sunny today? Weird. You sure the universe didn’t make a mistake?", Category.WEATHER, Spice.MEDIUM),
        Roast("Cold outside, but at least your personality is consistent.", Category.WEATHER, Spice.MEDIUM),
        Roast("Wind advisory: don’t get blown away… by your own mediocrity.", Category.WEATHER, Spice.MEDIUM),
        Roast("Feels like −10°C. That’s also your charisma score.", Category.WEATHER, Spice.HELLFIRE),
        Roast("Humidity’s up, confidence’s down.", Category.WEATHER, Spice.MEDIUM),
        Roast("UV index high; maybe let it burn off some bad decisions.", Category.WEATHER, Spice.MEDIUM),
        Roast("Nice day to pretend you’re thriving.", Category.WEATHER, Spice.MILD),
        Roast("Sunshine doesn’t fix your vibe, but points for trying.", Category.WEATHER, Spice.MILD),
        Roast("Cloudy with a chance of you blaming the weather.", Category.WEATHER, Spice.MEDIUM),
        Roast("There’s a breeze. Finally, something around you has movement.", Category.WEATHER, Spice.HELLFIRE),
        Roast("Fog advisory: perfect cover for your clarity.", Category.WEATHER, Spice.MEDIUM),
        Roast("Thunderstorm incoming, like your inbox.", Category.WEATHER, Spice.MEDIUM),
        Roast("Pollen count: brutal. You, however, stay remarkably uninspiring.", Category.WEATHER, Spice.HELLFIRE),
        Roast("Heat wave: the only thing hotter is your streak of questionable choices.", Category.WEATHER, Spice.MEDIUM),
        Roast("Freezing rain: nature’s way of telling you to stay inside and rethink everything.", Category.WEATHER, Spice.MEDIUM),
        Roast("Rainbow spotted. It left after meeting your energy.", Category.WEATHER, Spice.HELLFIRE),
        Roast("Barometer falling, standards following.", Category.WEATHER, Spice.MEDIUM),
        Roast("Nice breeze for airing out your delusions.", Category.WEATHER, Spice.HELLFIRE),
        Roast("Snow day: your productivity has already called in sick.", Category.WEATHER, Spice.MEDIUM),
        Roast("Black ice out there—just like your sense of humor.", Category.WEATHER, Spice.MEDIUM),
        Roast("Drizzle: technically weather, technically effort.", Category.WEATHER, Spice.MILD),
        Roast("Drought season: hydrate yourself and your personality.", Category.WEATHER, Spice.MEDIUM),
        Roast("Lightning nearby. Don’t worry, you’re not a conduct—wait, never mind.", Category.WEATHER, Spice.HELLFIRE),
        Roast("Air quality: questionable, like your take on everything.", Category.WEATHER, Spice.MEDIUM),
        Roast("Wind chill means you won’t be the coldest thing outside.", Category.WEATHER, Spice.HELLFIRE),
        Roast("It’s muggy. Your mood is, too.", Category.WEATHER, Spice.MEDIUM),
        Roast("Pressure rising: anxiety doing its best weather impression.", Category.WEATHER, Spice.MEDIUM),
        Roast("First snow: Instagram filters won’t save your day.", Category.WEATHER, Spice.MEDIUM),
        Roast("Sunset’s stunning. You’re indoors arguing with a schedule.", Category.WEATHER, Spice.MEDIUM),
        Roast("Rainbow? Nah, that’s your screen glare.", Category.WEATHER, Spice.MILD),
        Roast("Tornado watch: perfect metaphor for your decision-making.", Category.WEATHER, Spice.HELLFIRE),
        Roast("Cold snap: your empathy called—says it relates.", Category.WEATHER, Spice.MEDIUM),
        Roast("Heat index: ‘sweating in denial.’", Category.WEATHER, Spice.MEDIUM),
        Roast("UV 0. Like your output today.", Category.WEATHER, Spice.HELLFIRE),
        Roast("Good morning, champion of mediocrity.", Category.TIME_OF_DAY, Spice.MILD),
        Roast("Lunch already? Time flies when you’re avoiding work.", Category.TIME_OF_DAY, Spice.MEDIUM),
        Roast("It’s 3 p.m.—that weird hour where dreams go to die.", Category.TIME_OF_DAY, Spice.MEDIUM),
        Roast("Evening again. Another day, another participation ribbon in adulthood.", Category.TIME_OF_DAY, Spice.MEDIUM),
        Roast("It’s midnight, you insomniac raccoon. Go touch some REM sleep.", Category.TIME_OF_DAY, Spice.HELLFIRE),
        Roast("You’ve been awake for 18 hours and achieved roughly one thought.", Category.TIME_OF_DAY, Spice.HELLFIRE),
        Roast("You greet dawn the way people greet debt collectors.", Category.TIME_OF_DAY, Spice.HELLFIRE),
        Roast("Morning routine: survive. Barely.", Category.TIME_OF_DAY, Spice.MILD),
        Roast("Afternoon slump detected. Your ambition is buffering.", Category.TIME_OF_DAY, Spice.MEDIUM),
        Roast("Golden hour won’t make those choices look better.", Category.TIME_OF_DAY, Spice.MEDIUM),
        Roast("Late night coding: bold of you to fight bugs with fewer brain cells.", Category.TIME_OF_DAY, Spice.MEDIUM),
        Roast("Breakfast of champions… was not whatever that was.", Category.TIME_OF_DAY, Spice.MILD),
        Roast("Your bedtime is a rumor.", Category.TIME_OF_DAY, Spice.MEDIUM),
        Roast("You snoozed your alarm and your standards.", Category.TIME_OF_DAY, Spice.MEDIUM),
        Roast("Happy hour: the only productivity hack that ever worked for you.", Category.TIME_OF_DAY, Spice.MEDIUM),
        Roast("Sunrise is judging you in HDR.", Category.TIME_OF_DAY, Spice.HELLFIRE),
        Roast("Noon: halfway through the day, one-tenth through your tasks.", Category.TIME_OF_DAY, Spice.MEDIUM),
        Roast("Midnight thoughts again? Put them back. They’re feral.", Category.TIME_OF_DAY, Spice.HELLFIRE),
        Roast("You’re on a first-name basis with 2 a.m. and regret.", Category.TIME_OF_DAY, Spice.HELLFIRE),
        Roast("Good night. For everyone else. You’ll be up scrolling.", Category.TIME_OF_DAY, Spice.MEDIUM),
        Roast("Saturday energy: chaotic neutral. Yours: chaotic tired.", Category.TIME_OF_DAY, Spice.MILD),
        Roast("Sunday scaries called. They brought friends.", Category.TIME_OF_DAY, Spice.MEDIUM),
        Roast("Weekday mood: ‘we’ll try again tomorrow.’", Category.TIME_OF_DAY, Spice.MILD),
        Roast("You treat calendars like optional fiction.", Category.TIME_OF_DAY, Spice.MEDIUM),
        Roast("You earned a break from all that underachieving.", Category.TIME_OF_DAY, Spice.HELLFIRE),
        Roast("Tea time: spill it—preferably not on your keyboard again.", Category.TIME_OF_DAY, Spice.MILD),
        Roast("You’re late to today like it’s a meeting you could’ve been an email.", Category.TIME_OF_DAY, Spice.MEDIUM),
        Roast("It’s prime procrastination o’clock.", Category.TIME_OF_DAY, Spice.MEDIUM),
        Roast("Sunset says stop; you heard ‘start panicking.’", Category.TIME_OF_DAY, Spice.MEDIUM),
        Roast("It’s tomorrow already. Your plan is still yesterday.", Category.TIME_OF_DAY, Spice.HELLFIRE),
        Roast("Dawn patrol? More like yawn patrol.", Category.TIME_OF_DAY, Spice.MILD),
        Roast("Brunch is just breakfast with lies.", Category.TIME_OF_DAY, Spice.MEDIUM),
        Roast("You and time management are estranged parents.", Category.TIME_OF_DAY, Spice.HELLFIRE),
        Roast("Alarm off, delusion on.", Category.TIME_OF_DAY, Spice.MEDIUM),
        Roast("Midday: the sun is up, your standards aren’t.", Category.TIME_OF_DAY, Spice.HELLFIRE),
        Roast("Battery low—just like your standards.", Category.DEVICE, Spice.MILD),
        Roast("Signal weak, much like your arguments.", Category.DEVICE, Spice.MILD),
        Roast("Your phone’s hot enough to fry the last shred of your dignity.", Category.DEVICE, Spice.MEDIUM),
        Roast("Storage full? Maybe delete some bad decisions while you’re at it.", Category.DEVICE, Spice.MEDIUM),
        Roast("Battery at 1%. Fitting. You’re the human low-battery warning.", Category.DEVICE, Spice.HELLFIRE),
        Roast("Your phone’s faster than you’ll ever be.", Category.DEVICE, Spice.HELLFIRE),
        Roast("System update required: personality patch pending.", Category.DEVICE, Spice.HELLFIRE),
        Roast("Bluetooth connected: finally, something in your life paired successfully.", Category.DEVICE, Spice.MEDIUM),
        Roast("Wi‑Fi dropped—like your attention span.", Category.DEVICE, Spice.MEDIUM),
        Roast("Airplane mode? Bold for someone who never takes off.", Category.DEVICE, Spice.HELLFIRE),
        Roast("Your notifications are just tiny cries for help.", Category.DEVICE, Spice.MEDIUM),
        Roast("Screen time today: impressive if failure were a metric.", Category.DEVICE, Spice.MEDIUM),
        Roast("Auto-rotate does more turning than your career.", Category.DEVICE, Spice.HELLFIRE),
        Roast("Low storage: you hoard screenshots like regrets.", Category.DEVICE, Spice.MEDIUM),
        Roast("Fingerprint failed—your phone is trying to protect itself from you.", Category.DEVICE, Spice.MEDIUM),
        Roast("Camera opened to front-facing. Brave. Ill-advised.", Category.DEVICE, Spice.HELLFIRE),
        Roast("Your charger cable is healthier than your habits.", Category.DEVICE, Spice.MEDIUM),
        Roast("NFC error: even your payments are commitment-averse.", Category.DEVICE, Spice.MEDIUM),
        Roast("Notifications silenced. Peace at last—for everyone else.", Category.DEVICE, Spice.MILD),
        Roast("Auto-correct can’t save what you’re typing.", Category.DEVICE, Spice.MEDIUM),
        Roast("This app crashed just to avoid your plans.", Category.DEVICE, Spice.HELLFIRE),
        Roast("Your widgets are useful; can’t say the same for you today.", Category.DEVICE, Spice.HELLFIRE),
        Roast("Brightness: max. Insight: min.", Category.DEVICE, Spice.MEDIUM),
        Roast("Vibration on. That’s your phone trembling in fear.", Category.DEVICE, Spice.MEDIUM),
        Roast("You run background tasks called ‘procrastination.’", Category.DEVICE, Spice.MEDIUM),
        Roast("Clipboard empty, like your alibi.", Category.DEVICE, Spice.MILD),
        Roast("Battery saver won’t help your life choices.", Category.DEVICE, Spice.MEDIUM),
        Roast("Face unlock failed. Mood unlocked: embarrassment.", Category.DEVICE, Spice.HELLFIRE),
        Roast("You’ve got more tabs than thoughts.", Category.DEVICE, Spice.HELLFIRE),
        Roast("Charging slowly, just like your growth.", Category.DEVICE, Spice.MEDIUM),
        Roast("Hotspot on—congrats, you finally provide something of value.", Category.DEVICE, Spice.MILD),
        Roast("Permissions denied. Boundaries? You should try them.", Category.DEVICE, Spice.MEDIUM),
        Roast("Headphones connected; at least your ears made a connection.", Category.DEVICE, Spice.MILD),
        Roast("Gyroscope spin: that’s your moral compass.", Category.DEVICE, Spice.HELLFIRE),
        Roast("Keyboard lag: even your letters can’t be bothered.", Category.DEVICE, Spice.MEDIUM),
        Roast("You’re doing fine, if ‘fine’ means coasting on chaos.", Category.PERSONAL, Spice.MILD),
        Roast("Your best feature is ‘present by default.’", Category.PERSONAL, Spice.MILD),
        Roast("You’re proof evolution experiments sometimes need debugging.", Category.PERSONAL, Spice.MEDIUM),
        Roast("If mediocrity were an Olympic sport, you’d finally win something.", Category.PERSONAL, Spice.MEDIUM),
        Roast("You look like the before photo of a caffeine intervention.", Category.PERSONAL, Spice.HELLFIRE),
        Roast("You have the confidence of a Wi‑Fi signal in a bunker.", Category.PERSONAL, Spice.HELLFIRE),
        Roast("Your aura screams ‘left my potential in the drafts.’", Category.PERSONAL, Spice.HELLFIRE),
        Roast("Every time you speak, autocorrect gives up.", Category.PERSONAL, Spice.HELLFIRE),
        Roast("You’re the plot twist nobody asked for.", Category.PERSONAL, Spice.HELLFIRE),
        Roast("You radiate ‘forgot my password again’ energy.", Category.PERSONAL, Spice.MEDIUM),
        Roast("You’re the human version of a buffering wheel.", Category.PERSONAL, Spice.MEDIUM),
        Roast("Somewhere out there, someone’s proud of you. Probably by mistake.", Category.PERSONAL, Spice.MEDIUM),
        Roast("You bring big ‘update later’ energy to everything.", Category.PERSONAL, Spice.MEDIUM),
        Roast("Your potential called. It hung up.", Category.PERSONAL, Spice.HELLFIRE),
        Roast("You have main-character energy—of a printer tutorial.", Category.PERSONAL, Spice.MEDIUM),
        Roast("You’re a limited-time offer that never ends.", Category.PERSONAL, Spice.MILD),
        Roast("Confidence level: autocorrect when it’s wrong.", Category.PERSONAL, Spice.MEDIUM),
        Roast("You’re a group project in human form.", Category.PERSONAL, Spice.HELLFIRE),
        Roast("You’re a vibe, and that vibe is maintenance mode.", Category.PERSONAL, Spice.MEDIUM),
        Roast("You could trip over a cordless phone.", Category.PERSONAL, Spice.HELLFIRE),
        Roast("Personality patch notes: fixed nothing, added drama.", Category.PERSONAL, Spice.HELLFIRE),
        Roast("You look tired in 4K.", Category.PERSONAL, Spice.HELLFIRE),
        Roast("Your opinions come pre-debunked.", Category.PERSONAL, Spice.HELLFIRE),
        Roast("You’re a recurring captcha that nobody can solve.", Category.PERSONAL, Spice.MEDIUM),
        Roast("You’re not the main plot, you’re the loading screen.", Category.PERSONAL, Spice.HELLFIRE),
        Roast("Ambition not found. Try plugging it in.", Category.PERSONAL, Spice.MEDIUM),
        Roast("You’re the sequel no one greenlit.", Category.PERSONAL, Spice.HELLFIRE),
        Roast("You have the range—of a potato clock.", Category.PERSONAL, Spice.MEDIUM),
        Roast("You’re chaos with patchy Wi‑Fi.", Category.PERSONAL, Spice.MEDIUM),
        Roast("You’re a pop-up ad for naps.", Category.PERSONAL, Spice.MILD),
        Roast("You’re great at multitasking: avoiding tasks while generating new ones.", Category.PERSONAL, Spice.MEDIUM),
        Roast("You’re the human shrug emoji.", Category.PERSONAL, Spice.MILD),
        Roast("Your daily routine is a speedrun of bad timing.", Category.PERSONAL, Spice.HELLFIRE),
        Roast("You’re a spoiler alert for disappointment.", Category.PERSONAL, Spice.HELLFIRE),
        Roast("Your energy screams ‘left my resolve in the Uber.’", Category.PERSONAL, Spice.MEDIUM),
        Roast("The universe is vast, yet you still manage to make it about you.", Category.PHILOSOPHY, Spice.MILD),
        Roast("Existence called—it wants a refund.", Category.PHILOSOPHY, Spice.MILD),
        Roast("You’re an unskippable ad in the simulation.", Category.PHILOSOPHY, Spice.MEDIUM),
        Roast("Entropy increases, and so does your unread email count.", Category.PHILOSOPHY, Spice.MEDIUM),
        Roast("You stare into the void, and it sighs.", Category.PHILOSOPHY, Spice.HELLFIRE),
        Roast("Cosmic dust has more direction than your five‑year plan.", Category.PHILOSOPHY, Spice.HELLFIRE),
        Roast("You’re living proof that consciousness was a design flaw.", Category.PHILOSOPHY, Spice.HELLFIRE),
        Roast("Free will? You can’t even choose a bedtime.", Category.PHILOSOPHY, Spice.MEDIUM),
        Roast("Time is a flat circle; you’re stuck on the dull side.", Category.PHILOSOPHY, Spice.MEDIUM),
        Roast("Schrödinger’s motivation: both there and missing until observed.", Category.PHILOSOPHY, Spice.MEDIUM),
        Roast("Cogito ergo… never mind.", Category.PHILOSOPHY, Spice.MILD),
        Roast("Your purpose isn’t lost; it just ghosted you.", Category.PHILOSOPHY, Spice.HELLFIRE),
        Roast("You’re a paradox: loudly average.", Category.PHILOSOPHY, Spice.MEDIUM),
        Roast("Memento mori, but first memento coffee.", Category.PHILOSOPHY, Spice.MILD),
        Roast("You seek meaning like it’s on the next bus—spoiler: it’s not.", Category.PHILOSOPHY, Spice.MEDIUM),
        Roast("You’re the punchline in the cosmic joke.", Category.PHILOSOPHY, Spice.HELLFIRE),
        Roast("Moral compass spinning—try recalibrating with sleep.", Category.PHILOSOPHY, Spice.MEDIUM),
        Roast("Vanity of vanities; your to-do list remains.", Category.PHILOSOPHY, Spice.MILD),
        Roast("The abyss unfollowed you.", Category.PHILOSOPHY, Spice.HELLFIRE),
        Roast("You’re a glitch in the narrative without the fun powers.", Category.PHILOSOPHY, Spice.MEDIUM),
        Roast("Sisyphus saw your schedule and said ‘rough.’", Category.PHILOSOPHY, Spice.HELLFIRE),
        Roast("You’re a thought experiment gone rogue.", Category.PHILOSOPHY, Spice.MEDIUM),
        Roast("You meditate like a buffering gif.", Category.PHILOSOPHY, Spice.MILD),
        Roast("Destiny left you on read.", Category.PHILOSOPHY, Spice.HELLFIRE),
        Roast("Meaning of life: 42. Meaning of you: TBD.", Category.PHILOSOPHY, Spice.MILD),
        Roast("Karma’s backlog includes your tickets.", Category.PHILOSOPHY, Spice.MEDIUM),
        Roast("You venerate coffee, not wisdom. Fair.", Category.PHILOSOPHY, Spice.MILD),
        Roast("You’re the heat death of enthusiasm.", Category.PHILOSOPHY, Spice.HELLFIRE),
        Roast("Nihilism is a phase; your mess is a lifestyle.", Category.PHILOSOPHY, Spice.MEDIUM),
        Roast("You’re a knock‑knock joke with no punchline.", Category.PHILOSOPHY, Spice.HELLFIRE),
        Roast("You keep asking ‘why’ like it owes you money.", Category.PHILOSOPHY, Spice.MEDIUM),
        Roast("You’re the ship of Theseus, but every plank is procrastination.", Category.PHILOSOPHY, Spice.MEDIUM),
        Roast("You pray to alarms and snooze the answers.", Category.PHILOSOPHY, Spice.MILD),
        Roast("You’re an existential sneeze: brief, disruptive, forgotten.", Category.PHILOSOPHY, Spice.HELLFIRE),
        Roast("You talk to pets better than to people—correct decision.", Category.VET_TECH, Spice.MILD),
        Roast("Another day, another fur‑covered battle.", Category.VET_TECH, Spice.MILD),
        Roast("Even the cats have boundaries you could learn from.", Category.VET_TECH, Spice.MEDIUM),
        Roast("Your scrubs are cleaner than your coping mechanisms.", Category.VET_TECH, Spice.MEDIUM),
        Roast("You’ve handled literal crap with more grace than your last relationship.", Category.VET_TECH, Spice.HELLFIRE),
        Roast("The dogs respect you more than your reflection does.", Category.VET_TECH, Spice.HELLFIRE),
        Roast("You sterilize animals, yet chaos still breeds around you.", Category.VET_TECH, Spice.HELLFIRE),
        Roast("The guinea pigs gossip about your life choices.", Category.VET_TECH, Spice.HELLFIRE),
        Roast("Your bedside manner’s great—for creatures that can’t talk back.", Category.VET_TECH, Spice.HELLFIRE),
        Roast("You diagnose a cat faster than you diagnose your burnout.", Category.VET_TECH, Spice.MEDIUM),
        Roast("You wield clippers like a therapist with scissors.", Category.VET_TECH, Spice.MILD),
        Roast("Your treat pouch has better diplomacy than you.", Category.VET_TECH, Spice.MEDIUM),
        Roast("You’ve been scratched by friendlier personalities.", Category.VET_TECH, Spice.MEDIUM),
        Roast("You can read a dog’s body language, but miss your own red flags.", Category.VET_TECH, Spice.HELLFIRE),
        Roast("You smell like chlorhexidine and exhaustion.", Category.VET_TECH, Spice.MEDIUM),
        Roast("Even the lizards think you’re cold‑blooded about mornings.", Category.VET_TECH, Spice.MEDIUM),
        Roast("You clean kennels with the precision you avoid in budgeting.", Category.VET_TECH, Spice.MEDIUM),
        Roast("You wear PPE like armor against your feelings.", Category.VET_TECH, Spice.HELLFIRE),
        Roast("You say ‘good kitty’ the way others say ‘please don’t quit on me.’", Category.VET_TECH, Spice.MILD),
        Roast("You label syringes better than you label emotions.", Category.VET_TECH, Spice.HELLFIRE),
        Roast("Your patients bite less than your sense of humor.", Category.VET_TECH, Spice.MILD),
        Roast("You’ve mastered the towel burrito; now wrap up your excuses.", Category.VET_TECH, Spice.MEDIUM),
        Roast("You nail vein shots but miss good sleep consistently.", Category.VET_TECH, Spice.MEDIUM),
        Roast("You chart like a poet with trauma.", Category.VET_TECH, Spice.MEDIUM),
        Roast("You’ve got more cones than achievements.", Category.VET_TECH, Spice.HELLFIRE),
        Roast("Even the guinea pigs are side‑eyeing your time management.", Category.VET_TECH, Spice.MEDIUM),
        Roast("You give better aftercare instructions than you give yourself grace.", Category.VET_TECH, Spice.MEDIUM),
        Roast("You’re powered by coffee, treats, and denial.", Category.VET_TECH, Spice.MILD),
        Roast("You wash surgical tools and your hands of responsibility.", Category.VET_TECH, Spice.HELLFIRE),
        Roast("You’re enrichment for animals and chaos for humans.", Category.VET_TECH, Spice.MEDIUM),
        Roast("You can trim nails; can you trim the drama?", Category.VET_TECH, Spice.MEDIUM),
        Roast("Your kennel cards are neat; your life, less so.", Category.VET_TECH, Spice.HELLFIRE),
        Roast("You’re the calming pheromone everyone else needs.", Category.VET_TECH, Spice.MILD),
        Roast("You pour peroxide on wounds and gasoline on schedules.", Category.VET_TECH, Spice.HELLFIRE),
        Roast("Your scrub pockets carry five tools and zero boundaries.", Category.VET_TECH, Spice.MEDIUM),    )

    /**
     * Get a random roast with optional filters.
     * - category: restrict to a category
     * - spice: restrict to a spice level
     * - seed: pass a value for deterministic selection (e.g., LocalDate.now().toEpochDay())
     * - avoidRecent: use the recent-window filter to prevent repeats
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
        if (pool.isEmpty()) error("No roasts match the given filters")

        val rng = if (seed != null) Random(seed) else Random.Default

        // If avoiding recent, try up to 100 picks to find a non-recent index.
        if (avoidRecent) {
            repeat(100) {
                val pick = pool[rng.nextInt(pool.size)]
                if (pick.index !in recent) {
                    pushRecent(pick.index)
                    return pick.value
                }
            }
            // Fallback: accept a recent one if we somehow failed
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