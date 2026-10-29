const config = require('../config');

// eslint-disable-next-line no-unused-vars
function errorHandler(err, req, res, next) {
  const statusCode = err.statusCode || err.status || 500;

  // Log error internally for debugging (without leaking to response)
  console.error(`[Error] ${req.method} ${req.originalUrl}:`, err.message);
  if (config.env === 'development' && err.stack) {
    console.error(err.stack);
  }

  // Handle common database / payload errors safely
  let message = err.message || 'An internal server error occurred';
  let code = err.code || 'INTERNAL_SERVER_ERROR';

  if (err.name === 'ValidationError') {
    code = 'VALIDATION_ERROR';
  } else if (err.name === 'CastError') {
    code = 'INVALID_IDENTIFIER';
    message = 'Invalid resource identifier format';
  } else if (err.code === 11000) {
    code = 'DUPLICATE_KEY';
    message = 'A resource with this identifier or unique attribute already exists';
  }

  // In production, mask unhandled 500 server error details
  if (statusCode === 500 && config.env === 'production') {
    message = 'An unexpected server error occurred. Please try again later.';
    code = 'INTERNAL_SERVER_ERROR';
  }

  res.status(statusCode).json({
    success: false,
    error: {
      code,
      message,
      ...(config.env === 'development' && err.details ? { details: err.details } : {})
    }
  });
}

function notFoundHandler(req, res) {
  res.status(404).json({
    success: false,
    error: {
      code: 'ROUTE_NOT_FOUND',
      message: `Endpoint ${req.method} ${req.originalUrl} does not exist.`
    }
  });
}

module.exports = {
  errorHandler,
  notFoundHandler
};
