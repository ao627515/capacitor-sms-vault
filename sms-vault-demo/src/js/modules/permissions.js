import { SmsVault } from 'capacitor-sms-vault';
import { addLog } from './logger.js';
import { setBadge, toast } from './ui.js';
import { escapeHtml, timestamp } from './helpers.js';

export async function refreshPermissionBadges() {
  const status = await SmsVault.checkPermissions();
  setBadge('statusReadSms', status.readSms);
  setBadge('statusReceiveSms', status.receiveSms);
  setBadge('statusPhone', status.phone);
  setBadge('statusSendSms', status.sendSms);
  return status;
}

export async function refreshDefaultBadge() {
  const { isDefault } = await SmsVault.isDefaultSmsApp();
  setBadge('statusDefault', isDefault ? 'Oui' : 'Non');
  return isDefault;
}

export function initPermissions() {
  const container = document.getElementById('tab-permissions');
  if (!container) return;

  container.innerHTML = `
    <div class="card">
      <div class="card-header">🔍 Statut actuel</div>
      <div class="card-body">
        <div class="status-row">
          <span class="status-label">READ_SMS</span>
          <span class="badge badge-unknown" id="statusReadSms">?</span>
        </div>
        <div class="status-row">
          <span class="status-label">RECEIVE_SMS</span>
          <span class="badge badge-unknown" id="statusReceiveSms">?</span>
        </div>
        <div class="status-row">
          <span class="status-label">READ_PHONE_STATE (SIM)</span>
          <span class="badge badge-unknown" id="statusPhone">?</span>
        </div>
        <div class="status-row">
          <span class="status-label">SEND_SMS (Envoi direct)</span>
          <span class="badge badge-unknown" id="statusSendSms">?</span>
        </div>
        <div class="status-row">
          <span class="status-label">App SMS par défaut</span>
          <span class="badge badge-unknown" id="statusDefault">?</span>
        </div>
      </div>
    </div>

    <div class="card">
      <div class="card-header">🔐 Permissions SMS</div>
      <div class="card-body">
        <button class="btn btn-ghost" id="btnCheckPerms">
          🔍 checkPermissions()
        </button>
        <button class="btn btn-primary" id="btnRequestPerms">
          🔑 requestPermissions()
        </button>
      </div>
    </div>

    <div class="card">
      <div class="card-header">📱 App SMS par défaut</div>
      <div class="card-body">
        <button class="btn btn-ghost" id="btnIsDefault">
          ❓ isDefaultSmsApp()
        </button>
        <button class="btn btn-outline" id="btnRequestDefault">
          ⭐ requestDefaultSmsApp()
        </button>
      </div>
    </div>

    <div class="card">
      <div class="card-header">📱 Informations SIM</div>
      <div class="card-body">
        <p style="font-size: 0.82rem; color: var(--text-muted); margin-bottom: 12px; line-height: 1.4;">
          Récupère les informations des cartes SIM actives de l'appareil (opérateur, pays, slot et numéro de ligne si disponible).
        </p>
        <button class="btn btn-ghost" id="btnGetSimInfo">
          📱 getSimInfo()
        </button>
        <div id="simInfoResult" style="margin-top: 10px; font-size: 0.85rem;"></div>
      </div>
    </div>

    <div class="card">
      <div class="card-header">📡 Profil SIM &amp; Changements</div>
      <div class="card-body">
        <p style="font-size: 0.82rem; color: var(--text-muted); margin-bottom: 12px; line-height: 1.4;">
          Récupère le profil SIM complet (dual SIM et codes MCC/MNC) ou surveille les changements à chaud.
        </p>
        <div style="display:flex;gap:8px;margin-bottom:10px">
          <button class="btn btn-ghost" id="btnGetSimProfile" style="flex:1">
            📋 getDeviceSimProfile()
          </button>
          <button class="btn btn-outline" id="btnToggleSimMonitor" style="flex:1">
            ▶ Monitor SIM
          </button>
        </div>
        <div class="live-indicator" id="simMonitorIndicator" style="display:none;margin-bottom:10px">
          <div class="live-dot" id="simMonitorDot"></div>
          <span class="live-status" id="simMonitorStatus">Surveillance inactive</span>
        </div>
        <div id="simProfileResult" style="margin-top: 10px; font-size: 0.85rem;"></div>
      </div>
    </div>

    <!-- Note Sideload / Restricted Settings -->
    <div class="card" style="border-color: var(--yellow);">
      <div class="card-header" style="color: var(--yellow); border-bottom-color: var(--border);">
        ⚠️ Restriction Android 13+ (Sideload)
      </div>
      <div class="card-body" style="font-size: 0.85rem; color: var(--text-muted); line-height: 1.45;">
        <p style="margin-bottom: 8px;">Si vous installez cette application via un APK direct (sideload), Android bloque l'accès aux permissions sensibles par défaut.</p>
        <p style="font-weight: 700; color: var(--yellow); margin-bottom: 6px;">Procédure de déblocage :</p>
        <ol style="margin-left: 18px; margin-bottom: 8px; display: flex; flex-direction: column; gap: 4px;">
          <li>Faites un <strong>appui long</strong> sur l'icône de l'app sur votre écran d'accueil.</li>
          <li>Sélectionnez <strong>Infos sur l'appli</strong> (App info).</li>
          <li>Appuyez sur le menu **⋮** (trois points) en haut à droite.</li>
          <li>Sélectionnez <strong>Autoriser les paramètres restreints</strong> (Allow restricted settings) et validez.</li>
          <li>Allez maintenant dans le menu <strong>Autorisations / Permissions → SMS</strong> et passez sur <strong>Autoriser</strong>.</li>
        </ol>
        <p style="font-style: italic; font-size: 0.75rem; margin-top: 8px; color: var(--text-dim);">Ce paramètre se réinitialise à chaque réinstallation de l'APK de test.</p>
      </div>
    </div>
  `;

  // checkPermissions
  document.getElementById('btnCheckPerms').addEventListener('click', async () => {
    try {
      const status = await refreshPermissionBadges();
      const isDefault = await refreshDefaultBadge();
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
      setBadge('statusSendSms', status.sendSms);
      addLog('🔑', 'requestPermissions', {}, JSON.stringify(status));
      toast('Permissions : ' + status.readSms + ' / ' + status.receiveSms + ' / ' + status.phone + ' / ' + status.sendSms,
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
    if (!resultDiv) return;
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

  // getDeviceSimProfile
  document.getElementById('btnGetSimProfile').addEventListener('click', async () => {
    const resultDiv = document.getElementById('simProfileResult');
    if (!resultDiv) return;
    resultDiv.innerHTML = '<span class="spinner"></span> Chargement…';
    try {
      const profile = await SmsVault.getDeviceSimProfile();
      addLog('📋', 'getDeviceSimProfile', {}, `dualSim: ${profile.dualSim}, ${profile.sims.length} SIMs`);
      
      if (profile.sims.length === 0) {
        resultDiv.innerHTML = '<div style="color: var(--yellow)">Aucun profil SIM actif détecté.</div>';
        return;
      }

      resultDiv.innerHTML = `
        <div style="margin-bottom: 8px; font-weight: bold; color: var(--green);">
          Dual SIM : ${profile.dualSim ? 'Oui' : 'Non'}
        </div>
      ` + profile.sims.map(sim => `
        <div style="background: var(--surface2); border: 1px solid var(--border); padding: 8px; border-radius: var(--radius-sm); margin-bottom: 6px; text-align: left;">
          <strong>Opérateur :</strong> ${escapeHtml(sim.carrierName)}<br>
          <strong>Pays :</strong> ${escapeHtml(sim.countryIso).toUpperCase()}<br>
          <strong>MCC / MNC :</strong> ${sim.mcc ? sim.mcc : '?'}/${sim.mnc ? sim.mnc : '?'}<br>
          <strong>Slot :</strong> ${sim.simSlotIndex}<br>
          <strong>ID Abonnement :</strong> ${sim.subscriptionId}<br>
          <strong>Numéro :</strong> ${sim.phoneNumber ? escapeHtml(sim.phoneNumber) : '<span style="color: var(--text-dim); font-style: italic;">non disponible</span>'}
        </div>
      `).join('');
    } catch (e) {
      addLog('📋', 'getDeviceSimProfile', {}, e.message, 'err');
      resultDiv.innerHTML = `<div style="color: var(--red)">Erreur: ${escapeHtml(e.message)}</div>`;
      toast(e.message, 'error');
    }
  });

  // SIM Monitor
  let simMonitorListener = null;
  let isSimMonitoring = false;

  function updateSimMonitorUI(active) {
    isSimMonitoring = active;
    const indicator = document.getElementById('simMonitorIndicator');
    const status = document.getElementById('simMonitorStatus');
    const dot = document.getElementById('simMonitorDot');
    const btn = document.getElementById('btnToggleSimMonitor');

    if (!indicator || !status || !dot || !btn) return;

    if (active) {
      indicator.style.display = 'flex';
      dot.classList.add('active');
      status.textContent = 'Surveillance active (écoute changement SIM)…';
      status.style.color = 'var(--green)';
      btn.className = 'btn btn-danger';
      btn.textContent = '⏹ Stop Monitoring';
    } else {
      indicator.style.display = 'none';
      dot.classList.remove('active');
      status.textContent = 'Surveillance inactive';
      status.style.color = '';
      btn.className = 'btn btn-outline';
      btn.textContent = '▶ Monitor SIM';
    }
  }

  document.getElementById('btnToggleSimMonitor').addEventListener('click', async () => {
    try {
      if (!isSimMonitoring) {
        simMonitorListener = await SmsVault.addListener('simsChanged', (profile) => {
          addLog('📡', 'event:simsChanged', {}, `dualSim: ${profile.dualSim}, sims: ${profile.sims.length}`, 'event');
          toast('Changement de carte SIM détecté !', 'info');
          
          // Rafraîchir l'affichage
          const resultDiv = document.getElementById('simProfileResult');
          if (resultDiv) {
            resultDiv.innerHTML = `
              <div style="margin-bottom: 8px; font-weight: bold; color: var(--green);">
                Dual SIM : ${profile.dualSim ? 'Oui' : 'Non'} (Mis à jour à ${timestamp()})
              </div>
            ` + profile.sims.map(sim => `
              <div style="background: var(--surface2); border: 1px solid var(--border); padding: 8px; border-radius: var(--radius-sm); margin-bottom: 6px; text-align: left;">
                <strong>Opérateur :</strong> ${escapeHtml(sim.carrierName)}<br>
                <strong>Pays :</strong> ${escapeHtml(sim.countryIso).toUpperCase()}<br>
                <strong>MCC / MNC :</strong> ${sim.mcc}/${sim.mnc}<br>
                <strong>Slot :</strong> ${sim.simSlotIndex}<br>
                <strong>ID Abonnement :</strong> ${sim.subscriptionId}<br>
              </div>
            `).join('');
          }
        });
        
        await SmsVault.startMonitoringSimChanges();
        addLog('▶', 'startMonitoringSimChanges', {}, 'OK');
        updateSimMonitorUI(true);
        toast('Surveillance SIM démarrée', 'success');
      } else {
        await SmsVault.stopMonitoringSimChanges();
        if (simMonitorListener) {
          await simMonitorListener.remove();
          simMonitorListener = null;
        }
        addLog('⏹', 'stopMonitoringSimChanges', {}, 'OK');
        updateSimMonitorUI(false);
        toast('Surveillance SIM arrêtée', 'info');
      }
    } catch (e) {
      addLog('📡', 'MonitoringSIM', {}, e.message, 'err');
      toast(e.message, 'error');
    }
  });
}
