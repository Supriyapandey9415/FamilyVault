import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
    AlertCircle,
    AlertTriangle,
    Bell,
    CheckCircle2,
    Clock3,
    Database,
    FolderLock,
    KeyRound,
    Lock,
    LogOut,
    Pencil,
    RefreshCw,
    Save,
    Settings,
    Shield,
    ShieldCheck,
    Trash2,
    UserCog,
    Users,
    X,
} from 'lucide-react';
import { deleteFamily, leaveFamily } from '../services/familyService';
import { updateNotificationPreferences } from '../services/notificationPreferenceService';
import {
    getFamilySettings,
    transferFamilyOwnership,
    updateFamilyGeneralSettings,
    updateFamilyNotificationPreferences,
    updateFamilyPolicies,
} from '../services/familySettingsService';
import './SettingsTab.css';

const ROLE_SUMMARIES = {
    OWNER: 'Full control over this family vault, its policies, members, and content.',
    ADMIN: 'Can manage members and shared content within owner-defined policies.',
    MEMBER: 'Can collaborate on family content according to the current vault policies.',
    VIEWER: 'Can view permitted family content without making changes.',
};

const PERMISSION_DEFINITIONS = [
    { capability: 'FAMILY_RENAME', title: 'Rename family', description: 'Update the name shown across the family vault.', icon: Shield },
    { capability: 'MEMBER_INVITE', title: 'Invite members', description: 'Invite trusted people under the active family policy.', icon: Users },
    { capability: 'MEMBER_ROLE_CHANGE', title: 'Manage roles', description: 'Change eligible member roles and access.', icon: UserCog },
    { capability: 'FAMILY_DELETE', title: 'Delete family', description: 'Permanently remove this vault and its data.', icon: Trash2 },
    { capability: 'DOCUMENT_UPLOAD', title: 'Upload documents', description: 'Add documents to the shared family vault.', icon: FolderLock },
    { capability: 'MEMORY_CREATE', title: 'Create memories', description: 'Add memories to the shared family timeline.', icon: Database },
    { capability: 'PRIVATE_CONTENT_ACCESS', title: 'Use private vault', description: 'Unlock and access eligible hidden content.', icon: Lock },
    { capability: 'SETTINGS_POLICY_UPDATE', title: 'Change policies', description: 'Control collaboration and reminder policies.', icon: Settings },
];

const NOTIFICATION_OPTIONS = [
    { key: 'inAppEnabled', scope: 'account', title: 'In-app notifications', hint: 'Show non-critical activity in the notification bell.' },
    { key: 'invitationEnabled', scope: 'account', title: 'Family invitations', hint: 'Notify you when someone invites you to any family.' },
    { key: 'familyActivityEnabled', scope: 'family', title: 'This family’s activity', hint: 'Documents, memories, joins, removals, and role changes in this vault.' },
    { key: 'documentExpiryEnabled', scope: 'family', title: 'Document expiry reminders', hint: 'Use this family’s reminder policy for expiring documents.' },
];

const POLICY_OPTIONS = [
    { key: 'memberDocumentUploadEnabled', title: 'Member document uploads', hint: 'Allow MEMBER accounts to upload documents.' },
    { key: 'memberMemoryCreateEnabled', title: 'Member memory creation', hint: 'Allow MEMBER accounts to add family memories.' },
    { key: 'adminInvitationEnabled', title: 'Admin invitations', hint: 'Allow ADMIN accounts to invite members and viewers.' },
];

const getErrorMessage = (error, fallback) => error?.response?.data?.message || error?.message || fallback;
const roleLabel = (role = 'MEMBER') => role.charAt(0) + role.slice(1).toLowerCase();

const futureLocalDateTime = (hours) => {
    const date = new Date(Date.now() + hours * 60 * 60 * 1000);
    const shifted = new Date(date.getTime() - date.getTimezoneOffset() * 60 * 1000);
    return shifted.toISOString().slice(0, 19);
};

