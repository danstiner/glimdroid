package tv.glimesh.ui.home

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import tv.glimesh.R

class FollowingAdapter(private val onClick: (Channel) -> Unit) :
    ListAdapter<Channel, FollowingAdapter.ChannelViewHolder>(ChannelDiffCallback) {

    class ChannelViewHolder(itemView: View, val onClick: (Channel) -> Unit) :
        RecyclerView.ViewHolder(itemView) {
        private val displayNameTextView: TextView = itemView.findViewById(R.id.display_name_text)
        private val titleTextView: TextView = itemView.findViewById(R.id.title_text)
        private val avatarImageView: ImageView = itemView.findViewById(R.id.avatar_image)
        private var currentChannel: Channel? = null

        init {
            itemView.setOnClickListener {
                currentChannel?.let {
                    onClick(it)
                }
            }
        }

        fun bind(channel: Channel) {
            currentChannel = channel

            titleTextView.text = channel.title
            displayNameTextView.text = channel.streamerDisplayName
            if (channel.streamerAvatarUrl != null) {
                avatarImageView.setImageURI(Uri.parse(channel.streamerAvatarUrl))
            } else {
                // clear avatarImageView
            }
        }
    }

    /* Creates and inflates view, then returns ChannelViewHolder for view. */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_channel, parent, false)
        return ChannelViewHolder(view, onClick)
    }

    /* Gets current channel and uses it to bind view. */
    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        val channel = getItem(position)
        holder.bind(channel)

    }
}

object ChannelDiffCallback : DiffUtil.ItemCallback<Channel>() {
    override fun areItemsTheSame(oldItem: Channel, newItem: Channel): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: Channel, newItem: Channel): Boolean {
        return oldItem.id == newItem.id
    }
}
