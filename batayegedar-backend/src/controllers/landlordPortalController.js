const LandlordConfirmation = require('../models/LandlordConfirmation');
const CropRecord = require('../models/CropRecord');
const Field = require('../models/Field');
const Evidence = require('../models/Evidence');
const User = require('../models/User');
const { recordAudit } = require('../services/auditService');

function getLandlordQuery(user) {
  return {
    $or: [
      { landlordPhone: user.phone },
      { landlordId: user.userId }
    ]
  };
}

async function getDashboard(req, res, next) {
  try {
    const landlordFilter = getLandlordQuery(req.user);

    const pendingCount = await LandlordConfirmation.countDocuments({
      ...landlordFilter,
      status: 'REQUESTED'
    });

    const completedCount = await LandlordConfirmation.countDocuments({
      ...landlordFilter,
      status: { $ne: 'REQUESTED' }
    });

    const recentConfirmations = await LandlordConfirmation.find(landlordFilter)
      .sort({ updatedAt: -1, requestedAt: -1 })
      .limit(10);

    const recordIds = [...new Set(recentConfirmations.map(c => c.recordId))];
    const farmerIds = [...new Set(recentConfirmations.map(c => c.farmerId))];

    const [cropRecords, farmers] = await Promise.all([
      CropRecord.find({ recordId: { $in: recordIds } }),
      User.find({ userId: { $in: farmerIds } })
    ]);

    const fieldIds = [...new Set(cropRecords.map(r => r.fieldId).filter(Boolean))];
    const fields = await Field.find({ fieldId: { $in: fieldIds } });
    const fieldMap = new Map(fields.map(f => [f.fieldId, f]));

    const recordMap = new Map(cropRecords.map(r => [r.recordId, r]));
    const farmerMap = new Map(farmers.map(f => [f.userId, f]));

    const recentActivity = recentConfirmations.map(conf => {
      const rec = recordMap.get(conf.recordId);
      const farmer = farmerMap.get(conf.farmerId);
      const field = rec ? fieldMap.get(rec.fieldId) : null;
      return {
        confirmationId: conf.confirmationId,
        recordId: conf.recordId,
        status: conf.status,
        requestedAt: conf.requestedAt,
        respondedAt: conf.respondedAt,
        fieldName: field ? field.fieldName : (rec?.fieldName || 'Unknown Field'),
        cropType: rec ? rec.cropType : 'Unknown Crop',
        farmerName: farmer ? farmer.fullName : 'Unknown Farmer',
        farmerPhone: farmer ? farmer.phone : ''
      };
    });

    res.status(200).json({
      success: true,
      data: {
        pendingCount,
        completedCount,
        totalRequests: pendingCount + completedCount,
        recentActivity
      }
    });
  } catch (err) {
    next(err);
  }
}

async function getRequests(req, res, next) {
  try {
    const landlordFilter = getLandlordQuery(req.user);
    const { status } = req.query;

    const query = { ...landlordFilter };
    if (status && status !== 'ALL') {
      query.status = status;
    }

    const confirmations = await LandlordConfirmation.find(query).sort({ requestedAt: -1 });

    const recordIds = [...new Set(confirmations.map(c => c.recordId))];
    const farmerIds = [...new Set(confirmations.map(c => c.farmerId))];

    const [cropRecords, farmers, evidences] = await Promise.all([
      CropRecord.find({ recordId: { $in: recordIds } }),
      User.find({ userId: { $in: farmerIds } }),
      Evidence.find({ recordId: { $in: recordIds }, isDeleted: false })
    ]);

    const fieldIds = [...new Set(cropRecords.map(r => r.fieldId).filter(Boolean))];
    const fields = await Field.find({ fieldId: { $in: fieldIds } });
    const fieldMap = new Map(fields.map(f => [f.fieldId, f]));

    const recordMap = new Map(cropRecords.map(r => [r.recordId, r]));
    const farmerMap = new Map(farmers.map(f => [f.userId, f]));

    // Group evidence count by recordId
    const evidenceCountMap = new Map();
    const evidencePhotosMap = new Map();
    for (const ev of evidences) {
      evidenceCountMap.set(ev.recordId, (evidenceCountMap.get(ev.recordId) || 0) + 1);
      if (!evidencePhotosMap.has(ev.recordId)) {
        evidencePhotosMap.set(ev.recordId, []);
      }
      evidencePhotosMap.get(ev.recordId).push({
        evidenceId: ev.evidenceId,
        photoSha256: ev.sha256Checksum,
        latitude: ev.latitude,
        longitude: ev.longitude,
        accuracyMeters: ev.accuracyMeters,
        capturedAtLocal: ev.capturedAtLocalFormatted || ev.createdAt,
        imageUrl: `/api/v1/evidence/file/${ev.s3ObjectKey}`
      });
    }

    const items = confirmations.map(conf => {
      const rec = recordMap.get(conf.recordId);
      const farmer = farmerMap.get(conf.farmerId);
      const field = rec ? fieldMap.get(rec.fieldId) : null;
      return {
        confirmationId: conf.confirmationId,
        recordId: conf.recordId,
        status: conf.status,
        requestedAt: conf.requestedAt,
        respondedAt: conf.respondedAt,
        notes: conf.notes,
        witnessName: conf.witnessName,
        farmer: {
          farmerId: conf.farmerId,
          fullName: farmer ? farmer.fullName : 'Farmer',
          phone: farmer ? farmer.phone : '',
          village: farmer ? farmer.village : (field ? field.village : ''),
          district: farmer ? farmer.district : (field ? field.district : ''),
          state: farmer ? farmer.state : (field ? field.state : '')
        },
        cropRecord: rec ? {
          recordId: rec.recordId,
          fieldName: field ? field.fieldName : (rec.fieldName || 'Plot'),
          cropType: rec.cropType,
          sowingDate: rec.cultivationStartDate ? rec.cultivationStartDate.toISOString().split('T')[0] : '',
          fieldDescription: rec.notes || '',
          village: field ? field.village : '',
          district: field ? field.district : '',
          state: field ? field.state : ''
        } : null,
        evidenceCount: evidenceCountMap.get(conf.recordId) || 0,
        evidencePhotos: evidencePhotosMap.get(conf.recordId) || []
      };
    });

    res.status(200).json({
      success: true,
      data: {
        total: items.length,
        requests: items
      }
    });
  } catch (err) {
    next(err);
  }
}

