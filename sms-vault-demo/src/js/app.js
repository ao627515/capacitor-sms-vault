import { SmsVault } from 'capacitor-sms-vault';

// ══════════════════════════════════════════════════════════════════════════════
// LOGGER — console globale centralisée
// ══════════════════════════════════════════════════════════════════════════════

const logList = document.getElementById('logList');
let logCount = 0;

function timestamp() {
  return new Date().toLocaleTimeString('fr-FR', { hour12: false });
}

function addLog(icon, method, args, resultText, type = 'ok') {
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

// ══════════════════════════════════════════════════════════════════════════════
// UTILITIES
// ══════════════════════════════════════════════════════════════════════════════

function escapeHtml(str) {
  return String(str ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function toast(message, type = 'info') {
  const container = document.getElementById('toastContainer');
  const el = document.createElement('div');
  el.className = `toast toast-${type}`;
  el.textContent = message;
  container.appendChild(el);
  setTimeout(() => el.remove(), 2700);
}

function setBadge(id, value) {
  const el = document.getElementById(id);
  if (!el) return;
  el.textContent = value;
  el.className = 'badge ' + (
    value === 'granted'                    ? 'badge-granted' :
    value === 'denied'                     ? 'badge-denied'  :
    value === 'prompt' || value === 'prompt-with-rationale' ? 'badge-prompt' :
    value === 'Oui'                        ? 'badge-yes'     :
    value === 'Non'                        ? 'badge-no'      : 'badge-unknown'
  );
}

function formatDate(ts) {
  return new Date(ts).toLocaleString('fr-FR', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
  });
}

function formatShortTime(ts) {
  return new Date(ts).toLocaleTimeString('fr-FR', { hour12: false });
}

function mapTypeToString(type) {
  switch (type) {
    case 1: return 'inbox';
    case 2: return 'sent';
    case 3: return 'draft';
    case 4: return 'outbox';
    case 5: return 'failed';
    case 6: return 'queued';
    default: return 'all';
  }
}

function renderSmsCard(sms, isNew = false) {
  const card = document.createElement('div');
  card.className = 'sms-card';
  const typeStr = typeof sms.type === 'number' ? mapTypeToString(sms.type) : (sms.type || 'inbox');
  card.innerHTML = `
    <div class="sms-card-head">
      <span class="sms-address">${escapeHtml(sms.address || '(inconnu)')}</span>
      <span class="sms-type-badge ${typeStr}">${typeStr}</span>
      ${sms.id ? `<span class="sms-id">id:${escapeHtml(sms.id)}</span>` : ''}
    </div>
    <div class="sms-body">${escapeHtml(sms.body || '(vide)')}</div>
    <div class="sms-date">${isNew ? '🟢 ' + formatShortTime(sms.date) : formatDate(sms.date)}</div>
  `;
  return card;
}

// ══════════════════════════════════════════════════════════════════════════════
// TAB NAVIGATION
// ══════════════════════════════════════════════════════════════════════════════

document.querySelectorAll('.tab-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    const target = btn.dataset.tab;

    document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
    document.querySelectorAll('.tab-panel').forEach(p => p.classList.remove('active'));

    btn.classList.add('active');
    document.getElementById(`tab-${target}`)?.classList.add('active');
  });
});

// ══════════════════════════════════════════════════════════════════════════════
// TAB 1 — PERMISSIONS
// ══════════════════════════════════════════════════════════════════════════════

async function refreshPermissionBadges() {
  const status = await SmsVault.checkPermissions();
  setBadge('statusReadSms', status.readSms);
  setBadge('statusReceiveSms', status.receiveSms);
  setBadge('statusPhone', status.phone);
  return status;
}

async function refreshDefaultBadge() {
  const { isDefault } = await SmsVault.isDefaultSmsApp();
  setBadge('statusDefault', isDefault ? 'Oui' : 'Non');
  return isDefault;
}

