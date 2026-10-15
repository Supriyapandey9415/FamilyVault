import api from './api';

const requestHeaders = (vaultToken = null) => ({
    ...(vaultToken ? { 'X-Vault-Token': vaultToken } : {}),
    'X-Request-Id': globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random()}`,
});

export const getFamilySettings = async (familyId, vaultToken = null) => {
    const response = await api.get(`/families/${familyId}/settings`, {
        headers: vaultToken ? { 'X-Vault-Token': vaultToken } : {},
    });
    return response.data.data;
};

export const updateFamilyGeneralSettings = async (familyId, settings, vaultToken = null) => {
    const response = await api.patch(`/families/${familyId}/settings/general`, settings, {
        headers: requestHeaders(vaultToken),
    });
    return response.data.data;
};

export const updateFamilyPolicies = async (familyId, policies, vaultToken = null) => {
    const response = await api.patch(`/families/${familyId}/settings/policies`, policies, {
        headers: requestHeaders(vaultToken),
    });
    return response.data.data;
};

export const updateFamilyNotificationPreferences = async (
    familyId,
    preferences,
    vaultToken = null,
) => {
    const response = await api.patch(
        `/families/${familyId}/settings/notification-preferences/me`,
        preferences,
        { headers: requestHeaders(vaultToken) },
    );
    return response.data.data;
};

export const transferFamilyOwnership = async (familyId, transfer, vaultToken = null) => {
    const response = await api.post(
        `/families/${familyId}/settings/ownership-transfer`,
        transfer,
        { headers: requestHeaders(vaultToken) },
    );
    return response.data.data;
};
