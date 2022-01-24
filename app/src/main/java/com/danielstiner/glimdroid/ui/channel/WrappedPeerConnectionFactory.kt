package com.danielstiner.glimdroid.ui.channel

import android.util.Log
import org.webrtc.*

class WrappedPeerConnectionFactory(private val factory: PeerConnectionFactory) {

    private val TAG = "WPeerConnectionFactory"

    fun createPeerConnection(
        configuration: PeerConnection.RTCConfiguration,
        onIceCandidate: (candidate: IceCandidate) -> Unit,
        onAddStream: (stream: MediaStream) -> Unit,
    ): WrappedPeerConnection {
        return WrappedPeerConnection(
            factory.createPeerConnection(
                configuration,
                object : PeerConnection.Observer {

                    override fun onSignalingChange(state: PeerConnection.SignalingState) {
                        Log.d(TAG, "onSignalingChange: $state")
                    }

                    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                        Log.d(TAG, "onIceConnectionReceivingChange: $state")
                    }

                    override fun onIceConnectionReceivingChange(receiving: Boolean) {
                        Log.d(TAG, "onIceConnectionReceivingChange: $receiving")
                    }

                    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
                        Log.d(TAG, "onIceGatheringChange: $state")
                    }

                    override fun onIceCandidate(candidate: IceCandidate) {
                        onIceCandidate(candidate)
                    }

                    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {
                        Log.d(TAG, "onIceCandidatesRemoved: $candidates")
                    }

                    override fun onAddStream(stream: MediaStream) {
                        Log.d(TAG, "onAddStream: $stream")
                        onAddStream(stream)
                    }

                    override fun onRemoveStream(stream: MediaStream?) {
                        Log.d(TAG, "onRemoveStream: $stream")
                    }

                    override fun onDataChannel(channel: DataChannel?) {
                        Log.d(TAG, "onDataChannel: $channel")
                    }

                    override fun onRenegotiationNeeded() {
                        Log.d(TAG, "onRenegotiationNeeded")
                    }

                })!!
        )
    }
}