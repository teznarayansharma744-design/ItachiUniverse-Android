package com.arknox.youtube

/**
 * Listener interface for YouTube player events.
 */
interface YouTubePlayerListener {
    /**
     * Called when the player is ready to play.
     * @param videoId The loaded video ID
     */
    fun onReady(videoId: String) {}

    /**
     * Called when video playback starts.
     * @param videoId The current video ID
     */
    fun onPlaying(videoId: String) {}

    /**
     * Called when video playback is paused.
     * @param videoId The current video ID
     */
    fun onPaused(videoId: String) {}

    /**
     * Called when the video ends.
     * @param videoId The current video ID
     */
    fun onEnded(videoId: String) {}

    /**
     * Called periodically during playback with time updates.
     * @param currentTime Current playback position in seconds
     * @param duration Total video duration in seconds
     */
    fun onTimeUpdate(currentTime: Float, duration: Float) {}

    /**
     * Called when an error occurs.
     * @param message Error description
     */
    fun onError(message: String) {}

    /**
     * Called when fullscreen state changes.
     * @param isFullscreen Whether the player is now in fullscreen mode
     */
    fun onFullscreenChange(isFullscreen: Boolean) {}
}
