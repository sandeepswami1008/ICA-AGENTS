package com.icgroup.icaagents.ui.payments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.icgroup.icaagents.data.AgentPayment
import com.icgroup.icaagents.data.AgentRepository
import com.icgroup.icaagents.data.ApiResult
import kotlinx.coroutines.launch

class PaymentsViewModel(private val repo: AgentRepository) : ViewModel() {
    var payments by mutableStateOf<List<AgentPayment>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set

    fun load() {
        loading = true; error = null
        viewModelScope.launch {
            when (val r = repo.myPayments()) {
                is ApiResult.Success -> payments = r.data
                is ApiResult.Failure -> error = r.message
            }
            loading = false
        }
    }
}

@Composable
fun PaymentsScreen(vm: PaymentsViewModel) {
    LaunchedEffect(Unit) { vm.load() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("My Payments", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        when {
            vm.loading -> CircularProgressIndicator()
            vm.error != null -> Text(vm.error!!, color = MaterialTheme.colorScheme.error)
            vm.payments.isEmpty() -> Text("No payments recorded yet. Payments are processed by our team after invoice approval.")
            else -> LazyColumn {
                items(vm.payments) { p ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("₹${p.amount ?: 0}", style = MaterialTheme.typography.titleMedium)
                            Text("Ref: ${p.payment_ref ?: "—"}")
                            Text("Status: ${p.status ?: "—"}  •  ${p.processed_at ?: ""}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
