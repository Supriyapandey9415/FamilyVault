import api from './api';

export const getSmtpConfig = async () => {
    const response = await api.get('/settings/smtp');
    return response.data?.data;
};

export const saveSmtpConfig = async (configData) => {
    const response = await api.post('/settings/smtp', configData);
    return response.data?.data;
};

export const testSmtpConnection = async (email) => {
    const response = await api.post('/settings/smtp/test', { email });
    return response.data?.message;
};
