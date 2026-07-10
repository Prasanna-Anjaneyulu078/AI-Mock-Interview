import API from './api.js';

const uploadResume = async (file) => {
  const formData = new FormData();
  formData.append('file', file);

  const response = await API.post('/resume/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  const data = response.data.data;
  // Normalize: backend returns `extractedText`, frontend expects `text`
  return { ...data, text: data.extractedText };
};

const getResume = async () => {
  const response = await API.get('/resume');
  const data = response.data.data;
  if (!data) return null; // No resume uploaded yet
  // Normalize: backend returns `extractedText`, frontend expects `text`
  return { ...data, text: data.extractedText };
};

const startInterview = async (role, resumeId, interviewLevel, voiceSettings) => {
  const response = await API.post('/interview/start', {
    role,
    resumeId,
    interviewLevel,
    voiceEnabled: voiceSettings?.voiceEnabled,
    voiceName: voiceSettings?.voiceName,
    voiceSpeed: voiceSettings?.voiceSpeed,
    voiceId: voiceSettings?.voiceId,
    style: voiceSettings?.style,
  });
  return response.data.data;
};

const getMurfVoices = async () => {
  const response = await API.get('/voice/murf');
  return response.data?.data || [];
};

const submitTextAnswer = async (interviewId, answer) => {
  const response = await API.post(`/interview/${interviewId}/answer`, { answerText: answer });
  return response.data.data;
};

const transcribeAudio = async (audioBlob) => {
  const formData = new FormData();
  formData.append('audio', audioBlob, 'answer.webm');

  const response = await API.post('/interview/transcribe', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  // Backend now returns { text: "..." } object
  return response.data.data;
};

const submitCode = async (interviewId, code, language) => {
  const response = await API.post(`/interview/${interviewId}/code`, { code, language });
  return response.data.data;
};

const runCode = async (interviewId, code, language) => {
  const response = await API.post(`/interview/${interviewId}/run-code`, { code, language });
  return response.data.data;
};


const endInterview = async (interviewId) => {
  const response = await API.post(`/interview/${interviewId}/end`);
  return response.data.data;
};

const getInterview = async (interviewId) => {
  const response = await API.get(`/interview/${interviewId}`);
  return response.data.data;
};

const getWelcomeIntroduction = async (interviewId) => {
  const response = await API.get(`/interview/${interviewId}/welcome`);
  return response.data.data;
};

export {
  uploadResume,
  getResume,
  startInterview,
  getMurfVoices,
  submitTextAnswer,
  transcribeAudio,
  submitCode,
  runCode,
  endInterview,
  getInterview,
  getWelcomeIntroduction,
};