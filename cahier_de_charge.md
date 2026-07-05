# Cahier des charges — `capacitor-sms-vault`

> **Note d'usage** : ce document est conçu pour être lu et exécuté par un agent IA de développement (ex. Claude Code). Il contient un contexte projet, une spécification fonctionnelle/technique, et une section dédiée aux instructions de comportement de l'agent (recherche documentaire, gestion de l'incertitude, format de sortie). L'agent doit lire l'intégralité du document avant de produire du code.

---

## 0. Contexte projet (résumé pour orientation)

`capacitor-sms-vault` est un plugin Capacitor pour Android, généraliste, dédié à la gestion complète des SMS sur l'appareil : lecture de l'historique, interception temps réel, gestion des permissions, et support OTP. Le plugin est conçu comme une **brique bas niveau et neutre**, sans logique métier embarquée, réutilisable par tout type d'application (suivi financier, automatisation, notifications, archivage, etc.). Aucun cas d'usage spécifique n'est codé en dur dans le plugin.

---

## 1. Objectif

Fournir une API Capacitor/Android permettant à une application hôte de :
- gérer les permissions et le statut d'app SMS par défaut,
- lire l'historique des SMS,
- intercepter les SMS entrants en temps réel,
- capter un OTP sans permission sensible (SMS Retriever API),
- exporter les données SMS sous forme structurée (JSON).

---

## 2. Périmètre fonctionnel

| # | Fonction | Description | Priorité |
|---|---|---|---|
| F1 | Gestion des permissions | Vérifier/demander `READ_SMS`, `RECEIVE_SMS` | V1 |
| F2 | Default SMS handler | Vérifier si l'app est l'app SMS par défaut, déclencher la demande de changement | V1 |
| F3 | Lecture historique | Récupérer les SMS de l'inbox (filtrable par expéditeur, date, limite) | V1 |
| F4 | Interception temps réel | Écouter les nouveaux SMS entrants et notifier l'app JS en direct | V1 |
| F5 | SMS Retriever (OTP) | Capter un code OTP sans permission, via l'API officielle Google | V2 |
| F6 | Export brut | Retourner les données en JSON structuré | V1 |
| F8 | Détection SIM/numéro | `READ_PHONE_NUMBERS`/`READ_PHONE_STATE` — non justifié en V1 | V2 (si besoin avéré) |

---

## 3. Spécification API (TypeScript)

```ts
interface SmsVaultPlugin {
  // Permissions
  checkPermissions(): Promise<PermissionStatus>;
  requestPermissions(): Promise<PermissionStatus>;

  // Default handler
  isDefaultSmsApp(): Promise<{ isDefault: boolean }>;
  requestDefaultSmsApp(): Promise<{ isDefault: boolean }>;

  // Lecture historique
  getSmsList(options: {
    address?: string;
    since?: number;   // timestamp ms
    limit?: number;
  }): Promise<{ messages: SmsMessage[] }>;

  // Interception temps réel
  startListening(): Promise<void>;
  stopListening(): Promise<void>;
  addListener(eventName: 'smsReceived', listener: (sms: SmsMessage) => void): Promise<PluginListenerHandle>;

  // OTP (SMS Retriever API) — V2
  startSmsRetriever(): Promise<void>;
  addListener(eventName: 'otpReceived', listener: (data: { message: string }) => void): Promise<PluginListenerHandle>;
}

interface SmsMessage {
  id: string;
  address: string;   // expéditeur
  body: string;       // contenu brut
  date: number;        // timestamp
  type: 'inbox' | 'sent';
}
```

---

## 4. Architecture attendue

```
capacitor-sms-vault/
├── src/                      # Couche TS (définitions, web stub)
│   ├── definitions.ts
│   ├── index.ts
│   └── web.ts
├── android/
│   └── src/main/java/.../
│       ├── SmsVaultPlugin.kt        # classe @CapacitorPlugin
│       ├── SmsReader.kt             # logique ContentResolver
│       ├── SmsReceiver.kt           # BroadcastReceiver temps réel
│       └── SmsRetrieverHelper.kt    # wrapper SMS Retriever API (V2)
├── README.md
└── package.json
```

Générer le squelette via `@capacitor/create-plugin` (officiel) plutôt que from scratch intégral. Réutiliser la logique `ContentResolver`/`Telephony.Sms` éprouvée d'implémentations existantes comme référence de départ, mais réécrire la couche permissions avec l'API Capacitor moderne.

---

## 5. Contraintes techniques et compatibilité

| Composant | Cible |
|---|---|
| Capacitor | 8.x (principal), 7.x (supporté via peer dependency large) |
| Capacitor ≤6 | Non supporté — recommander migration |
| Ionic | Toutes versions — sans impact (plugin agnostique au framework UI) |
| `minSdkVersion` | 23 (Android 6.0) |
| `targetSdkVersion` | Dernière version exigée par Google Play au moment du build — **à vérifier en temps réel, voir §8** |
| Langage natif | Kotlin |
| Dépendances interdites | Tout package Ionic Native / Cordova legacy |

```json
"peerDependencies": {
  "@capacitor/core": "^7.0.0 || ^8.0.0"
}
```

---

## 6. Contraintes non-fonctionnelles

- Aucun appel réseau, aucun stockage externe — le plugin lit/transmet uniquement, jamais de transmission tierce.
- Doit fonctionner hors Play Store (sideload/APK direct) sans dégradation.
- Permissions runtime gérées via l'API Capacitor moderne (`PermissionState`).
- Aucune logique métier ou parsing spécifique à un cas d'usage embarquée nativement — le plugin reste générique.

---

## 7. Conformité Google Play

- `READ_SMS` nécessite que l'app hôte soit déclarée default SMS handler (sauf F5/OTP via SMS Retriever, qui n'exige aucune permission).
- La déclaration Play Console (formulaire Permissions SMS/Call Log) reste de la responsabilité de l'app hôte, pas du plugin — mais le README du plugin doit l'expliquer clairement.

