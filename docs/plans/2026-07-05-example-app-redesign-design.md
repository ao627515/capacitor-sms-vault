# Design Document — SmsVaultDemo Redesign

**Date** : 2026-07-05
**Scope** : Refonte complète de l'sms-vault-demo — suppression Ionic, HTML/CSS/JS vanilla, dark mode, tab bar 4 onglets
**Statut** : Approuvé

## Problème

L'sms-vault-demo actuelle utilise Ionic via CDN (dépendance externe), n'a que 3 boutons basiques, et ne permet pas de tester en profondeur les options de filtrage de `getSmsList`.

## Approche choisie : Single-file vanilla (Approche A)

`index.html` + `js/app.js` — zéro dépendance CDN, dark mode, tab bar JS pur.

## Architecture

### 4 onglets (tab bar fixe en bas)
- 🔐 Permissions : checkPermissions, requestPermissions, isDefaultSmsApp, requestDefaultSmsApp
- 📬 SMS List : getSmsList avec tous les filtres (address, since, limit, type) + JSON brut
- 📡 Live : startListening/stopListening toggle, compteur, historique SMS entrants
- 📋 Logs : console globale de tous les appels plugin

### Design
- Dark mode : fond #0f1117, surface #1a1d2e, accents #6c63ff
- Tab bar fixe en bas
- Font monospace pour les logs et JSON
- Badges colorés pour les statuts de permission

## Fichiers modifiés
- `sms-vault-demo/src/index.html` : refonte complète
- `sms-vault-demo/src/js/app.js` : refonte complète
