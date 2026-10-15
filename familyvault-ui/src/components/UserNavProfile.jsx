import React, { useState, useEffect, useContext, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { AuthContext } from '../context/AuthContext';
import { User, LogOut, ChevronDown, Shield, Mail, CheckCircle2 } from 'lucide-react';
import './UserNavProfile.css';

const UserNavProfile = () => {
    const { user, logout } = useContext(AuthContext);
    const navigate = useNavigate();
    const [isOpen, setIsOpen] = useState(false);
    const [logoutError, setLogoutError] = useState('');
    const menuRef = useRef(null);

    // Close on outside click
    useEffect(() => {
        const handleClickOutside = (e) => {
            if (menuRef.current && !menuRef.current.contains(e.target)) {
                setIsOpen(false);
            }
        };
        const handleKeyDown = (e) => {
            if (e.key === 'Escape') setIsOpen(false);
        };
        document.addEventListener('mousedown', handleClickOutside);
        window.addEventListener('keydown', handleKeyDown);
        return () => {
            document.removeEventListener('mousedown', handleClickOutside);
            window.removeEventListener('keydown', handleKeyDown);
        };
    }, []);

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

    const handleLogout = async () => {
        setLogoutError('');
        try {
            await logout();
            setIsOpen(false);
            navigate('/login');
        } catch {
            setLogoutError('Unable to sign out. Please try again.');
        }
    };

    return (
        <div className="nav-profile-wrapper" ref={menuRef}>
            {logoutError && <span role="alert">{logoutError}</span>}
            <button 
                type="button" 
                className={`nav-profile-chip ${isOpen ? 'active' : ''}`}
                onClick={() => setIsOpen(!isOpen)}
                aria-expanded={isOpen}
            >
                <div className="nav-profile-avatar">
                    {getInitials()}
                </div>
                <div className="nav-profile-info">
                    <span className="nav-profile-name">{getDisplayName()}</span>
                    <span className="nav-profile-email">{user?.email || 'Logged In'}</span>
                </div>
                <ChevronDown size={15} className={`nav-profile-chevron ${isOpen ? 'open' : ''}`} />
            </button>

            {isOpen && (
                <div className="nav-profile-dropdown" onClick={(e) => e.stopPropagation()}>
                    <div className="dropdown-user-card">
                        <div className="dropdown-avatar-lg">
                            {getInitials()}
                        </div>
                        <div className="dropdown-user-details">
                            <span className="dropdown-name">{getDisplayName()}</span>
                            <span className="dropdown-email">{user?.email}</span>
                            <span className="dropdown-status-badge">
                                <CheckCircle2 size={12} color="#10B981" /> Verified Member
                            </span>
                        </div>
                    </div>

                    <div className="dropdown-divider" />

                    <div className="dropdown-links">
                        <button 
                            type="button" 
                            className="dropdown-link-item"
                            onClick={() => {
                                setIsOpen(false);
                                navigate('/profile');
                            }}
                        >
                            <User size={16} color="#5A45FF" />
                            <span>My Profile</span>
                        </button>
                        <button 
                            type="button" 
                            className="dropdown-link-item"
                            onClick={() => {
                                setIsOpen(false);
                                navigate('/');
                            }}
                        >
                            <Shield size={16} color="#0284C7" />
                            <span>Family Vaults</span>
                        </button>
                    </div>

                    <div className="dropdown-divider" />

                    <button 
                        type="button" 
                        className="dropdown-logout-btn"
                        onClick={handleLogout}
                    >
                        <LogOut size={16} />
                        <span>Sign Out</span>
                    </button>
                </div>
            )}
        </div>
    );
};

export default UserNavProfile;
