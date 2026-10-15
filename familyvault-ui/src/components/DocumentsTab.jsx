import React, { useState, useEffect } from 'react';
import { getDocuments, getFolders, uploadDocument, downloadDocument, updateDocumentProtection, updateDocument, deleteDocument } from '../services/documentService';
import { getVaultStatus, setupVaultPin, unlockVault } from '../services/vaultService';
import { 
    FileText, Upload, Download, Trash2, Calendar, Search, 
    Plus, X, Check, CheckCircle2, AlertCircle, Shield, Clock, 
    Folder, Eye, Lock, EyeOff, ExternalLink, Pencil,
    KeyRound, LayoutGrid, List as ListIcon
} from 'lucide-react';
import DocumentActionsMenu from './DocumentActionsMenu';
import './DocumentsTab.css';

const CATEGORIES = [
    { id: 'ALL', label: 'All Documents' },
    { id: 'IDENTITY', label: 'Identity & IDs' },
    { id: 'INSURANCE', label: 'Insurance & Policies' },
    { id: 'MEDICAL', label: 'Medical & Health' },
    { id: 'FINANCIAL', label: 'Financial & Tax' },
    { id: 'LEGAL', label: 'Legal & Property' },
    { id: 'EDUCATION', label: 'Education' },
    { id: 'OTHER', label: 'Other' },
];

const readDocumentViewPreference = (preferenceKey) => {
    if (typeof window === 'undefined') return 'list';
    try {
        const savedMode = window.localStorage.getItem(preferenceKey);
        if (savedMode === 'list' || savedMode === 'grid') return savedMode;
    } catch {
        // A blocked localStorage should not prevent the documents page from loading.
    }
    return window.matchMedia('(max-width: 680px)').matches ? 'grid' : 'list';
};

