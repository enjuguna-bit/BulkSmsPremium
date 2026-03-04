package com.afriserve.smsmanager.ui.common

import android.view.View
import android.view.ViewGroup
import android.view.LayoutInflater
import com.facebook.shimmer.ShimmerFrameLayout

/**
 * Utility class for managing shimmer loading states.
 * Provides helper methods to show/hide shimmer placeholders.
 * 
 * Usage:
 * ```kotlin
 * // In Fragment/Activity
 * private val shimmerHelper = ShimmerHelper()
 * 
 * // Show shimmer while loading
 * shimmerHelper.showShimmer(shimmerLayout, contentView)
 * 
 * // Hide shimmer when content is ready
 * shimmerHelper.hideShimmer(shimmerLayout, contentView)
 * ```
 */
object ShimmerHelper {
    
    /**
     * Show shimmer loading state and hide content.
     * 
     * @param shimmerLayout The ShimmerFrameLayout to show
     * @param contentViews The content views to hide while loading
     */
    fun showShimmer(shimmerLayout: ShimmerFrameLayout, vararg contentViews: View?) {
        shimmerLayout.visibility = View.VISIBLE
        shimmerLayout.startShimmer()
        contentViews.forEach { it?.visibility = View.GONE }
    }
    
    /**
     * Hide shimmer loading state and show content.
     * 
     * @param shimmerLayout The ShimmerFrameLayout to hide
     * @param contentViews The content views to show
     */
    fun hideShimmer(shimmerLayout: ShimmerFrameLayout, vararg contentViews: View?) {
        shimmerLayout.stopShimmer()
        shimmerLayout.visibility = View.GONE
        contentViews.forEach { it?.visibility = View.VISIBLE }
    }
    
    /**
     * Toggle shimmer based on loading state.
     * 
     * @param isLoading Whether data is still loading
     * @param shimmerLayout The ShimmerFrameLayout
     * @param contentViews The content views
     */
    fun setLoading(isLoading: Boolean, shimmerLayout: ShimmerFrameLayout, vararg contentViews: View?) {
        if (isLoading) {
            showShimmer(shimmerLayout, *contentViews)
        } else {
            hideShimmer(shimmerLayout, *contentViews)
        }
    }
    
    /**
     * Create shimmer items for a RecyclerView placeholder.
     * Inflates the given shimmer item layout multiple times.
     * 
     * @param parent The parent ViewGroup
     * @param itemLayoutRes The layout resource for shimmer item
     * @param count Number of placeholder items to create
     * @return ShimmerFrameLayout containing the placeholder items
     */
    fun createListShimmer(
        parent: ViewGroup,
        itemLayoutRes: Int,
        count: Int = 5
    ): ShimmerFrameLayout {
        val inflater = LayoutInflater.from(parent.context)
        
        val shimmer = ShimmerFrameLayout(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        
        val container = android.widget.LinearLayout(parent.context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        
        repeat(count) {
            val itemView = inflater.inflate(itemLayoutRes, container, false)
            container.addView(itemView)
        }
        
        shimmer.addView(container)
        return shimmer
    }
}

/**
 * Extension function to easily show shimmer on a ShimmerFrameLayout.
 */
fun ShimmerFrameLayout.showLoading() {
    visibility = View.VISIBLE
    startShimmer()
}

/**
 * Extension function to easily hide shimmer on a ShimmerFrameLayout.
 */
fun ShimmerFrameLayout.hideLoading() {
    stopShimmer()
    visibility = View.GONE
}

/**
 * Extension function to toggle shimmer based on loading state.
 */
fun ShimmerFrameLayout.setLoading(isLoading: Boolean) {
    if (isLoading) {
        showLoading()
    } else {
        hideLoading()
    }
}
