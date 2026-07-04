package com.ttcoachai.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.ttcoachai.R
import com.ttcoachai.shared.feedback.SnapshotGeometry
import com.ttcoachai.shared.models.CorrectionType
import com.ttcoachai.shared.models.Landmark3D
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

/**
 * Static MediaPipe-33 skeleton snapshot for the training feedback-explanation bottom sheet.
 *
 * Data source: a single frame from `FeedbackItem.strokeLandmarks` (selected via
 * [com.ttcoachai.shared.feedback.StrokeSnapshotSelector]), rendered against the joint/arc
 * highlight mapping in [SnapshotGeometry.highlightFor] for the [CorrectionType] the user tapped.
 * Consumes the shared [Landmark3D] model (x, y normalized [0,1], visibility) — not the MediaPipe
 * SDK's `NormalizedLandmark` — so it has no MediaPipe dependency, unlike [PoseVisualizer].
 *
 * Adapts [PoseVisualizer]'s fit-to-view bounding-box technique (see `calculateScaleAndOffset`)
 * rather than modifying it: that class is frozen (feeds the live MediaPipe pipeline).
 */
class PoseSnapshotView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var frame: List<Landmark3D> = emptyList()
    private var highlight: SnapshotGeometry.SnapshotHighlight =
        SnapshotGeometry.SnapshotHighlight(null, emptyList(), false)

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    // Fit-to-view scale/offset. UNIFORM scale (same factor for x and y), like
    // PoseVisualizer.calculateScaleAndOffset: the legacy pipeline measured all angles directly on
    // normalized [0,1] coords treated as a square space, so only a uniform mapping keeps the
    // on-screen bone rays' angle identical to the measured angle shown in the degree label.
    // Independent x/y scaling would stretch the skeleton and desynchronize arc vs. bones.
    private var scaleFactor: Float = 1f
    private var offsetX: Float = 0f
    private var offsetY: Float = 0f

    private val bonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.ttc_text_3)
    }
    private val boneHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.ttc_gold_bright)
    }
    private val jointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.ttc_text_2)
    }
    private val jointHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.ttc_gold_bright)
    }
    private val arcFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.ttc_gold_accent)
        alpha = 64 // ~25% — semi-transparent wedge
    }
    private val arcStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = ContextCompat.getColor(context, R.color.ttc_gold_bright)
    }
    private val degreeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.ttc_gold_bright)
        textAlign = Paint.Align.CENTER
        typeface = ResourcesCompat.getFont(context, R.font.jetbrains_mono_medium)
    }

    init {
        bonePaint.strokeWidth = dp(BONE_STROKE_DP)
        boneHighlightPaint.strokeWidth = dp(BONE_STROKE_DP * 1.5f)
        arcStrokePaint.strokeWidth = dp(1.5f)
        degreeTextPaint.textSize = dp(13f)
    }

    /** Set the frame to render (single stroke-rep frame) and which correction type to highlight. */
    fun setSnapshot(frame: List<Landmark3D>, type: CorrectionType) {
        this.frame = frame
        this.highlight = SnapshotGeometry.highlightFor(type)
        calculateScaleAndOffset()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateScaleAndOffset()
    }

    /**
     * Fit the bounding box of visible (visibility >= 0.5) landmarks into the view with ~12%
     * padding, centered, using a single uniform scale factor (min of the per-axis fits) so
     * angles are preserved — see the field comment on [scaleFactor].
     */
    private fun calculateScaleAndOffset() {
        if (width == 0 || height == 0) return
        val visible = frame.filter { it.visibility >= MIN_VISIBILITY }
        if (visible.isEmpty()) return

        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (lm in visible) {
            minX = min(minX, lm.x)
            maxX = max(maxX, lm.x)
            minY = min(minY, lm.y)
            maxY = max(maxY, lm.y)
        }

        val boxWidth = (maxX - minX).let { if (it <= 0f) 1f else it }
        val boxHeight = (maxY - minY).let { if (it <= 0f) 1f else it }

        val paddedWidth = boxWidth * (1f + PADDING_FRACTION * 2f)
        val paddedHeight = boxHeight * (1f + PADDING_FRACTION * 2f)

        scaleFactor = min(width / paddedWidth, height / paddedHeight)

        val centerX = (minX + maxX) / 2f
        val centerY = (minY + maxY) / 2f

        offsetX = width / 2f - centerX * scaleFactor
        offsetY = height / 2f - centerY * scaleFactor
    }

    private fun px(lm: Landmark3D) = lm.x * scaleFactor + offsetX
    private fun py(lm: Landmark3D) = lm.y * scaleFactor + offsetY

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (frame.isEmpty()) return
        if (frame.none { it.visibility >= MIN_VISIBILITY }) return

        val highlightSet = highlight.highlightJoints.toSet()

        // Bones
        for ((startIdx, endIdx) in BONES) {
            val start = frame.getOrNull(startIdx) ?: continue
            val end = frame.getOrNull(endIdx) ?: continue
            if (start.visibility < MIN_VISIBILITY || end.visibility < MIN_VISIBILITY) continue

            val isHighlighted = startIdx in highlightSet && endIdx in highlightSet
            val paint = if (isHighlighted) boneHighlightPaint else bonePaint
            canvas.drawLine(px(start), py(start), px(end), py(end), paint)
        }

        // Joints
        for ((idx, lm) in frame.withIndex()) {
            if (lm.visibility < MIN_VISIBILITY) continue
            val isHighlighted = idx in highlightSet
            val paint = if (isHighlighted) jointHighlightPaint else jointPaint
            val radius = if (isHighlighted) dp(JOINT_RADIUS_HIGHLIGHT_DP) else dp(JOINT_RADIUS_DP)
            canvas.drawCircle(px(lm), py(lm), radius, paint)
        }

        drawArc(canvas)
    }

    /**
     * Draws the angle wedge at the vertex joint of [highlight].jointTriple, if present and
     * [SnapshotGeometry.angleAtJoint] returns a value. Ray angles are computed in view space
     * (post scale/offset) via atan2 so the arc always matches what's actually drawn on screen,
     * even though the sweep magnitude equals the true (xScale-free, snapshot-only) 2D angle.
     */
    private fun drawArc(canvas: Canvas) {
        if (!highlight.showArc) return
        val (aIdx, vertexIdx, bIdx) = highlight.jointTriple ?: return

        val angle = SnapshotGeometry.angleAtJoint(frame, aIdx, vertexIdx, bIdx) ?: return

        val a = frame.getOrNull(aIdx) ?: return
        val vertex = frame.getOrNull(vertexIdx) ?: return
        val b = frame.getOrNull(bIdx) ?: return

        val vx = px(vertex)
        val vy = py(vertex)

        val angleToA = Math.toDegrees(atan2((py(a) - vy).toDouble(), (px(a) - vx).toDouble())).toFloat()
        val angleToB = Math.toDegrees(atan2((py(b) - vy).toDouble(), (px(b) - vx).toDouble())).toFloat()

        // Sweep from ray-to-A to ray-to-B, taking the shorter direction, magnitude = actual angle.
        var sweep = angleToB - angleToA
        while (sweep > 180f) sweep -= 360f
        while (sweep < -180f) sweep += 360f
        val sweepMagnitude = if (sweep < 0) -angle else angle

        val radius = dp(ARC_RADIUS_DP)
        val rect = RectF(vx - radius, vy - radius, vx + radius, vy + radius)

        canvas.drawArc(rect, angleToA, sweepMagnitude, true, arcFillPaint)
        canvas.drawArc(rect, angleToA, sweepMagnitude, true, arcStrokePaint)

        // Degree label, offset outward from the vertex along the bisector of the two rays,
        // clamped to stay inside view bounds.
        val bisectorDeg = angleToA + sweepMagnitude / 2f
        val bisectorRad = Math.toRadians(bisectorDeg.toDouble())
        val labelDistance = radius + dp(LABEL_OFFSET_DP)
        var labelX = vx + (kotlin.math.cos(bisectorRad) * labelDistance).toFloat()
        var labelY = vy + (kotlin.math.sin(bisectorRad) * labelDistance).toFloat()

        val halfTextWidth = degreeTextPaint.measureText("000°") / 2f
        val textHeight = degreeTextPaint.textSize
        // coerceAtLeast/coerceAtMost chained (not coerceIn) so a view narrower than the label
        // text can't cross the bounds and throw.
        labelX = labelX.coerceAtLeast(halfTextWidth).coerceAtMost(width.toFloat() - halfTextWidth)
        labelY = labelY.coerceAtLeast(textHeight).coerceAtMost(height.toFloat())

        canvas.drawText("${angle.toInt()}°", labelX, labelY, degreeTextPaint)
    }

    companion object {
        private const val MIN_VISIBILITY = 0.5f
        private const val PADDING_FRACTION = 0.12f
        private const val BONE_STROKE_DP = 2.5f
        private const val JOINT_RADIUS_DP = 3f
        private const val JOINT_RADIUS_HIGHLIGHT_DP = 6f
        private const val ARC_RADIUS_DP = 28f
        private const val LABEL_OFFSET_DP = 14f

        // MediaPipe BlazePose 33-landmark bone list (same topology PoseVisualizer/OverlayView
        // draw via PoseLandmarker.POSE_LANDMARKS.forEach — reproduced here as plain index pairs
        // so this view has no MediaPipe SDK dependency).
        private val BONES: List<Pair<Int, Int>> = listOf(
            // Face
            0 to 1, 1 to 2, 2 to 3, 3 to 7,
            0 to 4, 4 to 5, 5 to 6, 6 to 8,
            9 to 10,
            // Torso
            11 to 12, 11 to 23, 12 to 24, 23 to 24,
            // Left arm
            11 to 13, 13 to 15,
            15 to 17, 15 to 19, 15 to 21, 17 to 19,
            // Right arm
            12 to 14, 14 to 16,
            16 to 18, 16 to 20, 16 to 22, 18 to 20,
            // Left leg
            23 to 25, 25 to 27, 27 to 29, 27 to 31, 29 to 31,
            // Right leg
            24 to 26, 26 to 28, 28 to 30, 28 to 32, 30 to 32
        )
    }
}
