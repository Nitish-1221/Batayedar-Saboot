const { z } = require('zod');

const syncPushSchema = {
  body: z.object({
    idempotencyKey: z.string().optional(),
    fields: z.array(
      z.object({
        fieldId: z.string().min(1),
        fieldName: z.string().min(1),
        description: z.string().optional().default(''),
        village: z.string().min(1),
        district: z.string().min(1),
        state: z.string().min(1),
        areaAcres: z.number().optional().default(0),
        surveyKhasraNumber: z.string().optional().default('')
      })
    ).optional().default([]),

    cropRecords: z.array(
      z.object({
        recordId: z.string().min(1),
        fieldId: z.string().min(1),
        cropType: z.string().min(1),
        cultivationStartDate: z.string(),
        notes: z.string().optional().default(''),
        landlordName: z.string().optional().default(''),
        landlordPhone: z.string().optional().default('')
      })
    ).optional().default([]),

    evidenceList: z.array(
      z.object({
        evidenceId: z.string().min(1),
        recordId: z.string().min(1),
        latitude: z.number().min(-90).max(90),
        longitude: z.number().min(-180).max(180),
        reportedGpsAccuracyMeters: z.number().min(0),
        clientCapturedAtUtc: z.string(),
        clientReportedChecksum: z.string().optional().default(''),
        notes: z.string().optional().default('')
      })
    ).optional().default([])
  })
};

const syncPullSchema = {
  query: z.object({
    since: z.string().optional()
  })
};

module.exports = {
  syncPushSchema,
  syncPullSchema
};
