import mongoose from 'mongoose';
import * as pdfjsLib from 'pdfjs-dist/legacy/build/pdf.mjs';
import Resume from '../models/Resume.model.js';

const fallbackResumes = new Map();

const isDatabaseAvailable = () => mongoose.connection.readyState === 1;

export const parseResumePDF = async (pdfBuffer) => {
  try {
    const uint8Array = new Uint8Array(
      pdfBuffer.buffer,
      pdfBuffer.byteOffset,
      pdfBuffer.byteLength
    );

    const loadingTask = pdfjsLib.getDocument({ data: uint8Array });
    const pdf = await loadingTask.promise;

    let extractedText = '';

    for (let pageNum = 1; pageNum <= pdf.numPages; pageNum++) {
      const page = await pdf.getPage(pageNum);
      const content = await page.getTextContent();
      const strings = content.items.map((item) => item.str);
      extractedText += strings.join(' ');
    }

    if (!extractedText || extractedText.trim().length === 0) {
      throw new Error('No text could be extracted from the PDF');
    }

    return extractedText;
  } catch (error) {
    console.error('PDF Parse Error:', error.message);
    throw new Error('Failed to parse PDF. Please upload a valid PDF file.');
  }
};

export const saveResume = async (userId, fileName, extractedText) => {
  if (!isDatabaseAvailable()) {
    const resume = {
      _id: `local-${Date.now()}`,
      userId,
      fileName,
      extractedText,
      createdAt: new Date(),
      updatedAt: new Date(),
    };

    fallbackResumes.set(userId, resume);
    return resume;
  }

  try {
    const resume = await Resume.findOneAndUpdate(
      { userId },
      { userId, fileName, extractedText },
      { returnDocument: 'after', upsert: true }
    );

    return resume;
  } catch (error) {
    if (
      error?.name === 'MongooseServerSelectionError' ||
      error?.name === 'MongoNotConnectedError' ||
      error?.message?.includes('ECONNREFUSED') ||
      error?.message?.includes('buffering timed out') ||
      error?.message?.includes('connection')
    ) {
      const resume = {
        _id: `local-${Date.now()}`,
        userId,
        fileName,
        extractedText,
        createdAt: new Date(),
        updatedAt: new Date(),
      };

      fallbackResumes.set(userId, resume);
      return resume;
    }

    throw error;
  }
};

export const getUserResume = async (userId) => {
  if (!isDatabaseAvailable()) {
    return fallbackResumes.get(userId) || null;
  }

  try {
    const resume = await Resume.findOne({ userId }).select('-__v');
    return resume;
  } catch (error) {
    if (
      error?.name === 'MongooseServerSelectionError' ||
      error?.name === 'MongoNotConnectedError' ||
      error?.message?.includes('ECONNREFUSED') ||
      error?.message?.includes('buffering timed out') ||
      error?.message?.includes('connection')
    ) {
      return fallbackResumes.get(userId) || null;
    }

    throw error;
  }
};