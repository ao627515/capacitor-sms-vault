import type { PermissionState, PluginListenerHandle } from '@capacitor/core';

// ─────────────────────────────────────────────
// Enums
// ─────────────────────────────────────────────

/**
 * Types de boîte SMS Android — correspond aux valeurs
 * de la colonne `type` dans Telephony.Sms.
 */
export enum MessageType {
  /** Tous les messages toutes boîtes confondues */
  ALL = 0,
  /** Messages reçus */
  INBOX = 1,
  /** Messages envoyés */
  SENT = 2,
  /** Brouillons */
  DRAFT = 3,
  /** Messages en attente d'envoi */
  OUTBOX = 4,
  /** Messages dont l'envoi a échoué */
  FAILED = 5,
  /** Messages en file d'attente */
  QUEUED = 6,
}

// ─────────────────────────────────────────────
// Interfaces de données
// ─────────────────────────────────────────────

export interface SmsMessage {
  /** Identifiant unique du SMS (depuis la base ContentProvider) */
  id: string;
  /** Identifiant du fil de conversation */
  threadId?: string;
  /** Numéro ou nom de l'expéditeur */
  address: string;
  /** Contenu brut du SMS */
  body: string;
  /** Sujet du message (MMS ou opérateurs spécifiques) */
  subject?: string;
  /** Timestamp local en millisecondes (date de réception/envoi sur l'appareil) */
  date: number;
  /** Timestamp réseau en millisecondes (date d'envoi côté opérateur) */
  dateSent?: number;
  /** Type de message selon MessageType */
  type: MessageType;
  /** Package de l'application ayant créé le SMS */
  creator?: string;
  /** Identifiant du contact associé à l'expéditeur */
  person?: string;
}

export interface PermissionStatus {
  /** État de la permission READ_SMS */
  readSms: PermissionState;
  /** État de la permission RECEIVE_SMS */
  receiveSms: PermissionState;
  /** État de la permission de lecture d'état de téléphone (SIM / numéro) */
  phone: PermissionState;
}

export interface SimCardInfo {
  /** Nom de l'opérateur (ex: Orange, SFR) */
  carrierName: string;
  /** Code pays ISO à 2 lettres (ex: fr) */
  countryIso: string;
  /** Numéro de téléphone de la ligne si provisionné (peut être vide) */
  phoneNumber: string;
  /** Index du slot SIM physique (0, 1, etc.) */
  simSlotIndex: number;
  /** Identifiant d'abonnement interne Android */
  subscriptionId: number;
}

// ─────────────────────────────────────────────
// Filtres et projection
// ─────────────────────────────────────────────

/**
 * Sélection des colonnes à retourner dans SmsMessage.
 * Omettre une colonne réduit la quantité de données
 * transférée entre la couche native et le JS.
 * Par défaut toutes les colonnes obligatoires sont retournées.
 */
export interface SmsProjection {
  threadId?: boolean;
  subject?: boolean;
  dateSent?: boolean;
  creator?: boolean;
  person?: boolean;
}

/**
 * Filtres appliqués côté natif (Kotlin/ContentResolver)
 * avant le retour des données au JS — plus performant
 * qu'un filtrage JS sur un inbox complet.
 */
export interface SmsFilter {
  /** Type de boîte à interroger (défaut : INBOX) */
  type?: MessageType;
  /** Identifiant exact du SMS */
  id?: string;
  /** Identifiant exact du fil de conversation */
  threadId?: string;
  /** Filtrer par expéditeur (correspondance partielle) */
  address?: string;
  /** Filtrer par expéditeur via expression régulière */
  addressRegex?: string;
  /** Filtrer par contenu du corps (correspondance partielle) */
  body?: string;
  /** Filtrer par contenu du corps via expression régulière */
  bodyRegex?: string;
  /** Timestamp ms minimum — retourne les SMS après cette date */
  minDate?: number;
  /** Timestamp ms maximum — retourne les SMS avant cette date */
  maxDate?: number;
  /** Index de départ pour la pagination (défaut : 0) */
  indexFrom?: number;
  /** Nombre maximum de résultats (défaut : 100) */
  maxCount?: number;
}

