package com.example.hershield.sms

import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.hershield.sos.SosManager
import com.example.hershield.ui.EmergencyAlertActivity

class SmsReceiver : BroadcastReceiver() {

    private val TAG = "HER_SHIELD"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val messages =
            Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return

        val from =
            messages.firstOrNull()?.originatingAddress ?: return

        val body =
            messages.joinToString("") { it.messageBody ?: "" }

        when {
            body.startsWith(SmsSender.EMERGENCY_TAG) -> {
                Log.d(TAG, "SOS_FLOW: Emergency SMS received from $from")
                showEmergency(context, from, body)
            }

            body.startsWith(SmsSender.ACK_TAG) -> {
                Log.d(TAG, "SOS_FLOW: Acknowledgement SMS received from $from: $body")
                SosManager.onAckReceived(from, body)
            }
        }
    }

    private fun showEmergency(
        context: Context,
        from: String,
        body: String
    ) {
        val alertIntent =
            Intent(context, EmergencyAlertActivity::class.java).apply {
                putExtra("from", from)
                putExtra("body", body)

                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }

        val pending =
            PendingIntent.getActivity(
                context,
                0,
                alertIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )

        val nm =
            context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

        // Using a distinct channel ID to ensure fresh sound & importance settings on test devices
        val channelId = "hershield_emergency_alerts_v2"
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(
            channelId,
            "HER SHIELD Emergency Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "High priority emergency alerts with repeating alarm sound"
            enableVibration(true)
            setSound(alarmUri, audioAttributes)
        }
        nm.createNotificationChannel(channel)

        // Also play immediate ringtone/alarm beep from broadcast receiver if possible
        try {
            val ringtone = RingtoneManager.getRingtone(context, alarmUri)
            ringtone?.audioAttributes = audioAttributes
            ringtone?.play()
            Log.d(TAG, "SOS_FLOW: Alarm started from SmsReceiver broadcast")
        } catch (e: Exception) {
            Log.e(TAG, "SOS_FLOW: Playback failure in SmsReceiver: ${e.localizedMessage}")
        }

        val notification =
            NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_dialog_alert)
                .setContentTitle("EMERGENCY - someone needs help")
                .setContentText("Tap to acknowledge")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setSound(alarmUri)
                .setVibrate(longArrayOf(0, 500, 500, 500))
                .setFullScreenIntent(pending, true)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()

        nm.notify(2002, notification)

        try {
            context.startActivity(alertIntent)
        } catch (e: Exception) {
            Log.w(TAG, "Background activity start restricted or failed: ${e.localizedMessage}")
        }
    }
}
