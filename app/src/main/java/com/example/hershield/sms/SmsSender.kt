package com.example.hershield.sms

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicInteger

object SmsSender {

    private const val TAG = "SmsSender"
    const val EMERGENCY_TAG = "HER_SHIELD_EMERGENCY"
    const val ACK_TAG = "HER_SHIELD_ACK"

    @Suppress("DEPRECATION")
    fun send(
        context: Context,
        phone: String,
        message: String,
        onResult: (Boolean) -> Unit
    ) {
        Log.d(TAG, "SOS_DEBUG: SmsSender.send ENTERED")
        Log.d(TAG, "Attempting to send SMS to phone: $phone")

        val perm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        )

        if (perm != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "SOS_FLOW: SMS_RESULT_FAILED (permission missing)")
            Toast.makeText(context, "SEND_SMS permission missing", Toast.LENGTH_LONG).show()
            onResult(false)
            return
        }

        if (phone.isBlank()) {
            Log.e(TAG, "SOS_FLOW: SMS_RESULT_FAILED (phone blank)")
            Toast.makeText(context, "Emergency contact phone number is invalid", Toast.LENGTH_LONG).show()
            onResult(false)
            return
        }

        try {
            val sms: SmsManager =
                if (Build.VERSION.SDK_INT >= 31) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    SmsManager.getDefault()
                }

            val parts = sms.divideMessage(message)
            val numParts = parts.size
            Log.d(TAG, "Message divided into $numParts parts.")

            val sentAction = "com.example.hershield.SMS_SENT_${System.currentTimeMillis()}"
            val sentIntents = ArrayList<PendingIntent>()

            val successCount = AtomicInteger(0)
            val failCount = AtomicInteger(0)

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    val partIndex = intent.getIntExtra("part_index", 0)
                    val resultCode = resultCode
                    Log.d(TAG, "SMS sent broadcast received for part $partIndex. Result code: $resultCode")

                    if (resultCode == Activity.RESULT_OK) {
                        if (successCount.incrementAndGet() + failCount.get() == numParts) {
                            try {
                                ctx.unregisterReceiver(this)
                            } catch (_: Exception) {}
                            Log.d(TAG, "SOS_FLOW: SMS_RESULT_SUCCESS")
                            Toast.makeText(context, "SMS sent successfully to $phone", Toast.LENGTH_SHORT).show()
                            onResult(true)
                        }
                    } else {
                        Log.e(TAG, "SMS part $partIndex failed with result code: $resultCode")
                        if (failCount.incrementAndGet() == 1) {
                            try {
                                ctx.unregisterReceiver(this)
                            } catch (_: Exception) {}
                            val errorMsg = when (resultCode) {
                                SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "Generic failure"
                                SmsManager.RESULT_ERROR_NO_SERVICE -> "No service"
                                SmsManager.RESULT_ERROR_NULL_PDU -> "Null PDU"
                                SmsManager.RESULT_ERROR_RADIO_OFF -> "Radio off"
                                else -> "Error code $resultCode"
                            }
                            Log.e(TAG, "SOS_FLOW: SMS_RESULT_FAILED ($errorMsg)")
                            Toast.makeText(context, "SMS send failed: $errorMsg", Toast.LENGTH_LONG).show()
                            onResult(false)
                        }
                    }
                }
            }

            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(sentAction),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )

            for (i in 0 until numParts) {
                val intent = Intent(sentAction).apply {
                    putExtra("part_index", i)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    (System.currentTimeMillis() + i).toInt(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                sentIntents.add(pendingIntent)
            }

            sms.sendMultipartTextMessage(
                phone,
                null,
                parts,
                sentIntents,
                null
            )

            Log.d(TAG, "sendMultipartTextMessage invoked successfully for $phone.")
            Toast.makeText(context, "SMS send initiated successfully", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e(TAG, "SOS_FLOW: SMS_RESULT_FAILED (exception: ${e.localizedMessage})", e)
            Toast.makeText(context, "SMS send failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            onResult(false)
        }
    }
}
