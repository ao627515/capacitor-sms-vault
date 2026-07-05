# capacitor-sms-vault

Generalist Capacitor plugin for reading, intercepting and exporting SMS on AndroidInstalling dependencies.

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

## API

<docgen-index>

* [`checkPermissions()`](#checkpermissions)
* [`requestPermissions()`](#requestpermissions)
* [`isDefaultSmsApp()`](#isdefaultsmsapp)
* [`requestDefaultSmsApp()`](#requestdefaultsmsapp)
* [`getSmsList(...)`](#getsmslist)
* [`startListening()`](#startlistening)
* [`stopListening()`](#stoplistening)
* [`addListener('smsReceived', ...)`](#addlistenersmsreceived-)
* [`removeAllListeners()`](#removealllisteners)
* [Interfaces](#interfaces)
* [Type Aliases](#type-aliases)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### checkPermissions()

```typescript
checkPermissions() => Promise<PermissionStatus>
```

Vérifie l'état actuel des permissions READ_SMS et RECEIVE_SMS.
Ne déclenche aucune demande.

**Returns:** <code>Promise&lt;<a href="#permissionstatus">PermissionStatus</a>&gt;</code>

--------------------


### requestPermissions()

```typescript
requestPermissions() => Promise<PermissionStatus>
```

Demande les permissions READ_SMS et RECEIVE_SMS à l'utilisateur.
Retourne l'état des permissions après la décision de l'utilisateur.

**Returns:** <code>Promise&lt;<a href="#permissionstatus">PermissionStatus</a>&gt;</code>

--------------------


### isDefaultSmsApp()

```typescript
isDefaultSmsApp() => Promise<{ isDefault: boolean; }>
```

Vérifie si l'application est l'application SMS par défaut du système.
Sur Android 10+, seule l'app par défaut peut lire l'historique SMS.

**Returns:** <code>Promise&lt;{ isDefault: boolean; }&gt;</code>

--------------------


### requestDefaultSmsApp()

```typescript
requestDefaultSmsApp() => Promise<{ isDefault: boolean; }>
```

Ouvre le dialogue système pour demander à l'utilisateur de définir
cette application comme application SMS par défaut.

**Returns:** <code>Promise&lt;{ isDefault: boolean; }&gt;</code>

--------------------


### getSmsList(...)

```typescript
getSmsList(options?: GetSmsListOptions | undefined) => Promise<{ messages: SmsMessage[]; }>
```

Récupère la liste des SMS de l'inbox avec filtres optionnels.
Requiert la permission READ_SMS et que l'app soit l'app SMS par défaut (Android 10+).

| Param         | Type                                                            | Description          |
| ------------- | --------------------------------------------------------------- | -------------------- |
| **`options`** | <code><a href="#getsmslistoptions">GetSmsListOptions</a></code> | - Filtres optionnels |

**Returns:** <code>Promise&lt;{ messages: SmsMessage[]; }&gt;</code>

--------------------


### startListening()

```typescript
startListening() => Promise<void>
```

Démarre l'écoute des SMS entrants en temps réel.
Requiert la permission RECEIVE_SMS.
Émettra l'événement 'smsReceived' à chaque nouveau SMS reçu.

--------------------


### stopListening()

```typescript
stopListening() => Promise<void>
```

Arrête l'écoute des SMS entrants.
Libère le BroadcastReceiver Android.

--------------------


### addListener('smsReceived', ...)

```typescript
addListener(eventName: 'smsReceived', listener: (sms: SmsMessage) => void) => Promise<PluginListenerHandle>
```

Enregistre un listener pour les SMS reçus en temps réel.
Appeler startListening() pour activer la réception.

| Param           | Type                                                                | Description                            |
| --------------- | ------------------------------------------------------------------- | -------------------------------------- |
| **`eventName`** | <code>'smsReceived'</code>                                          | - 'smsReceived'                        |
| **`listener`**  | <code>(sms: <a href="#smsmessage">SmsMessage</a>) =&gt; void</code> | - Callback appelé à chaque SMS entrant |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### removeAllListeners()

```typescript
removeAllListeners() => Promise<void>
```

Supprime tous les listeners enregistrés sur ce plugin.

--------------------


### Interfaces


#### PermissionStatus

| Prop             | Type                                                        | Description                       |
| ---------------- | ----------------------------------------------------------- | --------------------------------- |
| **`readSms`**    | <code><a href="#permissionstate">PermissionState</a></code> | État de la permission READ_SMS    |
| **`receiveSms`** | <code><a href="#permissionstate">PermissionState</a></code> | État de la permission RECEIVE_SMS |


#### SmsMessage

| Prop          | Type                           | Description                                                |
| ------------- | ------------------------------ | ---------------------------------------------------------- |
| **`id`**      | <code>string</code>            | Identifiant unique du SMS (depuis la base ContentProvider) |
| **`address`** | <code>string</code>            | Numéro ou nom de l'expéditeur                              |
| **`body`**    | <code>string</code>            | Contenu brut du SMS                                        |
| **`date`**    | <code>number</code>            | Timestamp en millisecondes                                 |
| **`type`**    | <code>'inbox' \| 'sent'</code> | Type de message : reçu ou envoyé                           |


#### GetSmsListOptions

| Prop          | Type                | Description                                                         |
| ------------- | ------------------- | ------------------------------------------------------------------- |
| **`address`** | <code>string</code> | Filtrer par numéro d'expéditeur (recherche partielle)               |
| **`since`**   | <code>number</code> | Timestamp ms minimum — retourne uniquement les SMS après cette date |
| **`limit`**   | <code>number</code> | Nombre maximum de résultats (défaut : 100)                          |


#### PluginListenerHandle

| Prop         | Type                                      |
| ------------ | ----------------------------------------- |
| **`remove`** | <code>() =&gt; Promise&lt;void&gt;</code> |


### Type Aliases


#### PermissionState

<code>'prompt' | 'prompt-with-rationale' | 'granted' | 'denied'</code>

</docgen-api>
