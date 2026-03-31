package com.example.elderlylauncher.adapter

import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.elderlylauncher.R
import com.example.elderlylauncher.databinding.ItemAppBinding
import com.example.elderlylauncher.model.AppInfo
import com.example.elderlylauncher.util.AppRepository

class AppListAdapter(
    private val onAppClick: (AppInfo) -> Unit,
    private val onAddToHome: (AppInfo) -> Unit,
    private val isFavorite: (String) -> Boolean
) : ListAdapter<AppInfo, AppListAdapter.AppViewHolder>(AppDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position), onAppClick, onAddToHome, isFavorite)
    }

    class AppViewHolder(
        private val binding: ItemAppBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            appInfo: AppInfo,
            onClick: (AppInfo) -> Unit,
            onAddToHome: (AppInfo) -> Unit,
            isFavorite: (String) -> Boolean
        ) {
            binding.appName.text = appInfo.name

            // Load icon lazily - use cached icon or load on-demand
            val icon = appInfo.icon ?: AppRepository.getAppIcon(
                binding.root.context,
                appInfo.packageName
            )
            if (icon != null) {
                binding.appIcon.setImageDrawable(icon)
            } else {
                binding.appIcon.setImageResource(R.drawable.ic_apps)
            }

            val isAlreadyFavorite = isFavorite(appInfo.packageName)

            // Show add button or check icon
            if (isAlreadyFavorite) {
                binding.btnAddToHome.visibility = View.GONE
                binding.iconAdded.visibility = View.VISIBLE
            } else {
                binding.btnAddToHome.visibility = View.VISIBLE
                binding.iconAdded.visibility = View.GONE
            }

            binding.root.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick(appInfo)
            }

            binding.btnAddToHome.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onAddToHome(appInfo)
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
