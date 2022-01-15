package tv.glimesh.phoenix.channels

import kotlinx.serialization.json.JsonObject

open class Reply {
    class Ok(val json: JsonObject) : Reply() {

    }

    class Err : Reply() {

    }
}
