import { registerPlugin } from '@capacitor/core';

import type { SmsVaultPlugin } from './definitions';

const SmsVault = registerPlugin<SmsVaultPlugin>('SmsVault', {
  web: () => import('./web').then((m) => new m.SmsVaultWeb()),
});

export * from './definitions';
export { SmsVault };
