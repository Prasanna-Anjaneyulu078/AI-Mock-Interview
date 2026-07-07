import test from 'node:test';
import assert from 'node:assert/strict';
import { saveResume, getUserResume } from '../src/models/resume.service.js';

test('saveResume stores resume data when MongoDB is unavailable', async () => {
  const userId = `test-user-${Date.now()}`;

  const savedResume = await saveResume(userId, 'resume.pdf', 'Sample resume text');

  assert.equal(savedResume.userId, userId);
  assert.equal(savedResume.fileName, 'resume.pdf');
  assert.equal(savedResume.extractedText, 'Sample resume text');

  const fetchedResume = await getUserResume(userId);
  assert.equal(fetchedResume?.fileName, 'resume.pdf');
  assert.equal(fetchedResume?.extractedText, 'Sample resume text');
});
