package tv.glimesh.ui.home

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import kotlinx.serialization.Serializable
import tv.glimesh.data.model.ChannelId
import tv.glimesh.data.model.StreamId
import tv.glimesh.databinding.FragmentFollowingBinding
import tv.glimesh.ui.ChannelAdapter
import tv.glimesh.ui.channel.ChannelActivity

class HomeFragment : Fragment() {

    private lateinit var homeViewModel: HomeViewModel
    private var _binding: FragmentFollowingBinding? = null

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

        _binding = FragmentFollowingBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val followingAdapter = ChannelAdapter { channel -> adapterOnClick(channel) }
        homeViewModel.channels.observe(viewLifecycleOwner, Observer {
            followingAdapter.submitList(it)
        })

        val recyclerView = binding.recyclerView
        recyclerView.adapter = followingAdapter

        homeViewModel.fetch()

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
                StreamId(channel.streamId?.toLong()!!)
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
