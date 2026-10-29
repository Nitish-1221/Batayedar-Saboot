package org.bataydar.evidence

import android.app.Application
import android.content.Context
import org.bataydar.evidence.data.local.AppDatabase
import org.bataydar.evidence.data.repository.EvidenceRepository
import org.bataydar.evidence.data.repository.FarmerProfileRepository
import org.bataydar.evidence.export.PdfDossierExporter
import org.bataydar.evidence.export.ZipBundleExporter
import org.bataydar.evidence.sync.CloudSyncEngine
import org.bataydar.evidence.util.LocaleHelper

class BataydarApp : Application() {

    val database by lazy { AppDatabase.getDatabase(this) }
    val profileRepository by lazy { FarmerProfileRepository(database.farmerProfileDao()) }
    val evidenceRepository by lazy {
        EvidenceRepository(
            context = this,
            cropRecordDao = database.cropRecordDao(),
            evidenceDao = database.geotaggedEvidenceDao(),
            auditLogDao = database.auditLogDao()
        )
    }
    val cloudSyncEngine by lazy {
        CloudSyncEngine(
            context = this,
            cropRecordDao = database.cropRecordDao(),
            evidenceDao = database.geotaggedEvidenceDao()
        )
    }
    val pdfExporter by lazy { PdfDossierExporter(this) }
    val zipExporter by lazy { ZipBundleExporter(this, pdfExporter) }
    val tokenManager by lazy { org.bataydar.evidence.data.remote.auth.TokenManager.getInstance(this) }
    val apiClient by lazy { org.bataydar.evidence.data.remote.ApiClient.getInstance(this) }
    val authRepository by lazy {
        org.bataydar.evidence.data.repository.AuthRepository(
            apiClient = apiClient,
            tokenManager = tokenManager,
            profileDao = database.farmerProfileDao(),
            context = this
        )
    }
    val landlordRepository by lazy {
        org.bataydar.evidence.data.repository.LandlordRepository(
            apiClient = apiClient,
            tokenManager = tokenManager
        )
    }
    val syncManager by lazy { org.bataydar.evidence.sync.SyncManager.getInstance(this) }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.applySavedLocale(base))
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    companion object {
        lateinit var instance: BataydarApp
            private set
    }
}
