package com.danielstiner.glimdroid.data.model

import android.net.Uri
import java.net.URL

public fun URL.toUri(): Uri = Uri.parse(this.toString())
