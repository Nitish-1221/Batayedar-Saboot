package org.bataydar.evidence.data.repository

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import org.bataydar.evidence.data.local.FarmerProfileDao
import org.bataydar.evidence.data.model.FarmerProfileEntity
import org.bataydar.evidence.data.remote.auth.TokenManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class FarmerProfileRepository(
    private val profileDao: FarmerProfileDao,
    private val tokenManager: TokenManager? = null
) {

    private val activeUidFlow: Flow<String> = tokenManager?.activeUserUidFlow ?: flowOf("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val profileFlow: Flow<FarmerProfileEntity?> = activeUidFlow.flatMapLatest { uid ->
        if (uid.isBlank()) flowOf(null)
        else profileDao.getProfileFlow(uid)
    }

    suspend fun getProfile(): FarmerProfileEntity? {
        val uid = tokenManager?.getActiveUid() ?: ""
        return if (uid.isNotBlank()) profileDao.getProfile(uid) else profileDao.getProfile()
    }

    suspend fun saveProfile(
        existingId: String?,
        fullName: String,
        phoneNumber: String,
        village: String,
        district: String,
        state: String,
        preferredLanguage: String
    ): Result<FarmerProfileEntity> {
        val trimmedName = fullName.trim()
        val cleanPhone = phoneNumber.trim().replace("\\s+".toRegex(), "")
        val trimmedVillage = village.trim()
        val trimmedDistrict = district.trim()
        val trimmedState = state.trim()

        if (trimmedName.isEmpty()) {
            return Result.failure(IllegalArgumentException("Name cannot be empty"))
        }
        if (cleanPhone.length < 10 || !cleanPhone.all { it.isDigit() }) {
            return Result.failure(IllegalArgumentException("Please enter a valid 10-digit mobile number"))
        }
        if (trimmedVillage.isEmpty()) {
            return Result.failure(IllegalArgumentException("Village cannot be empty"))
        }
        if (trimmedDistrict.isEmpty()) {
            return Result.failure(IllegalArgumentException("District cannot be empty"))
        }
        if (trimmedState.isEmpty()) {
            return Result.failure(IllegalArgumentException("State cannot be empty"))
        }

        val uid = tokenManager?.getActiveUid() ?: ""
        val farmerId = existingId ?: uid.ifEmpty { generateFarmerId() }
        val profile = FarmerProfileEntity(
            farmerId = farmerId,
            ownerFirebaseUid = uid.ifEmpty { farmerId },
            fullName = trimmedName,
            phoneNumber = cleanPhone,
            village = trimmedVillage,
            district = trimmedDistrict,
            state = trimmedState,
            preferredLanguage = preferredLanguage,
            updatedAt = System.currentTimeMillis()
        )

        profileDao.insertOrUpdate(profile)
        return Result.success(profile)
    }

    fun generateFarmerId(): String {
        val datePart = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val randomPart = UUID.randomUUID().toString().take(4).uppercase(Locale.US)
        return "FAR-$datePart-$randomPart"
    }
}
