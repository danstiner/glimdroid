package com.danielstiner.glimdroid.ui.channel

import android.graphics.drawable.Drawable
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ImageSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.danielstiner.glimdroid.R
import com.danielstiner.glimdroid.data.model.ChatMessage

class ChatAdapter :
    ListAdapter<ChatMessage, ChatAdapter.ChatViewHolder>(ChatDiffCallback) {

    class ChatViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(R.id.text)

        fun bind(chat: ChatMessage) {
            textView.text = buildSpannedString {
                append("  ")
                bold { append(chat.displayname) }
                append(": ")
                append(chat.message)
            }

            if (chat.avatarUrl != null) {
                Glide
                    .with(itemView)
                    .load(Uri.parse(chat.avatarUrl))
                    .circleCrop()
                    .into(object : CustomTarget<Drawable>(38, 38) {
                        override fun onLoadCleared(res: Drawable?) {
                            textView.text = buildSpannedString {
                                append("  ")
                                bold { append(chat.displayname) }
                                append(": ")
                                append(chat.message)
                            }
                        }

                        override fun onResourceReady(
                            drawable: Drawable,
                            transition: Transition<in Drawable>?
                        ) {
                            drawable.setBounds(0, 0, 38, 38)
                            textView.text = buildSpannedString {
                                appendSpan(
                                    "i",
                                    ImageSpan(drawable),
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                );
                                append(" ")
                                bold { append(chat.displayname) }
                                append(": ")
                                append(chat.message)
                            }
                        }
                    })
            }
        }
    }

    /* Creates and inflates view, then returns ChannelViewHolder for view. */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat, parent, false)
        return ChatViewHolder(view)
    }

    /* Gets current channel and uses it to bind view. */
    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

object ChatDiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
    override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
        return oldItem.id == newItem.id
    }
}

fun SpannableStringBuilder.appendSpan(
    text: String, what: Any, flags: Int
): SpannableStringBuilder {
    val start = length
    append(text)
    setSpan(what, start, length, flags)
    return this
}
