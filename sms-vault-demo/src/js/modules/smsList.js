import { SmsVault } from 'capacitor-sms-vault';
import { addLog } from './logger.js';
import { toast, renderSmsCard } from './ui.js';
import { escapeHtml } from './helpers.js';

export function initSmsList() {
  const container = document.getElementById('tab-smslist');
  if (!container) return;

  container.innerHTML = `
    <div class="card">
      <div class="card-header">🔧 Filtres — getSmsList()</div>
      <div class="card-body">

        <!-- address -->
        <div class="field">
          <label class="field-label" for="filterAddress">address</label>
          <input
            type="text"
            id="filterAddress"
            class="field-input"
            placeholder="+33612345678 ou partiel…"
            autocomplete="off"
            autocorrect="off"
            spellcheck="false"
          />
        </div>

        <!-- since -->
        <div class="toggle-row">
          <span class="toggle-label">since (activer le filtre de date)</span>
          <label class="toggle">
            <input type="checkbox" id="filterSinceToggle" />
            <span class="toggle-track"></span>
            <span class="toggle-thumb"></span>
          </label>
        </div>
        <div class="field" id="filterSinceField" style="display:none">
          <input
            type="datetime-local"
            id="filterSince"
            class="field-input"
          />
        </div>

        <!-- limit -->
        <div class="field">
          <label class="field-label" for="filterLimit">limit</label>
          <div class="range-wrap">
            <input type="range" id="filterLimit" min="1" max="200" value="20" />
            <span class="range-value" id="filterLimitValue">20</span>
          </div>
        </div>

        <!-- type -->
        <div class="field">
          <label class="field-label">type</label>
          <div class="radio-group">
            <div class="radio-btn selected" data-type="all">Tous</div>
            <div class="radio-btn" data-type="inbox">📥 Inbox</div>
            <div class="radio-btn" data-type="sent">📤 Sent</div>
          </div>
        </div>

        <!-- raw mode -->
        <div class="toggle-row" style="margin-top:10px; margin-bottom:15px">
          <span class="toggle-label">🔎 Mode brut (getRawSmsList)</span>
          <label class="toggle">
            <input type="checkbox" id="filterRawToggle" />
            <span class="toggle-track"></span>
            <span class="toggle-thumb"></span>
          </label>
        </div>

        <!-- Filtres Avancés Accordéon -->
        <details style="margin-top: 10px; margin-bottom: 15px;">
          <summary style="font-size: 0.85rem; font-weight: 700; color: var(--accent); margin-bottom: 6px; cursor: pointer;">⚙️ Filtres avancés &amp; Projections</summary>
          <div style="padding: 8px 0 0 0; display: flex; flex-direction: column; gap: 10px;">
            
            <!-- id & threadId -->
            <div style="display:flex; gap:8px">
              <div class="field" style="flex:1; margin-bottom:0">
                <label class="field-label" style="font-size:0.65rem">id exact</label>
                <input type="text" id="filterId" class="field-input" style="padding:6px 8px; font-size:0.8rem;" placeholder="id..." />
              </div>
              <div class="field" style="flex:1; margin-bottom:0">
                <label class="field-label" style="font-size:0.65rem">threadId exact</label>
                <input type="text" id="filterThreadId" class="field-input" style="padding:6px 8px; font-size:0.8rem;" placeholder="threadId..." />
              </div>
            </div>

            <!-- addressRegex -->
            <div class="field" style="margin-bottom:0">
              <label class="field-label" style="font-size:0.65rem">address (Regex)</label>
              <input type="text" id="filterAddressRegex" class="field-input" style="padding:6px 8px; font-size:0.8rem;" placeholder="ex: ^\\\\+33.*" />
            </div>

            <!-- body (search) & bodyRegex -->
            <div style="display:flex; gap:8px">
              <div class="field" style="flex:1; margin-bottom:0">
                <label class="field-label" style="font-size:0.65rem">body (partiel)</label>
                <input type="text" id="filterBody" class="field-input" style="padding:6px 8px; font-size:0.8rem" placeholder="texte..." />
              </div>
              <div class="field" style="flex:1; margin-bottom:0">
                <label class="field-label" style="font-size:0.65rem">body (Regex)</label>
                <input type="text" id="filterBodyRegex" class="field-input" style="padding:6px 8px; font-size:0.8rem;" placeholder="ex: .*code.*" />
              </div>
            </div>

            <!-- read & seen & orderBy -->
            <div style="display:flex; gap:8px">
              <div class="field" style="flex:1; margin-bottom:0">
                <label class="field-label" style="font-size:0.65rem">Statut Lu</label>
                <select id="filterRead" class="field-input" style="padding:6px 8px; font-size:0.8rem; height: auto;">
                  <option value="all">Tous</option>
                  <option value="read">Lus uniquement</option>
                  <option value="unread">Non lus uniquement</option>
                </select>
              </div>
              <div class="field" style="flex:1; margin-bottom:0">
                <label class="field-label" style="font-size:0.65rem">Statut Vu</label>
                <select id="filterSeen" class="field-input" style="padding:6px 8px; font-size:0.8rem; height: auto;">
                  <option value="all">Tous</option>
                  <option value="seen">Vus uniquement</option>
                  <option value="unseen">Non vus uniquement</option>
                </select>
              </div>
            </div>

            <div style="display:flex; gap:8px">
              <div class="field" style="flex:1; margin-bottom:0">
                <label class="field-label" style="font-size:0.65rem">Tri par date</label>
                <select id="filterOrderBy" class="field-input" style="padding:6px 8px; font-size:0.8rem; height: auto;">
                  <option value="date_desc">Décroissant (Défaut)</option>
                  <option value="date_asc">Croissant</option>
                </select>
              </div>
              <div class="field" style="flex:1; margin-bottom:0">
                <label class="field-label" style="font-size:0.65rem">Offset (indexFrom)</label>
                <input type="number" id="filterIndexFrom" class="field-input" style="padding:6px 8px; font-size:0.8rem" value="0" min="0" />
              </div>
            </div>

            <!-- Projections -->
            <div style="margin-top:4px">
              <span class="field-label" style="font-size:0.65rem; margin-bottom:4px">Projection (champs requis)</span>
              <div style="display:flex; flex-wrap:wrap; gap:10px; font-size:0.82rem; background:var(--surface2); padding:8px; border-radius:var(--radius-sm); border: 1px solid var(--border);">
                <label style="display:flex; align-items:center; gap:4px; cursor:pointer;"><input type="checkbox" id="proj_threadId" checked /> threadId</label>
                <label style="display:flex; align-items:center; gap:4px; cursor:pointer;"><input type="checkbox" id="proj_subject" checked /> subject</label>
                <label style="display:flex; align-items:center; gap:4px; cursor:pointer;"><input type="checkbox" id="proj_dateSent" checked /> dateSent</label>
                <label style="display:flex; align-items:center; gap:4px; cursor:pointer;"><input type="checkbox" id="proj_creator" checked /> creator</label>
                <label style="display:flex; align-items:center; gap:4px; cursor:pointer;"><input type="checkbox" id="proj_person" checked /> person</label>
                <label style="display:flex; align-items:center; gap:4px; cursor:pointer;"><input type="checkbox" id="proj_seen" checked /> seen</label>
                <label style="display:flex; align-items:center; gap:4px; cursor:pointer;"><input type="checkbox" id="proj_status" checked /> status</label>
                <label style="display:flex; align-items:center; gap:4px; cursor:pointer;"><input type="checkbox" id="proj_locked" checked /> locked</label>
              </div>
            </div>

          </div>
        </details>

        <hr class="divider" />

        <div style="display:flex;gap:8px;margin-bottom:8px">
          <button class="btn btn-ghost btn-sm" id="btnResetFilters" style="flex:1">
            ↺ Réinitialiser
          </button>
          <button class="btn btn-ghost btn-sm" id="btnGetCount" style="flex:1">
            📊 Compter
          </button>
        </div>
        <button class="btn btn-primary" id="btnGetSmsList" style="width:100%">
          🔍 Rechercher
        </button>
      </div>
    </div>

    <!-- Résultats -->
    <div id="smsResults">
      <div class="empty-state">
        <div class="empty-icon">📬</div>
        Appliquez des filtres et lancez la recherche
      </div>
    </div>
  `;

  // — Slider limit
  const filterLimit = document.getElementById('filterLimit');
  const filterLimitValue = document.getElementById('filterLimitValue');
  if (filterLimit && filterLimitValue) {
    filterLimit.addEventListener('input', () => {
      filterLimitValue.textContent = filterLimit.value;
    });
  }

  // — Since toggle
  const filterSinceToggle = document.getElementById('filterSinceToggle');
  const filterSinceField = document.getElementById('filterSinceField');
  if (filterSinceToggle && filterSinceField) {
    filterSinceToggle.addEventListener('change', () => {
      filterSinceField.style.display = filterSinceToggle.checked ? 'block' : 'none';
      const sinceInput = document.getElementById('filterSince');
      if (filterSinceToggle.checked && sinceInput && !sinceInput.value) {
        const d = new Date(Date.now() - 7 * 24 * 3600 * 1000);
        sinceInput.value = d.toISOString().slice(0, 16);
      }
    });
  }

  // — Type radio
  let selectedType = 'all';
  document.querySelectorAll('.radio-btn[data-type]').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.radio-btn[data-type]').forEach(b => b.classList.remove('selected'));
      btn.classList.add('selected');
      selectedType = btn.dataset.type;
    });
  });

  // Helper pour récupérer les filtres actifs
  function getActiveFilters() {
    const filter = {};
    const addressInput = document.getElementById('filterAddress');
    const address = addressInput ? addressInput.value.trim() : '';
    if (address) filter.address = address;

    if (filterSinceToggle && filterSinceToggle.checked) {
      const sinceInput = document.getElementById('filterSince');
      const sinceVal = sinceInput ? sinceInput.value : '';
      if (sinceVal) filter.minDate = new Date(sinceVal).getTime();
    }

    if (filterLimit) {
      filter.maxCount = parseInt(filterLimit.value, 10);
    } else {
      filter.maxCount = 20;
    }

    if (selectedType === 'all') {
      filter.type = 0; // MessageType.ALL
    } else if (selectedType === 'inbox') {
      filter.type = 1; // MessageType.INBOX
    } else if (selectedType === 'sent') {
      filter.type = 2; // MessageType.SENT
    }

    // Filtres avancés
    const idVal = document.getElementById('filterId')?.value.trim();
    if (idVal) filter.id = idVal;

    const threadIdVal = document.getElementById('filterThreadId')?.value.trim();
    if (threadIdVal) filter.threadId = threadIdVal;

    const addressRegexVal = document.getElementById('filterAddressRegex')?.value.trim();
    if (addressRegexVal) filter.addressRegex = addressRegexVal;

    const bodyVal = document.getElementById('filterBody')?.value.trim();
    if (bodyVal) filter.body = bodyVal;

    const bodyRegexVal = document.getElementById('filterBodyRegex')?.value.trim();
    if (bodyRegexVal) filter.bodyRegex = bodyRegexVal;

    const readSelect = document.getElementById('filterRead');
    if (readSelect && readSelect.value !== 'all') {
      filter.read = readSelect.value === 'read' ? 1 : 0;
    }

    const seenSelect = document.getElementById('filterSeen');
    if (seenSelect && seenSelect.value !== 'all') {
      filter.seen = seenSelect.value === 'seen' ? 1 : 0;
    }

    const orderBySelect = document.getElementById('filterOrderBy');
    if (orderBySelect && orderBySelect.value) {
      filter.orderBy = orderBySelect.value;
    }

    const indexFromVal = document.getElementById('filterIndexFrom')?.value;
    if (indexFromVal) {
      filter.indexFrom = parseInt(indexFromVal, 10) || 0;
    }

    return filter;
  }

  // Helper pour récupérer la projection
  function getActiveProjection() {
    const projection = {};
    const keys = ['threadId', 'subject', 'dateSent', 'creator', 'person', 'seen', 'status', 'locked'];
    let hasAny = false;
    keys.forEach(k => {
      const checkbox = document.getElementById(`proj_${k}`);
      if (checkbox) {
        projection[k] = checkbox.checked;
        hasAny = true;
      }
    });
    return hasAny ? projection : undefined;
  }

  // — Reset
  document.getElementById('btnResetFilters').addEventListener('click', () => {
    const addressInput = document.getElementById('filterAddress');
    if (addressInput) addressInput.value = '';
    
    if (filterSinceToggle) filterSinceToggle.checked = false;
    if (filterSinceField) filterSinceField.style.display = 'none';
    if (filterLimit) {
      filterLimit.value = 20;
      if (filterLimitValue) filterLimitValue.textContent = '20';
    }
    
    const filterRawToggle = document.getElementById('filterRawToggle');
    if (filterRawToggle) filterRawToggle.checked = false;

    // Reset des inputs avancés
    const fieldsToReset = ['filterId', 'filterThreadId', 'filterAddressRegex', 'filterBody', 'filterBodyRegex', 'filterIndexFrom'];
    fieldsToReset.forEach(id => {
      const el = document.getElementById(id);
      if (el) el.value = id === 'filterIndexFrom' ? '0' : '';
    });
    const selectsToReset = ['filterRead', 'filterSeen', 'filterOrderBy'];
    selectsToReset.forEach(id => {
      const el = document.getElementById(id);
      if (el) el.value = id === 'filterOrderBy' ? 'date_desc' : 'all';
    });
    const projectionsToReset = ['threadId', 'subject', 'dateSent', 'creator', 'person', 'seen', 'status', 'locked'];
    projectionsToReset.forEach(k => {
      const checkbox = document.getElementById(`proj_${k}`);
      if (checkbox) checkbox.checked = true;
    });

    selectedType = 'all';
    document.querySelectorAll('.radio-btn[data-type]').forEach(b => b.classList.remove('selected'));
    const allBtn = document.querySelector('.radio-btn[data-type="all"]');
    if (allBtn) allBtn.classList.add('selected');

    const resultsContainer = document.getElementById('smsResults');
    if (resultsContainer) {
      resultsContainer.innerHTML = `
        <div class="empty-state">
          <div class="empty-icon">📬</div>
          Filtres réinitialisés — lancez une nouvelle recherche
        </div>`;
    }
  });

  // — Compter (getCount)
  const btnGetCount = document.getElementById('btnGetCount');
  if (btnGetCount) {
    btnGetCount.addEventListener('click', async () => {
      btnGetCount.disabled = true;
      btnGetCount.innerHTML = '<span class="spinner"></span>...';

      const filter = getActiveFilters();
      const options = { filter };

      try {
        const { count } = await SmsVault.getCount(options);
        addLog('📊', 'getCount', options, `Nombre de SMS : ${count}`);
        toast(`Nombre de SMS correspondant : ${count}`, 'success');
      } catch (e) {
        addLog('📊', 'getCount', options, e.message, 'err');
        toast(`Erreur : ${e.message}`, 'error');
      } finally {
        btnGetCount.disabled = false;
        btnGetCount.innerHTML = '📊 Compter';
      }
    });
  }

  // — Rechercher (getSmsList ou getRawSmsList)
  const btnGetSmsList = document.getElementById('btnGetSmsList');
  if (btnGetSmsList) {
    btnGetSmsList.addEventListener('click', async () => {
      btnGetSmsList.disabled = true;
      btnGetSmsList.innerHTML = '<span class="spinner"></span> Chargement…';

      const filter = getActiveFilters();
      const projection = getActiveProjection();
      const options = { filter };
      if (projection) options.projection = projection;

      const filterRawToggle = document.getElementById('filterRawToggle');
      const isRawMode = filterRawToggle ? filterRawToggle.checked : false;

      try {
        if (isRawMode) {
          // Appel getRawSmsList
          let { messages } = await SmsVault.getRawSmsList(options);
          addLog('🔍', 'getRawSmsList', options, `${messages.length} messages bruts`);
          
          // Transformer en SmsMessage factices pour le rendu visuel simple
          const dummyMessages = messages.map(rawSms => ({
            id: rawSms._id || rawSms.id || '',
            address: rawSms.address || '(brut)',
            body: rawSms.body || JSON.stringify(rawSms),
            date: parseInt(rawSms.date, 10) || Date.now(),
            type: parseInt(rawSms.type, 10) || 1,
            locked: parseInt(rawSms.locked, 10) === 1
          }));

          renderSmsResults(dummyMessages, options, JSON.stringify({ raw_messages: messages }, null, 2));
          toast(`${messages.length} SMS bruts chargés`, 'success');
        } else {
          // Appel getSmsList classique
          let { messages } = await SmsVault.getSmsList(options);
          addLog('🔍', 'getSmsList', options, `${messages.length} messages`);
          renderSmsResults(messages, options);
          toast(`${messages.length} SMS chargés`, 'success');
        }
      } catch (e) {
        addLog('🔍', isRawMode ? 'getRawSmsList' : 'getSmsList', options, e.message, 'err');
        const resultsContainer = document.getElementById('smsResults');
        if (resultsContainer) {
          resultsContainer.innerHTML = `
            <div class="empty-state" style="color:#f87171">
              <div class="empty-icon">❌</div>
              ${escapeHtml(e.message)}
            </div>`;
        }
        toast(e.message, 'error');
      } finally {
        btnGetSmsList.disabled = false;
        btnGetSmsList.innerHTML = '🔍 Rechercher';
      }
    });
  }
}

function renderSmsResults(messages, options, rawJsonOverride = null) {
  const container = document.getElementById('smsResults');
  if (!container) return;

  if (messages.length === 0) {
    container.innerHTML = `
      <div class="empty-state">
        <div class="empty-icon">🔍</div>
        Aucun SMS trouvé avec ces filtres
      </div>`;
    return;
  }

  const jsonRaw = rawJsonOverride || JSON.stringify({ messages }, null, 2);

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
