import api from './api';

export const askAiAssistant = async (message) => {
    const response = await api.post('/chat/ask', { message });
    return response.data;
};
