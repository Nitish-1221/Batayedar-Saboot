const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const { S3Client, PutObjectCommand, GetObjectCommand, HeadObjectCommand } = require('@aws-sdk/client-s3');
const { getSignedUrl } = require('@aws-sdk/s3-request-presigner');
const config = require('../config');

// Ensure local storage directory exists if using local provider
const LOCAL_STORAGE_DIR = path.resolve(__dirname, '../../uploads');
if (!fs.existsSync(LOCAL_STORAGE_DIR)) {
  fs.mkdirSync(LOCAL_STORAGE_DIR, { recursive: true });
}

let s3Client = null;
if (config.storage.provider === 's3') {
  const s3Config = {
    region: config.storage.awsRegion,
    credentials: {
      accessKeyId: config.storage.awsAccessKeyId,
      secretAccessKey: config.storage.awsSecretAccessKey
    }
  };
  if (config.storage.s3Endpoint) {
    s3Config.endpoint = config.storage.s3Endpoint;
    s3Config.forcePathStyle = config.storage.s3ForcePathStyle;
  }
  s3Client = new S3Client(s3Config);
}

function detectMimeTypeFromMagicBytes(buffer) {
  if (!buffer || buffer.length < 4) return null;
  // JPEG: FF D8 FF
  if (buffer[0] === 0xff && buffer[1] === 0xd8 && buffer[2] === 0xff) {
    return 'image/jpeg';
  }
  // PNG: 89 50 4E 47
  if (buffer[0] === 0x89 && buffer[1] === 0x50 && buffer[2] === 0x4e && buffer[3] === 0x47) {
    return 'image/png';
  }
  // WEBP: RIFF .... WEBP
  if (
    buffer.length >= 12 &&
    buffer[0] === 0x52 && buffer[1] === 0x49 && buffer[2] === 0x46 && buffer[3] === 0x46 &&
    buffer[8] === 0x57 && buffer[9] === 0x45 && buffer[10] === 0x42 && buffer[11] === 0x50
  ) {
    return 'image/webp';
  }
  return null;
}

function calculateSha256(buffer) {
  return crypto.createHash('sha256').update(buffer).digest('hex').toLowerCase();
}

function safeResolveLocalStoragePath(objectKey) {
  if (!objectKey || typeof objectKey !== 'string') {
    throw new Error('Invalid object key: must be a non-empty string');
  }
  if (objectKey.indexOf('\0') !== -1) {
    throw new Error('Invalid object key: null bytes detected');
  }
  // Normalize forward/backward slashes
  const normalizedKey = objectKey.replace(/\\/g, '/');
  const segments = normalizedKey.split('/');
  if (segments.some(segment => segment === '..' || segment === '.')) {
    throw new Error('Path traversal sequence detected in object key');
  }

  const resolvedPath = path.resolve(LOCAL_STORAGE_DIR, ...segments);
  const normalizedBase = path.resolve(LOCAL_STORAGE_DIR);
  if (!resolvedPath.startsWith(normalizedBase + path.sep)) {
    throw new Error('Access denied: target path is outside local storage vault');
  }
  return resolvedPath;
}

function generateLocalUploadToken(evidenceId, farmerId) {
  const expiresAt = Date.now() + (config.storage.presignedUrlExpiresSeconds * 1000);
  const payload = `${evidenceId}:${farmerId}:${expiresAt}`;
  const hmac = crypto.createHmac('sha256', config.jwt.accessSecret).update(payload).digest('hex');
  return `${expiresAt}.${hmac}`;
}

function verifyLocalUploadToken(evidenceId, farmerId, token) {
  if (!token || typeof token !== 'string') return false;
  const parts = token.split('.');
  if (parts.length !== 2) return false;
  const [expiresAtStr, signature] = parts;
  const expiresAt = parseInt(expiresAtStr, 10);
  if (isNaN(expiresAt) || Date.now() > expiresAt) {
    return false;
  }
  const payload = `${evidenceId}:${farmerId}:${expiresAt}`;
  const expectedHmac = crypto.createHmac('sha256', config.jwt.accessSecret).update(payload).digest('hex');

  const sigBuffer = Buffer.from(signature, 'hex');
  const expBuffer = Buffer.from(expectedHmac, 'hex');
  if (sigBuffer.length !== expBuffer.length) return false;
  return crypto.timingSafeEqual(sigBuffer, expBuffer);
}

