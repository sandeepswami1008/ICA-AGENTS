package com.icgroup.icaagents.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.icgroup.icaagents.data.AgentRepository
import com.icgroup.icaagents.ui.invoices.InvoicesViewModel
import com.icgroup.icaagents.ui.leads.LeadsViewModel
import com.icgroup.icaagents.ui.login.LoginViewModel
import com.icgroup.icaagents.ui.payments.PaymentsViewModel
import com.icgroup.icaagents.ui.profile.ProfileViewModel
import com.icgroup.icaagents.ui.register.RegisterViewModel

class ViewModelFactory(private val repo: AgentRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
        LoginViewModel::class.java -> LoginViewModel(repo) as T
        RegisterViewModel::class.java -> RegisterViewModel(repo) as T
        LeadsViewModel::class.java -> LeadsViewModel(repo) as T
        InvoicesViewModel::class.java -> InvoicesViewModel(repo) as T
        PaymentsViewModel::class.java -> PaymentsViewModel(repo) as T
        ProfileViewModel::class.java -> ProfileViewModel(repo) as T
        else -> throw IllegalArgumentException("Unknown ViewModel: $modelClass")
    }
}
