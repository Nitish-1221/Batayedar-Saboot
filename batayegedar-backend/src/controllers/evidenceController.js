const crypto = require('crypto');
const path = require('path');
const jwt = require('jsonwebtoken');
const Evidence = require('../models/Evidence');
const DeletionTombstone = require('../models/DeletionTombstone');
const CropRecord = require('../models/CropRecord');
const storageService = require('../services/storageService');
const { recordAudit } = require('../services/auditService');
const config = require('../config');

async function requestUploadUrl(req, res, next) {
  try {
    const {
      recordId,
      evidenceId,
      fileExtension,
      contentType,
      latitude,
      longitude,
      altitude,
      reportedGpsAccuracyMeters,
      clientFixAgeSeconds,
      clientCapturedAtUtc,
      deviceManufacturer,
      deviceModel,
      notes
    } = req.body;

    // Validate crop record ownership
    const cropRecord = await CropRecord.findOne({ recordId, status: { $ne: 'DELETED' } });
    if (!cropRecord) {
      return res.status(404).json({
        success: false,
        error: { code: 'RECORD_NOT_FOUND', message: 'Associated crop record not found' }
      });
    }

    const isOwner = (cropRecord.farmerId === req.farmerId) ||
      (req.firebaseUid && cropRecord.firebaseUid === req.firebaseUid) ||
      (req.user?.firebaseUid && cropRecord.firebaseUid === req.user.firebaseUid) ||
      (req.user?.userId && cropRecord.farmerId === req.user.userId);

    if (!isOwner) {
      return res.status(403).json({
        success: false,
        error: { code: 'FORBIDDEN', message: 'You are not authorized to add evidence to this record' }
      });
    }

    const id = evidenceId || crypto.randomUUID();
    const cleanExt = (fileExtension || 'jpg').replace(/^\./, '').toLowerCase();

    // Check GPS accuracy constraint
    const isAccuracyAcceptable = reportedGpsAccuracyMeters <= config.gpsValidation.maxHorizontalAccuracyMeters;

    // Generate signed upload target
    const uploadMeta = await storageService.generateUploadUrl({
      evidenceId: id,
      farmerId: req.farmerId,
      recordId,
      fileExtension: cleanExt,
      contentType: contentType || 'image/jpeg'
    });

    // Create Evidence document in PENDING_UPLOAD status
    const evidence = new Evidence({
      evidenceId: id,
      recordId,
      farmerId: req.farmerId,
      firebaseUid: req.firebaseUid || req.user?.firebaseUid || req.farmerId,
      s3ObjectKey: uploadMeta.objectKey,
      s3Bucket: uploadMeta.bucket,
      mimeType: contentType || 'image/jpeg',
      latitude,
      longitude,
      altitude: altitude || 0,
      reportedGpsAccuracyMeters,
      clientFixAgeSeconds: clientFixAgeSeconds || 0,
      clientCapturedAtUtc: new Date(clientCapturedAtUtc),
      serverReceivedAtUtc: new Date(),
      validationStatus: 'PENDING_UPLOAD',
      gpsAccuracyAcceptable: isAccuracyAcceptable,
      deviceManufacturer: deviceManufacturer || '',
      deviceModel: deviceModel || '',
      notes: notes || ''
    });

    await evidence.save();

    await recordAudit({
      action: 'EVIDENCE_UPLOAD_REQUESTED',
      actorId: req.farmerId,
      resourceType: 'EVIDENCE',
      resourceId: evidence.evidenceId,
      details: {
        recordId,
        reportedGpsAccuracyMeters,
        isAccuracyAcceptable
      },
      req
    });

    res.status(200).json({
      success: true,
      message: 'Upload URL generated. Upload the photograph directly to the destination.',
      data: {
        evidenceId: evidence.evidenceId,
        objectKey: uploadMeta.objectKey,
        uploadUrl: uploadMeta.uploadUrl,
        storageProvider: uploadMeta.storageProvider,
        expiresInSeconds: uploadMeta.expiresInSeconds,
        gpsAccuracyAcceptable: isAccuracyAcceptable
      }
    });
  } catch (err) {
    next(err);
  }
}

