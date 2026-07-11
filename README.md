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

#### Règles de Filtrage et Priorités

Les filtres de `SmsFilter` sont classés en trois groupes :
1. **Groupe A — Filtres cumulatifs (AND)** : `minDate`, `maxDate`, `bodyRegex`, `orderBy`, `read`, `maxCount`, `indexFrom`. Ils s'appliquent tous simultanément.
2. **Groupe B — Filtres exclusifs (Priorité OR implicite)** : `id`, `threadId`, `address`, `addressRegex`, `body`. **N'en utiliser qu'un seul à la fois.** Si plusieurs sont fournis, la priorité suivante est appliquée : `id > threadId > address > addressRegex > body`. Seul le premier filtre valide trouvé selon cette priorité est appliqué (les autres sont ignorés).
3. **Groupe C — Pagination** : `indexFrom` + `maxCount` (cumulables avec tout, appliqués en dernier).

### Intercepter les SMS en temps réel

```typescript
// 1. Enregistrer le listener
const handle = await SmsVault.addListener('smsReceived', (sms) => {
  console.log('SMS reçu de', sms.address, ':', sms.body);
});

// 2. Démarrer l'écoute (par défaut 'broadcast')
await SmsVault.startListening();

// OU : Stratégie hybride recommandée pour les applications critiques / Fintech (UEMOA, Tecno/Infinix)
// active le BroadcastReceiver et le ContentObserver simultanément avec déduplication par id
await SmsVault.startListening({ strategy: 'both' });

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

### Détecter les codes de validation avec SMS User Consent

Contrairement à la SMS Retriever API, la SMS User Consent API ne requiert pas de format de message particulier ni de clé de hachage de l'application dans le SMS, mais elle demande un consentement explicite de l'utilisateur via une boîte de dialogue système lorsqu'un SMS est reçu.

```typescript
// 1. Enregistrer le listener pour le consentement
const handle = await SmsVault.addListener('smsUserConsentReceived', (data) => {
  console.log('Message reçu après consentement :', data.message);
  
  // Extraire le code OTP (ex: regex)
  const otpCode = data.message.match(/\d{4,10}/)?.[0];
  console.log('OTP :', otpCode);
  
  handle.remove();
});

// 2. Démarrer l'écoute (valide max 5 minutes)
// Spécifiez facultativement le numéro de l'expéditeur attendu pour restreindre le popup
await SmsVault.startSmsUserConsent({ senderAddress: '+22670000000' });
```

### Modifier ou Supprimer des SMS

> ⚠️ **Important (Android 4.4+)** : La suppression de messages ou le marquage comme lu nécessite que votre application soit configurée comme l'**application SMS par défaut** sur l'appareil.

```typescript
// Marquer un SMS comme lu
await SmsVault.markAsRead({ id: '123' });
console.log('Le message 123 a été marqué comme lu');

// Supprimer un SMS
await SmsVault.deleteSms({ id: '123' });
console.log('Le message 123 a été supprimé');
```

### Envoyer des SMS (Direct ou Indirect)

Vous pouvez envoyer des SMS de deux manières :
1. **Mode direct** : Envoi immédiat en arrière-plan. Requiert la permission `sendSms` (`android.permission.SEND_SMS`).
2. **Mode indirect** : Ouvre l'application de messagerie par défaut pré-remplie avec le numéro et le message. Aucune permission requise.

```typescript
// Mode Direct (avec permission)
const status = await SmsVault.requestPermissions();
if (status.sendSms === 'granted') {
  await SmsVault.sendSms({
    phoneNumber: '+33612345678',
    message: 'Bonjour, ceci est un SMS direct.',
    mode: 'direct'
  });
}

