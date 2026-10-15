import React, { useState, useEffect, useRef } from 'react';
import { getMemories, getMemoryMedia, createMemory, updateMemory, deleteMemory } from '../services/memoryService';
import { Image, Upload, Save, Grid, List, MoreVertical, Sparkles, X, Pencil, RotateCcw, Trash2, Eye } from 'lucide-react';
import './MemoriesTab.css';

const MemoriesTab = ({ familyId, currentUserRole = 'MEMBER', currentUserId = null }) => {
    const [memories, setMemories] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [mediaSources, setMediaSources] = useState({});
    const mediaSourcesRef = useRef({});

    // Form state (Create)
    const [title, setTitle] = useState('');
    const [description, setDescription] = useState('');
    const [file, setFile] = useState(null);
    const [previewUrl, setPreviewUrl] = useState(null);
    const [uploading, setUploading] = useState(false);
    const [uploadError, setUploadError] = useState(null);
    const [isModalOpen, setIsModalOpen] = useState(false);

    // 3-Dot Menu & Edit Memory State
    const [activeMenuId, setActiveMenuId] = useState(null);
    const [editingMemory, setEditingMemory] = useState(null);
    const [editTitle, setEditTitle] = useState('');
    const [editDescription, setEditDescription] = useState('');
    const [editFile, setEditFile] = useState(null);
    const [editPreviewUrl, setEditPreviewUrl] = useState(null);
    const [savingEdit, setSavingEdit] = useState(false);
    const [editError, setEditError] = useState(null);

    // Full photo lightbox state
    const [lightboxMemory, setLightboxMemory] = useState(null);
    
    // Featured Showcase (3 Photos) State
    const [customPhotos, setCustomPhotos] = useState(() => {
        try {
            const saved = localStorage.getItem(`familyvault_featured_memories_${familyId}`);
            return saved ? JSON.parse(saved) : null;
        } catch (e) {
            return null;
        }
    });
    const [isShowcaseModalOpen, setIsShowcaseModalOpen] = useState(false);
    const [tempPhotos, setTempPhotos] = useState([null, null, null]);
    const [selectedSlot, setSelectedSlot] = useState(0);

    // View state
    const [viewMode, setViewMode] = useState('grid'); // 'grid' or 'list'

    const fetchMemories = async () => {
        setLoading(true);
        try {
            const data = await getMemories(familyId);
            const nextMemories = data || [];
            const loadedSources = await Promise.all(nextMemories
                .filter((memory) => memory.fileUrl)
                .map(async (memory) => {
                    try {
                        const blob = await getMemoryMedia(memory.fileUrl);
                        return [memory.fileUrl, URL.createObjectURL(blob)];
                    } catch (mediaError) {
                        console.error(`Failed to load media for memory ${memory.id}:`, mediaError);
                        return [memory.fileUrl, null];
                    }
                }));
            Object.values(mediaSourcesRef.current).filter(Boolean).forEach((url) => URL.revokeObjectURL(url));
            const nextSources = Object.fromEntries(loadedSources);
            mediaSourcesRef.current = nextSources;
            setMediaSources(nextSources);
            setMemories(nextMemories);
            setError(null);
        } catch (err) {
            console.error('Error fetching memories:', err);
            setError('Failed to load memories.');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchMemories();
    }, [familyId]);

    useEffect(() => () => {
        Object.values(mediaSourcesRef.current).filter(Boolean).forEach((url) => URL.revokeObjectURL(url));
        mediaSourcesRef.current = {};
    }, []);

    // Close dropdown on outside click
    useEffect(() => {
        const handleClickOutside = (e) => {
            if (!e.target.closest('.memory-menu-wrapper')) {
                setActiveMenuId(null);
            }
        };
        document.addEventListener('click', handleClickOutside);
        return () => document.removeEventListener('click', handleClickOutside);
    }, []);

    // Handle Escape key to close modals and menu
    useEffect(() => {
        const handleKeyDown = (e) => {
            if (e.key === 'Escape') {
                if (isModalOpen && !uploading) closeModal();
                if (isShowcaseModalOpen) setIsShowcaseModalOpen(false);
                if (editingMemory && !savingEdit) setEditingMemory(null);
                if (lightboxMemory) setLightboxMemory(null);
                setActiveMenuId(null);
            }
        };
        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [isModalOpen, isShowcaseModalOpen, editingMemory, lightboxMemory, uploading, savingEdit]);

    const handleOpenEdit = (memory) => {
        setActiveMenuId(null);
        setEditingMemory(memory);
        setEditTitle(memory.title || '');
        setEditDescription(memory.description || '');
        setEditFile(null);
        setEditPreviewUrl(memory.fileUrl ? getPhotoSrc(memory.fileUrl) : null);
        setEditError(null);
    };

    const handleSaveEdit = async (e) => {
        e.preventDefault();
        if (!editTitle.trim() || !editingMemory) return;

        setSavingEdit(true);
        setEditError(null);
        try {
            const formData = new FormData();
            formData.append('title', editTitle);
            formData.append('description', editDescription);
            if (editFile) {
                formData.append('file', editFile);
            }

            await updateMemory(editingMemory.id, formData);
            setEditingMemory(null);
            await fetchMemories();
        } catch (err) {
            console.error('Error updating memory:', err);
            setEditError(err.response?.data?.message || err.message || 'Failed to update memory.');
        } finally {
            setSavingEdit(false);
        }
    };

    const handleDeleteMemory = async (memoryId) => {
        setActiveMenuId(null);
        if (!window.confirm('Are you sure you want to delete this memory? This action cannot be undone.')) return;
        try {
            await deleteMemory(memoryId);
            await fetchMemories();
        } catch (err) {
            console.error('Error deleting memory:', err);
            alert(err.response?.data?.message || err.message || 'Failed to delete memory.');
        }
    };

    // Helper to resolve image URL
    const getPhotoSrc = (url) => {
        if (!url) return '';
        if (url.startsWith('data:') || url.startsWith('blob:')) {
            return url;
        }
        return mediaSources[url] || '';
    };

    // Filter memories with photos, sorted latest first
    const memoriesWithPhotos = [...memories]
        .filter(m => m.fileUrl)
        .sort((a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0));

    // Determine the 3 photos to show on the header banner:
    // If user has not chosen custom photos, automatically use the latest 3 memories with photos
    const activeSlot1 = customPhotos?.[0] || memoriesWithPhotos[0]?.fileUrl || null;
    const activeSlot2 = customPhotos?.[1] || memoriesWithPhotos[1]?.fileUrl || null;
    const activeSlot3 = customPhotos?.[2] || memoriesWithPhotos[2]?.fileUrl || null;

    const openShowcaseModal = () => {
        setTempPhotos([
            customPhotos?.[0] || memoriesWithPhotos[0]?.fileUrl || null,
            customPhotos?.[1] || memoriesWithPhotos[1]?.fileUrl || null,
            customPhotos?.[2] || memoriesWithPhotos[2]?.fileUrl || null
        ]);
        setSelectedSlot(0);
        setIsShowcaseModalOpen(true);
    };

    const handleSlotFileUpload = (e, index) => {
        if (e.target.files && e.target.files[0]) {
            const pickedFile = e.target.files[0];
            const reader = new FileReader();
            reader.onload = (event) => {
                const newPhotos = [...tempPhotos];
                newPhotos[index] = event.target.result;
                setTempPhotos(newPhotos);
            };
            reader.readAsDataURL(pickedFile);
        }
    };

    const handlePickMemory = (fileUrl) => {
        const newPhotos = [...tempPhotos];
        newPhotos[selectedSlot] = fileUrl;
        setTempPhotos(newPhotos);
        setSelectedSlot((selectedSlot + 1) % 3);
    };

    const handleClearSlot = (index) => {
        const newPhotos = [...tempPhotos];
        newPhotos[index] = null;
        setTempPhotos(newPhotos);
    };

    const handleResetToAuto = () => {
        try {
            localStorage.removeItem(`familyvault_featured_memories_${familyId}`);
        } catch (e) {}
        setCustomPhotos(null);
        setIsShowcaseModalOpen(false);
    };

    const handleSaveShowcase = () => {
        const hasAny = tempPhotos.some(p => p !== null);
        if (!hasAny) {
            handleResetToAuto();
            return;
        }
        try {
            localStorage.setItem(`familyvault_featured_memories_${familyId}`, JSON.stringify(tempPhotos));
        } catch (e) {}
        setCustomPhotos(tempPhotos);
        setIsShowcaseModalOpen(false);
    };

    const handleFileChange = (e) => {
        if (e.target.files && e.target.files[0]) {
            const selectedFile = e.target.files[0];
            setFile(selectedFile);
            setPreviewUrl(URL.createObjectURL(selectedFile));
        }
    };

    const closeModal = () => {
        if (uploading) return;
        setIsModalOpen(false);
        setUploadError(null);
    };

    const handleUpload = async (e) => {
        e.preventDefault();
        if (!title.trim() || !file) return;

        setUploading(true);
        setUploadError(null);
        try {
            const formData = new FormData();
            formData.append('title', title);
            formData.append('description', description);
            formData.append('file', file);

            await createMemory(familyId, formData);
            
            // Reset form
            setTitle('');
            setDescription('');
            setFile(null);
            setPreviewUrl(null);
            setIsModalOpen(false);
            
            // Refresh list
            await fetchMemories();
        } catch (err) {
            console.error('Error uploading memory:', err);
            setUploadError(err.response?.data?.message || err.message || 'Failed to upload memory.');
        } finally {
            setUploading(false);
        }
    };

    return (
        <div className="memories-tab">
            {/* Page Header */}
            <div className="memories-page-header">
                <div className="header-text-content">
                    <div className="badge">
                        <Image size={14} /> OUR FAMILY MEMORIES
                    </div>
                    <h2>Family <span className="text-primary">Memories</span></h2>
                    <p>Share and preserve your favorite family moments.</p>
                </div>
                <div 
                    className="header-illustration" 
                    onClick={openShowcaseModal} 
                    title="Click to customize the 3 showcase photos"
                    role="button"
                    tabIndex={0}
                >
                    <div className="polaroid p1">
                        {activeSlot1 ? (
                            <img src={getPhotoSrc(activeSlot1)} alt="Showcase 1" className="polaroid-img" />
                        ) : (
                            <Image size={24} color="#CBD5E1"/>
                        )}
                    </div>
                    <div className="polaroid p2">
                        {activeSlot2 ? (
                            <img src={getPhotoSrc(activeSlot2)} alt="Showcase 2" className="polaroid-img" />
                        ) : (
                            <Image size={24} color="#CBD5E1"/>
                        )}
                    </div>
                    <div className="polaroid p3">
                        {activeSlot3 ? (
                            <img src={getPhotoSrc(activeSlot3)} alt="Showcase 3" className="polaroid-img" />
                        ) : (
                            <Image size={24} color="#CBD5E1"/>
                        )}
                    </div>
                    <Sparkles className="sparkle s1" size={16} color="#5A45FF" />
                    <Sparkles className="sparkle s2" size={24} color="#5A45FF" />
                    <div className="illustration-edit-pill">
                        <Pencil size={11} /> <span>Edit</span>
                    </div>
                </div>
            </div>

            {error && <div className="error-message">{error}</div>}

            {/* Upload Memory Popup Modal */}
            {isModalOpen && (
                <div className="memory-modal-overlay" onClick={closeModal}>
                    <div className="memory-modal-card" onClick={(e) => e.stopPropagation()}>
                        {/* Top-Right Cross Button to cut/close popup */}
                        <button 
                            type="button" 
                            className="modal-close-btn" 
                            onClick={closeModal}
                            disabled={uploading}
                            title="Close"
                        >
                            <X size={18} />
                        </button>

                        <div className="card-header-icon-title modal-header-title">
                            <div className="icon-box">
                                <Image size={22} color="#5A45FF" />
                            </div>
                            <div>
                                <h3>Upload a New Memory</h3>
                                <p>Add a photo and a few details to keep this memory safe for today and tomorrow.</p>
                            </div>
                        </div>

                        <form className="memory-form" onSubmit={handleUpload}>
                            <div className="form-row-split">
                                {/* Left Side: Title */}
                                <div className="form-group form-col-left">
                                    <label htmlFor="memory-title">Title</label>
                                    <input 
                                        id="memory-title"
                                        type="text" 
                                        className="modal-input"
                                        placeholder="E.g., Summer Vacation 2026" 
                                        value={title}
                                        onChange={(e) => setTitle(e.target.value)}
                                        disabled={uploading}
                                        required
                                    />
                                </div>

                                {/* Right Side: Photo Upload Option */}
                                <div className="form-group form-col-right">
                                    <label>Photo</label>
                                    <div className={`file-upload-compact ${file ? 'has-file' : ''}`}>
                                        <Upload size={18} color="#5A45FF" />
                                        <span className="upload-compact-text">
                                            {file ? file.name : 'Upload Img'}
                                        </span>
                                        <input 
                                            type="file" 
                                            accept="image/png, image/jpeg, image/webp" 
                                            onChange={handleFileChange}
                                            disabled={uploading}
                                            required
                                        />
                                    </div>
                                </div>
                            </div>

                            {/* Bottom: Description */}
                            <div className="form-group form-group-desc">
                                <div className="label-with-count">
                                    <label htmlFor="memory-desc">Description</label>
                                    <span className="char-count">{description.length}/500</span>
                                </div>
                                <textarea 
                                    id="memory-desc"
                                    className="modal-textarea"
                                    placeholder="Write something about this memory..." 
                                    value={description}
                                    onChange={(e) => setDescription(e.target.value)}
                                    disabled={uploading}
                                    rows="3"
                                    maxLength="500"
                                />
                            </div>

                            {uploadError && <div className="error-message">{uploadError}</div>}
                            
                            <div className="modal-actions">
                                <button type="button" className="btn-cancel" onClick={closeModal} disabled={uploading}>
                                    Cancel
                                </button>
                                <button type="submit" className="btn-save-memory" disabled={!title.trim() || !file || uploading}>
                                    <Save size={18} />
                                    {uploading ? 'Uploading...' : 'Save Memory'}
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}

            {/* Showcase Customization Modal */}
            {isShowcaseModalOpen && (
                <div className="memory-modal-overlay" onClick={() => setIsShowcaseModalOpen(false)}>
                    <div className="memory-modal-card showcase-modal-card" onClick={(e) => e.stopPropagation()}>
                        <button 
                            type="button" 
                            className="modal-close-btn" 
                            onClick={() => setIsShowcaseModalOpen(false)}
                            title="Close"
                        >
                            <X size={18} />
                        </button>

                        <div className="card-header-icon-title modal-header-title">
                            <div className="icon-box">
                                <Sparkles size={22} color="#5A45FF" />
                            </div>
                            <div>
                                <h3>Featured Banner Photos</h3>
                                <p>Select any 3 photos to showcase on your family header, or keep it on automatic.</p>
                            </div>
                        </div>

                        {/* Mode status indicator */}
                        <div className="showcase-status-bar">
                            <div className={`status-tag ${!customPhotos ? 'active-auto' : ''}`}>
                                <Sparkles size={14} />
                                <span>Mode: {!customPhotos ? 'Automatic (Showing 3 Latest Memories)' : 'Custom 3 Photos Active'}</span>
                            </div>
                            <button 
                                type="button" 
                                className="btn-auto-toggle"
                                onClick={handleResetToAuto}
                                title="Reset to automatically use latest 3 uploaded memories"
                            >
                                <RotateCcw size={14} /> Auto-fill Latest 3
                            </button>
                        </div>

                        {/* 3 Slots */}
                        <div className="showcase-slots-row">
                            {[0, 1, 2].map((index) => {
                                const photo = tempPhotos[index];
                                const isSelected = selectedSlot === index;
                                return (
                                    <div 
                                        key={index} 
                                        className={`showcase-slot-card ${isSelected ? 'active-slot' : ''}`}
                                        onClick={() => setSelectedSlot(index)}
                                    >
                                        <div className="slot-badge">Photo {index + 1}</div>
                                        <div className="slot-image-box">
                                            {photo ? (
                                                <img src={getPhotoSrc(photo)} alt={`Slot ${index + 1}`} className="slot-preview-img" />
                                            ) : (
                                                <div className="slot-empty">
                                                    <Image size={24} color="#CBD5E1" />
                                                    <span>Empty</span>
                                                </div>
                                            )}
                                        </div>
                                        <div className="slot-actions">
                                            <label className="slot-upload-btn" title="Upload custom photo from computer">
                                                <Upload size={13} /> Upload
                                                <input 
                                                    type="file" 
                                                    accept="image/png, image/jpeg, image/webp" 
                                                    onChange={(e) => handleSlotFileUpload(e, index)}
                                                    style={{ display: 'none' }}
                                                />
                                            </label>
                                            {photo && (
                                                <button 
                                                    type="button" 
                                                    className="slot-clear-btn" 
                                                    onClick={(e) => {
                                                        e.stopPropagation();
                                                        handleClearSlot(index);
                                                    }}
                                                    title="Clear this photo"
                                                >
                                                    <X size={13} />
                                                </button>
                                            )}
                                        </div>
                                    </div>
                                );
                            })}
                        </div>

                        {/* Pick from existing memories */}
                        {memoriesWithPhotos.length > 0 && (
                            <div className="showcase-memories-picker">
                                <div className="picker-header">
                                    <label>Or click any memory photo to set as Photo {selectedSlot + 1}:</label>
                                </div>
                                <div className="picker-thumbnails-grid">
                                    {memoriesWithPhotos.map((m) => {
                                        const pickedIndex = tempPhotos.indexOf(m.fileUrl);
                                        return (
                                            <div 
                                                key={m.id} 
                                                className={`picker-thumbnail ${pickedIndex !== -1 ? 'already-picked' : ''}`}
                                                onClick={() => handlePickMemory(m.fileUrl)}
                                                title={`Click to set as Photo ${selectedSlot + 1}: ${m.title}`}
                                            >
                                                <img src={getPhotoSrc(m.fileUrl)} alt={m.title} />
                                                {pickedIndex !== -1 && (
                                                    <div className="picked-badge">#{pickedIndex + 1}</div>
                                                )}
                                            </div>
                                        );
                                    })}
                                </div>
                            </div>
                        )}

                        <div className="modal-actions">
                            <button type="button" className="btn-cancel" onClick={() => setIsShowcaseModalOpen(false)}>
                                Cancel
                            </button>
                            <button type="button" className="btn-save-memory" onClick={handleSaveShowcase}>
                                <Save size={16} /> Save Showcase
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* Memories List Card */}
            <div className="card memories-list-card">
                <div className="list-card-header">
                    <div className="card-header-icon-title">
                        <div className="icon-box">
                            <Image size={20} color="#5A45FF" />
                        </div>
                        <div>
                            <h3>Your Memories</h3>
                            <p>All the special moments from your family vault.</p>
                        </div>
                    </div>
                    <div className="header-actions">
                        <button 
                            type="button" 
                            className="btn-add-memory" 
                            onClick={() => setIsModalOpen(true)}
                        >
                            <Upload size={16} /> Upload Memory
                        </button>
                        <div className="view-toggles">
                            <button 
                                type="button"
                                className={`toggle-btn ${viewMode === 'grid' ? 'active' : ''}`} 
                                onClick={() => setViewMode('grid')}
                            >
                                <Grid size={18} />
                            </button>
                            <button 
                                type="button"
                                className={`toggle-btn ${viewMode === 'list' ? 'active' : ''}`} 
                                onClick={() => setViewMode('list')}
                            >
                                <List size={18} />
                            </button>
                        </div>
                    </div>
                </div>

                <div className={`memories-layout ${viewMode}`}>
                    {loading ? (
                        <p className="loading-text">Loading memories...</p>
                    ) : memories.length === 0 ? (
                        <div className="empty-memories">
                            <Image size={48} color="#CBD5E1" />
                            <p>No memories yet. Add your first photo!</p>
                        </div>
                    ) : (
                        memories.map((memory) => (
                            <div key={memory.id} className="memory-card">
                                <div 
                                    className="memory-image"
                                    onClick={() => memory.fileUrl && setLightboxMemory(memory)}
                                    style={{ cursor: memory.fileUrl ? 'pointer' : 'default' }}
                                    title={memory.fileUrl ? 'Click to view full photo' : ''}
                                >
                                    {memory.fileUrl ? (
                                        <img src={getPhotoSrc(memory.fileUrl)} alt={memory.title} />
                                    ) : (
                                        <div className="memory-image-placeholder">
                                            <Image size={32} color="#94A3B8" />
                                        </div>
                                    )}
                                </div>
                                <div className="memory-details">
                                    <h4>{memory.title}</h4>
                                    {memory.description && <p className="memory-desc">{memory.description}</p>}
                                    <div className="memory-footer">
                                        <span className="memory-date">
                                            {new Date(memory.createdAt).toLocaleDateString()}
                                        </span>
                                        {(() => {
                                            const isCreator = currentUserId != null && memory.createdBy != null && String(memory.createdBy) === String(currentUserId);
                                            const canDelete = currentUserRole === 'OWNER' || currentUserRole === 'ADMIN' || isCreator;
                                            const canModify = currentUserRole === 'OWNER' || currentUserRole === 'ADMIN' || isCreator;
                                            const hasAnyAction = canModify || canDelete || !!memory.fileUrl;

                                            if (!hasAnyAction) return null;

                                            return (
                                                <div className="memory-menu-wrapper">
                                                    <button 
                                                        type="button" 
                                                        className={`more-btn ${activeMenuId === memory.id ? 'active' : ''}`}
                                                        onClick={(e) => {
                                                            e.stopPropagation();
                                                            setActiveMenuId(activeMenuId === memory.id ? null : memory.id);
                                                        }}
                                                        title="Options"
                                                    >
                                                        <MoreVertical size={16} />
                                                    </button>

                                                    {activeMenuId === memory.id && (
                                                        <div className="memory-dropdown-menu" onClick={(e) => e.stopPropagation()}>
                                                            {canModify && (
                                                                <button 
                                                                    type="button" 
                                                                    className="dropdown-item" 
                                                                    onClick={() => handleOpenEdit(memory)}
                                                                >
                                                                    <Pencil size={15} color="#5A45FF" />
                                                                    <span>Modify Details</span>
                                                                </button>
                                                            )}
                                                            {memory.fileUrl && (
                                                                <button 
                                                                    type="button" 
                                                                    className="dropdown-item" 
                                                                    onClick={() => {
                                                                        setActiveMenuId(null);
                                                                        setLightboxMemory(memory);
                                                                    }}
                                                                >
                                                                    <Eye size={15} color="#0284C7" />
                                                                    <span>View Full Photo</span>
                                                                </button>
                                                            )}
                                                            {canDelete && (
                                                                <>
                                                                    {(canModify || memory.fileUrl) && <div className="dropdown-divider" />}
                                                                    <button 
                                                                        type="button" 
                                                                        className="dropdown-item item-delete" 
                                                                        onClick={() => handleDeleteMemory(memory.id)}
                                                                    >
                                                                        <Trash2 size={15} color="#EF4444" />
                                                                        <span>Delete Memory</span>
                                                                    </button>
                                                                </>
                                                            )}
                                                        </div>
                                                    )}
                                                </div>
                                            );
                                        })()}
                                    </div>
                                </div>
                            </div>
                        ))
                    )}
                </div>
            </div>

            {/* Modify / Edit Memory Modal */}
            {editingMemory && (
                <div className="memory-modal-overlay" onClick={() => !savingEdit && setEditingMemory(null)}>
                    <div className="memory-modal-card" onClick={(e) => e.stopPropagation()}>
                        <button 
                            type="button" 
                            className="modal-close-btn" 
                            onClick={() => setEditingMemory(null)}
                            disabled={savingEdit}
                            title="Close"
                        >
                            <X size={18} />
                        </button>

                        <div className="card-header-icon-title modal-header-title">
                            <div className="icon-box">
                                <Pencil size={22} color="#5A45FF" />
                            </div>
                            <div>
                                <h3>Modify Memory</h3>
                                <p>Update title, description, or change the photo for this memory.</p>
                            </div>
                        </div>

                        <form className="memory-form" onSubmit={handleSaveEdit}>
                            <div className="form-row-split">
                                {/* Left: Title */}
                                <div className="form-group form-col-left">
                                    <label htmlFor="edit-memory-title">Title</label>
                                    <input 
                                        id="edit-memory-title"
                                        type="text" 
                                        className="modal-input"
                                        placeholder="E.g., Summer Vacation 2026" 
                                        value={editTitle}
                                        onChange={(e) => setEditTitle(e.target.value)}
                                        disabled={savingEdit}
                                        required
                                    />
                                </div>

                                {/* Right: Photo Upload (same 48px height matching Title) */}
                                <div className="form-group form-col-right">
                                    <label>Photo</label>
                                    <div className={`file-upload-compact ${(editFile || editPreviewUrl) ? 'has-file' : ''}`}>
                                        <Upload size={18} color="#5A45FF" />
                                        <span className="upload-compact-text">
                                            {editFile ? editFile.name : (editPreviewUrl ? 'Change Photo' : 'Upload Img')}
                                        </span>
                                        <input 
                                            type="file" 
                                            accept="image/png, image/jpeg, image/webp" 
                                            onChange={(e) => {
                                                if (e.target.files && e.target.files[0]) {
                                                    const selected = e.target.files[0];
                                                    setEditFile(selected);
                                                    setEditPreviewUrl(URL.createObjectURL(selected));
                                                }
                                            }}
                                            disabled={savingEdit}
                                        />
                                    </div>
                                </div>
                            </div>

                            {/* Bottom: Description */}
                            <div className="form-group form-group-desc">
                                <div className="label-with-count">
                                    <label htmlFor="edit-memory-desc">Description</label>
                                    <span className="char-count">{editDescription.length}/500</span>
                                </div>
                                <textarea 
                                    id="edit-memory-desc"
                                    className="modal-textarea"
                                    placeholder="Write something about this memory..." 
                                    value={editDescription}
                                    onChange={(e) => setEditDescription(e.target.value)}
                                    disabled={savingEdit}
                                    rows="3"
                                    maxLength="500"
                                />
                            </div>

                            {/* Photo Preview Strip if photo exists */}
                            {editPreviewUrl && (
                                <div className="edit-preview-badge">
                                    <img src={editPreviewUrl} alt="Preview" className="edit-preview-thumb" />
                                    <span>Current photo (Click "Change Photo" above to replace)</span>
                                </div>
                            )}

                            {editError && <div className="error-message">{editError}</div>}
                            
                            <div className="modal-actions">
                                <button 
                                    type="button" 
                                    className="btn-cancel" 
                                    onClick={() => setEditingMemory(null)} 
                                    disabled={savingEdit}
                                >
                                    Cancel
                                </button>
                                <button 
                                    type="submit" 
                                    className="btn-save-memory" 
                                    disabled={!editTitle.trim() || savingEdit}
                                >
                                    <Save size={18} />
                                    {savingEdit ? 'Saving...' : 'Save Changes'}
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}

            {/* Full Photo Lightbox Modal */}
            {lightboxMemory && (
                <div className="memory-lightbox-overlay" onClick={() => setLightboxMemory(null)}>
                    <div className="memory-lightbox-content" onClick={(e) => e.stopPropagation()}>
                        <button 
                            type="button" 
                            className="lightbox-close-btn" 
                            onClick={() => setLightboxMemory(null)}
                            title="Close"
                        >
                            <X size={20} />
                        </button>
                        <img src={getPhotoSrc(lightboxMemory.fileUrl)} alt={lightboxMemory.title} className="lightbox-img" />
                        <div className="lightbox-caption">
                            <h4>{lightboxMemory.title}</h4>
                            {lightboxMemory.description && <p>{lightboxMemory.description}</p>}
                            <span className="lightbox-date">{new Date(lightboxMemory.createdAt).toLocaleDateString()}</span>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default MemoriesTab;
