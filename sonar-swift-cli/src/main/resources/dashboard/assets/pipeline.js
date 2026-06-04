// Pipeline canvas — renders stages from a RunResult JSON payload.

(function() {
    function escapeHtml(s) {
        return String(s).replace(/[&<>"']/g, ch => ({
            '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
        })[ch]);
    }

    async function getJson(url) {
        const r = await fetch(url);
        try { return await r.json(); } catch { return null; }
    }

    function statusClass(status) {
        return (status || '').toString().toLowerCase();
    }

    function statusIcon(status) {
        switch ((status || '').toString().toUpperCase()) {
            case 'PASS': return '✓';
            case 'FAIL': return '✗';
            case 'SKIPPED': return '⏭';
            case 'RUNNING': return '·';
            default: return '?';
        }
    }

    function formatDuration(ms) {
        if (!ms || ms < 0) return '';
        if (ms < 1000) return ms + 'ms';
        const s = Math.round(ms / 1000);
        if (s < 60) return s + 's';
        return Math.floor(s / 60) + 'm' + (s % 60) + 's';
    }

    function renderCanvas(state) {
        const canvas = document.getElementById('canvas');
        if (!state || !state.stages) {
            canvas.innerHTML = '<div class="muted">No pipeline run yet — click "Run pipeline" or invoke <code>make ci</code>.</div>';
            return;
        }
        const meta = document.getElementById('run-meta');
        meta.textContent = `${state.pipelineName || 'pipeline'} · ${state.status} · ${formatDuration(state.durationMs)} · run ${state.runId}`;

        canvas.innerHTML = '';
        for (const stage of state.stages) {
            const card = document.createElement('article');
            card.className = `stage-card ${statusClass(stage.status)}`;

            const stepsHtml = (stage.steps || []).map(step => {
                const cls = statusClass(step.status);
                const time = formatDuration(step.durationMs);
                const meta = step.exitCode != null && step.exitCode >= 0
                    ? `exit ${step.exitCode} · ${time}` : (step.skippedReason || time);
                return `
                    <li class="step-item ${cls}"
                        data-run="${escapeHtml(state.runId)}"
                        data-stage="${escapeHtml(stage.id)}"
                        data-step="${escapeHtml(step.id)}"
                        data-name="${escapeHtml(step.name)}">
                        <span class="step-icon">${statusIcon(step.status)}</span>
                        <span class="step-name">${escapeHtml(step.name || step.id)}</span>
                        <span class="step-meta">${escapeHtml(meta)}</span>
                    </li>`;
            }).join('');

            const needs = stage.needs && stage.needs.length
                ? `<div class="stage-needs">needs: ${stage.needs.map(escapeHtml).join(', ')}</div>` : '';

            card.innerHTML = `
                <div class="stage-header">
                    <h3>${escapeHtml(stage.name || stage.id)}</h3>
                    <span class="step-icon ${statusClass(stage.status)}">${statusIcon(stage.status)}</span>
                </div>
                <div class="stage-id">${escapeHtml(stage.id)} · ${escapeHtml(stage.status)} · ${formatDuration(stage.durationMs)}</div>
                ${needs}
                <ul class="step-list">${stepsHtml}</ul>
            `;
            canvas.appendChild(card);
        }

        canvas.querySelectorAll('.step-item').forEach(item => {
            item.addEventListener('click', () => openStepLog(item));
        });
    }

    function renderRunsTable(entries) {
        const tbody = document.querySelector('#runs-table tbody');
        if (!entries || !entries.length) {
            tbody.innerHTML = '<tr><td colspan="5" class="muted">No runs yet — trigger one above or run <code>make ci</code>.</td></tr>';
            return;
        }
        tbody.innerHTML = entries.map(e => {
            let state = {};
            try { state = JSON.parse(e.state); } catch {}
            return `
                <tr>
                    <td><code>${escapeHtml(e.runId)}</code></td>
                    <td>${escapeHtml(state.pipelineName || '?')}</td>
                    <td><span class="step-icon ${statusClass(state.status)}">${statusIcon(state.status)}</span> ${escapeHtml(state.status || '?')}</td>
                    <td>${formatDuration(state.durationMs)}</td>
                    <td>${escapeHtml(e.mtime || '')}</td>
                </tr>`;
        }).join('');
    }

    async function openStepLog(item) {
        const runId = item.dataset.run;
        const stage = item.dataset.stage;
        const step = item.dataset.step;
        const name = item.dataset.name;
        document.getElementById('modal-title').textContent = `${stage} / ${name}`;
        const body = document.getElementById('modal-body');
        body.textContent = 'loading…';
        document.getElementById('step-modal').classList.remove('hidden');
        try {
            const r = await fetch(`/api/pipeline/run/${runId}/step/${stage}/${step}`);
            const text = await r.text();
            body.textContent = text || '(empty log)';
        } catch (e) {
            body.textContent = 'Failed to load log: ' + e;
        }
    }

    async function refresh() {
        const latest = await getJson('/api/pipeline/latest');
        renderCanvas(latest);
        if (latest && latest.stages) {
            const select = document.getElementById('stage-select');
            // Populate stage select with known stages — used for re-trigger
            const existing = new Set(Array.from(select.options).map(o => o.value));
            for (const s of latest.stages) {
                if (existing.has(s.id)) continue;
                const opt = document.createElement('option');
                opt.value = s.id;
                opt.textContent = s.id;
                select.appendChild(opt);
            }
        }
        const runs = await getJson('/api/pipeline/runs');
        renderRunsTable(runs ? runs.entries : []);
    }

    async function trigger() {
        const stage = document.getElementById('stage-select').value;
        const btn = document.getElementById('trigger-btn');
        btn.disabled = true;
        btn.textContent = 'Starting…';
        const body = new URLSearchParams();
        if (stage) body.append('stage', stage);
        try {
            await fetch('/api/pipeline/trigger', { method: 'POST', body });
            // Poll latest until status changes
            for (let i = 0; i < 60; i++) {
                await new Promise(r => setTimeout(r, 1000));
                await refresh();
            }
        } finally {
            btn.disabled = false;
            btn.textContent = '▶ Run pipeline';
        }
    }

    document.getElementById('refresh-btn').addEventListener('click', refresh);
    document.getElementById('trigger-btn').addEventListener('click', trigger);
    document.getElementById('modal-close').addEventListener('click', () =>
        document.getElementById('step-modal').classList.add('hidden'));
    document.getElementById('step-modal').addEventListener('click', e => {
        if (e.target.id === 'step-modal') {
            document.getElementById('step-modal').classList.add('hidden');
        }
    });

    refresh();
})();
