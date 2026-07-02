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
    scaleMode: VideoView.VideoScaleMode = VideoView.VideoScaleMode.FIT,
    isOverlay: Boolean = false
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
                if (isOverlay) {
                    val viewAsView = this as android.view.View
                    if (viewAsView is android.view.SurfaceView) {
                        viewAsView.setZOrderMediaOverlay(true)
                    } else if (viewAsView is android.view.ViewGroup) {
                        for (i in 0 until viewAsView.childCount) {
                            val child = viewAsView.getChildAt(i)
                            if (child is android.view.SurfaceView) {
                                child.setZOrderMediaOverlay(true)
                            }
                        }
                    }
                }
            }
        },
        update = { view ->
            view.videoScaleMode = scaleMode
            if (view.track != videoTrack) {
                view.track = videoTrack
            }
            if (isOverlay) {
                val viewAsView = view as android.view.View
                if (viewAsView is android.view.SurfaceView) {
                    viewAsView.setZOrderMediaOverlay(true)
                } else if (viewAsView is android.view.ViewGroup) {
                    for (i in 0 until viewAsView.childCount) {
                        val child = viewAsView.getChildAt(i)
                        if (child is android.view.SurfaceView) {
                            child.setZOrderMediaOverlay(true)
                        }
                    }
                }
            }
        }
    )
}
