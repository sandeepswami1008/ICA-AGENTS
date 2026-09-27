package com.icgroup.icaagents.data

import com.icgroup.icaagents.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/** Adds `Authorization: Bearer <token>` to every request when logged in. */
class AuthInterceptor(private val session: SessionStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val token = session.token
        val request = if (!token.isNullOrBlank()) {
            original.newBuilder().addHeader("Authorization", "Bearer $token").build()
        } else original
        return chain.proceed(request)
    }
}

object ApiClient {

    fun buildDmsApi(session: SessionStore): DmsMobileApi = retrofitFor(BuildConfig.DMS_BASE_URL, session)
        .create(DmsMobileApi::class.java)

    fun buildEmsApi(session: SessionStore): EmsApi = retrofitFor(BuildConfig.EMS_BASE_URL, session)
        .create(EmsApi::class.java)

    private fun retrofitFor(baseUrl: String, session: SessionStore): Retrofit {
        val logging = HttpLoggingInterceptor().apply {
            // BODY level is fine for debug builds; never log bodies in release
            // since responses can include masked-but-sensitive profile data.
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(session))
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