// Mode Indirect (sans permission, ouvre l'UI système)
await SmsVault.sendSms({
  phoneNumber: '+33612345678',
  message: 'Bonjour, ceci est un SMS pré-rempli.',
  mode: 'indirect'
});
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
* [`startListening(...)`](#startlistening)
* [`stopListening()`](#stoplistening)
* [`addListener('smsReceived', ...)`](#addlistenersmsreceived-)
* [`startSmsRetriever()`](#startsmsretriever)
* [`addListener('otpReceived', ...)`](#addlistenerotpreceived-)
* [`getSimInfo()`](#getsiminfo)
* [`getDeviceSimProfile()`](#getdevicesimprofile)
* [`startMonitoringSimChanges()`](#startmonitoringsimchanges)
* [`stopMonitoringSimChanges()`](#stopmonitoringsimchanges)
* [`startSmsUserConsent(...)`](#startsmsuserconsent)
* [`stopSmsUserConsent()`](#stopsmsuserconsent)
* [`addListener('simsChanged', ...)`](#addlistenersimschanged-)
* [`addListener('smsUserConsentReceived', ...)`](#addlistenersmsuserconsentreceived-)
* [`markAsRead(...)`](#markasread)
* [`markAsSeen(...)`](#markasseen)
* [`deleteSms(...)`](#deletesms)
* [`setSmsLocked(...)`](#setsmslocked)
* [`sendSms(...)`](#sendsms)
* [`removeAllListeners()`](#removealllisteners)
* [Interfaces](#interfaces)
* [Type Aliases](#type-aliases)
* [Enums](#enums)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

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

Ouvre le dialogue système pour demander à l'utilisateur de définir
cette application comme application SMS par défaut.
Retourne le statut après la décision de l'utilisateur.

**Returns:** <code>Promise&lt;{ isDefault: boolean; }&gt;</code>

--------------------


### getSmsList(...)

```typescript
getSmsList(options?: { filter?: SmsFilter | undefined; projection?: SmsProjection | undefined; } | undefined) => Promise<{ messages: SmsMessage[]; }>
```

Récupère la liste des SMS selon les filtres et la projection fournis.
Le filtrage est appliqué côté natif pour des raisons de performance.

| Param         | Type                                                                                                                   |
| ------------- | ---------------------------------------------------------------------------------------------------------------------- |
| **`options`** | <code>{ filter?: <a href="#smsfilter">SmsFilter</a>; projection?: <a href="#smsprojection">SmsProjection</a>; }</code> |

**Returns:** <code>Promise&lt;{ messages: SmsMessage[]; }&gt;</code>

--------------------


### getCount(...)

```typescript
getCount(options?: { filter?: SmsFilter | undefined; } | undefined) => Promise<{ count: number; }>
```

Retourne uniquement le nombre de SMS correspondant aux filtres,
sans charger les messages — utile pour la pagination ou les badges.

| Param         | Type                                                          |
| ------------- | ------------------------------------------------------------- |
| **`options`** | <code>{ filter?: <a href="#smsfilter">SmsFilter</a>; }</code> |

**Returns:** <code>Promise&lt;{ count: number; }&gt;</code>

--------------------


### getRawSmsList(...)

```typescript
getRawSmsList(options?: { filter?: SmsFilter | undefined; } | undefined) => Promise<{ messages: Record<string, string>[]; }>
```

Retourne toutes les colonnes brutes de la table SMS Android,
sans mapping — utile pour le debug ou l'accès à des colonnes
non exposées par l'API standard (sub_id, service_center, etc.).

⚠️ Performance : ne pas utiliser sur de grands volumes.
Préférer getSmsList() avec projection pour un usage en production.

| Param         | Type                                                          |
| ------------- | ------------------------------------------------------------- |
| **`options`** | <code>{ filter?: <a href="#smsfilter">SmsFilter</a>; }</code> |

**Returns:** <code>Promise&lt;{ messages: <a href="#record">Record</a>&lt;string, string&gt;[]; }&gt;</code>

--------------------


### startListening(...)

```typescript
startListening(options?: { strategy?: "broadcast" | "observer" | "both" | undefined; } | undefined) => Promise<void>
```

Démarre l'écoute des SMS entrants via un BroadcastReceiver ou un ContentObserver.
Requiert la permission RECEIVE_SMS.
Émet l'événement 'smsReceived' à chaque nouveau SMS reçu.

| Param         | Type                                                             |
| ------------- | ---------------------------------------------------------------- |
| **`options`** | <code>{ strategy?: 'broadcast' \| 'observer' \| 'both'; }</code> |

--------------------


### stopListening()

```typescript
stopListening() => Promise<void>
```

Arrête l'écoute des SMS entrants.
Libère le BroadcastReceiver Android enregistré par startListening().

--------------------


### addListener('smsReceived', ...)

```typescript
addListener(eventName: 'smsReceived', listener: (sms: SmsMessage) => void) => Promise<PluginListenerHandle>
```

Enregistre un listener pour les SMS reçus en temps réel.
Appeler startListening() pour activer la réception.

| Param           | Type                                                                |
| --------------- | ------------------------------------------------------------------- |
| **`eventName`** | <code>'smsReceived'</code>                                          |
| **`listener`**  | <code>(sms: <a href="#smsmessage">SmsMessage</a>) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### startSmsRetriever()

```typescript
startSmsRetriever() => Promise<void>
```

Démarre la détection d'OTP via la SMS Retriever API.
L'API attend la réception d'un unique message de format spécifique avec la clé de hachage de l'application.
Ne requiert aucune permission SMS sensible.

⚠️ L'écoute expire automatiquement après 5 minutes. Passé ce délai, relancez la méthode si nécessaire.

--------------------


### addListener('otpReceived', ...)

```typescript
addListener(eventName: 'otpReceived', listener: (data: { message: string; }) => void) => Promise<PluginListenerHandle>
```

Enregistre un listener pour la réception de l'OTP.

| Param           | Type                                                 | Description                                         |
| --------------- | ---------------------------------------------------- | --------------------------------------------------- |
| **`eventName`** | <code>'otpReceived'</code>                           | - 'otpReceived'                                     |
| **`listener`**  | <code>(data: { message: string; }) =&gt; void</code> | - Callback appelé lorsque l'OTP/message est détecté |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### getSimInfo()

```typescript
getSimInfo() => Promise<{ simCards: SimCardInfo[]; }>
```

Récupère la liste des cartes SIM actives et de leurs numéros de téléphone associés.
Requiert la permission 'phone' (READ_PHONE_STATE et READ_PHONE_NUMBERS).

**Returns:** <code>Promise&lt;{ simCards: SimCardInfo[]; }&gt;</code>

--------------------


### getDeviceSimProfile()

```typescript
getDeviceSimProfile() => Promise<SimProfile>
```

Récupère le profil détaillé des cartes SIM de l'appareil (état double SIM et liste).
Requiert la permission 'phone'.

**Returns:** <code>Promise&lt;<a href="#simprofile">SimProfile</a>&gt;</code>

--------------------


### startMonitoringSimChanges()

```typescript
startMonitoringSimChanges() => Promise<void>
```

Démarre la surveillance des changements de cartes SIM sur l'appareil.
Émet l'événement 'simsChanged' à chaque changement détecté.
Requiert la permission 'phone'.

--------------------


### stopMonitoringSimChanges()

```typescript
stopMonitoringSimChanges() => Promise<void>
```

Arrête la surveillance des changements de cartes SIM sur l'appareil.

--------------------


### startSmsUserConsent(...)

```typescript
startSmsUserConsent(options?: { senderAddress?: string | undefined; } | undefined) => Promise<void>
```

Démarre l'écoute d'un message de validation via l'API SMS User Consent de Google.
Affiche un pop-up système de consentement à la réception du SMS.

⚠️ L'écoute expire automatiquement après 5 minutes. Passé ce délai, relancez la méthode si nécessaire.

| Param         | Type                                     |
| ------------- | ---------------------------------------- |
| **`options`** | <code>{ senderAddress?: string; }</code> |

--------------------


### stopSmsUserConsent()

```typescript
stopSmsUserConsent() => Promise<void>
```

Arrête l'écoute du consentement utilisateur.

--------------------


### addListener('simsChanged', ...)

```typescript
addListener(eventName: 'simsChanged', listener: (profile: SimProfile) => void) => Promise<PluginListenerHandle>
```

Enregistre un listener pour les changements de cartes SIM.

| Param           | Type                                                                    |
| --------------- | ----------------------------------------------------------------------- |
| **`eventName`** | <code>'simsChanged'</code>                                              |
| **`listener`**  | <code>(profile: <a href="#simprofile">SimProfile</a>) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('smsUserConsentReceived', ...)

```typescript
addListener(eventName: 'smsUserConsentReceived', listener: (data: { message: string; }) => void) => Promise<PluginListenerHandle>
```

Enregistre un listener pour la réception du message après consentement de l'utilisateur.

| Param           | Type                                                 |
| --------------- | ---------------------------------------------------- |
| **`eventName`** | <code>'smsUserConsentReceived'</code>                |
| **`listener`**  | <code>(data: { message: string; }) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### markAsRead(...)

```typescript
markAsRead(options: { id: string; }) => Promise<void>
```

Marque un message SMS comme lu (définit le champ 'read' à 1).
Sur Android 10+, cette opération requiert d'être configuré comme l'application SMS par défaut.
Remarque : marque également le message comme vu.

| Param         | Type                         |
| ------------- | ---------------------------- |
| **`options`** | <code>{ id: string; }</code> |

--------------------


### markAsSeen(...)

```typescript
markAsSeen(options: { id: string; }) => Promise<void>
```

Marque un message SMS comme vu (définit le champ 'seen' à 1).
Sur Android 10+, cette opération requiert d'être configuré comme l'application SMS par défaut.

| Param         | Type                         |
| ------------- | ---------------------------- |
| **`options`** | <code>{ id: string; }</code> |

--------------------


### deleteSms(...)

```typescript
deleteSms(options: { id: string; force?: boolean; }) => Promise<{ deleted: boolean; reason?: 'locked' | 'not_found'; }>
```

Supprime un SMS par son identifiant unique.
⚠️ Cette méthode requiert obligatoirement d'être configuré comme l'application SMS par défaut.

| Param         | Type                                          |
| ------------- | --------------------------------------------- |
| **`options`** | <code>{ id: string; force?: boolean; }</code> |

**Returns:** <code>Promise&lt;{ deleted: boolean; reason?: 'locked' | 'not_found'; }&gt;</code>

--------------------


### setSmsLocked(...)

```typescript
setSmsLocked(options: { id: string; locked: boolean; }) => Promise<void>
```

Verrouille ou déverrouille un SMS par son identifiant unique.
⚠️ Cette méthode requiert obligatoirement d'être configuré comme l'application SMS par défaut.

| Param         | Type                                          |
| ------------- | --------------------------------------------- |
| **`options`** | <code>{ id: string; locked: boolean; }</code> |

--------------------


### sendSms(...)

```typescript
sendSms(options: { phoneNumber: string; message: string; mode: 'direct' | 'indirect'; }) => Promise<void>
```

Envoie un SMS.

| Param         | Type                                                                                 |
| ------------- | ------------------------------------------------------------------------------------ |
| **`options`** | <code>{ phoneNumber: string; message: string; mode: 'direct' \| 'indirect'; }</code> |

--------------------


### removeAllListeners()

```typescript
removeAllListeners() => Promise<void>
```

Supprime tous les listeners enregistrés sur ce plugin.

--------------------


### Interfaces


#### PermissionStatus

| Prop             | Type                                                        | Description                                                         |
| ---------------- | ----------------------------------------------------------- | ------------------------------------------------------------------- |
| **`readSms`**    | <code><a href="#permissionstate">PermissionState</a></code> | État de la permission READ_SMS                                      |
| **`receiveSms`** | <code><a href="#permissionstate">PermissionState</a></code> | État de la permission RECEIVE_SMS                                   |
| **`phone`**      | <code><a href="#permissionstate">PermissionState</a></code> | État de la permission de lecture d'état de téléphone (SIM / numéro) |
| **`sendSms`**    | <code><a href="#permissionstate">PermissionState</a></code> | État de la permission d'envoi de SMS (direct)                       |


#### SmsMessage

| Prop                 | Type                                                            | Description                                                                     |
| -------------------- | --------------------------------------------------------------- | ------------------------------------------------------------------------------- |
| **`id`**             | <code>string</code>                                             | Identifiant unique du SMS (depuis la base ContentProvider)                      |
| **`threadId`**       | <code>string</code>                                             | Identifiant du fil de conversation                                              |
| **`address`**        | <code>string</code>                                             | Numéro ou nom de l'expéditeur                                                   |
| **`body`**           | <code>string</code>                                             | Contenu brut du SMS                                                             |
| **`subject`**        | <code>string</code>                                             | Sujet du message (MMS ou opérateurs spécifiques)                                |
| **`date`**           | <code>number</code>                                             | Timestamp local en millisecondes (date de réception/envoi sur l'appareil)       |
| **`dateSent`**       | <code>number</code>                                             | Timestamp réseau en millisecondes (date d'envoi côté opérateur)                 |
| **`type`**           | <code><a href="#messagetype">MessageType</a></code>             | Type de message selon <a href="#messagetype">MessageType</a>                    |
| **`creator`**        | <code>string</code>                                             | Package de l'application ayant créé le SMS                                      |
| **`person`**         | <code>string</code>                                             | Identifiant du contact associé à l'expéditeur                                   |
| **`subscriptionId`** | <code>number</code>                                             | Identifiant d'abonnement SIM réceptrice (Android uniquement)                    |
| **`seen`**           | <code>boolean</code>                                            | Indique si le message a été vu dans la liste/notification (Android uniquement)  |
| **`status`**         | <code><a href="#smsdeliverystatus">SmsDeliveryStatus</a></code> | Statut de livraison (Android uniquement)                                        |
| **`errorCode`**      | <code>number</code>                                             | Code d'erreur associé à l'envoi/réception (Android uniquement)                  |
| **`locked`**         | <code>boolean</code>                                            | Indique si le message est verrouillé contre la suppression (Android uniquement) |
| **`serviceCenter`**  | <code>string</code>                                             | Numéro du centre de messagerie de l'opérateur (Android uniquement)              |


#### SmsFilter

Filtres appliqués côté natif (Kotlin/ContentResolver)
avant le retour des données au JS — plus performant
qu'un filtrage JS sur un inbox complet.

| Prop               | Type                                                | Description                                                                           |
| ------------------ | --------------------------------------------------- | ------------------------------------------------------------------------------------- |
| **`type`**         | <code><a href="#messagetype">MessageType</a></code> | Type de boîte à interroger (défaut : INBOX)                                           |
| **`id`**           | <code>string</code>                                 | Identifiant exact du SMS                                                              |
| **`threadId`**     | <code>string</code>                                 | Identifiant exact du fil de conversation                                              |
| **`address`**      | <code>string</code>                                 | Filtrer par expéditeur (correspondance partielle)                                     |
| **`addressRegex`** | <code>string</code>                                 | Filtrer par expéditeur via expression régulière                                       |
| **`body`**         | <code>string</code>                                 | Filtrer par contenu du corps (correspondance partielle)                               |
| **`bodyRegex`**    | <code>string</code>                                 | Filtrer par contenu du corps via expression régulière                                 |
| **`minDate`**      | <code>number</code>                                 | Timestamp ms minimum — retourne les SMS après cette date                              |
| **`maxDate`**      | <code>number</code>                                 | Timestamp ms maximum — retourne les SMS avant cette date                              |
| **`indexFrom`**    | <code>number</code>                                 | Index de départ pour la pagination (défaut : 0)                                       |
| **`maxCount`**     | <code>number</code>                                 | Nombre maximum de résultats (défaut : 100)                                            |
| **`read`**         | <code>0 \| 1</code>                                 | Filtrer par statut de lecture (0 = non lu, 1 = lu)                                    |
| **`seen`**         | <code>0 \| 1</code>                                 | Filtrer par statut d'affichage notification (0 = non vu, 1 = vu) (Android uniquement) |
| **`orderBy`**      | <code>'date_desc' \| 'date_asc'</code>              | Ordre de tri (date_desc ou date_asc, défaut : date_desc)                              |


#### SmsProjection

Sélection des colonnes à retourner dans <a href="#smsmessage">SmsMessage</a>.
Omettre une colonne réduit la quantité de données
transférée entre la couche native et le JS.
Par défaut toutes les colonnes obligatoires sont retournées.

| Prop                | Type                 |
| ------------------- | -------------------- |
| **`threadId`**      | <code>boolean</code> |
| **`subject`**       | <code>boolean</code> |
| **`dateSent`**      | <code>boolean</code> |
| **`creator`**       | <code>boolean</code> |
| **`person`**        | <code>boolean</code> |
| **`seen`**          | <code>boolean</code> |
| **`status`**        | <code>boolean</code> |
| **`errorCode`**     | <code>boolean</code> |
| **`locked`**        | <code>boolean</code> |
| **`serviceCenter`** | <code>boolean</code> |


#### PluginListenerHandle

| Prop         | Type                                      |
| ------------ | ----------------------------------------- |
| **`remove`** | <code>() =&gt; Promise&lt;void&gt;</code> |


#### SimCardInfo

| Prop                 | Type                | Description                                                                |
| -------------------- | ------------------- | -------------------------------------------------------------------------- |
| **`carrierName`**    | <code>string</code> | Nom de l'opérateur (ex: Orange, SFR)                                       |
| **`countryIso`**     | <code>string</code> | Code pays ISO à 2 lettres (ex: fr)                                         |
| **`phoneNumber`**    | <code>string</code> | Numéro de téléphone de la ligne si provisionné (peut être vide)            |
| **`simSlotIndex`**   | <code>number</code> | Index du slot SIM physique (0, 1, etc.)                                    |
| **`subscriptionId`** | <code>number</code> | Identifiant d'abonnement interne Android                                   |
| **`mcc`**            | <code>string</code> | Code pays mobile (Mobile Country Code) (ex: 208, 613) (Android uniquement) |
| **`mnc`**            | <code>string</code> | Code réseau mobile (Mobile Network Code) (ex: 01, 02) (Android uniquement) |


#### SimProfile

| Prop          | Type                       | Description                                                     |
| ------------- | -------------------------- | --------------------------------------------------------------- |
| **`dualSim`** | <code>boolean</code>       | Indique si le périphérique possède plusieurs cartes SIM actives |
| **`sims`**    | <code>SimCardInfo[]</code> | Liste des informations détaillées pour chaque carte SIM active  |


### Type Aliases


#### PermissionState

<code>'prompt' | 'prompt-with-rationale' | 'granted' | 'denied'</code>


#### Record

Construct a type with a set of properties K of type T

<code>{ [P in K]: T; }</code>


### Enums


#### MessageType

| Members      | Value          | Description                                |
| ------------ | -------------- | ------------------------------------------ |
| **`ALL`**    | <code>0</code> | Tous les messages toutes boîtes confondues |
| **`INBOX`**  | <code>1</code> | Messages reçus                             |
| **`SENT`**   | <code>2</code> | Messages envoyés                           |
| **`DRAFT`**  | <code>3</code> | Brouillons                                 |
| **`OUTBOX`** | <code>4</code> | Messages en attente d'envoi                |
| **`FAILED`** | <code>5</code> | Messages dont l'envoi a échoué             |
| **`QUEUED`** | <code>6</code> | Messages en file d'attente                 |


#### SmsDeliveryStatus

| Members        | Value           | Description                    |
| -------------- | --------------- | ------------------------------ |
| **`NONE`**     | <code>-1</code> | Aucun accusé de réception reçu |
| **`COMPLETE`** | <code>0</code>  | SMS livré avec succès          |
| **`PENDING`**  | <code>32</code> | Livraison en attente           |
| **`FAILED`**   | <code>64</code> | Livraison échouée              |

</docgen-api>
