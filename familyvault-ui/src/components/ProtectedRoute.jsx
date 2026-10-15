import React, { useContext } from 'react';
import { Navigate } from 'react-router-dom';
import { AuthContext } from '../context/AuthContext';

const ProtectedRoute = ({ children }) => {
    const { token, authLoading } = useContext(AuthContext);

    if (authLoading) return <div role="status" style={{ padding: '2rem' }}>Checking your session...</div>;

    if (!token) {
        return <Navigate to="/login" replace />;
    }

    return children;
};

export default ProtectedRoute;
