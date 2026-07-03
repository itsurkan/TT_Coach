package com.ttcoachai.pose

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ttcoachai.shared.models.Keypoint2D
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pure View smoke test for [Coco17OverlayView] (Phase 3, task C3). No camera involved:
 * measures/lays out the view at a fixed size, feeds it synthetic COCO-17 keypoints, and
 * draws it to an off-screen [Canvas] backed by a [Bitmap] — asserting only that drawing
 * does not throw. The projection math itself ([Coco17OverlayScaling]) already has direct
 * unit coverage (C1); this test proves the View wiring (onDraw + setKeypoints) is sound.
 */
@RunWith(AndroidJUnit4::class)
class Coco17OverlayViewTest {

    private val width = 480
    private val height = 640

    @Test
    fun draws_without_exception_for_full_synthetic_pose() {
        val view = layoutView()
        view.setKeypoints(syntheticKeypoints())

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Must not throw.
        view.draw(canvas)
    }

    @Test
    fun draws_clean_with_null_keypoints() {
        val view = layoutView()
        view.setKeypoints(null)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Must not throw, and must not draw anything over the cleared background.
        view.draw(canvas)
    }

    private fun layoutView(): Coco17OverlayView {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val view = Coco17OverlayView(context)
        val widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        view.measure(widthSpec, heightSpec)
        view.layout(0, 0, width, height)
        return view
    }

    /** 17 COCO keypoints at varied normalized positions, all confidently scored. */
    private fun syntheticKeypoints(): List<Keypoint2D> = List(17) { i ->
        val t = i / 16f
        Keypoint2D(x = 0.2f + 0.6f * t, y = 0.1f + 0.8f * t, score = 0.9f)
    }
}
