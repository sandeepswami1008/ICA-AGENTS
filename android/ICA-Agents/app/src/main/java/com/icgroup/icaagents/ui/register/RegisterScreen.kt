package com.icgroup.icaagents.ui.register

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.icgroup.icaagents.data.AgentRepository
import com.icgroup.icaagents.data.ApiResult
import com.icgroup.icaagents.data.RegisterRequest
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class RegisterViewModel(private val repo: AgentRepository) : ViewModel() {
    var name by mutableStateOf(""); var mobile by mutableStateOf(""); var email by mutableStateOf("")
    var pan by mutableStateOf(""); var aadhaar by mutableStateOf("")
    var bankAccount by mutableStateOf(""); var bankIfsc by mutableStateOf(""); var bankHolder by mutableStateOf("")
    var password by mutableStateOf("")

    var panUri by mutableStateOf<Uri?>(null)
    var aadhaarFrontUri by mutableStateOf<Uri?>(null)
    var aadhaarBackUri by mutableStateOf<Uri?>(null)
    var photoUri by mutableStateOf<Uri?>(null)

    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var submittedUsername by mutableStateOf<String?>(null); private set

    fun submit(cacheDir: File, resolveMime: (Uri) -> String, copyToCache: (Uri, String) -> File) {
        if (name.isBlank() || mobile.isBlank() || email.isBlank() || pan.isBlank() || aadhaar.isBlank() || password.isBlank()) {
            error = "Please fill in all required fields"; return
        }
        if (panUri == null || aadhaarFrontUri == null) {
            error = "PAN and Aadhaar (front) photos are required"; return
        }
        error = null; loading = true
        viewModelScope.launch {
            val req = RegisterRequest(
                name = name.trim(), mobile = mobile.trim(), email = email.trim(),
                pan = pan.trim().uppercase(), aadhaar = aadhaar.trim(),
                bank_account = bankAccount.ifBlank { null }, bank_ifsc = bankIfsc.ifBlank { null },
                bank_holder_name = bankHolder.ifBlank { null }, password = password
            )
            when (val r = repo.register(req)) {
                is ApiResult.Success -> {
                    val username = r.data.username
                    if (username != null) {
                        uploadDoc(username, "pan", panUri!!, copyToCache, resolveMime)
                        uploadDoc(username, "aadhaar_front", aadhaarFrontUri!!, copyToCache, resolveMime)
                        aadhaarBackUri?.let { uploadDoc(username, "aadhaar_back", it, copyToCache, resolveMime) }
                        photoUri?.let { uploadDoc(username, "photo", it, copyToCache, resolveMime) }
                        submittedUsername = username
                    }
                }
                is ApiResult.Failure -> error = r.message
            }
            loading = false
        }
    }

    private suspend fun uploadDoc(username: String, docType: String, uri: Uri, copyToCache: (Uri, String) -> File, resolveMime: (Uri) -> String) {
        val file = copyToCache(uri, docType)
        repo.uploadKyc(username, docType, file, resolveMime(uri))
        // Individual upload failures are non-fatal here (registration already
        // succeeded); admin can request a re-upload during KYC review.
    }
}

@Composable
fun RegisterScreen(vm: RegisterViewModel, onDone: () -> Unit) {
    val context = LocalContext.current

    fun resolveMime(uri: Uri): String = context.contentResolver.getType(uri) ?: "image/jpeg"
    fun copyToCache(uri: Uri, label: String): File {
        val dir = File(context.cacheDir, "kyc").apply { mkdirs() }
        val out = File(dir, "$label-${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(out).use { output -> input.copyTo(output) }
        }
        return out
    }

    val panPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { vm.panUri = it }
    val aadhaarFrontPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { vm.aadhaarFrontUri = it }
    val aadhaarBackPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { vm.aadhaarBackUri = it }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { vm.photoUri = it }

    LaunchedEffect(vm.submittedUsername) { if (vm.submittedUsername != null) onDone() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("Agent Registration", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Your account will be reviewed by an admin before you can sign in.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(16.dp))

        LabeledField("Full name*", vm.name) { vm.name = it }
        LabeledField("Mobile number*", vm.mobile) { vm.mobile = it }
        LabeledField("Email*", vm.email) { vm.email = it }
        LabeledField("PAN*", vm.pan) { vm.pan = it.uppercase() }
        LabeledField("Aadhaar number*", vm.aadhaar) { vm.aadhaar = it }
        LabeledField("Bank account number", vm.bankAccount) { vm.bankAccount = it }
        LabeledField("IFSC code", vm.bankIfsc) { vm.bankIfsc = it.uppercase() }
        LabeledField("Bank account holder name", vm.bankHolder) { vm.bankHolder = it }
        LabeledField("Password* (min 8 chars)", vm.password) { vm.password = it }

        Spacer(Modifier.height(16.dp))
        Text("KYC documents", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        DocPickRow("PAN card photo*", vm.panUri != null) { panPicker.launch("image/*") }
        DocPickRow("Aadhaar (front)*", vm.aadhaarFrontUri != null) { aadhaarFrontPicker.launch("image/*") }
        DocPickRow("Aadhaar (back)", vm.aadhaarBackUri != null) { aadhaarBackPicker.launch("image/*") }
        DocPickRow("Your photo", vm.photoUri != null) { photoPicker.launch("image/*") }

        vm.error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { vm.submit(context.cacheDir, ::resolveMime, ::copyToCache) },
            enabled = !vm.loading, modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (vm.loading) "Submitting..." else "Submit registration")
        }
    }
}

@Composable
private fun LabeledField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), singleLine = true
    )
}

@Composable
private fun DocPickRow(label: String, picked: Boolean, onPick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        OutlinedButton(onClick = onPick) { Text(if (picked) "Change" else "Choose") }
    }
}
