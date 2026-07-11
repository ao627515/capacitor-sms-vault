import { SmsVault } from 'capacitor-sms-vault';
import { addLog } from './logger.js';
import { toast, renderSmsCard } from './ui.js';

export function initLiveListening() {
  const container = document.getElementById('tab-live');
  if (!container) return;

  container.innerHTML = `
    <div class="live-indicator" id="liveIndicator">
      <div class="live-dot" id="liveDot"></div>
      <span class="live-status" id="liveStatus">Écoute inactive</span>
      <span class="live-counter" id="liveCounter">0 reçus</span>
    </div>

    <!-- Strategy selection -->
    <div class="field" style="margin-bottom:14px">
      <label class="field-label" for="liveStrategy">Stratégie d'écoute</label>
      <select id="liveStrategy" class="field-input">
        <option value="both" selected>Les deux (Recommandé)</option>
        <option value="broadcast">BroadcastReceiver uniquement (SMS en direct)</option>
        <option value="observer">ContentObserver uniquement (Changements de base)</option>
      </select>
    </div>

    <div style="display:flex;gap:8px;margin-bottom:8px">
      <button class="btn btn-success" id="btnToggleListen" style="flex:2">
        ▶ startListening()
      </button>
      <button class="btn btn-ghost btn-sm" id="btnClearLive" style="flex:1">
        🗑 Vider
      </button>
    </div>
    <button class="btn btn-ghost btn-sm" id="btnRemoveAllListeners" style="width:100%; margin-bottom:14px;">
      🗑 removeAllListeners()
    </button>

    <div id="liveList">
      <div class="empty-state">
        <div class="empty-icon">📡</div>
        Démarrez l'écoute puis envoyez un SMS à cet appareil
      </div>
    </div>
  `;

  let liveListener = null;
  let liveCount = 0;
  let isListening = false;

  const btnToggleListen = document.getElementById('btnToggleListen');
  const liveDot = document.getElementById('liveDot');
  const liveStatus = document.getElementById('liveStatus');
  const liveCounter = document.getElementById('liveCounter');
  const liveList = document.getElementById('liveList');
  const liveStrategy = document.getElementById('liveStrategy');

  function updateLiveUI() {
    if (!btnToggleListen || !liveDot || !liveStatus || !liveCounter) return;

    if (isListening) {
      liveDot.classList.add('active');
      liveStatus.textContent = 'Écoute active…';
      liveStatus.style.color = 'var(--green)';
      btnToggleListen.className = 'btn btn-danger';
      btnToggleListen.innerHTML = '⏹ stopListening()';
      if (liveStrategy) liveStrategy.disabled = true;
    } else {
      liveDot.classList.remove('active');
      liveStatus.textContent = 'Écoute inactive';
      liveStatus.style.color = '';
      btnToggleListen.className = 'btn btn-success';
      btnToggleListen.innerHTML = '▶ startListening()';
      if (liveStrategy) liveStrategy.disabled = false;
    }
    liveCounter.textContent = `${liveCount} reçu${liveCount > 1 ? 's' : ''}`;
  }

  if (btnToggleListen) {
    btnToggleListen.addEventListener('click', async () => {
      btnToggleListen.disabled = true;
      const strategy = liveStrategy ? liveStrategy.value : 'both';
      try {
        if (!isListening) {
          // Démarrer
          liveListener = await SmsVault.addListener('smsReceived', (sms) => {
            liveCount++;
            updateLiveUI();
            addLog('📡', 'event:smsReceived', {}, `from ${sms.address}`, 'event');

            // Supprimer l'empty state si présent
            if (liveList) {
              const empty = liveList.querySelector('.empty-state');
              if (empty) empty.remove();
              liveList.prepend(renderSmsCard(sms, true));
            }
          });
          await SmsVault.startListening({ strategy });
          isListening = true;
          addLog('▶', 'startListening', { strategy }, 'OK');
          toast(`Écoute SMS démarrée (Stratégie: ${strategy})`, 'success');
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
        addLog(isListening ? '⏹' : '▶', isListening ? 'stopListening' : 'startListening', { strategy }, e.message, 'err');
        toast(e.message, 'error');
      } finally {
        btnToggleListen.disabled = false;
      }
    });
  }

  const btnClearLive = document.getElementById('btnClearLive');
  if (btnClearLive) {
    btnClearLive.addEventListener('click', () => {
      liveCount = 0;
      updateLiveUI();
      if (liveList) {
        liveList.innerHTML = `
          <div class="empty-state">
            <div class="empty-icon">📡</div>
            Historique vidé — l'écoute continue si elle est active
          </div>`;
      }
    });
  }

  const btnRemoveAllListeners = document.getElementById('btnRemoveAllListeners');
  if (btnRemoveAllListeners) {
    btnRemoveAllListeners.addEventListener('click', async () => {
      try {
        await SmsVault.removeAllListeners();
        isListening = false;
        if (liveListener) {
          liveListener = null;
        }
        updateLiveUI();
        addLog('🗑', 'removeAllListeners', {}, 'OK');
        toast('Tous les listeners ont été supprimés', 'info');
      } catch (e) {
        addLog('🗑', 'removeAllListeners', {}, e.message, 'err');
        toast(e.message, 'error');
      }
    });
  }
}
