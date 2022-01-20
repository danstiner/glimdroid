package com.danielstiner.glimdroid.ui.home

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.danielstiner.glimdroid.data.model.Channel
import com.danielstiner.glimdroid.databinding.FragmentHomeBinding
import com.danielstiner.glimdroid.ui.channel.ChannelActivity
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private val TAG = "HomeFragment"

    private lateinit var homeViewModel: HomeViewModel

    private var _binding: FragmentHomeBinding? = null

    // This property is only valid between onCreateView and onDestroyView
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        homeViewModel = ViewModelProvider(
            this,
            HomeViewModelFactory(requireContext())
        )[HomeViewModel::class.java]

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val channelAdapter = SectionedChannelAdapter { channel -> adapterOnClick(channel) }

        homeViewModel.items.observe(viewLifecycleOwner, {
            channelAdapter.submitList(it)
        })

        val recyclerView = binding.recyclerView
        recyclerView.adapter = channelAdapter

        binding.swipeRefresh.setOnRefreshListener {
            Log.d(TAG, "onRefresh called from SwipeRefresh")
            lifecycleScope.launch {
                homeViewModel.fetchAsync()
                binding.swipeRefresh.isRefreshing = false
            }
        }

        lifecycleScope.launch { homeViewModel.fetchAsync() }

        return root
    }

    override fun onStart() {
        super.onStart()
    }

    /* Opens channel when RecyclerView item is clicked. */
    private fun adapterOnClick(channel: Channel) {
        Log.d(
            "HomeFragment",
            "Starting stream activity; ${channel.id}, ${channel.stream?.id}"
        )
        startActivity(
            ChannelActivity.intent(
                requireContext(),
                channel.id,
                channel.stream!!.id,
                channel.stream.thumbnailUrl?.let { Uri.parse(it) },
            )
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
