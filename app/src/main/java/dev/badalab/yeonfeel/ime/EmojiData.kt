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
}
