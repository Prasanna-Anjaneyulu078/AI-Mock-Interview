// ============================================
// auth.service.js - Authentication Service
// ============================================
// Contains the business logic for:
//   - Email/password registration and login
//   - Getting user profile
// Reference: bcrypt.hash(), bcrypt.compare() - reference-backend.md
// ============================================

import bcrypt from 'bcryptjs';
import mongoose from 'mongoose';
import User from '../models/User.model.js';
import { generateToken } from '../utils/jwt.utils.js';

const fallbackUsers = new Map();

const isDatabaseAvailable = () => mongoose.connection.readyState === 1;

const getFallbackUser = (userId) => {
  if (!userId) return null;
  return fallbackUsers.get(userId) || Array.from(fallbackUsers.values()).find((user) => user._id === userId) || null;
};

/**
 * Register a new user with email and password.
 */
export const register = async (name, email, password) => {
  try {
    const existingFallback = Array.from(fallbackUsers.values()).find((user) => user.email === email);
    if (existingFallback) {
      const error = new Error('Email already registered.');
      error.statusCode = 409;
      throw error;
    }

    if (!isDatabaseAvailable()) {
      const hashedPassword = await bcrypt.hash(password, 10);
      const user = {
        _id: `local-${Date.now()}`,
        name,
        email,
        password: hashedPassword,
        createdAt: new Date(),
        lastLogin: new Date(),
      };

      fallbackUsers.set(user._id, user);
      const token = generateToken(user);

      return {
        token,
        user: { id: user._id, email: user.email, name: user.name },
      };
    }

    const existing = await User.findOne({ email });
    if (existing) {
      const error = new Error('Email already registered.');
      error.statusCode = 409;
      throw error;
    }

    const hashedPassword = await bcrypt.hash(password, 10);
    const user = await User.create({ name, email, password: hashedPassword });
    const token = generateToken(user);

    return {
      token,
      user: { id: user._id, email: user.email, name: user.name },
    };
  } catch (error) {
    if (error?.message?.includes('ECONNREFUSED') || error?.message?.includes('buffering timed out') || error?.message?.includes('connection')) {
      const hashedPassword = await bcrypt.hash(password, 10);
      const user = {
        _id: `local-${Date.now()}`,
        name,
        email,
        password: hashedPassword,
        createdAt: new Date(),
        lastLogin: new Date(),
      };

      fallbackUsers.set(user._id, user);
      const token = generateToken(user);

      return {
        token,
        user: { id: user._id, email: user.email, name: user.name },
      };
    }

    if (error?.statusCode) {
      throw error;
    }

    const fallbackError = new Error('Unable to register user right now.');
    fallbackError.statusCode = 500;
    throw fallbackError;
  }
};

/**
 * Login a user with email and password.
 */
export const emailLogin = async (email, password) => {
  try {
    const fallbackUser = Array.from(fallbackUsers.values()).find((entry) => entry.email === email);
    if (!isDatabaseAvailable() && fallbackUser) {
      const isMatch = await bcrypt.compare(password, fallbackUser.password);
      if (!isMatch) {
        const error = new Error('Invalid email or password.');
        error.statusCode = 401;
        throw error;
      }

      fallbackUser.lastLogin = new Date();
      const token = generateToken(fallbackUser);

      return {
        token,
        user: { id: fallbackUser._id, email: fallbackUser.email, name: fallbackUser.name },
      };
    }

    if (!isDatabaseAvailable()) {
      const fallbackUser = Array.from(fallbackUsers.values()).find((entry) => entry.email === email);
      if (!fallbackUser) {
        const error = new Error('Invalid email or password.');
        error.statusCode = 401;
        throw error;
      }

      const isMatch = await bcrypt.compare(password, fallbackUser.password);
      if (!isMatch) {
        const error = new Error('Invalid email or password.');
        error.statusCode = 401;
        throw error;
      }

      fallbackUser.lastLogin = new Date();
      const token = generateToken(fallbackUser);

      return {
        token,
        user: { id: fallbackUser._id, email: fallbackUser.email, name: fallbackUser.name },
      };
    }

    const user = await User.findOne({ email });
    if (!user || !user.password) {
      const error = new Error('Invalid email or password.');
      error.statusCode = 401;
      throw error;
    }

    const isMatch = await bcrypt.compare(password, user.password);
    if (!isMatch) {
      const error = new Error('Invalid email or password.');
      error.statusCode = 401;
      throw error;
    }

    user.lastLogin = new Date();
    await user.save();

    const token = generateToken(user);

    return {
      token,
      user: { id: user._id, email: user.email, name: user.name },
    };
  } catch (error) {
    if (error?.statusCode) {
      throw error;
    }

    const fallbackUser = Array.from(fallbackUsers.values()).find((entry) => entry.email === email);
    if (fallbackUser) {
      const isMatch = await bcrypt.compare(password, fallbackUser.password);
      if (!isMatch) {
        const authError = new Error('Invalid email or password.');
        authError.statusCode = 401;
        throw authError;
      }

      fallbackUser.lastLogin = new Date();
      const token = generateToken(fallbackUser);

      return {
        token,
        user: { id: fallbackUser._id, email: fallbackUser.email, name: fallbackUser.name },
      };
    }

    const localError = new Error('Invalid email or password.');
    localError.statusCode = 401;
    throw localError;
  }
};

/**
 * Get a user's profile by their ID.
 */
export const getUserProfile = async (userId) => {
  const fallbackUser = getFallbackUser(userId);
  if (!isDatabaseAvailable() && fallbackUser) {
    return {
      id: fallbackUser._id,
      email: fallbackUser.email,
      name: fallbackUser.name,
      picture: fallbackUser.picture,
      createdAt: fallbackUser.createdAt,
      lastLogin: fallbackUser.lastLogin,
    };
  }

  try {
    const user = await User.findById(userId).select('-__v -password');

    if (!user) {
      throw new Error('User not found');
    }

    return {
      id: user._id,
      email: user.email,
      name: user.name,
      picture: user.picture,
      createdAt: user.createdAt,
      lastLogin: user.lastLogin,
    };
  } catch (error) {
    if (fallbackUser) {
      return {
        id: fallbackUser._id,
        email: fallbackUser.email,
        name: fallbackUser.name,
        picture: fallbackUser.picture,
        createdAt: fallbackUser.createdAt,
        lastLogin: fallbackUser.lastLogin,
      };
    }

    throw error;
  }
};