function generateLocalDownloadToken(objectKey, farmerId) {
  const expiresAt = Date.now() + (config.storage.presignedUrlExpiresSeconds * 1000);
  const payload = `${objectKey}:${farmerId}:${expiresAt}`;
  const hmac = crypto.createHmac('sha256', config.jwt.accessSecret).update(payload).digest('hex');
  return `${expiresAt}.${hmac}`;
}

function verifyLocalDownloadToken(objectKey, farmerId, token) {
  if (!token || typeof token !== 'string') return false;
  const parts = token.split('.');
  if (parts.length !== 2) return false;
  const [expiresAtStr, signature] = parts;
  const expiresAt = parseInt(expiresAtStr, 10);
  if (isNaN(expiresAt) || Date.now() > expiresAt) {
    return false;
  }
  const payload = `${objectKey}:${farmerId}:${expiresAt}`;
  const expectedHmac = crypto.createHmac('sha256', config.jwt.accessSecret).update(payload).digest('hex');

  const sigBuffer = Buffer.from(signature, 'hex');
  const expBuffer = Buffer.from(expectedHmac, 'hex');
  if (sigBuffer.length !== expBuffer.length) return false;
  return crypto.timingSafeEqual(sigBuffer, expBuffer);
}

/**
 * Generate a secure, narrowly-scoped presigned upload URL
 */
async function generateUploadUrl({ evidenceId, farmerId, recordId, fileExtension = 'jpg', contentType = 'image/jpeg' }) {
  const cleanExt = fileExtension.replace(/^\./, '').toLowerCase();
  const objectKey = `evidence/${farmerId}/${recordId}/${evidenceId}.${cleanExt}`;

  if (config.storage.provider === 's3' && s3Client) {
    const command = new PutObjectCommand({
      Bucket: config.storage.s3BucketName,
      Key: objectKey,
      ContentType: contentType,
      Metadata: {
        evidenceId,
        farmerId,
        recordId
      }
    });

    const uploadUrl = await getSignedUrl(s3Client, command, {
      expiresIn: config.storage.presignedUrlExpiresSeconds
    });

    return {
      uploadUrl,
      objectKey,
      bucket: config.storage.s3BucketName,
      expiresInSeconds: config.storage.presignedUrlExpiresSeconds,
      storageProvider: 's3'
    };
  } else {
    // Local development fallback mode
    // Generate a secure HMAC-signed upload token
    const uploadToken = generateLocalUploadToken(evidenceId, farmerId);
    const uploadUrl = `${config.apiPrefix}/evidence/upload-binary?evidenceId=${encodeURIComponent(evidenceId)}&token=${uploadToken}`;

    return {
      uploadUrl,
      objectKey,
      bucket: 'local-vault',
      uploadToken,
      expiresInSeconds: config.storage.presignedUrlExpiresSeconds,
      storageProvider: 'local'
    };
  }
}

/**
 * Save binary file buffer to local storage (for local mode / test mode)
 */
async function saveLocalFile(objectKey, buffer) {
  const filePath = safeResolveLocalStoragePath(objectKey);
  const dir = path.dirname(filePath);
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true });
  }
  await fs.promises.writeFile(filePath, buffer);
  return filePath;
}

/**
 * Fetch object buffer from S3 or local storage
 */
async function getObjectBuffer(objectKey) {
  if (config.storage.provider === 's3' && s3Client) {
    const command = new GetObjectCommand({
      Bucket: config.storage.s3BucketName,
      Key: objectKey
    });
    const response = await s3Client.send(command);
    const chunks = [];
    for await (const chunk of response.Body) {
      chunks.push(chunk);
    }
    return Buffer.concat(chunks);
  } else {
    const filePath = safeResolveLocalStoragePath(objectKey);
    if (!fs.existsSync(filePath)) {
      throw new Error(`Evidence file not found on storage: ${objectKey}`);
    }
    return await fs.promises.readFile(filePath);
  }
}

