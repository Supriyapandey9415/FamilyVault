import React from 'react';
import { Shield, Users, Cloud, Lock } from 'lucide-react';
import { NavLink } from 'react-router-dom';

const AuthLayout = ({ children, hideToggle = false }) => {
  return (
    <div className="auth-container">
      <div className="auth-left">
        <div className="auth-left-bg"></div>
        <div className="auth-left-content">
          <nav className="auth-nav">
            <div className="brand">
              <div className="brand-icon">
                <Shield size={24} />
              </div>
              FamilyVault
            </div>
            <div className="nav-links">
              <span>Secure</span> • <span>Organize</span> • <span>Share</span> • <span>Together</span>
            </div>
          </nav>

          <div className="auth-hero">
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
      </div>

      <div className="auth-right">
        <div className="auth-card">
          {!hideToggle && (
            <div className="auth-toggle">
              <NavLink to="/login" className={({isActive}) => `toggle-btn ${isActive ? 'active' : ''}`}>
                Login
              </NavLink>
              <NavLink to="/register" className={({isActive}) => `toggle-btn ${isActive ? 'active' : ''}`}>
                Register
              </NavLink>
            </div>
          )}
          {children}
        </div>
      </div>
    </div>
  );
};

export default AuthLayout;
