/* =========================================================
   API client — thin fetch() wrapper shared by every page.
   API_BASE is resolved at runtime so the same static files work
   whether the frontend is opened directly, served by nginx in
   Docker (proxying /api to the backend), or run locally.
   ========================================================= */

const API_BASE = (() => {
  // Explicit override always wins (set window.__API_BASE__ before this script loads).
  if (window.__API_BASE__) return window.__API_BASE__;

  const { protocol, hostname, port } = window.location;
  // Served by nginx (Docker) on the standard port -> nginx proxies /api same-origin.
  if (port === '' || port === '80' || port === '443') return '/api';
  // Local dev: frontend opened on some other port (e.g. `python -m http.server 5500`)
  // while the backend runs directly on 4000.
  return `${protocol}//${hostname}:4000/api`;
})();

const Auth = {
  getToken() { return localStorage.getItem('smap_token'); },
  setToken(t) { localStorage.setItem('smap_token', t); },
  getUser() {
    try { return JSON.parse(localStorage.getItem('smap_user') || 'null'); }
    catch (e) { return null; }
  },
  setUser(u) { localStorage.setItem('smap_user', JSON.stringify(u)); },
  clear() { localStorage.removeItem('smap_token'); localStorage.removeItem('smap_user'); },
  isLoggedIn() { return !!this.getToken(); },
};

async function apiRequest(path, { method = 'GET', body, auth = true } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (auth) {
    const token = Auth.getToken();
    if (token) headers.Authorization = `Bearer ${token}`;
  }

  let res;
  try {
    res = await fetch(`${API_BASE}${path}`, {
      method,
      headers,
      body: body ? JSON.stringify(body) : undefined,
    });
  } catch (networkErr) {
    throw new Error('Could not reach the server. Is the backend running?');
  }

  let data = null;
  const text = await res.text();
  if (text) {
    try { data = JSON.parse(text); } catch (e) { data = { raw: text }; }
  }

  if (!res.ok) {
    const message = (data && data.error) || `Request failed (${res.status})`;
    const err = new Error(message);
    err.status = res.status;
    throw err;
  }
  return data;
}

const Api = {
  register: (payload) => apiRequest('/auth/register', { method: 'POST', body: payload, auth: false }),
  login: (payload) => apiRequest('/auth/login', { method: 'POST', body: payload, auth: false }),
  me: () => apiRequest('/auth/me'),

  studentDashboard: () => apiRequest('/students/dashboard'),
  getBudget: () => apiRequest('/students/budget'),
  setBudget: (payload) => apiRequest('/students/budget', { method: 'POST', body: payload }),
  listExpenses: () => apiRequest('/students/expenses'),
  addExpense: (payload) => apiRequest('/students/expenses', { method: 'POST', body: payload }),
  suggestNextFood: (payload) => apiRequest('/students/foods/suggest', { method: 'POST', body: payload }),
  recommendMeals: () => apiRequest('/students/foods/recommendations'),
  savePlan: (itemIds) => apiRequest('/students/meal-plans', { method: 'POST', body: { itemIds } }),
  listMealPlans: () => apiRequest('/students/meal-plans'),
  getMealPlan: (id) => apiRequest(`/students/meal-plans/${id}`),
  insights: () => apiRequest('/students/insights'),
  notifications: () => apiRequest('/students/notifications'),

  adminDashboard: () => apiRequest('/admin/dashboard'),
  adminUsers: (role) => apiRequest(`/admin/users${role ? `?role=${role}` : ''}`),
  adminDeleteUser: (id) => apiRequest(`/admin/users/${id}`, { method: 'DELETE' }),
  adminListMealPlans: () => apiRequest('/admin/meal-plans'),
  adminListFoodItems: () => apiRequest('/admin/food-items'),
  adminCreateFoodItem: (payload) => apiRequest('/admin/food-items', { method: 'POST', body: payload }),
  adminUpdatePrice: (id, price) => apiRequest(`/admin/food-items/${id}/price`, { method: 'PATCH', body: { price } }),
  adminDeleteFoodItem: (id) => apiRequest(`/admin/food-items/${id}`, { method: 'DELETE' }),
};

function requireAuth(role) {
  if (!Auth.isLoggedIn()) {
    window.location.href = 'index.html';
    return false;
  }
  const user = Auth.getUser();
  if (role && user && user.role !== role) {
    window.location.href = user.role === 'ADMIN' ? 'admin.html' : 'dashboard.html';
    return false;
  }
  return true;
}

function showToast(message) {
  let el = document.getElementById('toast');
  if (!el) {
    el = document.createElement('div');
    el.id = 'toast';
    el.className = 'toast';
    document.body.appendChild(el);
  }
  el.textContent = message;
  el.classList.add('show');
  clearTimeout(window.__toastTimer);
  window.__toastTimer = setTimeout(() => el.classList.remove('show'), 3200);
}

function showError(elId, message) {
  const el = document.getElementById(elId);
  if (!el) return;
  if (!message) { el.classList.remove('show'); el.textContent = ''; return; }
  el.textContent = message;
  el.classList.add('show');
}

function money(n) {
  const num = Number(n) || 0;
  return `$${num.toFixed(2)}`;
}
