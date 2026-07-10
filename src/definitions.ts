import type { PermissionState, PluginListenerHandle } from '@capacitor/core';

export interface SmsMessage {
  /** Identifiant unique du SMS (depuis la base ContentProvider) */
  id: string;
  /** Numéro ou nom de l'expéditeur */
  address: string;
  /** Contenu brut du SMS */
  body: string;
  /** Timestamp en millisecondes */
  date: number;
  /** Type de message : reçu ou envoyé */
  type: 'inbox' | 'sent';
}

export interface PermissionStatus {
  /** État de la permission READ_SMS */
  readSms: PermissionState;
  /** État de la permission RECEIVE_SMS */
  receiveSms: PermissionState;
}

export interface GetSmsListOptions {
  /** Filtrer par numéro d'expéditeur (recherche partielle) */
  address?: string;
  /** Timestamp ms minimum — retourne uniquement les SMS après cette date */
  since?: number;
  /** Nombre maximum de résultats (défaut : 100) */
  limit?: number;
}

export interface SmsVaultPlugin {
  /**
   * Vérifie l'état actuel des permissions READ_SMS et RECEIVE_SMS.
   * Ne déclenche aucune demande.
   */
  checkPermissions(): Promise<PermissionStatus>;

  /**
   * Demande les permissions READ_SMS et RECEIVE_SMS à l'utilisateur.
   * Retourne l'état des permissions après la décision de l'utilisateur.
   */
  requestPermissions(): Promise<PermissionStatus>;

  /**
   * Vérifie si l'application est l'application SMS par défaut du système.
   * Sur Android 10+, seule l'app par défaut peut lire l'historique SMS.
   */
  isDefaultSmsApp(): Promise<{ isDefault: boolean }>;

  /**
   * Ouvre le dialogue système pour demander à l'utilisateur de définir
   * cette application comme application SMS par défaut.
   */
  requestDefaultSmsApp(): Promise<{ isDefault: boolean }>;

  /**
   * Récupère la liste des SMS de l'inbox avec filtres optionnels.
   * Requiert la permission READ_SMS et que l'app soit l'app SMS par défaut (Android 10+).
   *
   * @param options - Filtres optionnels
   * @param options.address - Filtrer par expéditeur (recherche partielle)
   * @param options.since - Timestamp ms minimum
   * @param options.limit - Nombre maximum de résultats (défaut : 100)
   */
  getSmsList(options?: GetSmsListOptions): Promise<{ messages: SmsMessage[] }>;

  /**
   * Démarre l'écoute des SMS entrants en temps réel.
   * Requiert la permission RECEIVE_SMS.
   * Émettra l'événement 'smsReceived' à chaque nouveau SMS reçu.
   */
  startListening(): Promise<void>;

  /**
   * Arrête l'écoute des SMS entrants.
   * Libère le BroadcastReceiver Android.
   */
  stopListening(): Promise<void>;

  /**
   * Enregistre un listener pour les SMS reçus en temps réel.
   * Appeler startListening() pour activer la réception.
   *
   * @param eventName - 'smsReceived'
   * @param listener - Callback appelé à chaque SMS entrant
   */
  addListener(
    eventName: 'smsReceived',
    listener: (sms: SmsMessage) => void,
  ): Promise<PluginListenerHandle>;

  /**
   * Démarre la détection d'OTP via la SMS Retriever API (V2).
   * L'API attend la réception d'un unique message de format spécifique avec la clé de hachage de l'application.
   * Ne requiert aucune permission SMS sensible.
   */
  startSmsRetriever(): Promise<void>;

  /**
   * Enregistre un listener pour la réception de l'OTP.
   *
   * @param eventName - 'otpReceived'
   * @param listener - Callback appelé lorsque l'OTP/message est détecté
   */
  addListener(
    eventName: 'otpReceived',
    listener: (data: { message: string }) => void,
  ): Promise<PluginListenerHandle>;

  /**
   * Supprime tous les listeners enregistrés sur ce plugin.
   */
  removeAllListeners(): Promise<void>;
}
