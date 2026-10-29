const mongoose = require('mongoose');

const auditLogSchema = new mongoose.Schema(
  {
    logId: {
      type: String,
      required: true,
      unique: true,
      index: true
    },
    action: {
      type: String,
      required: true,
      index: true
    },
    actorId: {
      type: String,
      required: true,
      index: true
    },
    resourceType: {
      type: String,
      enum: ['USER', 'FIELD', 'CROP_RECORD', 'EVIDENCE', 'CONFIRMATION', 'SYNC', 'AUTH'],
      required: true,
      index: true
    },
    resourceId: {
      type: String,
      required: true,
      index: true
    },
    details: {
      type: mongoose.Schema.Types.Mixed,
      default: {}
    },
    ipAddress: {
      type: String,
      default: 'unknown'
    },
    userAgent: {
      type: String,
      default: 'unknown'
    },
    timestamp: {
      type: Date,
      default: Date.now,
      immutable: true,
      index: true
    }
  },
  {
    timestamps: false,
    versionKey: false
  }
);

auditLogSchema.index({ resourceType: 1, resourceId: 1, timestamp: -1 });
auditLogSchema.index({ actorId: 1, timestamp: -1 });

module.exports = mongoose.model('AuditLog', auditLogSchema);
