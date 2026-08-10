package com.otakeeesen.byebyemoneylist.data

import com.otakeeesen.byebyemoneylist.R

object CategoryEmoji {

    data class Group(val nameResId: Int, val emojis: List<String>)

    val GROUPS: List<Group> = listOf(
        Group(
            R.string.emoji_group_food,
            listOf(
                "🍎", "🍌", "🍊", "🍋", "🍉", "🍇", "🍓", "🫐", "🍒", "🍑",
                "🥝", "🍍", "🥑", "🥦", "🥬", "🥕", "🌽", "🥔", "🍅", "🧄",
                "🍞", "🥖", "🥐", "🧀", "🥛", "🧈", "🥚", "🍗", "🥩", "🍖",
                "🐟", "🦐", "🍕", "🍔", "🍟", "🌭", "🍿", "🍜", "🍚", "🥗",
                "🍰", "🍦", "🍫", "🍪", "🍬", "☕", "🫖", "🧃", "🍺", "🥤"
            )
        ),
        Group(
            R.string.emoji_group_transport,
            listOf(
                "🚗", "🚕", "🚙", "🚌", "🚎", "🚓", "🚑", "🚒", "🚚", "🛻",
                "🏍️", "🚲", "🛴", "🚆", "🚇", "🚢", "✈️", "⛽", "🅿️", "🚦",
                "🛞"
            )
        ),
        Group(
            R.string.emoji_group_home,
            listOf(
                "🏠", "🏡", "🏢", "🏬", "🛋️", "🛏️", "🛁", "🚿", "🧹", "🧺",
                "🧽", "🧴", "🧻", "💡", "🔌", "🔑", "🔨", "🪛", "🧰", "🪴",
                "🍽️", "🥄", "🍳", "🧊", "🧯"
            )
        ),
        Group(
            R.string.emoji_group_health,
            listOf(
                "🏥", "💊", "💉", "🩺", "🌡️", "🦷", "👁️", "🫀", "🦴", "🧠",
                "🩹", "🤧", "😷", "🧼", "🪥", "💪", "🏃", "🧘", "⚕️"
            )
        ),
        Group(
            R.string.emoji_group_shopping,
            listOf(
                "🛒", "🛍️", "💳", "🪙", "🏷️", "📦", "🎁", "👕", "👖", "👗",
                "🧥", "👟", "👠", "🧢", "👜", "💄", "🎮"
            )
        ),
        Group(
            R.string.emoji_group_entertainment,
            listOf(
                "🎬", "🎭", "🎤", "🎵", "🎧", "🎹", "🎸", "🎨", "🖌️", "📚",
                "📖", "🎮", "🕹️", "🎲", "🧩", "⚽", "🏀", "🏈", "🎾", "⚾",
                "🏐", "🎯", "🚴", "🎳", "🎢"
            )
        ),
        Group(
            R.string.emoji_group_money,
            listOf(
                "💰", "💵", "💶", "💷", "💳", "🏦", "📈", "📉", "💸", "🪙",
                "🧾", "📊", "💼", "🏧", "💱"
            )
        ),
        Group(
            R.string.emoji_group_people,
            listOf(
                "👶", "🧒", "👦", "👧", "👨", "👩", "🧑", "👴", "👵", "👩‍🦰",
                "👨‍🦳", "👮", "👷", "👩‍⚕️", "👨‍🎓", "👩‍🏫", "🧑‍🚀", "🦸", "🦹", "🧙"
            )
        ),
        Group(
            R.string.emoji_group_nature,
            listOf(
                "🌱", "🌿", "🌳", "🌲", "🌵", "🌸", "🌻", "🌷", "🌹", "🍂",
                "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯",
                "🦁", "🐮", "🐷", "🐸", "🐵", "🐔", "🐧", "🦆", "🦅", "🦉",
                "🐝", "🦋", "🐢", "🐬", "🐳", "🌍", "☀️", "🌧️", "❄️"
            )
        ),
        Group(
            R.string.emoji_group_other,
            listOf(
                "📱", "💻", "⌚", "📷", "🔋", "📡", "📞", "✉️", "🔒", "🗂️",
                "📌", "📍", "⭐", "🔥", "💎", "🎈", "🎊", "🕐", "📅", "🔔"
            )
        ),
    )

    val ALL_EMOJIS: List<String> = GROUPS.flatMap { it.emojis }
}
