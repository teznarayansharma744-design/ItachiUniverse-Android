package com.arknox.youtube

/**
 * Configuration options for YouTubePlayerView.
 *
 * @property themeColor Accent color for player controls (hex format, e.g., "#3058e5")
 * @property showCaptions Whether to show captions control
 * @property showSpeed Whether to show playback speed option
 * @property showQuality Whether to show quality selector
 * @property autoplay Whether to autoplay video on load
 */
data class YouTubePlayerConfig(
    val themeColor: String = "#3058e5",
    val showCaptions: Boolean = true,
    val showSpeed: Boolean = true,
    val showQuality: Boolean = true,
    val autoplay: Boolean = false
) {
    companion object {
        /**
         * Default configuration with Presto blue theme.
         */
        val DEFAULT = YouTubePlayerConfig()

        /**
         * Dark theme configuration.
         */
        val DARK = YouTubePlayerConfig(themeColor = "#6366f1")

        /**
         * Red theme (similar to YouTube).
         */
        val RED = YouTubePlayerConfig(themeColor = "#ff0000")
    }
}
