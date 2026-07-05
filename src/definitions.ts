export interface SmsVaultPlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
}
