import api from './api';

export const getNotificationPreferences = async () => {
    const response = await api.get('/settings/notifications');
    return response.data?.data;
};

export const updateNotificationPreferences = async (preferences) => {
    const response = await api.put('/settings/notifications', preferences);
    return response.data?.data;
};
