const Field = require('../models/Field');
const CropRecord = require('../models/CropRecord');
const Evidence = require('../models/Evidence');
const LandlordConfirmation = require('../models/LandlordConfirmation');
const { recordAudit } = require('../services/auditService');

async function pushSync(req, res, next) {
  try {
    const { idempotencyKey, fields, cropRecords, evidenceList } = req.body;
    const farmerId = req.farmerId;

    const syncedFieldIds = [];
    const syncedRecordIds = [];
    const syncedEvidenceIds = [];

    // 1. Process Fields with strict farmer ownership checks
    if (Array.isArray(fields)) {
      for (const f of fields) {
        if (!f.fieldId) continue;
        const existing = await Field.findOne({ fieldId: f.fieldId });
        if (existing) {
          if (existing.farmerId !== farmerId) {
            // Cross-tenant collision! Deny unauthorized claim
            continue;
          }
        } else {
          await Field.create({
            fieldId: f.fieldId,
            farmerId,
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

        const associatedField = await Field.findOne({ fieldId: r.fieldId, farmerId, isDeleted: false });
        if (!associatedField) {
          // Reject crop records linked to nonexistent or another farmer's field
          continue;
        }

        const existing = await CropRecord.findOne({ recordId: r.recordId });
        if (existing) {
          if (existing.farmerId !== farmerId) {
            continue;
          }
        } else {
          await CropRecord.create({
            recordId: r.recordId,
            farmerId,
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

        const associatedRecord = await CropRecord.findOne({ recordId: e.recordId, farmerId, status: { $ne: 'DELETED' } });
        if (!associatedRecord) {
          // Reject evidence linked to nonexistent or another farmer's crop record
          continue;
        }

        const existing = await Evidence.findOne({ evidenceId: e.evidenceId });
        if (existing) {
          if (existing.farmerId !== farmerId) {
            continue;
          }
        } else {
          await Evidence.create({
            evidenceId: e.evidenceId,
            recordId: e.recordId,
            farmerId,
            s3ObjectKey: `evidence/${farmerId}/${e.recordId}/${e.evidenceId}.jpg`,
            s3Bucket: 'pending',
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

async function pullSync(req, res, next) {
  try {
    const farmerId = req.farmerId;
    const since = req.query.since ? new Date(req.query.since) : new Date(0);

    const [fields, cropRecords, evidenceList, confirmations] = await Promise.all([
      Field.find({ farmerId, updatedAt: { $gte: since } }),
      CropRecord.find({ farmerId, updatedAt: { $gte: since } }),
      Evidence.find({ farmerId, updatedAt: { $gte: since } }),
      LandlordConfirmation.find({ farmerId, updatedAt: { $gte: since } })
    ]);

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

module.exports = {
  pushSync,
  pullSync
};
