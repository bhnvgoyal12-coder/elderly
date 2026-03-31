package com.example.elderlylauncher.adapter

import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.elderlylauncher.R
import com.example.elderlylauncher.databinding.ItemFavoriteAppBinding
import com.example.elderlylauncher.model.AppInfo

class FavoriteAppsAdapter(
    private val onAppClick: (AppInfo) -> Unit,
    private val onAppLongClick: (AppInfo) -> Unit,
    private val onRemoveClick: (AppInfo) -> Unit
) : ListAdapter<AppInfo, FavoriteAppsAdapter.FavoriteAppViewHolder>(AppDiffCallback()) {

    private var editMode = false

    fun setEditMode(enabled: Boolean) {
        editMode = enabled
        notifyDataSetChanged()
    }

    fun isInEditMode() = editMode

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavoriteAppViewHolder {
        val binding = ItemFavoriteAppBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FavoriteAppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FavoriteAppViewHolder, position: Int) {
        holder.bind(getItem(position), editMode, onAppClick, onAppLongClick, onRemoveClick)
    }

    class FavoriteAppViewHolder(
        private val binding: ItemFavoriteAppBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            appInfo: AppInfo,
            editMode: Boolean,
            onClick: (AppInfo) -> Unit,
            onLongClick: (AppInfo) -> Unit,
            onRemove: (AppInfo) -> Unit
        ) {
            binding.appName.text = appInfo.name
            if (appInfo.icon != null) {
                binding.appIcon.setImageDrawable(appInfo.icon)
            } else {
                binding.appIcon.setImageResource(R.drawable.ic_apps)
            }

            // Show/hide remove button based on edit mode
            binding.btnRemove.visibility = if (editMode) View.VISIBLE else View.GONE

            binding.root.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                if (editMode) {
                    onRemove(appInfo)
                } else {
                    onClick(appInfo)
                }
            }

            binding.root.setOnLongClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                onLongClick(appInfo)
                true
            }

            binding.btnRemove.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onRemove(appInfo)
            }
        }
    }

    class AppDiffCallback : DiffUtil.ItemCallback<AppInfo>() {
        override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo) =
            oldItem.packageName == newItem.packageName

        override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo) =
            oldItem.packageName == newItem.packageName && oldItem.name == newItem.name
    }
}
