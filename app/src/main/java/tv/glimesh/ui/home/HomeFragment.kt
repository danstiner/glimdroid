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
import kotlinx.serialization.Serializable
import okhttp3.internal.toImmutableList
import tv.glimesh.data.model.ChannelId
import tv.glimesh.data.model.StreamId
import tv.glimesh.databinding.FragmentHomeBinding
import tv.glimesh.ui.ChannelAdapter
import tv.glimesh.ui.categories.CategoriesViewModel
import tv.glimesh.ui.categories.CategoriesViewModelFactory
import tv.glimesh.ui.channel.ChannelActivity
import tv.glimesh.ui.featured.FeaturedViewModel
import tv.glimesh.ui.featured.FeaturedViewModelFactory

@RequiresApi(Build.VERSION_CODES.O)
class HomeFragment : Fragment() {

    private lateinit var homeViewModel: HomeViewModel
    private lateinit var featuredViewModel: FeaturedViewModel
    private lateinit var categoriesViewModel: CategoriesViewModel
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
        featuredViewModel = ViewModelProvider(
            this,
            FeaturedViewModelFactory(requireContext())
        )[FeaturedViewModel::class.java]
        categoriesViewModel = ViewModelProvider(
            this,
            CategoriesViewModelFactory(requireContext())
        )[CategoriesViewModel::class.java]

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        var followingChannels = listOf<Channel>()
        var featuredChannels = listOf<Channel>()
        var liveChannels = listOf<Channel>()

        val channelAdapter = ChannelAdapter { channel -> adapterOnClick(channel) }

        homeViewModel.channels.observe(viewLifecycleOwner, Observer {
            followingChannels = it
            channelAdapter.submitList(
                concatChannels(
                    followingChannels,
                    featuredChannels,
                    liveChannels
                )
            )
        })

        featuredViewModel.channels.observe(viewLifecycleOwner, Observer {
            featuredChannels = it
            channelAdapter.submitList(
                concatChannels(
                    followingChannels,
                    featuredChannels,
                    liveChannels
                )
            )
        })

        categoriesViewModel.channels.observe(viewLifecycleOwner, Observer {
            liveChannels = it
            channelAdapter.submitList(
                concatChannels(
                    followingChannels,
                    featuredChannels,
                    liveChannels
                )
            )
        })

        val recyclerView = binding.recyclerView
        recyclerView.adapter = channelAdapter

        homeViewModel.fetch()
        featuredViewModel.fetch()
        categoriesViewModel.fetch()

        return root
    }

    private fun concatChannels(
        followingChannels: List<Channel>,
        featuredChannels: List<Channel>,
        liveChannels: List<Channel>
    ): List<Channel> {
        var addedIds = mutableSetOf<String>()
        var channels = mutableListOf<Channel>()
        for (channel in followingChannels) {
            if (channel.id !in addedIds) {
                channels.add(channel)
                addedIds.add(channel.id)
            }
        }
        for (channel in featuredChannels) {
            if (channel.id !in addedIds) {
                channels.add(channel)
                addedIds.add(channel.id)
            }
        }
        for (channel in liveChannels) {
            if (channel.id !in addedIds) {
                channels.add(channel)
                addedIds.add(channel.id)
            }
        }
        return channels.toImmutableList()
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
