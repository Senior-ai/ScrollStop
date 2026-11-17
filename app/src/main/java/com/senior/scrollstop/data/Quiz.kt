package com.senior.scrollstop.data

import kotlin.random.Random

/** A multiple-choice question. [options] holds exactly one correct answer, at [answerIndex]. */
data class QuizQuestion(val prompt: String, val options: List<String>, val answerIndex: Int) {
    val answer: String get() = options[answerIndex]
}

/** A question-and-answer pair the user wrote themselves. */
data class MyCard(val question: String, val answer: String)

enum class QuizTopic(val key: String, val label: String, val description: String) {
    MATH("math", "Mental maths", "Quick sums, never the same twice"),
    CAPITALS("capitals", "World capitals", "Which city is the capital of…"),
    VOCABULARY("vocabulary", "English vocabulary", "What does this word mean?"),
    SCIENCE("science", "Science basics", "Planets, cells, elements and more"),
    MY_CARDS("my_cards", "My decks", "Your own questions, imported or written, for anything you study");

    companion object {
        fun byKey(key: String?): QuizTopic = entries.firstOrNull { it.key == key } ?: MATH
    }
}

/**
 * Offline question source: ScrollStop has no internet access, so every topic is bundled or
 * generated. Pure Kotlin so it can be unit tested.
 */
object QuizBank {

    /** Own cards need this many, so each question has three wrong answers to choose from. */
    const val MIN_CARDS = 4

    fun next(topic: QuizTopic, random: Random, cards: List<MyCard> = emptyList()): QuizQuestion = when (topic) {
        QuizTopic.MATH -> math(random)
        QuizTopic.CAPITALS -> fromPairs(CAPITALS, random) { "What's the capital of $it?" }
        QuizTopic.VOCABULARY -> fromPairs(VOCABULARY, random) { "What does “$it” mean?" }
        QuizTopic.SCIENCE -> SCIENCE.random(random).shuffled(random)
        QuizTopic.MY_CARDS ->
            if (cards.distinctBy { it.answer.lowercase() }.size >= MIN_CARDS) {
                fromPairs(cards.map { it.question to it.answer }, random) { it }
            } else {
                math(random)
            }
    }

    private fun math(random: Random): QuizQuestion {
        val (prompt, answer) = when (random.nextInt(3)) {
            0 -> random.nextInt(12, 90).let { a -> random.nextInt(12, 90).let { b -> "$a + $b" to a + b } }
            1 -> random.nextInt(40, 150).let { a -> random.nextInt(11, a - 5).let { b -> "$a − $b" to a - b } }
            else -> random.nextInt(3, 13).let { a -> random.nextInt(4, 20).let { b -> "$a × $b" to a * b } }
        }
        val wrong = linkedSetOf<Int>()
        val nearMisses = listOf(1, -1, 2, -2, 10, -10, 11, -9).shuffled(random)
        for (delta in nearMisses) {
            val candidate = answer + delta
            if (candidate > 0 && candidate != answer) wrong += candidate
            if (wrong.size == 3) break
        }
        return build("What's $prompt?", answer.toString(), wrong.map { it.toString() }, random)
    }

    private fun fromPairs(pairs: List<Pair<String, String>>, random: Random, prompt: (String) -> String): QuizQuestion {
        val (question, answer) = pairs.random(random)
        val wrong = pairs.map { it.second }
            .filter { !it.equals(answer, ignoreCase = true) }
            .distinctBy { it.lowercase() }
            .shuffled(random)
            .take(3)
        return build(prompt(question), answer, wrong, random)
    }

    private fun build(prompt: String, answer: String, wrong: List<String>, random: Random): QuizQuestion {
        val options = (wrong + answer).shuffled(random)
        return QuizQuestion(prompt, options, options.indexOf(answer))
    }

    private fun QuizQuestion.shuffled(random: Random): QuizQuestion = build(prompt, answer, options - answer, random)

    private fun q(prompt: String, answer: String, vararg wrong: String) =
        QuizQuestion(prompt, listOf(answer) + wrong, 0)