async function getRequestDetails(req, res, next) {
  try {
    const { confirmationId } = req.params;

    const confirmation = await LandlordConfirmation.findOne({ confirmationId });
    if (!confirmation) {
      return res.status(404).json({
        success: false,
        error: { code: 'CONFIRMATION_NOT_FOUND', message: 'Confirmation request not found.' }
      });
    }

    // Security Authorization: Landlord must be the intended recipient
    const matchesPhone = confirmation.landlordPhone === req.user.phone;
    const matchesId = confirmation.landlordId && confirmation.landlordId === req.user.userId;
    if (!matchesPhone && !matchesId) {
      return res.status(403).json({
        success: false,
        error: {
          code: 'FORBIDDEN_REQUEST',
          message: 'Access denied: You are not authorized to view this confirmation request.'
        }
      });
    }

    const [cropRecord, farmer, evidences] = await Promise.all([
      CropRecord.findOne({ recordId: confirmation.recordId }),
      User.findOne({ userId: confirmation.farmerId }),
      Evidence.find({ recordId: confirmation.recordId, isDeleted: false }).sort({ createdAt: -1 })
    ]);

    const field = cropRecord ? await Field.findOne({ fieldId: cropRecord.fieldId }) : null;

    const evidencePhotos = evidences.map(ev => ({
      evidenceId: ev.evidenceId,
      photoSha256: ev.sha256Checksum,
      latitude: ev.latitude,
      longitude: ev.longitude,
      altitude: ev.altitude,
      accuracyMeters: ev.accuracyMeters,
      gpsFixAgeMs: ev.gpsFixAgeMs,
      capturedAtLocal: ev.capturedAtLocalFormatted,
      capturedAtUtc: ev.capturedAtUtc,
      deviceModel: ev.deviceModel,
      validationStatus: ev.validationStatus,
      imageUrl: `/api/v1/evidence/file/${ev.s3ObjectKey}`
    }));

    res.status(200).json({
      success: true,
      data: {
        confirmationId: confirmation.confirmationId,
        recordId: confirmation.recordId,
        status: confirmation.status,
        requestedAt: confirmation.requestedAt,
        respondedAt: confirmation.respondedAt,
        notes: confirmation.notes,
        witnessName: confirmation.witnessName,
        canRespond: confirmation.status === 'REQUESTED',

        // Farmer-provided declarations (clearly segregated)
        farmerDeclaredInfo: {
          farmerId: confirmation.farmerId,
          farmerName: farmer ? farmer.fullName : 'Farmer',
          farmerPhone: farmer ? farmer.phone : '',
          farmerLocation: `${farmer?.village || ''}, ${farmer?.district || ''}, ${farmer?.state || ''}`.trim().replace(/^,\s*|,\s*$/g, ''),
          fieldName: field ? field.fieldName : (cropRecord ? cropRecord.fieldName : ''),
          cropType: cropRecord ? cropRecord.cropType : '',
          sowingDate: cropRecord?.cultivationStartDate ? cropRecord.cultivationStartDate.toISOString().split('T')[0] : '',
          fieldDescription: cropRecord ? cropRecord.notes : '',
          declaredAt: cropRecord ? cropRecord.createdAt : null,
          note: 'The above information was provided directly by the tenant farmer.'
        },

        // Verified device & GPS telemetry (clearly segregated)
        independentlyVerifiedTelemetry: {
          totalEvidenceCount: evidences.length,
          evidencePhotos,
          gpsMethod: 'Android Hardware GPS Satellite Lock (< 20m accuracy threshold)',
          integrityProtection: 'Cryptographic SHA-256 fingerprint on original photograph files',
          note: 'Coordinates and timestamps were recorded directly by device hardware sensors.'
        }
      }
    });
  } catch (err) {
    next(err);
  }
}

