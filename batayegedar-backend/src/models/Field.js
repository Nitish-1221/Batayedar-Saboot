const mongoose = require('mongoose');

const fieldSchema = new mongoose.Schema(
  {
    fieldId: {
      type: String,
      required: true,
      unique: true
    },
    farmerId: {
      type: String,
      required: true
    },
    fieldName: {
      type: String,
      required: true,
      trim: true,
      maxlength: 120
    },
    description: {
      type: String,
      trim: true,
      default: '',
      maxlength: 500
    },
    village: {
      type: String,
      required: true,
      trim: true
    },
    district: {
      type: String,
      required: true,
      trim: true
    },
    state: {
      type: String,
      required: true,
      trim: true
    },
    areaAcres: {
      type: Number,
      min: 0,
      default: 0
    },
    surveyKhasraNumber: {
      type: String,
      trim: true,
      default: ''
    },
    isDeleted: {
      type: Boolean,
      default: false,
      index: true
    }
  },
  {
    timestamps: { createdAt: 'createdAt', updatedAt: 'updatedAt' }
  }
);

fieldSchema.index({ farmerId: 1, isDeleted: 1 });

module.exports = mongoose.model('Field', fieldSchema);
