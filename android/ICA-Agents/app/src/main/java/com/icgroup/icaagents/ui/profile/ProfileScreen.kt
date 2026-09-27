package com.icgroup.icaagents.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.icgroup.icaagents.data.AgentRepository
import com.icgroup.icaagents.data.ApiResult
import com.icgroup.icaagents.data.ProfileResponse
import kotlinx.coroutines.launch

class ProfileViewModel(private val repo: AgentRepository) : ViewModel() {
    var profile by mutableStateOf<ProfileResponse?>(null); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var loggedOut by mutableStateOf(false); private set

    fun load() {
        loading = true; error = null
        viewModelScope.launch {
            when (val r = repo.myProfile()) {
                is ApiResult.Success -> profile = r.data
                is ApiResult.Failure -> error = r.message
            }
            loading = false
        }
    }

    fun logout() {
        repo.logout()
        loggedOut = true
    }
}

@Composable
fun ProfileScreen(vm: ProfileViewModel, onLoggedOut: () -> Unit) {
    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(vm.loggedOut) { if (vm.loggedOut) onLoggedOut() }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("My Profile", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        when {
            vm.loading -> CircularProgressIndicator()
            vm.error != null -> Text(vm.error!!, color = MaterialTheme.colorScheme.error)
            vm.profile != null -> {
                val p = vm.profile!!
                InfoRow("Name", p.name)
                InfoRow("Username", p.username)
                InfoRow("Role", p.role)
                InfoRow("Email", p.email)
                InfoRow("Mobile", p.mobile)
                Spacer(Modifier.height(12.dp))
                Text("KYC (masked)", style = MaterialTheme.typography.titleMedium)
                InfoRow("PAN", p.pan_masked)
                InfoRow("Aadhaar", p.aadhaar_masked)
                InfoRow("Bank account", p.bank_account_masked)
                InfoRow("IFSC", p.bank_ifsc)
                InfoRow("Account holder", p.bank_holder_name)
                Text(
                    "Full PAN/Aadhaar/bank numbers are never shown in the app.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = vm::logout) { Text("Log out") }
    }
}

@Composable
private fun InfoRow(label: String, value: String?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("$label: ", style = MaterialTheme.typography.bodyMedium)
        Text(value ?: "—", style = MaterialTheme.typography.bodyMedium)
    }
}
