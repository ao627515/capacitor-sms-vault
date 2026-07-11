import { escapeHtml, timestamp } from './helpers.js';
import { toast } from './ui.js';

let logList;
let logCount = 0;

export function addLog(icon, method, args, resultText, type = 'ok') {
  if (!logList) {
    logList = document.getElementById('logList');
  }
  if (!logList) return;

  logCount++;

  const logEmpty = logList.querySelector('.log-empty');
  if (logEmpty) logEmpty.remove();

  const entry = document.createElement('div');
  entry.className = 'log-entry';

  const argsStr = args && Object.keys(args).length > 0
    ? '(' + JSON.stringify(args) + ')'
    : '()';

  const resultClass = type === 'ok' ? 'log-result-ok'
    : type === 'event' ? 'log-result-event'
    : 'log-result-err';

  entry.innerHTML = `
    <span class="log-time">${timestamp()}</span>
    <span class="log-icon">${icon}</span>
    <div class="log-body">
      <span class="log-method">${escapeHtml(method)}</span><span class="log-args">${escapeHtml(argsStr)}</span>
      <br><span class="${resultClass}">→ ${escapeHtml(String(resultText))}</span>
    </div>
  `;

  logList.prepend(entry);
}

export function initLogger() {
  const container = document.getElementById('tab-logs');
  if (!container) return;

  container.innerHTML = `
    <div style="display:flex;gap:8px;margin-bottom:12px">
      <button class="btn btn-ghost btn-sm" id="btnClearLogs" style="flex:1">🗑 Vider</button>
      <button class="btn btn-ghost btn-sm" id="btnCopyLogs" style="flex:1">📋 Copier</button>
    </div>

    <div class="card">
      <div id="logList">
        <div class="log-empty">Aucun appel plugin enregistré</div>
      </div>
    </div>
  `;

  logList = document.getElementById('logList');

  document.getElementById('btnClearLogs').addEventListener('click', () => {
    logCount = 0;
    logList.innerHTML = '<div class="log-empty">Aucun appel plugin enregistré</div>';
    toast('Logs effacés', 'info');
  });

  document.getElementById('btnCopyLogs').addEventListener('click', () => {
    const entries = [...logList.querySelectorAll('.log-entry')].map(e => e.textContent.trim()).join('\n---\n');
    if (!entries) {
      toast('Aucun log à copier', 'warn');
      return;
    }
    navigator.clipboard?.writeText(entries)
      .then(() => toast('Logs copiés ✓', 'success'))
      .catch(() => toast('Copie non supportée sur ce device', 'warn'));
  });
}
