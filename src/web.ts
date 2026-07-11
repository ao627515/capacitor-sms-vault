import { WebPlugin } from '@capacitor/core';

import type {
  PermissionStatus,
  SimCardInfo,
  SimProfile,
  SmsFilter,
  SmsMessage,
  SmsProjection,
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

  async getSmsList(_options?: { filter?: SmsFilter; projection?: SmsProjection }): Promise<{ messages: SmsMessage[] }> {
    throw this.unavailable('SmsVault is not available on web.');
  }

  async getCount(_options?: { filter?: SmsFilter }): Promise<{ count: number }> {
    throw this.unavailable('SmsVault is not available on web.');
  }

  async getRawSmsList(_options?: { filter?: SmsFilter }): Promise<{ messages: Record<string, string>[] }> {
    throw this.unavailable('SmsVault is not available on web.');
  }

  async startListening(_options?: { strategy?: 'broadcast' | 'observer' | 'both' }): Promise<void> {
    throw this.unavailable('SmsVault is not available on web.');
  }

  async stopListening(): Promise<void> {
    throw this.unavailable('SmsVault is not available on web.');
  }

  async startSmsRetriever(): Promise<void> {
    throw this.unavailable('SmsVault is not available on web.');
  }

  async getSimInfo(): Promise<{ simCards: SimCardInfo[] }> {
    throw this.unavailable('SmsVault is not available on web.');
  }

  async getDeviceSimProfile(): Promise<SimProfile> {
    throw this.unavailable('SmsVault is not available on web.');
  }

  async startMonitoringSimChanges(): Promise<void> {
    throw this.unavailable('SmsVault is not available on web.');
  }

  async stopMonitoringSimChanges(): Promise<void> {
    throw this.unavailable('SmsVault is not available on web.');
  }

  async startSmsUserConsent(_options?: { senderAddress?: string }): Promise<void> {
    throw this.unavailable('SmsVault is not available on web.');
  }

  async stopSmsUserConsent(): Promise<void> {
    throw this.unavailable('SmsVault is not available on web.');
  }
}
