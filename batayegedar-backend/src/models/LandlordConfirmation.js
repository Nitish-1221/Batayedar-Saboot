const mongoose = require('mongoose');

const landlordConfirmationSchema = new mongoose.Schema(
  {
    confirmationId: {
      type: String,
      required: true,
      unique: true
    },
    recordId: {
      type: String,
      required: true
    },
    farmerId: {
      type: String,
      required: true
    },
    landlordName: {
      type: String,
      required: true,
      trim: true
    },
    landlordPhone: {
      type: String,
      required: true,
      trim: true,
      index: true
    },
    landlordId: {
      type: String,
      sparse: true,
      index: true
    },
    status: {
      type: String,
      enum: ['NOT_REQUESTED', 'REQUESTED', 'CONFIRMED_VERBAL', 'CONFIRMED_WRITTEN', 'REFUSED', 'UNRESPONSIVE'],
      default: 'REQUESTED',
      index: true
    },
    requestedAt: {
      type: Date,
      default: Date.now
    },
    respondedAt: {
      type: Date
    },
    witnessName: {
      type: String,
      default: '',
      trim: true
    },
    notes: {
      type: String,
      default: '',
      maxlength: 1000
    },
    verificationMethod: {
      type: String,
      enum: ['PHONE_INQUIRY', 'DIRECT_VERBAL', 'SIGNED_AFFIDAVIT', 'SMS_CONFIRMATION', 'OTHER'],
      default: 'DIRECT_VERBAL'
    },
    auditDetails: {
      type: mongoose.Schema.Types.Mixed,
      default: {}
    }
  },
  {
    timestamps: { createdAt: 'createdAt', updatedAt: 'updatedAt' }
  }
);

landlordConfirmationSchema.index({ recordId: 1 });
landlordConfirmationSchema.index({ farmerId: 1 });

module.exports = mongoose.model('LandlordConfirmation', landlordConfirmationSchema);
