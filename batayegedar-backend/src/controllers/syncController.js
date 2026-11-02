const crypto = require('crypto');
const User = require('../models/User');
const Field = require('../models/Field');
const CropRecord = require('../models/CropRecord');
const Evidence = require('../models/Evidence');
const LandlordConfirmation = require('../models/LandlordConfirmation');
const DeletionTombstone = require('../models/DeletionTombstone');
const { recordAudit } = require('../services/auditService');
const storageService = require('../services/storageService');
const config = require('../config');

/**
 * Push offline records to server.
 * Handles duplicate prevention, conflict resolution (never overwrites newer server data with older local data),
 * and permanently tags every record with authenticated user's Firebase UID and farmerId.
 */
async function pushSync(req, res, next) {
  try {
    const { idempotencyKey, fields, cropRecords, evidenceList } = req.body;
    const farmerId = req.farmerId;
    const firebaseUid = req.firebaseUid || req.user?.firebaseUid || farmerId;

    const syncedFieldIds = [];
    const syncedRecordIds = [];
    const syncedEvidenceIds = [];

    // 1. Process Fields with strict farmer ownership checks
    if (Array.isArray(fields)) {
      for (const f of fields) {
        if (!f.fieldId) continue;
        const existing = await Field.findOne({ fieldId: f.fieldId });
        if (existing) {
          if (existing.farmerId !== farmerId && existing.firebaseUid !== firebaseUid) {
            // Cross-tenant collision! Deny unauthorized claim
            continue;
          }
          // Conflict resolution: do not overwrite newer server data with older local data
          const clientUpdatedAt = f.updatedAt ? new Date(f.updatedAt) : new Date();
          if (!existing.updatedAt || clientUpdatedAt >= existing.updatedAt) {
            existing.fieldName = f.fieldName || existing.fieldName;
            existing.description = f.description !== undefined ? f.description : existing.description;
            existing.village = f.village || existing.village;
            existing.district = f.district || existing.district;
            existing.state = f.state || existing.state;
            existing.areaAcres = f.areaAcres !== undefined ? f.areaAcres : existing.areaAcres;
            existing.surveyKhasraNumber = f.surveyKhasraNumber !== undefined ? f.surveyKhasraNumber : existing.surveyKhasraNumber;
            existing.firebaseUid = existing.firebaseUid || firebaseUid;
            existing.updatedAt = clientUpdatedAt;
            await existing.save();
          }
        } else {
          await Field.create({
            fieldId: f.fieldId,
            farmerId,
            firebaseUid,
            fieldName: f.fieldName,
            description: f.description || '',
            village: f.village,
            district: f.district,
            state: f.state,
            areaAcres: f.areaAcres || 0,
            surveyKhasraNumber: f.surveyKhasraNumber || ''
          });
        }
        syncedFieldIds.push(f.fieldId);
      }
    }

    // 2. Process Crop Records: verify associated field belongs to the farmer
    if (Array.isArray(cropRecords)) {
      for (const r of cropRecords) {
        if (!r.recordId) continue;
        // Check tombstone to prevent resurrection
        const recordTombstone = await DeletionTombstone.findOne({ recordUuid: r.recordId, firebaseUid });
        if (recordTombstone) {
          console.log('[pushSync] Rejecting deleted record ' + r.recordId + ' (tombstone exists)');
          continue;
        }

        const associatedField = await Field.findOne({
          fieldId: r.fieldId,
          $or: [{ farmerId }, { firebaseUid }],
          isDeleted: false
        });
        if (!associatedField) {
          // Reject crop records linked to nonexistent or another farmer's field
          continue;
        }

        const existing = await CropRecord.findOne({ recordId: r.recordId });
        if (existing) {
          if (existing.farmerId !== farmerId && existing.firebaseUid !== firebaseUid) {
            continue;
          }
          const clientUpdatedAt = r.updatedAt ? new Date(r.updatedAt) : new Date();
          if (!existing.updatedAt || clientUpdatedAt >= existing.updatedAt) {
            existing.cropType = r.cropType || existing.cropType;
            existing.cultivationStartDate = r.cultivationStartDate ? new Date(r.cultivationStartDate) : existing.cultivationStartDate;
            existing.notes = r.notes !== undefined ? r.notes : existing.notes;
            existing.landlordName = r.landlordName !== undefined ? r.landlordName : existing.landlordName;
            existing.landlordPhone = r.landlordPhone !== undefined ? r.landlordPhone : existing.landlordPhone;
            existing.firebaseUid = existing.firebaseUid || firebaseUid;
            existing.updatedAt = clientUpdatedAt;
            existing.lastSyncedAt = new Date();
            await existing.save();
          }
        } else {
          await CropRecord.create({
            recordId: r.recordId,
            farmerId,
            firebaseUid,
            fieldId: r.fieldId,
            cropType: r.cropType,
            cultivationStartDate: new Date(r.cultivationStartDate),
            notes: r.notes || '',
            landlordName: r.landlordName || '',
            landlordPhone: r.landlordPhone || '',
            status: 'ACTIVE',
            syncVersion: 1,
            lastSyncedAt: new Date()
          });
        }
        syncedRecordIds.push(r.recordId);
      }
    }

    // 3. Process Evidence metadata: verify associated crop record belongs to the farmer
    if (Array.isArray(evidenceList)) {
      for (const e of evidenceList) {
        if (!e.evidenceId) continue;
        // Check tombstone to prevent resurrection
        const evTombstone = await DeletionTombstone.findOne({ recordUuid: e.evidenceId, firebaseUid });
        if (evTombstone) {
          console.log('[pushSync] Rejecting deleted evidence ' + e.evidenceId + ' (tombstone exists)');
          continue;
        }

        const associatedRecord = await CropRecord.findOne({
          recordId: e.recordId,
          $or: [{ farmerId }, { firebaseUid }],
          status: { $ne: 'DELETED' }
        });
        if (!associatedRecord) {
          // Reject evidence linked to nonexistent or another farmer's crop record
          continue;
        }

        const existing = await Evidence.findOne({ evidenceId: e.evidenceId });
        if (existing) {
          if (existing.farmerId !== farmerId && existing.firebaseUid !== firebaseUid) {
            continue;
          }
          existing.firebaseUid = existing.firebaseUid || firebaseUid;
          if (e.notes) existing.notes = e.notes;
          await existing.save();
        } else {
          await Evidence.create({
            evidenceId: e.evidenceId,
            recordId: e.recordId,
            farmerId,
            firebaseUid,
            s3ObjectKey: `evidence/${farmerId}/${e.recordId}/${e.evidenceId}.jpg`,
            s3Bucket: 'bataydar-evidence',
            mimeType: 'image/jpeg',
            latitude: e.latitude,
            longitude: e.longitude,
            reportedGpsAccuracyMeters: e.reportedGpsAccuracyMeters,
            clientCapturedAtUtc: new Date(e.clientCapturedAtUtc),
            clientReportedChecksum: e.clientReportedChecksum || '',
            validationStatus: 'PENDING_UPLOAD',
            notes: e.notes || ''
          });
        }
        syncedEvidenceIds.push(e.evidenceId);
      }
    }

    await recordAudit({
      action: 'SYNC_PUSH_COMPLETED',
      actorId: farmerId,
      resourceType: 'SYNC',
      resourceId: idempotencyKey || 'N/A',
      details: {
        fieldsCount: syncedFieldIds.length,
        cropRecordsCount: syncedRecordIds.length,
        evidenceCount: syncedEvidenceIds.length
      },
      req
    });

    res.status(200).json({
      success: true,
      message: 'Batch synchronization processed successfully.',
      data: {
        serverTime: new Date().toISOString(),
        syncedFieldIds,
        syncedRecordIds,
        syncedEvidenceIds
      }
    });
  } catch (err) {
    next(err);
  }
}

