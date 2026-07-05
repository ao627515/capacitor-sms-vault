# Design Document — `capacitor-sms-vault` V1

**Date** : 2026-07-05  
**Scope** : Implémentation complète V1 (F1, F2, F3, F4, F6) + README + sms-vault-demo Ionic  
**Statut** : Approuvé

---

## Problème

Le projet `capacitor-sms-vault` a été scaffoldé avec `@capacitor/create-plugin` mais ne contient que le code de démonstration `echo`. Aucune logique SMS n'a été implémentée. L'objectif est de livrer un plugin Capacitor/Android fonctionnel couvrant toute la V1 du cahier des charges.

---

## Périmètre V1

| ID | Fonction | Inclus |
|---|---|---|
| F1 | Gestion des permissions (`READ_SMS`, `RECEIVE_SMS`) | ✅ |
| F2 | Default SMS handler (vérification + demande) | ✅ |
| F3 | Lecture historique SMS (filtres : address, since, limit) | ✅ |
| F4 | Interception temps réel (BroadcastReceiver + événement JS) | ✅ |
| F5 | SMS Retriever API (OTP) | ❌ V2 |
| F6 | Export JSON structuré | ✅ (inclus dans F3/F4) |
| F8 | Détection SIM/numéro | ❌ V2 si besoin avéré |

---

## Approche choisie : Séparation stricte (Approche A)

Chaque fichier a une responsabilité unique, conformément à l'arborescence du §4 du cahier des charges.

---

## Architecture

### Couche TypeScript (src/)

- **definitions.ts** : SmsMessage, PermissionStatus, SmsVaultPlugin interface complète
- **web.ts** : Stubs avec unavailableOnPlatform pour toutes les méthodes
- **index.ts** : Inchangé

### Couche Android Kotlin

- **AndroidManifest.xml** : READ_SMS, RECEIVE_SMS + <receiver> SmsReceiver
- **SmsReader.kt** : ContentResolver + Telephony.Sms.Inbox avec filtres
- **SmsReceiver.kt** : BroadcastReceiver + notifyListeners
- **SmsVaultPlugin.kt** : Pont Capacitor, permissions, délégation

### Configuration

- minSdkVersion : 23 (Android 6.0)
- peerDependencies : "^7.0.0 || ^8.0.0"

---

## Fichiers à créer/modifier

| Fichier | Action |
|---|---|
| src/definitions.ts | Réécrire complet |
| src/web.ts | Réécrire complet |
| android/src/main/AndroidManifest.xml | Ajouter permissions + receiver |
| android/.../SmsVaultPlugin.kt | Réécrire complet |
| android/.../SmsVault.kt | Remplacer par SmsReader.kt |
| android/.../SmsReader.kt | Créer |
| android/.../SmsReceiver.kt | Créer |
| package.json | Corriger peerDependencies |
| android/build.gradle | minSdkVersion → 23 |
| README.md | Compléter après docgen |
| sms-vault-demo/ | Refaire en Ionic |
