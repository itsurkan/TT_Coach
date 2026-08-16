package com.ttcoachai.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.ttcoachai.R
import com.ttcoachai.managers.RepPoseCapture
import com.ttcoachai.shared.feedback.Coco17ToLandmark3D
import com.ttcoachai.shared.models.CorrectionType
import com.ttcoachai.shared.models.Landmark3D

/**
 * Reusable horizontal swipeable carousel of stroke-rep snapshots — one page per rep, each
 * showing the start/end skeleton pair (via [PoseSnapshotView]) side by side with the caption
 * "Rep N · flagged/clean" (same phrasing as
 * [com.ttcoachai.ui.dialogs.FeedbackExplanationSheet.showRep]). Left/right arrow buttons and a
 * dot page indicator sit around a [RecyclerView] + [PagerSnapHelper] — ViewPager2 is not an app
 * dependency; `recyclerview` 1.3.2 already is (see app/build.gradle), and each page is exactly
 * the carousel's width, so a snap-paged RecyclerView behaves like a single-item-per-screen pager.
 *
 * Self-contained: owns its own adapter, snap/scroll behaviour, arrow enabled/disabled state, and
 * the dot page indicator. Written for a SECOND call site beyond Session Review — the live-training
 * pause panel — so the public surface is intentionally just [setReps].
 *
 * Data + filtering contract for [setReps]:
 * - Pass the RAW (unfiltered) capture list, chronological (oldest first) — e.g.
 *   `TrainingStateManager.getRepPoses()` order, or the resolved list from
 *   [com.ttcoachai.util.RepCarouselDataSource]. Entries whose start or end keypoint list is
 *   empty are dropped internally (cannot render) — same "usable" rule as
 *   [com.ttcoachai.util.RepresentativeRepSelector]; do not pre-filter.
 * - "Rep N" in the caption is the capture's 1-based position in the INPUT list (before
 *   filtering), matching [com.ttcoachai.ui.dialogs.FeedbackExplanationSheet]'s indexing
 *   convention even when some in-between reps were unusable and skipped.
 * - Opens on the LAST page (most recent rep).
 * - The whole view becomes [View.GONE] when zero captures are usable; callers that wrap this in
 *   their own card (e.g. `SessionReviewFragment`'s `cardStrokeSnapshot`) still decide that
 *   outer card's visibility themselves based on whether they had anything to pass in.
 */