async function completeUpload(req, res, next) {
  try {
    const { evidenceId } = req.params;
    const { clientReportedChecksum, notes } = req.body;

    const evidence = await Evidence.findOne({ evidenceId, isDeleted: false });
    if (!evidence) {
      return res.status(404).json({
        success: false,
        error: { code: 'EVIDENCE_NOT_FOUND', message: 'Evidence record not found' }
      });
    }

    const isOwner = (evidence.farmerId === req.farmerId) ||
      (req.firebaseUid && evidence.firebaseUid === req.firebaseUid) ||
      (req.user?.firebaseUid && evidence.firebaseUid === req.user.firebaseUid) ||
      (req.user?.userId && evidence.farmerId === req.user.userId);

    if (!isOwner) {
      return res.status(403).json({
        success: false,
        error: { code: 'FORBIDDEN', message: 'You are not authorized to finalize this evidence' }
      });
    }

    // Verify stored object on storage (checks file existence, size, magic bytes, SHA-256)
    const verification = await storageService.verifyUploadedEvidence(
      evidence.s3ObjectKey,
      clientReportedChecksum || evidence.clientReportedChecksum,
      {
        evidenceId: evidence.evidenceId,
        farmerId: evidence.farmerId,
        recordId: evidence.recordId
      }
    );

    if (!verification.success) {
      evidence.validationStatus = 'FAILED_INTEGRITY';
      evidence.notes = `${evidence.notes || ''} [Verification Failed: ${verification.error}]`.trim();
      await evidence.save();

      await recordAudit({
        action: 'EVIDENCE_VERIFICATION_FAILED',
        actorId: req.farmerId,
        resourceType: 'EVIDENCE',
        resourceId: evidence.evidenceId,
        details: { reason: verification.error },
        req
      });

      return res.status(400).json({
        success: false,
        error: {
          code: 'EVIDENCE_VERIFICATION_FAILED',
          message: verification.error
        }
      });
    }

    // Update verified evidence metadata
    evidence.sha256Checksum = verification.serverSha256;
    evidence.clientReportedChecksum = verification.clientSha256;
    evidence.hashMatch = verification.hashMatch;
    evidence.fileSizeBytes = verification.fileSizeBytes;
    evidence.mimeType = verification.detectedMimeType;
    if (verification.cloudinaryUrl) {
      evidence.cloudinaryUrl = verification.cloudinaryUrl;
      evidence.cloudinaryPublicId = verification.cloudinaryPublicId;
      evidence.storageProvider = 'cloudinary';
    }
    if (req.firebaseUid) {
      evidence.firebaseUid = req.firebaseUid;
    }

    if (!evidence.gpsAccuracyAcceptable) {
      evidence.validationStatus = 'FLAGGED_INACCURATE';
    } else {
      evidence.validationStatus = 'VERIFIED';
    }

    if (notes) {
      evidence.notes = notes;
    }

    await evidence.save();

    await recordAudit({
      action: 'EVIDENCE_VERIFIED',
      actorId: req.farmerId,
      resourceType: 'EVIDENCE',
      resourceId: evidence.evidenceId,
      details: {
        serverSha256: evidence.sha256Checksum,
        hashMatch: evidence.hashMatch,
        validationStatus: evidence.validationStatus,
        fileSizeBytes: evidence.fileSizeBytes
      },
      req
    });

    res.status(200).json({
      success: true,
      message: 'Evidence photograph verified and finalized successfully.',
      data: {
        evidence
      }
    });
  } catch (err) {
    next(err);
  }
}

async function getRecordEvidence(req, res, next) {
  try {
    const { recordId } = req.params;

    const cropRecord = await CropRecord.findOne({ recordId, status: { $ne: 'DELETED' } });
    if (!cropRecord) {
      return res.status(404).json({
        success: false,
        error: { code: 'RECORD_NOT_FOUND', message: 'Crop record not found' }
      });
    }

    const isCropOwner = (cropRecord.farmerId === req.farmerId) ||
      (req.firebaseUid && cropRecord.firebaseUid === req.firebaseUid) ||
      (req.user?.firebaseUid && cropRecord.firebaseUid === req.user.firebaseUid) ||
      (req.user?.userId && cropRecord.farmerId === req.user.userId);

    if (!isCropOwner) {
      return res.status(403).json({
        success: false,
        error: { code: 'FORBIDDEN', message: 'You are not authorized to view evidence for this record' }
      });
    }

    const evidenceList = await Evidence.find({ recordId, isDeleted: false }).sort({ createdAt: -1 });

    res.status(200).json({
      success: true,
      data: {
        evidence: evidenceList
      }
    });
  } catch (err) {
    next(err);
  }
}

async function getEvidenceById(req, res, next) {
  try {
    const { evidenceId } = req.params;

    const evidence = await Evidence.findOne({ evidenceId, isDeleted: false });
    if (!evidence) {
      return res.status(404).json({
        success: false,
        error: { code: 'EVIDENCE_NOT_FOUND', message: 'Evidence not found' }
      });
    }

    const isEvOwner = (evidence.farmerId === req.farmerId) ||
      (req.firebaseUid && evidence.firebaseUid === req.firebaseUid) ||
      (req.user?.firebaseUid && evidence.firebaseUid === req.user.firebaseUid) ||
      (req.user?.userId && evidence.farmerId === req.user.userId);

    if (!isEvOwner) {
      return res.status(403).json({
        success: false,
        error: { code: 'FORBIDDEN', message: 'You are not authorized to access this evidence' }
      });
    }

    res.status(200).json({
      success: true,
      data: { evidence }
    });
  } catch (err) {
    next(err);
  }
}

