const mongoose = require('mongoose');

/**
 * Server-Side Deletion Tombstone Model
 * Persists permanent deletion records for Evidence, Crop Records, and Fields.
 * Prevents older offline devices from resurrecting deleted data and ensures
 * cross-device deletions synchronize reliably without data returning upon restore.
 */
const deletionTombstoneSchema = new mongoose.Schema(
  {
    recordUuid: {
      type: String,
      required: true,
      index: true
    },
    recordType: {
      type: String,
      enum: ['EVIDENCE', 'CROP_RECORD', 'FIELD'],
      required: true,
      index: true
    },
    firebaseUid: {
      type: String,
      required: true,
      index: true
    },
    farmerId: {
      type: String,
      default: '',
      index: true
    },
    cloudinaryPublicId: {
      type: String,
      default: ''
    },
    deletedAt: {
      type: Date,
      default: Date.now,
      index: true
    },
    deletionStatus: {
      type: String,
      enum: ['PERMANENTLY_DELETED', 'PENDING_STORAGE_CLEANUP'],
      default: 'PERMANENTLY_DELETED'
    }
  },
  {
    timestamps: true
  }
);

deletionTombstoneSchema.index({ recordUuid: 1, firebaseUid: 1 }, { unique: true });
deletionTombstoneSchema.index({ firebaseUid: 1, deletedAt: 1 });

module.exports = mongoose.model('DeletionTombstone', deletionTombstoneSchema);

