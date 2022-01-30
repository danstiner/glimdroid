package com.danielstiner.glimdroid.ui

import android.net.Uri
import com.danielstiner.glimdroid.BuildConfig

object Urls {
    val BASE_URI = Uri.parse(BuildConfig.GLIMESH_BASE_URL)

    fun userProfile(username: String): Uri = BASE_URI.buildUpon()
        .appendPath(username)
        .appendPath("profile").build()
}