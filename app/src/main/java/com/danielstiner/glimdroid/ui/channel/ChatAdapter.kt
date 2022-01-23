package com.danielstiner.glimdroid.ui.channel

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.DynamicDrawableSpan
import android.text.style.ImageSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.DrawableRes
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
        private val iconSize = itemView.resources.getDimensionPixelSize(R.dimen.chat_icon_size)
        private val textView: TextView = itemView.findViewById(R.id.text)

        fun bind(chat: ChatMessage) {
            textView.text = buildSpannedString {
                append("   ") // Placeholder for avatar image
                appendChatBody(chat, itemView.context)
            }

            if (chat.avatarUrl != null) {
                Glide
                    .with(itemView)
                    .load(Uri.parse(chat.avatarUrl))
                    .circleCrop()
                    .into(object : CustomTarget<Drawable>(iconSize, iconSize) {
                        override fun onLoadCleared(res: Drawable?) {
                            textView.text = buildSpannedString {
                                append("   ") // Placeholder for avatar image
                                appendChatBody(chat, itemView.context)
                            }
                        }

                        override fun onResourceReady(
                            drawable: Drawable,
                            transition: Transition<in Drawable>?
                        ) {
                            drawable.setBounds(0, 0, iconSize, iconSize)
                            textView.text = buildSpannedString {
                                appendSpan(
                                    "i",
                                    ImageSpan(drawable, DynamicDrawableSpan.ALIGN_CENTER),
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                )
                                append(" ")
                                appendChatBody(chat, itemView.context)
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

private fun SpannableStringBuilder.appendChatBody(chat: ChatMessage, context: Context) {
    bold { append(chat.displayname) }
    append(": ")
    for (token in chat.tokens) {
        when (token.type) {
            "text" -> append(token.text)
            "emote" -> {
                val resId = getEmoteDrawableResId(token.text)
                if (resId != null) {
                    appendSpan(
                        "e",
                        ImageSpan(context, resId, DynamicDrawableSpan.ALIGN_CENTER),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                } else {
                    append(token.text)
                }
            }
        }

    }
}

@DrawableRes
fun getEmoteDrawableResId(name: String) = when (name) {
    ":glimangry:" -> R.drawable.ic_emote_glimangry
    ":glimart:" -> R.drawable.ic_emote_glimart
    ":glimbacon:" -> R.drawable.ic_emote_glimbacon
    ":glimbday:" -> R.drawable.ic_emote_glimbday
    ":glimbeholder:" -> R.drawable.ic_emote_glimbeholder
    ":glimburrito:" -> R.drawable.ic_emote_glimburrito
    ":glimcat:" -> R.drawable.ic_emote_glimcat
    ":glimchef:" -> R.drawable.ic_emote_glimchef
    ":glimchicken:" -> R.drawable.ic_emote_glimchicken
    ":glimchu:" -> R.drawable.ic_emote_glimchu
    ":glimcry:" -> R.drawable.ic_emote_glimcry
    ":glimfam:" -> R.drawable.ic_emote_glimfam
    ":glimheart:" -> R.drawable.ic_emote_glimheart
    ":glimhype:" -> R.drawable.ic_emote_glimhype
    ":glimlol:" -> R.drawable.ic_emote_glimlol
    ":glimlove:" -> R.drawable.ic_emote_glimlove
    ":glimsad:" -> R.drawable.ic_emote_glimsad
    ":glimsleepy:" -> R.drawable.ic_emote_glimsleepy
    ":glimsmile:" -> R.drawable.ic_emote_glimsmile
    ":glimtongue:" -> R.drawable.ic_emote_glimtongue
    ":glimuwu:" -> R.drawable.ic_emote_glimuwu
    ":glimwink:" -> R.drawable.ic_emote_glimwink
    ":glimwow:" -> R.drawable.ic_emote_glimwow
    else -> null
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
