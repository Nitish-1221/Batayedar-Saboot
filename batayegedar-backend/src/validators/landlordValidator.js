const { z } = require('zod');

const requestLandlordConfirmationSchema = {
  params: z.object({
    recordId: z.string().min(1, 'Record ID is required')
  }),
  body: z.object({
    landlordName: z.string().trim().min(1, 'Landlord name is required').max(120),
    landlordPhone: z.string().trim().min(5, 'Landlord phone is required').max(20),
    notes: z.string().max(1000).optional().default(''),
    verificationMethod: z.enum(['PHONE_INQUIRY', 'DIRECT_VERBAL', 'SIGNED_AFFIDAVIT', 'SMS_CONFIRMATION', 'OTHER']).default('DIRECT_VERBAL')
  })
};

const respondLandlordConfirmationSchema = {
  params: z.object({
    confirmationId: z.string().min(1, 'Confirmation ID is required')
  }),
  body: z.object({
    status: z.enum(['CONFIRMED_VERBAL', 'CONFIRMED_WRITTEN', 'REFUSED', 'UNRESPONSIVE']),
    notes: z.string().max(1000).optional().default(''),
    witnessName: z.string().max(120).optional().default('')
  })
};

const confirmationIdParamSchema = {
  params: z.object({
    confirmationId: z.string().min(1, 'Confirmation ID is required')
  })
};

module.exports = {
  requestLandlordConfirmationSchema,
  respondLandlordConfirmationSchema,
  confirmationIdParamSchema
};