/**
 * Pull updated records since a specific timestamp
 */
async function pullSync(req, res, next) {
  try {
    const farmerId = req.farmerId;
    const firebaseUid = req.firebaseUid || req.user?.firebaseUid || farmerId;
    const since = req.query.since ? new Date(req.query.since) : new Date(0);

    const userFilter = {
      $or: [{ farmerId }, { firebaseUid }]
    };

    const [fields, cropRecords, evidenceListRaw, confirmations] = await Promise.all([
      Field.find({ ...userFilter, updatedAt: { $gte: since }, isDeleted: false }),
      CropRecord.find({ ...userFilter, updatedAt: { $gte: since }, status: { $ne: 'DELETED' } }),
      Evidence.find({ ...userFilter, updatedAt: { $gte: since }, isDeleted: false }),
      LandlordConfirmation.find({ ...userFilter, updatedAt: { $gte: since } })
    ]);

    const evidenceList = await Promise.all(
      evidenceListRaw.map(async (e) => {
        const obj = e.toObject();
        if (e.cloudinaryUrl) {
          obj.photoUrl = e.cloudinaryUrl;
        } else if (e.s3ObjectKey) {
          try {
            const urlInfo = await storageService.generateDownloadUrl(e.s3ObjectKey, farmerId);
            obj.photoUrl = urlInfo.downloadUrl;
          } catch (err) {
            obj.photoUrl = '';
          }
        }
        return obj;
      })
    );

    res.status(200).json({
      success: true,
      data: {
        serverTime: new Date().toISOString(),
        fields,
        cropRecords,
        evidenceList,
        confirmations
      }
    });
  } catch (err) {
    next(err);
  }
}

