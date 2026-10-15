import api from './api';

export const getFamilyMembers = async (familyId) => {
    const response = await api.get(`/families/${familyId}/members`);
    return response.data.data;
};

export const createFamilyInvitation = async (familyId, invitationData) => {
    const response = await api.post(`/families/${familyId}/invitations`, invitationData);
    return response.data.data;
};

export const getPendingInvitations = async (familyId) => {
    const response = await api.get(`/families/${familyId}/invitations`);
    return response.data.data;
};

export const resendFamilyInvitation = async (familyId, invitationId) => {
    const response = await api.post(`/families/${familyId}/invitations/${invitationId}/resend`);
    return response.data.data;
};

export const revokeFamilyInvitation = async (familyId, invitationId) => {
    const response = await api.delete(`/families/${familyId}/invitations/${invitationId}`);
    return response.data;
};

export const updateFamilyMemberRole = async (familyId, memberId, role, email = '') => {
    const response = await api.put(`/families/${familyId}/members/${memberId}`, { role, email });
    return response.data.data;
};

export const removeFamilyMember = async (familyId, memberId) => {
    const response = await api.delete(`/families/${familyId}/members/${memberId}`);
    return response.data;
};