/**
 * Server-side verification of uploaded object:
 * - Checks file exists
 * - Checks size limits
 * - Inspects magic bytes (MIME type verification)
 * - Computes true SHA-256 hash
 * - Compares with client-reported checksum
 */
async function verifyUploadedEvidence(objectKey, clientReportedChecksum) {
  const buffer = await getObjectBuffer(objectKey);

  if (buffer.length === 0) {
    return {
      success: false,
      error: 'Uploaded file is empty (0 bytes)',
      fileSizeBytes: 0
    };
  }

  if (buffer.length > config.storage.maxFileSizeBytes) {
    return {
      success: false,
      error: `Uploaded file exceeds maximum limit of ${config.storage.maxFileSizeBytes} bytes`,
      fileSizeBytes: buffer.length
    };
  }

  const detectedMime = detectMimeTypeFromMagicBytes(buffer);
  if (!detectedMime || !config.storage.allowedMimeTypes.includes(detectedMime)) {
    return {
      success: false,
      error: `Invalid file format or spoofed MIME type. Detected: ${detectedMime || 'unknown'}`,
      fileSizeBytes: buffer.length
    };
  }

  const calculatedSha256 = calculateSha256(buffer);
  const normalizedClientChecksum = (clientReportedChecksum || '').toLowerCase().trim();
  const hashMatch = normalizedClientChecksum ? calculatedSha256 === normalizedClientChecksum : false;

  return {
    success: true,
    fileSizeBytes: buffer.length,
    detectedMimeType: detectedMime,
    serverSha256: calculatedSha256,
    clientSha256: normalizedClientChecksum,
    hashMatch
  };
}

/**
 * Generate a short-lived download / view URL for authorized farmer
 */
async function generateDownloadUrl({ objectKey, farmerId }) {
  if (config.storage.provider === 's3' && s3Client) {
    const command = new GetObjectCommand({
      Bucket: config.storage.s3BucketName,
      Key: objectKey
    });

    const downloadUrl = await getSignedUrl(s3Client, command, {
      expiresIn: config.storage.presignedUrlExpiresSeconds
    });

    return {
      downloadUrl,
      expiresInSeconds: config.storage.presignedUrlExpiresSeconds
    };
  } else {
    // For local mode, include HMAC signed download token
    const token = generateLocalDownloadToken(objectKey, farmerId);
    return {
      downloadUrl: `${config.apiPrefix}/evidence/file/${encodeURIComponent(objectKey)}?token=${token}`,
      expiresInSeconds: config.storage.presignedUrlExpiresSeconds
    };
  }
}

/**
 * Delete object from storage
 */
async function deleteObject(objectKey) {
  try {
    if (config.storage.provider === 's3' && s3Client) {
      const { DeleteObjectCommand } = require('@aws-sdk/client-s3');
      await s3Client.send(
        new DeleteObjectCommand({
          Bucket: config.storage.s3BucketName,
          Key: objectKey
        })
      );
    } else {
      const filePath = safeResolveLocalStoragePath(objectKey);
      if (fs.existsSync(filePath)) {
        await fs.promises.unlink(filePath);
      }
    }
    return true;
  } catch (err) {
    console.error('Failed to delete object from storage:', err.message);
    return false;
  }
}

module.exports = {
  generateUploadUrl,
  verifyUploadedEvidence,
  generateDownloadUrl,
  saveLocalFile,
  getObjectBuffer,
  deleteObject,
  calculateSha256,
  detectMimeTypeFromMagicBytes,
  safeResolveLocalStoragePath,
  generateLocalUploadToken,
  verifyLocalUploadToken,
  generateLocalDownloadToken,
  verifyLocalDownloadToken
};