/**
 * Incremental changes endpoint for multi-device sync
 */
async function getChanges(req, res, next) {
  return pullSync(req, res, next);
}

/**
 * Account-Based Cloud Restore endpoint.
 * When a user clears app data, reinstalls, or logs in on a new device,
 * this endpoint retrieves ALL cloud data owned by their authenticated Firebase UID.
 */
async function restoreUserData(req, res, next) {
  try {
    const farmerId = req.farmerId;
    const firebaseUid = req.firebaseUid || req.user?.firebaseUid || farmerId;
    const userRole = req.user?.role || 'BATAYDAR';

    // 1. Fetch user profile
    const userDoc = await User.findOne({
      $or: [
        { userId: farmerId },
        { firebaseUid: firebaseUid },
        ...(req.user?.email ? [{ email: req.user.email }] : [])
      ]
    });

    const safeProfile = userDoc ? {
      userId: userDoc.userId,
      firebaseUid: userDoc.firebaseUid || firebaseUid,
      role: userDoc.role,
      fullName: userDoc.fullName,
      phone: userDoc.phone,
      email: userDoc.email,
      village: userDoc.village,
      district: userDoc.district,
      state: userDoc.state,
      preferredLanguage: userDoc.preferredLanguage || 'hi',
      createdAt: userDoc.createdAt
    } : {
      userId: farmerId,
      firebaseUid,
      role: userRole,
      fullName: req.user?.fullName || '',
      phone: req.user?.phone || '',
      email: req.user?.email || '',
      village: req.user?.village || '',
      district: req.user?.district || '',
      state: req.user?.state || '',
      preferredLanguage: 'hi'
    };

    const userFilter = {
      $or: [{ farmerId }, { firebaseUid }]
    };

    // Fetch server-side deletion tombstones
    const tombstones = await DeletionTombstone.find({
      $or: [{ farmerId }, { firebaseUid }]
    });
    const deletedEvidenceIds = tombstones.filter(t => t.recordType === 'EVIDENCE').map(t => t.recordUuid);
    const deletedRecordIds = tombstones.filter(t => t.recordType === 'CROP_RECORD').map(t => t.recordUuid);

    // 2. Fetch all fields
    const fields = await Field.find({
      ...userFilter,
      isDeleted: false
    }).sort({ createdAt: 1 });

    // 3. Fetch all crop records
    const cropRecords = await CropRecord.find({
      ...userFilter,
      status: { $ne: 'DELETED' }
    }).sort({ cultivationStartDate: -1 });

    // 4. Fetch all evidence metadata and generate accessible photo URLs
    const evidenceListRaw = await Evidence.find({
      ...userFilter,
      isDeleted: false
    }).sort({ clientCapturedAtUtc: 1 });

    const evidenceList = await Promise.all(
      evidenceListRaw.map(async (e) => {
        const obj = e.toObject();
        if (e.cloudinaryUrl) {
          obj.photoUrl = e.cloudinaryUrl;
        } else if (e.s3ObjectKey) {
          try {
            const urlInfo = await storageService.generateDownloadUrl(e.s3ObjectKey, farmerId);
            obj.photoUrl = urlInfo.downloadUrl;
          } catch (err) {
            obj.photoUrl = '';
          }
        }
        return obj;
      })
    );

    // 5. Fetch landlord confirmations
    const confirmations = await LandlordConfirmation.find({
      ...userFilter
    }).sort({ requestedAt: -1 });

    await recordAudit({
      action: 'CLOUD_RESTORE_REQUESTED',
      actorId: farmerId,
      resourceType: 'SYNC',
      resourceId: firebaseUid,
      details: {
        fieldsCount: fields.length,
        cropRecordsCount: cropRecords.length,
        evidenceCount: evidenceList.length,
        confirmationsCount: confirmations.length
      },
      req
    });

    res.status(200).json({
      success: true,
      message: 'Account cloud backup restored successfully.',
      data: {
        serverTime: new Date().toISOString(),
        firebaseUid,
        profile: safeProfile,
        fields,
        cropRecords,
        evidenceList,
        confirmations,
        deletedEvidenceIds,
        deletedRecordIds,
        tombstones: tombstones.map(t => ({ recordUuid: t.recordUuid, recordType: t.recordType, deletedAt: t.deletedAt })),
        totalCounts: {
          fields: fields.length,
          cropRecords: cropRecords.length,
          evidenceList: evidenceList.length,
          confirmations: confirmations.length
        }
      }
    });
  } catch (err) {
    next(err);
  }
}

