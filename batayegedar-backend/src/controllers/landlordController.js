const crypto = require('crypto');
const LandlordConfirmation = require('../models/LandlordConfirmation');
const CropRecord = require('../models/CropRecord');
const { recordAudit } = require('../services/auditService');

async function requestConfirmation(req, res, next) {
  try {
    const { recordId } = req.params;
    const { landlordName, landlordPhone, notes, verificationMethod } = req.body;

    const cropRecord = await CropRecord.findOne({ recordId, status: { $ne: 'DELETED' } });
    if (!cropRecord) {
      return res.status(404).json({
        success: false,
        error: { code: 'RECORD_NOT_FOUND', message: 'Crop record not found' }
      });
    }

    if (cropRecord.farmerId !== req.farmerId) {
      return res.status(403).json({
        success: false,
        error: { code: 'FORBIDDEN', message: 'You are not authorized to request landlord confirmation for this record' }
      });
    }

    const confirmationId = crypto.randomUUID();

    // Link only to a valid registered landlord account
    const User = require('../models/User');
    const cleanPhone = landlordPhone.trim();
    const existingLandlord = await User.findOne({
      $or: [
        { phone: cleanPhone },
        { phone: cleanPhone.replace('+91', '') },
        { phone: '+91' + cleanPhone.replace('+91', '') }
      ],
      role: 'LANDLORD'
    });

    if (!existingLandlord) {
      return res.status(404).json({
        success: false,
        error: {
          code: 'LANDLORD_NOT_REGISTERED',
          message: `No registered landlord account found with phone number ${landlordPhone}. Confirmation cannot be requested until the landlord registers an account. You can still save and export your cultivation evidence without a landlord.`
        }
      });
    }

    const confirmation = new LandlordConfirmation({
      confirmationId,
      recordId,
      farmerId: req.farmerId,
      landlordName: landlordName.trim(),
      landlordPhone: cleanPhone,
      landlordId: existingLandlord.userId,
      status: 'REQUESTED',
      requestedAt: new Date(),
      verificationMethod: verificationMethod || 'DIRECT_VERBAL',
      notes: notes || '',
      auditDetails: {
        createdVia: 'api',
        clientIp: req.ip
      }
    });

    await confirmation.save();

    // Update crop record landlord details and status
    cropRecord.landlordName = landlordName.trim();
    cropRecord.landlordPhone = cleanPhone;
    cropRecord.landlordConfirmationStatus = 'REQUESTED';
    await cropRecord.save();

    await recordAudit({
      action: 'LANDLORD_CONFIRMATION_REQUESTED',
      actorId: req.farmerId,
      resourceType: 'CONFIRMATION',
      resourceId: confirmation.confirmationId,
      details: { landlordName, recordId },
      req
    });

    res.status(201).json({
      success: true,
      message: 'Landlord confirmation request created successfully',
      data: { confirmation }
    });
  } catch (err) {
    next(err);
  }
}

async function respondConfirmation(req, res, next) {
  try {
    const { confirmationId } = req.params;
    const { status, notes, witnessName } = req.body;

    const confirmation = await LandlordConfirmation.findOne({ confirmationId });
    if (!confirmation) {
      return res.status(404).json({
        success: false,
        error: { code: 'CONFIRMATION_NOT_FOUND', message: 'Confirmation record not found' }
      });
    }

    if (confirmation.farmerId !== req.farmerId) {
      return res.status(403).json({
        success: false,
        error: { code: 'FORBIDDEN', message: 'You are not authorized to update this confirmation' }
      });
    }

    confirmation.status = status;
    confirmation.respondedAt = new Date();
    if (notes) confirmation.notes = notes;
    if (witnessName) confirmation.witnessName = witnessName;
    confirmation.auditDetails = {
      ...(confirmation.auditDetails || {}),
      respondedVia: 'api',
      recordedAt: new Date().toISOString()
    };

    await confirmation.save();

    await recordAudit({
      action: 'LANDLORD_CONFIRMATION_RESPONDED',
      actorId: req.farmerId,
      resourceType: 'CONFIRMATION',
      resourceId: confirmation.confirmationId,
      details: { status, witnessName },
      req
    });

    res.status(200).json({
      success: true,
      message: 'Landlord confirmation status recorded successfully',
      data: { confirmation }
    });
  } catch (err) {
    next(err);
  }
}

async function getRecordConfirmations(req, res, next) {
  try {
    const { recordId } = req.params;

    const cropRecord = await CropRecord.findOne({ recordId, status: { $ne: 'DELETED' } });
    if (!cropRecord) {
      return res.status(404).json({
        success: false,
        error: { code: 'RECORD_NOT_FOUND', message: 'Crop record not found' }
      });
    }

    if (cropRecord.farmerId !== req.farmerId) {
      return res.status(403).json({
        success: false,
        error: { code: 'FORBIDDEN', message: 'You are not authorized to view confirmations for this record' }
      });
    }

    const confirmations = await LandlordConfirmation.find({ recordId }).sort({ createdAt: -1 });

    res.status(200).json({
      success: true,
      data: { confirmations }
    });
  } catch (err) {
    next(err);
  }
}

module.exports = {
  requestConfirmation,
  respondConfirmation,
  getRecordConfirmations
};
