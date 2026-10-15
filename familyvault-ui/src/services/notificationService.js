import api from './api';

export const getNotifications = async ({ familyId, unreadOnly = false } = {}) => {
    const params = { unreadOnly };
    if (familyId !== undefined && familyId !== null) {
        params.familyId = familyId;
    }
    const response = await api.get('/notifications', { params });
    return response.data?.data || [];
};

export const getUnreadNotificationCount = async (familyId) => {
    const params = familyId === undefined || familyId === null ? {} : { familyId };
    const response = await api.get('/notifications/unread-count', { params });
    return response.data?.data ?? 0;
};

export const markNotificationAsRead = async (id) => {
    const response = await api.put(`/notifications/${id}/read`);
    return response.data;
};

export const markAllNotificationsAsRead = async (familyId) => {
    const params = familyId === undefined || familyId === null ? {} : { familyId };
    const response = await api.put('/notifications/read-all', null, { params });
    return response.data;
};
