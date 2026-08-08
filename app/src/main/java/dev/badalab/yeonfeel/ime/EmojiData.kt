package dev.badalab.yeonfeel.ime

/** 이모지 패널 데이터. ZWJ 조합 없이 단일 코드포인트 위주로 구성한다. */
object EmojiData {

    data class Category(val title: String, val emojis: List<String>)

    val categories = listOf(
        Category(
            "스마일리",
            "😀 😃 😄 😁 😆 😅 😂 🤣 🙂 😊 😇 🥰 😍 🤩 😘 😋 😜 🤪 😝 🤗 🤭 🤔 😐 😶 🙄 😏 😴 🥱 😪 🤤 😷 🤒 🤕 🤢 🤮 🥵 🥶 😵 🤯 🥳 😎 🤓 🧐 😕 😟 😢 😭 😤 😠 😡 🤬 😱 😨 😰 😥 😓 🤝".split(" "),
        ),
        Category(
            "제스처",
            "👍 👎 👏 🙌 👋 🤚 ✋ 🖐 🤙 💪 🙏 ☝️ 👆 👇 👈 👉 ✌️ 🤞 🤘 🤟 👌 🤌 🤏 ✊ 👊 🤛 🤜".split(" "),
        ),
        Category(
            "하트·기호",
            "❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 💕 💞 💓 💗 💖 💘 💝 💯 💢 💥 💫 💦 💨 ✨ ⭐ 🌟 ⚡ 🔥 🎉 🎊".split(" "),
        ),
        Category(
            "동물·자연",
            "🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🐔 🐧 🐦 🦆 🦅 🦉 🐴 🦄 🐝 🦋 🐢 🐍 🐙 🦀 🐬 🐳 🌸 🌼 🌻 🌹 🌷 🍀 🌈 ☀️ 🌙".split(" "),
        ),
        Category(
            "음식",
            "🍎 🍌 🍇 🍓 🍉 🍑 🍍 🥝 🍅 🥑 🍞 🧀 🍖 🍗 🍔 🍟 🍕 🌭 🌮 🍜 🍚 🍙 🍣 🍤 🍦 🍰 🎂 🍫 🍬 🍿 ☕ 🍵 🥤 🍺 🍷".split(" "),
        ),
        Category(
            "활동·사물",
            "⚽ 🏀 🏈 ⚾ 🎾 🏐 🎱 🏓 🏸 🥊 🎮 🎲 🎯 🎸 🎹 🎤 🎧 📱 💻 ⌚ 📷 🔋 💡 📚 ✏️ 📌 🔑 💰 🎁 🚗 ✈️ 🚀 🏠 ⏰ 📅".split(" "),
        ),
    )

    /** 검색 키워드 → 이모지. 키워드는 공백 구분, 부분 일치로 찾는다. */
    private val keywords = listOf(
        "웃음 스마일 기쁨 행복 smile happy" to "😀 😃 😄 😁 😆 😊 🙂",
        "폭소 낄낄 웃겨 lol laugh ㅋㅋ" to "😂 🤣",
        "사랑 좋아 love" to "🥰 😍 😘 💘 💖 💕",
        "하트 heart" to "❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 💗 💓 💞",
        "슬픔 눈물 울음 sad cry" to "😢 😭 😥 😪 💔",
        "화남 분노 짜증 angry" to "😠 😡 🤬 💢",
        "놀람 충격 헉 wow shock" to "😱 🤯 😨 😰",
        "졸림 피곤 잠 sleep tired" to "😴 🥱 😪",
        "아픔 병 아파 sick" to "😷 🤒 🤕 🤢 🤮",
        "멋짐 쿨 cool" to "😎 🤩 🥳",
        "생각 고민 think" to "🤔 🧐 😕",
        "따봉 최고 굿 good thumbs" to "👍 💪 👏 🙌",
        "싫어 별로 bad" to "👎",
        "인사 안녕 손 hi hello wave" to "👋 🤚 ✋",
        "기도 부탁 감사 pray please" to "🙏",
        "브이 victory" to "✌️ 🤞",
        "박수 clap" to "👏 🙌",
        "불 열정 fire" to "🔥",
        "별 star" to "⭐ 🌟 ✨ 💫",
        "축하 파티 party congrats" to "🎉 🎊 🥳 🎁 🎂",
        "강아지 개 멍멍 dog puppy" to "🐶",
        "고양이 냥 cat" to "🐱",
        "곰 bear" to "🐻 🐼",
        "토끼 rabbit" to "🐰",
        "새 bird" to "🐦 🐧 🦆 🦅 🦉",
        "꽃 flower" to "🌸 🌼 🌻 🌹 🌷",
        "나무 자연 행운 luck" to "🍀",
        "무지개 rainbow" to "🌈",
        "해 태양 sun" to "☀️",
        "달 밤 moon" to "🌙",
        "커피 coffee" to "☕",
        "맥주 술 beer" to "🍺 🍷",
        "밥 식사 rice" to "🍚 🍙 🍜",
        "피자 pizza" to "🍕",
        "치킨 chicken" to "🍗",
        "케이크 cake" to "🍰 🎂",
        "과일 사과 fruit apple" to "🍎 🍌 🍇 🍓 🍉",
        "축구 공 soccer ball" to "⚽",
        "농구 basketball" to "🏀",
        "야구 baseball" to "⚾",
        "게임 game" to "🎮 🎲",
        "음악 노래 music song" to "🎸 🎹 🎤 🎧",
        "폰 전화 phone" to "📱",
        "컴퓨터 노트북 computer" to "💻",
        "돈 money" to "💰",
        "집 home house" to "🏠",
        "차 자동차 car" to "🚗",
        "비행기 여행 travel plane" to "✈️ 🚀",
        "시간 시계 time clock" to "⏰ ⌚ 📅",
        "책 공부 book study" to "📚 ✏️",
        "백점 만점 100" to "💯",
        "번개 전기 bolt" to "⚡",
        "선물 gift" to "🎁",
    ).map { (keys, emojis) -> keys.split(" ") to emojis.split(" ") }

    /** 키워드·카테고리명 부분 일치 검색. */
    fun search(query: String): List<String> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val result = LinkedHashSet<String>()
        keywords.forEach { (keys, emojis) ->
            if (keys.any { it.contains(trimmed, ignoreCase = true) }) result.addAll(emojis)
        }
        categories.forEach { category ->
            if (category.title.contains(trimmed, ignoreCase = true)) result.addAll(category.emojis)
        }
        return result.take(30)
    }
}