// ─────────────────────────────────────────────
// Interface principale du plugin
// ─────────────────────────────────────────────

export interface SmsVaultPlugin {

  // ── Permissions ──────────────────────────────

  /**
   * Vérifie l'état actuel des permissions READ_SMS, RECEIVE_SMS et de l'état du téléphone.
   * Ne déclenche aucune demande.
   */
  checkPermissions(): Promise<PermissionStatus>;

  /**
   * Demande les permissions de lecture, réception de SMS et de l'état du téléphone à l'utilisateur.
   * Retourne l'état des permissions après la décision de l'utilisateur.
   */
  requestPermissions(): Promise<PermissionStatus>;

  // ── Default SMS handler ───────────────────────

  /**
   * Vérifie si l'application est l'application SMS par défaut du système.
   * Sur Android 10+, seule l'app par défaut peut lire l'historique SMS complet.
   */
  isDefaultSmsApp(): Promise<{ isDefault: boolean }>;

  /**
   * Ouvre le dialogue système pour demander à l'utilisateur de définir
   * cette application comme application SMS par défaut.
   * Retourne le statut après la décision de l'utilisateur.
   */
  requestDefaultSmsApp(): Promise<{ isDefault: boolean }>;

  // ── Lecture historique ────────────────────────

  /**
   * Récupère la liste des SMS selon les filtres et la projection fournis.
   * Le filtrage est appliqué côté natif pour des raisons de performance.
   *
   * @param options.filter  - Critères de filtrage (expéditeur, date, regex, type...)
   * @param options.projection - Colonnes supplémentaires à inclure dans la réponse
   */
  getSmsList(options?: {
    filter?: SmsFilter;
    projection?: SmsProjection;
  }): Promise<{ messages: SmsMessage[] }>;

  /**
   * Retourne uniquement le nombre de SMS correspondant aux filtres,
   * sans charger les messages — utile pour la pagination ou les badges.
   *
   * @param options.filter - Mêmes critères que getSmsList
   */
  getCount(options?: {
    filter?: SmsFilter;
  }): Promise<{ count: number }>;

  /**
   * Retourne toutes les colonnes brutes de la table SMS Android,
   * sans mapping — utile pour le debug ou l'accès à des colonnes
   * non exposées par l'API standard (sub_id, service_center, etc.).
   *
   * ⚠️ Performance : ne pas utiliser sur de grands volumes.
   * Préférer getSmsList() avec projection pour un usage en production.
   *
   * @param options.filter - Mêmes critères que getSmsList
   */
  getRawSmsList(options?: {
    filter?: SmsFilter;
  }): Promise<{ messages: Record<string, string>[] }>;

  // ── Interception temps réel ───────────────────

  /**
   * Démarre l'écoute des SMS entrants via un BroadcastReceiver dynamique.
   * Requiert la permission RECEIVE_SMS.
   * Émet l'événement 'smsReceived' à chaque nouveau SMS reçu.
   */
  startListening(): Promise<void>;

  /**
   * Arrête l'écoute des SMS entrants.
   * Libère le BroadcastReceiver Android enregistré par startListening().
   */
  stopListening(): Promise<void>;

  /**
   * Enregistre un listener pour les SMS reçus en temps réel.
   * Appeler startListening() pour activer la réception.
   */
  addListener(
    eventName: 'smsReceived',
    listener: (sms: SmsMessage) => void,
  ): Promise<PluginListenerHandle>;

  // ── OTP (SMS Retriever API) ───────────────────

  /**
   * Démarre la détection d'OTP via la SMS Retriever API.
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

  // ── Détection SIM / Numéro ────────────────────

  /**
   * Récupère la liste des cartes SIM actives et de leurs numéros de téléphone associés.
   * Requiert la permission 'phone' (READ_PHONE_STATE et READ_PHONE_NUMBERS).
   */
  getSimInfo(): Promise<{ simCards: SimCardInfo[] }>;

  // ── Utilitaires ───────────────────────────────

  /**
   * Supprime tous les listeners enregistrés sur ce plugin.
   */
  removeAllListeners(): Promise<void>;
}
