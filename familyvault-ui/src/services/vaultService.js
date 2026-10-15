import api from './api';

const vaultHeaders = (vaultToken) => vaultToken
    ? { 'X-Vault-Token': vaultToken }
    : {};

export const getVaultStatus = async (familyId, vaultToken = null) => {
    const response = await api.get('/vault/status', {
        params: { familyId },
        headers: vaultHeaders(vaultToken),
    });
    return response.data.data;
};

export const setupVaultPin = async (familyId, accountPassword, pin, confirmPin) => {
    const response = await api.post('/vault/pin', {
        familyId,
        accountPassword,
        pin,
        confirmPin,
    });
    return response.data.data;
};

export const unlockVault = async (familyId, pin) => {
    const response = await api.post('/vault/unlock', { familyId, pin });
    return response.data.data;
};
