const mongoose = require('mongoose');

const userSchema = new mongoose.Schema(
  {
    userId: {
      type: String,
      required: true,
      unique: true,
      index: true
    },
    firebaseUid: {
      type: String,
      sparse: true,
      unique: true,
      index: true
    },
    fullName: {
      type: String,
      required: true,
      trim: true,
      maxlength: 120
    },
    phone: {
      type: String,
      required: true,
      unique: true,
      index: true,
      trim: true
    },
    email: {
      type: String,
      trim: true,
      lowercase: true,
      sparse: true,
      index: true
    },
    passwordHash: {
      type: String,
      required: false
    },
    role: {
      type: String,
      enum: ['BATAYDAR', 'LANDLORD'],
      default: 'BATAYDAR',
      required: true,
      index: true
    },
    village: {
      type: String,
      trim: true,
      default: ''
    },
    district: {
      type: String,
      trim: true,
      default: ''
    },
    state: {
      type: String,
      trim: true,
      default: ''
    },
    preferredLanguage: {
      type: String,
      enum: ['en', 'hi'],
      default: 'en'
    },
    accountStatus: {
      type: String,
      enum: ['ACTIVE', 'SUSPENDED', 'DELETION_REQUESTED', 'DELETED'],
      default: 'ACTIVE',
      index: true
    },
    deletionRequestedAt: {
      type: Date
    }
  },
  {
    timestamps: { createdAt: 'createdAt', updatedAt: 'updatedAt' }
  }
);

userSchema.methods.toSafeObject = function () {
  return {
    userId: this.userId,
    firebaseUid: this.firebaseUid || this.userId,
    fullName: this.fullName,
    phone: this.phone,
    email: this.email || null,
    role: this.role || 'BATAYDAR',
    village: this.village,
    district: this.district,
    state: this.state,
    preferredLanguage: this.preferredLanguage,
    accountStatus: this.accountStatus,
    createdAt: this.createdAt,
    updatedAt: this.updatedAt
  };
};

module.exports = mongoose.model('User', userSchema);
