package org.bataydar.evidence.data.repository

import kotlinx.coroutines.flow.Flow
import org.bataydar.evidence.data.local.FarmerProfileDao
import org.bataydar.evidence.data.model.FarmerProfileEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class FarmerProfileRepository(private val profileDao: FarmerProfileDao) {

    val profileFlow: Flow<FarmerProfileEntity?> = profileDao.getProfileFlow()

    suspend fun getProfile(): FarmerProfileEntity? = profileDao.getProfile()

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

        val farmerId = existingId ?: generateFarmerId()
        val profile = FarmerProfileEntity(
            farmerId = farmerId,
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
        return "FMR-$datePart-$randomPart"
    }
}
