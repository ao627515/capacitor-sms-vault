# capacitor-sms-vault

Plugin Capacitor généraliste pour Android — lecture de l'historique SMS, interception en temps réel et export JSON structuré. Conçu comme une brique bas niveau et neutre, sans logique métier embarquée.

[![npm version](https://img.shields.io/npm/v/capacitor-sms-vault)](https://www.npmjs.com/package/capacitor-sms-vault)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)


## Install

To use npm

```bash
npm install capacitor-sms-vault
````

To use yarn

```bash
yarn add capacitor-sms-vault
```

Sync native files

```bash
npx cap sync
```

## Configuration Android

### 1. Permissions dans le Manifest de l'app hôte

Ajouter dans `android/app/src/main/AndroidManifest.xml` de **votre application** :

```xml
<!-- Requis pour l'historique et la réception de SMS -->
<uses-permission android:name="android.permission.READ_SMS" />
<uses-permission android:name="android.permission.RECEIVE_SMS" />

<!-- Requis pour la détection SIM / Numéro -->
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
<uses-permission android:name="android.permission.READ_PHONE_NUMBERS" />
```

### 2. App SMS par défaut (requis pour `READ_SMS` sur Android 10+)

Sur Android 10+, votre application doit être l'application SMS par défaut pour accéder à l'historique SMS. Utilisez `requestDefaultSmsApp()` pour déclencher le dialogue système.

> ⚠️ **Note Google Play** : Les permissions `READ_SMS` et `RECEIVE_SMS` nécessitent une déclaration dans la Play Console (formulaire « Permissions Declaration »). Voir [§ Conformité Google Play](#conformit-google-play).

### 3. Test en sideload (APK installé hors Play Store)

> 🚧 **Ce n'est pas un bug du plugin.** C'est une restriction système Android intentionnelle.

Depuis **Android 13**, Google restreint automatiquement les permissions sensibles (`READ_SMS`, `RECEIVE_SMS`) pour toute application installée en sideload (APK direct, hors Play Store). Vous verrez ce message :

> *"L'accès au statut Application de SMS par défaut a été refusé. Cette appli a demandé l'accès à des autorisations sensibles…"*

Référence officielle Google : https://support.google.com/android/answer/12623953

#### Débloquer manuellement (développeurs)

Il n'existe **aucun code, aucun flag Manifest** qui contourne cette restriction — elle est appliquée au niveau de l'installeur Android, pas de l'application.

Procédure manuelle à effectuer **après chaque réinstallation** de l'APK :

1. **Appui long** sur l'icône de l'application
2. **App info** (ou Infos sur l'appli)
3. Menu **⋮** (trois points) en haut à droite
4. **Allow restricted settings** (Autoriser les paramètres restreints)
5. Revenir dans **Permissions → SMS → Autoriser**

> ⚠️ Ce flag se remet à zéro à chaque réinstallation ou mise à jour de l'APK.

#### Solutions selon votre contexte

| Contexte | Solution |
|---|---|
| **Dev / test personnel** | Procédure manuelle "Allow restricted settings" ci-dessus |
| **Distribution interne / entreprise** | MDM (Mobile Device Management) — peut pré-autoriser les permissions |
| **Distribution publique** | Publier sur le Play Store avec le formulaire SMS Permissions Declaration |
| **Store alternatif** | Utiliser un store qui déploie via l'API session-based installer (ex. F-Droid) |

## Exemples d'utilisation

### Vérifier et demander les permissions

```typescript
import { SmsVault } from 'capacitor-sms-vault';

const status = await SmsVault.checkPermissions();

if (status.readSms !== 'granted' || status.receiveSms !== 'granted') {
  const result = await SmsVault.requestPermissions();
  console.log('Permissions accordées :', result);
}
```

### Vérifier et demander l'app SMS par défaut

```typescript
const { isDefault } = await SmsVault.isDefaultSmsApp();

if (!isDefault) {
  // Ouvre le dialogue système (Android 10+ : RoleManager, Android 6-9 : intent)
  const result = await SmsVault.requestDefaultSmsApp();
  console.log('App par défaut :', result.isDefault);
}
```

### Lire l'historique SMS

```typescript
import { MessageType } from 'capacitor-sms-vault';

// 20 derniers SMS de la boîte de réception (INBOX par défaut)
const { messages } = await SmsVault.getSmsList({
  filter: { maxCount: 20 }
});

// SMS des 7 derniers jours d'un expéditeur avec projection de fil
const { messages: recent } = await SmsVault.getSmsList({
  filter: {
    address: '+33612345678',
    minDate: Date.now() - 7 * 24 * 60 * 60 * 1000,
    maxCount: 50,
    type: MessageType.ALL // Toutes les boîtes
  },
  projection: {
    threadId: true
  }
});

messages.forEach(sms => {
  console.log(`[${new Date(sms.date).toISOString()}] [Thread: ${sms.threadId}] ${sms.address}: ${sms.body}`);
});

// Récupérer le nombre de messages correspondants sans les charger
const { count } = await SmsVault.getCount({
  filter: { address: '+33612345678' }
});
console.log(`Nombre de SMS reçus de l'expéditeur: ${count}`);

// Obtenir toutes les colonnes brutes pour le débogage
const { messages: rawMessages } = await SmsVault.getRawSmsList({
  filter: { maxCount: 5 }
});
console.log('SMS brut pour débogage :', rawMessages);
```

### Intercepter les SMS en temps réel

```typescript
// 1. Enregistrer le listener
const handle = await SmsVault.addListener('smsReceived', (sms) => {
  console.log('SMS reçu de', sms.address, ':', sms.body);
});

// 2. Démarrer l'écoute
await SmsVault.startListening();

// 3. Plus tard, nettoyer
await SmsVault.stopListening();
await handle.remove();
// ou : await SmsVault.removeAllListeners();
```

### Détecter les OTP (SMS Retriever API)

L'API SMS Retriever de Google Play Services permet de récupérer le contenu d'un SMS de validation de manière totalement transparente, **sans requérir de permissions SMS** (`READ_SMS` / `RECEIVE_SMS`).

#### Prérequis de format du SMS

Pour que le message soit intercepté par le système, le serveur d'envoi doit formater le message selon ces règles strictes :
1. Le message ne doit pas dépasser **140 octets**.
2. Commencer par le préfixe `<#>` (recommandé).
3. Se terminer par l'**App Hash** unique de 11 caractères propre à votre signature d'application (ex: `FA+9qCX9VSu`).

Exemple de SMS :
```text
<#> Votre code de validation est : 123456
FA+9qCX9VSu
```

#### Exemple de code

```typescript
// 1. Enregistrer le listener pour l'OTP
const handle = await SmsVault.addListener('otpReceived', (data) => {
  console.log('SMS OTP reçu :', data.message);
  
  // Extraire le code (ex: regex)
  const otpCode = data.message.match(/\d{6}/)?.[0];
  console.log('Code OTP extrait :', otpCode);

  // Libérer le listener (l'API SMS Retriever est à usage unique)
  handle.remove();
});

// 2. Lancer l'écouteur (valide pour un seul message, max 5 minutes)
await SmsVault.startSmsRetriever();
```

### Détecter les cartes SIM actives

Récupère les informations des cartes SIM actives et leurs numéros de téléphone si disponibles (sans garantie pour le numéro de téléphone qui dépend de l'opérateur).

```typescript
// Demander la permission phone si nécessaire
const status = await SmsVault.requestPermissions();
if (status.phone === 'granted') {
  const { simCards } = await SmsVault.getSimInfo();
  
  simCards.forEach(sim => {
    console.log(`SIM ${sim.simSlotIndex}: ${sim.carrierName} (${sim.countryIso})`);
    if (sim.phoneNumber) {
      console.log(`Numéro détecté : ${sim.phoneNumber}`);
    } else {
      console.log(`Numéro non stocké sur la carte SIM`);
    }
  });
}
```

## Conformité Google Play

> ⚠️ **Important** : Les permissions `READ_SMS` et `RECEIVE_SMS` font partie des permissions sensibles soumises à une procédure de validation par Google.

- La déclaration dans la **Play Console** (formulaire « Permissions Declaration ») est de la responsabilité de **l'application hôte**, pas du plugin.
- Pour accéder à `READ_SMS`, l'application doit être déclarée **application SMS par défaut** ou justifier un usage conforme à la politique de Google.
- Le plugin lui-même ne fait aucun appel réseau et ne stocke aucune donnée — il lit et transmet uniquement.
- Référence : [Politique Google Play sur les permissions SMS/Call Log](https://support.google.com/googleplay/android-developer/answer/9047303)

## Compatibilité

| Capacitor | Android | Support |
|---|---|---|
| 7.x | API 23+ (Android 6.0+) | ✅ |
| 8.x | API 23+ (Android 6.0+) | ✅ |
| ≤ 6.x | — | ❌ Migrer vers Capacitor 7+ |
| iOS | — | ❌ Non supporté (Apple n'autorise pas la lecture SMS) |



<docgen-index>

* [`checkPermissions()`](#checkpermissions)
* [`requestPermissions()`](#requestpermissions)
* [`isDefaultSmsApp()`](#isdefaultsmsapp)
* [`requestDefaultSmsApp()`](#requestdefaultsmsapp)
* [`getSmsList(...)`](#getsmslist)
* [`getCount(...)`](#getcount)
* [`getRawSmsList(...)`](#getrawsmslist)
* [`startListening()`](#startlistening)
* [`stopListening()`](#stoplistening)
* [`startSmsRetriever()`](#startsmsretriever)
* [`getSimInfo()`](#getsiminfo)
* [`addListener('smsReceived', ...)`](#addlistenersmsreceived-)
* [`addListener('otpReceived', ...)`](#addlistenerotpreceived-)
* [`removeAllListeners()`](#removealllisteners)
* [Interfaces](#interfaces)

</docgen-index>

<docgen-api>

### checkPermissions()

```typescript
checkPermissions() => Promise<PermissionStatus>
```

Vérifie l'état actuel des permissions READ_SMS, RECEIVE_SMS et de l'état du téléphone.
Ne déclenche aucune demande.

**Returns:** <code>Promise&lt;<a href="#permissionstatus">PermissionStatus</a>&gt;</code>

--------------------


### requestPermissions()

```typescript
requestPermissions() => Promise<PermissionStatus>
```

Demande les permissions de lecture, réception de SMS et de l'état du téléphone à l'utilisateur.
Retourne l'état des permissions après la décision de l'utilisateur.

**Returns:** <code>Promise&lt;<a href="#permissionstatus">PermissionStatus</a>&gt;</code>

--------------------


### isDefaultSmsApp()

```typescript
isDefaultSmsApp() => Promise<{ isDefault: boolean; }>
```

Vérifie si l'application est l'application SMS par défaut du système.
Sur Android 10+, seule l'app par défaut peut lire l'historique SMS complet.

**Returns:** <code>Promise&lt;{ isDefault: boolean; }&gt;</code>

--------------------


### requestDefaultSmsApp()

```typescript
requestDefaultSmsApp() => Promise<{ isDefault: boolean; }>
```

Ouvre le dialogue système pour demander à l'utilisateur de définir cette application comme application SMS par défaut.

**Returns:** <code>Promise&lt;{ isDefault: boolean; }&gt;</code>

--------------------


### getSmsList(...)

```typescript
getSmsList(options?: { filter?: SmsFilter; projection?: SmsProjection; } | undefined) => Promise<{ messages: SmsMessage[]; }>
```

Récupère la liste des SMS selon les filtres et la projection fournis. Le filtrage est appliqué côté natif pour des raisons de performance.

**Returns:** <code>Promise&lt;{ messages: SmsMessage[]; }&gt;</code>

--------------------


### getCount(...)

```typescript
getCount(options?: { filter?: SmsFilter; } | undefined) => Promise<{ count: number; }>
```

Retourne uniquement le nombre de SMS correspondant aux filtres, sans charger les messages.

**Returns:** <code>Promise&lt;{ count: number; }&gt;</code>

--------------------


### getRawSmsList(...)

```typescript
getRawSmsList(options?: { filter?: SmsFilter; } | undefined) => Promise<{ messages: Record<string, string>[]; }>
```

Retourne toutes les colonnes brutes de la table SMS Android, sans mapping, pour le débogage.

**Returns:** <code>Promise&lt;{ messages: Record&lt;string, string&gt;[]; }&gt;</code>

--------------------


### startListening()

```typescript
startListening() => Promise<void>
```

Démarre l'écoute des SMS entrants via un BroadcastReceiver dynamique.
Requiert la permission RECEIVE_SMS.

--------------------


### stopListening()

```typescript
stopListening() => Promise<void>
```

Arrête l'écoute des SMS entrants.

--------------------


### startSmsRetriever()

```typescript
startSmsRetriever() => Promise<void>
```

Démarre la détection d'OTP via la SMS Retriever API.

--------------------


### getSimInfo()

```typescript
getSimInfo() => Promise<{ simCards: SimCardInfo[]; }>
```

Récupère la liste des cartes SIM actives et de leurs numéros de téléphone associés.
Requiert la permission 'phone'.

**Returns:** <code>Promise&lt;{ simCards: SimCardInfo[]; }&gt;</code>

--------------------


### addListener('smsReceived', ...)

```typescript
addListener(eventName: 'smsReceived', listener: (sms: SmsMessage) => void) => Promise<PluginListenerHandle>
```

Enregistre un listener pour les SMS reçus en temps réel.

--------------------


### addListener('otpReceived', ...)

```typescript
addListener(eventName: 'otpReceived', listener: (data: { message: string; }) => void) => Promise<PluginListenerHandle>
```

Enregistre un listener pour la réception de l'OTP.

--------------------


### removeAllListeners()

```typescript
removeAllListeners() => Promise<void>
```

Supprime tous les listeners enregistrés sur ce plugin.

--------------------


### Interfaces


#### PermissionStatus

| Prop             | Type                                                     | Description                       |
| ---------------- | -------------------------------------------------------- | --------------------------------- |
| **`readSms`**    | <code>'prompt' \| 'prompt-with-rationale' \| 'granted' \| 'denied'</code> | État de la permission READ_SMS    |
| **`receiveSms`** | <code>'prompt' \| 'prompt-with-rationale' \| 'granted' \| 'denied'</code> | État de la permission RECEIVE_SMS |
| **`phone`**      | <code>'prompt' \| 'prompt-with-rationale' \| 'granted' \| 'denied'</code> | État de la permission de téléphone |


#### SimCardInfo

| Prop                 | Type                 | Description                                                 |
| -------------------- | -------------------- | ----------------------------------------------------------- |
| **`carrierName`**    | <code>string</code>  | Nom de l'opérateur (ex: Orange, SFR)                        |
| **`countryIso`**     | <code>string</code>  | Code pays ISO à 2 lettres (ex: fr)                          |
| **`phoneNumber`**    | <code>string</code>  | Numéro de téléphone (si disponible)                         |
| **`simSlotIndex`**   | <code>number</code>  | Index du slot SIM (0, 1)                                    |
| **`subscriptionId`** | <code>number</code>  | Identifiant d'abonnement interne                            |


#### SmsFilter

| Prop               | Type                 | Description                                                 |
| ------------------ | -------------------- | ----------------------------------------------------------- |
| **`type`**         | <code>number</code>  | Type de boîte (défaut : INBOX = 1, ALL = 0)                 |
| **`id`**           | <code>string</code>  | Identifiant exact du message                                |
| **`threadId`**     | <code>string</code>  | Identifiant du fil de conversation                          |
| **`address`**      | <code>string</code>  | Filtrer par expéditeur (correspondance partielle)           |
| **`addressRegex`** | <code>string</code>  | Filtrer par expéditeur (expression régulière)               |
| **`body`**         | <code>string</code>  | Filtrer par contenu du corps (correspondance partielle)      |
| **`bodyRegex`**    | <code>string</code>  | Filtrer par contenu du corps (expression régulière)         |
| **`minDate`**      | <code>number</code>  | Timestamp ms minimum                                        |
| **`maxDate`**      | <code>number</code>  | Timestamp ms maximum                                        |
| **`indexFrom`**    | <code>number</code>  | Index de départ pour la pagination                          |
| **`maxCount`**     | <code>number</code>  | Nombre maximum de résultats                                 |


#### SmsProjection

| Prop           | Type                 | Description                                                 |
| -------------- | -------------------- | ----------------------------------------------------------- |
| **`threadId`** | <code>boolean</code> | Inclure l'identifiant du fil de conversation                |
| **`subject`**  | <code>boolean</code> | Inclure le sujet du message                                 |
| **`dateSent`** | <code>boolean</code> | Inclure le timestamp d'envoi opérateur                      |
| **`creator`**  | <code>boolean</code> | Inclure l'application créatrice                             |
| **`person`**   | <code>boolean</code> | Inclure l'identifiant du contact                            |


#### SmsMessage

| Prop          | Type                 | Description                                                 |
| ------------- | -------------------- | ----------------------------------------------------------- |
| **`id`**      | <code>string</code>  | Identifiant unique du SMS                                   |
| **`threadId`**| <code>string</code>  | Identifiant du fil de conversation                          |
| **`address`** | <code>string</code>  | Numéro ou nom de l'expéditeur                               |
| **`body`**    | <code>string</code>  | Contenu brut du SMS                                         |
| **`subject`** | <code>string</code>  | Sujet du message                                            |
| **`date`**    | <code>number</code>  | Timestamp local en ms                                       |
| **`dateSent`**| <code>number</code>  | Timestamp réseau en ms                                      |
| **`type`**    | <code>number</code>  | Type de message (MessageType)                               |
| **`creator`** | <code>string</code>  | Package créateur                                            |
| **`person`**  | <code>string</code>  | Identifiant contact                                         |

</docgen-api>
