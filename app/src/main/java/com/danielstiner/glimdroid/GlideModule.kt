package com.danielstiner.glimdroid

import android.content.Context
import android.util.Log
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.engine.cache.ExternalPreferredCacheDiskCacheFactory
import com.bumptech.glide.load.engine.cache.MemorySizeCalculator
import com.bumptech.glide.module.AppGlideModule


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

}
