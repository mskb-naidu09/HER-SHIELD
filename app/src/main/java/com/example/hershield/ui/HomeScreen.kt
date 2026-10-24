package com.example.hershield.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hershield.data.ContactStore
import com.example.hershield.sos.SosManager
import com.example.hershield.sos.SosState
import com.example.hershield.voice.VoiceTriggerManager

@Composable
fun HomeScreen(onOpenContacts: () -> Unit) {
    val context = LocalContext.current
    val contactStore = remember { ContactStore(context) }

    var userName by remember { mutableStateOf(contactStore.userName) }
    var voiceTriggerEnabled by remember { mutableStateOf(false) }

    val sosState by SosManager.state.collectAsState()

    val voiceTriggerManager = remember {
        VoiceTriggerManager(context) {
            SosManager.start(context)
        }
    }

    DisposableEffect(voiceTriggerEnabled) {
        if (voiceTriggerEnabled) {
            voiceTriggerManager.start()
        } else {
            voiceTriggerManager.stop()
        }
        onDispose {
            voiceTriggerManager.stop()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 1. HER SHIELD title
        Text(
            text = "HER SHIELD",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 2. User name input
        OutlinedTextField(
            value = userName,
            onValueChange = {
                userName = it
                contactStore.userName = it
            },
            label = { Text("Your Name") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))

        // 3. Large red SOS button
        val isSosActive = sosState is SosState.GettingLocation || 
                sosState is SosState.WaitingForAck || 
                sosState is SosState.Escalating || 
                sosState is SosState.SendingSms

        Button(
            onClick = {
                SosManager.start(context)
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
            modifier = Modifier
                .size(160.dp)
        ) {
            Text(
                text = "SOS",
                color = Color.White,
                fontSize = 32.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 4. SOS status message
        val statusText = when (val state = sosState) {
            is SosState.Idle -> "Status: Safe / Ready"
            is SosState.GettingLocation -> "Status: Acquiring location..."
            is SosState.SendingSms -> "Sending emergency SMS…"
            is SosState.WaitingForAck -> "Waiting for acknowledgement from ${state.contactName}…"
            is SosState.Escalating -> state.message
            is SosState.Completed -> "Acknowledgement received from ${state.by}"
            is SosState.Failed -> state.reason.ifBlank { "All contacts attempted" }
            is SosState.Cancelled -> "SOS cancelled"
        }

        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 5. Cancel SOS button when SOS is active
        if (isSosActive) {
            OutlinedButton(
                onClick = { SosManager.cancel() },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
            ) {
                Text("Cancel SOS")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 6. Voice trigger switch for the word "HELP"
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "Voice Trigger ('HELP')")
            Switch(
                checked = voiceTriggerEnabled,
                onCheckedChange = { voiceTriggerEnabled = it }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 7. Emergency Contacts button
        Button(
            onClick = onOpenContacts,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Emergency Contacts")
        }
    }
}
