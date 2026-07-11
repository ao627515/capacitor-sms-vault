import { SmsVault } from 'capacitor-sms-vault';
import { addLog } from './logger.js';
import { escapeHtml, formatDate, formatShortTime, mapTypeToString } from './helpers.js';

export function toast(message, type = 'info') {
  const container = document.getElementById('toastContainer');
  if (!container) return;
  const el = document.createElement('div');
  el.className = `toast toast-${type}`;
  el.textContent = message;
  container.appendChild(el);
  setTimeout(() => el.remove(), 2700);
}

export function setBadge(id, value) {
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

export function renderSmsCard(sms, isNew = false) {
  const card = document.createElement('div');
  card.className = 'sms-card';
  const typeStr = typeof sms.type === 'number' ? mapTypeToString(sms.type) : (sms.type || 'inbox');
  
  // Construit le code HTML initial de la carte avec les nouveaux boutons.
  card.innerHTML = `
    <div class="sms-card-head">
      <span class="sms-address">${escapeHtml(sms.address || '(inconnu)')}</span>
      <span class="sms-type-badge ${typeStr}">${typeStr}</span>
      ${sms.id ? `<span class="sms-id">id:${escapeHtml(sms.id)}</span>` : ''}
    </div>
    <div class="sms-body">${escapeHtml(sms.body || '(vide)')}</div>
    <div class="sms-date">
      <span class="sms-lock-indicator">${sms.locked ? '🔒 ' : ''}</span>
      ${isNew ? '🟢 ' + formatShortTime(sms.date) : formatDate(sms.date)}
    </div>
    ${sms.id ? `
      <div class="sms-actions" style="margin-top:8px; display:flex; gap:8px; flex-wrap:wrap;">
        <button class="btn btn-ghost btn-xs btn-mark-seen" data-id="${escapeHtml(sms.id)}">👁 Vu</button>
        <button class="btn btn-ghost btn-xs btn-mark-read" data-id="${escapeHtml(sms.id)}">👁 Lu</button>
        <button class="btn btn-ghost btn-xs btn-toggle-lock" data-id="${escapeHtml(sms.id)}">
          ${sms.locked ? '🔓 Déverr.' : '🔒 Verr.'}
        </button>
        <button class="btn btn-danger-ghost btn-xs btn-delete" data-id="${escapeHtml(sms.id)}">🗑 Suppr.</button>
      </div>
    ` : ''}
  `;

  if (sms.id) {
    // Bouton : marquer comme vu.
    card.querySelector('.btn-mark-seen').addEventListener('click', async (e) => {
      e.stopPropagation();
      const btn = card.querySelector('.btn-mark-seen');
      btn.disabled = true;
      try {
        await SmsVault.markAsSeen({ id: sms.id });
        addLog('👁', 'markAsSeen', { id: sms.id }, 'Succès');
        toast('SMS marqué comme vu', 'success');
        btn.remove();
      } catch (err) {
        addLog('👁', 'markAsSeen', { id: sms.id }, err.message, 'err');
        toast(`Échec : ${err.message}`, 'error');
        btn.disabled = false;
      }
    });

    // Bouton : marquer comme lu.
    card.querySelector('.btn-mark-read').addEventListener('click', async (e) => {
      e.stopPropagation();
      const btn = card.querySelector('.btn-mark-read');
      btn.disabled = true;
      try {
        await SmsVault.markAsRead({ id: sms.id });
        addLog('👁', 'markAsRead', { id: sms.id }, 'Succès');
        toast('SMS marqué comme lu', 'success');
        btn.remove();
      } catch (err) {
        addLog('👁', 'markAsRead', { id: sms.id }, err.message, 'err');
        toast(`Échec : ${err.message}`, 'error');
        btn.disabled = false;
      }
    });

    // Bouton : basculer le verrouillage (setSmsLocked).
    card.querySelector('.btn-toggle-lock').addEventListener('click', async (e) => {
      e.stopPropagation();
      const btn = card.querySelector('.btn-toggle-lock');
      btn.disabled = true;
      const targetLockState = !sms.locked;
      try {
        await SmsVault.setSmsLocked({ id: sms.id, locked: targetLockState });
        sms.locked = targetLockState;
        addLog('🔒', 'setSmsLocked', { id: sms.id, locked: targetLockState }, 'Succès');
        toast(targetLockState ? 'SMS verrouillé ✓' : 'SMS déverrouillé ✓', 'success');
        
        btn.textContent = targetLockState ? '🔓 Déverr.' : '🔒 Verr.';
        const lockIndicator = card.querySelector('.sms-lock-indicator');
        if (lockIndicator) {
          lockIndicator.textContent = targetLockState ? '🔒 ' : '';
        }
      } catch (err) {
        addLog('🔒', 'setSmsLocked', { id: sms.id, locked: targetLockState }, err.message, 'err');
        toast(`Échec : ${err.message}`, 'error');
      } finally {
        btn.disabled = false;
      }
    });

    // Bouton : supprimer (gère force: true pour les SMS verrouillés).
    card.querySelector('.btn-delete').addEventListener('click', async (e) => {
      e.stopPropagation();
      const btn = card.querySelector('.btn-delete');
      btn.disabled = true;

      let force = false;
      let promptMessage = 'Supprimer ce message ?';
      if (sms.locked) {
        promptMessage = 'Ce message est VERROUILLÉ. Voulez-vous FORCER sa suppression ?';
        force = true;
      }

      if (!confirm(promptMessage)) {
        btn.disabled = false;
        return;
      }

      try {
        const result = await SmsVault.deleteSms({ id: sms.id, force });
        addLog('🗑', 'deleteSms', { id: sms.id, force }, `Succès: ${JSON.stringify(result)}`);
        toast('SMS supprimé', 'success');
        card.style.transition = 'opacity 0.3s';
        card.style.opacity = '0';
        setTimeout(() => card.remove(), 300);
      } catch (err) {
        addLog('🗑', 'deleteSms', { id: sms.id, force }, err.message, 'err');
        toast(`Échec : ${err.message}`, 'error');
        btn.disabled = false;
      }
    });
  }

  return card;
}
