package com.example.elderlylauncher.adapter

import android.net.Uri
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.elderlylauncher.R
import com.example.elderlylauncher.databinding.ItemFavoriteContactBinding
import com.example.elderlylauncher.model.FavoriteContact

class FavoriteContactAdapter(
    private val onContactClick: (FavoriteContact) -> Unit
) : ListAdapter<FavoriteContact, FavoriteContactAdapter.ContactViewHolder>(ContactDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val binding = ItemFavoriteContactBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ContactViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        holder.bind(getItem(position), onContactClick)
    }

    class ContactViewHolder(
        private val binding: ItemFavoriteContactBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(contact: FavoriteContact, onClick: (FavoriteContact) -> Unit) {
            binding.contactName.text = contact.name

            // Load contact photo with Glide
            if (contact.photoUri != null) {
                Glide.with(binding.root.context)
                    .load(Uri.parse(contact.photoUri))
                    .placeholder(R.drawable.ic_contact_placeholder)
                    .error(R.drawable.ic_contact_placeholder)
                    .circleCrop()
                    .into(binding.contactPhoto)
            } else {
                binding.contactPhoto.setImageResource(R.drawable.ic_contact_placeholder)
            }

            binding.root.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick(contact)
            }
        }
    }

    class ContactDiffCallback : DiffUtil.ItemCallback<FavoriteContact>() {
        override fun areItemsTheSame(oldItem: FavoriteContact, newItem: FavoriteContact) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: FavoriteContact, newItem: FavoriteContact) =
            oldItem == newItem
    }
}
