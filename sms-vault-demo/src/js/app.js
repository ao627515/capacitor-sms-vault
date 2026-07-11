import { initTabs } from './modules/tabs.js';
import { initLogger, addLog } from './modules/logger.js';
import { initPermissions, refreshPermissionBadges, refreshDefaultBadge } from './modules/permissions.js';
import { initSmsList } from './modules/smsList.js';
import { initLiveListening } from './modules/live.js';
import { initOtpRetriever } from './modules/otp.js';
import { initSendSms } from './modules/send.js';

// Initialisation des différents modules de l'application
initTabs();
initLogger();
initPermissions();
initSmsList();
initLiveListening();
initOtpRetriever();
initSendSms();

// Vérification initiale des permissions au démarrage
(async () => {
  try {
    const status = await refreshPermissionBadges();
    await refreshDefaultBadge();
    addLog('🚀', 'init:checkPermissions', {}, JSON.stringify(status));
  } catch (e) {
    // Silencieux au démarrage (normal sur navigateur web ou simulateur)
    addLog('🚀', 'init:checkPermissions', {}, e.message, 'err');
  }
})();
