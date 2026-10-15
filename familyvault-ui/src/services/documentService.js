import api from './api';

const vaultHeaders = (vaultToken) => vaultToken
    ? { 'X-Vault-Token': vaultToken }
    : {};

export const getDocuments = async (familyId, includeHidden = false, vaultToken = null) => {
    const response = await api.get(`/families/${familyId}/documents`, {
        params: { includeHidden },
        headers: vaultHeaders(vaultToken),
    });
    return response.data.data;
};

export const getFolders = async (familyId, includeHidden = false, vaultToken = null) => {
    const response = await api.get(`/families/${familyId}/folders`, {
        params: { includeHidden },
        headers: vaultHeaders(vaultToken),
    });
    return response.data?.data || [];
};

export const createFolder = async (familyId, folder, vaultToken = null) => {
    const response = await api.post(`/families/${familyId}/folders`, folder, {
        headers: vaultHeaders(vaultToken),
    });
    return response.data?.data;
};

export const updateFolder = async (familyId, folderId, folder, vaultToken = null) => {
    const response = await api.put(`/families/${familyId}/folders/${folderId}`, folder, {
        headers: vaultHeaders(vaultToken),
    });
    return response.data?.data;
};

export const deleteFolder = async (familyId, folderId, vaultToken = null) => {
    await api.delete(`/families/${familyId}/folders/${folderId}`, {
        headers: vaultHeaders(vaultToken),
    });
};

export const uploadDocument = async (familyId, formData, vaultToken = null) => {
    if (!formData.has('familyId')) {
        formData.append('familyId', familyId);
    }
    const response = await api.post(`/families/${familyId}/documents`, formData, {
        headers: {
            'Content-Type': 'multipart/form-data',
            ...vaultHeaders(vaultToken),
        },
    });
    return response.data.data;
};

export const downloadDocument = async (documentId, password = null, vaultToken = null) => {
    const config = {
        responseType: 'blob',
        headers: vaultHeaders(vaultToken),
    };
    const response = password === null
        ? await api.get(`/documents/${documentId}/download`, config)
        : await api.post(`/documents/${documentId}/download`, { password }, config);
    return response;
};

export const updateDocumentProtection = async (documentId, protection, vaultToken = null) => {
    const response = await api.put(`/documents/${documentId}/protection`, protection, {
        headers: vaultHeaders(vaultToken),
    });
    return response.data?.data;
};

export const updateDocument = async (documentId, document, vaultToken = null) => {
    const response = await api.put(`/documents/${documentId}`, document, {
        headers: vaultHeaders(vaultToken),
    });
    return response.data?.data;
};

export const deleteDocument = async (documentId, vaultToken = null) => {
    const response = await api.delete(`/documents/${documentId}`, {
        headers: vaultHeaders(vaultToken),
    });
    return response.data;
};
