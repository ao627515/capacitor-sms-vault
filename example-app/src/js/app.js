import { SmsVault } from 'capacitor-sms-vault';

// ─────────────────────────────────────────────────────────────────────────────
// Utilitaires UI
// ─────────────────────────────────────────────────────────────────────────────

function setBadge(id, value) {
  const el = document.getElementById(id);
  if (!el) return;
  el.textContent = value;
  el.className = 'status-badge ' + (
    value === 'granted' ? 'granted' :
    value === 'denied'  ? 'denied'  :
    value === 'Oui' || value === 'true' ? 'granted' :
    value === 'Non' || value === 'false' ? 'denied'  : 'prompt'
  );
}

function renderSmsList(containerId, messages) {
  const container = document.getElementById(containerId);
  if (!container) return;

  if (!messages || messages.length === 0) {
    container.innerHTML = '<p class="empty-state">Aucun SMS trouvé</p>';
    return;
  }

  container.innerHTML = messages.map(sms => `
    <ion-item>
      <ion-label class="ion-text-wrap">
        <p class="sms-address">${escapeHtml(sms.address || '(inconnu)')}</p>
        <p class="sms-body">${escapeHtml((sms.body || '').substring(0, 120))}${sms.body && sms.body.length > 120 ? '…' : ''}</p>
        <p class="sms-date">${new Date(sms.date).toLocaleString('fr-FR')}</p>
      </ion-label>
      <ion-badge slot="end" color="${sms.type === 'sent' ? 'medium' : 'primary'}">${sms.type}</ion-badge>
    </ion-item>
  `).join('');
}

function prependLiveSms(sms) {
  const container = document.getElementById('liveContainer');
  if (!container) return;

  // Supprimer le message "vide" si présent
  const empty = container.querySelector('.empty-state');
  if (empty) empty.remove();

  const item = document.createElement('ion-item');
  item.innerHTML = `
    <ion-label class="ion-text-wrap">
      <p class="sms-address"><span class="live-badge"></span>${escapeHtml(sms.address || '(inconnu)')}</p>
      <p class="sms-body">${escapeHtml(sms.body || '')}</p>
      <p class="sms-date">${new Date(sms.date).toLocaleString('fr-FR')}</p>
    </ion-label>
    <ion-badge slot="end" color="success">nouveau</ion-badge>
  `;
  container.prepend(item);
}

function escapeHtml(str) {
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

async function showToast(message, color = 'dark') {
  const toast = document.createElement('ion-toast');
  toast.message = message;
  toast.duration = 2500;
  toast.color = color;
  toast.position = 'bottom';
  document.body.appendChild(toast);
  await toast.present();
}

// ─────────────────────────────────────────────────────────────────────────────
// F1 + F2 : Permissions & Default SMS App
// ─────────────────────────────────────────────────────────────────────────────

document.getElementById('btnCheckPerms').addEventListener('click', async () => {
  try {
    const status = await SmsVault.checkPermissions();
    setBadge('statusReadSms', status.readSms);
    setBadge('statusReceiveSms', status.receiveSms);

    const { isDefault } = await SmsVault.isDefaultSmsApp();
    setBadge('statusDefault', isDefault ? 'Oui' : 'Non');
  } catch (e) {
    await showToast('Erreur : ' + e.message, 'danger');
  }
});

document.getElementById('btnRequestPerms').addEventListener('click', async () => {
  try {
    const status = await SmsVault.requestPermissions();
    setBadge('statusReadSms', status.readSms);
    setBadge('statusReceiveSms', status.receiveSms);
    await showToast('Permissions mises à jour', 'success');
  } catch (e) {
    await showToast('Erreur : ' + e.message, 'danger');
  }
});

document.getElementById('btnRequestDefault').addEventListener('click', async () => {
  try {
    const { isDefault } = await SmsVault.requestDefaultSmsApp();
    setBadge('statusDefault', isDefault ? 'Oui' : 'Non');
    await showToast(
      isDefault ? 'App définie comme SMS par défaut ✓' : 'Changement annulé',
      isDefault ? 'success' : 'warning'
    );
  } catch (e) {
    await showToast('Erreur : ' + e.message, 'danger');
  }
});

// ─────────────────────────────────────────────────────────────────────────────
// F3 : Historique SMS
// ─────────────────────────────────────────────────────────────────────────────

document.getElementById('btnReadSms').addEventListener('click', async () => {
  const limitEl = document.getElementById('smsLimit');
  const limit = limitEl ? parseInt(limitEl.value, 10) : 10;
  const container = document.getElementById('smsListContainer');

  if (container) {
    container.innerHTML = '<ion-item><ion-label>Chargement…</ion-label><ion-spinner slot="end"></ion-spinner></ion-item>';
  }

  try {
    const { messages } = await SmsVault.getSmsList({ limit });
    renderSmsList('smsListContainer', messages);
    await showToast(`${messages.length} SMS chargés`, 'success');
  } catch (e) {
    if (container) {
      container.innerHTML = `<p class="empty-state" style="color: var(--ion-color-danger)">Erreur : ${escapeHtml(e.message)}</p>`;
    }
    await showToast('Erreur lecture SMS : ' + e.message, 'danger');
  }
});

// ─────────────────────────────────────────────────────────────────────────────
// F4 : Écoute temps réel
// ─────────────────────────────────────────────────────────────────────────────

let liveListener = null;

document.getElementById('btnStartListen').addEventListener('click', async () => {
  if (liveListener) {
    await showToast('Déjà en cours d\'écoute', 'warning');
    return;
  }

  try {
    // Enregistrer le listener AVANT startListening pour ne pas manquer de SMS
    liveListener = await SmsVault.addListener('smsReceived', (sms) => {
      prependLiveSms(sms);
    });

    await SmsVault.startListening();

    const statusEl = document.getElementById('listenStatus');
    if (statusEl) {
      statusEl.innerHTML = '<span class="live-badge"></span> Écoute active…';
    }

    await showToast('Écoute SMS démarrée', 'success');
  } catch (e) {
    // Nettoyer le listener si startListening a échoué
    if (liveListener) {
      await liveListener.remove();
      liveListener = null;
    }
    await showToast('Erreur : ' + e.message, 'danger');
  }
});

document.getElementById('btnStopListen').addEventListener('click', async () => {
  try {
    await SmsVault.stopListening();

    if (liveListener) {
      await liveListener.remove();
      liveListener = null;
    }

    const statusEl = document.getElementById('listenStatus');
    if (statusEl) {
      statusEl.textContent = '⏸ Écoute arrêtée';
    }

    await showToast('Écoute SMS arrêtée', 'medium');
  } catch (e) {
    await showToast('Erreur : ' + e.message, 'danger');
  }
});
