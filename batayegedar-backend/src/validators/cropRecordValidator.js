const { z } = require('zod');

const createCropRecordSchema = {
  body: z.object({
    recordId: z.string().uuid().optional(),
    fieldId: z.string().min(1, 'Field ID is required'),
    cropType: z.string().trim().min(1, 'Crop type is required').max(120),
    cultivationStartDate: z.string().datetime({ offset: true }).or(z.string().regex(/^\d{4}-\d{2}-\d{2}/)),
    notes: z.string().trim().max(1000).optional().default(''),
    landlordName: z.string().trim().max(120).optional().default(''),
    landlordPhone: z.string().trim().max(20).optional().default('')
  })
};

const updateCropRecordSchema = {
  body: z.object({
    cropType: z.string().trim().min(1).max(120).optional(),
    cultivationStartDate: z.string().datetime({ offset: true }).or(z.string().regex(/^\d{4}-\d{2}-\d{2}/)).optional(),
    notes: z.string().trim().max(1000).optional(),
    landlordName: z.string().trim().max(120).optional(),
    landlordPhone: z.string().trim().max(20).optional(),
    status: z.enum(['ACTIVE', 'ARCHIVED']).optional()
  }),
  params: z.object({
    recordId: z.string().min(1, 'Record ID is required')
  })
};

const recordIdParamSchema = {
  params: z.object({
    recordId: z.string().min(1, 'Record ID is required')
  })
};

module.exports = {
  createCropRecordSchema,
  updateCropRecordSchema,
  recordIdParamSchema
};
