package tv.glimesh.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import tv.glimesh.R
import tv.glimesh.ui.home.Channel
import java.net.URL

class ChannelAdapter(private val onClick: (Channel) -> Unit) :
    ListAdapter<Channel, ChannelAdapter.ChannelViewHolder>(ChannelDiffCallback) {

    class ChannelViewHolder(itemView: View, val onClick: (Channel) -> Unit) :
        RecyclerView.ViewHolder(itemView) {
        private val displayNameTextView: TextView = itemView.findViewById(R.id.display_name_text)
        private val titleTextView: TextView = itemView.findViewById(R.id.title_text)
        private val avatarImageView: ImageView = itemView.findViewById(R.id.avatar_image)
        private val channelPreviewImageView: ImageView = itemView.findViewById(R.id.thumbnail_image)
        private val radius = itemView.resources.getDimensionPixelSize(R.dimen.corner_radius)
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

            if (channel.streamerAvatarUrl == null) {
                Glide.with(itemView).clear(avatarImageView)
            } else {
                Glide
                    .with(itemView)
                    .load(URL(channel.streamerAvatarUrl))
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(avatarImageView)
            }

            if (channel.streamThumbnailUrl == null) {
                Glide.with(itemView).clear(channelPreviewImageView)
            } else {
                Glide
                    .with(itemView)
                    .load(URL(channel.streamThumbnailUrl))
                    .transform(RoundedCorners(radius))
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(channelPreviewImageView)
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
