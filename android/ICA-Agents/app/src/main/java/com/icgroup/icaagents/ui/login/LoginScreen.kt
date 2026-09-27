package com.icgroup.icaagents.ui.login

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.icgroup.icaagents.data.AgentRepository
import com.icgroup.icaagents.data.ApiResult
import kotlinx.coroutines.launch

sealed class LoginStep { object Credentials : LoginStep(); object Otp : LoginStep() }

class LoginViewModel(private val repo: AgentRepository) : ViewModel() {
    var step by mutableStateOf<LoginStep>(LoginStep.Credentials); private set
    var username by mutableStateOf(""); var password by mutableStateOf(""); var otp by mutableStateOf("")
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var loggedIn by mutableStateOf(false); private set

    fun submitCredentials() {
        if (username.isBlank() || password.isBlank()) { error = "Enter username and password"; return }
        error = null; loading = true
        viewModelScope.launch {
            when (val r = repo.login(username.trim().lowercase(), password)) {
                is ApiResult.Success -> step = LoginStep.Otp
                is ApiResult.Failure -> error = r.message
            }
            loading = false
        }
    }

    fun submitOtp() {
        if (otp.isBlank()) { error = "Enter the OTP sent to your email"; return }
        error = null; loading = true
        viewModelScope.launch {
            when (val r = repo.verifyOtp(username.trim().lowercase(), otp.trim())) {
                is ApiResult.Success -> loggedIn = true
                is ApiResult.Failure -> error = r.message
            }
            loading = false
        }
    }
}

@Composable
fun LoginScreen(vm: LoginViewModel, onLoggedIn: () -> Unit, onGoToRegister: () -> Unit) {
    LaunchedEffect(vm.loggedIn) { if (vm.loggedIn) onLoggedIn() }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("ICA Agents", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text("Sign in to view your leads, invoices and payments", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))

        when (vm.step) {
            LoginStep.Credentials -> {
                OutlinedTextField(
                    value = vm.username, onValueChange = { vm.username = it },
                    label = { Text("Username") }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = vm.password, onValueChange = { vm.password = it },
                    label = { Text("Password") }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, visualTransformation = PasswordVisualTransformation()
                )
                Spacer(Modifier.height(20.dp))
                Button(onClick = vm::submitCredentials, enabled = !vm.loading, modifier = Modifier.fillMaxWidth()) {
                    Text(if (vm.loading) "Sending OTP..." else "Continue")
                }
            }
            LoginStep.Otp -> {
                Text("Enter the OTP sent to your registered email")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = vm.otp, onValueChange = { vm.otp = it },
                    label = { Text("6-digit OTP") }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                Spacer(Modifier.height(20.dp))
                Button(onClick = vm::submitOtp, enabled = !vm.loading, modifier = Modifier.fillMaxWidth()) {
                    Text(if (vm.loading) "Verifying..." else "Verify & Sign in")
                }
            }
        }

        vm.error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            Text("New agent? ")
            Text("Register", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickableText(onGoToRegister))
        }
    }
}

// small helper to avoid pulling in extra imports for a single clickable text
private fun Modifier.clickableText(onClick: () -> Unit): Modifier =
    this.then(androidx.compose.foundation.clickable(onClick = onClick))
