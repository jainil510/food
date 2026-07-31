import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import AdminRoute from './AdminRoute';
import { useAuth } from '../contexts/AuthContext';

vi.mock('../contexts/AuthContext', () => ({ useAuth: vi.fn() }));

function renderAdmin(initialPath) {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Routes>
        <Route path="/login" element={<div>Login Page</div>} />
        <Route path="/" element={<div>Home Page</div>} />
        <Route
          path="/admin"
          element={
            <AdminRoute>
              <div>Admin Panel</div>
            </AdminRoute>
          }
        />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  useAuth.mockReset();
});

describe('AdminRoute', () => {
  it('renders the admin children when authenticated as ADMIN', () => {
    useAuth.mockReturnValue({ isAuthenticated: true, user: { role: 'ADMIN' } });

    renderAdmin('/admin');

    expect(screen.getByText('Admin Panel')).toBeInTheDocument();
  });

  it('redirects to /login when not authenticated', () => {
    useAuth.mockReturnValue({ isAuthenticated: false, user: null });

    renderAdmin('/admin');

    expect(screen.getByText('Login Page')).toBeInTheDocument();
    expect(screen.queryByText('Admin Panel')).not.toBeInTheDocument();
  });

  it('redirects to / when authenticated but not an ADMIN', () => {
    useAuth.mockReturnValue({ isAuthenticated: true, user: { role: 'USER' } });

    renderAdmin('/admin');

    expect(screen.getByText('Home Page')).toBeInTheDocument();
    expect(screen.queryByText('Admin Panel')).not.toBeInTheDocument();
  });
});