---

## 8. Livrables

1. Plugin npm `capacitor-sms-vault` (TS + Android natif Kotlin).
2. README : installation, permissions Manifest, exemples d'usage, avertissement policy Play Store, tableau de compatibilité.
3. App de démo minimale (Ionic) : lecture historique, interception live, OTP retriever (V2).

---

## 9. Roadmap

- **V1** : F1, F2, F3, F4, F6.
- **V2** : F5 (OTP SMS Retriever), réévaluation F8 si besoin réel avéré.

---

## 10. Hors périmètre

- iOS (lecture SMS non autorisée nativement par Apple).

---

## 11. Instructions pour l'agent IA

Cette section encadre le comportement attendu de l'agent qui implémente ce cahier des charges.

### 11.1 Principe général
Les API Android et Capacitor évoluent (permissions, politiques Play Store, signatures d'API natives). L'agent **ne doit pas se fier uniquement à sa mémoire d'entraînement** pour ces éléments — il doit vérifier les informations à jour avant de les utiliser dans le code ou la documentation produite.

### 11.2 Quand rechercher activement de l'information
L'agent doit effectuer une recherche documentaire avant d'agir dans les cas suivants :
- Signature exacte d'une API Android (`Telephony.Sms`, `SmsManager`, `BroadcastReceiver`, `SubscriptionManager`, etc.) — vérifier sur `developer.android.com`.
- Comportement ou disponibilité de la **SMS Retriever API** — vérifier sur `developers.google.com/identity/sms-retriever`.
- API Capacitor (structure `@CapacitorPlugin`, `PermissionState`, `notifyListeners`, gestion des permissions) — vérifier sur `capacitorjs.com/docs`.
- Procédure de génération de squelette plugin (`@capacitor/create-plugin`) — vérifier la commande et les options actuelles dans la doc officielle Capacitor.
- Politique Google Play sur les permissions `READ_SMS`/`RECEIVE_SMS`/`READ_PHONE_STATE` (formulaire "Permissions Declaration", default SMS handler) — vérifier sur `support.google.com/googleplay/android-developer`.
- Toute valeur numérique sensible au temps : `targetSdkVersion` minimum exigé actuellement par Google Play, version stable courante de Capacitor/AGP/Android Studio.
- Tout comportement spécifique à une version d'Android (permissions runtime, restrictions background, dual-SIM) si le comportement diffère entre versions.

### 11.3 Quand ne pas rechercher
- Logique générale de structure d'un plugin Capacitor (déjà détaillée au §4).
- Choix déjà figés dans ce document (noms de méthodes, périmètre fonctionnel, architecture des fichiers) — l'agent les applique sans les remettre en question, sauf incohérence technique bloquante.

### 11.4 Gestion de l'incertitude
- Si une information technique nécessaire n'est pas trouvée ou reste ambiguë après recherche, l'agent doit le signaler explicitement plutôt que de deviner silencieusement, et proposer l'hypothèse la plus raisonnable avec sa source (ou l'absence de source).
- Si une exigence de ce cahier des charges devient incompatible avec une contrainte technique découverte en cours d'implémentation (ex. changement de policy Play Store), l'agent signale l'écart avant de poursuivre, plutôt que de modifier silencieusement le périmètre.

### 11.5 Sources de référence prioritaires
- `https://capacitorjs.com/docs` — API Capacitor, création de plugin, permissions.
- `https://developer.android.com/reference` — API Android natives (Telephony, BroadcastReceiver, permissions).
- `https://developers.google.com/identity/sms-retriever` — SMS Retriever API.
- `https://support.google.com/googleplay/android-developer` — politiques de permissions Play Store.
- `https://github.com/ionic-team/capacitor` — changelog, versions, breaking changes.

### 11.6 Format de sortie attendu
- Code organisé selon l'arborescence du §4.
- Toute déviation par rapport à ce cahier des charges (API, structure, permissions) doit être justifiée en commentaire ou dans le message de réponse, avec la source ayant motivé le changement.
- README généré conforme aux exigences du §7-8.