// ============================================
// db.config.js - MongoDB Connection
// ============================================
// Connects to MongoDB Atlas using Mongoose.
// Reference: mongoose.connect() - reference-mongodb.md
// ============================================

import mongoose from 'mongoose';

const DEFAULT_URI = 'mongodb://127.0.0.1:27017/interviewpilot';

const connectDB = async () => {
  try {
    const mongoURI = process.env.MONGODB_URI || process.env.MONGO_URI || DEFAULT_URI;

    if (!mongoURI) {
      throw new Error('No MongoDB connection string was provided');
    }

    const conn = await mongoose.connect(mongoURI, {
      serverSelectionTimeoutMS: 5000,
    });

    console.error(`MongoDB Connected: ${conn.connection.host}`);
    return conn;
  } catch (error) {
    console.error(`MongoDB Connection Error: ${error.message}`);

    if (process.env.NODE_ENV === 'production') {
      process.exit(1);
    }

    console.warn('Continuing without MongoDB connection for local development.');
    return null;
  }
};

export default connectDB;
