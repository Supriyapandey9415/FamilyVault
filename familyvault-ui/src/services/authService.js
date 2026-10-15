import api from './api';

export const logoutSession = () => api.post('/auth/logout');

export const login = async (credentials) => {
    const response = await api.post('/auth/login', credentials);
    return response.data;
};

export const register = async (userData) => {
    const response = await api.post('/auth/register', userData);
    return response.data;
};

export const getProfile = async () => {
    const response = await api.get('/auth/me');
    return response.data?.data;
};

export const updateProfile = async (profileData) => {
    const response = await api.put('/auth/me', profileData);
    return response.data?.data;
};

export const forgotPassword = async (email) => {
    const response = await api.post('/auth/forgot-password', { email });
    return response.data;
};

export const resetPassword = async (token, newPassword) => {
    const response = await api.post('/auth/reset-password', { token, newPassword });
    return response.data;
};

export const verifyResetToken = async (token) => {
    const response = await api.get(`/auth/verify-reset-token?token=${encodeURIComponent(token)}`);
    return response.data;
};

export const loginWithGoogle = async (googleData) => {
    const response = await api.post('/auth/google', googleData);
    return response.data;
};

export const getAuthConfig = async () => {
    const response = await api.get('/auth/config');
    return response.data?.data;
};

export const getAppleAuthConfig = async () => {
    const response = await api.get('/auth/apple/config');
    return response.data?.data;
};

export const createAppleAuthSession = async () => {
    const response = await api.post('/auth/apple/session');
    return response.data?.data;
};

export const loginWithApple = async (appleData) => {
    const response = await api.post('/auth/apple/login', appleData);
    return response.data;
};
