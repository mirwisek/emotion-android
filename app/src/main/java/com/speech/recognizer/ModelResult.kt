package com.speech.recognizer

data class ModelResult(
    val error: String? = null,
    val result: String? = null
)

data class ModelResultErrorBody(
    var error: String? = null,
    var result: StrMsg? = null
)

// When error body is returned by Server it sends and empty {}, rather than a ''
// to handle that on client side, this is a work around, as changing a docker container is tedious task
data class StrMsg(
    val dummy: String? = null
)