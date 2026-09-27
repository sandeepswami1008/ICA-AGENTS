package com.icgroup.icaagents.ui.leads

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.icgroup.icaagents.data.AgentRepository
import com.icgroup.icaagents.data.ApiResult
import com.icgroup.icaagents.data.LeadItem
import kotlinx.coroutines.launch

class LeadsViewModel(private val repo: AgentRepository) : ViewModel() {
    var leads by mutableStateOf<List<LeadItem>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set

    fun load() {
        loading = true; error = null
        viewModelScope.launch {
            when (val r = repo.myLeads()) {
                is ApiResult.Success -> leads = r.data
                is ApiResult.Failure -> error = r.message
            }
            loading = false
        }
    }
}

@Composable
fun LeadsScreen(vm: LeadsViewModel) {
    LaunchedEffect(Unit) { vm.load() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("My Leads", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        when {
            vm.loading -> CircularProgressIndicator()
            vm.error != null -> Text(vm.error!!, color = MaterialTheme.colorScheme.error)
            vm.leads.isEmpty() -> Text("No leads assigned yet.")
            else -> LazyColumn {
                items(vm.leads) { lead -> LeadRow(lead) }
            }
        }
    }
}

@Composable
private fun LeadRow(lead: LeadItem) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(lead.full_name ?: "—", style = MaterialTheme.typography.titleMedium)
            Text("${lead.loan_type ?: "Loan"} • ₹${lead.loan_amount ?: 0}", style = MaterialTheme.typography.bodyMedium)
            Text(lead.status_text ?: "Status pending", style = MaterialTheme.typography.bodySmall)
        }
    }
}
