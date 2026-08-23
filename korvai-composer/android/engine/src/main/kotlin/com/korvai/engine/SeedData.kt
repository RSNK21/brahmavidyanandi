// AUTO-GENERATED from data/seed.json — do not edit by hand; regenerate with web/gen-kotlin-seed.js
package com.korvai.engine

object SeedData {
    val jatis = listOf(Jati(id = "tisra", name = "Tisra", laghu = 3), Jati(id = "chaturasra", name = "Chaturasra", laghu = 4), Jati(id = "khanda", name = "Khanda", laghu = 5), Jati(id = "misra", name = "Misra", laghu = 7), Jati(id = "sankirna", name = "Sankirna", laghu = 9))

    val talas = listOf(Tala(
        id = "adi", name = "Adi", jati = "Chaturasra",
        angas = listOf(Anga(AngaType.LAGHU, 4), Anga(AngaType.DHRUTAM, 2), Anga(AngaType.DHRUTAM, 2)),
        aksharas = 8
    ), Tala(
        id = "rupaka", name = "Rupaka", jati = "Chaturasra",
        angas = listOf(Anga(AngaType.DHRUTAM, 2), Anga(AngaType.LAGHU, 4)),
        aksharas = 6
    ), Tala(
        id = "tisra_triputa", name = "Tisra Triputa", jati = "Tisra",
        angas = listOf(Anga(AngaType.LAGHU, 3), Anga(AngaType.DHRUTAM, 2), Anga(AngaType.DHRUTAM, 2)),
        aksharas = 7
    ), Tala(
        id = "khanda_chapu", name = "Khanda Chapu", jati = "Khanda",
        angas = listOf(Anga(AngaType.SECTION, 2), Anga(AngaType.SECTION, 3)),
        aksharas = 5
    ), Tala(
        id = "misra_chapu", name = "Misra Chapu", jati = "Misra",
        angas = listOf(Anga(AngaType.SECTION, 3), Anga(AngaType.SECTION, 4)),
        aksharas = 7
    ), Tala(
        id = "eka", name = "Eka", jati = "Chaturasra",
        angas = listOf(Anga(AngaType.LAGHU, 4)),
        aksharas = 4
    ), Tala(
        id = "matya", name = "Matya", jati = "Chaturasra",
        angas = listOf(Anga(AngaType.LAGHU, 4), Anga(AngaType.DHRUTAM, 2), Anga(AngaType.LAGHU, 4)),
        aksharas = 10
    ), Tala(
        id = "ata", name = "Ata", jati = "Chaturasra",
        angas = listOf(Anga(AngaType.LAGHU, 4), Anga(AngaType.LAGHU, 4), Anga(AngaType.DHRUTAM, 2), Anga(AngaType.DHRUTAM, 2)),
        aksharas = 12
    ), Tala(
        id = "dhruva", name = "Dhruva", jati = "Chaturasra",
        angas = listOf(Anga(AngaType.LAGHU, 4), Anga(AngaType.DHRUTAM, 2), Anga(AngaType.LAGHU, 4), Anga(AngaType.LAGHU, 4)),
        aksharas = 14
    ))

    val nadais = listOf(Nadai(id = "tisra", name = "Tisra", subdivision = 3), Nadai(id = "chaturasra", name = "Chaturasra", subdivision = 4), Nadai(id = "khanda", name = "Khanda", subdivision = 5), Nadai(id = "misra", name = "Misra", subdivision = 7), Nadai(id = "sankirna", name = "Sankirna", subdivision = 9))

    val eduppus = listOf(Eduppu(id = "samam", name = "Samam", aksharas = 0.0), Eduppu(id = "arai", name = "1/2 akshara", aksharas = 0.5), Eduppu(id = "idu", name = "1 akshara", aksharas = 1.0), Eduppu(id = "idiyam", name = "1 1/2 akshara", aksharas = 1.5), Eduppu(id = "randam", name = "2 aksharas", aksharas = 2.0))

