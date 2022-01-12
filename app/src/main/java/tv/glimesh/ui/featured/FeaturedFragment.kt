package tv.glimesh.ui.featured

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import tv.glimesh.data.model.ChannelId
import tv.glimesh.data.model.StreamId
import tv.glimesh.databinding.FragmentFeaturedBinding
import tv.glimesh.ui.ChannelAdapter
import tv.glimesh.ui.channel.ChannelActivity
import tv.glimesh.ui.home.Channel

class FeaturedFragment : Fragment() {

    private val TAG = "FeaturedFragment"

    private lateinit var viewModel: FeaturedViewModel
    private var _binding: FragmentFeaturedBinding? = null

    // This property is only valid between onCreateView and onDestroyView
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        viewModel = ViewModelProvider(
            this,
            FeaturedViewModelFactory(requireContext())
        )[FeaturedViewModel::class.java]

        _binding = FragmentFeaturedBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val channelAdapter = ChannelAdapter { channel -> adapterOnClick(channel) }
        viewModel.channels.observe(viewLifecycleOwner, {
            channelAdapter.submitList(it)
        })

        val recyclerView = binding.recyclerView
        recyclerView.adapter = channelAdapter

        viewModel.fetch()

        return root
    }

    /* Opens channel when RecyclerView item is clicked. */
    private fun adapterOnClick(channel: Channel) {
        Log.d(
            TAG,
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