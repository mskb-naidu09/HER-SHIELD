package com.example.hershield.sos

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.hershield.data.Contact
import com.example.hershield.data.ContactStore
import com.example.hershield.location.LocationHelper
import com.example.hershield.sms.SmsSender
import com.example.hershield.util.DistanceCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object SosManager {

    private const val TAG = "HER_SHIELD"
    const val ACK_TIMEOUT_MS = 15_000L

    private val _state = MutableStateFlow<SosState>(SosState.Idle)
    val state: StateFlow<SosState> = _state

    private val handler = Handler(Looper.getMainLooper())

    private var queue: List<Contact> = emptyList()
    private var index = 0
    private var message = ""
    private var appContext: Context? = null
    private var locationCancel: (() -> Unit)? = null
    private var currentSosEventId: Long = 0L

    private fun isActive() =
        _state.value is SosState.GettingLocation ||
                _state.value is SosState.WaitingForAck ||
                _state.value is SosState.Escalating ||
                _state.value is SosState.SendingSms

    fun start(context: Context) {
        val eventId = System.currentTimeMillis()
        currentSosEventId = eventId
        Log.d(TAG, "SOS started with event ID: $eventId")

        if (isActive()) {
            Log.d(TAG, "SOS is already active. Ignoring start request.")
            return
        }

        val ctx = context.applicationContext
        appContext = ctx

        val perm = ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.SEND_SMS
        )
        if (perm != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "SMS send failed: permission missing")
            Toast.makeText(ctx, "SMS permission missing. Please grant SEND_SMS in settings.", Toast.LENGTH_LONG).show()
            _state.value = SosState.Failed("SMS permission missing")
            return
        }

        val store = ContactStore(ctx)
        val contacts = store.load()
        Log.d(TAG, "Contacts loaded: ${contacts.size}")

        if (contacts.isEmpty()) {
            Log.e(TAG, "No emergency contact configured")
            Toast.makeText(ctx, "No emergency contact configured", Toast.LENGTH_LONG).show()
            _state.value =
                SosState.Failed("Add at least one emergency contact first.")
            return
        }

        _state.value = SosState.GettingLocation
        Log.d(TAG, "Location start: acquiring location...")

        locationCancel = LocationHelper.getLocation(ctx) { loc ->
            if (currentSosEventId != eventId) {
                Log.d(TAG, "Stale location callback received for old event. Ignoring.")
                return@getLocation
            }

            if (_state.value !is SosState.GettingLocation) {
                Log.d(TAG, "State changed while getting location. Aborting location result.")
                return@getLocation
            }

            queue = if (loc != null) {
                Log.d(TAG, "Location success: ${loc.latitude}, ${loc.longitude}. Sorting contacts by distance.")
                contacts.sortedWith(
                    compareBy<Contact> { contact ->
                        if (contact.latitude != 0.0 || contact.longitude != 0.0) {
                            val dist = DistanceCalculator.haversineKm(
                                loc.latitude,
                                loc.longitude,
                                contact.latitude,
                                contact.longitude
                            )
                            Log.d(TAG, "Contact distance - ${contact.name}: ${dist}km")
                            dist
                        } else {
                            Log.d(TAG, "Contact ${contact.name} has no coordinates. Falling back to priority.")
                            Double.MAX_VALUE
                        }
                    }.thenBy { it.priority }
                )
            } else {
                Log.w(TAG, "Location unavailable / timeout. Sorting contacts by priority fallback.")
                contacts.sortedBy { it.priority }
            }

            Log.d(TAG, "Contact ordering: ${queue.joinToString { "${it.name} (Priority ${it.priority})" }}")

            val who = store.userName.ifBlank { "a HER SHIELD user" }
            val where = if (loc != null) {
                "https://maps.google.com/?q=${loc.latitude},${loc.longitude}"
            } else {
                "Location unavailable"
            }

            message =
                "${SmsSender.EMERGENCY_TAG}:$eventId\n" +
                        "Emergency alert from $who.\n" +
                        "Location: $where\n" +
                        "Please respond through HER SHIELD."

            index = 0
            alertCurrent(eventId)
        }
    }

    private fun alertCurrent(eventId: Long) {
        if (currentSosEventId != eventId) {
            Log.d(TAG, "alertCurrent called for stale event ID $eventId. Aborting.")
            return
        }

        val ctx = appContext ?: return

        if (index >= queue.size) {
            Log.e(TAG, "All contacts attempted. Escalation exhausted.")
            _state.value = SosState.Failed("All contacts attempted")
            return
        }

        val contact = queue[index]
        Log.d(TAG, "SMS send attempt to ${contact.name} (${contact.phone}) (index $index)")
        _state.value = SosState.SendingSms

        SmsSender.send(
            ctx,
            contact.phone,
            message
        ) { success ->
            if (currentSosEventId != eventId) return@send

            if (success) {
                Log.d(TAG, "SMS send success for ${contact.name}. Waiting for acknowledgement...")
                _state.value = SosState.WaitingForAck(contact.name, index + 1, queue.size)

                // 15-second acknowledgement timeout
                handler.postDelayed({
                    if (currentSosEventId != eventId) return@postDelayed
                    Log.w(TAG, "Acknowledgement timeout reached for ${contact.name}")
                    _state.value = SosState.Escalating("No acknowledgement — contacting [next contact]…")
                    index++
                    alertCurrent(eventId)
                }, ACK_TIMEOUT_MS)

            } else {
                Log.e(TAG, "SMS send failed for ${contact.name}")
                _state.value = SosState.Escalating("SMS send failed — trying next contact")
                handler.removeCallbacksAndMessages(null)
                handler.postDelayed({
                    if (currentSosEventId != eventId) return@postDelayed
                    index++
                    alertCurrent(eventId)
                }, 1500L) // short delay before trying next contact
            }
        }
    }

    fun onAckReceived(fromPhone: String, messageBody: String = "") {
        Log.d(TAG, "Incoming emergency SMS / ACK received from $fromPhone, body: $messageBody")

        if (_state.value !is SosState.WaitingForAck && _state.value !is SosState.Escalating && _state.value !is SosState.SendingSms) {
            Log.d(TAG, "Not waiting for ACK. Ignoring ACK from $fromPhone")
            return
        }

        // Validate event ID if present in messageBody (format: HER_SHIELD_ACK:eventId ...)
        if (messageBody.contains(":")) {
            val parts = messageBody.split(":")
            if (parts.size >= 2) {
                val idStr = parts[1].trim().takeWhile { it.isDigit() }
                val ackEventId = idStr.toLongOrNull() ?: 0L
                if (ackEventId != 0L && ackEventId != currentSosEventId) {
                    Log.w(TAG, "Stale ACK received for event $ackEventId (current is $currentSosEventId). Ignoring.")
                    return
                }
            }
        }

        val expectedContact = queue.getOrNull(index)
        if (expectedContact != null && !samePhone(expectedContact.phone, fromPhone)) {
            Log.w(TAG, "ACK received from unexpected phone $fromPhone (expected ${expectedContact.phone}). Ignoring.")
            return
        }

        handler.removeCallbacksAndMessages(null)
        val contactName = expectedContact?.name ?: messageBody.ifBlank { fromPhone }
        Log.d(TAG, "Acknowledgment received from $contactName")
        _state.value = SosState.Completed(contactName)
    }

    fun cancel() {
        Log.d(TAG, "SOS cancelled by user")
        currentSosEventId = 0L
        locationCancel?.invoke()
        locationCancel = null
        handler.removeCallbacksAndMessages(null)
        _state.value = SosState.Cancelled
    }

    fun reset() {
        if (!isActive()) {
            _state.value = SosState.Idle
        }
    }

    private fun samePhone(a: String, b: String): Boolean {
        val x = a.filter { it.isDigit() }
        val y = b.filter { it.isDigit() }
        if (x.isEmpty() || y.isEmpty()) return false
        return x.endsWith(y.takeLast(10)) || y.endsWith(x.takeLast(10))
    }
}
