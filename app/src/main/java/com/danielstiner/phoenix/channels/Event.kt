package com.danielstiner.phoenix.channels

data class Event(var name: String) {
    companion object {
        val JOIN = Event("phx_join")
        val REPLY = Event("phx_reply")
        val ERROR = Event("phx_error")
        val LEAVE = Event("phx_leave")
        val CLOSE = Event("phx_close")
    }
}