class RepCarouselView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private data class Page(
        val ordinal: Int,
        val start: List<Landmark3D>,
        val end: List<Landmark3D>,
        val flagged: Boolean,
        val highlight: CorrectionType,
    )

    private val recyclerView: RecyclerView
    private val btnPrev: ImageButton
    private val btnNext: ImageButton
    private val tvCaption: TextView
    private val indicatorContainer: LinearLayout
    private val snapHelper = PagerSnapHelper()
    private val adapter = PageAdapter()

    private var pages: List<Page> = emptyList()
    private var currentIndex: Int = 0

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_rep_carousel, this, true)
        recyclerView = findViewById(R.id.rv_rep_carousel_pages)
        btnPrev = findViewById(R.id.btn_rep_carousel_prev)
        btnNext = findViewById(R.id.btn_rep_carousel_next)
        tvCaption = findViewById(R.id.tv_rep_carousel_caption)
        indicatorContainer = findViewById(R.id.rep_carousel_indicator)

        recyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        recyclerView.adapter = adapter
        snapHelper.attachToRecyclerView(recyclerView)

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE) return
                val layoutManager = rv.layoutManager ?: return
                val snapView = snapHelper.findSnapView(layoutManager) ?: return
                onPageSelected(layoutManager.getPosition(snapView))
            }
        })

        btnPrev.setOnClickListener { goTo(currentIndex - 1) }
        btnNext.setOnClickListener { goTo(currentIndex + 1) }
    }

    /**
     * Replaces the displayed reps. See the class doc for the filtering/numbering/opening-page
     * contract. [highlight] is the [CorrectionType] to highlight on every skeleton and to test
     * each capture's `flaggedTypes` against for the caption's flagged/clean status; when null,
     * [CorrectionType.GENERAL] is used for the highlight color and a capture counts as "flagged"
     * when it has ANY flagged type — mirrors `SessionReviewFragment.bindStrokeSnapshot`'s
     * `topFocusType ?: CorrectionType.GENERAL` fallback.
     */
    fun setReps(captures: List<RepPoseCapture>, highlight: CorrectionType?) {
        val effectiveHighlight = highlight ?: CorrectionType.GENERAL
        pages = captures.mapIndexedNotNull { index, capture ->
            if (capture.start.isEmpty() || capture.end.isEmpty()) return@mapIndexedNotNull null
            val flagged = if (highlight != null) {
                highlight in capture.flaggedTypes
            } else {
                capture.flaggedTypes.isNotEmpty()
            }
            Page(
                ordinal = index + 1,
                start = Coco17ToLandmark3D.map(capture.start),
                end = Coco17ToLandmark3D.map(capture.end),
                flagged = flagged,
                highlight = effectiveHighlight,
            )
        }

        if (pages.isEmpty()) {
            visibility = View.GONE
            return
        }
        visibility = View.VISIBLE
        adapter.notifyDataSetChanged()
        buildIndicatorDots()
        val lastIndex = pages.lastIndex
        recyclerView.layoutManager?.scrollToPosition(lastIndex)
        // scrollToPosition alone doesn't fire the idle scroll listener synchronously; update the
        // caption/arrows/indicator for the opening page once layout has settled.
        recyclerView.post { onPageSelected(lastIndex) }
    }

    private fun goTo(index: Int) {
        if (index !in pages.indices) return
        recyclerView.smoothScrollToPosition(index)
    }

    private fun onPageSelected(index: Int) {
        if (index !in pages.indices) return
        currentIndex = index
        val page = pages[index]
        val statusRes = if (page.flagged) {
            R.string.feedback_snapshot_rep_flagged
        } else {
            R.string.feedback_snapshot_rep_clean
        }
        tvCaption.text = context.getString(R.string.feedback_snapshot_caption_rep, page.ordinal) +
            " · " + context.getString(statusRes)
        btnPrev.isEnabled = index > 0
        btnPrev.alpha = if (index > 0) 1f else DISABLED_ALPHA
        btnNext.isEnabled = index < pages.lastIndex
        btnNext.alpha = if (index < pages.lastIndex) 1f else DISABLED_ALPHA
        updateIndicatorSelection()
    }

    private fun buildIndicatorDots() {
        indicatorContainer.removeAllViews()
        val dotSize = dp(DOT_SIZE_DP)
        val margin = dp(DOT_MARGIN_DP)
        pages.indices.forEach { i ->
            val dot = View(context)
            dot.layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply {
                marginStart = margin
                marginEnd = margin
            }
            dot.background = dotDrawable(i == currentIndex)
            indicatorContainer.addView(dot)
        }
    }

    private fun updateIndicatorSelection() {
        for (i in 0 until indicatorContainer.childCount) {
            indicatorContainer.getChildAt(i).background = dotDrawable(i == currentIndex)
        }
    }

    private fun dotDrawable(active: Boolean) = ContextCompat.getDrawable(
        context,
        if (active) R.drawable.shape_carousel_dot_active else R.drawable.shape_carousel_dot_inactive
    )

    private fun dp(v: Float) = (v * resources.displayMetrics.density).toInt()

    private inner class PageAdapter : RecyclerView.Adapter<PageAdapter.PageViewHolder>() {
        inner class PageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val poseStart: PoseSnapshotView = view.findViewById(R.id.pose_snapshot_start_carousel)
            val poseEnd: PoseSnapshotView = view.findViewById(R.id.pose_snapshot_end_carousel)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_rep_carousel_page, parent, false)
            return PageViewHolder(view)
        }

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            val page = pages[position]
            holder.poseStart.setSnapshot(page.start, page.highlight)
            holder.poseEnd.setSnapshot(page.end, page.highlight)
        }

        override fun getItemCount(): Int = pages.size
    }

    companion object {
        private const val DISABLED_ALPHA = 0.35f
        private const val DOT_SIZE_DP = 6f
        private const val DOT_MARGIN_DP = 3f
    }
}
