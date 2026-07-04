package com.ttcoachai.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
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
import kotlin.math.hypot

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
    private var type: CorrectionType = CorrectionType.GENERAL

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
    private val guideDashedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = ContextCompat.getColor(context, R.color.ttc_gold_bright)
    }
    private val guideReferenceLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = ContextCompat.getColor(context, R.color.ttc_text_3)
        alpha = 90
    }
    private val guideAxisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.ttc_gold_bright)
    }

    init {
        bonePaint.strokeWidth = dp(BONE_STROKE_DP)
        boneHighlightPaint.strokeWidth = dp(BONE_STROKE_DP * 1.5f)
        arcStrokePaint.strokeWidth = dp(1.5f)
        degreeTextPaint.textSize = dp(13f)
        guideDashedPaint.strokeWidth = dp(2f)
        guideDashedPaint.pathEffect = DashPathEffect(floatArrayOf(dp(4f), dp(4f)), 0f)
        guideReferenceLinePaint.strokeWidth = dp(1f)
        guideAxisPaint.strokeWidth = dp(3f)
    }

    /** Set the frame to render (single stroke-rep frame) and which correction type to highlight. */
    fun setSnapshot(frame: List<Landmark3D>, type: CorrectionType) {
        this.frame = frame
        this.type = type
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
     *
     * Computed over [FIT_LANDMARKS] (the simplified render set: head anchors + torso/arm/leg
     * joints) only, so stray low-confidence face/finger landmarks that are no longer drawn can't
     * skew the fit.
     */
    private fun calculateScaleAndOffset() {
        if (width == 0 || height == 0) return
        val visible = FIT_LANDMARKS.mapNotNull { frame.getOrNull(it) }
            .filter { it.visibility >= MIN_VISIBILITY }
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
        if (FIT_LANDMARKS.none { idx -> frame.getOrNull(idx)?.let { it.visibility >= MIN_VISIBILITY } == true }) return

        val highlightSet = highlight.highlightJoints.toSet()
        val includesWristCorrection = WRIST in highlightSet && INDEX_FINGER in highlightSet

        // Bones (simplified skeleton only: no face, no hand/finger bones)
        for ((startIdx, endIdx) in BONES) {
            val start = frame.getOrNull(startIdx) ?: continue
            val end = frame.getOrNull(endIdx) ?: continue
            if (start.visibility < MIN_VISIBILITY || end.visibility < MIN_VISIBILITY) continue

            val isHighlighted = isBoneHighlighted(startIdx, endIdx, highlightSet)
            val paint = if (isHighlighted) boneHighlightPaint else bonePaint
            canvas.drawLine(px(start), py(start), px(end), py(end), paint)
        }

        // Exception: WRIST correction also draws the 16-20 bone (index finger direction ray).
        if (includesWristCorrection) {
            val wrist = frame.getOrNull(WRIST)
            val indexFinger = frame.getOrNull(INDEX_FINGER)
            if (wrist != null && indexFinger != null &&
                wrist.visibility >= MIN_VISIBILITY && indexFinger.visibility >= MIN_VISIBILITY
            ) {
                canvas.drawLine(px(wrist), py(wrist), px(indexFinger), py(indexFinger), boneHighlightPaint)
            }
        }

        // Head: single circle from mean of visible {0 (nose), 7, 8 (ears)}.
        drawHead(canvas)

        // Joints: simplified set only, plus landmark 20 when it's part of the WRIST arc triple.
        val jointIndices = if (includesWristCorrection) JOINT_LANDMARKS_WITH_INDEX_FINGER else JOINT_LANDMARKS
        for (idx in jointIndices) {
            val lm = frame.getOrNull(idx) ?: continue
            if (lm.visibility < MIN_VISIBILITY) continue
            val isHighlighted = idx in highlightSet
            val paint = if (isHighlighted) jointHighlightPaint else jointPaint
            val radius = if (isHighlighted) dp(JOINT_RADIUS_HIGHLIGHT_DP) else dp(JOINT_RADIUS_DP)
            canvas.drawCircle(px(lm), py(lm), radius, paint)
        }

        drawArc(canvas)
        drawGuides(canvas)
    }

    /**
     * Which bones render gold. Defaults to "both endpoints in [highlight].highlightJoints"
     * (unchanged for WRIST/FOLLOW_THROUGH/STROKE_SPEED/GENERAL), but ELBOW_POSITION,
     * CONTACT_HEIGHT and BODY_ROTATION override this per the per-type guide design — see
     * [drawGuides] — since their `highlightJoints` are joint dots for a relationship, not a
     * literal bone.
     */
    private fun isBoneHighlighted(startIdx: Int, endIdx: Int, highlightSet: Set<Int>): Boolean {
        return when (type) {
            CorrectionType.ELBOW_POSITION -> {
                (startIdx == SHOULDER_R && endIdx == ELBOW) || (startIdx == ELBOW && endIdx == SHOULDER_R) ||
                    (startIdx == SHOULDER_R && endIdx == HIP_R) || (startIdx == HIP_R && endIdx == SHOULDER_R)
            }
            CorrectionType.CONTACT_HEIGHT -> false
            CorrectionType.BODY_ROTATION -> false
            else -> startIdx in highlightSet && endIdx in highlightSet
        }
    }

    /** Head circle: center = mean of visible {0, 7, 8}; radius = 0.75 * dist(7,8), or dp(9) fallback. */
    private fun drawHead(canvas: Canvas) {
        val nose = frame.getOrNull(NOSE)?.takeIf { it.visibility >= MIN_VISIBILITY }
        val earL = frame.getOrNull(EAR_L)?.takeIf { it.visibility >= MIN_VISIBILITY }
        val earR = frame.getOrNull(EAR_R)?.takeIf { it.visibility >= MIN_VISIBILITY }

        val visibleHeadPoints = listOfNotNull(nose, earL, earR)
        if (visibleHeadPoints.isEmpty()) return

        val cx = visibleHeadPoints.map { px(it) }.average().toFloat()
        val cy = visibleHeadPoints.map { py(it) }.average().toFloat()

        val radius = if (earL != null && earR != null) {
            0.75f * hypot((px(earL) - px(earR)).toDouble(), (py(earL) - py(earR)).toDouble()).toFloat()
        } else {
            dp(9f)
        }

        canvas.drawCircle(cx, cy, radius, bonePaint)
    }

    /**
     * Per-[type] measurement guides drawn on top of the skeleton. Each guide is gated on
     * visibility >= [MIN_VISIBILITY] of the landmarks it uses.
     */
    private fun drawGuides(canvas: Canvas) {
        when (type) {
            CorrectionType.ELBOW_POSITION -> drawElbowPositionGuide(canvas)
            CorrectionType.CONTACT_HEIGHT -> drawContactHeightGuide(canvas)
            CorrectionType.BODY_ROTATION -> drawBodyRotationGuide(canvas)
            else -> Unit
        }
    }

    private fun drawElbowPositionGuide(canvas: Canvas) {
        val elbow = frame.getOrNull(ELBOW) ?: return
        val hip = frame.getOrNull(HIP_R) ?: return
        if (elbow.visibility < MIN_VISIBILITY || hip.visibility < MIN_VISIBILITY) return
        canvas.drawLine(px(elbow), py(elbow), px(hip), py(hip), guideDashedPaint)
    }

    private fun drawContactHeightGuide(canvas: Canvas) {
        val wrist = frame.getOrNull(WRIST) ?: return
        val shoulder = frame.getOrNull(SHOULDER_R)
        val hip = frame.getOrNull(HIP_R)
        if (wrist.visibility < MIN_VISIBILITY) return

        val wristY = py(wrist)
        canvas.drawLine(0f, wristY, width.toFloat(), wristY, guideDashedPaint)

        if (shoulder != null && shoulder.visibility >= MIN_VISIBILITY) {
            val y = py(shoulder)
            canvas.drawLine(0f, y, width.toFloat(), y, guideReferenceLinePaint)
        }
        if (hip != null && hip.visibility >= MIN_VISIBILITY) {
            val y = py(hip)
            canvas.drawLine(0f, y, width.toFloat(), y, guideReferenceLinePaint)
        }
    }

    private fun drawBodyRotationGuide(canvas: Canvas) {
        val shoulderL = frame.getOrNull(SHOULDER_L)
        val shoulderR = frame.getOrNull(SHOULDER_R)
        val hipL = frame.getOrNull(HIP_L)
        val hipR = frame.getOrNull(HIP_R)

        if (shoulderL != null && shoulderR != null &&
            shoulderL.visibility >= MIN_VISIBILITY && shoulderR.visibility >= MIN_VISIBILITY
        ) {
            drawExtendedLine(canvas, shoulderL, shoulderR, guideAxisPaint)
        }
        if (hipL != null && hipR != null &&
            hipL.visibility >= MIN_VISIBILITY && hipR.visibility >= MIN_VISIBILITY
        ) {
            drawExtendedLine(canvas, hipL, hipR, guideAxisPaint)
        }
    }

    /** Draws the line through [a]->[b] extended ~20% beyond each endpoint. */
    private fun drawExtendedLine(canvas: Canvas, a: Landmark3D, b: Landmark3D, paint: Paint) {
        val ax = px(a)
        val ay = py(a)
        val bx = px(b)
        val by = py(b)
        val dx = bx - ax
        val dy = by - ay
        val extX = dx * EXTEND_FRACTION
        val extY = dy * EXTEND_FRACTION
        canvas.drawLine(ax - extX, ay - extY, bx + extX, by + extY, paint)
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
        private const val EXTEND_FRACTION = 0.2f

        // MediaPipe BlazePose-33 landmark indices used by name below (mirrors SnapshotGeometry's
        // per-correction-type joint mapping so guide drawing lines up with what's highlighted).
        private const val NOSE = 0
        private const val EAR_L = 7
        private const val EAR_R = 8
        private const val SHOULDER_L = 11
        private const val SHOULDER_R = 12
        private const val ELBOW = 14
        private const val WRIST = 16
        private const val INDEX_FINGER = 20
        private const val HIP_L = 23
        private const val HIP_R = 24

        /**
         * Simplified skeleton bone list: torso, arms, legs only — no face (0-10), no hand/finger
         * bones (17-22). Addresses complaint (a): face dot-cluster and finger dots read as
         * floating debris on real-device footage. The head is instead drawn as a single circle
         * (see [drawHead]); the 16-20 wrist bone is added conditionally when the WRIST correction
         * arc triple includes landmark 20 (see [onDraw]).
         */
        private val BONES: List<Pair<Int, Int>> = listOf(
            // Torso
            11 to 12, 11 to 23, 12 to 24, 23 to 24,
            // Arms
            11 to 13, 13 to 15,
            12 to 14, 14 to 16,
            // Legs
            23 to 25, 25 to 27, 27 to 29, 27 to 31, 29 to 31,
            24 to 26, 26 to 28, 28 to 30, 28 to 32
        )

        /** Joint dots drawn by default: torso/arm landmarks 11-16 + leg landmarks 23-32. */
        private val JOINT_LANDMARKS: List<Int> = (11..16) + (23..32)

        /** [JOINT_LANDMARKS] plus landmark 20 (index finger), for the WRIST correction exception. */
        private val JOINT_LANDMARKS_WITH_INDEX_FINGER: List<Int> = JOINT_LANDMARKS + INDEX_FINGER

        /**
         * Landmark set used to fit the view's bounding box (see [calculateScaleAndOffset]):
         * head anchors {0,7,8} union the simplified joint set, so the fit can't be skewed by
         * face/finger landmarks that are no longer rendered.
         */
        private val FIT_LANDMARKS: List<Int> = listOf(NOSE, EAR_L, EAR_R) + JOINT_LANDMARKS
    }
}
