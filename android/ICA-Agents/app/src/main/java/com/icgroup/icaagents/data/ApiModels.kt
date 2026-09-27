package com.icgroup.icaagents.data

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

// ---------- DMS: dms/api/mobile.php ----------

data class RegisterRequest(
    val name: String,
    val mobile: String,
    val email: String,
    val pan: String,
    val aadhaar: String,
    val bank_account: String? = null,
    val bank_ifsc: String? = null,
    val bank_holder_name: String? = null,
    val password: String
)

data class RegisterResponse(val ok: Boolean, val username: String?, val status: String?, val message: String?, val error: String?)

data class LoginRequest(val username: String, val password: String)
data class SimpleMessage(val ok: Boolean, val message: String?, val username: String?, val error: String?)

data class VerifyOtpRequest(val username: String, val otp: String, val device_label: String = "Android")
data class UserSummary(val username: String, val name: String, val role: String)
data class TokenResponse(val ok: Boolean, val token: String?, val expires_in_days: Int?, val user: UserSummary?, val error: String?)

data class PendingAgent(
    val username: String, val name: String, val email: String?, val mobile_number: String?,
    val pan_last4: String?, val aadhaar_last4: String?, val created_at: String?
)
data class PendingAgentsResponse(val ok: Boolean, val agents: List<PendingAgent>?, val error: String?)

data class UsernameOnly(val username: String)
data class ApproveRejectRequest(val username: String, val reason: String? = null)

data class ProfileResponse(
    val ok: Boolean,
    val username: String?, val name: String?, val role: String?, val email: String?,
    val mobile: String?, val pan_masked: String?, val aadhaar_masked: String?,
    val bank_account_masked: String?, val bank_ifsc: String?, val bank_holder_name: String?,
    val approval_status: String?, val error: String?
)

interface DmsMobileApi {
    @POST("mobile.php")
    @retrofit2.http.Headers("Content-Type: application/json")
    suspend fun register(@Query("r") route: String = "register", @Body body: RegisterRequest): Response<RegisterResponse>

    @Multipart
    @POST("mobile.php")
    suspend fun uploadKyc(
        @Query("r") route: String = "kyc/upload",
        @Part("username") username: RequestBody,
        @Part("doc_type") docType: RequestBody,
        @Part file: MultipartBody.Part
    ): Response<SimpleMessage>

    @POST("mobile.php")
    suspend fun login(@Query("r") route: String = "auth/login", @Body body: LoginRequest): Response<SimpleMessage>

    @POST("mobile.php")
    suspend fun verifyOtp(@Query("r") route: String = "auth/verify-otp", @Body body: VerifyOtpRequest): Response<TokenResponse>

    @POST("mobile.php")
    suspend fun logout(@Query("r") route: String = "auth/logout"): Response<SimpleMessage>

    @GET("mobile.php")
    suspend fun pendingAgents(@Query("r") route: String = "admin/agents/pending"): Response<PendingAgentsResponse>

    @POST("mobile.php")
    suspend fun approveAgent(@Query("r") route: String = "admin/agents/approve", @Body body: UsernameOnly): Response<SimpleMessage>

    @POST("mobile.php")
    suspend fun rejectAgent(@Query("r") route: String = "admin/agents/reject", @Body body: ApproveRejectRequest): Response<SimpleMessage>

    @GET("mobile.php")
    suspend fun profile(@Query("r") route: String = "profile/me"): Response<ProfileResponse>
}

// ---------- EMS: ems/api/index.php (agent-scoped routes) ----------

data class LeadItem(
    val id: String?, val full_name: String?, val loan_type: String?, val loan_amount: Double?,
    val status_text: String?, val agent: String?, val assigned_to: String?, val updated_at: String?
)

data class AgentInvoice(
    val id: Int?, val invoice_no: String?, val agent_user_ref: String?, val customer_name: String?,
    val bank_name: String?, val invoice_amount: Double?, val amount_payable: Double?,
    val agent_revenue_amount: Double?, val status: String?, val agent_approved: Int?,
    val invoice_date: String?, val due_date: String?
)

data class AgentPayment(
    val id: Int?, val agent_user_ref: String?, val amount: Double?, val processed_at: String?,
    val payment_ref: String?, val status: String?
)

data class ApproveResponse(val ok: Boolean, val approved: Boolean?, val error: String?)
data class GenericOkList<T>(val ok: Boolean, val error: String? = null)

interface EmsApi {
    @GET("index.php")
    suspend fun leads(@Query("path") path: String = "leads", @Query("fy") fy: String? = null): Response<List<LeadItem>>

    @GET("index.php")
    suspend fun agentInvoices(
        @Query("path") path: String = "agent-invoices",
        @Query("agent_user_ref") agentUserRef: String? = null
    ): Response<List<AgentInvoice>>

    // EMS matches this against preg_match('#^agent-invoices/([0-9]+)/approve$#', ...)
    // on the `path` query value itself, so we build the value here, not the URL path.
    @POST("index.php")
    suspend fun approveInvoice(@Query("path") path: String): Response<ApproveResponse>

    @GET("index.php")
    suspend fun agentPayments(
        @Query("path") path: String = "agent-payments",
        @Query("agent_user_ref") agentUserRef: String? = null
    ): Response<List<AgentPayment>>
}
