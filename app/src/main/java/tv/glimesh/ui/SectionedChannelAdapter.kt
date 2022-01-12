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

class SectionedChannelAdapter(private val onClick: (Channel) -> Unit) :
    ListAdapter<SectionedChannelAdapter.Item, RecyclerView.ViewHolder>(
        DiffCallback
    ) {

    private val ITEM_VIEW_TYPE_CHANNEL = 1
    private val ITEM_VIEW_TYPE_LARGE_CHANNEL = 2
    private val ITEM_VIEW_TYPE_HEADER = 3

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

        fun bind(item: Item.Channel) {
            val channel = item.channel
            currentChannel = channel

            titleTextView.text = channel.title
            displayNameTextView.text = channel.streamerDisplayName

            if (channel.streamerAvatarUrl == null) {
                Glide.with(itemView).clear(avatarImageView)
            } else {
                Glide
                    .with(itemView)
                    .load(URL(channel.streamerAvatarUrl))
                    .circleCrop()
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

        companion object {
            fun inflate(parent: ViewGroup, onClick: (Channel) -> Unit): ChannelViewHolder {
                return ChannelViewHolder(
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_channel, parent, false),
                    onClick
                )
            }
        }
    }

    class LargeChannelViewHolder(itemView: View, val onClick: (Channel) -> Unit) :
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

        fun bind(item: Item.LargeChannel) {
            val channel = item.channel
            currentChannel = channel

            titleTextView.text = channel.title
            displayNameTextView.text = channel.streamerDisplayName

            if (channel.streamerAvatarUrl == null) {
                Glide.with(itemView).clear(avatarImageView)
            } else {
                Glide
                    .with(itemView)
                    .load(URL(channel.streamerAvatarUrl))
                    .circleCrop()
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

        companion object {
            fun inflate(parent: ViewGroup, onClick: (Channel) -> Unit): LargeChannelViewHolder {
                return LargeChannelViewHolder(
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_channel_large, parent, false),
                    onClick
                )
            }
        }
    }

    class HeaderViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(R.id.text)

        fun bind(header: Item.Header) {
            textView.text = header.title
        }

        companion object {
            fun inflate(parent: ViewGroup): HeaderViewHolder {
                return HeaderViewHolder(
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_channel_header, parent, false)
                )
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is Item.Channel -> ITEM_VIEW_TYPE_CHANNEL
            is Item.LargeChannel -> ITEM_VIEW_TYPE_LARGE_CHANNEL
            is Item.Header -> ITEM_VIEW_TYPE_HEADER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            ITEM_VIEW_TYPE_CHANNEL -> ChannelViewHolder.inflate(parent, onClick)
            ITEM_VIEW_TYPE_LARGE_CHANNEL -> LargeChannelViewHolder.inflate(parent, onClick)
            ITEM_VIEW_TYPE_HEADER -> HeaderViewHolder.inflate(parent)
            else -> throw ClassCastException("Unknown viewType: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ChannelViewHolder -> {
                holder.bind(getItem(position) as Item.Channel)
            }
            is LargeChannelViewHolder -> {
                holder.bind(getItem(position) as Item.LargeChannel)
            }
            is HeaderViewHolder -> {
                holder.bind(getItem(position) as Item.Header)
            }
        }
    }

    sealed class Item {
        data class Channel(val channel: tv.glimesh.ui.home.Channel) : Item() {
            override val id = channel.id
        }

        data class LargeChannel(val channel: tv.glimesh.ui.home.Channel) : Item() {
            override val id = channel.id
        }

        data class Header(val title: String) : Item() {
            override val id = title
        }

        abstract val id: String
    }

    object DiffCallback : DiffUtil.ItemCallback<Item>() {
        override fun areItemsTheSame(oldItem: Item, newItem: Item): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: Item, newItem: Item): Boolean {
            return oldItem.id == newItem.id
        }
    }
}