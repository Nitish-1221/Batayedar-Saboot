require('dotenv').config();

const config = {
  env: process.env.NODE_ENV || 'development',
  port: parseInt(process.env.PORT, 10) || 5000,
  apiPrefix: process.env.API_PREFIX || '/api/v1',

  mongo: {
    uri: process.env.MONGODB_URI || 'mongodb://localhost:27017/batayedar_saboot'
  },

  jwt: {
    accessSecret: process.env.JWT_ACCESS_SECRET || 'dev_secret_access_key_min_64_chars_batayedar_saboot_app_dev_mode_only_12345678',
    refreshSecret: process.env.JWT_REFRESH_SECRET || 'dev_secret_refresh_key_min_64_chars_batayedar_saboot_app_dev_mode_only_87654321',
    accessExpiry: process.env.JWT_ACCESS_EXPIRY || '15m',
    refreshExpiry: process.env.JWT_REFRESH_EXPIRY || '7d',
    issuer: process.env.JWT_ISSUER || 'batayedar-saboot-auth',
    audience: process.env.JWT_AUDIENCE || 'batayedar-saboot-client'
  },

  cors: {
    allowedOrigins: (process.env.CORS_ALLOWED_ORIGINS || 'http://localhost:3000,http://localhost:5173,http://localhost:5000')
      .split(',')
      .map(o => o.trim())
  },

  rateLimit: {
    windowMs: parseInt(process.env.RATE_LIMIT_WINDOW_MS, 10) || 15 * 60 * 1000,
    maxRequests: parseInt(process.env.RATE_LIMIT_MAX_REQUESTS, 10) || 100,
    authMaxRequests: parseInt(process.env.AUTH_RATE_LIMIT_MAX, 10) || 10
  },

  firebase: {
    projectId: process.env.FIREBASE_PROJECT_ID || 'batayedar-saboot',
    serviceAccountPath: process.env.FIREBASE_SERVICE_ACCOUNT || null,
    serviceAccountKey: process.env.FIREBASE_SERVICE_ACCOUNT_KEY || null
  },

  storage: {
    provider: process.env.STORAGE_PROVIDER || 'local', // 's3' or 'local'
    awsRegion: process.env.AWS_REGION || 'ap-south-1',
    awsAccessKeyId: process.env.AWS_ACCESS_KEY_ID || '',
    awsSecretAccessKey: process.env.AWS_SECRET_ACCESS_KEY || '',
    s3BucketName: process.env.S3_BUCKET_NAME || 'batayedar-evidence-vault-private',
    s3Endpoint: process.env.S3_ENDPOINT || undefined,
    s3ForcePathStyle: process.env.S3_FORCE_PATH_STYLE === 'true',
    presignedUrlExpiresSeconds: parseInt(process.env.PRESIGNED_URL_EXPIRES_SECONDS, 10) || 900,
    maxFileSizeBytes: parseInt(process.env.MAX_FILE_SIZE_BYTES, 10) || 15 * 1024 * 1024,
    allowedMimeTypes: (process.env.ALLOWED_MIME_TYPES || 'image/jpeg,image/png,image/webp').split(',').map(m => m.trim())
  },

  gpsValidation: {
    maxHorizontalAccuracyMeters: 20.0,
    maxFixAgeSeconds: 10
  },

  logging: {
    level: process.env.LOG_LEVEL || 'info'
  }
};

// Strict validation for production environments
if (config.env === 'production') {
  const errors = [];
  if (!process.env.JWT_ACCESS_SECRET || process.env.JWT_ACCESS_SECRET.startsWith('dev_secret_') || process.env.JWT_ACCESS_SECRET.length < 32) {
    errors.push('JWT_ACCESS_SECRET must be set to a strong production secret (min 32 characters)');
  }
  if (!process.env.JWT_REFRESH_SECRET || process.env.JWT_REFRESH_SECRET.startsWith('dev_secret_') || process.env.JWT_REFRESH_SECRET.length < 32) {
    errors.push('JWT_REFRESH_SECRET must be set to a strong production secret (min 32 characters)');
  }
  if (config.jwt.accessSecret === config.jwt.refreshSecret) {
    errors.push('JWT_ACCESS_SECRET and JWT_REFRESH_SECRET must not be identical');
  }
  if (!process.env.MONGODB_URI) {
    errors.push('MONGODB_URI must be explicitly set for production');
  }
  if (config.storage.provider === 's3') {
    if (!process.env.AWS_ACCESS_KEY_ID || !process.env.AWS_SECRET_ACCESS_KEY || !process.env.S3_BUCKET_NAME) {
      errors.push('AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, and S3_BUCKET_NAME are required when STORAGE_PROVIDER=s3');
    }
  }
  if (errors.length > 0) {
    throw new Error(`CRITICAL CONFIGURATION ERROR in production:\n - ${errors.join('\n - ')}`);
  }
}

module.exports = config;
