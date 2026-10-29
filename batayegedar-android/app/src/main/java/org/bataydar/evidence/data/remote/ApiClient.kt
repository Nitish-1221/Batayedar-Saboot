package org.bataydar.evidence.data.remote

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.bataydar.evidence.BuildConfig
import org.bataydar.evidence.data.remote.api.*
import org.bataydar.evidence.data.remote.auth.AuthInterceptor
import org.bataydar.evidence.data.remote.auth.TokenAuthenticator
import org.bataydar.evidence.data.remote.auth.TokenManager
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class ApiClient private constructor(context: Context) {

    private val gson: Gson = GsonBuilder()
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .create()

    private val tokenManager: TokenManager = TokenManager.getInstance(context)

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(HostSelectionInterceptor(context))
        .addInterceptor(AuthInterceptor(tokenManager))
        .addInterceptor(loggingInterceptor)
        .authenticator(TokenAuthenticator(tokenManager, gson, context))
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(ServerConfig.getBaseUrl(context))
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    val authApi: AuthApiService = retrofit.create(AuthApiService::class.java)
    val farmerApi: FarmerApiService = retrofit.create(FarmerApiService::class.java)
    val fieldApi: FieldApiService = retrofit.create(FieldApiService::class.java)
    val cropRecordApi: CropRecordApiService = retrofit.create(CropRecordApiService::class.java)
    val evidenceApi: EvidenceApiService = retrofit.create(EvidenceApiService::class.java)
    val landlordApi: LandlordApiService = retrofit.create(LandlordApiService::class.java)
    val syncApi: SyncApiService = retrofit.create(SyncApiService::class.java)

    companion object {
        @Volatile
        private var instance: ApiClient? = null

        fun getInstance(context: Context): ApiClient =
            instance ?: synchronized(this) {
                instance ?: ApiClient(context.applicationContext).also { instance = it }
            }
    }
}
