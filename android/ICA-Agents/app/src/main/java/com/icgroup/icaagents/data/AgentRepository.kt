package com.icgroup.icaagents.data

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Failure(val message: String) : ApiResult<Nothing>()
}

class AgentRepository(private val dms: DmsMobileApi, private val ems: EmsApi, private val session: SessionStore) {

    suspend fun register(req: RegisterRequest): ApiResult<RegisterResponse> = safeCall {
        val r = dms.register(body = req)
        if (r.isSuccessful && r.body()?.ok == true) ApiResult.Success(r.body()!!)
        else ApiResult.Failure(r.body()?.error ?: errorBody(r))
    }

    suspend fun uploadKyc(username: String, docType: String, file: File, mimeType: String): ApiResult<String> = safeCall {
        val usernamePart = username.toRequestBody("text/plain".toMediaTypeOrNull())
        val docTypePart = docType.toRequestBody("text/plain".toMediaTypeOrNull())
        val filePart = MultipartBody.Part.createFormData(
            "file", file.name, file.asRequestBody(mimeType.toMediaTypeOrNull())
        )
        val r = dms.uploadKyc(username = usernamePart, docType = docTypePart, file = filePart)
        if (r.isSuccessful && r.body()?.ok == true) ApiResult.Success(r.body()?.message ?: "Uploaded")
        else ApiResult.Failure(r.body()?.error ?: errorBody(r))
    }

    suspend fun login(username: String, password: String): ApiResult<String> = safeCall {
        val r = dms.login(body = LoginRequest(username, password))
        if (r.isSuccessful && r.body()?.ok == true) ApiResult.Success(r.body()?.message ?: "OTP sent")
        else ApiResult.Failure(r.body()?.error ?: errorBody(r))
    }

    suspend fun verifyOtp(username: String, otp: String): ApiResult<UserSummary> = safeCall {
        val r = dms.verifyOtp(body = VerifyOtpRequest(username, otp))
        val body = r.body()
        if (r.isSuccessful && body?.ok == true && body.token != null && body.user != null) {
            session.token = body.token
            session.username = body.user.username
            session.displayName = body.user.name
            session.role = body.user.role
            ApiResult.Success(body.user)
        } else ApiResult.Failure(body?.error ?: errorBody(r))
    }

    fun logout() {
        session.clear()
    }

    suspend fun myProfile(): ApiResult<ProfileResponse> = safeCall {
        val r = dms.profile()
        if (r.isSuccessful && r.body()?.ok == true) ApiResult.Success(r.body()!!)
        else ApiResult.Failure(r.body()?.error ?: errorBody(r))
    }

    suspend fun pendingAgents(): ApiResult<List<PendingAgent>> = safeCall {
        val r = dms.pendingAgents()
        if (r.isSuccessful && r.body()?.ok == true) ApiResult.Success(r.body()?.agents ?: emptyList())
        else ApiResult.Failure(r.body()?.error ?: errorBody(r))
    }

    suspend fun approveAgent(username: String): ApiResult<String> = safeCall {
        val r = dms.approveAgent(body = UsernameOnly(username))
        if (r.isSuccessful && r.body()?.ok == true) ApiResult.Success("Approved")
        else ApiResult.Failure(r.body()?.error ?: errorBody(r))
    }

    suspend fun rejectAgent(username: String, reason: String): ApiResult<String> = safeCall {
        val r = dms.rejectAgent(body = ApproveRejectRequest(username, reason))
        if (r.isSuccessful && r.body()?.ok == true) ApiResult.Success("Rejected")
        else ApiResult.Failure(r.body()?.error ?: errorBody(r))
    }

    suspend fun myLeads(): ApiResult<List<LeadItem>> = safeCall {
        val r = ems.leads()
        if (r.isSuccessful) ApiResult.Success(r.body() ?: emptyList())
        else ApiResult.Failure(errorBody(r))
    }

    suspend fun myInvoices(): ApiResult<List<AgentInvoice>> = safeCall {
        val r = ems.agentInvoices()
        if (r.isSuccessful) ApiResult.Success(r.body() ?: emptyList())
        else ApiResult.Failure(errorBody(r))
    }

    suspend fun approveInvoice(id: Int): ApiResult<String> = safeCall {
        val r = ems.approveInvoice(path = "agent-invoices/$id/approve")
        if (r.isSuccessful && r.body()?.ok == true) ApiResult.Success("Invoice approved")
        else ApiResult.Failure(r.body()?.error ?: errorBody(r))
    }

    suspend fun myPayments(): ApiResult<List<AgentPayment>> = safeCall {
        val r = ems.agentPayments()
        if (r.isSuccessful) ApiResult.Success(r.body() ?: emptyList())
        else ApiResult.Failure(errorBody(r))
    }

    private fun <T> errorBody(r: retrofit2.Response<*>): String =
        "Request failed (HTTP ${r.code()}). Please try again."

    private suspend fun <T> safeCall(block: suspend () -> ApiResult<T>): ApiResult<T> = try {
        block()
    } catch (e: Exception) {
        ApiResult.Failure(e.message ?: "Network error. Check your connection.")
    }
}
