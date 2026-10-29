const { z } = require('zod');

const createFieldSchema = {
  body: z.object({
    fieldId: z.string().uuid().optional(),
    fieldName: z.string().trim().min(1, 'Field name is required').max(120),
    description: z.string().trim().max(500).optional().default(''),
    village: z.string().trim().min(1, 'Village is required'),
    district: z.string().trim().min(1, 'District is required'),
    state: z.string().trim().min(1, 'State is required'),
    areaAcres: z.number().min(0).optional().default(0),
    surveyKhasraNumber: z.string().trim().max(100).optional().default('')
  })
};

const updateFieldSchema = {
  body: z.object({
    fieldName: z.string().trim().min(1).max(120).optional(),
    description: z.string().trim().max(500).optional(),
    village: z.string().trim().min(1).optional(),
    district: z.string().trim().min(1).optional(),
    state: z.string().trim().min(1).optional(),
    areaAcres: z.number().min(0).optional(),
    surveyKhasraNumber: z.string().trim().max(100).optional()
  }),
  params: z.object({
    fieldId: z.string().min(1, 'Field ID is required')
  })
};

const fieldIdParamSchema = {
  params: z.object({
    fieldId: z.string().min(1, 'Field ID is required')
  })
};

module.exports = {
  createFieldSchema,
  updateFieldSchema,
  fieldIdParamSchema
};