    // Capitals that are disputed, split or in transition are left out on purpose.
    internal val CAPITALS = listOf(
        "France" to "Paris", "Germany" to "Berlin", "Italy" to "Rome", "Spain" to "Madrid",
        "Portugal" to "Lisbon", "the Netherlands" to "Amsterdam", "Belgium" to "Brussels",
        "Switzerland" to "Bern", "Austria" to "Vienna", "Poland" to "Warsaw", "Czechia" to "Prague",
        "Hungary" to "Budapest", "Greece" to "Athens", "Sweden" to "Stockholm", "Norway" to "Oslo",
        "Denmark" to "Copenhagen", "Finland" to "Helsinki", "Ireland" to "Dublin",
        "Iceland" to "Reykjavík", "Romania" to "Bucharest", "Bulgaria" to "Sofia",
        "Serbia" to "Belgrade", "Croatia" to "Zagreb", "Ukraine" to "Kyiv", "Turkey" to "Ankara",
        "Slovakia" to "Bratislava", "Slovenia" to "Ljubljana", "Estonia" to "Tallinn",
        "Latvia" to "Riga", "Lithuania" to "Vilnius", "Albania" to "Tirana", "Russia" to "Moscow",
        "Georgia" to "Tbilisi", "Armenia" to "Yerevan", "Azerbaijan" to "Baku",
        "Kazakhstan" to "Astana", "Mongolia" to "Ulaanbaatar", "Egypt" to "Cairo",
        "Morocco" to "Rabat", "Tunisia" to "Tunis", "Algeria" to "Algiers", "Kenya" to "Nairobi",
        "Nigeria" to "Abuja", "Ethiopia" to "Addis Ababa", "Ghana" to "Accra", "Senegal" to "Dakar",
        "Uganda" to "Kampala", "Tanzania" to "Dodoma", "Zimbabwe" to "Harare", "Japan" to "Tokyo",
        "China" to "Beijing", "South Korea" to "Seoul", "India" to "New Delhi",
        "Pakistan" to "Islamabad", "Bangladesh" to "Dhaka", "Nepal" to "Kathmandu",
        "Thailand" to "Bangkok", "Vietnam" to "Hanoi", "the Philippines" to "Manila",
        "Malaysia" to "Kuala Lumpur", "Myanmar" to "Naypyidaw", "Afghanistan" to "Kabul",
        "Iran" to "Tehran", "Iraq" to "Baghdad", "Jordan" to "Amman", "Lebanon" to "Beirut",
        "Saudi Arabia" to "Riyadh", "the United Arab Emirates" to "Abu Dhabi", "Qatar" to "Doha",
        "Australia" to "Canberra", "New Zealand" to "Wellington", "Canada" to "Ottawa",
        "the United States" to "Washington, D.C.", "Mexico" to "Mexico City", "Cuba" to "Havana",
        "Jamaica" to "Kingston", "Brazil" to "Brasília", "Argentina" to "Buenos Aires",
        "Chile" to "Santiago", "Peru" to "Lima", "Colombia" to "Bogotá", "Venezuela" to "Caracas",
        "Ecuador" to "Quito", "Uruguay" to "Montevideo", "Paraguay" to "Asunción",
    )

    // Words whose meanings overlap (so two options could both be right) are kept out on purpose.
    internal val VOCABULARY = listOf(
        "ephemeral" to "Lasting a very short time",
        "ubiquitous" to "Found everywhere",
        "meticulous" to "Very careful about small details",
        "candid" to "Honest and direct",
        "pragmatic" to "Dealing with things in a practical way",
        "lethargic" to "Sluggish and lacking energy",
        "benevolent" to "Kind and well-meaning",
        "ambivalent" to "Having mixed feelings about something",
        "eloquent" to "Fluent and persuasive in speaking or writing",
        "frugal" to "Careful not to waste money",
        "gregarious" to "Fond of company; sociable",
        "resilient" to "Able to recover quickly from difficulty",
        "tenacious" to "Persistent in sticking to a goal",
        "verbose" to "Using more words than needed",
        "serendipity" to "A happy discovery made by chance",
        "procrastinate" to "To keep delaying something you should do",
        "mundane" to "Dull and ordinary",
        "lucid" to "Clear and easy to understand",
        "diligent" to "Hard-working and careful",
        "obsolete" to "No longer used; out of date",
        "inevitable" to "Certain to happen",
        "ambiguous" to "Open to more than one meaning",
        "nostalgia" to "A fond longing for the past",
        "alleviate" to "To make pain or a problem less severe",
        "scrutinize" to "To examine closely",
        "reticent" to "Not revealing your thoughts easily",
        "cogent" to "Clear, logical and convincing",
        "apathy" to "Lack of interest or concern",
        "zealous" to "Full of energy for a cause",
        "fortuitous" to "Happening by lucky chance",
        "meander" to "To wander slowly without a clear direction",
        "plethora" to "An excessive amount",
        "ostentatious" to "Showy, meant to impress",
        "quell" to "To put an end to, usually by force",
        "pensive" to "Deep in serious thought",
        "innocuous" to "Harmless",
        "laconic" to "Using very few words",
        "magnanimous" to "Generous and forgiving, especially toward a rival",
        "obstinate" to "Stubbornly refusing to change your mind",
        "placate" to "To calm someone by giving them something they want",
        "recalcitrant" to "Uncooperative toward authority",
        "sanguine" to "Optimistic, especially in a bad situation",
        "voracious" to "Wanting great quantities of something",
        "wary" to "Cautious about possible dangers",
        "conundrum" to "A confusing and difficult problem",
        "dexterity" to "Skill with the hands",
        "euphoria" to "A feeling of intense happiness",
        "impetuous" to "Acting quickly without thinking",
        "nonchalant" to "Casually calm and relaxed",
        "paradox" to "A statement that seems to contradict itself but may be true",
        "quintessential" to "The most perfect example of something",
        "rhetoric" to "Persuasive speaking or writing",
        "tacit" to "Understood without being said",
        "vindicate" to "To clear someone of blame",
    )

