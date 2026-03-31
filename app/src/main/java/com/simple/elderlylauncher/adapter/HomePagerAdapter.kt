package com.simple.elderlylauncher.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.simple.elderlylauncher.R

class HomePagerAdapter(
    private val onEssentialsInflated: (View) -> Unit,
    private val onHomeInflated: (View) -> Unit,
    private val onEntertainmentInflated: (View) -> Unit
) : RecyclerView.Adapter<HomePagerAdapter.PageViewHolder>() {

    companion object {
        const val PAGE_ESSENTIALS = 0
        const val PAGE_HOME = 1
        const val PAGE_ENTERTAINMENT = 2
        const val PAGE_COUNT = 3
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val layout = when (viewType) {
            PAGE_ESSENTIALS -> R.layout.layout_essentials
            PAGE_HOME -> R.layout.layout_home
            PAGE_ENTERTAINMENT -> R.layout.layout_entertainment
            else -> throw IllegalArgumentException("Invalid view type")
        }
        val view = inflater.inflate(layout, parent, false)
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        when (position) {
            PAGE_ESSENTIALS -> onEssentialsInflated(holder.itemView)
            PAGE_HOME -> onHomeInflated(holder.itemView)
            PAGE_ENTERTAINMENT -> onEntertainmentInflated(holder.itemView)
        }
    }

    override fun getItemCount(): Int = PAGE_COUNT

    override fun getItemViewType(position: Int): Int = position

    class PageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
}
