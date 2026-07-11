import { SmsVault } from 'capacitor-sms-vault';
import { addLog } from './logger.js';
import { toast } from './ui.js';

export function initSendSms() {
  const container = document.getElementById('tab-send');
  if (!container) return;

  container.innerHTML = `
    <div class="card">
      <div class="card-header">✉️ Envoyer un SMS — sendSms()</div>
      <div class="card-body">

        <!-- Numéro de téléphone -->
        <div class="field">
          <label class="field-label" for="sendPhoneNumber">Numéro de téléphone</label>
          <input
            type="tel"
            id="sendPhoneNumber"
            class="field-input"
            placeholder="+33612345678"
            autocomplete="off"
          />
        </div>

        <!-- Message -->
        <div class="field">
          <label class="field-label" for="sendMessageText">Message</label>
          <textarea
            id="sendMessageText"
            class="field-input"
            placeholder="Votre message ici…"
            rows="3"
            style="resize: none;"
          ></textarea>
        </div>

        <!-- Mode -->
        <div class="field">
          <label class="field-label">Mode d'envoi</label>
          <div class="radio-group">
            <div class="radio-btn selected" id="btnSendModeIndirect" data-mode="indirect">📤 Via l'app SMS</div>
            <div class="radio-btn" id="btnSendModeDirect" data-mode="direct">⚡ Direct (Background)</div>
          </div>
        </div>

        <hr class="divider" />

        <button class="btn btn-primary" id="btnSendSms" style="width:100%">
          ✉️ Envoyer
        </button>
      </div>
    </div>
  `;

  let sendMode = 'indirect';

  document.querySelectorAll('.radio-btn[data-mode]').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.radio-btn[data-mode]').forEach(b => b.classList.remove('selected'));
      btn.classList.add('selected');
      sendMode = btn.dataset.mode;
    });
  });

  const btnSendSms = document.getElementById('btnSendSms');
  if (btnSendSms) {
    btnSendSms.addEventListener('click', async () => {
      const phoneNumberInput = document.getElementById('sendPhoneNumber');
      const messageTextarea = document.getElementById('sendMessageText');

      const phoneNumber = phoneNumberInput ? phoneNumberInput.value.trim() : '';
      const message = messageTextarea ? messageTextarea.value.trim() : '';

      if (!phoneNumber || !message) {
        toast('Veuillez remplir le numéro et le message', 'warn');
        return;
      }

      btnSendSms.disabled = true;
      btnSendSms.innerHTML = '<span class="spinner"></span> Envoi…';

      const options = { phoneNumber, message, mode: sendMode };

      try {
        await SmsVault.sendSms(options);
        addLog('✉️', 'sendSms', options, `SMS envoyé (mode: ${sendMode})`);
        toast('SMS envoyé avec succès', 'success');
        if (messageTextarea) messageTextarea.value = '';
      } catch (e) {
        addLog('✉️', 'sendSms', options, e.message, 'err');
        toast(`Échec de l'envoi : ${e.message}`, 'error');
      } finally {
        btnSendSms.disabled = false;
        btnSendSms.innerHTML = '✉️ Envoyer';
      }
    });
  }
}