/**
 * Direct authenticated multipart evidence photograph upload.
 * Uploads photo binary to Cloudinary (or local vault), computes SHA-256 hash,
 * verifies magic bytes and GPS parameters, and persists metadata in MongoDB.
 */
async function uploadDirect(req, res, next) {
  try {
    const farmerId = req.farmerId;
    const firebaseUid = req.firebaseUid || req.user?.firebaseUid || farmerId;
    const file = req.file;

    if (!file || !file.buffer) {
      return res.status(400).json({
        success: false,
        error: {
          code: 'MISSING_FILE',
          message: 'Evidence photograph file is required (form field "photo")'
        }
      });
    }

    const {
      recordId,
      evidenceId,
      latitude,
      longitude,
      reportedGpsAccuracyMeters,
      clientCapturedAtUtc,
      capturedAtUtc,
      clientReportedChecksum,
      notes,
      clientFixAgeSeconds,
      deviceManufacturer,
      deviceModel
    } = req.body;

    // Prevent uploading previously deleted evidence
    const existingTombstone = await DeletionTombstone.findOne({ recordUuid: evidenceId, firebaseUid });
    if (existingTombstone) {
      return res.status(410).json({
        success: false,
        error: {
          code: 'EVIDENCE_DELETED',
          message: 'Evidence was permanently deleted from cloud and cannot be re-uploaded.'
        }
      });
    }

    if (!recordId || !evidenceId) {
      return res.status(400).json({
        success: false,
        error: {
          code: 'VALIDATION_ERROR',
          message: 'Both recordId and evidenceId are required'
        }
      });
    }

    // Verify record ownership
    const cropRecord = await CropRecord.findOne({
      recordId,
      status: { $ne: 'DELETED' }
    });

    if (!cropRecord) {
      return res.status(404).json({
        success: false,
        error: {
          code: 'CROP_RECORD_NOT_FOUND',
          message: 'Crop record not found'
        }
      });
    }

    const isRecordOwner = (cropRecord.farmerId === farmerId) ||
      (cropRecord.firebaseUid === firebaseUid) ||
      (req.user?.firebaseUid && cropRecord.firebaseUid === req.user.firebaseUid) ||
      (req.user?.userId && cropRecord.farmerId === req.user.userId);

    if (!isRecordOwner) {
      return res.status(403).json({
        success: false,
        error: {
          code: 'FORBIDDEN',
          message: 'Crop record does not belong to authenticated user'
        }
      });
    }

    const buffer = file.buffer;
    const isJpeg = buffer.length > 3 && buffer[0] === 0xff && buffer[1] === 0xd8 && buffer[2] === 0xff;
    const isPng = buffer.length > 4 && buffer[0] === 0x89 && buffer[1] === 0x50 && buffer[2] === 0x4e && buffer[3] === 0x47;
    if (!isJpeg && !isPng) {
      return res.status(400).json({
        success: false,
        error: {
          code: 'INVALID_FILE_TYPE',
          message: 'Uploaded file is not a valid JPEG or PNG image'
        }
      });
    }

    // Calculate server SHA-256 hash
    const serverSha256 = crypto.createHash('sha256').update(buffer).digest('hex');
    const hashMatch = clientReportedChecksum
      ? clientReportedChecksum.toLowerCase() === serverSha256.toLowerCase()
      : true;

    // Check GPS accuracy
    const gpsAccuracy = parseFloat(reportedGpsAccuracyMeters) || 0;
    const gpsAccuracyAcceptable = gpsAccuracy > 0 && gpsAccuracy <= (config.gps?.maxAcceptableAccuracyMeters || 100);

    let cloudinaryUrl = '';
    let cloudinaryPublicId = '';
    const s3ObjectKey = `evidence/${farmerId}/${recordId}/${evidenceId}.jpg`;
    let storageProvider = config.storage.provider;

    // Cloudinary upload if configured
    if (storageService.isCloudinaryConfigured()) {
      try {
        const uploadResult = await storageService.uploadBufferToCloudinary(buffer, {
          folder: `bataydar/evidence/${firebaseUid}/${recordId}`,
          public_id: evidenceId
        });
        if (uploadResult && uploadResult.secure_url) {
          cloudinaryUrl = uploadResult.secure_url;
          cloudinaryPublicId = uploadResult.public_id;
          storageProvider = 'cloudinary';
        }
      } catch (cloudErr) {
        console.error('[SyncUpload] Cloudinary upload error:', cloudErr.message);
        await storageService.saveLocalFile(s3ObjectKey, buffer);
        storageProvider = 'local';
      }
    } else {
      await storageService.saveLocalFile(s3ObjectKey, buffer);
      storageProvider = 'local';
    }

    // Persist or update evidence in MongoDB
    let evidence = await Evidence.findOne({ evidenceId });
    if (evidence) {
      const isEvOwner = (evidence.farmerId === farmerId) ||
        (evidence.firebaseUid === firebaseUid) ||
        (req.user?.firebaseUid && evidence.firebaseUid === req.user.firebaseUid) ||
        (req.user?.userId && evidence.farmerId === req.user.userId);

      if (!isEvOwner) {
        return res.status(403).json({
          success: false,
          error: { code: 'FORBIDDEN', message: 'Evidence belongs to another user' }
        });
      }
      evidence.cloudinaryUrl = cloudinaryUrl || evidence.cloudinaryUrl;
      evidence.cloudinaryPublicId = cloudinaryPublicId || evidence.cloudinaryPublicId;
      evidence.storageProvider = storageProvider;
      evidence.sha256Checksum = serverSha256;
      evidence.fileSizeBytes = buffer.length;
      evidence.validationStatus = 'VERIFIED';
      evidence.hashMatch = hashMatch;
      evidence.notes = notes || evidence.notes;
      evidence.firebaseUid = firebaseUid;
      await evidence.save();
    } else {
      evidence = await Evidence.create({
        evidenceId,
        recordId,
        farmerId,
        firebaseUid,
        s3ObjectKey,
        s3Bucket: 'bataydar-evidence',
        cloudinaryUrl,
        cloudinaryPublicId,
        storageProvider,
        sha256Checksum: serverSha256,
        clientReportedChecksum: clientReportedChecksum || serverSha256,
        fileSizeBytes: buffer.length,
        mimeType: isJpeg ? 'image/jpeg' : 'image/png',
        latitude: parseFloat(latitude) || 0,
        longitude: parseFloat(longitude) || 0,
        reportedGpsAccuracyMeters: gpsAccuracy,
        clientFixAgeSeconds: parseInt(clientFixAgeSeconds, 10) || 0,
        clientCapturedAtUtc: new Date(clientCapturedAtUtc || capturedAtUtc || Date.now()),
        validationStatus: 'VERIFIED',
        gpsAccuracyAcceptable,
        hashMatch,
        deviceManufacturer: deviceManufacturer || '',
        deviceModel: deviceModel || '',
        notes: notes || ''
      });
    }

    // Update crop record evidence flag and timestamp
    cropRecord.hasEvidence = true;
    cropRecord.updatedAt = new Date();
    await cropRecord.save();

    let photoUrl = cloudinaryUrl;
    if (!photoUrl) {
      try {
        const downloadInfo = await storageService.generateDownloadUrl(s3ObjectKey, farmerId);
        photoUrl = downloadInfo.downloadUrl;
      } catch (err) {
        photoUrl = '';
      }
    }

    await recordAudit({
      action: 'DIRECT_EVIDENCE_UPLOADED',
      actorId: farmerId,
      resourceType: 'EVIDENCE',
      resourceId: evidenceId,
      details: {
        recordId,
        storageProvider,
        sha256Checksum: serverSha256,
        fileSizeBytes: buffer.length
      },
      req
    });

    res.status(201).json({
      success: true,
      message: 'Evidence photo uploaded and verified successfully',
      data: {
        evidenceId: evidence.evidenceId,
        recordId: evidence.recordId,
        photoUrl,
        cloudinaryUrl: evidence.cloudinaryUrl,
        cloudinaryPublicId: evidence.cloudinaryPublicId,
        storageProvider: evidence.storageProvider,
        validationStatus: evidence.validationStatus,
        sha256Checksum: evidence.sha256Checksum,
        hashMatch
      }
    });
  } catch (err) {
    next(err);
  }
}


