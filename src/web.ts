import { WebPlugin } from '@capacitor/core';

import type { SmsVaultPlugin } from './definitions';

export class SmsVaultWeb extends WebPlugin implements SmsVaultPlugin {
  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }
}