const DocumentsTab = ({ familyId, members = [], currentUserRole = 'MEMBER', currentUserId = null }) => {
    const [documents, setDocuments] = useState([]);
    const [folders, setFolders] = useState([]);
    const [showHidden, setShowHidden] = useState(false);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    // Filter & Search State
    const [searchQuery, setSearchQuery] = useState('');
    const [selectedCategory, setSelectedCategory] = useState('ALL');
    const viewPreferenceKey = `familyvault.documents.viewMode.${currentUserId ?? 'guest'}`;
    const [viewModePreferences, setViewModePreferences] = useState(() => ({
        [viewPreferenceKey]: readDocumentViewPreference(viewPreferenceKey),
    }));
    const viewMode = viewModePreferences[viewPreferenceKey] || readDocumentViewPreference(viewPreferenceKey);

    // Upload Modal State
    const [isUploadModalOpen, setIsUploadModalOpen] = useState(false);
    const [documentName, setDocumentName] = useState('');
    const [documentType, setDocumentType] = useState('IDENTITY');
    const [expiryDate, setExpiryDate] = useState('');
    const [hideDocument, setHideDocument] = useState(false);
    const [selectedFile, setSelectedFile] = useState(null);
    const [uploading, setUploading] = useState(false);
    const [uploadError, setUploadError] = useState(null);

    // Short-lived Vault unlock state. The token intentionally stays in memory only.
    const [vaultConfigured, setVaultConfigured] = useState(false);
    const [vaultToken, setVaultToken] = useState(null);
    const [vaultExpiresAt, setVaultExpiresAt] = useState(null);
    const [vaultRemainingSeconds, setVaultRemainingSeconds] = useState(0);
    const [isVaultModalOpen, setIsVaultModalOpen] = useState(false);
    const [vaultPin, setVaultPin] = useState('');
    const [vaultConfirmPin, setVaultConfirmPin] = useState('');
    const [vaultAccountPassword, setVaultAccountPassword] = useState('');
    const [vaultError, setVaultError] = useState(null);
    const [vaultSubmitting, setVaultSubmitting] = useState(false);

    // Compact action menus
    const [activeDocumentMenuId, setActiveDocumentMenuId] = useState(null);

    // Edit Document State
    const [editingDoc, setEditingDoc] = useState(null);
    const [editDocumentName, setEditDocumentName] = useState('');
    const [editDocumentType, setEditDocumentType] = useState('IDENTITY');
    const [editExpiryDate, setEditExpiryDate] = useState('');
    const [editFolderId, setEditFolderId] = useState('');
    const [editHidden, setEditHidden] = useState(false);
    const [removeEditPassword, setRemoveEditPassword] = useState(false);
    const [editError, setEditError] = useState(null);
    const [savingEdit, setSavingEdit] = useState(false);

    // Delete Confirmation State
    const [deletingDoc, setDeletingDoc] = useState(null);
    const [isDeleting, setIsDeleting] = useState(false);

    // Download feedback state
    const [downloadingId, setDownloadingId] = useState(null);
    const [visibilityUpdatingId, setVisibilityUpdatingId] = useState(null);
    const [passwordDocument, setPasswordDocument] = useState(null);
    const [documentPassword, setDocumentPassword] = useState('');
    const [passwordError, setPasswordError] = useState(null);
    const [passwordSubmitting, setPasswordSubmitting] = useState(false);
    const [passwordAction, setPasswordAction] = useState('download');
    const [previewDocument, setPreviewDocument] = useState(null);
    const [previewUrl, setPreviewUrl] = useState(null);
    const [previewMimeType, setPreviewMimeType] = useState(null);
    const [openingId, setOpeningId] = useState(null);
    const [toastMessage, setToastMessage] = useState(null);

    const showToast = (msg) => {
        setToastMessage(msg);
        setTimeout(() => setToastMessage(null), 3500);
    };

    const isVaultUnlocked = Boolean(vaultToken && vaultExpiresAt && vaultRemainingSeconds > 0);

    const fetchDocuments = async (includeHidden = showHidden, token = vaultToken) => {
        setLoading(true);
        try {
            const data = await getDocuments(familyId, includeHidden, includeHidden ? token : null);
            setDocuments(Array.isArray(data) ? data.filter(Boolean) : []);
            setError(null);
        } catch (err) {
            console.error('Error fetching documents:', err);
            setError('Failed to load documents. Please try again.');
        } finally {
            setLoading(false);
        }
    };

    const fetchFolders = async (includeHidden = showHidden, token = vaultToken) => {
        const data = await getFolders(familyId, includeHidden, includeHidden ? token : null);
        setFolders(Array.isArray(data) ? data.filter(Boolean) : []);
    };

    useEffect(() => {
        setShowHidden(false);
        setVaultToken(null);
        setVaultExpiresAt(null);
        setActiveDocumentMenuId(null);
        fetchDocuments(false, null);
        fetchFolders(false, null)
            .catch((err) => {
                console.error('Error fetching folders:', err);
                setFolders([]);
            });
        getVaultStatus(familyId)
            .then((status) => setVaultConfigured(Boolean(status?.configured)))
            .catch(() => setVaultConfigured(false));
    }, [familyId]);

    useEffect(() => {
        const closeMenus = () => {
            setActiveDocumentMenuId(null);
        };
        const handleKeyDown = (event) => {
            if (event.key === 'Escape') closeMenus();
        };
        document.addEventListener('click', closeMenus);
        document.addEventListener('keydown', handleKeyDown);
        return () => {
            document.removeEventListener('click', closeMenus);
            document.removeEventListener('keydown', handleKeyDown);
        };
    }, []);

    useEffect(() => {
        if (!vaultExpiresAt) {
            setVaultRemainingSeconds(0);
            return undefined;
        }
        const updateCountdown = () => {
            const remaining = Math.max(0, Math.ceil((vaultExpiresAt - Date.now()) / 1000));
            setVaultRemainingSeconds(remaining);
            if (remaining === 0) {
                setVaultToken(null);
                setVaultExpiresAt(null);
                setShowHidden(false);
                setActiveDocumentMenuId(null);
                setEditingDoc(null);
                setDeletingDoc(null);
                setPasswordDocument(null);
                setDocumentPassword('');
                setPreviewDocument(null);
                setPreviewMimeType(null);
                setPreviewUrl(currentUrl => {
                    if (currentUrl) window.URL.revokeObjectURL(currentUrl);
                    return null;
                });
                fetchDocuments(false, null);
                fetchFolders(false, null).catch(() => setFolders([]));
                showToast('Vault locked automatically.');
            }
        };
        updateCountdown();
        const timer = window.setInterval(updateCountdown, 1000);
        return () => window.clearInterval(timer);
    }, [vaultExpiresAt]);

    const openVaultModal = () => {
        setVaultPin('');
        setVaultConfirmPin('');
        setVaultAccountPassword('');
        setVaultError(null);
        setIsVaultModalOpen(true);
    };

    const handleHiddenToggle = async () => {
        const next = !showHidden;
        if (!next) {
            await handleLockVault();
            return;
        }
        if (!isVaultUnlocked) {
            openVaultModal();
            return;
        }
        setShowHidden(true);
        try {
            await Promise.all([
                fetchDocuments(true, vaultToken),
                fetchFolders(true, vaultToken),
            ]);
        } catch (err) {
            setShowHidden(false);
            showToast(err.response?.data?.message || 'Could not open hidden content.');
        }
    };

    const handleVaultSubmit = async (e) => {
        e.preventDefault();
        if (!/^\d{6}$/.test(vaultPin)) {
            setVaultError('Enter a valid 6-digit Vault PIN.');
            return;
        }
        if (!vaultConfigured && vaultPin !== vaultConfirmPin) {
            setVaultError('Vault PIN and confirmation do not match.');
            return;
        }
        setVaultSubmitting(true);
        setVaultError(null);
        try {
            const result = vaultConfigured
                ? await unlockVault(familyId, vaultPin)
                : await setupVaultPin(familyId, vaultAccountPassword, vaultPin, vaultConfirmPin);
            setVaultConfigured(true);
            setVaultToken(result.vaultAccessToken);
            setVaultExpiresAt(result.expiresAt);
            setShowHidden(true);
            setIsVaultModalOpen(false);
            await Promise.all([
                fetchDocuments(true, result.vaultAccessToken),
                fetchFolders(true, result.vaultAccessToken),
            ]);
            showToast('Private vault unlocked. Use Hide & lock when finished.');
        } catch (err) {
            setVaultError(err.response?.data?.message || 'Could not unlock the vault.');
        } finally {
            setVaultSubmitting(false);
        }
    };

    const handleLockVault = async () => {
        setVaultToken(null);
        setVaultExpiresAt(null);
        setShowHidden(false);
        setActiveDocumentMenuId(null);
        setEditingDoc(null);
        setDeletingDoc(null);
        setPasswordDocument(null);
        setDocumentPassword('');
        closeDocumentPreview();
        await Promise.all([fetchDocuments(false, null), fetchFolders(false, null)]);
        showToast('Vault locked.');
    };

    const requestVaultForHiddenOption = () => {
        if (!isVaultUnlocked) {
            openVaultModal();
            return false;
        }
        return true;
    };

    const resetUploadForm = () => {
        setDocumentName('');
        setDocumentType('IDENTITY');
        setExpiryDate('');
        setHideDocument(false);
        setSelectedFile(null);
        setUploadError(null);
    };

    const openUploadModal = () => {
        resetUploadForm();
        setIsUploadModalOpen(true);
    };

    const closeUploadModal = () => {
        resetUploadForm();
        setIsUploadModalOpen(false);
    };

    const openEditModal = (doc) => {
        setEditingDoc(doc);
        setEditDocumentName(doc.documentName || '');
        setEditDocumentType(doc.documentType || 'OTHER');
        setEditExpiryDate(doc.expiryDate || '');
        setEditFolderId(doc.folderId != null ? String(doc.folderId) : '');
        setEditHidden(Boolean(doc.hidden));
        setRemoveEditPassword(false);
        setEditError(null);
    };

    const closeEditModal = () => {
        setEditingDoc(null);
        setEditError(null);
    };

    // Handle Upload
    const handleFileChange = (e) => {
        if (e.target.files && e.target.files[0]) {
            const file = e.target.files[0];
            if (file.size > 25 * 1024 * 1024) {
                setSelectedFile(null);
                setUploadError('Document size must not exceed 25MB.');
                e.target.value = '';
                return;
            }
            setUploadError(null);
            setSelectedFile(file);
            // Autofill document name if not already typed
            if (!documentName.trim()) {
                const nameWithoutExt = file.name.replace(/\.[^/.]+$/, '');
                setDocumentName(nameWithoutExt);
            }
        }
    };

    const handleUploadSubmit = async (e) => {
        e.preventDefault();
        if (!documentName.trim() || !selectedFile) {
            setUploadError('Please provide a document name and select a file.');
            return;
        }
        setUploading(true);
        setUploadError(null);

        try {
            const formData = new FormData();
            formData.append('documentName', documentName.trim());
            formData.append('documentType', documentType);
            if (expiryDate) {
                formData.append('expiryDate', expiryDate);
            }
            formData.append('hidden', String(hideDocument));
            formData.append('file', selectedFile);

            await uploadDocument(familyId, formData, vaultToken);
            showToast(`"${documentName.trim()}" uploaded successfully!`);
            
            closeUploadModal();

            await fetchDocuments();
        } catch (err) {
            console.error('Error uploading document:', err);
            setUploadError(err.response?.data?.message || err.message || 'Failed to upload document.');
        } finally {
            setUploading(false);
        }
    };

    const handleEditSubmit = async (e) => {
        e.preventDefault();
        if (!editingDoc) return;
        if (!editDocumentName.trim()) {
            setEditError('Document name is required.');
            return;
        }
        setSavingEdit(true);
        setEditError(null);
        try {
            await updateDocument(editingDoc.id, {
                documentName: editDocumentName.trim(),
                documentType: editDocumentType,
                expiryDate: editExpiryDate || null,
                folderId: editFolderId ? Number(editFolderId) : null,
                hidden: editHidden,
                password: removeEditPassword ? '' : null,
            }, vaultToken);
            showToast(`"${editDocumentName.trim()}" updated successfully.`);
            closeEditModal();
            await fetchDocuments(showHidden);
        } catch (err) {
            console.error('Error editing document:', err);
            setEditError(err.response?.data?.message || 'Failed to update document.');
        } finally {
            setSavingEdit(false);
        }
    };

    const saveDownloadedFile = (doc, response) => {
        let filename = doc.documentName || 'document';
        const disposition = response.headers['content-disposition'];
        if (disposition && disposition.includes('filename=')) {
            const match = /filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/.exec(disposition);
            if (match && match[1]) {
                filename = match[1].replace(/['"]/g, '');
            }
        } else if (!filename.includes('.')) {
            const contentType = response.headers['content-type'];
            if (contentType?.includes('pdf')) filename += '.pdf';
            else if (contentType?.includes('png')) filename += '.png';
            else if (contentType?.includes('jpeg') || contentType?.includes('jpg')) filename += '.jpg';
        }

        const blob = new Blob([response.data], {
            type: response.headers['content-type'] || 'application/octet-stream'
        });
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.setAttribute('download', filename);
        document.body.appendChild(link);
        link.click();
        link.parentNode.removeChild(link);
        window.URL.revokeObjectURL(url);
        showToast(`Downloaded "${doc.documentName}" successfully!`);
    };

    const getPreviewMimeType = (doc, response) => {
        const responseType = response.headers['content-type'];
        if (responseType && responseType !== 'application/octet-stream') {
            return responseType.split(';')[0];
        }

        const name = (doc.documentName || '').toLowerCase();
        if (name.endsWith('.pdf')) return 'application/pdf';
        if (name.endsWith('.png')) return 'image/png';
        if (name.endsWith('.jpg') || name.endsWith('.jpeg')) return 'image/jpeg';
        if (name.endsWith('.webp')) return 'image/webp';
        if (name.endsWith('.gif')) return 'image/gif';
        if (name.endsWith('.txt')) return 'text/plain';
        return null;
    };

    const showDocumentPreview = (doc, response) => {
        if (previewUrl) {
            window.URL.revokeObjectURL(previewUrl);
        }
        const mimeType = getPreviewMimeType(doc, response);
        const nextUrl = mimeType
            ? window.URL.createObjectURL(new Blob([response.data], { type: mimeType }))
            : null;
        setPreviewDocument(doc);
        setPreviewMimeType(mimeType);
        setPreviewUrl(nextUrl);
    };

    const closeDocumentPreview = () => {
        if (previewUrl) {
            window.URL.revokeObjectURL(previewUrl);
        }
        setPreviewDocument(null);
        setPreviewMimeType(null);
        setPreviewUrl(null);
    };

    // Handle File Download
    const handleDownload = async (doc) => {
        try {
            setDownloadingId(doc.id);
            const response = await downloadDocument(doc.id, null, vaultToken);
            saveDownloadedFile(doc, response);
        } catch (err) {
            if (doc.passwordProtected) {
                setPasswordDocument(doc);
                setPasswordAction('download');
                setDocumentPassword('');
                setPasswordError(null);
            } else {
                console.error('Error downloading document:', err);
                showToast('Failed to download document. Please try again.');
            }
        } finally {
            setDownloadingId(null);
        }
    };

    const handleOpenDocument = async (doc) => {
        try {
            setOpeningId(doc.id);
            const response = await downloadDocument(doc.id, null, vaultToken);
            showDocumentPreview(doc, response);
        } catch (err) {
            if (doc.passwordProtected) {
                setPasswordDocument(doc);
                setPasswordAction('preview');
                setDocumentPassword('');
                setPasswordError(null);
            } else {
                console.error('Error opening document:', err);
                showToast('Failed to open document. Please try again.');
            }
        } finally {
            setOpeningId(null);
        }
    };

    const handleProtectedDownload = async (e) => {
        e.preventDefault();
        if (!passwordDocument) return;
        if (documentPassword.trim().length < 6) {
            setPasswordError('Password must be at least 6 characters.');
            return;
        }

        setPasswordSubmitting(true);
        setPasswordError(null);
        try {
            const response = await downloadDocument(passwordDocument.id, documentPassword, vaultToken);
            if (passwordAction === 'preview') {
                showDocumentPreview(passwordDocument, response);
            } else {
                saveDownloadedFile(passwordDocument, response);
            }
            setPasswordDocument(null);
            setDocumentPassword('');
        } catch (err) {
            setPasswordError('Incorrect password. Please try again.');
        } finally {
            setPasswordSubmitting(false);
        }
    };

    // Handle Delete Document
    const handleConfirmDelete = async () => {
        if (!deletingDoc) return;
        setIsDeleting(true);
        try {
            await deleteDocument(deletingDoc.id, vaultToken);
            showToast(`"${deletingDoc.documentName}" deleted.`);
            setDeletingDoc(null);
            await fetchDocuments();
        } catch (err) {
            console.error('Error deleting document:', err);
            showToast('Failed to delete document.');
        } finally {
            setIsDeleting(false);
        }
    };

    const handleToggleVisibility = async (doc) => {
        if (visibilityUpdatingId === doc.id) return;
        if (!doc.hidden && !requestVaultForHiddenOption()) return;
        setVisibilityUpdatingId(doc.id);
        try {
            await updateDocumentProtection(doc.id, {
                hidden: !doc.hidden,
                password: null,
            }, vaultToken);
            showToast(doc.hidden
                ? `"${doc.documentName}" is visible now.`
                : `"${doc.documentName}" is hidden from the normal list.`);
            await fetchDocuments(showHidden);
        } catch (err) {
            console.error('Error updating document visibility:', err);
            showToast(err.response?.data?.message || 'Failed to update document visibility.');
        } finally {
            setVisibilityUpdatingId(null);
        }
    };

    // Helper: Find uploader name
    const getUploaderName = (doc) => {
        if (doc.uploadedByName) return doc.uploadedByName;
        if (members && members.length > 0 && doc.uploadedBy) {
            const member = members.find(m => m.userId === doc.uploadedBy);
            if (member) return member.name;
        }
        return 'Family Member';
    };

    // Helper: Get uploader initials
    const getInitials = (name) => {
        if (!name) return '??';
        const parts = name.trim().split(' ');
        if (parts.length > 1) {
            return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
        }
        return name.substring(0, 2).toUpperCase();
    };

    // Helper: File Type Icon and Color Styling
    const getFileMeta = (doc) => {
        const name = (doc.documentName || '').toLowerCase();
        const type = (doc.documentType || '').toLowerCase();
        
        if (name.endsWith('.pdf') || type.includes('pdf')) {
            return { label: 'PDF', bg: '#FEE2E2', color: '#DC2626' };
        }
        if (name.endsWith('.doc') || name.endsWith('.docx') || type.includes('word')) {
            return { label: 'DOC', bg: '#DBEAFE', color: '#2563EB' };
        }
        if (name.endsWith('.xls') || name.endsWith('.xlsx') || name.endsWith('.csv') || type.includes('excel')) {
            return { label: 'SHEET', bg: '#DCFCE7', color: '#16A34A' };
        }
        if (name.endsWith('.jpg') || name.endsWith('.jpeg') || name.endsWith('.png') || name.endsWith('.webp')) {
            return { label: 'IMAGE', bg: '#F3E8FF', color: '#9333EA' };
        }
        return { label: doc.documentType || 'DOC', bg: '#EEF2FF', color: '#4F46E5' };
    };

    // Helper: Format Expiry Date
    const formatExpiry = (expiryStr) => {
        if (!expiryStr) return null;
        try {
            const expiry = new Date(expiryStr);
            const today = new Date();
            const diffTime = expiry - today;
            const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));
            
            const formatted = expiry.toLocaleDateString(undefined, {
                year: 'numeric',
                month: 'short',
                day: 'numeric'
            });

            if (diffDays < 0) {
                return { text: `Expired (${formatted})`, isExpired: true };
            }
            if (diffDays <= 30) {
                return { text: `Expires in ${diffDays}d (${formatted})`, isExpiringSoon: true };
            }
            return { text: `Expires: ${formatted}`, isOk: true };
        } catch (e) {
            return { text: `Expires: ${expiryStr}`, isOk: true };
        }
    };

    // Format File Size
    const formatFileSize = (bytes) => {
        if (!bytes) return '';
        if (bytes < 1024) return bytes + ' B';
        if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB';
        return (bytes / 1048576).toFixed(1) + ' MB';
    };

    // Filter documents
    const filteredDocuments = documents.filter((doc) => {
        const matchesCategory = selectedCategory === 'ALL' || doc.documentType === selectedCategory;
        const query = searchQuery.trim().toLowerCase();
        if (!query) return matchesCategory;

        const nameMatch = doc.documentName?.toLowerCase().includes(query);
        const typeMatch = doc.documentType?.toLowerCase().includes(query);
        const uploaderMatch = getUploaderName(doc).toLowerCase().includes(query);

        return matchesCategory && (nameMatch || typeMatch || uploaderMatch);
    });

    const formatCreatedDate = (createdAt) => createdAt
        ? new Date(createdAt).toLocaleDateString(undefined, {
            year: 'numeric',
            month: 'short',
            day: 'numeric',
        })
        : 'Recently';

    const handleViewModeChange = (nextViewMode) => {
        if (nextViewMode === viewMode) return;
        setActiveDocumentMenuId(null);
        setViewModePreferences((current) => ({
            ...current,
            [viewPreferenceKey]: nextViewMode,
        }));
        try {
            window.localStorage.setItem(viewPreferenceKey, nextViewMode);
        } catch {
            // The selected view still works for this session when storage is unavailable.
        }
    };

    return (
        <div className="documents-tab">
            {/* Page Header */}
            <div className="doc-page-header">
                <div className="doc-header-text">
                    <span className="doc-badge">
                        <Shield size={13} /> Secure Vault Repository
                    </span>
                    <h2>Family <span className="doc-text-primary">Documents</span></h2>
                    <p>Securely store, organize, and share important files for today and tomorrow.</p>
                </div>
                <div className="doc-header-actions">
                    <button type="button" className="btn-upload-doc" onClick={() => openUploadModal()}>
                        <Plus size={18} /> <span>Upload Document</span>
                    </button>
                </div>
            </div>

            {error && (
                <div className="doc-error-banner">
                    <AlertCircle size={18} />
                    <span>{error}</span>
                </div>
            )}

            {/* Main Documents Card */}
            <div className="doc-card">
                {/* Search & Category Filter Bar */}
                <div className="doc-toolbar">
                    <div className="doc-search-box">
                        <Search size={18} className="doc-search-icon" />
                        <input 
                            type="text" 
                            placeholder="Search documents by name, category, or uploader..." 
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                        />
                        {searchQuery && (
                            <button 
                                type="button" 
                                className="doc-search-clear" 
                                onClick={() => setSearchQuery('')}
                            >
                                <X size={15} />
                            </button>
                        )}
                    </div>

                    <div className="doc-toolbar-controls">
                        <div className="doc-category-scroll">
                            {CATEGORIES.map((cat) => (
                                <button 
                                    key={cat.id}
                                    type="button"
                                    className={`doc-category-pill ${selectedCategory === cat.id ? 'active' : ''}`}
                                    onClick={() => setSelectedCategory(cat.id)}
                                >
                                    {cat.label}
                                    {cat.id === 'ALL' && (
                                        <span className="pill-count">{documents.length}</span>
                                    )}
                                </button>
                            ))}
                        </div>

                        <div className="doc-toolbar-actions">
                            <div className="doc-view-switcher" role="group" aria-label="Document view">
                                <button
                                    type="button"
                                    className={viewMode === 'list' ? 'active' : ''}
                                    aria-label="List view"
                                    aria-pressed={viewMode === 'list'}
                                    title="List view"
                                    onClick={() => handleViewModeChange('list')}
                                >
                                    <ListIcon size={16} />
                                    <span>List</span>
                                </button>
                                <button
                                    type="button"
                                    className={viewMode === 'grid' ? 'active' : ''}
                                    aria-label="Grid view"
                                    aria-pressed={viewMode === 'grid'}
                                    title="Grid view"
                                    onClick={() => handleViewModeChange('grid')}
                                >
                                    <LayoutGrid size={16} />
                                    <span>Grid</span>
                                </button>
                            </div>
                            <button
                                type="button"
                                className={`doc-hidden-filter ${showHidden ? 'active' : ''}`}
                                onClick={handleHiddenToggle}
                            >
                                {showHidden ? <Lock size={14} /> : <EyeOff size={14} />}
                                {showHidden ? 'Hide & lock' : 'View private'}
                            </button>
                        </div>
                    </div>
                </div>

                {/* Document Table */}
                {loading ? (
                    <div className="doc-loading-state">
                        <div className="doc-spinner" />
                        <p>Loading documents securely...</p>
                    </div>
                ) : filteredDocuments.length === 0 ? (
                    <div className="doc-empty-state">
                        <div className="empty-icon-wrap">
                            <Folder size={44} color="#5A45FF" />
                        </div>
                        <h3>{searchQuery || selectedCategory !== 'ALL' ? 'No matching documents found' : 'No documents uploaded yet'}</h3>
                        <p>
                            {searchQuery || selectedCategory !== 'ALL'
                                ? 'Try adjusting your search terms or selecting a different category filter.'
                                : 'Keep your passports, insurance policies, medical records, and certificates safe in one private vault.'}
                        </p>
                        {searchQuery || selectedCategory !== 'ALL' ? (
                            <button 
                                type="button" 
                                className="btn-empty-reset"
                                onClick={() => { setSearchQuery(''); setSelectedCategory('ALL'); }}
                            >
                                Reset Filters
                            </button>
                        ) : (
                            <button 
                                type="button" 
                                className="btn-upload-doc"
                                onClick={() => openUploadModal()}
                            >
                                <Plus size={18} />
                                <span>Upload First Document</span>
                            </button>
                        )}
                    </div>
                ) : viewMode === 'grid' ? (
                    <div className="doc-grid-wrap">
                        <div className="doc-document-grid" role="list" aria-label="Documents in grid view">
                            {filteredDocuments.map((doc, docIndex) => {
                                const meta = getFileMeta(doc);
                                const expiryInfo = formatExpiry(doc.expiryDate);
                                const uploaderName = getUploaderName(doc);
                                const isDownloading = downloadingId === doc.id;
                                const isOpening = openingId === doc.id;
                                const isVisibilityUpdating = visibilityUpdatingId === doc.id;
                                const isUploader = currentUserId != null && doc.uploadedBy != null && String(doc.uploadedBy) === String(currentUserId);
                                const canDelete = currentUserRole === 'OWNER' || currentUserRole === 'ADMIN' || isUploader;
                                const canChangeVisibility = currentUserRole === 'OWNER' || currentUserRole === 'ADMIN' || isUploader;
                                const parentFolder = folders.find(folder => String(folder.id) === String(doc.folderId));
                                const isPrivate = Boolean(doc.hidden || parentFolder?.hidden);
                                const categoryLabel = CATEGORIES.find(category => category.id === doc.documentType)?.label
                                    || doc.documentType
                                    || 'General';

                                return (
                                    <article
                                        key={doc.id}
                                        className={`doc-grid-card ${isPrivate ? 'private' : ''} ${activeDocumentMenuId === doc.id ? 'menu-open' : ''}`}
                                        role="listitem"
                                    >
                                        <div className="doc-grid-card-header">
                                            <div
                                                className="doc-grid-file-icon"
                                                style={{ backgroundColor: meta.bg, color: meta.color }}
                                            >
                                                <FileText size={25} />
                                            </div>
                                            <DocumentActionsMenu
                                                document={doc}
                                                isOpen={activeDocumentMenuId === doc.id}
                                                alignUp={filteredDocuments.length > 4 && docIndex >= filteredDocuments.length - 4}
                                                isOpening={isOpening}
                                                isDownloading={isDownloading}
                                                isVisibilityUpdating={isVisibilityUpdating}
                                                canChangeVisibility={canChangeVisibility}
                                                canDelete={canDelete}
                                                onToggle={() => setActiveDocumentMenuId(activeDocumentMenuId === doc.id ? null : doc.id)}
                                                onClose={() => setActiveDocumentMenuId(null)}
                                                onPreview={() => handleOpenDocument(doc)}
                                                onDownload={() => handleDownload(doc)}
                                                onEdit={() => openEditModal(doc)}
                                                onToggleVisibility={() => handleToggleVisibility(doc)}
                                                onDelete={() => setDeletingDoc(doc)}
                                            />
                                        </div>

                                        <button
                                            type="button"
                                            className="doc-grid-preview-target"
                                            onClick={() => handleOpenDocument(doc)}
                                            disabled={isOpening}
                                            aria-label={`Preview ${doc.documentName}`}
                                        >
                                            <span className="doc-grid-document-name" title={doc.documentName}>{doc.documentName}</span>
                                            <span className="doc-grid-preview-hint">{isOpening ? 'Opening preview...' : 'Click to preview document'}</span>
                                        </button>

                                        <div className="doc-grid-badges">
                                            <span className="doc-ext-tag" style={{ color: meta.color, backgroundColor: meta.bg }}>
                                                {meta.label}
                                            </span>
                                            {expiryInfo && (
                                                <span className={`doc-expiry-badge ${expiryInfo.isExpired ? 'expired' : (expiryInfo.isExpiringSoon ? 'warning' : 'ok')}`}>
                                                    <Clock size={11} /> {expiryInfo.text}
                                                </span>
                                            )}
                                            {doc.passwordProtected && (
                                                <span className="doc-protection-badge" title="Legacy password protected"><Lock size={11} /> Protected</span>
                                            )}
                                            {isPrivate && (
                                                <span className="doc-protection-badge hidden" title="Available only while the private vault is unlocked"><EyeOff size={11} /> Private</span>
                                            )}
                                        </div>

                                        <div className="doc-grid-category-row">
                                            <span>Category</span>
                                            <strong>{categoryLabel}</strong>
                                        </div>

                                        <div className="doc-grid-card-footer">
                                            <div className="doc-grid-uploader" title={`Uploaded by ${uploaderName}`}>
                                                <span className="doc-uploader-avatar">{getInitials(uploaderName)}</span>
                                                <span>{uploaderName}</span>
                                            </div>
                                            <div className="doc-grid-date">
                                                <Calendar size={13} />
                                                <span>{formatCreatedDate(doc.createdAt)}</span>
                                            </div>
                                        </div>
                                    </article>
                                );
                            })}
                        </div>
                    </div>
                ) : (
                    <div className="doc-table-wrap">
                        <table className="doc-table">
                            <thead>
                                <tr>
                                    <th>Document Name</th>
                                    <th>Category</th>
                                    <th>Uploaded By</th>
                                    <th>Date</th>
                                    <th style={{ textAlign: 'right', paddingRight: '1.25rem' }}>Actions</th>
                                </tr>
                            </thead>
                            <tbody>
                                {filteredDocuments.map((doc, docIndex) => {
                                    const meta = getFileMeta(doc);
                                    const expiryInfo = formatExpiry(doc.expiryDate);
                                    const uploaderName = getUploaderName(doc);
                                    const isDownloading = downloadingId === doc.id;
                                    const isOpening = openingId === doc.id;
                                    const isVisibilityUpdating = visibilityUpdatingId === doc.id;
                                    const isUploader = currentUserId != null && doc.uploadedBy != null && String(doc.uploadedBy) === String(currentUserId);
                                    const canDelete = currentUserRole === 'OWNER' || currentUserRole === 'ADMIN' || isUploader;
                                    const canChangeVisibility = currentUserRole === 'OWNER' || currentUserRole === 'ADMIN' || isUploader;
                                    const parentFolder = folders.find(folder => String(folder.id) === String(doc.folderId));

                                    return (
                                        <tr key={doc.id}>
                                            {/* Document Name & Type Badge */}
                                            <td>
                                                <div className="doc-name-cell">
                                                    <div 
                                                        className="doc-type-icon-box"
                                                        style={{ backgroundColor: meta.bg, color: meta.color }}
                                                    >
                                                        <FileText size={20} />
                                                    </div>
                                                    <div className="doc-title-group">
                                                        <span className="doc-primary-name" title={doc.documentName}>
                                                            {doc.documentName}
                                                        </span>
                                                        <div className="doc-subtags-row">
                                                            <span 
                                                                className="doc-ext-tag"
                                                                style={{ color: meta.color, backgroundColor: meta.bg }}
                                                            >
                                                                {meta.label}
                                                            </span>
                                                            {expiryInfo && (
                                                                <span className={`doc-expiry-badge ${expiryInfo.isExpired ? 'expired' : (expiryInfo.isExpiringSoon ? 'warning' : 'ok')}`}>
                                                                    <Clock size={11} />
                                                                    {expiryInfo.text}
                                                                </span>
                                                            )}
                                                            {doc.passwordProtected && (
                                                                <span className="doc-protection-badge" title="Password protected"><Lock size={11} /> Protected</span>
                                                            )}
                                                            {(doc.hidden || parentFolder?.hidden) && (
                                                                <span className="doc-protection-badge hidden" title="Available only while the private vault is unlocked"><EyeOff size={11} /> Private</span>
                                                            )}
                                                        </div>
                                                    </div>
                                                </div>
                                            </td>

                                            {/* Category */}
                                            <td>
                                                <span className="doc-category-badge">
                                                    {doc.documentType || 'General'}
                                                </span>
                                            </td>

                                            {/* Uploaded By */}
                                            <td>
                                                <div className="doc-uploader-cell">
                                                    <div className="doc-uploader-avatar">
                                                        {getInitials(uploaderName)}
                                                    </div>
                                                    <span className="doc-uploader-name">{uploaderName}</span>
                                                </div>
                                            </td>

                                            {/* Date */}
                                            <td>
                                                <div className="doc-date-cell">
                                                    <Calendar size={14} className="doc-date-icon" />
                                                    <span>{formatCreatedDate(doc.createdAt)}</span>
                                                </div>
                                            </td>

                                            {/* Actions */}
                                            <td style={{ textAlign: 'right', paddingRight: '1rem' }}>
                                                <DocumentActionsMenu
                                                    document={doc}
                                                    isOpen={activeDocumentMenuId === doc.id}
                                                    alignUp={docIndex >= filteredDocuments.length - 2}
                                                    isOpening={isOpening}
                                                    isDownloading={isDownloading}
                                                    isVisibilityUpdating={isVisibilityUpdating}
                                                    canChangeVisibility={canChangeVisibility}
                                                    canDelete={canDelete}
                                                    onToggle={() => setActiveDocumentMenuId(activeDocumentMenuId === doc.id ? null : doc.id)}
                                                    onClose={() => setActiveDocumentMenuId(null)}
                                                    onPreview={() => handleOpenDocument(doc)}
                                                    onDownload={() => handleDownload(doc)}
                                                    onEdit={() => openEditModal(doc)}
                                                    onToggleVisibility={() => handleToggleVisibility(doc)}
                                                    onDelete={() => setDeletingDoc(doc)}
                                                />
                                            </td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    </div>
                )}
            </div>

            {/* Vault PIN Setup / Unlock Modal */}
            {isVaultModalOpen && (
                <div className="doc-modal-overlay vault-overlay" onClick={() => !vaultSubmitting && setIsVaultModalOpen(false)}>
                    <div className="doc-modal-card doc-vault-modal-card" onClick={(event) => event.stopPropagation()}>
                        <button
                            type="button"
                            className="doc-modal-close-btn"
                            onClick={() => setIsVaultModalOpen(false)}
                            disabled={vaultSubmitting}
                            title="Close"
                        >
                            <X size={18} />
                        </button>
                        <div className="doc-modal-header">
                            <div className="doc-modal-icon-wrap vault-icon-wrap"><KeyRound size={22} /></div>
                            <div>
                                <h3>{vaultConfigured ? 'Unlock Hidden Vault' : 'Create Your Vault PIN'}</h3>
                                <p>{vaultConfigured
                                    ? 'Enter your PIN once to access hidden content for 10 minutes.'
                                    : 'Protect hidden folders and documents with a personal 6-digit PIN.'}</p>
                            </div>
                        </div>
                        <form className="doc-upload-form" onSubmit={handleVaultSubmit}>
                            {!vaultConfigured && (
                                <div className="form-group">
                                    <label htmlFor="vault-account-password">Current account password</label>
                                    <div className="doc-password-input-wrap">
                                        <Lock size={15} />
                                        <input
                                            id="vault-account-password"
                                            className="doc-form-input"
                                            type="password"
                                            value={vaultAccountPassword}
                                            onChange={(event) => { setVaultAccountPassword(event.target.value); setVaultError(null); }}
                                            placeholder="Confirm your account password"
                                            autoComplete="current-password"
                                            disabled={vaultSubmitting}
                                            autoFocus
                                        />
                                    </div>
                                </div>
                            )}
                            <div className="form-group">
                                <label htmlFor="vault-pin">{vaultConfigured ? 'Vault PIN' : 'Create 6-digit PIN'}</label>
                                <div className="doc-password-input-wrap vault-pin-input">
                                    <KeyRound size={15} />
                                    <input
                                        id="vault-pin"
                                        className="doc-form-input"
                                        type="password"
                                        inputMode="numeric"
                                        pattern="[0-9]{6}"
                                        maxLength={6}
                                        value={vaultPin}
                                        onChange={(event) => { setVaultPin(event.target.value.replace(/\D/g, '').slice(0, 6)); setVaultError(null); }}
                                        placeholder="••••••"
                                        autoComplete="off"
                                        disabled={vaultSubmitting}
                                        autoFocus={vaultConfigured}
                                    />
                                </div>
                            </div>
                            {!vaultConfigured && (
                                <div className="form-group">
                                    <label htmlFor="vault-confirm-pin">Confirm Vault PIN</label>
                                    <div className="doc-password-input-wrap vault-pin-input">
                                        <KeyRound size={15} />
                                        <input
                                            id="vault-confirm-pin"
                                            className="doc-form-input"
                                            type="password"
                                            inputMode="numeric"
                                            pattern="[0-9]{6}"
                                            maxLength={6}
                                            value={vaultConfirmPin}
                                            onChange={(event) => { setVaultConfirmPin(event.target.value.replace(/\D/g, '').slice(0, 6)); setVaultError(null); }}
                                            placeholder="••••••"
                                            autoComplete="off"
                                            disabled={vaultSubmitting}
                                        />
                                    </div>
                                </div>
                            )}
                            <div className="doc-vault-security-note">
                                <Shield size={16} />
                                <span>The unlock token stays only in this tab and expires automatically.</span>
                            </div>
                            {vaultError && <div className="doc-modal-error"><AlertCircle size={16} /><span>{vaultError}</span></div>}
                            <div className="doc-modal-actions">
                                <button type="button" className="doc-btn-cancel" onClick={() => setIsVaultModalOpen(false)} disabled={vaultSubmitting}>Cancel</button>
                                <button
                                    type="submit"
                                    className="doc-btn-primary"
                                    disabled={vaultSubmitting || vaultPin.length !== 6 || (!vaultConfigured && (!vaultAccountPassword || vaultConfirmPin.length !== 6))}
                                >
                                    <KeyRound size={17} />
                                    <span>{vaultSubmitting ? 'Verifying...' : vaultConfigured ? 'Unlock Vault' : 'Create & Unlock'}</span>
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}

            {/* Edit Document Modal */}
            {editingDoc && (
                <div className="doc-modal-overlay" onClick={() => !savingEdit && closeEditModal()}>
                    <div className="doc-modal-card doc-edit-modal-card" onClick={(e) => e.stopPropagation()}>
                        <button
                            type="button"
                            className="doc-modal-close-btn"
                            onClick={closeEditModal}
                            disabled={savingEdit}
                            title="Close"
                        >
                            <X size={18} />
                        </button>

                        <div className="doc-modal-header">
                            <div className="doc-modal-icon-wrap edit-icon-wrap">
                                <Pencil size={22} color="#5A45FF" />
                            </div>
                            <div>
                                <h3>Edit Document</h3>
                                <p>Update document details and access settings.</p>
                            </div>
                        </div>

                        <form className="doc-upload-form" onSubmit={handleEditSubmit}>
                            <div className="form-row-dual">
                                <div className="form-group">
                                    <label htmlFor="edit-document-name">Document Name <span className="req">*</span></label>
                                    <input
                                        id="edit-document-name"
                                        type="text"
                                        className="doc-form-input"
                                        value={editDocumentName}
                                        onChange={(e) => setEditDocumentName(e.target.value)}
                                        disabled={savingEdit}
                                        maxLength={120}
                                    />
                                </div>
                                <div className="form-group">
                                    <label htmlFor="edit-document-type">Category</label>
                                    <select
                                        id="edit-document-type"
                                        className="doc-form-select"
                                        value={editDocumentType}
                                        onChange={(e) => setEditDocumentType(e.target.value)}
                                        disabled={savingEdit}
                                    >
                                        {CATEGORIES.filter(category => category.id !== 'ALL').map(category => (
                                            <option key={category.id} value={category.id}>{category.label}</option>
                                        ))}
                                    </select>
                                </div>
                            </div>

                            <div className="form-group">
                                <label htmlFor="edit-expiry-date">Expiry Date (Optional)</label>
                                <input
                                    id="edit-expiry-date"
                                    type="date"
                                    className="doc-form-input"
                                    value={editExpiryDate || ''}
                                    onChange={(e) => setEditExpiryDate(e.target.value)}
                                    disabled={savingEdit}
                                />
                            </div>

                            <label className={`doc-folder-visibility-option ${editHidden ? 'selected' : ''}`}>
                                <input
                                    type="checkbox"
                                    checked={editHidden}
                                    onChange={(e) => {
                                        if (e.target.checked && !requestVaultForHiddenOption()) return;
                                        setEditHidden(e.target.checked);
                                    }}
                                    disabled={savingEdit}
                                />
                                <span className="doc-folder-visibility-icon">
                                    {editHidden ? <EyeOff size={17} /> : <Eye size={17} />}
                                </span>
                                <span className="doc-folder-visibility-copy">
                                    <strong>{editHidden ? 'Keep in private vault' : 'Show in family documents'}</strong>
                                    <small>{editHidden ? 'Vault PIN is required before this document appears.' : 'Family members can find it in the normal document list.'}</small>
                                </span>
                            </label>

                            {editingDoc.passwordProtected && (
                                <div className="doc-edit-password-section legacy-password-notice">
                                    <div className="doc-legacy-password-copy">
                                        <Lock size={17} />
                                        <div>
                                            <strong>Legacy document password is active</strong>
                                            <span>This file keeps its existing password until you remove it. New documents use the Vault PIN instead.</span>
                                        </div>
                                    </div>
                                    <label className="doc-edit-remove-password">
                                        <input
                                            type="checkbox"
                                            checked={removeEditPassword}
                                            onChange={(e) => setRemoveEditPassword(e.target.checked)}
                                            disabled={savingEdit}
                                        />
                                        <span>Remove custom password and use Vault protection only</span>
                                    </label>
                                </div>
                            )}

                            {editError && (
                                <div className="doc-modal-error">
                                    <AlertCircle size={16} />
                                    <span>{editError}</span>
                                </div>
                            )}

                            <div className="doc-modal-actions">
                                <button type="button" className="doc-btn-cancel" onClick={closeEditModal} disabled={savingEdit}>
                                    Cancel
                                </button>
                                <button type="submit" className="doc-btn-primary" disabled={!editDocumentName.trim() || savingEdit}>
                                    <Check size={17} />
                                    <span>{savingEdit ? 'Saving...' : 'Save Changes'}</span>
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}

            {/* Document Preview Modal */}
            {previewDocument && (
                <div className="doc-modal-overlay" onClick={closeDocumentPreview}>
                    <div className="doc-modal-card doc-preview-modal-card" onClick={(e) => e.stopPropagation()}>
                        <button
                            type="button"
                            className="doc-modal-close-btn"
                            onClick={closeDocumentPreview}
                            title="Close preview"
                        >
                            <X size={18} />
                        </button>

                        <div className="doc-modal-header">
                            <div className="doc-modal-icon-wrap preview-icon-wrap">
                                <FileText size={22} color="#5A45FF" />
                            </div>
                            <div>
                                <h3>Document Preview</h3>
                                <p>{previewDocument.documentName}</p>
                            </div>
                        </div>

                        <div className="doc-preview-content">
                            {previewUrl && previewMimeType === 'application/pdf' && (
                                <iframe title={`Preview of ${previewDocument.documentName}`} src={previewUrl} />
                            )}
                            {previewUrl && previewMimeType?.startsWith('image/') && (
                                <img src={previewUrl} alt={previewDocument.documentName} />
                            )}
                            {previewUrl && previewMimeType?.startsWith('text/') && (
                                <iframe title={`Preview of ${previewDocument.documentName}`} src={previewUrl} />
                            )}
                            {!previewUrl && (
                                <div className="doc-preview-unsupported">
                                    <div className="doc-preview-unsupported-icon"><FileText size={28} /></div>
                                    <h4>Preview is not available</h4>
                                    <p>This file type cannot be displayed in the browser. You can download it securely instead.</p>
                                </div>
                            )}
                        </div>

                        <div className="doc-modal-actions">
                            {previewUrl && (
                                <a
                                    className="doc-btn-cancel doc-preview-new-tab"
                                    href={previewUrl}
                                    target="_blank"
                                    rel="noreferrer"
                                >
                                    <ExternalLink size={16} /> Open in new tab
                                </a>
                            )}
                            <button type="button" className="doc-btn-cancel" onClick={closeDocumentPreview}>
                                Close
                            </button>
                            <button
                                type="button"
                                className="doc-btn-primary"
                                onClick={() => {
                                    const documentToDownload = previewDocument;
                                    closeDocumentPreview();
                                    handleDownload(documentToDownload);
                                }}
                            >
                                <Download size={16} /> Download
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* Protected Document Password Modal */}
            {passwordDocument && (
                <div className="doc-modal-overlay" onClick={() => !passwordSubmitting && setPasswordDocument(null)}>
                    <div className="doc-modal-card doc-password-modal-card" onClick={(e) => e.stopPropagation()}>
                        <button
                            type="button"
                            className="doc-modal-close-btn"
                            onClick={() => setPasswordDocument(null)}
                            disabled={passwordSubmitting}
                            title="Close"
                        >
                            <X size={18} />
                        </button>

                        <div className="doc-modal-header">
                            <div className="doc-modal-icon-wrap password-icon-wrap">
                                <Lock size={22} color="#5A45FF" />
                            </div>
                            <div>
                                <h3>Password Required</h3>
                                <p>Enter the password to download this protected document.</p>
                            </div>
                        </div>

                        <div className="doc-password-document-preview">
                            <FileText size={18} color="#5A45FF" />
                            <div>
                                <strong>{passwordDocument.documentName}</strong>
                                <span>Protected document</span>
                            </div>
                        </div>

                        <form className="doc-upload-form" onSubmit={handleProtectedDownload}>
                            <div className="form-group">
                                <label htmlFor="protected-document-password">Document Password</label>
                                <div className="doc-password-input-wrap">
                                    <Lock size={15} />
                                    <input
                                        id="protected-document-password"
                                        type="password"
                                        className="doc-form-input"
                                        placeholder="Enter document password"
                                        value={documentPassword}
                                        onChange={(e) => {
                                            setDocumentPassword(e.target.value);
                                            if (passwordError) setPasswordError(null);
                                        }}
                                        disabled={passwordSubmitting}
                                        autoFocus
                                    />
                                </div>
                            </div>

                            {passwordError && (
                                <div className="doc-modal-error">
                                    <AlertCircle size={16} />
                                    <span>{passwordError}</span>
                                </div>
                            )}

                            <div className="doc-modal-actions">
                                <button
                                    type="button"
                                    className="doc-btn-cancel"
                                    onClick={() => setPasswordDocument(null)}
                                    disabled={passwordSubmitting}
                                >
                                    Cancel
                                </button>
                                <button
                                    type="submit"
                                    className="doc-btn-primary"
                                    disabled={!documentPassword.trim() || passwordSubmitting}
                                >
                                    <Download size={17} />
                                    <span>{passwordSubmitting ? 'Verifying...' : 'Download Document'}</span>
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}

            {/* Upload Document Modal */}
            {isUploadModalOpen && (
                <div className="doc-modal-overlay" onClick={() => !uploading && closeUploadModal()}>
                    <div className="doc-modal-card" onClick={(e) => e.stopPropagation()}>
                        <button 
                            type="button" 
                            className="doc-modal-close-btn" 
                            onClick={closeUploadModal}
                            disabled={uploading}
                            title="Close"
                        >
                            <X size={18} />
                        </button>

                        <div className="doc-modal-header">
                            <div className="doc-modal-icon-wrap">
                                <Upload size={22} color="#5A45FF" />
                            </div>
                            <div>
                                <h3>Upload Document</h3>
                                <p>Add a new file to your secure family vault.</p>
                            </div>
                        </div>

                        <form className="doc-upload-form" onSubmit={handleUploadSubmit}>
                            {/* Document Title */}
                            <div className="form-group">
                                <label htmlFor="doc-title-input">
                                    Document Name <span className="req">*</span>
                                </label>
                                <input 
                                    id="doc-title-input"
                                    type="text" 
                                    className="doc-form-input"
                                    placeholder="e.g. Passport - Sarah, Health Insurance 2026..." 
                                    value={documentName}
                                    onChange={(e) => setDocumentName(e.target.value)}
                                    disabled={uploading}
                                    required
                                />
                            </div>

                            {/* Category & Expiry Row */}
                            <div className="form-row-dual">
                                <div className="form-group">
                                    <label htmlFor="doc-type-select">Category</label>
                                    <select 
                                        id="doc-type-select"
                                        className="doc-form-select"
                                        value={documentType}
                                        onChange={(e) => setDocumentType(e.target.value)}
                                        disabled={uploading}
                                    >
                                        <option value="IDENTITY">Identity & IDs</option>
                                        <option value="INSURANCE">Insurance & Policies</option>
                                        <option value="MEDICAL">Medical & Health</option>
                                        <option value="FINANCIAL">Financial & Tax</option>
                                        <option value="LEGAL">Legal & Property</option>
                                        <option value="EDUCATION">Education</option>
                                        <option value="OTHER">Other</option>
                                    </select>
                                </div>

                                <div className="form-group">
                                    <label htmlFor="doc-expiry-input">Expiry Date (Optional)</label>
                                    <input 
                                        id="doc-expiry-input"
                                        type="date" 
                                        className="doc-form-input"
                                        value={expiryDate}
                                        onChange={(e) => setExpiryDate(e.target.value)}
                                        disabled={uploading}
                                    />
                                </div>
                            </div>

                            <label className={`doc-folder-visibility-option doc-private-upload-option ${hideDocument ? 'selected' : ''}`}>
                                <input
                                    type="checkbox"
                                    checked={hideDocument}
                                    onChange={(e) => {
                                        if (e.target.checked && !requestVaultForHiddenOption()) return;
                                        setHideDocument(e.target.checked);
                                    }}
                                    disabled={uploading}
                                />
                                <span className="doc-folder-visibility-icon">
                                    {hideDocument ? <EyeOff size={17} /> : <Eye size={17} />}
                                </span>
                                <span className="doc-folder-visibility-copy">
                                    <strong>Keep in private vault</strong>
                                    <small>{hideDocument ? 'This document will require the Vault PIN to view.' : 'Turn this on for sensitive documents.'}</small>
                                </span>
                                <span className={`doc-private-toggle ${hideDocument ? 'on' : ''}`} aria-hidden="true">
                                    <span className="doc-private-toggle-knob" />
                                </span>
                                <span className="doc-private-toggle-state">{hideDocument ? 'ON' : 'OFF'}</span>
                            </label>

                            {/* File Upload Drop Zone */}
                            <div className="form-group">
                                <label>
                                    Select File <span className="req">*</span>
                                </label>
                                <div className={`doc-dropzone ${selectedFile ? 'has-file' : ''}`}>
                                    <input 
                                        type="file" 
                                        id="doc-file-input"
                                        className="doc-hidden-file-input"
                                        onChange={handleFileChange}
                                        disabled={uploading}
                                        accept=".pdf,.doc,.docx,.xls,.xlsx,.csv,.txt,.jpg,.jpeg,.png,.webp"
                                        required={!selectedFile}
                                    />
                                    <label htmlFor="doc-file-input" className="doc-dropzone-inner">
                                        <div className="doc-upload-icon-circle">
                                            <Upload size={22} color="#5A45FF" />
                                        </div>
                                        {selectedFile ? (
                                            <div className="doc-selected-file-details">
                                                <span className="selected-filename">{selectedFile.name}</span>
                                                <span className="selected-filesize">
                                                    {formatFileSize(selectedFile.size)} • Click to choose a different file
                                                </span>
                                            </div>
                                        ) : (
                                            <div className="doc-dropzone-prompt">
                                                <span className="prompt-primary">Click to browse or drag & drop</span>
                                                <span className="prompt-secondary">
                                                    PDF, Word, Excel, Images, Text (up to 25MB)
                                                </span>
                                            </div>
                                        )}
                                    </label>
                                </div>
                            </div>

                            {uploadError && (
                                <div className="doc-modal-error">
                                    <AlertCircle size={16} />
                                    <span>{uploadError}</span>
                                </div>
                            )}

                            {/* Modal Actions */}
                            <div className="doc-modal-actions">
                                <button 
                                    type="button" 
                                    className="doc-btn-cancel"
                                    onClick={closeUploadModal}
                                    disabled={uploading}
                                >
                                    Cancel
                                </button>
                                <button 
                                    type="submit" 
                                    className="doc-btn-primary"
                                    disabled={!documentName.trim() || !selectedFile || uploading}
                                >
                                    <Upload size={18} />
                                    <span>{uploading ? 'Uploading File...' : 'Upload Document'}</span>
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}

            {/* Delete Confirmation Modal */}
            {deletingDoc && (
                <div className="doc-modal-overlay" onClick={() => !isDeleting && setDeletingDoc(null)}>
                    <div className="doc-modal-card doc-delete-modal-card" onClick={(e) => e.stopPropagation()}>
                        <button 
                            type="button" 
                            className="doc-modal-close-btn" 
                            onClick={() => setDeletingDoc(null)}
                            disabled={isDeleting}
                            title="Close"
                        >
                            <X size={18} />
                        </button>

                        <div className="doc-modal-header">
                            <div className="doc-modal-icon-wrap delete-icon-wrap">
                                <Trash2 size={22} color="#EF4444" />
                            </div>
                            <div>
                                <h3>Delete Document</h3>
                                <p>Are you sure you want to delete this document from the vault?</p>
                            </div>
                        </div>

                        <div className="doc-delete-preview-box">
                            <FileText size={20} color="#DC2626" />
                            <div className="preview-info">
                                <span className="preview-name">{deletingDoc.documentName}</span>
                                <span className="preview-type">{deletingDoc.documentType || 'Document'}</span>
                            </div>
                        </div>

                        <p className="doc-delete-warning-text">
                            This will permanently remove the file from your family vault. Other family members will no longer be able to download or view it.
                        </p>

                        <div className="doc-modal-actions">
                            <button 
                                type="button" 
                                className="doc-btn-cancel"
                                onClick={() => setDeletingDoc(null)}
                                disabled={isDeleting}
                            >
                                Cancel
                            </button>
                            <button 
                                type="button" 
                                className="doc-btn-danger"
                                onClick={handleConfirmDelete}
                                disabled={isDeleting}
                            >
                                <Trash2 size={16} />
                                <span>{isDeleting ? 'Deleting...' : 'Yes, Delete Document'}</span>
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* Floating Toast Feedback */}
            {toastMessage && (
                <div className="doc-floating-toast">
                    <CheckCircle2 size={18} color="#10B981" />
                    <span>{toastMessage}</span>
                </div>
            )}
        </div>
    );
};

export default DocumentsTab;
