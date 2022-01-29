package com.danielstiner.glimdroid

import android.content.Context
import android.graphics.Picture
import android.graphics.drawable.PictureDrawable
import android.util.Log
import androidx.annotation.Nullable
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.engine.cache.ExternalPreferredCacheDiskCacheFactory
import com.bumptech.glide.load.engine.cache.MemorySizeCalculator
import com.bumptech.glide.load.resource.SimpleResource
import com.bumptech.glide.load.resource.transcode.ResourceTranscoder
import com.bumptech.glide.module.AppGlideModule
import com.bumptech.glide.request.target.Target.SIZE_ORIGINAL
import com.caverock.androidsvg.SVG
import com.caverock.androidsvg.SVGParseException
import java.io.IOException
import java.io.InputStream

@Suppress("unused")
@GlideModule
class GlideModule : AppGlideModule() {

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        // Reduce disk cache size from default 250MB, we don't have much to cache
        val diskCacheSizeBytes = 10L * 1024 * 1024 * 100 // 10 MB
        builder.setDiskCache(ExternalPreferredCacheDiskCacheFactory(context, diskCacheSizeBytes))

        // Log memory cache size out of curiosity
        val memsize = MemorySizeCalculator.Builder(context).build()
        Log.v(
            "GlideModule",
            "memsize; cacheSize:${memsize.memoryCacheSize}, arrayPoolSize:${memsize.arrayPoolSizeInBytes}, bitmapPoolSize:${memsize.bitmapPoolSize}"
        )
    }

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        super.registerComponents(context, glide, registry)
        registry
            .register(SVG::class.java, PictureDrawable::class.java, SvgDrawableTranscoder())
            .append(InputStream::class.java, SVG::class.java, SvgDecoder())
    }

    internal class SvgDecoder : ResourceDecoder<InputStream, SVG> {
        override fun handles(source: InputStream, options: Options): Boolean {
            return true
        }

        @Throws(IOException::class)
        override fun decode(
            source: InputStream, width: Int, height: Int, options: Options
        ): Resource<SVG> {
            return try {
                val svg: SVG = SVG.getFromInputStream(source)
                if (width != SIZE_ORIGINAL) {
                    svg.documentWidth = width.toFloat()
                }
                if (height != SIZE_ORIGINAL) {
                    svg.documentHeight = height.toFloat()
                }
                SimpleResource(svg)
            } catch (ex: SVGParseException) {
                throw IOException("Cannot decode SVG", ex)
            }
        }
    }

    internal class SvgDrawableTranscoder : ResourceTranscoder<SVG, PictureDrawable> {
        @Nullable
        override fun transcode(
            toTranscode: Resource<SVG>, options: Options
        ): Resource<PictureDrawable> {
            val svg: SVG = toTranscode.get()
            val picture: Picture = svg.renderToPicture()
            val drawable = PictureDrawable(picture)
            return SimpleResource(drawable)
        }
    }

}
