import { SmsVault } from 'capacitor-sms-vault';
import { addLog } from './logger.js';
import { toast } from './ui.js';
import { escapeHtml, timestamp } from './helpers.js';

export function initOtpRetriever() {
  const container = document.getElementById('tab-otp');
  if (!container) return;

  container.innerHTML = `
    <div class="card">
      <div class="card-header">🔑 SMS Retriever API</div>
      <div class="card-body">
        <p style="font-size: 0.85rem; color: var(--text-muted); margin-bottom: 12px; line-height: 1.4;">
          Cette API capte un SMS d'authentification (OTP) contenant le code de sécurité et la clé de hachage de l'app <strong>sans aucune permission SMS</strong>.
        </p>

        <div class="live-indicator" id="otpIndicator">
          <div class="live-dot" id="otpDot"></div>
          <span class="live-status" id="otpStatus">Inactif (Attente de démarrage)</span>
        </div>

        <button class="btn btn-primary" id="btnStartOtp">
          ▶ startSmsRetriever()
        </button>
      </div>
    </div>

    <div class="card">
      <div class="card-header">🙋‍♂️ SMS User Consent API</div>
      <div class="card-body">
        <p style="font-size: 0.85rem; color: var(--text-muted); margin-bottom: 12px; line-height: 1.4;">
          Cette API capte un SMS OTP de n'importe quel expéditeur en affichant un dialogue système natif de consentement.
        </p>

        <div class="live-indicator" id="consentIndicator">
          <div class="live-dot" id="consentDot"></div>
          <span class="live-status" id="consentStatus">Inactif (Attente de démarrage)</span>
        </div>

        <div class="field" style="margin-bottom:10px">
          <label class="field-label" for="consentSenderAddress">Adresse expéditeur (Optionnel)</label>
          <input type="text" id="consentSenderAddress" class="field-input" placeholder="ex: 38600 ou laisser vide..." />
        </div>

        <div style="display:flex;gap:8px">
          <button class="btn btn-primary" id="btnStartConsent" style="flex:2">
            ▶ startSmsUserConsent()
          </button>
          <button class="btn btn-danger" id="btnStopConsent" style="flex:1;display:none">
            ⏹ Stop
          </button>
        </div>
      </div>
    </div>

    <div class="card">
      <div class="card-header">📥 Message OTP Intercepté</div>
      <div class="card-body">
        <div id="otpResult">
          <div class="empty-state">
            <div class="empty-icon">📥</div>
            Démarrez l'écouteur puis envoyez le SMS
          </div>
        </div>
      </div>
    </div>

    <div class="card" style="border-color: var(--blue);">
      <div class="card-header" style="color: var(--blue); border-bottom-color: var(--border);">
        ℹ️ Guide de test du SMS OTP
      </div>
      <div class="card-body" style="font-size: 0.82rem; color: var(--text-muted); line-height: 1.45;">
        <p style="margin-bottom: 8px;">Pour tester l'interception, envoyez un SMS formaté ainsi à cet appareil :</p>
        <pre style="background: var(--surface2); border: 1px solid var(--border); padding: 8px; border-radius: var(--radius-sm); font-family: var(--font-mono); color: #fff; margin-bottom: 8px; font-size: 0.75rem; overflow-x: auto; white-space: pre-wrap; word-break: break-all;">&lt;#&gt; Votre code de validation est 987654.
Clé de test : [VOTRE_HASH]</pre>
        <p>Le hash dépend de la signature de l'APK installée. Pour l'obtenir lors des tests locaux, vous pouvez regarder les logs logcat.</p>
      </div>
    </div>
  `;

  let otpListener = null;
  let isOtpListening = false;
  let consentListener = null;
  let isConsentListening = false;

  const btnStartOtp = document.getElementById('btnStartOtp');
  const otpDot = document.getElementById('otpDot');
  const otpStatus = document.getElementById('otpStatus');
  const otpResult = document.getElementById('otpResult');

  const btnStartConsent = document.getElementById('btnStartConsent');
  const btnStopConsent = document.getElementById('btnStopConsent');
  const consentDot = document.getElementById('consentDot');
  const consentStatus = document.getElementById('consentStatus');
  const consentSenderAddress = document.getElementById('consentSenderAddress');

  function updateOtpUI(listening, message = null) {
    isOtpListening = listening;
    if (!btnStartOtp || !otpDot || !otpStatus || !otpResult) return;

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

  function updateConsentUI(listening) {
    isConsentListening = listening;
    if (!btnStartConsent || !btnStopConsent || !consentDot || !consentStatus) return;

    if (listening) {
      consentDot.classList.add('active');
      consentStatus.textContent = 'Écoute active (attente SMS OTP)…';
      consentStatus.style.color = 'var(--green)';
      btnStartConsent.disabled = true;
      btnStartConsent.innerHTML = '<span class="spinner"></span> En attente…';
      btnStopConsent.style.display = 'block';
    } else {
      consentDot.classList.remove('active');
      consentStatus.textContent = 'Inactif (Attente de démarrage)';
      consentStatus.style.color = '';
      btnStartConsent.disabled = false;
      btnStartConsent.innerHTML = '▶ startSmsUserConsent()';
      btnStopConsent.style.display = 'none';
    }
  }

  // --- SMS Retriever API ---
  if (btnStartOtp) {
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
  }

  // --- SMS User Consent API ---
  if (btnStartConsent) {
    btnStartConsent.addEventListener('click', async () => {
      const senderAddress = consentSenderAddress ? (consentSenderAddress.value.trim() || null) : null;
      const options = senderAddress ? { senderAddress } : {};

      try {
        updateConsentUI(true);

        if (consentListener) {
          await consentListener.remove();
          consentListener = null;
        }

        consentListener = await SmsVault.addListener('smsUserConsentReceived', (data) => {
          addLog('🙋‍♂️', 'event:smsUserConsentReceived', {}, data.message, 'event');
          updateOtpUI(false, data.message); // Affiche le message dans la zone d'affichage commune OTP
          toast('Message reçu via Consentement !', 'success');
          updateConsentUI(false);
          
          if (consentListener) {
            consentListener.remove();
            consentListener = null;
          }
        });

        await SmsVault.startSmsUserConsent(options);
        addLog('▶', 'startSmsUserConsent', options, 'OK');
        toast('Écouteur SMS User Consent démarré', 'success');
      } catch (e) {
        updateConsentUI(false);
        addLog('▶', 'startSmsUserConsent', options, e.message, 'err');
        toast(e.message, 'error');
      }
    });
  }

  if (btnStopConsent) {
    btnStopConsent.addEventListener('click', async () => {
      try {
        await SmsVault.stopSmsUserConsent();
        if (consentListener) {
          await consentListener.remove();
          consentListener = null;
        }
        addLog('⏹', 'stopSmsUserConsent', {}, 'OK');
        updateConsentUI(false);
        toast('Écouteur SMS User Consent arrêté', 'info');
      } catch (e) {
        addLog('⏹', 'stopSmsUserConsent', {}, e.message, 'err');
        toast(e.message, 'error');
      }
    });
  }
}
