package com.example.hershield.ui

import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.example.hershield.data.ContactStore
import com.example.hershield.sms.SmsSender

class EmergencyAlertActivity : ComponentActivity() {
    private val TAG = "HER_SHIELD"
    private var ringtone: Ringtone? = null
    private val handler = Handler(Looper.getMainLooper())
    private var stopAlarmRunnable: Runnable? = null
    private var isAlarmRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val from = intent.getStringExtra("from") ?: "Unknown"
        val body = intent.getStringExtra("body") ?: "Emergency Alert"

        startAlarm()

        setContent {
            MaterialTheme {
                EmergencyAlertScreen(
                    from = from,
                    body = body,
                    onAcknowledge = {
                        stopAlarm()
                        val userName = ContactStore(this).userName.ifBlank { "Contact" }
                        val ackMessage = "${SmsSender.ACK_TAG} Acknowledged by $userName"
                        Log.d(TAG, "SOS_FLOW: Sending acknowledgement SMS to $from")
                        SmsSender.send(
                            this,
                            from,
                            ackMessage
                        ) { success ->
                            if (success) {
                                Log.d(TAG, "SOS_FLOW: ACK SMS sent successfully")
                            } else {
                                Log.e(TAG, "SOS_FLOW: ACK SMS send failed")
                            }
                        }
                        finish()
                    },
                    onDismiss = {
                        stopAlarm()
                        Log.d(TAG, "SOS_FLOW: Alert dismissed by recipient without ACK")
                        finish()
                    }
                )
            }
        }
    }

    private fun startAlarm() {
        if (isAlarmRunning) return
        try {
            val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            ringtone = RingtoneManager.getRingtone(applicationContext, alertUri).apply {
                this.audioAttributes = audioAttributes
                play()
            }
            isAlarmRunning = true
            Log.d(TAG, "SOS_FLOW: Alarm started")

            // Configurable 10 to 15 seconds alarm duration (using 10s default, extendable up to 15s)
            val alarmDurationMs = 10_000L
            stopAlarmRunnable = Runnable {
                stopAlarm()
            }
            handler.postDelayed(stopAlarmRunnable!!, alarmDurationMs)
        } catch (e: Exception) {
            Log.e(TAG, "SOS_FLOW: Playback failure starting alarm: ${e.localizedMessage}")
            isAlarmRunning = false
        }
    }

    private fun stopAlarm() {
        if (!isAlarmRunning) return
        try {
            stopAlarmRunnable?.let { handler.removeCallbacks(it) }
            stopAlarmRunnable = null
            if (ringtone?.isPlaying == true) {
                ringtone?.stop()
                Log.d(TAG, "SOS_FLOW: Alarm stopped")
            }
            ringtone = null
            isAlarmRunning = false
        } catch (e: Exception) {
            Log.e(TAG, "SOS_FLOW: Playback failure stopping alarm: ${e.localizedMessage}")
            isAlarmRunning = false
        }
    }

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }
}
