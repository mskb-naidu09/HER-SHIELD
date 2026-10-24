package com.example.hershield.sos

sealed class SosState {

    object Idle : SosState()

    object GettingLocation : SosState()

    object SendingSms : SosState()

    data class WaitingForAck(
        val contactName: String,
        val index: Int,
        val total: Int
    ) : SosState()

    data class Escalating(
        val message: String
    ) : SosState()

    data class Completed(
        val by: String
    ) : SosState()

    data class Failed(
        val reason: String
    ) : SosState()

    object Cancelled : SosState()
}
