const crypto = require('crypto');
const CropRecord = require('../models/CropRecord');
const Field = require('../models/Field');
const { recordAudit } = require('../services/auditService');

async function createCropRecord(req, res, next) {
  try {
    const { recordId, fieldId, cropType, cultivationStartDate, notes, landlordName, landlordPhone } = req.body;

    // Verify field ownership
    const field = await Field.findOne({ fieldId, isDeleted: false });
    if (!field) {
      return res.status(404).json({
        success: false,
        error: { code: 'FIELD_NOT_FOUND', message: 'Associated field does not exist' }
      });
    }

    if (field.farmerId !== req.farmerId) {
      return res.status(403).json({
        success: false,
        error: { code: 'FORBIDDEN', message: 'You do not own the field specified for this crop record' }
      });
    }

    const id = recordId || crypto.randomUUID();

    const cropRecord = new CropRecord({
      recordId: id,
      farmerId: req.farmerId,
      fieldId,
      cropType,
      cultivationStartDate: new Date(cultivationStartDate),
      notes: notes || '',
      landlordName: landlordName || '',
      landlordPhone: landlordPhone || '',
      landlordConfirmationStatus: 'NOT_REQUESTED',
      status: 'ACTIVE'
    });

    await cropRecord.save();

    await recordAudit({
      action: 'CROP_RECORD_CREATED',
      actorId: req.farmerId,
      resourceType: 'CROP_RECORD',
      resourceId: cropRecord.recordId,
      details: { cropType: cropRecord.cropType, fieldId },
      req
    });

    res.status(201).json({
      success: true,
      message: 'Crop record created successfully',
      data: { record: cropRecord }
    });
  } catch (err) {
    next(err);
  }
}

async function getCropRecords(req, res, next) {
  try {
    const page = parseInt(req.query.page, 10) || 1;
    const limit = Math.min(parseInt(req.query.limit, 10) || 50, 100);
    const skip = (page - 1) * limit;

    const filter = { farmerId: req.farmerId, status: { $ne: 'DELETED' } };
    if (req.query.fieldId) {
      filter.fieldId = req.query.fieldId;
    }

    const [records, total] = await Promise.all([
      CropRecord.find(filter).sort({ createdAt: -1 }).skip(skip).limit(limit),
      CropRecord.countDocuments(filter)
    ]);

    res.status(200).json({
      success: true,
      data: {
        records,
        pagination: {
          total,
          page,
          limit,
          totalPages: Math.ceil(total / limit)
        }
      }
    });
  } catch (err) {
    next(err);
  }
}

async function getCropRecordById(req, res, next) {
  try {
    const { recordId } = req.params;

    const record = await CropRecord.findOne({ recordId, status: { $ne: 'DELETED' } });
    if (!record) {
      return res.status(404).json({
        success: false,
        error: { code: 'RECORD_NOT_FOUND', message: 'Crop record not found' }
      });
    }

    if (record.farmerId !== req.farmerId) {
      return res.status(403).json({
        success: false,
        error: { code: 'FORBIDDEN', message: 'You are not authorized to access this crop record' }
      });
    }

    res.status(200).json({
      success: true,
      data: { record }
    });
  } catch (err) {
    next(err);
  }
}

async function updateCropRecord(req, res, next) {
  try {
    const { recordId } = req.params;

    const record = await CropRecord.findOne({ recordId, status: { $ne: 'DELETED' } });
    if (!record) {
      return res.status(404).json({
        success: false,
        error: { code: 'RECORD_NOT_FOUND', message: 'Crop record not found' }
      });
    }

    if (record.farmerId !== req.farmerId) {
      return res.status(403).json({
        success: false,
        error: { code: 'FORBIDDEN', message: 'You are not authorized to update this crop record' }
      });
    }

    const { cropType, cultivationStartDate, notes, landlordName, landlordPhone, status } = req.body;
    if (cropType !== undefined) record.cropType = cropType;
    if (cultivationStartDate !== undefined) record.cultivationStartDate = new Date(cultivationStartDate);
    if (notes !== undefined) record.notes = notes;
    if (landlordName !== undefined) record.landlordName = landlordName;
    if (landlordPhone !== undefined) record.landlordPhone = landlordPhone;
    if (status !== undefined) record.status = status;
    record.syncVersion = (record.syncVersion || 1) + 1;

    await record.save();

    await recordAudit({
      action: 'CROP_RECORD_UPDATED',
      actorId: req.farmerId,
      resourceType: 'CROP_RECORD',
      resourceId: record.recordId,
      details: { updatedFields: Object.keys(req.body) },
      req
    });

    res.status(200).json({
      success: true,
      message: 'Crop record updated successfully',
      data: { record }
    });
  } catch (err) {
    next(err);
  }
}

async function deleteCropRecord(req, res, next) {
  try {
    const { recordId } = req.params;

    const record = await CropRecord.findOne({ recordId, status: { $ne: 'DELETED' } });
    if (!record) {
      return res.status(404).json({
        success: false,
        error: { code: 'RECORD_NOT_FOUND', message: 'Crop record not found' }
      });
    }

    if (record.farmerId !== req.farmerId) {
      return res.status(403).json({
        success: false,
        error: { code: 'FORBIDDEN', message: 'You are not authorized to delete this crop record' }
      });
    }

    record.status = 'DELETED';
    record.syncVersion = (record.syncVersion || 1) + 1;
    await record.save();

    await recordAudit({
      action: 'CROP_RECORD_DELETED',
      actorId: req.farmerId,
      resourceType: 'CROP_RECORD',
      resourceId: record.recordId,
      details: {},
      req
    });

    res.status(200).json({
      success: true,
      message: 'Crop record soft-deleted successfully'
    });
  } catch (err) {
    next(err);
  }
}

module.exports = {
  createCropRecord,
  getCropRecords,
  getCropRecordById,
  updateCropRecord,
  deleteCropRecord
};
