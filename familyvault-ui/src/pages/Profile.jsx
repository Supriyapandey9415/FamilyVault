import React, { useState, useEffect, useContext } from 'react';
import { useNavigate } from 'react-router-dom';
import { AuthContext } from '../context/AuthContext';
import { updateProfile } from '../services/authService';
import { getFamilies } from '../services/familyService';
import { 
    Shield, ArrowLeft, User, Mail, Calendar, Lock, CheckCircle2, 
    Save, AlertCircle, Sparkles, Folder, LogOut, ChevronRight 
} from 'lucide-react';
import './Profile.css';

const Profile = () => {
    const { user, refreshProfile, logout } = useContext(AuthContext);
    const navigate = useNavigate();

    const [firstName, setFirstName] = useState('');
    const [lastName, setLastName] = useState('');
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState(null);
    const [toastMessage, setToastMessage] = useState(null);

    const [families, setFamilies] = useState([]);
    const [loadingFamilies, setLoadingFamilies] = useState(true);

    useEffect(() => {
        if (user) {
            setFirstName(user.firstName || '');
            setLastName(user.lastName || '');
        }
    }, [user]);

    useEffect(() => {
        const loadFamilies = async () => {
            setLoadingFamilies(true);
            try {
                const data = await getFamilies();
                setFamilies(data || []);
            } catch (e) {
                console.error('Failed to load families:', e);
            } finally {
                setLoadingFamilies(false);
            }
        };
        loadFamilies();
    }, []);

    const showToast = (msg) => {
        setToastMessage(msg);
        setTimeout(() => setToastMessage(null), 3500);
    };

    const handleSaveProfile = async (e) => {
        e.preventDefault();
        if (!firstName.trim() || !lastName.trim()) {
            setError('First name and last name are required.');
            return;
        }

        setSaving(true);
        setError(null);
        try {
            await updateProfile({ firstName: firstName.trim(), lastName: lastName.trim() });
            await refreshProfile();
            showToast('Profile updated successfully!');
        } catch (err) {
            console.error('Error updating profile:', err);
            setError(err.response?.data?.message || err.message || 'Failed to update profile.');
        } finally {
            setSaving(false);
        }
    };

    const getDisplayName = () => {
        if (!user) return 'User Account';
        if (user.firstName && user.lastName) return `${user.firstName} ${user.lastName}`;
        if (user.firstName) return user.firstName;
        if (user.email) return user.email.split('@')[0];
        return 'User Account';
    };

    const getInitials = () => {
        if (!user) return 'U';
        if (user.firstName && user.lastName) {
            return (user.firstName[0] + user.lastName[0]).toUpperCase();
        }
        if (user.firstName) return user.firstName.substring(0, 2).toUpperCase();
        if (user.email) return user.email.substring(0, 2).toUpperCase();
        return 'U';
    };

    return (
        <div className="profile-page-layout">
            {/* Top Navigation Bar */}
            <nav className="profile-nav">
                <div className="profile-brand" onClick={() => navigate('/')}>
                    <Shield size={24} color="#5A45FF" />
                    <span>FamilyVault</span>
                </div>
                <div className="profile-nav-actions">
                    <button 
                        type="button" 
                        className="btn-back-dash"
                        onClick={() => navigate('/')}
                    >
                        <ArrowLeft size={16} />
                        <span>Back to Dashboard</span>
                    </button>
                    <button 
                        type="button" 
                        className="btn-profile-logout"
                        onClick={() => {
                            logout();
                            navigate('/login');
                        }}
                    >
                        <LogOut size={16} />
                        <span>Logout</span>
                    </button>
                </div>
            </nav>

            {/* Profile Main Content */}
            <main className="profile-main-container">
                <div className="profile-content-wrap">
                    
                    {/* Header Hero Card */}
                    <div className="profile-hero-card">
                        <div className="hero-avatar-wrap">
                            <div className="hero-avatar-circle">
                                {getInitials()}
                            </div>
                            <div className="hero-badge-sparkle" title="Active Account">
                                <Sparkles size={14} color="#FFFFFF" />
                            </div>
                        </div>

                        <div className="hero-info-text">
                            <div className="hero-name-row">
                                <h1>{getDisplayName()}</h1>
                                <span className="hero-verified-tag">
                                    <CheckCircle2 size={13} /> Verified User
                                </span>
                            </div>
                            <span className="hero-email-text">{user?.email}</span>
                            
                            <div className="hero-meta-row">
                                <div className="hero-meta-item">
                                    <Calendar size={14} />
                                    <span>
                                        Joined {user?.createdAt ? new Date(user.createdAt).toLocaleDateString(undefined, { month: 'short', year: 'numeric' }) : 'Recently'}
                                    </span>
                                </div>
                                <div className="hero-meta-item">
                                    <Folder size={14} />
                                    <span>{families.length} {families.length === 1 ? 'Family Vault' : 'Family Vaults'}</span>
                                </div>
                            </div>
                        </div>
                    </div>

                    {/* Split Grid Section */}
                    <div className="profile-grid">
                        
                        {/* Left Card: Edit Personal Information */}
                        <div className="profile-card">
                            <div className="profile-card-header">
                                <div className="card-header-icon-box">
                                    <User size={20} color="#5A45FF" />
                                </div>
                                <div>
                                    <h3>Personal Information</h3>
                                    <p>Update your personal details and how your name appears to family members.</p>
                                </div>
                            </div>

                            <form className="profile-form" onSubmit={handleSaveProfile}>
                                <div className="form-row-dual">
                                    <div className="form-group">
                                        <label htmlFor="input-first-name">First Name</label>
                                        <input 
                                            id="input-first-name"
                                            type="text" 
                                            className="profile-input"
                                            placeholder="Your first name"
                                            value={firstName}
                                            onChange={(e) => setFirstName(e.target.value)}
                                            disabled={saving}
                                            required
                                        />
                                    </div>
                                    <div className="form-group">
                                        <label htmlFor="input-last-name">Last Name</label>
                                        <input 
                                            id="input-last-name"
                                            type="text" 
                                            className="profile-input"
                                            placeholder="Your last name"
                                            value={lastName}
                                            onChange={(e) => setLastName(e.target.value)}
                                            disabled={saving}
                                            required
                                        />
                                    </div>
                                </div>

                                <div className="form-group">
                                    <label htmlFor="input-email">Email Address</label>
                                    <div className="input-with-lock">
                                        <Mail size={18} className="input-mail-icon" />
                                        <input 
                                            id="input-email"
                                            type="email" 
                                            className="profile-input disabled-input"
                                            value={user?.email || ''}
                                            disabled
                                        />
                                        <Lock size={15} className="input-lock-icon" title="Email address cannot be changed directly." />
                                    </div>
                                    <span className="input-hint">
                                        Your email is your unique FamilyVault identity used for logins and vault invitations.
                                    </span>
                                </div>

                                {error && (
                                    <div className="profile-error-box">
                                        <AlertCircle size={16} />
                                        <span>{error}</span>
                                    </div>
                                )}

                                <div className="form-actions-row">
                                    <button 
                                        type="submit" 
                                        className="btn-save-profile"
                                        disabled={saving || (firstName === (user?.firstName || '') && lastName === (user?.lastName || ''))}
                                    >
                                        <Save size={16} />
                                        <span>{saving ? 'Saving Changes...' : 'Save Profile'}</span>
                                    </button>
                                </div>
                            </form>
                        </div>

                        {/* Right Card: Vaults & Security */}
                        <div className="profile-card">
                            <div className="profile-card-header">
                                <div className="card-header-icon-box">
                                    <Shield size={20} color="#5A45FF" />
                                </div>
                                <div>
                                    <h3>Vault Memberships</h3>
                                    <p>Family vaults you are currently enrolled in and have access to.</p>
                                </div>
                            </div>

                            <div className="vault-list-container">
                                {loadingFamilies ? (
                                    <p className="vault-loading-text">Loading your vaults...</p>
                                ) : families.length === 0 ? (
                                    <div className="vault-empty-box">
                                        <Shield size={32} color="#CBD5E1" />
                                        <p>You haven't joined or created any family vaults yet.</p>
                                    </div>
                                ) : (
                                    <div className="vault-items-stack">
                                        {families.map((fam) => (
                                            <div 
                                                key={fam.id} 
                                                className="vault-membership-row"
                                                onClick={() => navigate(`/family/${fam.id}`)}
                                                role="button"
                                                tabIndex={0}
                                            >
                                                <div className="vault-row-left">
                                                    <div className="vault-row-icon">
                                                        <Folder size={18} />
                                                    </div>
                                                    <div className="vault-row-info">
                                                        <span className="vault-row-name">{fam.name}</span>
                                                        <span className="vault-row-badge">Family Vault</span>
                                                    </div>
                                                </div>
                                                <div className="vault-row-arrow">
                                                    <ChevronRight size={18} />
                                                </div>
                                            </div>
                                        ))}
                                    </div>
                                )}
                            </div>

                            <div className="security-status-card">
                                <div className="security-status-header">
                                    <Lock size={16} color="#10B981" />
                                    <span>Account & Data Protection</span>
                                </div>
                                <p>Your documents, photos, and memories are secured with tokenized JWT authentication and private access control.</p>
                            </div>
                        </div>

                    </div>

                </div>
            </main>

            {/* Toast Feedback */}
            {toastMessage && (
                <div className="profile-toast">
                    <CheckCircle2 size={18} color="#10B981" />
                    <span>{toastMessage}</span>
                </div>
            )}
        </div>
    );
};

export default Profile;