    val cells = listOf(RhythmicCell(
        id = "c_ta", notation = "ta",
        syllables = listOf("ta"), durations = listOf(1),
        matraCount = 1, weights = listOf(Weight.L),
        character = CellCharacter.BRISK, function = CellFunction.CORE,
        usableNadais = listOf("tisra", "chaturasra", "khanda", "misra", "sankirna"), difficulty = 1,
        kaarvai = false
    ), RhythmicCell(
        id = "c_taka", notation = "ta ka",
        syllables = listOf("ta", "ka"), durations = listOf(1, 1),
        matraCount = 2, weights = listOf(Weight.L, Weight.L),
        character = CellCharacter.SQUARE, function = CellFunction.CORE,
        usableNadais = listOf("tisra", "chaturasra", "khanda", "misra", "sankirna"), difficulty = 1,
        kaarvai = false
    ), RhythmicCell(
        id = "c_takita", notation = "ta ki ta",
        syllables = listOf("ta", "ki", "ta"), durations = listOf(1, 1, 1),
        matraCount = 3, weights = listOf(Weight.L, Weight.L, Weight.H),
        character = CellCharacter.FLOWING, function = CellFunction.CORE,
        usableNadais = listOf("tisra", "chaturasra"), difficulty = 2,
        kaarvai = false
    ), RhythmicCell(
        id = "c_takadimi", notation = "ta ka di mi",
        syllables = listOf("ta", "ka", "di", "mi"), durations = listOf(1, 1, 1, 1),
        matraCount = 4, weights = listOf(Weight.L, Weight.L, Weight.L, Weight.H),
        character = CellCharacter.SQUARE, function = CellFunction.CORE,
        usableNadais = listOf("chaturasra"), difficulty = 2,
        kaarvai = false
    ), RhythmicCell(
        id = "c_takajonu", notation = "ta ka jo nu",
        syllables = listOf("ta", "ka", "jo", "nu"), durations = listOf(1, 1, 1, 1),
        matraCount = 4, weights = listOf(Weight.L, Weight.L, Weight.L, Weight.H),
        character = CellCharacter.STRAIGHT, function = CellFunction.CORE,
        usableNadais = listOf("chaturasra"), difficulty = 2,
        kaarvai = false
    ), RhythmicCell(
        id = "c_thakadhimi", notation = "tha ka dhi mi",
        syllables = listOf("tha", "ka", "dhi", "mi"), durations = listOf(1, 1, 1, 1),
        matraCount = 4, weights = listOf(Weight.H, Weight.L, Weight.L, Weight.H),
        character = CellCharacter.FLOWING, function = CellFunction.CORE,
        usableNadais = listOf("chaturasra"), difficulty = 2,
        kaarvai = false
    ), RhythmicCell(
        id = "c_tarikita", notation = "ta ri ki ta",
        syllables = listOf("ta", "ri", "ki", "ta"), durations = listOf(1, 1, 1, 1),
        matraCount = 4, weights = listOf(Weight.L, Weight.L, Weight.L, Weight.H),
        character = CellCharacter.BRISK, function = CellFunction.CORE,
        usableNadais = listOf("chaturasra"), difficulty = 3,
        kaarvai = false
    ), RhythmicCell(
        id = "c_dhimithaka", notation = "dhi mi ta ka",
        syllables = listOf("dhi", "mi", "ta", "ka"), durations = listOf(1, 1, 1, 1),
        matraCount = 4, weights = listOf(Weight.H, Weight.L, Weight.L, Weight.L),
        character = CellCharacter.BRISK, function = CellFunction.CORE,
        usableNadais = listOf("chaturasra"), difficulty = 2,
        kaarvai = false
    ), RhythmicCell(
        id = "c_kitataka", notation = "ki ta ta ka",
        syllables = listOf("ki", "ta", "ta", "ka"), durations = listOf(1, 1, 1, 1),
        matraCount = 4, weights = listOf(Weight.L, Weight.H, Weight.L, Weight.L),
        character = CellCharacter.SQUARE, function = CellFunction.FILLER,
        usableNadais = listOf("chaturasra"), difficulty = 2,
        kaarvai = false
    ), RhythmicCell(
        id = "c_takatakita", notation = "ta ka ta ki ta",
        syllables = listOf("ta", "ka", "ta", "ki", "ta"), durations = listOf(1, 1, 1, 1, 1),
        matraCount = 5, weights = listOf(Weight.L, Weight.L, Weight.L, Weight.L, Weight.H),
        character = CellCharacter.SQUARE, function = CellFunction.CORE,
        usableNadais = listOf("chaturasra", "khanda"), difficulty = 3,
        kaarvai = false
    ), RhythmicCell(
        id = "c_taditakajonu", notation = "ta di ta ka jo nu",
        syllables = listOf("ta", "di", "ta", "ka", "jo", "nu"), durations = listOf(1, 1, 1, 1, 1, 1),
        matraCount = 6, weights = listOf(Weight.L, Weight.L, Weight.H, Weight.L, Weight.L, Weight.H),
        character = CellCharacter.STRAIGHT, function = CellFunction.CORE,
        usableNadais = listOf("chaturasra"), difficulty = 3,
        kaarvai = false
    ), RhythmicCell(
        id = "c_thakatharikita", notation = "tha ka tha ri ki ta",
        syllables = listOf("tha", "ka", "tha", "ri", "ki", "ta"), durations = listOf(1, 1, 1, 1, 1, 1),
        matraCount = 6, weights = listOf(Weight.H, Weight.L, Weight.H, Weight.L, Weight.L, Weight.H),
        character = CellCharacter.FLOWING, function = CellFunction.CORE,
        usableNadais = listOf("chaturasra"), difficulty = 3,
        kaarvai = false
    ), RhythmicCell(
        id = "c_takita_x2", notation = "ta ki ta ta ki ta",
        syllables = listOf("ta", "ki", "ta", "ta", "ki", "ta"), durations = listOf(1, 1, 1, 1, 1, 1),
        matraCount = 6, weights = listOf(Weight.L, Weight.L, Weight.H, Weight.L, Weight.L, Weight.H),
        character = CellCharacter.FLOWING, function = CellFunction.FILLER,
        usableNadais = listOf("tisra"), difficulty = 2,
        kaarvai = false
    ), RhythmicCell(
        id = "c_takadimi_x2", notation = "ta ka dhi mi tha ka dhi mi",
        syllables = listOf("ta", "ka", "dhi", "mi", "tha", "ka", "dhi", "mi"), durations = listOf(1, 1, 1, 1, 1, 1, 1, 1),
        matraCount = 8, weights = listOf(Weight.L, Weight.L, Weight.L, Weight.H, Weight.H, Weight.L, Weight.L, Weight.H),
        character = CellCharacter.SQUARE, function = CellFunction.CORE,
        usableNadais = listOf("chaturasra"), difficulty = 3,
        kaarvai = false
    ), RhythmicCell(
        id = "c_takajonu_thakadhimi", notation = "ta ka jo nu tha ka dhi mi",
        syllables = listOf("ta", "ka", "jo", "nu", "tha", "ka", "dhi", "mi"), durations = listOf(1, 1, 1, 1, 1, 1, 1, 1),
        matraCount = 8, weights = listOf(Weight.L, Weight.L, Weight.L, Weight.H, Weight.H, Weight.L, Weight.L, Weight.H),
        character = CellCharacter.STRAIGHT, function = CellFunction.CORE,
        usableNadais = listOf("chaturasra"), difficulty = 3,
        kaarvai = false, tags = listOf("mohra")
    ), RhythmicCell(
        id = "c_takitatakadimi", notation = "ta ki ta ta ka di mi",
        syllables = listOf("ta", "ki", "ta", "ta", "ka", "di", "mi"), durations = listOf(1, 1, 1, 1, 1, 1, 1),
        matraCount = 7, weights = listOf(Weight.L, Weight.L, Weight.H, Weight.L, Weight.L, Weight.L, Weight.H),
        character = CellCharacter.FLOWING, function = CellFunction.CORE,
        usableNadais = listOf("chaturasra", "misra"), difficulty = 3,
        kaarvai = false
    ), RhythmicCell(
        id = "c_takadimitakita", notation = "ta ka di mi ta ki ta",
        syllables = listOf("ta", "ka", "di", "mi", "ta", "ki", "ta"), durations = listOf(1, 1, 1, 1, 1, 1, 1),
        matraCount = 7, weights = listOf(Weight.L, Weight.L, Weight.L, Weight.H, Weight.L, Weight.L, Weight.H),
        character = CellCharacter.SQUARE, function = CellFunction.CORE,
        usableNadais = listOf("chaturasra", "misra"), difficulty = 3,
        kaarvai = false
    ), RhythmicCell(
        id = "c_takatakimix2", notation = "ta ka ta ki ta ta ka ta ki ta",
        syllables = listOf("ta", "ka", "ta", "ki", "ta", "ta", "ka", "ta", "ki", "ta"), durations = listOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1),
        matraCount = 10, weights = listOf(Weight.L, Weight.L, Weight.L, Weight.L, Weight.H, Weight.L, Weight.L, Weight.L, Weight.L, Weight.H),
        character = CellCharacter.SQUARE, function = CellFunction.CORE,
        usableNadais = listOf("chaturasra", "khanda"), difficulty = 4,
        kaarvai = false
    ), RhythmicCell(
        id = "c_faran_tisra", notation = "ta ki ta × 4",
        syllables = listOf("ta", "ki", "ta", "ta", "ki", "ta", "ta", "ki", "ta", "ta", "ki", "ta"), durations = listOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1),
        matraCount = 12, weights = listOf(Weight.L, Weight.L, Weight.H, Weight.L, Weight.L, Weight.H, Weight.L, Weight.L, Weight.H, Weight.L, Weight.L, Weight.H),
        character = CellCharacter.FLOWING, function = CellFunction.MACRO,
        usableNadais = listOf("chaturasra"), difficulty = 4,
        kaarvai = false, tags = listOf("faran", "tisra-gati")
    ), RhythmicCell(
        id = "c_faran_khanda", notation = "ta ka ta ki ta × 4",
        syllables = listOf("ta", "ka", "ta", "ki", "ta", "ta", "ka", "ta", "ki", "ta", "ta", "ka", "ta", "ki", "ta", "ta", "ka", "ta", "ki", "ta"), durations = listOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1),
        matraCount = 20, weights = listOf(Weight.L, Weight.L, Weight.L, Weight.L, Weight.H, Weight.L, Weight.L, Weight.L, Weight.L, Weight.H, Weight.L, Weight.L, Weight.L, Weight.L, Weight.H, Weight.L, Weight.L, Weight.L, Weight.L, Weight.H),
        character = CellCharacter.SQUARE, function = CellFunction.MACRO,
        usableNadais = listOf("chaturasra"), difficulty = 5,
        kaarvai = false, tags = listOf("faran", "khanda-gati")
    ), RhythmicCell(
        id = "c_faran_misra", notation = "ta ki ta ta ka di mi × 4",
        syllables = listOf("ta", "ki", "ta", "ta", "ka", "di", "mi", "ta", "ki", "ta", "ta", "ka", "di", "mi", "ta", "ki", "ta", "ta", "ka", "di", "mi", "ta", "ki", "ta", "ta", "ka", "di", "mi"), durations = listOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1),
        matraCount = 28, weights = listOf(Weight.L, Weight.L, Weight.H, Weight.L, Weight.L, Weight.L, Weight.H, Weight.L, Weight.L, Weight.H, Weight.L, Weight.L, Weight.L, Weight.H, Weight.L, Weight.L, Weight.H, Weight.L, Weight.L, Weight.L, Weight.H, Weight.L, Weight.L, Weight.H, Weight.L, Weight.L, Weight.L, Weight.H),
        character = CellCharacter.FLOWING, function = CellFunction.MACRO,
        usableNadais = listOf("chaturasra"), difficulty = 5,
        kaarvai = false, tags = listOf("faran", "misra-gati")
    ), RhythmicCell(
        id = "c_gap2", notation = "— —",
        syllables = listOf("—", "—"), durations = listOf(1, 1),
        matraCount = 2, weights = listOf(Weight.L, Weight.L),
        character = CellCharacter.STRAIGHT, function = CellFunction.GAP,
        usableNadais = listOf("tisra", "chaturasra", "khanda", "misra", "sankirna"), difficulty = 1,
        kaarvai = true
    ), RhythmicCell(
        id = "c_gap3", notation = "— — —",
        syllables = listOf("—", "—", "—"), durations = listOf(1, 1, 1),
        matraCount = 3, weights = listOf(Weight.L, Weight.L, Weight.L),
        character = CellCharacter.STRAIGHT, function = CellFunction.GAP,
        usableNadais = listOf("tisra", "chaturasra", "khanda", "misra", "sankirna"), difficulty = 1,
        kaarvai = true
    ), RhythmicCell(
        id = "c_gap4", notation = "— — — —",
        syllables = listOf("—", "—", "—", "—"), durations = listOf(1, 1, 1, 1),
        matraCount = 4, weights = listOf(Weight.L, Weight.L, Weight.L, Weight.L),
        character = CellCharacter.STRAIGHT, function = CellFunction.GAP,
        usableNadais = listOf("tisra", "chaturasra", "khanda", "misra", "sankirna"), difficulty = 1,
        kaarvai = true
    ), RhythmicCell(
        id = "c_taiyaitaiyai", notation = "tai yai tai yai",
        syllables = listOf("tai", "yai", "tai", "yai"), durations = listOf(1, 1, 1, 1),
        matraCount = 4, weights = listOf(Weight.H, Weight.L, Weight.H, Weight.L),
        character = CellCharacter.STRAIGHT, function = CellFunction.FILLER,
        usableNadais = listOf("chaturasra"), difficulty = 1,
        kaarvai = false, tags = listOf("dance")
    ), RhythmicCell(
        id = "c_taihattaihi", notation = "tai hat tai hi",
        syllables = listOf("tai", "hat", "tai", "hi"), durations = listOf(1, 1, 1, 1),
        matraCount = 4, weights = listOf(Weight.H, Weight.L, Weight.H, Weight.L),
        character = CellCharacter.BRISK, function = CellFunction.CORE,
        usableNadais = listOf("chaturasra"), difficulty = 3,
        kaarvai = false, tags = listOf("dance", "kudittu")
    ), RhythmicCell(
        id = "c_taitat", notation = "tai tat",
        syllables = listOf("tai", "tat"), durations = listOf(1, 1),
        matraCount = 2, weights = listOf(Weight.H, Weight.H),
        character = CellCharacter.SQUARE, function = CellFunction.CORE,
        usableNadais = listOf("tisra", "chaturasra", "khanda", "misra", "sankirna"), difficulty = 1,
        kaarvai = false, tags = listOf("dance")
    ), RhythmicCell(
        id = "c_thathai", notation = "tha thai",
        syllables = listOf("tha", "thai"), durations = listOf(1, 1),
        matraCount = 2, weights = listOf(Weight.H, Weight.L),
        character = CellCharacter.RESONANT, function = CellFunction.TRANSITION,
        usableNadais = listOf("tisra", "chaturasra", "khanda", "misra", "sankirna"), difficulty = 1,
        kaarvai = false
    ), RhythmicCell(
        id = "c_thaithathom", notation = "thai tha thom",
        syllables = listOf("thai", "tha", "thom"), durations = listOf(1, 1, 1),
        matraCount = 3, weights = listOf(Weight.H, Weight.L, Weight.H),
        character = CellCharacter.RESONANT, function = CellFunction.CORE,
        usableNadais = listOf("tisra", "chaturasra"), difficulty = 2,
        kaarvai = false, tags = listOf("dance")
    ), RhythmicCell(
        id = "c_thadhithom", notation = "tha dhi thom",
        syllables = listOf("tha", "dhi", "thom"), durations = listOf(1, 1, 1),
        matraCount = 3, weights = listOf(Weight.H, Weight.L, Weight.H),
        character = CellCharacter.RESONANT, function = CellFunction.TRANSITION,
        usableNadais = listOf("tisra", "chaturasra"), difficulty = 2,
        kaarvai = false
    ), RhythmicCell(
        id = "c_tham", notation = "tham",
        syllables = listOf("tham"), durations = listOf(1),
        matraCount = 1, weights = listOf(Weight.H),
        character = CellCharacter.RESONANT, function = CellFunction.LANDING,
        usableNadais = listOf("tisra", "chaturasra", "khanda", "misra", "sankirna"), difficulty = 1,
        kaarvai = false
    ), RhythmicCell(
        id = "c_thom2", notation = "thom,",
        syllables = listOf("thom"), durations = listOf(2),
        matraCount = 2, weights = listOf(Weight.H),
        character = CellCharacter.RESONANT, function = CellFunction.LANDING,
        usableNadais = listOf("tisra", "chaturasra", "khanda", "misra", "sankirna"), difficulty = 1,
        kaarvai = false
    ), RhythmicCell(
        id = "c_thathom", notation = "tha tham",
        syllables = listOf("tha", "tham"), durations = listOf(1, 1),
        matraCount = 2, weights = listOf(Weight.H, Weight.H),
        character = CellCharacter.RESONANT, function = CellFunction.LANDING,
        usableNadais = listOf("tisra", "chaturasra", "khanda", "misra", "sankirna"), difficulty = 1,
        kaarvai = false
    ), RhythmicCell(
        id = "c_thakatham", notation = "tha ka tham",
        syllables = listOf("tha", "ka", "tham"), durations = listOf(1, 1, 1),
        matraCount = 3, weights = listOf(Weight.H, Weight.L, Weight.H),
        character = CellCharacter.RESONANT, function = CellFunction.LANDING,
        usableNadais = listOf("tisra", "chaturasra", "khanda", "misra", "sankirna"), difficulty = 2,
        kaarvai = false
    ), RhythmicCell(
        id = "c_namthom", notation = "nam thom",
        syllables = listOf("nam", "thom"), durations = listOf(1, 1),
        matraCount = 2, weights = listOf(Weight.H, Weight.H),
        character = CellCharacter.RESONANT, function = CellFunction.LANDING,
        usableNadais = listOf("tisra", "chaturasra", "khanda", "misra", "sankirna"), difficulty = 2,
        kaarvai = false
    ), RhythmicCell(
        id = "c_thathaiatham", notation = "tha thai tha tham",
        syllables = listOf("tha", "thai", "tha", "tham"), durations = listOf(1, 1, 1, 1),
        matraCount = 4, weights = listOf(Weight.H, Weight.L, Weight.H, Weight.H),
        character = CellCharacter.RESONANT, function = CellFunction.ENDING,
        usableNadais = listOf("chaturasra"), difficulty = 2,
        kaarvai = false, tags = listOf("mohra", "dance")
    ), RhythmicCell(
        id = "c_dhiththaithathom", notation = "dhi thit thai tham",
        syllables = listOf("dhi", "thit", "thai", "tham"), durations = listOf(1, 1, 1, 1),
        matraCount = 4, weights = listOf(Weight.H, Weight.H, Weight.L, Weight.H),
        character = CellCharacter.RESONANT, function = CellFunction.ENDING,
        usableNadais = listOf("chaturasra"), difficulty = 3,
        kaarvai = false, tags = listOf("dance")
    ), RhythmicCell(
        id = "c_taihat_x3", notation = "tai hat tai hat tai hi",
        syllables = listOf("tai", "hat", "tai", "hat", "tai", "hi"), durations = listOf(1, 1, 1, 1, 1, 1),
        matraCount = 6, weights = listOf(Weight.H, Weight.L, Weight.H, Weight.L, Weight.H, Weight.L),
        character = CellCharacter.BRISK, function = CellFunction.ENDING,
        usableNadais = listOf("chaturasra"), difficulty = 3,
        kaarvai = false, tags = listOf("dance", "teermana")
    ))

    val aliases = listOf(Alias(notation = "ta ka di mi", variants = listOf("ta ka di mi", "ta ka dhi mi", "tha ka dhi mi", "ta ka jhi mi")), Alias(notation = "ta ka jo nu", variants = listOf("ta ka jo nu", "ta ka jo no", "tha ka jo nu")), Alias(notation = "ta ki ta", variants = listOf("ta ki ta", "tha ki ta", "ta ki tom", "ta din gi")), Alias(notation = "ta ka ta ki ta", variants = listOf("ta ka ta ki ta", "tha ka tha ki ta", "ta ka ta ki tom")))

    val templates = listOf(Template(
        id = "korvai_x3", name = "Korvai — X X X", tags = listOf("korvai"),
        description = "One phrase stated three times, landing on sam. The classic dance/mridangam cadence.", structure = "X X X + landing",
        repetitions = 3, landingMode = "eduppu",
        slots = listOf(Slot(
            id = "X", label = "Korvai phrase", minMatra = 2, maxMatra = 64, allowedFunctions = listOf(CellFunction.CORE, CellFunction.FILLER, CellFunction.TRANSITION), allowGaps = true
        ))
    ), Template(
        id = "korvai_crescendo", name = "Korvai — 13-14-15 crescendo", tags = listOf("korvai"),
        description = "Three phrases of increasing length (n, n+1, n+2); the whole group is played three times, then the landing. The canonical Adi/2-kalai/2-avartana korvai is 13+14+15 ×3 + 2 = 128.", structure = "(A B C) ×3 + landing",
        repetitions = 3, landingMode = "eduppu", staircase = 1,
        slots = listOf(Slot(
            id = "A", label = "Short phrase", minMatra = 2, maxMatra = 48, allowedFunctions = listOf(CellFunction.CORE, CellFunction.FILLER, CellFunction.TRANSITION), allowGaps = true
        ), Slot(
            id = "B", label = "Medium phrase", minMatra = 3, maxMatra = 49, allowedFunctions = listOf(CellFunction.CORE, CellFunction.FILLER, CellFunction.TRANSITION), allowGaps = true
        ), Slot(
            id = "C", label = "Long phrase", minMatra = 4, maxMatra = 50, allowedFunctions = listOf(CellFunction.CORE, CellFunction.FILLER, CellFunction.TRANSITION), allowGaps = true
        ))
    ), Template(
        id = "teermana_x3", name = "Teermana — X X X (sam landings)", tags = listOf("teermana", "korvai"),
        description = "A phrase spanning whole avartanas, played three times — each statement lands on sam (the phrase itself supplies the landing). Typical tirmana/teermana finisher.", structure = "X X X (each X = whole avartanas)",
        repetitions = 3, landingMode = "none", multipleOf = "avartana",
        slots = listOf(Slot(
            id = "X", label = "Teermana phrase", minMatra = 2, maxMatra = 128, allowedFunctions = listOf(CellFunction.CORE, CellFunction.FILLER, CellFunction.TRANSITION, CellFunction.ENDING), allowGaps = true
        ))
    ), Template(
        id = "mohra_korvai", name = "Mohra → Korvai", tags = listOf("mohra", "korvai"),
        description = "Full cadence: (A A A B) ×3 mohra rounds, the 'tha thai tha tham' cadence ×3, then a X X X korvai + landing. Front-padded with kaarvai to fill the tala cycles.", structure = "(A A A B)×3 · C×3 · X X X + landing",
        repetitions = 1, landingMode = "eduppu", autoAvartanas = true,
        slots = listOf(Slot(
            id = "A", label = "Mohra line", minMatra = 8, maxMatra = 8, allowedFunctions = listOf(CellFunction.CORE)
        ), Slot(
            id = "B", label = "Mohra answer", minMatra = 8, maxMatra = 8, allowedFunctions = listOf(CellFunction.CORE)
        ), Slot(
            id = "C", label = "Cadence", fixedCell = "c_thathaiatham"
        ), Slot(
            id = "X", label = "Korvai phrase", minMatra = 2, maxMatra = 32, allowedFunctions = listOf(CellFunction.CORE, CellFunction.FILLER, CellFunction.TRANSITION), allowGaps = true
        ))
    ), Template(
        id = "kuraippu", name = "Kuraippu (diminishing)", tags = listOf("kuraippu"),
        description = "A long phrase restated in progressively shorter versions (roughly halving), converging onto sam. Drama through reduction.", structure = "A A/2 A/4 … + landing",
        repetitions = 1, landingMode = "eduppu", kind = "kuraippu",
        slots = listOf(Slot(
            id = "A", label = "Opening phrase", minMatra = 4, maxMatra = 64, allowedFunctions = listOf(CellFunction.CORE, CellFunction.FILLER, CellFunction.TRANSITION), allowGaps = true
        ))
    ), Template(
        id = "tirmana", name = "Tirmana (3-4-5 ×3)", tags = listOf("tirmana", "dance"),
        description = "The Bharatanatyam tirmana: a 3-matra phrase ×3, a 4-matra phrase ×3, a 5-matra phrase ×3 (ta-ki-ta / ta-ka-dhi-mi / ta-ka-ta-ki-ta families), then landing.", structure = "T3×3 T4×3 T5×3 + landing",
        repetitions = 1, landingMode = "eduppu", autoAvartanas = true,
        slots = listOf(Slot(
            id = "T3", label = "3-matra phrase", minMatra = 3, maxMatra = 3, allowedFunctions = listOf(CellFunction.CORE, CellFunction.TRANSITION)
        ), Slot(
            id = "T4", label = "4-matra phrase", minMatra = 4, maxMatra = 4, allowedFunctions = listOf(CellFunction.CORE, CellFunction.TRANSITION)
        ), Slot(
            id = "T5", label = "5-matra phrase", minMatra = 5, maxMatra = 5, allowedFunctions = listOf(CellFunction.CORE, CellFunction.TRANSITION)
        ))
    ), Template(
        id = "jathi_1avartana", name = "Jathi — one avartana", tags = listOf("jathi"),
        description = "A single-cycle pattern exactly filling one avartana of the chosen tala/nadai/kalai. Building block for jathis.", structure = "X (exactly one avartana)",
        repetitions = 1, landingMode = "none",
        slots = listOf(Slot(
            id = "X", label = "Jathi phrase", minMatra = 2, maxMatra = 128, allowedFunctions = listOf(CellFunction.CORE, CellFunction.FILLER, CellFunction.TRANSITION, CellFunction.ENDING), allowGaps = true
        ))
    ), Template(
        id = "faran", name = "Faran (cross-rhythm)", tags = listOf("faran"),
        description = "A cross-rhythm cell (tisra/khanda/misra inside chaturasra) repeated to span whole aksharas — mathematically guaranteed to resolve.", structure = "F × n (resolves on sam)",
        repetitions = 1, landingMode = "none", kind = "faran",
        slots = listOf(Slot(
            id = "F", label = "Faran cell", allowedFunctions = listOf(CellFunction.MACRO)
        ))
    ), Template(
        id = "gap_korvai", name = "Kaarvai korvai (with gaps)", tags = listOf("korvai"),
        description = "Korvai shape where each statement of the phrase is separated by a kaarvai (rest), building tension into sam.", structure = "X G X G X + landing",
        repetitions = 1, landingMode = "eduppu",
        slots = listOf(Slot(
            id = "X", label = "Korvai phrase", minMatra = 2, maxMatra = 48, allowedFunctions = listOf(CellFunction.CORE, CellFunction.FILLER, CellFunction.TRANSITION)
        ), Slot(
            id = "G", label = "Kaarvai", minMatra = 2, maxMatra = 4, allowedFunctions = listOf(CellFunction.GAP), allowGaps = true
        ))
    ), Template(
        id = "aba_sandwich", name = "A-B-A sandwich", tags = listOf("korvai"),
        description = "Open and close with the same phrase, different contrasting material in the middle. Good for jathi variation.", structure = "A B A + landing",
        repetitions = 1, landingMode = "eduppu",
        slots = listOf(Slot(
            id = "A", label = "Frame phrase", minMatra = 2, maxMatra = 48, allowedFunctions = listOf(CellFunction.CORE, CellFunction.FILLER, CellFunction.TRANSITION), allowGaps = true
        ), Slot(
            id = "B", label = "Contrast phrase", minMatra = 2, maxMatra = 48, allowedFunctions = listOf(CellFunction.CORE, CellFunction.FILLER, CellFunction.TRANSITION), allowGaps = true
        ))
    ))

    val adavus = listOf(Adavu(
        id = "tatta", name = "Tatta Adavu", family = "Tatta", sollukattu = "thai yai that",
        counts = 8, characters = listOf(CellCharacter.SQUARE, CellCharacter.STRAIGHT),
        nadais = listOf("chaturasra"), description = "Flat-foot strikes in ardhamandali; the first adavu learned.", difficulty = 1
    ), Adavu(
        id = "natta", name = "Natta Adavu", family = "Natta", sollukattu = "thai yai that (nattu)",
        counts = 8, characters = listOf(CellCharacter.SQUARE, CellCharacter.STRAIGHT),
        nadais = listOf("chaturasra"), description = "Nattu position with heel-strike stretches; mardiya stance work.", difficulty = 2
    ), Adavu(
        id = "visharu", name = "Visharu Adavu", family = "Visharu", sollukattu = "ta thei thei tat",
        counts = 8, characters = listOf(CellCharacter.FLOWING, CellCharacter.STRAIGHT),
        nadais = listOf("chaturasra"), description = "Side-swinging traverse steps (visharu — to spread out), covering stage space.", difficulty = 2
    ), Adavu(
        id = "kudittu_mettu", name = "Kudittu Mettu Adavu", family = "Kudittu Mettu", sollukattu = "tai hat tai hi",
        counts = 8, characters = listOf(CellCharacter.BRISK),
        nadais = listOf("chaturasra"), description = "Jump-and-strike mettu combinations; sharp, percussive landings.", difficulty = 3
    ), Adavu(
        id = "sarikkal", name = "Sarikkal / Kal Adavu", family = "Sarikkal", sollukattu = "tha thei thei",
        counts = 8, characters = listOf(CellCharacter.STRAIGHT, CellCharacter.RESONANT),
        nadais = listOf("chaturasra"), description = "Gliding/sliding steps (sarikka — to glide) with held endings.", difficulty = 2
    ), Adavu(
        id = "mandi", name = "Mandi Adavu", family = "Mandi", sollukattu = "tha thai thai (mandi)",
        counts = 8, characters = listOf(CellCharacter.RESONANT, CellCharacter.SQUARE),
        nadais = listOf("chaturasra"), description = "Full-sit (mandi) positions with sweeping strikes.", difficulty = 4
    ), Adavu(
        id = "tirmana_adavu", name = "Tirmana Adavu", family = "Tirmana", sollukattu = "ta ki ta · ta ka dhi mi · ta ka ta ki ta",
        counts = 36, characters = listOf(CellCharacter.FLOWING),
        nadais = listOf("chaturasra", "tisra"), description = "The classic three-pace finisher (3-4-5) closing jathis and varnams.", difficulty = 4
    ), Adavu(
        id = "teermana_adavu", name = "Teermana Adavu", family = "Teermana", sollukattu = "tai hat tai hat tai hi",
        counts = 6, characters = listOf(CellCharacter.BRISK, CellCharacter.RESONANT),
        nadais = listOf("chaturasra"), description = "Triple-landing finishing adavu that converges on sam.", difficulty = 4
    ), Adavu(
        id = "panjanan", name = "Panjanan Adavu", family = "Panjanan", sollukattu = "thai that thai that thai hi",
        counts = 8, characters = listOf(CellCharacter.SQUARE),
        nadais = listOf("chaturasra"), description = "Sharp striking combinations in place; strong arangetram material.", difficulty = 3
    ), Adavu(
        id = "hitchu", name = "Hitchu / Kattadavu", family = "Kattu", sollukattu = "kit a ta ka",
        counts = 8, characters = listOf(CellCharacter.SQUARE, CellCharacter.BRISK),
        nadais = listOf("chaturasra"), description = "Small held-step adavus used as connectors between larger movements.", difficulty = 2
    ), Adavu(
        id = "thagaditham", name = "Thagaditham Adavu", family = "Thagaditham", sollukattu = "tha ka dhi mi thom (thagaditham)",
        counts = 8, characters = listOf(CellCharacter.RESONANT, CellCharacter.FLOWING),
        nadais = listOf("chaturasra"), description = "Tha-ga-di-gee-thom striking sequence with chest and foot accents.", difficulty = 3
    ), Adavu(
        id = "sutral", name = "Sutral / Bramari Adavu", family = "Sutral", sollukattu = "thai yai that (turn)",
        counts = 8, characters = listOf(CellCharacter.FLOWING, CellCharacter.BRISK),
        nadais = listOf("chaturasra"), description = "Spinning/turning adavus for coverage and closure of stage space.", difficulty = 5
    ))

    val library: Library = Library(talas, jatis, nadais, eduppus, cells, aliases, templates, adavus)
}
