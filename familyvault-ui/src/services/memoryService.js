import api from './api';

export const getMemories = async (familyId) => {
    const response = await api.get(`/memories/family/${familyId}`);
    return response.data.data;
};

export const getMemoryMedia = async (fileUrl) => {
    if (!fileUrl) return null;
    const apiPath = fileUrl.replace(/^\/api/, '');
    const response = await api.get(apiPath, { responseType: 'blob' });
    return response.data;
};

export const createMemory = async (familyId, formData) => {
    // 1. Create the memory record first (expects JSON)
    const request = {
        familyId: parseInt(familyId),
        title: formData.get('title'),
        description: formData.get('description'),
        memoryDate: new Date().toISOString().split('T')[0]
    };
    
    const response = await api.post(`/memories`, request);
    const memory = response.data.data;
    
    // 2. Upload the file if provided
    const file = formData.get('file');
    if (file && memory.id) {
        const mediaFormData = new FormData();
        mediaFormData.append('file', file);
        await api.post(`/memories/${memory.id}/media`, mediaFormData, {
            headers: {
                'Content-Type': 'multipart/form-data',
            },
        });
    }
    
    return memory;
};

export const updateMemory = async (memoryId, formData) => {
    const request = {
        title: formData.get('title'),
        description: formData.get('description'),
    };
    
    const response = await api.put(`/memories/${memoryId}`, request);
    const updatedMemory = response.data.data;
    
    const file = formData.get('file');
    if (file && typeof file !== 'string') {
        const mediaFormData = new FormData();
        mediaFormData.append('file', file);
        await api.post(`/memories/${memoryId}/media`, mediaFormData, {
            headers: {
                'Content-Type': 'multipart/form-data',
            },
        });
    }
    
    return updatedMemory;
};

export const deleteMemory = async (memoryId) => {
    const response = await api.delete(`/memories/${memoryId}`);
    return response.data;
};
