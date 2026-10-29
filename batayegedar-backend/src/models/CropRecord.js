const mongoose = require('mongoose');

const cropRecordSchema = new mongoose.Schema(
  {
    recordId: {
      type: String,
      required: true,
      unique: true
    },
    farmerId: {
      type: String,
      required: true
    },
    fieldId: {
      type: String,
      required: true
    },
    cropType: {
      type: String,
      required: true,
      trim: true,
      maxlength: 120
    },
    cultivationStartDate: {
      type: Date,
      required: true
    },
    notes: {
      type: String,
      trim: true,
      default: '',
      maxlength: 1000
    },
    landlordName: {
      type: String,
      trim: true,
      default: ''
    },
    landlordPhone: {
      type: String,
      trim: true,
      default: ''
    },
    landlordConfirmationStatus: {
      type: String,
      enum: ['NOT_REQUESTED', 'REQUESTED', 'CONFIRMED_VERBAL', 'CONFIRMED_WRITTEN', 'REFUSED', 'UNRESPONSIVE'],
      default: 'NOT_REQUESTED',
      index: true
    },
    status: {
      type: String,
      enum: ['ACTIVE', 'ARCHIVED', 'DELETED'],
      default: 'ACTIVE',
      index: true
    },
    syncVersion: {
      type: Number,
      default: 1
    },
    lastSyncedAt: {
      type: Date,
      default: Date.now
    }
  },
  {
    timestamps: { createdAt: 'createdAt', updatedAt: 'updatedAt' }
  }
);

cropRecordSchema.index({ farmerId: 1, status: 1 });
cropRecordSchema.index({ fieldId: 1 });

module.exports = mongoose.model('CropRecord', cropRecordSchema);