    internal val SCIENCE = listOf(
        q("Which gas do plants take from the air for photosynthesis?", "Carbon dioxide", "Oxygen", "Nitrogen", "Hydrogen"),
        q("What is the chemical symbol for gold?", "Au", "Ag", "Gd", "Go"),
        q("How many bones are in an adult human body?", "206", "186", "226", "306"),
        q("Which planet is closest to the Sun?", "Mercury", "Venus", "Mars", "Earth"),
        q("What is the largest planet in the Solar System?", "Jupiter", "Saturn", "Neptune", "Earth"),
        q("At sea level, water boils at…", "100 °C", "90 °C", "110 °C", "120 °C"),
        q("Which part of a cell holds most of its DNA?", "The nucleus", "The ribosome", "The cell membrane", "The cytoplasm"),
        q("What is the hardest natural substance?", "Diamond", "Quartz", "Granite", "Iron"),
        q("About how long does sunlight take to reach Earth?", "About 8 minutes", "About 8 seconds", "About 8 hours", "About 1 second"),
        q("Which force keeps the planets in orbit around the Sun?", "Gravity", "Magnetism", "Friction", "Air pressure"),
        q("Which blood cells carry oxygen?", "Red blood cells", "White blood cells", "Platelets", "Plasma"),
        q("Which organ pumps blood around the body?", "The heart", "The lungs", "The liver", "The kidneys"),
        q("What is the chemical symbol for sodium?", "Na", "So", "Sd", "S"),
        q("Which planet is known as the Red Planet?", "Mars", "Venus", "Jupiter", "Mercury"),
        q("Roughly how fast does light travel in a vacuum?", "300,000 km per second", "30,000 km per second", "3,000 km per second", "300 km per second"),
        q("What is the most common gas in Earth's air?", "Nitrogen", "Oxygen", "Carbon dioxide", "Argon"),
        q("What do bees collect from flowers to make honey?", "Nectar", "Sap", "Dew", "Seeds"),
        q("Sound cannot travel through…", "A vacuum", "Water", "Steel", "Air"),
        q("Which part of the cell releases energy from food?", "Mitochondria", "Ribosomes", "The nucleus", "The cell wall"),
        q("Which vitamin does your skin make in sunlight?", "Vitamin D", "Vitamin A", "Vitamin C", "Vitamin K"),
        q("How many chromosomes do humans usually have?", "46", "23", "44", "48"),
        q("Water freezes at…", "32 °F", "0 °F", "12 °F", "50 °F"),
        q("Which element has atomic number 1?", "Hydrogen", "Helium", "Oxygen", "Carbon"),
        q("What is the pH of pure water at 25 °C?", "7", "0", "5", "14"),
        q("What kind of animal is a dolphin?", "A mammal", "A fish", "A reptile", "An amphibian"),
        q("Which planet has the most prominent rings?", "Saturn", "Mars", "Venus", "Mercury"),
        q("Which instrument measures earthquakes?", "A seismometer", "A barometer", "A thermometer", "A hygrometer"),
        q("What is the largest organ of the human body?", "The skin", "The liver", "The brain", "The lungs"),
        q("Electrical resistance is measured in…", "Ohms", "Volts", "Amperes", "Watts"),
        q("What is the chemical formula for table salt?", "NaCl", "KCl", "NaOH", "CaCO₃"),
        q("Which part of a plant takes up water from the soil?", "The roots", "The leaves", "The flowers", "The stem"),
        q("What is the closest star to Earth?", "The Sun", "Proxima Centauri", "Sirius", "Polaris"),
        q("Which gas makes up most of the Sun?", "Hydrogen", "Oxygen", "Carbon", "Nitrogen"),
        q("What do we call animals that eat only plants?", "Herbivores", "Carnivores", "Omnivores", "Insectivores"),
    )
}
