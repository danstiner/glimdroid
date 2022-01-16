package tv.glimesh.android.ui.channel

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.text.Html
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ImageSpan
import android.util.Log
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
import com.bumptech.glide.request.Request
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.target.SizeReadyCallback
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import tv.glimesh.android.R
import tv.glimesh.android.data.ChatMessage
import java.lang.ref.WeakReference
import java.net.URL

class ChatAdapter :
    ListAdapter<ChatMessage, ChatAdapter.ChatViewHolder>(ChatDiffCallback) {

    class ChatViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(R.id.text)

        fun bind(chat: ChatMessage) {
            Log.d("ChatAdapter", chat.message)
            textView.text = buildSpannedString {
                append("  ")
                bold { append(chat.displayname) }
                append(": ")
                append(chat.message)
            }

            if (chat.avatarUrl != null) {
                Glide
                    .with(itemView)
                    .load(URL(chat.avatarUrl))
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
                            res: Drawable,
                            transition: Transition<in Drawable>?
                        ) {
                            res.setBounds(0, 0, 38, 38)
                            textView.text = buildSpannedString {
                                appendSpan("i", ImageSpan(res), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
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

inline fun SpannableStringBuilder.appendSpan(
    text: String, what: Any, flags: Int
): SpannableStringBuilder {
    val start = length
    append(text)
    setSpan(what, start, length, flags)
    return this
}


class GlideImageGetter(
    textView: TextView,
    private val width: Int,
    private val height: Int,
    densityAware: Boolean = false,
    private val imagesHandler: HtmlImagesHandler? = null
) : Html.ImageGetter {
    private val container: WeakReference<TextView> = WeakReference(textView)
    private var density = 1.0f

    init {
        if (densityAware) {
            container.get()?.let {
                density = it.resources.displayMetrics.density
            }
        }
    }

    override fun getDrawable(source: String): Drawable {
        imagesHandler?.addImage(source)

        val drawable = BitmapDrawablePlaceholder()

        // Load Image to the Drawable
        container.get()?.apply {
            post {
                Glide.with(context)
                    .asBitmap()
                    .load(source)
                    .fitCenter()
                    .optionalCircleCrop()
                    .into(drawable)
            }
        }

        return drawable
    }

    private inner class BitmapDrawablePlaceholder : BitmapDrawable(
        container.get()?.resources,
        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    ), Target<Bitmap> {
        private var drawable: Drawable? = null
            set(value) {
                field = value
                value?.let { drawable ->
                    val drawableWidth = (width * density).toInt()
                    val drawableHeight = (height * density).toInt()
                    val maxHeight = container.get()!!.measuredHeight
                    if (drawableHeight > maxHeight) {
                        val calculatedWidth = maxHeight * drawableHeight / drawableWidth
                        drawable.setBounds(0, 0, calculatedWidth, maxHeight)
                        setBounds(0, 0, calculatedWidth, maxHeight)
                    } else {
                        drawable.setBounds(0, 0, drawableWidth, drawableHeight)
                        setBounds(0, 0, drawableWidth, drawableHeight)
                    }
                    container.get()?.text = container.get()?.text
                }
            }

        override fun draw(canvas: Canvas) {
            drawable?.draw(canvas)
        }

        override fun onLoadStarted(placeholderDrawable: Drawable?) {
            placeholderDrawable?.let {
                drawable = it
            }
        }

        override fun onLoadFailed(errorDrawable: Drawable?) {
            errorDrawable?.let {
                drawable = it
            }
        }

        override fun onResourceReady(bitmap: Bitmap, transition: Transition<in Bitmap>?) {
            drawable = BitmapDrawable(container.get()!!.resources, bitmap)
        }

        override fun onLoadCleared(placeholderDrawable: Drawable?) {
            placeholderDrawable?.let {
                drawable = it
            }
        }

        override fun getSize(sizeReadyCallback: SizeReadyCallback) {
            sizeReadyCallback.onSizeReady(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL)
        }

        override fun removeCallback(cb: SizeReadyCallback) {}
        override fun setRequest(request: Request?) {}
        override fun getRequest(): Request? {
            return null
        }

        override fun onStart() {}
        override fun onStop() {}
        override fun onDestroy() {}
    }

    interface HtmlImagesHandler {
        fun addImage(uri: String?)
    }
}