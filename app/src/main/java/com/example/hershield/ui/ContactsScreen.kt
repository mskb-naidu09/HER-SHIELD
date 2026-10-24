package com.example.hershield.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.hershield.data.Contact
import com.example.hershield.data.ContactStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val contactStore = remember { ContactStore(context) }
    var contacts by remember { mutableStateOf(contactStore.load()) }

    var showAddDialog by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf("") }
    var phoneInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Emergency Contacts") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("< Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Text("+")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (contacts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No contacts added yet. Tap + to add.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(contacts) { contact ->
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(contact.name, style = MaterialTheme.typography.titleMedium)
                                    Text(contact.phone, style = MaterialTheme.typography.bodyMedium)
                                }
                                TextButton(onClick = {
                                    val updated = contacts.filter { it != contact }
                                    contacts = updated
                                    contactStore.save(updated)
                                }) {
                                    Text("Delete", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            if (showAddDialog) {
                AlertDialog(
                    onDismissRequest = { showAddDialog = false },
                    title = { Text("Add Contact") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = nameInput,
                                onValueChange = { nameInput = it },
                                label = { Text("Name") }
                            )
                            OutlinedTextField(
                                value = phoneInput,
                                onValueChange = { phoneInput = it },
                                label = { Text("Phone") }
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            if (nameInput.isNotBlank() && phoneInput.isNotBlank()) {
                                val newContact = Contact(
                                    name = nameInput,
                                    phone = phoneInput,
                                    latitude = 0.0,
                                    longitude = 0.0,
                                    priority = contacts.size + 1
                                )
                                val updated = contacts + newContact
                                contacts = updated
                                contactStore.save(updated)
                                nameInput = ""
                                phoneInput = ""
                                showAddDialog = false
                            }
                        }) {
                            Text("Add")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAddDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}
