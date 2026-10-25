import { useEffect, useMemo, useState } from 'react';

const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

async function apiRequest(path, options = {}) {
  const response = await fetch(`${API_URL}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers || {}),
    },
    credentials: 'include',
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(data.error || 'Request failed.');
  }
  return data;
}

export default function App() {
  const [ownerExists, setOwnerExists] = useState(null);
  const [authMode, setAuthMode] = useState('register');
  const [user, setUser] = useState(null);
  const [error, setError] = useState('');

  const loadOwnerStatus = async () => {
    const data = await apiRequest('/api/auth/owner-status');
    setOwnerExists(Boolean(data.ownerExists));
  };

  const loadCurrentUser = async () => {
    try {
      const data = await apiRequest('/api/auth/me');
      setUser(data.user);
    } catch {
      setUser(null);
    }
  };

  useEffect(() => {
    (async () => {
      try {
        await loadOwnerStatus();
        await loadCurrentUser();
      } catch (err) {
        setError(err.message);
      }
    })();
  }, []);

  const handleSubmit = async (event) => {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    const payload = Object.fromEntries(formData.entries());
    const isRegister = !ownerExists;
    const endpoint = isRegister ? '/api/auth/register' : '/api/auth/login';

    try {
      const body = isRegister
        ? { username: payload.username, email: payload.email, password: payload.password }
        : { usernameOrEmail: payload.usernameOrEmail, password: payload.password };

      const response = await apiRequest(endpoint, {
        method: 'POST',
        body: JSON.stringify(body),
      });

      setUser(response.user);
      setError('');
      if (isRegister) {
        setOwnerExists(true);
      }
    } catch (err) {
      setError(err.message);
    }
  };

  const handleLogout = async () => {
    try {
      await apiRequest('/api/auth/logout', { method: 'POST' });
      setUser(null);
    } catch (err) {
      setError(err.message);
    }
  };

  const handleUpload = async (event) => {
    const file = event.target.files?.[0];
    if (!file) return;
    const formData = new FormData();
    formData.append('file', file);
    try {
      const response = await fetch(`${API_URL}/api/uploads`, {
        method: 'POST',
        credentials: 'include',
        body: formData,
        headers: {
          'X-User-Id': user.id,
        },
      });
      const data = await response.json();
      if (!response.ok) {
        throw new Error(data.error || 'Upload failed');
      }
      setError('');
      alert(`Upload complete: ${data.originalName}`);
    } catch (err) {
      setError(err.message);
    }
  };

  const isAuthenticated = useMemo(() => Boolean(user), [user]);

  if (ownerExists === null) {
    return <div className="shell"><div className="card"><h1>Loading…</h1></div></div>;
  }

  if (!isAuthenticated) {
    const isRegisterFlow = !ownerExists;
    const title = isRegisterFlow ? 'Create the first owner account' : 'Login';
    return (
      <div className="shell">
        <div className="card">
          <div className="eyebrow">modl.gg self-hosted</div>
          <h1>{title}</h1>
          {error && <div className="error-box">{error}</div>}
          <form onSubmit={handleSubmit} className="auth-form">
            {isRegisterFlow && <input type="text" name="username" placeholder="Username" required />}
            {!isRegisterFlow && <input type="text" name="usernameOrEmail" placeholder="Username or email" required />}
            {isRegisterFlow && <input type="email" name="email" placeholder="Email" required />}
            <input type="password" name="password" placeholder="Password" required />
            <button type="submit">{isRegisterFlow ? 'Create account' : 'Log in'}</button>
          </form>
        </div>
      </div>
    );
  }

  return (
    <div className="shell">
      <div className="card dashboard">
        <div className="header-row">
          <div>
            <div className="eyebrow">Panel</div>
            <h1>Welcome, {user.username}</h1>
          </div>
          <button type="button" className="secondary" onClick={handleLogout}>Log out</button>
        </div>

        {error && <div className="error-box">{error}</div>}

        <div className="panel-block">
          <h2>Upload a file</h2>
          <label className="upload-box">
            <input type="file" onChange={handleUpload} />
            <span>Choose file</span>
          </label>
        </div>
      </div>
    </div>
  );
}
