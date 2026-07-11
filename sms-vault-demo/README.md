# Application de Démonstration SMS Vault

Cette application permet de tester toutes les fonctionnalités du plugin `capacitor-sms-vault` de manière visuelle et interactive sur un appareil Android ou via le navigateur.

---

## Structure du projet

Pour faciliter la lecture et la maintenance, le code a été découpé de manière modulaire :
* **`index.html`** : Squelette minimal de l'application (contient l'en-tête, la barre de navigation et les conteneurs d'onglets).
* **`css/app.css`** : Regroupe l'intégralité des styles graphiques et du design sombre.
* **`js/app.js`** : Fichier principal qui initialise et charge les différents composants.
* **`js/modules/`** : Contient la logique et la structure HTML propre à chaque onglet :
  * `permissions.js` : Gère les autorisations SMS et les cartes SIM.
  * `smsList.js` : Recherche les SMS avec filtres (regex, tri, date).
  * `live.js` : Intercepte les nouveaux SMS reçus en temps réel.
  * `otp.js` : Capte les codes de validation temporaires (OTP).
  * `send.js` : Envoie des SMS (directement ou via l'application native).
  * `logger.js` : Affiche l'historique des appels d'API.
  * `ui.js` : Affiche les cartes SMS, les alertes et les badges.
  * `helpers.js` : Contient des fonctions utilitaires (formatage de date, échappement HTML).

---

## Tester l'application

Les fonctionnalités SMS natives (lecture, envoi direct, détection automatique) requièrent obligatoirement un **appareil physique Android** ou un émulateur configuré.

### 1. Lancer le serveur local
Pour lancer l'application sur votre navigateur (les fonctionnalités natives y seront simulées ou indisponibles) :
```bash
npm install
npm run start
```

### 2. Lancer sur Android
Pour compiler et tester l'application sur un vrai téléphone :
```bash
# Synchroniser le code web vers le dossier Android
npx cap sync

# Ouvrir le projet dans Android Studio pour le lancer sur un appareil
npx cap open android
```

---

## Guide d'utilisation des Onglets

* **🔐 Permissions** : Permet de demander les autorisations requises par Android. Vous pouvez aussi y inspecter vos cartes SIM (Dual SIM).
* **📬 SMS List** : Permet de rechercher des SMS avec des filtres avancés (expéditeur, date, limite, tri). Cochez "Mode brut" pour voir toutes les colonnes système d'Android.
* **📡 Live** : Lance l'écouteur en tâche de fond. Envoyez un SMS à l'appareil pour le voir s'afficher en direct.
* **🔑 OTP** : Permet de tester l'interception automatique de codes de validation (sans demander de permissions SMS) grâce aux outils Google.
* **✉️ Envoyer** : Permet d'envoyer un SMS, soit en arrière-plan (mode direct), soit en ouvrant votre application de messagerie habituelle (mode indirect).
* **📋 Logs** : Affiche en temps réel la trace des appels d'API effectués sur le plugin pour faciliter le débogage.
