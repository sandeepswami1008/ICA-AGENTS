package com.icgroup.icaagents.ui.invoices

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.icgroup.icaagents.data.AgentInvoice
import com.icgroup.icaagents.data.AgentRepository
import com.icgroup.icaagents.data.ApiResult
import kotlinx.coroutines.launch

class InvoicesViewModel(private val repo: AgentRepository) : ViewModel() {
    var invoices by mutableStateOf<List<AgentInvoice>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var actionMessage by mutableStateOf<String?>(null); private set
    var approvingId by mutableStateOf<Int?>(null); private set

    fun load() {
        loading = true; error = null
        viewModelScope.launch {
            when (val r = repo.myInvoices()) {
                is ApiResult.Success -> invoices = r.data
                is ApiResult.Failure -> error = r.message
            }
            loading = false
        }
    }

    fun approve(id: Int) {
        approvingId = id; actionMessage = null
        viewModelScope.launch {
            when (val r = repo.approveInvoice(id)) {
                is ApiResult.Success -> { actionMessage = r.data; load() }
                is ApiResult.Failure -> actionMessage = r.message
            }
            approvingId = null
        }
    }
}

@Composable
fun InvoicesScreen(vm: InvoicesViewModel) {
    LaunchedEffect(Unit) { vm.load() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("My Invoices", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        vm.actionMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall); Spacer(Modifier.height(8.dp)) }

        when {
            vm.loading -> CircularProgressIndicator()
            vm.error != null -> Text(vm.error!!, color = MaterialTheme.colorScheme.error)
            vm.invoices.isEmpty() -> Text("No invoices yet.")
            else -> LazyColumn {
                items(vm.invoices) { inv ->
                    InvoiceRow(inv, approving = vm.approvingId == inv.id, onApprove = { inv.id?.let(vm::approve) })
                }
            }
        }
    }
}

@Composable
private fun InvoiceRow(inv: AgentInvoice, approving: Boolean, onApprove: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(inv.invoice_no ?: "Invoice", style = MaterialTheme.typography.titleMedium)
            Text(inv.customer_name ?: "", style = MaterialTheme.typography.bodyMedium)
            Text("Revenue: ₹${inv.agent_revenue_amount ?: 0}  •  Payable: ₹${inv.amount_payable ?: 0}")
            Text("Status: ${inv.status ?: "—"}", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            if (inv.agent_approved == 1) {
                AssistChip(onClick = {}, label = { Text("Approved") }, enabled = false)
            } else {
                Button(onClick = onApprove, enabled = !approving) {
                    Text(if (approving) "Approving..." else "Approve invoice")
                }
            }
        }
    }
}
