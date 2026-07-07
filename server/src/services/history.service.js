import mongoose from 'mongoose';
import Interview from '../models/Interview.model.js';

const isDatabaseAvailable = () => mongoose.connection.readyState === 1;

export const getUserHistory = async (userId, page = 1, limit = 10) => {
  if (!isDatabaseAvailable()) {
    return {
      entries: [],
      totalEntries: 0,
      totalPages: 0,
      currentPage: page,
    };
  }

  const skip = (page - 1) * limit;

  const [entries, totalEntries] = await Promise.all([
    Interview.find({ userId })
      .sort({ createdAt: -1 })
      .skip(skip)
      .limit(limit)
      .select('role status overallScore totalQuestions createdAt'),

    Interview.countDocuments({ userId }),
  ]);

  return {
    entries,
    totalEntries,
    totalPages: Math.ceil(totalEntries / limit),
    currentPage: page,
  };
};

export const getHistoryEntry = async (entryId, userId) => {
  if (!isDatabaseAvailable()) {
    throw new Error('Interview not found');
  }

  const entry = await Interview.findOne({ _id: entryId, userId }).select('-__v');
  if (!entry) throw new Error('Interview not found');
  return entry;
};

export const deleteHistoryEntry = async (entryId, userId) => {
  if (!isDatabaseAvailable()) {
    return null;
  }

  const entry = await Interview.findOneAndDelete({ _id: entryId, userId });
  if (!entry) throw new Error('Interview not found');
  return entry;
};

export const clearUserHistory = async (userId) => {
  if (!isDatabaseAvailable()) {
    return { deletedCount: 0 };
  }

  const result = await Interview.deleteMany({ userId });
  return { deletedCount: result.deletedCount };
};