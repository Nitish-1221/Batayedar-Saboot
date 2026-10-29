const { z } = require('zod');

const registerSchema = {
  body: z.object({
    fullName: z.string().trim().min(2, 'Full name must be at least 2 characters').max(120),
    phone: z.string().trim().regex(/^[0-9+]{10,15}$/, 'Phone number must be between 10 and 15 digits'),
    password: z.string().min(6, 'Password must be at least 6 characters').max(100).optional(),
    email: z.string().email('Invalid email address').optional().or(z.literal('')),
    idToken: z.string().optional(),
    role: z.enum(['BATAYDAR', 'LANDLORD']).optional().default('BATAYDAR'),
    village: z.string().trim().max(100).optional().default(''),
    district: z.string().trim().max(100).optional().default(''),
    state: z.string().trim().max(100).optional().default(''),
    preferredLanguage: z.enum(['en', 'hi']).optional().default('en')
  })
};

const loginSchema = {
  body: z.object({
    phone: z.string().trim().optional(),
    email: z.string().email().optional(),
    password: z.string().optional(),
    idToken: z.string().optional(),
    role: z.enum(['BATAYDAR', 'LANDLORD']).optional()
  })
};

const refreshSchema = {
  body: z.object({
    refreshToken: z.string().min(1, 'Refresh token is required')
  })
};

const changePasswordSchema = {
  body: z.object({
    currentPassword: z.string().min(1, 'Current password is required'),
    newPassword: z.string().min(6, 'New password must be at least 6 characters').max(100)
  })
};

const updateProfileSchema = {
  body: z.object({
    fullName: z.string().trim().min(2).max(120).optional(),
    village: z.string().trim().max(100).optional(),
    district: z.string().trim().max(100).optional(),
    state: z.string().trim().max(100).optional(),
    preferredLanguage: z.enum(['en', 'hi']).optional()
  })
};

module.exports = {
  registerSchema,
  loginSchema,
  refreshSchema,
  changePasswordSchema,
  updateProfileSchema
};
