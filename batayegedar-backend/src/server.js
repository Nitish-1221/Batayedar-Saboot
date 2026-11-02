const app = require('./app');
const config = require('./config');
const { connectDB, disconnectDB } = require('./config/db');
const { startKeepAlive, stopKeepAlive } = require('./services/keepAliveService');

async function startServer() {
  try {
    // Sanitize database URI for safe logging (hides username:password credentials)
    const sanitizedUri = config.mongo.uri
      ? config.mongo.uri.replace(/:([^@]+)@/, ':****@')
      : 'local';

    console.log(`[Startup] Connecting to MongoDB at ${sanitizedUri}...`);
    await connectDB();
    console.log('[Startup] MongoDB database connected successfully.');

    const server = app.listen(config.port, () => {
      console.log(`Batayedar Saboot Backend server running on port ${config.port} in ${config.env} mode`);
      console.log(`API endpoints accessible at http://localhost:${config.port}${config.apiPrefix}`);
      console.log(`Health check available at http://localhost:${config.port}${config.apiPrefix}/health`);
      console.log(`Swagger documentation available at http://localhost:${config.port}/api-docs`);

      // Start auto-ping keep-alive if in production, on Render, or when AUTO_PING=true
      if (config.env === 'production' || process.env.RENDER || process.env.AUTO_PING === 'true') {
        startKeepAlive();
      }
    });

    const shutdown = async (signal) => {
      console.log(`Received ${signal}. Shutting down gracefully...`);
      stopKeepAlive();
      server.close(async () => {
        await disconnectDB();
        console.log('Server and database connections closed cleanly.');
        process.exit(0);
      });
    };

    process.on('SIGINT', () => shutdown('SIGINT'));
    process.on('SIGTERM', () => shutdown('SIGTERM'));
    return server;
  } catch (err) {
    console.error('Fatal error during database connection or server startup:', err.message);
    process.exit(1);
  }
}

if (require.main === module) {
  startServer();
}

module.exports = { startServer };