async function getDownloadUrl(req, res, next) {
  try {
    const { evidenceId } = req.params;

    const evidence = await Evidence.findOne({ evidenceId, isDeleted: false });
    if (!evidence) {
      return res.status(404).json({
        success: false,
        error: { code: 'EVIDENCE_NOT_FOUND', message: 'Evidence not found' }
      });
    }

    const isDlOwner = (evidence.farmerId === req.farmerId) ||
      (req.firebaseUid && evidence.firebaseUid === req.firebaseUid) ||
      (req.user?.firebaseUid && evidence.firebaseUid === req.user.firebaseUid) ||
      (req.user?.userId && evidence.farmerId === req.user.userId);

    if (!isDlOwner) {
      return res.status(403).json({
        success: false,
        error: { code: 'FORBIDDEN', message: 'You are not authorized to download this evidence' }
      });
    }

    const downloadInfo = await storageService.generateDownloadUrl({
      objectKey: evidence.s3ObjectKey,
      farmerId: req.farmerId,
      cloudinaryUrl: evidence.cloudinaryUrl
    });

    await recordAudit({
      action: 'EVIDENCE_DOWNLOAD_ACCESSED',
      actorId: req.farmerId,
      resourceType: 'EVIDENCE',
      resourceId: evidence.evidenceId,
      details: {},
      req
    });

    res.status(200).json({
      success: true,
      data: {
        downloadUrl: downloadInfo.downloadUrl,
        expiresInSeconds: downloadInfo.expiresInSeconds
      }
    });
  } catch (err) {
    next(err);
  }
}

