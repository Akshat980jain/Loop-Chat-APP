package com.loopchat.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import co.daily.model.Participant
import co.daily.view.VideoView

/**
 * Jetpack Compose wrapper for Daily.co's native hardware-accelerated VideoView.
 * Renders a WebRTC VideoTrack (camera or screen share) directly to the screen.
 */
@Composable
fun DailyVideoView(
    videoTrack: co.daily.model.MediaStreamTrack?,
    modifier: Modifier = Modifier,
    scaleMode: VideoView.VideoScaleMode = VideoView.VideoScaleMode.FIT
) {
    if (videoTrack == null) {
        // Render empty box when no video track is available (e.g. camera off)
        Box(modifier = modifier)
        return
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            VideoView(context).apply {
                this.videoScaleMode = scaleMode
                this.track = videoTrack
            }
        },
        update = { view ->
            view.videoScaleMode = scaleMode
            if (view.track != videoTrack) {
                view.track = videoTrack
            }
        }
    )
}