async function respondToRequest(req, res, next) {
  try {
    const { confirmationId } = req.params;
    const { status, notes, witnessName } = req.body;

    const confirmation = await LandlordConfirmation.findOne({ confirmationId });
    if (!confirmation) {
      return res.status(404).json({
        success: false,
        error: { code: 'CONFIRMATION_NOT_FOUND', message: 'Confirmation request not found.' }
      });
    }

    // Security Authorization: Landlord must be the intended recipient
    const matchesPhone = confirmation.landlordPhone === req.user.phone;
    const matchesId = confirmation.landlordId && confirmation.landlordId === req.user.userId;
    if (!matchesPhone && !matchesId) {
      return res.status(403).json({
        success: false,
        error: {
          code: 'FORBIDDEN_REQUEST',
          message: 'Access denied: You are not authorized to respond to this confirmation request.'
        }
      });
    }

    // Prevent accidental duplicate response
    if (confirmation.status !== 'REQUESTED') {
      return res.status(409).json({
        success: false,
        error: {
          code: 'ALREADY_RESPONDED',
          message: `This confirmation request has already been responded to with status: ${confirmation.status}.`
        }
      });
    }

    confirmation.status = status;
    confirmation.respondedAt = new Date();
    confirmation.landlordId = req.user.userId;
    if (notes) confirmation.notes = notes;
    if (witnessName) confirmation.witnessName = witnessName;
    confirmation.auditDetails = {
      ...(confirmation.auditDetails || {}),
      respondedByLandlordId: req.user.userId,
      respondedByPhone: req.user.phone,
      respondedAt: new Date().toISOString(),
      clientIp: req.ip
    };

    await confirmation.save();

    // Update associated CropRecord's landlordConfirmationStatus
    const cropRecord = await CropRecord.findOne({ recordId: confirmation.recordId });
    if (cropRecord) {
      cropRecord.landlordConfirmationStatus = status;
      await cropRecord.save();
    }

    await recordAudit({
      action: 'LANDLORD_CONFIRMATION_RESPONDED',
      actorId: req.user.userId,
      resourceType: 'CONFIRMATION',
      resourceId: confirmation.confirmationId,
      details: {
        status,
        landlordId: req.user.userId,
        landlordPhone: req.user.phone,
        notes: confirmation.notes,
        recordId: confirmation.recordId
      },
      req
    });

    res.status(200).json({
      success: true,
      message: 'Confirmation response recorded successfully.',
      data: { confirmation }
    });
  } catch (err) {
    next(err);
  }
}

async function getHistory(req, res, next) {
  try {
    const landlordFilter = getLandlordQuery(req.user);

    const confirmations = await LandlordConfirmation.find({
      ...landlordFilter,
      status: { $ne: 'REQUESTED' }
    }).sort({ respondedAt: -1, updatedAt: -1 });

    const recordIds = [...new Set(confirmations.map(c => c.recordId))];
    const farmerIds = [...new Set(confirmations.map(c => c.farmerId))];

    const [cropRecords, farmers] = await Promise.all([
      CropRecord.find({ recordId: { $in: recordIds } }),
      User.find({ userId: { $in: farmerIds } })
    ]);

    const recordMap = new Map(cropRecords.map(r => [r.recordId, r]));
    const farmerMap = new Map(farmers.map(f => [f.userId, f]));

    const historyItems = confirmations.map(conf => {
      const rec = recordMap.get(conf.recordId);
      const farmer = farmerMap.get(conf.farmerId);
      return {
        confirmationId: conf.confirmationId,
        recordId: conf.recordId,
        status: conf.status,
        requestedAt: conf.requestedAt,
        respondedAt: conf.respondedAt,
        notes: conf.notes,
        witnessName: conf.witnessName,
        farmerName: farmer ? farmer.fullName : 'Farmer',
        farmerPhone: farmer ? farmer.phone : '',
        fieldName: rec ? rec.fieldName : 'Field',
        cropType: rec ? rec.cropType : 'Crop'
      };
    });

    res.status(200).json({
      success: true,
      data: {
        total: historyItems.length,
        history: historyItems
      }
    });
  } catch (err) {
    next(err);
  }
}

module.exports = {
  getDashboard,
  getRequests,
  getRequestDetails,
  respondToRequest,
  getHistory
};