async function deleteEvidence(req, res, next) {
  try {
    const { evidenceId } = req.params;
    const farmerId = req.farmerId;
    const firebaseUid = req.firebaseUid || req.user?.firebaseUid || farmerId;

    const evidence = await Evidence.findOne({ evidenceId });
    if (!evidence) {
      // Check if already in DeletionTombstone
      const tombstone = await DeletionTombstone.findOne({ recordUuid: evidenceId, firebaseUid });
      if (tombstone) {
        return res.status(200).json({
          success: true,
          message: 'Evidence is already permanently deleted.',
          data: { evidenceId }
        });
      }
      return res.status(404).json({
        success: false,
        error: { code: 'EVIDENCE_NOT_FOUND', message: 'Evidence not found' }
      });
    }

    const isOwner = (evidence.farmerId === farmerId) ||
      (evidence.firebaseUid === firebaseUid) ||
      (req.user?.firebaseUid && evidence.firebaseUid === req.user.firebaseUid) ||
      (req.user?.userId && evidence.farmerId === req.user.userId);

    if (!isOwner) {
      return res.status(403).json({
        success: false,
        error: { code: 'FORBIDDEN', message: 'You are not authorized to delete this evidence' }
      });
    }

    // Delete image from Cloudinary if not shared by another active record
    if (evidence.cloudinaryPublicId) {
      try {
        const sharedActive = await Evidence.findOne({
          cloudinaryPublicId: evidence.cloudinaryPublicId,
          evidenceId: { $ne: evidence.evidenceId },
          isDeleted: false
        });
        if (!sharedActive) {
          await storageService.deleteFromCloudinary(evidence.cloudinaryPublicId);
        }
      } catch (cloudErr) {
        console.error('[deleteEvidence] Cloudinary deletion error:', cloudErr.message);
        return res.status(502).json({
          success: false,
          error: {
            code: 'CLOUDINARY_DELETION_FAILED',
            message: 'Failed deleting photo from cloud storage: ' + cloudErr.message + '. Please retry.'
          }
        });
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

    // Create server-side tombstone
    await DeletionTombstone.findOneAndUpdate(
      { recordUuid: evidence.evidenceId, firebaseUid },
      {
        recordUuid: evidence.evidenceId,
        recordType: 'EVIDENCE',
        firebaseUid,
        farmerId,
        cloudinaryPublicId: evidence.cloudinaryPublicId || '',
        deletedAt: new Date(),
        deletionStatus: 'PERMANENTLY_DELETED'
      },
      { upsert: true, new: true }
    );

    // Update parent crop record's hasEvidence flag if no active evidence left
    const remainingActive = await Evidence.countDocuments({
      recordId: evidence.recordId,
      isDeleted: false
    });
    if (remainingActive === 0) {
      await CropRecord.updateOne({ recordId: evidence.recordId }, { hasEvidence: false, updatedAt: new Date() });
    }

    await recordAudit({
      action: 'EVIDENCE_PERMANENTLY_DELETED',
      actorId: farmerId,
      resourceType: 'EVIDENCE',
      resourceId: evidence.evidenceId,
      details: {
        recordId: evidence.recordId,
        cloudinaryPublicId: evidence.cloudinaryPublicId || 'none'
      },
      req
    });

    res.status(200).json({
      success: true,
      message: 'Evidence permanently deleted from cloud and storage successfully',
      data: { evidenceId: evidence.evidenceId }
    });
  } catch (err) {
    next(err);
  }
}

// Fallback direct upload endpoint for local development / testing
async function uploadLocalBinary(req, res, next) {
  try {
    const { evidenceId, token } = req.query;
    if (!evidenceId || !token) {
      return res.status(400).json({
        success: false,
        error: { code: 'INVALID_REQUEST', message: 'evidenceId and valid signed upload token are required' }
      });
    }

    const evidence = await Evidence.findOne({ evidenceId, isDeleted: false });
    if (!evidence) {
      return res.status(404).json({
        success: false,
        error: { code: 'EVIDENCE_NOT_FOUND', message: 'Evidence not found' }
      });
    }

    if (!storageService.verifyLocalUploadToken(evidenceId, evidence.farmerId, token)) {
      return res.status(403).json({
        success: false,
        error: { code: 'FORBIDDEN', message: 'Invalid or expired upload authorization token' }
      });
    }

    // Read incoming raw binary body with size guard
    const chunks = [];
    let totalBytes = 0;
    req.on('data', chunk => {
      totalBytes += chunk.length;
      if (totalBytes > config.storage.maxFileSizeBytes) {
        req.destroy(new Error(`Payload exceeds maximum file size of ${config.storage.maxFileSizeBytes} bytes`));
        return;
      }
      chunks.push(chunk);
    });

    req.on('end', async () => {
      try {
        const buffer = Buffer.concat(chunks);
        await storageService.saveLocalFile(evidence.s3ObjectKey, buffer);
        res.status(200).json({
          success: true,
          message: 'Binary evidence file written to local storage vault successfully.'
        });
      } catch (saveErr) {
        next(saveErr);
      }
    });

    req.on('error', err => {
      res.status(400).json({
        success: false,
        error: { code: 'UPLOAD_FAILED', message: err.message }
      });
    });
  } catch (err) {
    next(err);
  }
}

// Streaming download for local mode with mandatory authentication / signed token verification
async function streamEvidenceFile(req, res, next) {
  try {
    const objectKey = decodeURIComponent(req.params.objectKey);

    // Verify evidence exists
    const evidence = await Evidence.findOne({ s3ObjectKey: objectKey, isDeleted: false });
    if (!evidence) {
      return res.status(404).json({
        success: false,
        error: { code: 'EVIDENCE_NOT_FOUND', message: 'Evidence object not found' }
      });
    }

    // Authorization Check: Must satisfy either signed token or valid JWT authorization
    let isAuthorized = false;

    // Check 1: HMAC signed download token
    const token = req.query.token;
    if (token && storageService.verifyLocalDownloadToken(objectKey, evidence.farmerId, token)) {
      isAuthorized = true;
    }

    // Check 2: Bearer JWT Authorization header
    if (!isAuthorized && req.headers.authorization && req.headers.authorization.startsWith('Bearer ')) {
      try {
        const bearerToken = req.headers.authorization.split(' ')[1];
        const decoded = jwt.verify(bearerToken, config.jwt.accessSecret, {
          issuer: config.jwt.issuer,
          audience: config.jwt.audience
        });
        if (decoded && decoded.farmerId === evidence.farmerId) {
          isAuthorized = true;
        }
      } catch (authErr) {
        // Fall through to forbidden
      }
    }

    if (!isAuthorized) {
      return res.status(403).json({
        success: false,
        error: { code: 'FORBIDDEN', message: 'You are not authorized to view or stream this evidence file' }
      });
    }

    const buffer = await storageService.getObjectBuffer(objectKey);
    const mime = storageService.detectMimeTypeFromMagicBytes(buffer) || 'application/octet-stream';

    res.setHeader('Content-Type', mime);
    res.setHeader('Content-Length', buffer.length);
    res.setHeader('Cache-Control', 'private, max-age=900');
    res.send(buffer);
  } catch (err) {
    if (err.message && (err.message.includes('Path traversal') || err.message.includes('outside local storage'))) {
      return res.status(400).json({
        success: false,
        error: { code: 'BAD_REQUEST', message: err.message }
      });
    }
    next(err);
  }
}

module.exports = {
  requestUploadUrl,
  completeUpload,
  getRecordEvidence,
  getEvidenceById,
  getDownloadUrl,
  deleteEvidence,
  uploadLocalBinary,
  streamEvidenceFile
};