// checkPermissions
document.getElementById('btnCheckPerms').addEventListener('click', async () => {
  try {
    const status = await refreshPermissionBadges();
    const { isDefault } = await SmsVault.isDefaultSmsApp();
    setBadge('statusDefault', isDefault ? 'Oui' : 'Non');
    addLog('🔍', 'checkPermissions', {}, JSON.stringify(status));
    toast('Permissions vérifiées', 'info');
  } catch (e) {
    addLog('🔍', 'checkPermissions', {}, e.message, 'err');
    toast(e.message, 'error');
  }
});

// requestPermissions
document.getElementById('btnRequestPerms').addEventListener('click', async () => {
  try {
    const status = await SmsVault.requestPermissions();
    setBadge('statusReadSms', status.readSms);
    setBadge('statusReceiveSms', status.receiveSms);
    setBadge('statusPhone', status.phone);
    addLog('🔑', 'requestPermissions', {}, JSON.stringify(status));
    toast('Permissions : ' + status.readSms + ' / ' + status.receiveSms + ' / ' + status.phone,
      status.readSms === 'granted' ? 'success' : 'warn');
  } catch (e) {
    addLog('🔑', 'requestPermissions', {}, e.message, 'err');
    toast(e.message, 'error');
  }
});

// isDefaultSmsApp
document.getElementById('btnIsDefault').addEventListener('click', async () => {
  try {
    const isDefault = await refreshDefaultBadge();
    addLog('❓', 'isDefaultSmsApp', {}, '{ isDefault: ' + isDefault + ' }');
    toast('App par défaut : ' + (isDefault ? 'Oui ✓' : 'Non'), isDefault ? 'success' : 'warn');
  } catch (e) {
    addLog('❓', 'isDefaultSmsApp', {}, e.message, 'err');
    toast(e.message, 'error');
  }
});

// requestDefaultSmsApp
document.getElementById('btnRequestDefault').addEventListener('click', async () => {
  try {
    const { isDefault } = await SmsVault.requestDefaultSmsApp();
    setBadge('statusDefault', isDefault ? 'Oui' : 'Non');
    addLog('⭐', 'requestDefaultSmsApp', {}, '{ isDefault: ' + isDefault + ' }');
    toast(isDefault ? 'Définie comme app SMS par défaut ✓' : 'Changement annulé',
      isDefault ? 'success' : 'warn');
  } catch (e) {
    addLog('⭐', 'requestDefaultSmsApp', {}, e.message, 'err');
    toast(e.message, 'error');
  }
});

// getSimInfo
document.getElementById('btnGetSimInfo').addEventListener('click', async () => {
  const resultDiv = document.getElementById('simInfoResult');
  resultDiv.innerHTML = '<span class="spinner"></span> Chargement…';
  try {
    const { simCards } = await SmsVault.getSimInfo();
    addLog('📱', 'getSimInfo', {}, `${simCards.length} cartes SIM`);
    
    if (simCards.length === 0) {
      resultDiv.innerHTML = '<div style="color: var(--yellow)">Aucune carte SIM active détectée.</div>';
      return;
    }

    resultDiv.innerHTML = simCards.map(sim => `
      <div style="background: var(--surface2); border: 1px solid var(--border); padding: 8px; border-radius: var(--radius-sm); margin-bottom: 6px; text-align: left;">
        <strong>Opérateur :</strong> ${escapeHtml(sim.carrierName)}<br>
        <strong>Pays :</strong> ${escapeHtml(sim.countryIso).toUpperCase()}<br>
        <strong>Slot :</strong> ${sim.simSlotIndex}<br>
        <strong>ID Abonnement :</strong> ${sim.subscriptionId}<br>
        <strong>Numéro :</strong> ${sim.phoneNumber ? escapeHtml(sim.phoneNumber) : '<span style="color: var(--text-dim); font-style: italic;">non disponible</span>'}
      </div>
    `).join('');
  } catch (e) {
    addLog('📱', 'getSimInfo', {}, e.message, 'err');
    resultDiv.innerHTML = `<div style="color: var(--red)">Erreur: ${escapeHtml(e.message)}</div>`;
    toast(e.message, 'error');
  }
});

// ══════════════════════════════════════════════════════════════════════════════
// TAB 2 — SMS LIST
// ══════════════════════════════════════════════════════════════════════════════

