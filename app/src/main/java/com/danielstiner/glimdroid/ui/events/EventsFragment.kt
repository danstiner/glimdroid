package com.danielstiner.glimdroid.ui.events

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import com.danielstiner.glimdroid.BuildConfig
import com.danielstiner.glimdroid.databinding.FragmentEventsBinding

class EventsFragment : Fragment() {

    private var _binding: FragmentEventsBinding? = null

    // This property is only valid between onCreateView and onDestroyView
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentEventsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        binding.webview.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                Log.d("Webview", url)
                return when (url) {
                    BASE_URL -> true
                    else -> false
                }
            }
        }

        binding.webview.loadUrl(EVENTS_URL)

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val BASE_URL = BuildConfig.GLIMESH_BASE_URL
        private val EVENTS_URL =
            Uri.parse(BuildConfig.GLIMESH_BASE_URL).buildUpon()
                .appendPath("events")
                .build().toString()
    }
}