const SettingsTab = ({ familyId, members = [] }) => {
    const navigate = useNavigate();
    const [settingsData, setSettingsData] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [activeSection, setActiveSection] = useState('overview');
    const [toastMessage, setToastMessage] = useState(null);

    const [isEditingGeneral, setIsEditingGeneral] = useState(false);
    const [editedName, setEditedName] = useState('');
    const [editedTimezone, setEditedTimezone] = useState('UTC');
    const [savingGeneral, setSavingGeneral] = useState(false);
    const [generalError, setGeneralError] = useState(null);

    const [savingNotification, setSavingNotification] = useState(null);
    const [savingPolicy, setSavingPolicy] = useState(null);
    const [reminderDays, setReminderDays] = useState(30);
    const [selectedRole, setSelectedRole] = useState('MEMBER');

    const [isLeaveModalOpen, setIsLeaveModalOpen] = useState(false);
    const [isLeaving, setIsLeaving] = useState(false);
    const [leaveError, setLeaveError] = useState(null);
    const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
    const [confirmDeleteName, setConfirmDeleteName] = useState('');
    const [isDeleting, setIsDeleting] = useState(false);
    const [deleteError, setDeleteError] = useState(null);

    const [isTransferModalOpen, setIsTransferModalOpen] = useState(false);
    const [targetMemberId, setTargetMemberId] = useState('');
    const [accountPassword, setAccountPassword] = useState('');
    const [isTransferring, setIsTransferring] = useState(false);
    const [transferError, setTransferError] = useState(null);

    const showToast = (message) => {
        setToastMessage(message);
        window.setTimeout(() => setToastMessage(null), 3500);
    };

    const applySettings = useCallback((data) => {
        setSettingsData(data);
        setEditedName(data?.family?.name || '');
        setEditedTimezone(data?.policies?.timezone || 'UTC');
        setReminderDays(data?.policies?.documentExpiryReminderDays ?? 30);
        setSelectedRole((current) => {
            const roles = data?.roleCatalog?.map((item) => item.role) || [];
            return roles.includes(current) ? current : (data?.currentUser?.role || roles[0] || 'MEMBER');
        });
    }, []);

    const loadSettings = useCallback(async ({ quiet = false } = {}) => {
        if (!quiet) setLoading(true);
        try {
            const data = await getFamilySettings(familyId);
            applySettings(data);
            setError(null);
        } catch (requestError) {
            setError(getErrorMessage(requestError, 'Could not load family settings.'));
        } finally {
            if (!quiet) setLoading(false);
        }
    }, [applySettings, familyId]);

    useEffect(() => {
        loadSettings();
    }, [loadSettings]);

    const capabilities = useMemo(
        () => new Set(settingsData?.currentUser?.capabilities || []),
        [settingsData],
    );
    const can = (capability) => capabilities.has(capability);
    const family = settingsData?.family;
    const currentRole = settingsData?.currentUser?.role || 'MEMBER';
    const roleCatalog = settingsData?.roleCatalog || [];
    const selectedRoleCapabilities = new Set(
        roleCatalog.find((item) => item.role === selectedRole)?.capabilities || [],
    );
    const transferableMembers = members.filter((member) => member.role !== 'OWNER');
    const timezones = useMemo(() => {
        const browserZones = typeof Intl.supportedValuesOf === 'function'
            ? Intl.supportedValuesOf('timeZone')
            : ['UTC', 'Asia/Kolkata', 'Europe/London', 'America/New_York'];
        return browserZones.includes('UTC') ? browserZones : ['UTC', ...browserZones];
    }, []);

    const handleConflict = async (requestError) => {
        if (requestError?.response?.status === 409) {
            await loadSettings({ quiet: true });
            return 'Settings changed elsewhere. Latest values have been loaded.';
        }
        return null;
    };

    const handleSaveGeneral = async (event) => {
        event.preventDefault();
        if (!editedName.trim() || !settingsData) return;
        setSavingGeneral(true);
        setGeneralError(null);
        try {
            const updated = await updateFamilyGeneralSettings(familyId, {
                name: editedName.trim(),
                timezone: editedTimezone,
                version: settingsData.version,
            });
            applySettings(updated);
            setIsEditingGeneral(false);
            showToast('General settings updated.');
        } catch (requestError) {
            const conflict = await handleConflict(requestError);
            setGeneralError(conflict || getErrorMessage(requestError, 'Failed to update general settings.'));
        } finally {
            setSavingGeneral(false);
        }
    };

    const handleNotificationChange = async (option, value) => {
        if (!settingsData) return;
        setSavingNotification(option.key);
        try {
            if (option.scope === 'account') {
                const saved = await updateNotificationPreferences({ [option.key]: value });
                setSettingsData((current) => ({
                    ...current,
                    notifications: { ...current.notifications, [option.key]: saved[option.key] },
                }));
            } else {
                const current = settingsData.notifications;
                const updated = await updateFamilyNotificationPreferences(familyId, {
                    familyActivityEnabled: option.key === 'familyActivityEnabled' ? value : current.familyActivityEnabled,
                    documentExpiryEnabled: option.key === 'documentExpiryEnabled' ? value : current.documentExpiryEnabled,
                    mutedUntil: current.mutedUntil,
                    version: current.version,
                });
                applySettings(updated);
            }
            showToast('Notification preference updated.');
        } catch (requestError) {
            const conflict = await handleConflict(requestError);
            showToast(conflict || getErrorMessage(requestError, 'Failed to update notification preference.'));
        } finally {
            setSavingNotification(null);
        }
    };

    const handleMuteChange = async (mutedUntil) => {
        if (!settingsData) return;
        setSavingNotification('mutedUntil');
        try {
            const current = settingsData.notifications;
            const updated = await updateFamilyNotificationPreferences(familyId, {
                familyActivityEnabled: current.familyActivityEnabled,
                documentExpiryEnabled: current.documentExpiryEnabled,
                mutedUntil,
                version: current.version,
            });
            applySettings(updated);
            showToast(mutedUntil ? 'This family is muted for one hour.' : 'Family notifications resumed.');
        } catch (requestError) {
            const conflict = await handleConflict(requestError);
            showToast(conflict || getErrorMessage(requestError, 'Failed to update mute status.'));
        } finally {
            setSavingNotification(null);
        }
    };

    const handlePolicyChange = async (key, value) => {
        if (!settingsData || !can('SETTINGS_POLICY_UPDATE')) return;
        setSavingPolicy(key);
        try {
            const updated = await updateFamilyPolicies(familyId, {
                [key]: value,
                version: settingsData.version,
            });
            applySettings(updated);
            showToast('Family policy updated.');
        } catch (requestError) {
            const conflict = await handleConflict(requestError);
            showToast(conflict || getErrorMessage(requestError, 'Failed to update family policy.'));
        } finally {
            setSavingPolicy(null);
        }
    };

    const handleSaveReminderDays = () => {
        const parsed = Number(reminderDays);
        if (!Number.isInteger(parsed) || parsed < 1 || parsed > 365) {
            showToast('Reminder days must be between 1 and 365.');
            return;
        }
        handlePolicyChange('documentExpiryReminderDays', parsed);
    };

    const handleConfirmLeave = async () => {
        setIsLeaving(true);
        setLeaveError(null);
        try {
            await leaveFamily(familyId);
            setIsLeaveModalOpen(false);
            navigate('/');
        } catch (requestError) {
            setLeaveError(getErrorMessage(requestError, 'Failed to leave family vault.'));
        } finally {
            setIsLeaving(false);
        }
    };

    const handleConfirmDelete = async () => {
        if (confirmDeleteName.trim().toLowerCase() !== (family?.name || '').trim().toLowerCase()) {
            setDeleteError('Vault name does not match.');
            return;
        }
        setIsDeleting(true);
        setDeleteError(null);
        try {
            await deleteFamily(familyId);
            setIsDeleteModalOpen(false);
            navigate('/');
        } catch (requestError) {
            setDeleteError(getErrorMessage(requestError, 'Failed to delete family vault.'));
        } finally {
            setIsDeleting(false);
        }
    };

    const handleTransferOwnership = async (event) => {
        event.preventDefault();
        setIsTransferring(true);
        setTransferError(null);
        try {
            const updated = await transferFamilyOwnership(familyId, {
                targetMemberId: Number(targetMemberId),
                currentAccountPassword: accountPassword,
            });
            applySettings(updated);
            setIsTransferModalOpen(false);
            setTargetMemberId('');
            setAccountPassword('');
            showToast('Ownership transferred successfully.');
        } catch (requestError) {
            setTransferError(getErrorMessage(requestError, 'Failed to transfer ownership.'));
        } finally {
            setIsTransferring(false);
        }
    };

    const sections = [
        { id: 'overview', label: 'Overview', icon: Shield },
        { id: 'notifications', label: 'Notifications', icon: Bell },
        { id: 'access', label: 'Members & Roles', icon: UserCog },
        { id: 'security', label: 'Security & Privacy', icon: ShieldCheck },
        { id: 'content', label: 'Policies', icon: FolderLock },
        { id: 'danger', label: 'Danger Zone', icon: AlertTriangle },
    ];

    return (
        <div className="settings-tab">
            <div className="settings-page-header">
                <div className="settings-header-text">
                    <span className="settings-badge"><Settings size={13} /> Vault Preferences</span>
                    <h2>Family <span className="settings-text-primary">Settings</span></h2>
                    <p>Manage this vault through server-enforced roles, policies, and preferences.</p>
                </div>
                <button type="button" className="settings-refresh-button" onClick={() => loadSettings()} disabled={loading}>
                    <RefreshCw size={15} className={loading ? 'spinning' : ''} /> Refresh
                </button>
            </div>

            {error && (
                <div className="settings-error-banner">
                    <AlertCircle size={18} />
                    <span>{error}</span>
                    <button type="button" onClick={() => loadSettings()}>Try again</button>
                </div>
            )}

            <div className="settings-section-nav" role="tablist" aria-label="Family settings sections">
                {sections.map((section) => {
                    const Icon = section.icon;
                    return (
                        <button
                            key={section.id}
                            type="button"
                            role="tab"
                            aria-selected={activeSection === section.id}
                            className={`settings-section-tab ${activeSection === section.id ? 'active' : ''}`}
                            onClick={() => setActiveSection(section.id)}
                        >
                            <Icon size={16} /><span>{section.label}</span>
                        </button>
                    );
                })}
            </div>

            {loading && !settingsData ? (
                <div className="settings-card settings-loading-box">
                    <div className="settings-spinner" /><span>Loading secure family settings...</span>
                </div>
            ) : settingsData && (
                <>
                    {activeSection === 'overview' && (
                        <div className="settings-card">
                            <div className="settings-card-header">
                                <div className="settings-header-icon-box"><Shield size={22} color="#5A45FF" /></div>
                                <div><h3>Vault Overview & Information</h3><p>Live information returned by the FamilyVault server.</p></div>
                            </div>
                            <div className="settings-grid">
                                <div className="settings-info-item settings-info-item-wide">
                                    <div className="info-item-label"><Shield size={16} /><span>Vault identity</span></div>
                                    {isEditingGeneral ? (
                                        <form onSubmit={handleSaveGeneral} className="settings-general-form">
                                            <label>Family name<input className="settings-name-input" value={editedName} maxLength={120} onChange={(event) => setEditedName(event.target.value)} required /></label>
                                            <label>Timezone<select className="settings-name-input" value={editedTimezone} onChange={(event) => setEditedTimezone(event.target.value)}>{timezones.map((zone) => <option key={zone} value={zone}>{zone}</option>)}</select></label>
                                            <div className="name-edit-buttons">
                                                <button type="submit" className="btn-name-save" disabled={savingGeneral || !editedName.trim()}><Save size={14} />{savingGeneral ? 'Saving...' : 'Save'}</button>
                                                <button type="button" className="btn-name-cancel" disabled={savingGeneral} onClick={() => { setEditedName(family.name); setEditedTimezone(settingsData.policies.timezone); setGeneralError(null); setIsEditingGeneral(false); }}><X size={14} />Cancel</button>
                                            </div>
                                            {generalError && <span className="edit-name-error">{generalError}</span>}
                                        </form>
                                    ) : (
                                        <>
                                            <div className="info-item-value-row">
                                                <div><span className="vault-name-text">{family.name}</span><span className="settings-secondary-value">{settingsData.policies.timezone}</span></div>
                                                {can('FAMILY_RENAME') && <button type="button" className="btn-edit-name" onClick={() => setIsEditingGeneral(true)}><Pencil size={14} /> Edit</button>}
                                            </div>
                                            <span className="info-item-hint">A friendly name is shown instead of exposing an internal database ID.</span>
                                        </>
                                    )}
                                </div>
                                <div className="settings-info-item">
                                    <div className="info-item-label"><Users size={16} /><span>Vault activity</span></div>
                                    <div className="settings-metric-row"><strong>{family.memberCount}</strong><span>members</span><strong>{family.documentCount}</strong><span>documents</span><strong>{family.memoryCount}</strong><span>memories</span></div>
                                    <span className="info-item-hint">Counts are calculated from current persisted data.</span>
                                </div>
                                <div className="settings-info-item">
                                    <div className="info-item-label"><UserCog size={16} /><span>Your effective role</span></div>
                                    <div className="security-status-badge"><span className="status-dot" /><span>{roleLabel(currentRole)}</span></div>
                                    <span className="info-item-hint">Your actions are authorized by backend capabilities, not browser state.</span>
                                </div>
                            </div>
                        </div>
                    )}

                    {activeSection === 'notifications' && (
                        <div className="settings-card">
                            <div className="settings-card-header">
                                <div className="settings-header-icon-box"><Bell size={22} color="#5A45FF" /></div>
                                <div><h3>Notification Preferences</h3><p>Account-wide controls and this family’s preferences are kept separate.</p></div>
                            </div>
                            <div className="settings-preference-list">
                                {NOTIFICATION_OPTIONS.map((option) => (
                                    <div className="settings-preference-row" key={option.key}>
                                        <div><div className="settings-row-title"><h4>{option.title}</h4><span className="settings-scope-pill">{option.scope}</span></div><p>{option.hint}</p></div>
                                        <button type="button" role="switch" aria-checked={Boolean(settingsData.notifications[option.key])} className={`settings-toggle ${settingsData.notifications[option.key] ? 'on' : ''}`} disabled={savingNotification === option.key} onClick={() => handleNotificationChange(option, !settingsData.notifications[option.key])}><span /></button>
                                    </div>
                                ))}
                                <div className="settings-preference-row">
                                    <div><h4>Temporarily mute this family</h4><p>{settingsData.notifications.mutedUntil ? `Muted until ${new Date(settingsData.notifications.mutedUntil).toLocaleString()}` : 'Pause family activity and expiry alerts without changing account-wide settings.'}</p></div>
                                    <button type="button" className="settings-secondary-button" disabled={savingNotification === 'mutedUntil'} onClick={() => handleMuteChange(settingsData.notifications.mutedUntil ? null : futureLocalDateTime(1))}><Clock3 size={15} />{settingsData.notifications.mutedUntil ? 'Resume' : 'Mute 1 hour'}</button>
                                </div>
                                <div className="settings-info-note"><ShieldCheck size={17} /><span>Critical account-security alerts remain enabled for your protection.</span></div>
                            </div>
                        </div>
                    )}

                    {activeSection === 'access' && (
                        <div className="settings-card">
                            <div className="settings-card-header">
                                <div className="settings-header-icon-box"><UserCog size={22} color="#5A45FF" /></div>
                                <div><h3>Members & Roles</h3><p>Effective access is generated from backend capabilities and active policies.</p></div>
                            </div>
                            <div className="settings-access-summary">
                                <div className="settings-current-role-card"><div className="settings-role-avatar"><UserCog size={19} /></div><div className="settings-role-summary-copy"><span className="settings-eyebrow">Your access</span><div className="settings-role-title-row"><h4>{roleLabel(currentRole)} role</h4><span className="settings-role-pill current">{currentRole}</span></div><p>{ROLE_SUMMARIES[currentRole]}</p></div></div>
                                <div className="settings-member-count-card"><div className="settings-member-count-icon"><Users size={18} /></div><div><strong>{family.memberCount}</strong><span>Active {family.memberCount === 1 ? 'member' : 'members'}</span></div></div>
                            </div>
                            <div className="settings-permissions-toolbar">
                                <div><h4>Role permissions</h4><p>Select a role to review its current effective access.</p></div>
                                <div className="settings-role-selector" role="tablist" aria-label="Select a role">
                                    {roleCatalog.map(({ role }) => <button key={role} type="button" role="tab" aria-selected={selectedRole === role} className={`settings-role-selector-button ${selectedRole === role ? 'active' : ''}`} onClick={() => setSelectedRole(role)}>{roleLabel(role)}</button>)}
                                </div>
                            </div>
                            <div className="settings-permission-list">
                                {PERMISSION_DEFINITIONS.map((permission) => {
                                    const Icon = permission.icon;
                                    const allowed = selectedRoleCapabilities.has(permission.capability);
                                    return <div className={`settings-permission-row ${allowed ? '' : 'disabled'}`} key={permission.capability}><div className="settings-permission-icon"><Icon size={17} /></div><div className="settings-permission-copy"><h4>{permission.title}</h4><p>{permission.description}</p></div><span className={`settings-permission-status ${allowed ? 'allowed' : 'restricted'}`}>{allowed ? <CheckCircle2 size={15} /> : <Lock size={14} />}{allowed ? 'Allowed' : 'Restricted'}</span></div>;
                                })}
                            </div>
                            <div className="settings-info-note"><ShieldCheck size={17} /><span>The UI reflects permissions; the server independently enforces every protected action.</span></div>
                        </div>
                    )}

                    {activeSection === 'security' && (
                        <div className="settings-card">
                            <div className="settings-card-header"><div className="settings-header-icon-box"><ShieldCheck size={22} color="#5A45FF" /></div><div><h3>Security & Privacy</h3><p>Live protection state for your account in this family vault.</p></div></div>
                            <div className="settings-security-grid">
                                <div className="settings-security-item"><ShieldCheck size={18} /><div><h4>Private family access</h4><p>Only authenticated members can retrieve family content.</p></div><span className="settings-status-pill enabled">Enforced</span></div>
                                <div className="settings-security-item"><KeyRound size={18} /><div><h4>Personal Vault PIN</h4><p>{settingsData.security.privateVaultConfigured ? `Configured · auto-locks after ${Math.round(settingsData.security.autoLockSeconds / 60)} minutes` : 'Create a PIN from Documents when opening private content.'}</p></div><span className={`settings-status-pill ${settingsData.security.privateVaultConfigured ? 'enabled' : 'available'}`}>{settingsData.security.privateVaultUnlocked ? 'Unlocked' : settingsData.security.privateVaultConfigured ? 'Locked' : 'Not set'}</span></div>
                                <div className="settings-security-item"><FolderLock size={18} /><div><h4>Private files</h4><p>File bytes are served through authenticated API endpoints.</p></div><span className="settings-status-pill enabled">Protected</span></div>
                                <div className="settings-security-item"><Bell size={18} /><div><h4>Security alerts</h4><p>Critical account alerts are not controlled by family muting.</p></div><span className="settings-status-pill enabled">Always on</span></div>
                            </div>
                        </div>
                    )}

                    {activeSection === 'content' && (
                        <div className="settings-card">
                            <div className="settings-card-header"><div className="settings-header-icon-box"><FolderLock size={22} color="#5A45FF" /></div><div><h3>Family Policies</h3><p>Owner-controlled rules that immediately change effective capabilities.</p></div></div>
                            <div className="settings-preference-list">
                                {POLICY_OPTIONS.map((policy) => <div className="settings-preference-row" key={policy.key}><div><h4>{policy.title}</h4><p>{policy.hint}</p></div><button type="button" role="switch" aria-checked={Boolean(settingsData.policies[policy.key])} className={`settings-toggle ${settingsData.policies[policy.key] ? 'on' : ''}`} disabled={!can('SETTINGS_POLICY_UPDATE') || savingPolicy === policy.key} onClick={() => handlePolicyChange(policy.key, !settingsData.policies[policy.key])}><span /></button></div>)}
                                <div className="settings-preference-row"><div><h4>Expiry reminder window</h4><p>Notify users this many days before a document expires.</p></div><div className="settings-inline-control"><input type="number" min="1" max="365" value={reminderDays} disabled={!can('SETTINGS_POLICY_UPDATE')} onChange={(event) => setReminderDays(event.target.value)} /><span>days</span><button type="button" className="settings-secondary-button" disabled={!can('SETTINGS_POLICY_UPDATE') || savingPolicy === 'documentExpiryReminderDays'} onClick={handleSaveReminderDays}>Save</button></div></div>
                                {!can('SETTINGS_POLICY_UPDATE') && <div className="settings-info-note"><Lock size={17} /><span>Only the family owner can change collaboration policies. You can still review their current values.</span></div>}
                            </div>
                        </div>
                    )}

                    {activeSection === 'danger' && (
                        <div className="settings-card danger-zone-card">
                            <div className="settings-card-header danger-header"><div className="settings-header-icon-box danger-icon-box"><AlertTriangle size={22} color="#EF4444" /></div><div><h3 className="danger-title">Danger Zone</h3><p>High-impact actions are displayed from your effective capabilities.</p></div></div>
                            <div className="danger-actions-list">
                                {can('OWNERSHIP_TRANSFER') && <div className="danger-action-row"><div className="danger-action-text"><h4>Transfer ownership</h4><p>Make another member the owner. Your role will become Admin.</p></div><button type="button" className="btn-danger-outline" disabled={!transferableMembers.length} onClick={() => setIsTransferModalOpen(true)}><UserCog size={16} />Transfer</button></div>}
                                {can('FAMILY_LEAVE') && <div className="danger-action-row"><div className="danger-action-text"><h4>Leave Family Vault</h4><p>Remove your access to this vault and its shared content.</p></div><button type="button" className="btn-danger-outline" onClick={() => setIsLeaveModalOpen(true)}><LogOut size={16} />Leave Family</button></div>}
                                {can('FAMILY_DELETE') && <div className="danger-action-row"><div className="danger-action-text"><h4>Delete This Family Vault</h4><p>Permanently delete family records and schedule stored files for cleanup after commit.</p></div><button type="button" className="btn-danger-solid" onClick={() => { setConfirmDeleteName(''); setDeleteError(null); setIsDeleteModalOpen(true); }}><Trash2 size={16} />Delete Vault</button></div>}
                            </div>
                        </div>
                    )}
                </>
            )}

            {isLeaveModalOpen && <div className="settings-modal-overlay" onClick={() => !isLeaving && setIsLeaveModalOpen(false)}><div className="settings-modal-card" onClick={(event) => event.stopPropagation()}><button type="button" className="settings-modal-close" onClick={() => setIsLeaveModalOpen(false)} disabled={isLeaving}><X size={18} /></button><div className="settings-modal-header"><div className="settings-modal-icon-wrap warning-wrap"><LogOut size={22} color="#F59E0B" /></div><div><h3>Leave Family Vault?</h3><p>You will lose access to <strong>{family?.name}</strong>.</p></div></div><p className="settings-modal-description">An owner or admin must invite you again if you want to return.</p>{leaveError && <div className="settings-modal-error"><AlertCircle size={16} /><span>{leaveError}</span></div>}<div className="settings-modal-actions"><button type="button" className="btn-modal-cancel" onClick={() => setIsLeaveModalOpen(false)} disabled={isLeaving}>Cancel</button><button type="button" className="btn-modal-leave" onClick={handleConfirmLeave} disabled={isLeaving}><LogOut size={16} />{isLeaving ? 'Leaving...' : 'Yes, Leave Vault'}</button></div></div></div>}

            {isDeleteModalOpen && <div className="settings-modal-overlay" onClick={() => !isDeleting && setIsDeleteModalOpen(false)}><div className="settings-modal-card" onClick={(event) => event.stopPropagation()}><button type="button" className="settings-modal-close" onClick={() => setIsDeleteModalOpen(false)} disabled={isDeleting}><X size={18} /></button><div className="settings-modal-header"><div className="settings-modal-icon-wrap delete-wrap"><Trash2 size={22} color="#EF4444" /></div><div><h3 className="danger-title">Delete Family Vault</h3><p>This permanently erases <strong>{family?.name}</strong>.</p></div></div><div className="delete-confirmation-box"><p>Type <strong>{family?.name}</strong> to confirm:</p><input className="confirm-delete-input" value={confirmDeleteName} onChange={(event) => setConfirmDeleteName(event.target.value)} disabled={isDeleting} /></div>{deleteError && <div className="settings-modal-error"><AlertCircle size={16} /><span>{deleteError}</span></div>}<div className="settings-modal-actions"><button type="button" className="btn-modal-cancel" onClick={() => setIsDeleteModalOpen(false)} disabled={isDeleting}>Cancel</button><button type="button" className="btn-modal-delete" onClick={handleConfirmDelete} disabled={isDeleting || confirmDeleteName.trim().toLowerCase() !== (family?.name || '').trim().toLowerCase()}><Trash2 size={16} />{isDeleting ? 'Deleting...' : 'Delete Permanently'}</button></div></div></div>}

            {isTransferModalOpen && <div className="settings-modal-overlay" onClick={() => !isTransferring && setIsTransferModalOpen(false)}><form className="settings-modal-card" onSubmit={handleTransferOwnership} onClick={(event) => event.stopPropagation()}><button type="button" className="settings-modal-close" onClick={() => setIsTransferModalOpen(false)} disabled={isTransferring}><X size={18} /></button><div className="settings-modal-header"><div className="settings-modal-icon-wrap warning-wrap"><UserCog size={22} color="#5A45FF" /></div><div><h3>Transfer ownership</h3><p>This action changes your role to Admin.</p></div></div><div className="settings-modal-fields"><label>New owner<select className="confirm-delete-input" value={targetMemberId} onChange={(event) => setTargetMemberId(event.target.value)} required><option value="">Select a family member</option>{transferableMembers.map((member) => <option key={member.id} value={member.id}>{member.name || member.email} · {roleLabel(member.role)}</option>)}</select></label><label>Current account password<input type="password" className="confirm-delete-input" value={accountPassword} onChange={(event) => setAccountPassword(event.target.value)} autoComplete="current-password" required /></label></div>{transferError && <div className="settings-modal-error"><AlertCircle size={16} /><span>{transferError}</span></div>}<div className="settings-modal-actions"><button type="button" className="btn-modal-cancel" onClick={() => setIsTransferModalOpen(false)} disabled={isTransferring}>Cancel</button><button type="submit" className="btn-name-save" disabled={isTransferring || !targetMemberId || !accountPassword}><UserCog size={16} />{isTransferring ? 'Transferring...' : 'Confirm Transfer'}</button></div></form></div>}

            {toastMessage && <div className="settings-floating-toast"><CheckCircle2 size={18} color="#10B981" /><span>{toastMessage}</span></div>}
        </div>
    );
};

export default SettingsTab;
