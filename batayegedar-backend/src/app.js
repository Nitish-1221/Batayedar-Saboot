const express = require('express');
const helmet = require('helmet');
const cors = require('cors');
const morgan = require('morgan');
const swaggerUi = require('swagger-ui-express');

const config = require('./config');
const apiRoutes = require('./routes');
const swaggerSpec = require('./docs/swaggerSpec');
const { apiLimiter } = require('./middleware/rateLimiter');
const { errorHandler, notFoundHandler } = require('./middleware/errorHandler');

const app = express();

// Security Headers
app.use(helmet({
  crossOriginResourcePolicy: { policy: 'cross-origin' }
}));

// CORS Configuration
app.use(cors({
  origin: (origin, callback) => {
    // Allow mobile apps and tools with no origin (e.g. mobile apps, curl, Postman)
    if (!origin) return callback(null, true);
    if (config.cors.allowedOrigins.includes(origin) || config.cors.allowedOrigins.includes('*')) {
      return callback(null, true);
    }
    return callback(new Error('Origin blocked by CORS security policy'));
  },
  credentials: true,
  methods: ['GET', 'POST', 'PATCH', 'DELETE', 'OPTIONS'],
  allowedHeaders: ['Content-Type', 'Authorization', 'X-Requested-With', 'Idempotency-Key']
}));

// Request payload size limits
app.use(express.json({ limit: '5mb' }));
app.use(express.urlencoded({ extended: true, limit: '5mb' }));

// Logging (morgan)
if (config.env !== 'test') {
  app.use(morgan('combined'));
}

// Global API Rate Limiter
app.use('/api', apiLimiter);

// OpenAPI / Swagger Documentation
app.use('/api-docs', swaggerUi.serve, swaggerUi.setup(swaggerSpec, {
  customCss: '.swagger-ui .topbar { display: none }',
  customSiteTitle: 'Batayedar Saboot API Docs'
}));

// API Routes
app.use(config.apiPrefix, apiRoutes);

// Root Health & Welcome Check
app.get('/', (req, res) => {
  res.status(200).json({
    message: 'Welcome to Batayedar Saboot Secure Backend API',
    docs: '/api-docs',
    version: '1.0.0',
    status: 'ACTIVE'
  });
});

// 404 Handler
app.use(notFoundHandler);

// Centralized Error Handler (no leaking of credentials or stack traces in production)
app.use(errorHandler);

module.exports = app;
