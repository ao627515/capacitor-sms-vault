import { WebPlugin } from '@capacitor/core';

import type {
  GetSmsListOptions,
  PermissionStatus,
  SmsMessage,
  SmsVaultPlugin,
} from './definitions';

/**
 * Web stub pour SmsVaultPlugin.
 * Toutes les méthodes lèvent une erreur "unavailable" car la lecture SMS
 * n'est pas disponible dans un navigateur web.
 */
export class SmsVaultWeb extends WebPlugin implements SmsVaultPlugin {
  async checkPermissions(): Promise<PermissionStatus> {
    throw this.unavailable('SmsVault is not available on web.');
  }

  async requestPermissions(): Promise<PermissionStatus> {
    throw this.unavailable('SmsVault is not available on web.');
  }

  async isDefaultSmsApp(): Promise<{ isDefault: boolean }> {
    throw this.unavailable('SmsVault is not available on web.');
  }

  async requestDefaultSmsApp(): Promise<{ isDefault: boolean }> {
    throw this.unavailable('SmsVault is not available on web.');
  }

  async getSmsList(_options?: GetSmsListOptions): Promise<{ messages: SmsMessage[] }> {
    throw this.unavailable('SmsVault is not available on web.');
  }

  async startListening(): Promise<void> {
    throw this.unavailable('SmsVault is not available on web.');
  }

  async stopListening(): Promise<void> {
    throw this.unavailable('SmsVault is not available on web.');
  }
}
