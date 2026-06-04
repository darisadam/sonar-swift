// Vanilla-JS dashboard. No build step. Loaded as <script src="/assets/app.js">.
// The Java DashboardServer serves /api/* endpoints; this file just renders.

const SonarSwift = (() => {

    function escapeHtml(s) {
        return String(s).replace(/[&<>"']/g, ch => ({
            '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
        })[ch]);
    }

    async function getJson(url, opts = {}) {
        const r = await fetch(url, opts);
        try {
            return await r.json();
        } catch {
            return { error: `Non-JSON response (status ${r.status})` };
        }
    }

    async function refreshOverview() {
        const [health, ci, leak] = await Promise.all([
            getJson('/api/health'),
            getJson('/api/ci/runs'),
            getJson('/api/leaks/latest')
        ]);

        const healthList = document.getElementById('health-list');
        healthList.innerHTML = `
            <li><span>dashboard</span><span class="tag ok">${escapeHtml(health.status || 'unknown')}</span></li>
            <li><span>sonarqube</span><span class="tag ${health.sonarqube && health.sonarqube.reachable ? 'ok' : 'fail'}">
                ${health.sonarqube && health.sonarqube.reachable ? 'reachable' : 'unreachable'}
            </span></li>
            <li><span>checked at</span><span class="muted">${escapeHtml(health.now || '')}</span></li>
        `;
        document.getElementById('ci-summary').textContent =
            ci && ci.timestamp ? JSON.stringify(ci, null, 2) : '(no local CI runs yet)';
        document.getElementById('leak-summary').textContent =
            leak && leak.timestamp ? JSON.stringify(leak, null, 2) : '(no leak reports yet)';
    }

    async function triggerScan() {
        const btn = document.getElementById('run-scan');
        const status = document.getElementById('run-scan-status');
        btn.disabled = true;
        status.textContent = 'Scanning…';
        const r = await fetch('/api/scan', { method: 'POST' });
        const j = await r.json();
        status.textContent = j.exit === 0
            ? '✓ Scan completed. Open SonarQube to see results.'
            : `✗ Scan failed (exit ${j.exit}). See dashboard logs.`;
        btn.disabled = false;
    }

    async function refreshScans() {
        const [ci, projects] = await Promise.all([
            getJson('/api/ci/runs'),
            getJson('/api/projects')
        ]);
        const tbody = document.querySelector('#ci-table tbody');
        if (!ci || !ci.stages) {
            tbody.innerHTML = '<tr><td colspan="3" class="muted">No CI runs recorded yet — run <code>scripts/ci/ci-local.sh</code>.</td></tr>';
        } else {
            tbody.innerHTML = Object.entries(ci.stages).map(([name, info]) => `
                <tr>
                    <td>${escapeHtml(name)}</td>
                    <td><span class="tag ${info.result}">${escapeHtml(info.result)}</span></td>
                    <td>${info.durationSec}s</td>
                </tr>
            `).join('');
        }
        const projectsList = document.getElementById('projects-list');
        if (projects && projects.components) {
            projectsList.innerHTML = projects.components.map(c => `
                <li><span>${escapeHtml(c.name || c.key)}</span><span class="muted">${escapeHtml(c.key)}</span></li>
            `).join('') || '<li class="muted">(SonarQube has no projects yet)</li>';
        } else {
            projectsList.innerHTML = `<li class="muted">SonarQube unreachable (${escapeHtml(projects.error || '?')}).</li>`;
        }
    }

    async function refreshLeaks() {
        const [latest, history] = await Promise.all([
            getJson('/api/leaks/latest'),
            getJson('/api/leaks/history')
        ]);
        document.getElementById('leak-latest').textContent =
            latest && latest.timestamp ? JSON.stringify(latest, null, 2) : '(no leak reports yet)';
        const tbody = document.querySelector('#leak-history tbody');
        const entries = (history && history.entries) || [];
        tbody.innerHTML = entries.length
            ? entries.map(e => `
                <tr>
                    <td><code>${escapeHtml(e.path)}</code></td>
                    <td>${escapeHtml(e.mtime || '')}</td>
                    <td>${e.size} B</td>
                </tr>
              `).join('')
            : '<tr><td colspan="3" class="muted">No leak reports yet — run <code>scripts/leak/leak-check.sh</code>.</td></tr>';
    }

    async function acceptBudget() {
        const r = await fetch('/api/leaks/budget/accept', { method: 'POST' });
        const j = await r.json();
        alert(j.ok ? `Budget updated → ${j.budget}` : `Failed: ${j.error}`);
        refreshLeaks();
    }

    async function refreshPrecommit() {
        const j = await getJson('/api/precommit/runs');
        const tbody = document.querySelector('#precommit-table tbody');
        const entries = (j && j.entries) || [];
        tbody.innerHTML = entries.length
            ? entries.map(e => `
                <tr>
                    <td><code>${escapeHtml(e.path)}</code></td>
                    <td>${escapeHtml(e.mtime || '')}</td>
                    <td>${e.size} B</td>
                </tr>
              `).join('')
            : '<tr><td colspan="3" class="muted">No pre-commit history recorded yet. The runner writes to .sonar/precommit/.</td></tr>';
    }

    async function refreshSettings() {
        const j = await getJson('/api/config');
        document.getElementById('config-json').textContent = JSON.stringify(j, null, 2);
    }

    return {
        refreshOverview,
        refreshScans,
        refreshLeaks,
        refreshPrecommit,
        refreshSettings,
        triggerScan,
        acceptBudget
    };
})();

window.SonarSwift = SonarSwift;
