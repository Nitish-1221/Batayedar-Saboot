const { z } = require('zod');

const requestUploadUrlSchema = {
  body: z.object({
    recordId: z.string().min(1, 'Record ID is required'),
    evidenceId: z.string().uuid().optional(),
    fileName: z.string().optional(),
    fileExtension: z.string().regex(/^(jpg|jpeg|png|webp)$/i, 'File extension must be jpg, jpeg, png, or webp').default('jpg'),
    contentType: z.enum(['image/jpeg', 'image/png', 'image/webp']).default('image/jpeg'),
    latitude: z.number().min(-90).max(90),
    longitude: z.number().min(-180).max(180),
    altitude: z.number().optional().default(0),
    reportedGpsAccuracyMeters: z.number().min(0, 'Accuracy must be positive'),
    clientFixAgeSeconds: z.number().min(0).optional().default(0),
    clientCapturedAtUtc: z.string().datetime({ offset: true }).or(z.string().regex(/^\d{4}-\d{2}-\d{2}/)),
    deviceManufacturer: z.string().optional().default(''),
    deviceModel: z.string().optional().default(''),
    notes: z.string().max(1000).optional().default('')
  })
};

const completeUploadSchema = {
  params: z.object({
    evidenceId: z.string().min(1, 'Evidence ID is required')
  }),
  body: z.object({
    clientReportedChecksum: z.string().min(64).max(64, 'Checksum must be 64-character SHA-256 hex string').optional(),
    latitude: z.number().min(-90).max(90).optional(),
    longitude: z.number().min(-180).max(180).optional(),
    reportedGpsAccuracyMeters: z.number().min(0).optional(),
    clientCapturedAtUtc: z.string().datetime({ offset: true }).or(z.string().regex(/^\d{4}-\d{2}-\d{2}/)).optional(),
    notes: z.string().max(1000).optional()
  })
};

const evidenceIdParamSchema = {
  params: z.object({
    evidenceId: z.string().min(1, 'Evidence ID is required')
  })
};

module.exports = {
  requestUploadUrlSchema,
  completeUploadSchema,
  evidenceIdParamSchema
};
