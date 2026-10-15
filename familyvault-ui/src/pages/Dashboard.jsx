import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { getFamilies, createFamily } from '../services/familyService';
import { Shield, Plus, Users, ChevronRight, Lock, Cloud } from 'lucide-react';
import UserNavProfile from '../components/UserNavProfile';
import './Dashboard.css';

const Dashboard = () => {
    const navigate = useNavigate();
    const [families, setFamilies] = useState([]);
    const [newFamilyName, setNewFamilyName] = useState('');
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null); // General page errors
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [errorMessage, setErrorMessage] = useState(null); // Form specific error

    const fetchFamilies = async () => {
        setLoading(true);
        try {
            const data = await getFamilies();
            setFamilies(data || []);
            setError(null);
        } catch (err) {
            console.error('Error fetching families:', err);
            setError('Failed to load families.');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchFamilies();
    }, []);

    const handleCreateFamily = async (e) => {
        e.preventDefault();
        if (!newFamilyName.trim()) return;
        
        setIsSubmitting(true);
        setErrorMessage(null);
        
        try {
            await createFamily({ name: newFamilyName });
            setNewFamilyName('');
            await fetchFamilies();
        } catch (err) {
            console.error('Error creating family:', err);
            setErrorMessage(err.response?.data?.message || err.message || 'Failed to create family.');
        } finally {
            setIsSubmitting(false);
        }
    };

    return (
        <div className="dash-layout">
            {/* Top Navigation */}
            <nav className="dash-nav">
                <div className="dash-brand">
                    <div className="dash-brand-icon">
                        <Shield size={24} />
                    </div>
                    <span>FamilyVault</span>
                </div>
                <UserNavProfile />
            </nav>

            {/* Main Split Layout */}
            <div className="dash-main">
                {/* Left Side - Image & Marketing */}
                <div className="dash-left">
                    <div className="dash-left-content">
                        <div className="auth-badge">
                            <Users size={14} /> TRUSTED BY FAMILIES
                        </div>
                        
                        <h1 className="auth-title">
                            Your Family's<br/>
                            <span>Digital Vault</span>
                        </h1>
                        
                        <p className="auth-subtitle">
                            Store, organize and share your important documents, photos and memories — all in one secure place, for today and tomorrow.
                        </p>
                        
                        <div className="feature-list">
                            <div className="feature-item">
                                <div className="feature-icon blue">
                                    <Lock size={20} />
                                </div>
                                <div className="feature-text">
                                    <h4>Secure Storage</h4>
                                    <p>Your data, always protected</p>
                                </div>
                            </div>
                            <div className="feature-item">
                                <div className="feature-icon green">
                                    <Users size={20} />
                                </div>
                                <div className="feature-text">
                                    <h4>Family First</h4>
                                    <p>Keep everyone connected</p>
                                </div>
                            </div>
                            <div className="feature-item">
                                <div className="feature-icon purple">
                                    <Cloud size={20} />
                                </div>
                                <div className="feature-text">
                                    <h4>Access Anywhere</h4>
                                    <p>Anytime, on any device</p>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                {/* Right Side - Dashboard Content */}
                <div className="dash-right">
                    <div className="dash-right-container">
                        
                        {/* Create Family Card */}
                        <div className="dash-card create-card">
                            <h2>Your Families</h2>
                            <p className="card-subtitle">Manage and access all your family vaults in one place.</p>
                            
                            {error && <div style={{ color: 'red', marginBottom: '1rem', fontSize: '0.9rem' }}>{error}</div>}

                            <form className="dash-create-form" onSubmit={handleCreateFamily}>
                                <input 
                                    type="text" 
                                    placeholder="Enter family name..." 
                                    value={newFamilyName}
                                    onChange={(e) => setNewFamilyName(e.target.value)}
                                    disabled={isSubmitting}
                                />
                                <button type="submit" className="dash-btn-primary" disabled={!newFamilyName.trim() || isSubmitting}>
                                    <Plus size={18} /> {isSubmitting ? 'Creating...' : 'Create Family'}
                                </button>
                            </form>
                            
                            {errorMessage && <div style={{ color: '#E11D48', marginTop: '0.75rem', fontSize: '0.9rem', fontWeight: '500' }}>{errorMessage}</div>}
                        </div>

                        {/* Family List */}
                        <div className="dash-family-list">
                            {loading ? (
                                <p style={{color: '#64748B'}}>Loading families...</p>
                            ) : families.length === 0 ? (
                                <p style={{color: '#64748B'}}>No families found. Create one above!</p>
                            ) : (
                                families.map(family => (
                                    <div key={family.id} className="dash-family-card" onClick={() => navigate(`/family/${family.id}`)}>
                                        <div className="family-card-left">
                                            <div className="family-icon-wrap">
                                                <Users size={20} />
                                            </div>
                                            <div className="family-info">
                                                <h3>{family.name}</h3>
                                                <span>Family Vault</span>
                                            </div>
                                        </div>
                                        <div className="family-card-right">
                                            <div className="arrow-btn">
                                                <ChevronRight size={20} />
                                            </div>
                                        </div>
                                    </div>
                                ))
                            )}
                        </div>

                    </div>
                </div>
            </div>
        </div>
    );
};

export default Dashboard;
