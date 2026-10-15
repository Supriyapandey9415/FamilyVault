import api from './api';

export const previewInvitation = async (token) => {
    const response = await api.get('/invitations/preview', { params: { token } });
    return response.data.data;
};

export const acceptInvitation = async (token) => {
    const response = await api.post('/invitations/accept', { token });
    return response.data.data;
};
