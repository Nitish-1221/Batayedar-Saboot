const crypto = require('crypto');
const Field = require('../models/Field');
const { recordAudit } = require('../services/auditService');

async function createField(req, res, next) {
  try {
    const { fieldId, fieldName, description, village, district, state, areaAcres, surveyKhasraNumber } = req.body;

    const id = fieldId || crypto.randomUUID();

    const field = new Field({
      fieldId: id,
      farmerId: req.farmerId,
      fieldName,
      description: description || '',
      village,
      district,
      state,
      areaAcres: areaAcres || 0,
      surveyKhasraNumber: surveyKhasraNumber || ''
    });

    await field.save();

    await recordAudit({
      action: 'FIELD_CREATED',
      actorId: req.farmerId,
      resourceType: 'FIELD',
      resourceId: field.fieldId,
      details: { fieldName: field.fieldName },
      req
    });

    res.status(201).json({
      success: true,
      message: 'Field created successfully',
      data: { field }
    });
  } catch (err) {
    next(err);
  }
}

async function getFields(req, res, next) {
  try {
    const page = parseInt(req.query.page, 10) || 1;
    const limit = Math.min(parseInt(req.query.limit, 10) || 50, 100);
    const skip = (page - 1) * limit;

    const filter = { farmerId: req.farmerId, isDeleted: false };

    const [fields, total] = await Promise.all([
      Field.find(filter).sort({ createdAt: -1 }).skip(skip).limit(limit),
      Field.countDocuments(filter)
    ]);

    res.status(200).json({
      success: true,
      data: {
        fields,
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

async function getFieldById(req, res, next) {
  try {
    const { fieldId } = req.params;

    const field = await Field.findOne({ fieldId, isDeleted: false });
    if (!field) {
      return res.status(404).json({
        success: false,
        error: { code: 'FIELD_NOT_FOUND', message: 'Field not found' }
      });
    }

    // Strict multi-tenant authorization
    if (field.farmerId !== req.farmerId) {
      return res.status(403).json({
        success: false,
        error: { code: 'FORBIDDEN', message: 'You are not authorized to access this field' }
      });
    }

    res.status(200).json({
      success: true,
      data: { field }
    });
  } catch (err) {
    next(err);
  }
}

async function updateField(req, res, next) {
  try {
    const { fieldId } = req.params;

    const field = await Field.findOne({ fieldId, isDeleted: false });
    if (!field) {
      return res.status(404).json({
        success: false,
        error: { code: 'FIELD_NOT_FOUND', message: 'Field not found' }
      });
    }

    if (field.farmerId !== req.farmerId) {
      return res.status(403).json({
        success: false,
        error: { code: 'FORBIDDEN', message: 'You are not authorized to update this field' }
      });
    }

    const { fieldName, description, village, district, state, areaAcres, surveyKhasraNumber } = req.body;
    if (fieldName !== undefined) field.fieldName = fieldName;
    if (description !== undefined) field.description = description;
    if (village !== undefined) field.village = village;
    if (district !== undefined) field.district = district;
    if (state !== undefined) field.state = state;
    if (areaAcres !== undefined) field.areaAcres = areaAcres;
    if (surveyKhasraNumber !== undefined) field.surveyKhasraNumber = surveyKhasraNumber;

    await field.save();

    await recordAudit({
      action: 'FIELD_UPDATED',
      actorId: req.farmerId,
      resourceType: 'FIELD',
      resourceId: field.fieldId,
      details: { updatedFields: Object.keys(req.body) },
      req
    });

    res.status(200).json({
      success: true,
      message: 'Field updated successfully',
      data: { field }
    });
  } catch (err) {
    next(err);
  }
}

async function deleteField(req, res, next) {
  try {
    const { fieldId } = req.params;

    const field = await Field.findOne({ fieldId, isDeleted: false });
    if (!field) {
      return res.status(404).json({
        success: false,
        error: { code: 'FIELD_NOT_FOUND', message: 'Field not found' }
      });
    }

    if (field.farmerId !== req.farmerId) {
      return res.status(403).json({
        success: false,
        error: { code: 'FORBIDDEN', message: 'You are not authorized to delete this field' }
      });
    }

    field.isDeleted = true;
    await field.save();

    await recordAudit({
      action: 'FIELD_DELETED',
      actorId: req.farmerId,
      resourceType: 'FIELD',
      resourceId: field.fieldId,
      details: {},
      req
    });

    res.status(200).json({
      success: true,
      message: 'Field deleted successfully'
    });
  } catch (err) {
    next(err);
  }
}

module.exports = {
  createField,
  getFields,
  getFieldById,
  updateField,
  deleteField
};
