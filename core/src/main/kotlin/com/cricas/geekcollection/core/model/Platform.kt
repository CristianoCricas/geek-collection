package com.cricas.geekcollection.core.model

/**
 * A gaming platform. The app ships a curated list of well known platforms,
 * but items may also store a free-form platform name typed by the user.
 */
data class Platform(
    val name: String,
    val brand: String,
    /** Alternative spellings used to detect the platform in recognized text. */
    val aliases: List<String> = emptyList(),
)

object Platforms {
    val all: List<Platform> = listOf(
        // Nintendo
        Platform("NES", "Nintendo", listOf("Nintendo Entertainment System", "Nintendinho", "Famicom")),
        Platform("Super Nintendo", "Nintendo", listOf("SNES", "Super NES", "Super Famicom")),
        Platform("Nintendo 64", "Nintendo", listOf("N64")),
        Platform("GameCube", "Nintendo", listOf("Nintendo GameCube", "NGC")),
        Platform("Wii", "Nintendo", listOf("Nintendo Wii")),
        Platform("Wii U", "Nintendo", listOf("Nintendo Wii U")),
        Platform("Nintendo Switch", "Nintendo", listOf("Switch", "NSW")),
        Platform("Nintendo Switch 2", "Nintendo", listOf("Switch 2")),
        Platform("Game Boy", "Nintendo", listOf("Gameboy", "GB")),
        Platform("Game Boy Color", "Nintendo", listOf("GBC")),
        Platform("Game Boy Advance", "Nintendo", listOf("GBA")),
        Platform("Nintendo DS", "Nintendo", listOf("NDS", "DS")),
        Platform("Nintendo 3DS", "Nintendo", listOf("3DS")),
        // Sony
        Platform("PlayStation", "Sony", listOf("PS1", "PSX", "PSOne", "PlayStation 1")),
        Platform("PlayStation 2", "Sony", listOf("PS2")),
        Platform("PlayStation 3", "Sony", listOf("PS3")),
        Platform("PlayStation 4", "Sony", listOf("PS4")),
        Platform("PlayStation 5", "Sony", listOf("PS5")),
        Platform("PSP", "Sony", listOf("PlayStation Portable")),
        Platform("PS Vita", "Sony", listOf("PlayStation Vita", "Vita")),
        // Microsoft
        Platform("Xbox", "Microsoft", listOf("Xbox Classic")),
        Platform("Xbox 360", "Microsoft", listOf("X360")),
        Platform("Xbox One", "Microsoft", listOf("XONE", "XB1")),
        Platform("Xbox Series X|S", "Microsoft", listOf("Xbox Series X", "Xbox Series S", "Series X", "Series S", "XSX")),
        // Sega
        Platform("Master System", "Sega", listOf("Sega Master System", "SMS")),
        Platform("Mega Drive", "Sega", listOf("Sega Genesis", "Genesis", "Sega Mega Drive")),
        Platform("Sega Saturn", "Sega", listOf("Saturn")),
        Platform("Dreamcast", "Sega", listOf("Sega Dreamcast")),
        Platform("Game Gear", "Sega", listOf("Sega Game Gear")),
        // Others
        Platform("Atari 2600", "Atari", listOf("Atari")),
        Platform("Neo Geo", "SNK", listOf("NeoGeo")),
        Platform("PC", "PC", listOf("Windows", "Steam", "Computador")),
        Platform("Mobile", "Mobile", listOf("Android", "iOS", "Celular")),
        Platform("Arcade", "Arcade", listOf("Fliperama")),
    )

    val names: List<String> = all.map { it.name }

    fun byName(name: String): Platform? =
        all.firstOrNull { it.name.equals(name, ignoreCase = true) }
}