// — Slider limit
const filterLimit = document.getElementById('filterLimit');
const filterLimitValue = document.getElementById('filterLimitValue');
filterLimit.addEventListener('input', () => {
  filterLimitValue.textContent = filterLimit.value;
});

// — Since toggle
const filterSinceToggle = document.getElementById('filterSinceToggle');
const filterSinceField = document.getElementById('filterSinceField');
filterSinceToggle.addEventListener('change', () => {
  filterSinceField.style.display = filterSinceToggle.checked ? 'block' : 'none';
  if (filterSinceToggle.checked && !document.getElementById('filterSince').value) {
    // Pré-remplir avec il y a 7 jours
    const d = new Date(Date.now() - 7 * 24 * 3600 * 1000);
    document.getElementById('filterSince').value =
      d.toISOString().slice(0, 16);
  }
});

// — Type radio
let selectedType = 'all';
document.querySelectorAll('.radio-btn[data-type]').forEach(btn => {
  btn.addEventListener('click', () => {
    document.querySelectorAll('.radio-btn[data-type]').forEach(b => b.classList.remove('selected'));
    btn.classList.add('selected');
    selectedType = btn.dataset.type;
  });
});

// — Reset
document.getElementById('btnResetFilters').addEventListener('click', () => {
  document.getElementById('filterAddress').value = '';
  document.getElementById('filterSinceToggle').checked = false;
  filterSinceField.style.display = 'none';
  filterLimit.value = 20;
  filterLimitValue.textContent = '20';
  selectedType = 'all';
  document.querySelectorAll('.radio-btn[data-type]').forEach(b => b.classList.remove('selected'));
  document.querySelector('.radio-btn[data-type="all"]').classList.add('selected');

  document.getElementById('smsResults').innerHTML = `
    <div class="empty-state">
      <div class="empty-icon">📬</div>
      Filtres réinitialisés — lancez une nouvelle recherche
    </div>`;
});

// — Rechercher
document.getElementById('btnGetSmsList').addEventListener('click', async () => {
  const btn = document.getElementById('btnGetSmsList');
  btn.disabled = true;
  btn.innerHTML = '<span class="spinner"></span> Chargement…';

  const filter = {};
  const address = document.getElementById('filterAddress').value.trim();
  if (address) filter.address = address;

  if (filterSinceToggle.checked) {
    const sinceVal = document.getElementById('filterSince').value;
    if (sinceVal) filter.minDate = new Date(sinceVal).getTime();
  }

  filter.maxCount = parseInt(filterLimit.value, 10);

  // Type de boîte
  if (selectedType === 'all') {
    filter.type = 0; // MessageType.ALL
  } else if (selectedType === 'inbox') {
    filter.type = 1; // MessageType.INBOX
  } else if (selectedType === 'sent') {
    filter.type = 2; // MessageType.SENT
  }

  const options = { filter };

  try {
    let { messages } = await SmsVault.getSmsList(options);

    addLog('🔍', 'getSmsList', options, `${messages.length} messages`);
    renderSmsResults(messages, options);
    toast(`${messages.length} SMS chargés`, 'success');
  } catch (e) {
    addLog('🔍', 'getSmsList', options, e.message, 'err');
    document.getElementById('smsResults').innerHTML = `
      <div class="empty-state" style="color:#f87171">
        <div class="empty-icon">❌</div>
        ${escapeHtml(e.message)}
      </div>`;
    toast(e.message, 'error');
  } finally {
    btn.disabled = false;
    btn.innerHTML = '🔍 Rechercher';
  }
});

