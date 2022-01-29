package com.danielstiner.glimdroid.ui.channel

import android.graphics.drawable.Drawable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.DynamicDrawableSpan
import android.text.style.ImageSpan
import android.text.style.URLSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.text.bold
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
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
        private val multiTarget = TextViewMultiTarget(textView)
        private val requestManager = Glide.with(textView)

        init {
            textView.movementMethod = LinkMovementMethod()
        }

        fun bind(chat: ChatMessage) {
            bindMessage(chat)
        }

        private fun bindMessage(chat: ChatMessage) {
            val replacements = mutableListOf<TextViewMultiTarget.Replacement>()
            val spannedString = SpannableStringBuilder().apply {
                if (chat.avatarUri == null) {
                    append("   ") // No avatar placeholder for alignment
                } else {
                    replacements.add(
                        replace(
                            requestManager.load(chat.avatarUri).circleCrop(),
                            iconSize,
                            iconSize
                        ) { append("   ") })
                }
                append(" ")
                bold { append(chat.displayname) }
                append(": ")

                for (token in chat.tokens) {
                    when (token) {
                        is ChatMessage.Token.Text -> append(token.text)
                        is ChatMessage.Token.Emote -> {
                            val resId = getEmoteDrawableResId(token.text)

                            if (resId != null) {
                                appendSpan(
                                    token.text,
                                    ImageSpan(
                                        textView.context,
                                        resId,
                                        DynamicDrawableSpan.ALIGN_CENTER
                                    ),
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                )
                            } else {
                                replacements.add(
                                    replace(
                                        requestManager.load(token.src).fitCenter(),
                                        iconSize,
                                        iconSize
                                    ) { append(token.text) }
                                )
                            }
                        }
                        is ChatMessage.Token.Url -> appendSpan(
                            token.text,
                            URLSpan(token.url),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                }
            }

            multiTarget.load(spannedString, replacements.toList())
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

inline fun SpannableStringBuilder.replace(
    request: RequestBuilder<Drawable>,
    height: Int,
    width: Int,
    verticalAlignment: Int = DynamicDrawableSpan.ALIGN_BOTTOM,
    builderAction: SpannableStringBuilder.() -> Unit
): TextViewMultiTarget.Replacement {
    val start = length
    builderAction()
    val end = length
    return TextViewMultiTarget.Replacement(
        request,
        TextViewMultiTarget.SpanTarget(
            start,
            end,
            width,
            height,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            verticalAlignment
        )
    )
}

class TextViewMultiTarget(val view: TextView) {
    val requestManager = Glide.with(view)
    val targets: MutableList<Target> = mutableListOf()
    var spannable: SpannableStringBuilder = SpannableStringBuilder()

    fun load(text: SpannableStringBuilder, requests: List<Replacement>) {
        clear()
        spannable = text
        view.text = spannable
        for (request in requests) {
            targets.add(request.request.into(Target(request.where)))
        }
    }

    private fun clear() {
        targets.removeAll {
            requestManager.clear(it)
            true
        }
    }

    data class Replacement(
        val request: RequestBuilder<Drawable>,
        val where: SpanTarget
    )

    data class SpanTarget(
        val start: Int,
        val end: Int,
        val width: Int,
        val height: Int,
        val flags: Int,
        val verticalAlignment: Int,
    )

    inner class Target(private val s: SpanTarget) : CustomTarget<Drawable>(s.width, s.height) {
        private var span: ImageSpan? = null

        override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
            resource.setBounds(0, 0, s.width, s.height)
            spannable.removeSpan(span)
            span = ImageSpan(resource, s.verticalAlignment)
            spannable.setSpan(span, s.start, s.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            view.text = spannable
        }

        override fun onLoadCleared(placeholder: Drawable?) {
            spannable.removeSpan(span)
            if (placeholder != null) {
                placeholder.setBounds(0, 0, s.width, s.height)
                span = ImageSpan(placeholder, s.verticalAlignment)
                spannable.setSpan(span, s.start, s.end, s.flags)
            }
            view.text = spannable
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
    ":glimfacepalm:" -> R.drawable.ic_emote_glimfacepalm
    ":glimfam:" -> R.drawable.ic_emote_glimfam
    ":glimheart:" -> R.drawable.ic_emote_glimheart
    ":glimhype:" -> R.drawable.ic_emote_glimhype
    ":glimlol:" -> R.drawable.ic_emote_glimlol
    ":glimlove:" -> R.drawable.ic_emote_glimlove
    ":glimsad:" -> R.drawable.ic_emote_glimsad
    ":glimsleepy:" -> R.drawable.ic_emote_glimsleepy
    ":glimsmile:" -> R.drawable.ic_emote_glimsmile
    ":glimspace:" -> R.drawable.ic_emote_glimspace
    ":glimtongue:" -> R.drawable.ic_emote_glimtongue
    ":glimthink:" -> R.drawable.ic_emote_glimthink
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
