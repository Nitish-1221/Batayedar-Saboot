const mongoose = require('mongoose');
const dns = require('dns');
const config = require('./index');

// Configure public DNS resolvers to ensure MongoDB Atlas SRV lookups succeed across all networks
try {
  if (config.mongo.uri && config.mongo.uri.startsWith('mongodb+srv://')) {
    dns.setServers(['8.8.8.8', '1.1.1.1']);
  }
} catch (e) {
  // Ignore in restricted sandboxes
}

let eventListenersAttached = false;

function attachConnectionListeners() {
  if (eventListenersAttached) return;

  mongoose.connection.on('connected', () => {
    console.log(`[MongoDB] Connected to database: ${mongoose.connection.name} on ${mongoose.connection.host}`);
  });

  mongoose.connection.on('error', (err) => {
    console.error('[MongoDB] Runtime database connection error:', err.message);
  });

  mongoose.connection.on('disconnected', () => {
    console.warn('[MongoDB] Database connection disconnected.');
  });

  mongoose.connection.on('reconnected', () => {
    console.log('[MongoDB] Database reconnected successfully.');
  });

  eventListenersAttached = true;
}

async function connectDB(customUri, options = {}) {
  const uri = customUri || config.mongo.uri;
  attachConnectionListeners();

  try {
    const opts = {
      autoIndex: true,
      serverSelectionTimeoutMS: 10000,
      socketTimeoutMS: 45000,
      maxPoolSize: 20,
      ...options
    };

    // If no dbName specified in URI path and not in options, default to configured dbName
    const pathPart = uri.split('?')[0].split('/').slice(3).join('/');
    if (!opts.dbName && !customUri && !pathPart) {
      opts.dbName = config.mongo.dbName || 'batayedar_saboot';
    }

    const conn = await mongoose.connect(uri, opts);
    return conn;
  } catch (err) {
    console.error('[MongoDB] Connection failed:', err.message);
    throw err;
  }
}

function isDbConnected() {
  return mongoose.connection.readyState === 1;
}

async function disconnectDB() {
  if (mongoose.connection.readyState !== 0) {
    await mongoose.disconnect();
  }
}

module.exports = {
  connectDB,
  disconnectDB,
  isDbConnected
};
