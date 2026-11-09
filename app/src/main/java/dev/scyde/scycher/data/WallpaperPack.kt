package dev.scyde.scycher.data

/** Scycher's built-in wallpapers (assets/wallpapers), each with a dark and a light variant. */
object WallpaperPack {
    data class Design(val key: String, val name: String)

    val designs = listOf(
        Design("bloom", "Bloom"), Design("tide", "Tide"), Design("orbit", "Orbit"), Design("ribbon", "Aurora"),
        Design("mesh", "Mesh"), Design("dune", "Dune"), Design("prism", "Prism"), Design("liquid", "Liquid"),
    )

    fun asset(key: String, variant: String) = "wallpapers/${key}_$variant.webp"
    fun thumb(key: String, variant: String) = "wallpapers/thumbs/${key}_$variant.webp"
}
