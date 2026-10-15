import api from './api';

export const getFamilies = async () => {
    const response = await api.get('/families');
    return response.data.data;
};

export const getFamilyById = async (familyId) => {
    const response = await api.get(`/families/${familyId}`);
    return response.data.data;
};

export const createFamily = async (familyData) => {
    const response = await api.post('/families', familyData);
    return response.data;
};

export const updateFamily = async (familyId, familyData) => {
    const response = await api.put(`/families/${familyId}`, familyData);
    return response.data.data;
};

export const deleteFamily = async (familyId) => {
    const response = await api.delete(`/families/${familyId}`);
    return response.data;
};

export const leaveFamily = async (familyId) => {
    const response = await api.post(`/families/${familyId}/leave`);
    return response.data;
};
