const swaggerSpec = {
  openapi: '3.0.0',
  info: {
    title: 'Batayedar Saboot API',
    version: '1.0.0',
    description:
      'Production-oriented secure REST API for Batayedar Saboot tenant farmer crop evidence platform. Enables GPS-verified evidence photography, offline-first sync, encrypted access, and landlord confirmations with multi-tenant data isolation.',
    contact: {
      name: 'Batayedar Saboot Support',
      email: 'support@batayedar.org'
    }
  },
  servers: [
    {
      url: '/api/v1',
      description: 'API Version 1'
    }
  ],
  components: {
    securitySchemes: {
      bearerAuth: {
        type: 'http',
        scheme: 'bearer',
        bearerFormat: 'JWT',
        description: 'Provide your JWT access token obtained from /api/v1/auth/login or /api/v1/auth/register'
      }
    },
    schemas: {
      ErrorResponse: {
        type: 'object',
        properties: {
          success: { type: 'boolean', example: false },
          error: {
            type: 'object',
            properties: {
              code: { type: 'string', example: 'VALIDATION_ERROR' },
              message: { type: 'string', example: 'Detailed error explanation' }
            }
          }
        }
      },
      User: {
        type: 'object',
        properties: {
          userId: { type: 'string', format: 'uuid' },
          fullName: { type: 'string', example: 'Rameshwar Yadav' },
          phone: { type: 'string', example: '+919876543210' },
          email: { type: 'string', nullable: true },
          village: { type: 'string', example: 'Manoharpur' },
          district: { type: 'string', example: 'Samastipur' },
          state: { type: 'string', example: 'Bihar' },
          preferredLanguage: { type: 'string', enum: ['en', 'hi'], example: 'hi' },
          accountStatus: { type: 'string', enum: ['ACTIVE', 'SUSPENDED', 'DELETED'] }
        }
      },
      Field: {
        type: 'object',
        properties: {
          fieldId: { type: 'string', format: 'uuid' },
          farmerId: { type: 'string', format: 'uuid' },
          fieldName: { type: 'string', example: 'North Canal Parcel 2' },
          description: { type: 'string', example: 'Clay soil near irrigation ditch' },
          village: { type: 'string', example: 'Manoharpur' },
          district: { type: 'string', example: 'Samastipur' },
          state: { type: 'string', example: 'Bihar' },
          areaAcres: { type: 'number', example: 1.5 },
          surveyKhasraNumber: { type: 'string', example: '412/1' }
        }
      },
      CropRecord: {
        type: 'object',
        properties: {
          recordId: { type: 'string', format: 'uuid' },
          farmerId: { type: 'string', format: 'uuid' },
          fieldId: { type: 'string', format: 'uuid' },
          cropType: { type: 'string', example: 'Wheat (Gehun)' },
          cultivationStartDate: { type: 'string', format: 'date-time' },
          notes: { type: 'string' },
          landlordName: { type: 'string', example: 'Thakur Balram Singh' },
          landlordPhone: { type: 'string', example: '+919811122233' },
          status: { type: 'string', enum: ['ACTIVE', 'ARCHIVED'] }
        }
      },
      Evidence: {
        type: 'object',
        properties: {
          evidenceId: { type: 'string', format: 'uuid' },
          recordId: { type: 'string', format: 'uuid' },
          farmerId: { type: 'string', format: 'uuid' },
          s3ObjectKey: { type: 'string' },
          sha256Checksum: { type: 'string', example: 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855' },
          latitude: { type: 'number', example: 25.8643 },
          longitude: { type: 'number', example: 85.7821 },
          reportedGpsAccuracyMeters: { type: 'number', example: 4.8 },
          validationStatus: { type: 'string', enum: ['PENDING_UPLOAD', 'VERIFIED', 'FAILED_INTEGRITY', 'FLAGGED_INACCURATE'] },
          gpsAccuracyAcceptable: { type: 'boolean', example: true },
          legalDisclaimerNotice: { type: 'string' }
        }
      }
    }
  },
  paths: {
    '/auth/register': {
      post: {
        summary: 'Register a new farmer',
        tags: ['Authentication'],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: {
                type: 'object',
                required: ['fullName', 'phone', 'password'],
                properties: {
                  fullName: { type: 'string' },
                  phone: { type: 'string' },
                  password: { type: 'string' },
                  village: { type: 'string' },
                  district: { type: 'string' },
                  state: { type: 'string' }
                }
              }
            }
          }
        },
        responses: {
          201: { description: 'Farmer registered successfully' },
          409: { description: 'Phone number already registered' }
        }
      }
    },
    '/auth/login': {
      post: {
        summary: 'Farmer login',
        tags: ['Authentication'],
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: {
                type: 'object',
                required: ['phone', 'password'],
                properties: {
                  phone: { type: 'string' },
                  password: { type: 'string' }
                }
              }
            }
          }
        },
        responses: {
          200: { description: 'Login successful' },
          401: { description: 'Invalid credentials' }
        }
      }
    },
    '/auth/refresh': {
      post: {
        summary: 'Rotate refresh token and issue new access token',
        tags: ['Authentication'],
        responses: {
          200: { description: 'Tokens rotated' },
          401: { description: 'Invalid or revoked refresh token' }
        }
      }
    },
    '/users/me': {
      get: {
        summary: 'Get authenticated farmer profile',
        tags: ['Farmer Profile'],
        security: [{ bearerAuth: [] }],
        responses: {
          200: { description: 'Profile data returned' }
        }
      },
      patch: {
        summary: 'Update authenticated farmer profile',
        tags: ['Farmer Profile'],
        security: [{ bearerAuth: [] }],
        responses: {
          200: { description: 'Profile updated' }
        }
      }
    },
    '/fields': {
      post: {
        summary: 'Create agricultural field parcel',
        tags: ['Fields'],
        security: [{ bearerAuth: [] }],
        responses: {
          201: { description: 'Field created' }
        }
      },
      get: {
        summary: 'List fields owned by authenticated farmer',
        tags: ['Fields'],
        security: [{ bearerAuth: [] }],
        responses: {
          200: { description: 'Fields list' }
        }
      }
    },
    '/crop-records': {
      post: {
        summary: 'Create crop cultivation record',
        tags: ['Crop Records'],
        security: [{ bearerAuth: [] }],
        responses: {
          201: { description: 'Record created' }
        }
      },
      get: {
        summary: 'List crop records for authenticated farmer',
        tags: ['Crop Records'],
        security: [{ bearerAuth: [] }],
        responses: {
          200: { description: 'Crop records list' }
        }
      }
    },
    '/evidence/upload-url': {
      post: {
        summary: 'Request presigned upload URL for GPS evidence photo',
        tags: ['Evidence'],
        security: [{ bearerAuth: [] }],
        responses: {
          200: { description: 'Upload URL generated' }
        }
      }
    },
    '/evidence/{evidenceId}/complete': {
      post: {
        summary: 'Finalize and verify uploaded evidence',
        tags: ['Evidence'],
        security: [{ bearerAuth: [] }],
        parameters: [
          { name: 'evidenceId', in: 'path', required: true, schema: { type: 'string' } }
        ],
        responses: {
          200: { description: 'Evidence verified and status recorded' }
        }
      }
    },
    '/sync/push': {
      post: {
        summary: 'Push batch of offline records from mobile device',
        tags: ['Synchronization'],
        security: [{ bearerAuth: [] }],
        responses: {
          200: { description: 'Batch sync complete' }
        }
      }
    },
    '/sync/pull': {
      get: {
        summary: 'Pull changes since timestamp',
        tags: ['Synchronization'],
        security: [{ bearerAuth: [] }],
        responses: {
          200: { description: 'Delta data returned' }
        }
      }
    }
  }
};

module.exports = swaggerSpec;
