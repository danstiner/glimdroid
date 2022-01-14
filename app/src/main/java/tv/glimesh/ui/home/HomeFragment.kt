package tv.glimesh.ui.home

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import tv.glimesh.data.model.ChannelId
import tv.glimesh.data.model.StreamId
import tv.glimesh.databinding.FragmentHomeBinding
import tv.glimesh.ui.channel.ChannelActivity

@RequiresApi(Build.VERSION_CODES.O)
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

        homeViewModel.items.observe(viewLifecycleOwner, Observer {
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

    /* Opens channel when RecyclerView item is clicked. */
    private fun adapterOnClick(channel: Channel) {
        Log.d(
            "HomeFragment",
            "Starting stream activity; channel_id:${channel.id}, stream_id:${channel.streamId}"
        )
        startActivity(
            ChannelActivity.intent(
                requireContext(),
                ChannelId(channel.id.toLong()),
                StreamId(channel.streamId?.toLong()!!),
                channel.streamThumbnailUrl?.let { Uri.parse(it) }
            )
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

@Serializable
data class Channel(
    val id: String,
    val title: String,
    val streamerDisplayName: String,
    val streamerAvatarUrl: String?,
    val streamId: String?,
    val streamThumbnailUrl: String?
)
