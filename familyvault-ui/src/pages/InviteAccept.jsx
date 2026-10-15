import React, { useContext, useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { ArrowRight, CheckCircle2, Mail, Shield, XCircle } from 'lucide-react';
import { AuthContext } from '../context/AuthContext';
import { acceptInvitation, previewInvitation } from '../services/invitationService';
import AuthLayout from '../components/AuthLayout';

const InviteAccept = () => {
    const [searchParams] = useSearchParams();
    const token = searchParams.get('token');
    const navigate = useNavigate();
    const { token: authToken } = useContext(AuthContext);
    const [invitation, setInvitation] = useState(null);
    const [loading, setLoading] = useState(true);
    const [accepting, setAccepting] = useState(false);
    const [error, setError] = useState(null);
    const [accepted, setAccepted] = useState(false);

    useEffect(() => {
        const loadInvitation = async () => {
            if (!token) {
                setError('This invitation link is missing a token.');
                setLoading(false);
                return;
            }
            try {
                const data = await previewInvitation(token);
                setInvitation(data);
            } catch (err) {
                setError(err.response?.data?.message || 'This invitation link is invalid or has expired.');
            } finally {
                setLoading(false);
            }
        };
        loadInvitation();
    }, [token]);

    const handleAccept = async () => {
        if (!authToken) {
            navigate(`/login?invite=${encodeURIComponent(token)}`);
            return;
        }

        setAccepting(true);
        setError(null);
        try {
            await acceptInvitation(token);
            setAccepted(true);
            setTimeout(() => navigate(`/family/${invitation.familyId}`), 900);
        } catch (err) {
            setError(err.response?.data?.message || 'We could not accept this invitation.');
        } finally {
            setAccepting(false);
        }
    };

    return (
        <AuthLayout>
            <div className="auth-header">
                <div style={{ display: 'flex', justifyContent: 'center', marginBottom: '1rem' }}>
                    <Shield size={36} color="#4F46E5" fill="#EEF2FF" />
                </div>
                <h2>{accepted ? 'Welcome to the family!' : 'Family invitation'}</h2>
                <p>{accepted ? 'Your access has been added successfully.' : 'Join your family securely on FamilyVault.'}</p>
            </div>

            {loading && <p style={{ textAlign: 'center', color: '#64748B' }}>Checking invitation...</p>}

            {!loading && invitation && !accepted && (
                <div style={{ textAlign: 'center' }}>
                    <div style={{ margin: '0 auto 1rem', width: 54, height: 54, borderRadius: '50%', background: '#EEF2FF', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#4F46E5' }}>
                        <Mail size={24} />
                    </div>
                    <p style={{ color: '#475569', lineHeight: 1.5 }}>
                        You have been invited to join <strong>{invitation.familyName}</strong> as a <strong>{invitation.role}</strong>.
                    </p>
                    <p style={{ color: '#64748B', fontSize: '0.9rem' }}>
                        Invitation for {invitation.email}
                    </p>

                    {invitation.status !== 'PENDING' ? (
                        <p style={{ color: '#DC2626', marginTop: '1.25rem' }}>This invitation is no longer active.</p>
                    ) : (
                        <button type="button" className="btn-primary" onClick={handleAccept} disabled={accepting} style={{ marginTop: '1.25rem' }}>
                            {accepting ? 'Joining...' : (authToken ? 'Accept & Join Family' : 'Login to Accept')} <ArrowRight size={18} />
                        </button>
                    )}

                    {!authToken && invitation.status === 'PENDING' && (
                        <p style={{ marginTop: '1rem', fontSize: '0.85rem', color: '#64748B' }}>
                            New to FamilyVault? <Link to={`/register?invite=${encodeURIComponent(token)}`}>Create an account</Link>
                        </p>
                    )}
                </div>
            )}

            {accepted && (
                <div style={{ textAlign: 'center', color: '#16A34A' }}>
                    <CheckCircle2 size={42} />
                    <p>Opening your family vault...</p>
                </div>
            )}

            {error && (
                <div style={{ color: '#DC2626', textAlign: 'center', marginTop: '1rem', fontSize: '0.9rem' }}>
                    <XCircle size={18} style={{ verticalAlign: 'middle', marginRight: 6 }} />{error}
                </div>
            )}
        </AuthLayout>
    );
};

export default InviteAccept;
