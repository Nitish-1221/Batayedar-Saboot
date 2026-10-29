const mongoose = require('mongoose');

const evidenceSchema = new mongoose.Schema(
  {
    evidenceId: {
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
    s3ObjectKey: {
      type: String,
      required: true
    },
    s3Bucket: {
      type: String,
      required: true
    },
    sha256Checksum: {
      type: String,
      default: '' // Server-calculated SHA-256 hash
    },
    clientReportedChecksum: {
      type: String,
      default: '' // Client-calculated SHA-256 hash
    },
    fileSizeBytes: {
      type: Number,
      default: 0
    },
    mimeType: {
      type: String,
      default: 'image/jpeg'
    },
    latitude: {
      type: Number,
      required: true,
      min: -90,
      max: 90
    },
    longitude: {
      type: Number,
      required: true,
      min: -180,
      max: 180
    },
    altitude: {
      type: Number,
      default: 0
    },
    reportedGpsAccuracyMeters: {
      type: Number,
      required: true
    },
    clientFixAgeSeconds: {
      type: Number,
      default: 0
    },
    clientCapturedAtUtc: {
      type: Date,
      required: true
    },
    serverReceivedAtUtc: {
      type: Date,
      default: Date.now
    },
    validationStatus: {
      type: String,
      enum: ['PENDING_UPLOAD', 'VERIFIED', 'FAILED_INTEGRITY', 'FLAGGED_INACCURATE', 'REJECTED'],
      default: 'PENDING_UPLOAD',
      index: true
    },
    gpsAccuracyAcceptable: {
      type: Boolean,
      default: true
    },
    hashMatch: {
      type: Boolean,
      default: false
    },
    deviceManufacturer: {
      type: String,
      default: ''
    },
    deviceModel: {
      type: String,
      default: ''
    },
    notes: {
      type: String,
      default: '',
      maxlength: 1000
    },
    legalDisclaimerNotice: {
      type: String,
      default: 'Treat GPS metadata and photographs as supporting evidence. They do not independently prove land ownership, legal tenancy, or cultivation entitlement.'
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

evidenceSchema.index({ recordId: 1, isDeleted: 1 });
evidenceSchema.index({ farmerId: 1, isDeleted: 1 });

module.exports = mongoose.model('Evidence', evidenceSchema);