function renderSmsResults(messages, options) {
  const container = document.getElementById('smsResults');

  if (messages.length === 0) {
    container.innerHTML = `
      <div class="empty-state">
        <div class="empty-icon">🔍</div>
        Aucun SMS trouvé avec ces filtres
      </div>`;
    return;
  }

  const jsonRaw = JSON.stringify({ messages }, null, 2);

  container.innerHTML = '';

  // Barre de résultats
  const bar = document.createElement('div');
  bar.className = 'result-bar';
  bar.innerHTML = `
    <span class="result-count"><strong>${messages.length}</strong> SMS trouvés</span>
    <div class="result-actions">
      <button class="btn btn-ghost btn-sm" id="btnToggleJson">{ } JSON</button>
      <button class="btn btn-ghost btn-sm" id="btnCopyJson">📋 Copier</button>
    </div>
  `;
  container.appendChild(bar);

  // JSON brut (caché par défaut)
  const jsonBlock = document.createElement('pre');
  jsonBlock.className = 'json-block';
  jsonBlock.textContent = jsonRaw;
  container.appendChild(jsonBlock);

  // Toggle JSON
  bar.querySelector('#btnToggleJson').addEventListener('click', () => {
    jsonBlock.classList.toggle('visible');
  });

  // Copier JSON
  bar.querySelector('#btnCopyJson').addEventListener('click', () => {
    navigator.clipboard?.writeText(jsonRaw)
      .then(() => toast('JSON copié ✓', 'success'))
      .catch(() => toast('Copie non supportée sur ce device', 'warn'));
  });

  // Cards SMS
  messages.forEach(sms => {
    container.appendChild(renderSmsCard(sms));
  });
}

// ══════════════════════════════════════════════════════════════════════════════
// TAB 3 — LIVE
// ══════════════════════════════════════════════════════════════════════════════

let liveListener = null;
let liveCount = 0;
let isListening = false;

const btnToggleListen = document.getElementById('btnToggleListen');
const liveDot = document.getElementById('liveDot');
const liveStatus = document.getElementById('liveStatus');
const liveCounter = document.getElementById('liveCounter');
const liveList = document.getElementById('liveList');

function updateLiveUI() {
  if (isListening) {
    liveDot.classList.add('active');
    liveStatus.textContent = 'Écoute active…';
    liveStatus.style.color = 'var(--green)';
    btnToggleListen.className = 'btn btn-danger';
    btnToggleListen.innerHTML = '⏹ stopListening()';
  } else {
    liveDot.classList.remove('active');
    liveStatus.textContent = 'Écoute inactive';
    liveStatus.style.color = '';
    btnToggleListen.className = 'btn btn-success';
    btnToggleListen.innerHTML = '▶ startListening()';
  }
  liveCounter.textContent = `${liveCount} reçu${liveCount > 1 ? 's' : ''}`;
}

btnToggleListen.addEventListener('click', async () => {
  btnToggleListen.disabled = true;
  try {
    if (!isListening) {
      // Démarrer
      liveListener = await SmsVault.addListener('smsReceived', (sms) => {
        liveCount++;
        updateLiveUI();
        addLog('📡', 'event:smsReceived', {}, `from ${sms.address}`, 'event');

        // Supprimer l'empty state si présent
        const empty = liveList.querySelector('.empty-state');
        if (empty) empty.remove();

        liveList.prepend(renderSmsCard(sms, true));
      });
      await SmsVault.startListening();
      isListening = true;
      addLog('▶', 'startListening', {}, 'OK');
      toast('Écoute SMS démarrée', 'success');
    } else {
      // Arrêter
      await SmsVault.stopListening();
      if (liveListener) {
        await liveListener.remove();
        liveListener = null;
      }
      isListening = false;
      addLog('⏹', 'stopListening', {}, 'OK');
      toast('Écoute SMS arrêtée', 'info');
    }
    updateLiveUI();
  } catch (e) {
    addLog(isListening ? '⏹' : '▶', isListening ? 'stopListening' : 'startListening', {}, e.message, 'err');
    toast(e.message, 'error');
  } finally {
    btnToggleListen.disabled = false;
  }
});

document.getElementById('btnClearLive').addEventListener('click', () => {
  liveCount = 0;
  updateLiveUI();
  liveList.innerHTML = `
    <div class="empty-state">
      <div class="empty-icon">📡</div>
      Historique vidé — l'écoute continue si elle est active
    </div>`;
});

// ══════════════════════════════════════════════════════════════════════════════
// TAB 4 — LOGS
// ══════════════════════════════════════════════════════════════════════════════

