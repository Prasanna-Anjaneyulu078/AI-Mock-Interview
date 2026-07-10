import API from './api.js';

const getSummary = async () => {
  const response = await API.get('/analytics/summary');
  return response.data.data;
};

const getSkills = async () => {
  const response = await API.get('/analytics/skills');
  return response.data.data;
};

const getProgress = async () => {
  const response = await API.get('/analytics/progress');
  return response.data.data;
};

export { getSummary, getSkills, getProgress };