/**
 * Synchronize pending deletions from client.
 * Enforces ownership, permanently deletes associated Cloudinary photos,
 * marks database entities as DELETED, and records server-side tombstones
 * so records never reappear upon restore, data clearing, or multi-device sync.
 */
async function syncDeletions(req, res, next) {
  try {
    const farmerId = req.farmerId;
    const firebaseUid = req.firebaseUid || req.user?.firebaseUid || farmerId;
    const { deletedEvidenceIds = [], deletedRecordIds = [] } = req.body;

    const confirmedDeletedEvidenceIds = [];
    const confirmedDeletedRecordIds = [];

    // 1. Process Evidence Deletions
    for (const evidenceId of deletedEvidenceIds) {
      if (!evidenceId) continue;
      const evidence = await Evidence.findOne({ evidenceId });
      if (evidence) {
        const isOwner = (evidence.farmerId === farmerId) ||
          (evidence.firebaseUid === firebaseUid) ||
          (req.user?.firebaseUid && evidence.firebaseUid === req.user.firebaseUid) ||
          (req.user?.userId && evidence.farmerId === req.user.userId);

        if (!isOwner) {
          continue;
        }

        // Delete from Cloudinary if not shared
        if (evidence.cloudinaryPublicId) {
          try {
            const shared = await Evidence.findOne({
              cloudinaryPublicId: evidence.cloudinaryPublicId,
              evidenceId: { $ne: evidence.evidenceId },
              isDeleted: false
            });
            if (!shared) {
              await storageService.deleteFromCloudinary(evidence.cloudinaryPublicId);
            }
          } catch (cloudErr) {
            console.error('[SyncDeletions] Cloudinary delete error for ' + evidenceId + ':', cloudErr.message);
          }
        }

        if (evidence.s3ObjectKey) {
          try {
            await storageService.deleteObject(evidence.s3ObjectKey);
          } catch (e) {}
        }

        evidence.isDeleted = true;
        evidence.deletedAt = new Date();
        evidence.validationStatus = 'DELETED';
        await evidence.save();
      }

      await DeletionTombstone.findOneAndUpdate(
        { recordUuid: evidenceId, firebaseUid },
        {
          recordUuid: evidenceId,
          recordType: 'EVIDENCE',
          firebaseUid,
          farmerId,
          cloudinaryPublicId: evidence?.cloudinaryPublicId || '',
          deletedAt: new Date(),
          deletionStatus: 'PERMANENTLY_DELETED'
        },
        { upsert: true, new: true }
      );

      confirmedDeletedEvidenceIds.push(evidenceId);
    }

    // 2. Process Crop Record Deletions
    for (const recordId of deletedRecordIds) {
      if (!recordId) continue;
      const record = await CropRecord.findOne({ recordId });
      if (record) {
        const isOwner = (record.farmerId === farmerId) ||
          (record.firebaseUid === firebaseUid) ||
          (req.user?.firebaseUid && record.firebaseUid === req.user.firebaseUid) ||
          (req.user?.userId && record.farmerId === req.user.userId);

        if (!isOwner) continue;

        // Delete associated child evidence
        const childEvidence = await Evidence.find({ recordId, isDeleted: false });
        for (const ev of childEvidence) {
          if (ev.cloudinaryPublicId) {
            try {
              const shared = await Evidence.findOne({
                cloudinaryPublicId: ev.cloudinaryPublicId,
                evidenceId: { $ne: ev.evidenceId },
                isDeleted: false
              });
              if (!shared) {
                await storageService.deleteFromCloudinary(ev.cloudinaryPublicId);
              }
            } catch (err) {}
          }
          if (ev.s3ObjectKey) {
            try { await storageService.deleteObject(ev.s3ObjectKey); } catch (e) {}
          }
          ev.isDeleted = true;
          ev.deletedAt = new Date();
          ev.validationStatus = 'DELETED';
          await ev.save();

          await DeletionTombstone.findOneAndUpdate(
            { recordUuid: ev.evidenceId, firebaseUid },
            {
              recordUuid: ev.evidenceId,
              recordType: 'EVIDENCE',
              firebaseUid,
              farmerId,
              cloudinaryPublicId: ev.cloudinaryPublicId || '',
              deletedAt: new Date(),
              deletionStatus: 'PERMANENTLY_DELETED'
            },
            { upsert: true }
          );
          if (!confirmedDeletedEvidenceIds.includes(ev.evidenceId)) {
            confirmedDeletedEvidenceIds.push(ev.evidenceId);
          }
        }

        record.status = 'DELETED';
        record.isDeleted = true;
        record.deletedAt = new Date();
        await record.save();
      }

      await DeletionTombstone.findOneAndUpdate(
        { recordUuid: recordId, firebaseUid },
        {
          recordUuid: recordId,
          recordType: 'CROP_RECORD',
          firebaseUid,
          farmerId,
          deletedAt: new Date(),
          deletionStatus: 'PERMANENTLY_DELETED'
        },
        { upsert: true, new: true }
      );

      confirmedDeletedRecordIds.push(recordId);
    }

    await recordAudit({
      action: 'SYNC_DELETIONS_COMPLETED',
      actorId: farmerId,
      resourceType: 'SYNC',
      resourceId: firebaseUid,
      details: {
        evidenceDeletedCount: confirmedDeletedEvidenceIds.length,
        recordsDeletedCount: confirmedDeletedRecordIds.length
      },
      req
    });

    res.status(200).json({
      success: true,
      message: 'Cloud deletions synchronized and tombstones established successfully.',
      data: {
        confirmedDeletedEvidenceIds,
        confirmedDeletedRecordIds,
        serverTime: new Date().toISOString()
      }
    });
  } catch (err) {
    next(err);
  }
}

module.exports = {
  pushSync,
  pullSync,
  getChanges,
  restoreUserData,
  uploadDirect,
  syncDeletions
};