document.getElementById('btnClearLogs').addEventListener('click', () => {
  logCount = 0;
  logList.innerHTML = '<div class="log-empty">Aucun appel plugin enregistré</div>';
  toast('Logs effacés', 'info');
});

document.getElementById('btnCopyLogs').addEventListener('click', () => {
  const entries = [...logList.querySelectorAll('.log-entry')].map(e => e.textContent.trim()).join('\n---\n');
  if (!entries) { toast('Aucun log à copier', 'warn'); return; }
  navigator.clipboard?.writeText(entries)
    .then(() => toast('Logs copiés ✓', 'success'))
    .catch(() => toast('Copie non supportée sur ce device', 'warn'));
});

// ══════════════════════════════════════════════════════════════════════════════
// TAB — OTP RETRIEVER
// ══════════════════════════════════════════════════════════════════════════════

let otpListener = null;
let isOtpListening = false;

const btnStartOtp = document.getElementById('btnStartOtp');
const otpDot = document.getElementById('otpDot');
const otpStatus = document.getElementById('otpStatus');
const otpResult = document.getElementById('otpResult');

function updateOtpUI(listening, message = null) {
  isOtpListening = listening;
  if (listening) {
    otpDot.classList.add('active');
    otpStatus.textContent = 'Écoute active (attente SMS)…';
    otpStatus.style.color = 'var(--green)';
    btnStartOtp.disabled = true;
    btnStartOtp.innerHTML = '<span class="spinner"></span> Écoute en cours…';
  } else {
    otpDot.classList.remove('active');
    otpStatus.textContent = 'Inactif (Attente de démarrage)';
    otpStatus.style.color = '';
    btnStartOtp.disabled = false;
    btnStartOtp.innerHTML = '▶ startSmsRetriever()';
  }

  if (message) {
    otpResult.innerHTML = `
      <div class="sms-card" style="border-color: var(--green);">
        <div class="sms-card-head">
          <span class="sms-address" style="color: var(--green);">🔑 OTP Détecté</span>
          <span class="sms-type-badge inbox">OTP</span>
        </div>
        <div class="sms-body" style="color: #fff; font-weight: bold; font-size: 1.05rem;">${escapeHtml(message)}</div>
        <div class="sms-date">Reçu à : ${timestamp()}</div>
      </div>
    `;
  }
}

btnStartOtp.addEventListener('click', async () => {
  try {
    updateOtpUI(true);
    
    // Supprimer l'écouteur précédent s'il existe
    if (otpListener) {
      await otpListener.remove();
      otpListener = null;
    }

    // S'abonner à l'événement otpReceived
    otpListener = await SmsVault.addListener('otpReceived', (data) => {
      addLog('🔑', 'event:otpReceived', {}, data.message, 'event');
      updateOtpUI(false, data.message);
      toast('OTP reçu avec succès !', 'success');
      
      if (otpListener) {
        otpListener.remove();
        otpListener = null;
      }
    });

    // Démarrer l'écouteur natif
    await SmsVault.startSmsRetriever();
    addLog('▶', 'startSmsRetriever', {}, 'OK');
    toast('Écouteur SMS Retriever démarré (valide 5 min)', 'success');
  } catch (e) {
    updateOtpUI(false);
    addLog('▶', 'startSmsRetriever', {}, e.message, 'err');
    toast(e.message, 'error');
  }
});

// ══════════════════════════════════════════════════════════════════════════════
// INIT — vérifier les permissions au démarrage
// ══════════════════════════════════════════════════════════════════════════════

(async () => {
  try {
    const status = await SmsVault.checkPermissions();
    setBadge('statusReadSms', status.readSms);
    setBadge('statusReceiveSms', status.receiveSms);

    const { isDefault } = await SmsVault.isDefaultSmsApp();
    setBadge('statusDefault', isDefault ? 'Oui' : 'Non');

    addLog('🚀', 'init:checkPermissions', {}, JSON.stringify(status));
  } catch (e) {
    // Silencieux au démarrage (normal sur web/émulateur)
    addLog('🚀', 'init:checkPermissions', {}, e.message, 'err');
  }
})();
