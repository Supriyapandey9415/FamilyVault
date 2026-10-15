import React, { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import { getFamilies } from '../services/familyService';
import { getFamilyMembers, createFamilyInvitation, getPendingInvitations, resendFamilyInvitation, revokeFamilyInvitation, updateFamilyMemberRole, removeFamilyMember } from '../services/familyMemberService';
import { getNotifications, getUnreadNotificationCount, markNotificationAsRead, markAllNotificationsAsRead } from '../services/notificationService';
import { 
    Shield, Users, Folder, Image, Settings, UserPlus, Mail, User, 
    ChevronDown, MoreVertical, Heart, Pencil, Trash2, X, 
    Check, CheckCircle2, ShieldCheck, AlertCircle, Info,
    Bell, BellOff, CheckCheck, Sparkles
} from 'lucide-react';
import MemoriesTab from '../components/MemoriesTab';
import DocumentsTab from '../components/DocumentsTab';
import SettingsTab from '../components/SettingsTab';
import UserNavProfile from '../components/UserNavProfile';
import ChatbotWidget from '../components/ChatbotWidget';
import './FamilyView.css';
import { AuthContext } from '../context/AuthContext';

const FamilyView = () => {
    const { user: signedInUser } = React.useContext(AuthContext);
    const { id: familyId } = useParams();
    const navigate = useNavigate();
    const location = useLocation();
    const getTabFromLocation = () => {
        const tab = new URLSearchParams(location.search).get('tab');
        return ['members', 'documents', 'memories', 'settings'].includes(tab) ? tab : 'members';
    };

    const [activeTab, setActiveTab] = useState(getTabFromLocation);
    const [families, setFamilies] = useState([]);
    const [currentFamily, setCurrentFamily] = useState(null);
    const [isFamilySwitcherOpen, setIsFamilySwitcherOpen] = useState(false);
    
    const [members, setMembers] = useState([]);
    const [pendingInvitations, setPendingInvitations] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    
    const [email, setEmail] = useState('');
    const [role, setRole] = useState('MEMBER');
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [addError, setAddError] = useState(null);

    // 3-Dot Action Menu State
    const [activeMemberMenuId, setActiveMemberMenuId] = useState(null);

    // Modify Member Role State
    const [modifyingMember, setModifyingMember] = useState(null);
    const [selectedRole, setSelectedRole] = useState('MEMBER');
    const [savingRole, setSavingRole] = useState(false);
    const [modifyError, setModifyError] = useState(null);

    // Remove Member State
    const [removingMember, setRemovingMember] = useState(null);
    const [removingLoading, setRemovingLoading] = useState(false);
    const [removeError, setRemoveError] = useState(null);

    // Feedback Toast
    const [toastMessage, setToastMessage] = useState(null);

    // Sidebar Card Interactive Heart State
    const [heartLiked, setHeartLiked] = useState(false);
    const [showHeartToast, setShowHeartToast] = useState(false);

    const handleToggleHeart = (e) => {
        e.stopPropagation();
        const nextState = !heartLiked;
        setHeartLiked(nextState);
        if (nextState) {
            setShowHeartToast(true);
            setTimeout(() => setShowHeartToast(false), 2200);
        }
    };

    // Notification State & Polling
    const [notifications, setNotifications] = useState([]);
    const [notificationFilter, setNotificationFilter] = useState('all');
    const [notificationLoading, setNotificationLoading] = useState(false);
    const [unreadCount, setUnreadCount] = useState(0);
    const [isNotifOpen, setIsNotifOpen] = useState(false);
    const notifDropdownRef = useRef(null);
    const familySwitcherRef = useRef(null);

    const fetchFamilyContext = async () => {
        setCurrentFamily(null);
        try {
            const data = await getFamilies();
            const accessibleFamilies = data || [];
            setFamilies(accessibleFamilies);
            setCurrentFamily(
                accessibleFamilies.find((family) => String(family.id) === String(familyId)) || null
            );
        } catch (err) {
            console.error('Error fetching family context:', err);
        }
    };

    useEffect(() => {
        fetchFamilyContext();
    }, [familyId]);

    useEffect(() => {
        setActiveTab(getTabFromLocation());
    }, [familyId, location.search]);

    const fetchUnreadCount = async () => {
        try {
            const count = await getUnreadNotificationCount();
            setUnreadCount(Number(count) || 0);
        } catch (err) {
            console.error('Error fetching unread notification count:', err);
        }
    };

    const fetchNotifications = async (filter = notificationFilter) => {
        setNotificationLoading(true);
        try {
            const params = filter === 'unread'
                ? { unreadOnly: true }
                : filter === 'family'
                    ? { familyId }
                    : {};
            const data = await getNotifications(params);
            setNotifications(data || []);
        } catch (err) {
            console.error('Error fetching notifications:', err);
        } finally {
            setNotificationLoading(false);
        }
    };

    useEffect(() => {
        fetchNotifications(notificationFilter);
        fetchUnreadCount();
        const interval = setInterval(() => {
            fetchNotifications(notificationFilter);
            fetchUnreadCount();
        }, 30000); // Poll every 30 seconds
        return () => clearInterval(interval);
    }, [familyId, notificationFilter]);

    // Close notification dropdown on outside click
    useEffect(() => {
        const handleClickOutside = (e) => {
            if (notifDropdownRef.current && !notifDropdownRef.current.contains(e.target)) {
                setIsNotifOpen(false);
            }
        };
        if (isNotifOpen) {
            document.addEventListener('mousedown', handleClickOutside);
        }
        return () => {
            document.removeEventListener('mousedown', handleClickOutside);
        };
    }, [isNotifOpen]);

    useEffect(() => {
        const handleClickOutside = (e) => {
            if (familySwitcherRef.current && !familySwitcherRef.current.contains(e.target)) {
                setIsFamilySwitcherOpen(false);
            }
        };
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);

    const handleNotificationClick = async (notif) => {
        try {
            if (!notif.isRead) {
                await markNotificationAsRead(notif.id);
                setNotifications(prev => prev.map(n => n.id === notif.id ? { ...n, isRead: true } : n));
                setUnreadCount(prev => Math.max(0, prev - 1));
            }

            if (notif.actionUrl) {
                if (/^https?:\/\//i.test(notif.actionUrl)) {
                    window.location.href = notif.actionUrl;
                } else {
                    navigate(notif.actionUrl);
                }
                setIsNotifOpen(false);
            }
        } catch (err) {
            console.error('Error opening notification:', err);
        }
    };

    const handleMarkAllRead = async () => {
        try {
            const familyFilter = notificationFilter === 'family' ? familyId : undefined;
            await markAllNotificationsAsRead(familyFilter);
            setNotifications(prev => prev.map(n => ({ ...n, isRead: true })));
            await fetchUnreadCount();
        } catch (err) {
            console.error('Error marking all notifications as read:', err);
        }
    };

    const formatTimeAgo = (dateStr) => {
        if (!dateStr) return '';
        try {
            const date = new Date(dateStr);
            const now = new Date();
            const diffInSeconds = Math.floor((now - date) / 1000);
            if (diffInSeconds < 60) return 'Just now';
            const diffInMinutes = Math.floor(diffInSeconds / 60);
            if (diffInMinutes < 60) return `${diffInMinutes}m ago`;
            const diffInHours = Math.floor(diffInMinutes / 60);
            if (diffInHours < 24) return `${diffInHours}h ago`;
            const diffInDays = Math.floor(diffInHours / 24);
            if (diffInDays < 7) return `${diffInDays}d ago`;
            return date.toLocaleDateString();
        } catch {
            return '';
        }
    };

    const getNotificationIcon = (type) => {
        const iconProps = { size: 17 };
        if (type === 'DOCUMENT_ADDED' || type === 'DOCUMENT_EXPIRING') {
            return <Folder {...iconProps} />;
        }
        if (type === 'MEMORY_ADDED') {
            return <Image {...iconProps} />;
        }
        if (type?.startsWith('FAMILY_')) {
            return <Users {...iconProps} />;
        }
        return <Bell {...iconProps} />;
    };

    const fetchMembers = async () => {
        setLoading(true);
        try {
            const data = await getFamilyMembers(familyId);
            setMembers(data || []);
            setError(null);
        } catch (err) {
            console.error('Error fetching members:', err);
            setError('Failed to load family members.');
        } finally {
            setLoading(false);
        }
    };

    const fetchInvitations = async () => {
        try {
            const data = await getPendingInvitations(familyId);
            setPendingInvitations(data || []);
        } catch (err) {
            console.error('Error fetching pending invitations:', err);
        }
    };

    useEffect(() => {
        fetchMembers();
    }, [familyId]);

    // Close dropdown on outside click or Escape key
    useEffect(() => {
        const handleGlobalClick = () => {
            setActiveMemberMenuId(null);
        };
        const handleKeyDown = (e) => {
            if (e.key === 'Escape') {
                setActiveMemberMenuId(null);
                if (!savingRole) setModifyingMember(null);
                if (!removingLoading) setRemovingMember(null);
            }
        };
        window.addEventListener('click', handleGlobalClick);
        window.addEventListener('keydown', handleKeyDown);
        return () => {
            window.removeEventListener('click', handleGlobalClick);
            window.removeEventListener('keydown', handleKeyDown);
        };
    }, [savingRole, removingLoading]);

    const handleAddMember = async (e) => {
        e.preventDefault();
        if (!email.trim()) return;
        
        setIsSubmitting(true);
        setAddError(null);
        try {
            await createFamilyInvitation(familyId, { email, role });
            setEmail('');
            setRole('MEMBER');
            await fetchInvitations();
            await fetchMembers();
            setToastMessage('Invitation sent by email successfully!');
            setTimeout(() => setToastMessage(null), 3500);
        } catch (err) {
            console.error('Error adding member:', err);
            setAddError(err.response?.data?.message || err.message || 'Failed to add member.');
        } finally {
            setIsSubmitting(false);
        }
    };

    // Open Modify Modal
    const handleOpenModify = (member) => {
        setActiveMemberMenuId(null);
        setModifyingMember(member);
        setSelectedRole(member.role === 'OWNER' ? 'ADMIN' : member.role);
        setModifyError(null);
    };

    // Save Modified Role
    const handleSaveModifyRole = async (e) => {
        e.preventDefault();
        if (!modifyingMember) return;
        const mId = modifyingMember.memberId || modifyingMember.id;
        if (!mId) return;

        if (modifyingMember.role === selectedRole) {
            setModifyingMember(null);
            return;
        }

        setSavingRole(true);
        setModifyError(null);
        try {
            await updateFamilyMemberRole(familyId, mId, selectedRole, modifyingMember.email);
            setToastMessage(`Updated ${modifyingMember.name}'s role to ${selectedRole}!`);
            setModifyingMember(null);
            await fetchMembers();
            setTimeout(() => setToastMessage(null), 3500);
        } catch (err) {
            console.error('Error updating member role:', err);
            setModifyError(err.response?.data?.message || err.message || 'Failed to update member role.');
        } finally {
            setSavingRole(false);
        }
    };

    // Open Remove Confirmation Modal
    const handleOpenRemove = (member) => {
        setActiveMemberMenuId(null);
        setRemovingMember(member);
        setRemoveError(null);
    };

    // Confirm Remove Member
    const handleConfirmRemove = async () => {
        if (!removingMember) return;
        const mId = removingMember.memberId || removingMember.id;
        if (!mId) return;

        setRemovingLoading(true);
        setRemoveError(null);
        try {
            await removeFamilyMember(familyId, mId);
            setToastMessage(`Removed ${removingMember.name} from family vault.`);
            setRemovingMember(null);
            await fetchMembers();
            setTimeout(() => setToastMessage(null), 3500);
        } catch (err) {
            console.error('Error removing member:', err);
            setRemoveError(err.response?.data?.message || err.message || 'Failed to remove member.');
        } finally {
            setRemovingLoading(false);
        }
    };

    const getInitials = (name) => {
        if (!name) return '??';
        const parts = name.trim().split(' ');
        if (parts.length > 1) {
            return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
        }
        return name.substring(0, 2).toUpperCase();
    };

    const loggedInEmail = signedInUser?.email?.trim().toLowerCase() || null;
    const currentLoggedInMember = members.find(
        (m) => m.email && loggedInEmail && m.email.trim().toLowerCase() === loggedInEmail
    );
    const currentUserRole = currentLoggedInMember ? currentLoggedInMember.role : 'MEMBER';
    const currentUserId = currentLoggedInMember ? (currentLoggedInMember.userId || currentLoggedInMember.id) : null;
    const isOwnerOrAdmin = currentUserRole === 'OWNER' || currentUserRole === 'ADMIN';

    useEffect(() => {
        if (isOwnerOrAdmin) {
            fetchInvitations();
        } else {
            setPendingInvitations([]);
        }
    }, [familyId, isOwnerOrAdmin]);

    const handleResendInvitation = async (invitation) => {
        try {
            await resendFamilyInvitation(familyId, invitation.id);
            await fetchInvitations();
            setToastMessage(`Invitation resent to ${invitation.email}.`);
            setTimeout(() => setToastMessage(null), 3500);
        } catch (err) {
            setAddError(err.response?.data?.message || err.message || 'Failed to resend invitation.');
        }
    };

    const handleRevokeInvitation = async (invitation) => {
        try {
            await revokeFamilyInvitation(familyId, invitation.id);
            await fetchInvitations();
            setToastMessage(`Invitation to ${invitation.email} revoked.`);
            setTimeout(() => setToastMessage(null), 3500);
        } catch (err) {
            setAddError(err.response?.data?.message || err.message || 'Failed to revoke invitation.');
        }
    };

    const handleFamilySwitch = (nextFamilyId) => {
        setIsFamilySwitcherOpen(false);
        if (String(nextFamilyId) !== String(familyId)) {
            navigate(`/family/${nextFamilyId}`);
        }
    };

    return (
        <div className="family-view-container">
            <nav className="fv-nav">
                <div className="fv-brand" onClick={() => navigate('/')}>
                    <Shield size={24} color="#4F46E5" fill="#EEF2FF" />
                    <span>FamilyVault</span>
                </div>

                <div className="fv-nav-actions">
                    <div className="fv-family-switcher" ref={familySwitcherRef}>
                        <button
                            type="button"
                            className={`fv-family-switcher-btn ${isFamilySwitcherOpen ? 'active' : ''}`}
                            onClick={() => setIsFamilySwitcherOpen(prev => !prev)}
                            aria-expanded={isFamilySwitcherOpen}
                            aria-label="Switch family"
                        >
                            <span className="fv-family-switcher-icon"><Users size={16} /></span>
                            <span className="fv-family-switcher-copy">
                                <span className="fv-family-switcher-label">Current family</span>
                                <strong>{currentFamily?.name || 'Loading family...'}</strong>
                            </span>
                            <ChevronDown size={15} className={isFamilySwitcherOpen ? 'open' : ''} />
                        </button>

                        {isFamilySwitcherOpen && (
                            <div className="fv-family-switcher-menu">
                                <div className="fv-family-switcher-menu-title">Your families</div>
                                {families.length === 0 ? (
                                    <div className="fv-family-switcher-empty">No families found</div>
                                ) : (
                                    families.map(family => (
                                        <button
                                            type="button"
                                            key={family.id}
                                            className={`fv-family-option ${String(family.id) === String(familyId) ? 'selected' : ''}`}
                                            onClick={() => handleFamilySwitch(family.id)}
                                        >
                                            <span className="fv-family-option-icon"><Users size={15} /></span>
                                            <span>{family.name}</span>
                                            {String(family.id) === String(familyId) && <Check size={15} />}
                                        </button>
                                    ))
                                )}
                                <button
                                    type="button"
                                    className="fv-family-view-all"
                                    onClick={() => {
                                        setIsFamilySwitcherOpen(false);
                                        navigate('/');
                                    }}
                                >
                                    View all families
                                </button>
                            </div>
                        )}
                    </div>

                    {/* Notification Bell Dropdown */}
                    <div className="notification-bell-container" ref={notifDropdownRef}>
                        <button 
                            type="button" 
                            className={`fv-bell-btn ${isNotifOpen ? 'active' : ''}`}
                            onClick={() => setIsNotifOpen(prev => !prev)}
                            aria-label="Notifications"
                            title="Notifications"
                        >
                            <Bell size={20} />
                            {unreadCount > 0 && (
                                <span className="fv-bell-badge">
                                    {unreadCount > 9 ? '9+' : unreadCount}
                                </span>
                            )}
                        </button>

                        {isNotifOpen && (
                            <div className="fv-notif-dropdown">
                                <div className="fv-notif-header">
                                    <div className="fv-notif-title">
                                        <Bell size={16} />
                                        <span>Notifications</span>
                                        {unreadCount > 0 && (
                                            <span className="fv-notif-count-pill">{unreadCount} new</span>
                                        )}
                                    </div>
                                    {unreadCount > 0 && (
                                        <button 
                                            type="button" 
                                            className="fv-notif-mark-all"
                                            onClick={handleMarkAllRead}
                                        >
                                            <CheckCheck size={14} /> Mark all read
                                        </button>
                                    )}
                                </div>

                                <div className="fv-notif-filters">
                                    <button
                                        type="button"
                                        className={notificationFilter === 'all' ? 'active' : ''}
                                        onClick={() => setNotificationFilter('all')}
                                    >
                                        All
                                    </button>
                                    <button
                                        type="button"
                                        className={notificationFilter === 'unread' ? 'active' : ''}
                                        onClick={() => setNotificationFilter('unread')}
                                    >
                                        Unread
                                    </button>
                                    <button
                                        type="button"
                                        className={notificationFilter === 'family' ? 'active' : ''}
                                        onClick={() => setNotificationFilter('family')}
                                        title={currentFamily?.name || 'Current family'}
                                    >
                                        Current family
                                    </button>
                                </div>

                                <div className="fv-notif-list">
                                    {notificationLoading ? (
                                        <div className="fv-notif-empty">
                                            <p>Loading notifications...</p>
                                        </div>
                                    ) : notifications.length === 0 ? (
                                        <div className="fv-notif-empty">
                                            <BellOff size={28} />
                                            <p>{notificationFilter === 'unread' ? 'No unread notifications' : 'No notifications yet'}</p>
                                            <span>
                                                {notificationFilter === 'family'
                                                    ? `No activity in ${currentFamily?.name || 'this family'} yet.`
                                                    : 'Family activity, invitations, and reminders will appear here.'}
                                            </span>
                                        </div>
                                    ) : (
                                        notifications.map(notif => (
                                            <div 
                                                key={notif.id} 
                                                className={`fv-notif-item ${!notif.isRead ? 'unread' : ''}`}
                                                onClick={() => handleNotificationClick(notif)}
                                            >
                                                {!notif.isRead && <span className="fv-notif-dot" />}
                                                <span className={`fv-notif-icon type-${(notif.type || 'SYSTEM').toLowerCase()}`}>
                                                    {getNotificationIcon(notif.type)}
                                                </span>
                                                <div className="fv-notif-content">
                                                    <div className="fv-notif-line">
                                                        <p className="fv-notif-title-text">{notif.title || 'FamilyVault notification'}</p>
                                                        <span className="fv-notif-date">{formatTimeAgo(notif.createdAt)}</span>
                                                    </div>
                                                    <p className="fv-notif-msg">{notif.message}</p>
                                                    <span className="fv-notif-context">
                                                        {notif.familyName || 'FamilyVault'}
                                                        {notif.actorName ? ` · ${notif.actorName}` : ''}
                                                    </span>
                                                </div>
                                            </div>
                                        ))
                                    )}
                                </div>
                            </div>
                        )}
                    </div>

                    <UserNavProfile />
                </div>
            </nav>

            <div className="family-view-body">
                {/* Sidebar */}
                <aside className="sidebar">
                    <div className="fv-tab-list">
                        <div className={`fv-tab ${activeTab === 'members' ? 'active' : ''}`} onClick={() => setActiveTab('members')}>
                            <Users size={18} /> Members
                        </div>
                        <div className={`fv-tab ${activeTab === 'documents' ? 'active' : ''}`} onClick={() => setActiveTab('documents')}>
                            <Folder size={18} /> Documents
                        </div>
                        <div className={`fv-tab ${activeTab === 'memories' ? 'active' : ''}`} onClick={() => setActiveTab('memories')}>
                            <Image size={18} /> Memories
                        </div>
                        <div className={`fv-tab ${activeTab === 'settings' ? 'active' : ''}`} onClick={() => setActiveTab('settings')}>
                            <Settings size={18} /> Settings
                        </div>
                    </div>

                    <div className="fv-sidebar-card">
                        {/* Top Pill & Interactive Heart Button */}
                        <div className="fv-sidebar-card-top">
                            <span className="fv-sidebar-pill">
                                <Sparkles size={11} className="fv-pill-sparkle" /> Family Space
                            </span>
                            <button 
                                type="button"
                                className={`fv-sidebar-heart-btn ${heartLiked ? 'liked' : ''}`}
                                onClick={handleToggleHeart}
                                title={heartLiked ? 'You sent love to your family!' : 'Send love to your family'}
                                aria-label="Send love"
                            >
                                <Heart 
                                    size={15} 
                                    fill={heartLiked ? '#F43F5E' : 'none'} 
                                    color={heartLiked ? '#F43F5E' : '#7C3AED'} 
                                    className={heartLiked ? 'pulse-heart' : ''}
                                />
                                {showHeartToast && (
                                    <span className="fv-heart-tooltip">❤️ Love Sent!</span>
                                )}
                            </button>
                        </div>

                        {/* Main Title & Icon */}
                        <div className="fv-sidebar-card-main">
                            <div className="fv-sidebar-card-icon">
                                <Users size={19} />
                            </div>
                            <div className="fv-sidebar-card-text">
                                <h4>{currentFamily?.name || 'Family Vault'}</h4>
                                <p>Keep everyone connected</p>
                            </div>
                        </div>

                        {/* Member Stack & Active Status */}
                        <div className="fv-sidebar-card-footer">
                            <div className="fv-sidebar-avatar-stack">
                                {members.slice(0, 3).map((m, idx) => {
                                    const initial = (m.name?.[0] || m.email?.[0] || 'M').toUpperCase();
                                    return (
                                        <div 
                                            key={m.id || idx} 
                                            className={`fv-mini-avatar avatar-bg-${idx % 3}`} 
                                            title={m.name || m.email}
                                        >
                                            {initial}
                                        </div>
                                    );
                                })}
                                {members.length > 3 && (
                                    <div className="fv-mini-avatar more">
                                        +{members.length - 3}
                                    </div>
                                )}
                            </div>
                            <span className="fv-sidebar-stat">
                                <span className="fv-pulse-dot" />
                                {members.length} {members.length === 1 ? 'member' : 'members'}
                            </span>
                        </div>
                    </div>
                </aside>

                {/* Main Content */}
                <main className="main-content">
                    {activeTab === 'members' && (
                        <div className="handwritten-text">
                            Memories<br/>Today<br/>Tomorrow<br/>Always<br/>Together <br/>♡
                        </div>
                    )}

                    <div className="content-wrapper">
                        {activeTab === 'members' && (
                            <>
                                <div className="fv-header">
                                    <div className="fv-breadcrumb">
                                        <button type="button" onClick={() => navigate('/')}>Your Families</button>
                                        <span>/</span>
                                        <span>{currentFamily?.name || 'Family Vault'}</span>
                                    </div>
                                    <div className="fv-header-title-row">
                                        <h1>{currentFamily?.name || 'Family Vault'}</h1>
                                        <span className="fv-current-role">{currentUserRole}</span>
                                    </div>
                                    <p>
                                        Family Vault
                                        <span className="fv-header-separator">·</span>
                                        {loading ? 'Loading members...' : `${members.length} ${members.length === 1 ? 'member' : 'members'}`}
                                        <span className="fv-header-separator">·</span>
                                        Manage documents and memories for this family.
                                    </p>
                                </div>

                                {error && <div style={{ color: '#E11D48', marginBottom: '1rem' }}>{error}</div>}

                                {/* Add Member Card - Only visible to OWNER or ADMIN */}
                                {isOwnerOrAdmin && (
                                    <div className="card">
                                        <div className="fv-card-header">
                                            <div className="fv-card-icon">
                                                <UserPlus size={22} />
                                            </div>
                                            <div className="fv-card-title">
                                                <h2>Invite a Member</h2>
                                                <p>Add your family members to share documents, photos and memories.</p>
                                            </div>
                                        </div>

                                        <form className="fv-add-form" onSubmit={handleAddMember}>
                                            <div className="fv-input-wrapper">
                                                <Mail className="fv-input-icon" size={18} />
                                                <input 
                                                    type="email" 
                                                    className="fv-input"
                                                    placeholder="Enter member's email address..." 
                                                    value={email}
                                                    onChange={(e) => setEmail(e.target.value)}
                                                    disabled={isSubmitting}
                                                    required
                                                />
                                            </div>
                                            
                                            <div className="fv-select-wrapper">
                                                <User className="fv-select-icon" size={18} />
                                                <select className="fv-select" value={role} onChange={(e) => setRole(e.target.value)} disabled={isSubmitting}>
                                                    <option value="MEMBER">Member</option>
                                                    <option value="ADMIN">Admin</option>
                                                </select>
                                                <ChevronDown className="fv-select-chevron" size={16} />
                                            </div>

                                            <button type="submit" className="fv-btn-primary" disabled={!email.trim() || isSubmitting}>
                                                <UserPlus size={18} /> {isSubmitting ? 'Sending...' : 'Invite'}
                                            </button>
                                        </form>
                                {addError && <div style={{ color: '#E11D48', marginTop: '0.75rem', fontSize: '0.9rem' }}>{addError}</div>}
                                    </div>
                                )}

                                {isOwnerOrAdmin && pendingInvitations.length > 0 && (
                                    <div className="card fv-pending-card">
                                        <div className="fv-card-header">
                                            <div className="fv-card-icon fv-pending-icon">
                                                <Mail size={22} />
                                            </div>
                                            <div className="fv-card-title">
                                                <h2>Pending Invitations</h2>
                                                <p>These people will join the vault after accepting their email invitation.</p>
                                            </div>
                                        </div>
                                        <div className="fv-table-wrap">
                                            <table className="fv-table">
                                                <thead>
                                                    <tr>
                                                        <th>Email</th>
                                                        <th>Role</th>
                                                        <th>Expires</th>
                                                        <th style={{ textAlign: 'right', paddingRight: '1.25rem' }}>Actions</th>
                                                    </tr>
                                                </thead>
                                                <tbody>
                                                    {pendingInvitations.map(invitation => (
                                                        <tr key={invitation.id}>
                                                            <td>
                                                                <div className="member-info-cell">
                                                                    <div className="member-avatar pending-avatar">
                                                                        <Mail size={15} />
                                                                    </div>
                                                                    <div className="member-details">
                                                                        <span className="member-name">{invitation.email}</span>
                                                                        <span className="member-email">Invitation sent</span>
                                                                    </div>
                                                                </div>
                                                            </td>
                                                            <td><span className={`role-badge ${invitation.role?.toLowerCase()}`}>{invitation.role}</span></td>
                                                            <td style={{ color: '#64748B', fontSize: '0.9rem' }}>
                                                                {invitation.expiresAt ? new Date(invitation.expiresAt).toLocaleDateString() : '—'}
                                                            </td>
                                                            <td style={{ textAlign: 'right', paddingRight: '1rem' }}>
                                                                <div className="fv-pending-actions">
                                                                    <button type="button" className="fv-text-action" onClick={() => handleResendInvitation(invitation)}>Resend</button>
                                                                    <button type="button" className="fv-text-action danger" onClick={() => handleRevokeInvitation(invitation)}>Revoke</button>
                                                                </div>
                                                            </td>
                                                        </tr>
                                                    ))}
                                                </tbody>
                                            </table>
                                        </div>
                                    </div>
                                )}

                                {/* Members List Card */}
                                <div className="card">
                                    <div className="fv-card-header">
                                        <div className="fv-card-icon">
                                            <Users size={22} />
                                        </div>
                                        <div className="fv-card-title">
                                            <h2>Current Members</h2>
                                            <p>People in this family vault and their roles.</p>
                                        </div>
                                    </div>
                                    
                                    {loading ? (
                                        <p style={{color: '#64748B'}}>Loading members...</p>
                                    ) : members.length === 0 ? (
                                        <p style={{color: '#64748B'}}>No members found.</p>
                                    ) : (
                                        <div className="fv-table-wrap">
                                            <table className="fv-table">
                                                <thead>
                                                    <tr>
                                                        <th>Member</th>
                                                        <th>Role</th>
                                                        <th>Joined At</th>
                                                        {isOwnerOrAdmin && (
                                                            <th style={{ textAlign: 'right', paddingRight: '1.25rem' }}>Actions</th>
                                                        )}
                                                    </tr>
                                                </thead>
                                                <tbody>
                                                    {members.map(member => {
                                                        const mId = member.memberId || member.id;
                                                        const isMenuOpen = activeMemberMenuId === mId;
                                                        const canModify = currentUserRole === 'OWNER' || (currentUserRole === 'ADMIN' && member.role === 'MEMBER');
                                                        const canRemove = member.role !== 'OWNER' && (currentUserRole === 'OWNER' || (currentUserRole === 'ADMIN' && member.role !== 'ADMIN'));
                                                        const canManageMember = canModify || canRemove;

                                                        return (
                                                            <tr key={mId}>
                                                                <td>
                                                                    <div className="member-info-cell">
                                                                        <div className="member-avatar">
                                                                            {getInitials(member.name)}
                                                                        </div>
                                                                        <div className="member-details">
                                                                            <span className="member-name">{member.name}</span>
                                                                            <span className="member-email">{member.email}</span>
                                                                        </div>
                                                                    </div>
                                                                </td>
                                                                <td>
                                                                    <span className={`role-badge ${member.role?.toLowerCase()}`}>
                                                                        {member.role}
                                                                    </span>
                                                                </td>
                                                                <td style={{color: '#64748B', fontSize: '0.9rem'}}>
                                                                    {member.joinedAt ? new Date(member.joinedAt).toLocaleDateString() : 'Recent'}
                                                                </td>
                                                                {isOwnerOrAdmin && (
                                                                    <td style={{ textAlign: 'right', paddingRight: '1rem' }}>
                                                                        {canManageMember ? (
                                                                            <div className="fv-action-menu-wrapper">
                                                                                <button 
                                                                                    type="button"
                                                                                    className={`action-btn ${isMenuOpen ? 'active' : ''}`}
                                                                                    onClick={(e) => {
                                                                                        e.stopPropagation();
                                                                                        setActiveMemberMenuId(isMenuOpen ? null : mId);
                                                                                    }}
                                                                                    title="Member Options"
                                                                                >
                                                                                    <MoreVertical size={16} />
                                                                                </button>

                                                                                {isMenuOpen && (
                                                                                    <div className="fv-dropdown-menu" onClick={(e) => e.stopPropagation()}>
                                                                                        {canModify && (
                                                                                            <button 
                                                                                                type="button" 
                                                                                                className="fv-dropdown-item" 
                                                                                                onClick={() => handleOpenModify(member)}
                                                                                            >
                                                                                                <div className="fv-dropdown-icon-wrap modify">
                                                                                                    <Pencil size={15} />
                                                                                                </div>
                                                                                                <div className="fv-dropdown-text">
                                                                                                    <span className="fv-dropdown-title">Modify Member Role</span>
                                                                                                    <span className="fv-dropdown-desc">Change between Member & Admin</span>
                                                                                                </div>
                                                                                            </button>
                                                                                        )}

                                                                                        {canModify && canRemove && (
                                                                                            <div className="fv-dropdown-divider" />
                                                                                        )}

                                                                                        {canRemove && (
                                                                                            <button 
                                                                                                type="button" 
                                                                                                className="fv-dropdown-item fv-item-delete" 
                                                                                                onClick={() => handleOpenRemove(member)}
                                                                                            >
                                                                                                <div className="fv-dropdown-icon-wrap delete">
                                                                                                    <Trash2 size={15} />
                                                                                                </div>
                                                                                                <div className="fv-dropdown-text">
                                                                                                    <span className="fv-dropdown-title">Remove Member</span>
                                                                                                    <span className="fv-dropdown-desc">Revoke vault access</span>
                                                                                                </div>
                                                                                            </button>
                                                                                        )}
                                                                                    </div>
                                                                                )}
                                                                            </div>
                                                                        ) : (
                                                                            <span style={{ color: '#94A3B8', fontSize: '0.8rem', fontStyle: 'italic', paddingRight: '0.5rem' }}>
                                                                                {member.role === 'OWNER' ? 'Owner' : 'Admin'}
                                                                            </span>
                                                                        )}
                                                                    </td>
                                                                )}
                                                            </tr>
                                                        );
                                                    })}
                                                </tbody>
                                            </table>
                                        </div>
                                    )}
                                </div>
                            </>
                        )}
                        {activeTab === 'memories' && (
                            <MemoriesTab 
                                familyId={familyId} 
                                currentUserRole={currentUserRole} 
                                currentUserId={currentUserId} 
                            />
                        )}
                        {activeTab === 'documents' && (
                            <DocumentsTab 
                                familyId={familyId} 
                                members={members} 
                                currentUserRole={currentUserRole} 
                                currentUserId={currentUserId} 
                            />
                        )}
                        {activeTab === 'settings' && (
                            <SettingsTab 
                                familyId={familyId} 
                                members={members} 
                                currentUserRole={currentUserRole} 
                                currentUserId={currentUserId} 
                            />
                        )}
                    </div>
                </main>
            </div>

            {/* Modify Member Role Modal */}
            {modifyingMember && (
                <div className="fv-modal-overlay" onClick={() => !savingRole && setModifyingMember(null)}>
                    <div className="fv-modal-card" onClick={(e) => e.stopPropagation()}>
                        <button 
                            type="button" 
                            className="fv-modal-close-btn" 
                            onClick={() => setModifyingMember(null)}
                            disabled={savingRole}
                            title="Close"
                        >
                            <X size={18} />
                        </button>

                        <div className="fv-modal-header">
                            <div className="fv-modal-header-icon">
                                <ShieldCheck size={22} color="#5A45FF" />
                            </div>
                            <div>
                                <h3>Modify Member Role</h3>
                                <p>Update vault permissions and access level for this member.</p>
                            </div>
                        </div>

                        {/* Member Identity Preview */}
                        <div className="fv-modal-member-preview">
                            <div className="fv-modal-avatar">
                                {getInitials(modifyingMember.name)}
                            </div>
                            <div className="fv-modal-member-info">
                                <div className="fv-modal-member-name-row">
                                    <span className="fv-modal-name">{modifyingMember.name}</span>
                                    <span className={`role-badge ${modifyingMember.role?.toLowerCase()}`}>
                                        Current: {modifyingMember.role}
                                    </span>
                                </div>
                                <span className="fv-modal-email">{modifyingMember.email}</span>
                            </div>
                        </div>

                        {modifyingMember.role === 'OWNER' ? (
                            <div className="fv-owner-notice">
                                <Info size={20} color="#4F46E5" />
                                <div>
                                    <strong>Vault Owner Account</strong>
                                    <p>This user is the creator and owner of this family vault. Ownership cannot be changed or demoted.</p>
                                </div>
                            </div>
                        ) : (
                            <form onSubmit={handleSaveModifyRole}>
                                <div className="fv-role-selection-section">
                                    <label className="fv-role-selection-label">Select New Role</label>
                                    <div className="fv-role-cards-grid">
                                        {/* Member Card */}
                                        <div 
                                            className={`fv-role-card ${selectedRole === 'MEMBER' ? 'selected' : ''}`}
                                            onClick={() => setSelectedRole('MEMBER')}
                                        >
                                            <div className="role-card-radio">
                                                <div className="radio-inner" />
                                            </div>
                                            <div className="role-card-content">
                                                <div className="role-card-header">
                                                    <div className="role-card-icon user-icon">
                                                        <Users size={18} />
                                                    </div>
                                                    <div className="role-card-titles">
                                                        <span className="role-title">Member</span>
                                                        <span className="role-badge-tag standard">Standard</span>
                                                    </div>
                                                </div>
                                                <p className="role-description">
                                                    Can view family members, upload & view memories, and browse shared family documents.
                                                </p>
                                            </div>
                                        </div>

                                        {/* Admin Card */}
                                        <div 
                                            className={`fv-role-card ${selectedRole === 'ADMIN' ? 'selected' : ''}`}
                                            onClick={() => setSelectedRole('ADMIN')}
                                        >
                                            <div className="role-card-radio">
                                                <div className="radio-inner" />
                                            </div>
                                            <div className="role-card-content">
                                                <div className="role-card-header">
                                                    <div className="role-card-icon admin-icon">
                                                        <Shield size={18} />
                                                    </div>
                                                    <div className="role-card-titles">
                                                        <span className="role-title">Admin</span>
                                                        <span className="role-badge-tag elevated">Full Access</span>
                                                    </div>
                                                </div>
                                                <p className="role-description">
                                                    Can invite new members, modify permissions, edit/delete memories, and manage vault content.
                                                </p>
                                            </div>
                                        </div>
                                    </div>
                                </div>

                                {modifyError && (
                                    <div className="fv-modal-error">
                                        <AlertCircle size={16} />
                                        <span>{modifyError}</span>
                                    </div>
                                )}

                                <div className="fv-modal-actions">
                                    <button 
                                        type="button" 
                                        className="fv-modal-btn-cancel" 
                                        onClick={() => setModifyingMember(null)}
                                        disabled={savingRole}
                                    >
                                        Cancel
                                    </button>
                                    <button 
                                        type="submit" 
                                        className="fv-modal-btn-save" 
                                        disabled={savingRole || selectedRole === modifyingMember.role}
                                    >
                                        <Check size={18} />
                                        {savingRole ? 'Saving...' : (selectedRole === modifyingMember.role ? 'No Changes' : 'Save Changes')}
                                    </button>
                                </div>
                            </form>
                        )}

                        {modifyingMember.role === 'OWNER' && (
                            <div className="fv-modal-actions" style={{ marginTop: '1.5rem' }}>
                                <button 
                                    type="button" 
                                    className="fv-modal-btn-cancel" 
                                    onClick={() => setModifyingMember(null)}
                                >
                                    Close
                                </button>
                            </div>
                        )}
                    </div>
                </div>
            )}

            {/* Remove Member Confirmation Modal */}
            {removingMember && (
                <div className="fv-modal-overlay" onClick={() => !removingLoading && setRemovingMember(null)}>
                    <div className="fv-modal-card remove-modal-card" onClick={(e) => e.stopPropagation()}>
                        <button 
                            type="button" 
                            className="fv-modal-close-btn" 
                            onClick={() => setRemovingMember(null)}
                            disabled={removingLoading}
                            title="Close"
                        >
                            <X size={18} />
                        </button>

                        <div className="fv-modal-header">
                            <div className="fv-modal-header-icon remove-icon-box">
                                <Trash2 size={22} color="#EF4444" />
                            </div>
                            <div>
                                <h3>Remove Member</h3>
                                <p>Are you sure you want to remove this member from the vault?</p>
                            </div>
                        </div>

                        <div className="fv-modal-member-preview remove-preview">
                            <div className="fv-modal-avatar">
                                {getInitials(removingMember.name)}
                            </div>
                            <div className="fv-modal-member-info">
                                <span className="fv-modal-name">{removingMember.name}</span>
                                <span className="fv-modal-email">{removingMember.email}</span>
                            </div>
                        </div>

                        <p className="fv-remove-warning-text">
                            This action will revoke their access to all shared family memories, documents, and vault updates. You can invite them back anytime with their email.
                        </p>

                        {removeError && (
                            <div className="fv-modal-error">
                                <AlertCircle size={16} />
                                <span>{removeError}</span>
                            </div>
                        )}

                        <div className="fv-modal-actions">
                            <button 
                                type="button" 
                                className="fv-modal-btn-cancel" 
                                onClick={() => setRemovingMember(null)}
                                disabled={removingLoading}
                            >
                                Cancel
                            </button>
                            <button 
                                type="button" 
                                className="fv-modal-btn-danger" 
                                onClick={handleConfirmRemove}
                                disabled={removingLoading}
                            >
                                <Trash2 size={16} />
                                {removingLoading ? 'Removing...' : 'Yes, Remove Member'}
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* Floating Toast Notification */}
            {toastMessage && (
                <div className="fv-floating-toast">
                    <CheckCircle2 size={18} color="#10B981" />
                    <span>{toastMessage}</span>
                </div>
            )}

            <ChatbotWidget
                familyName={currentFamily?.name}
            />
        </div>
    );
};

export default FamilyView;
