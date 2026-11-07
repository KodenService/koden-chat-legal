# Koden Chat — prototype Android

Messagerie de téléphone à téléphone, sans internet ni réseau opérateur, via Bluetooth et Wi‑Fi Direct (Google Nearby Connections).

## Ce que fait le prototype

- Pseudo choisi au premier lancement, identifiant généré sur le téléphone (aucun compte).
- Détection et connexion automatiques des téléphones proches.
- Salon public « À proximité » et messages privés vers un contact déjà croisé.
- Relais multi-sauts : jusqu'à 7 sauts, anti-doublon par identifiant de message.
- Stocker et relayer : chaque nouveau voisin reçoit les messages de moins de 48 h.
- Service au premier plan : le téléphone continue de relayer écran éteint.

**Limites assumées du prototype** : messages privés non chiffrés, stockage en mémoire (perdu au redémarrage), pas d'accusé de réception. Tout cela est prévu en V1 (voir le cahier des charges).

## Compiler et installer

1. Installez [Android Studio](https://developer.android.com/studio) (version Koala ou plus récente).
2. Ouvrez le dossier `koden-chat` avec **File > Open**. Android Studio télécharge Gradle et les dépendances automatiquement (une connexion internet est nécessaire pour cette étape uniquement).
3. Branchez un téléphone Android 8.0 ou plus, avec le mode développeur et le débogage USB activés.
4. Cliquez sur **Run**. Répétez sur 2 à 4 autres téléphones.

Pour distribuer l'APK aux testeurs : **Build > Build App Bundle(s) / APK(s) > Build APK(s)**, puis partagez le fichier `app-debug.apk`.

## Compiler en ligne avec GitHub (sans Android Studio)

1. Créez un compte gratuit sur [github.com](https://github.com), puis un dépôt **privé** nommé `koden-chat` (bouton **New**).
2. Sur un ordinateur, dans le dépôt : **Add file > Upload files**, puis glissez tout le **contenu** du dossier `koden-chat` (dossiers `app` et `gradle`, fichiers `gradlew`, `build.gradle.kts`, etc.). Cliquez **Commit changes**.
3. Le dossier caché `.github` n'est souvent pas envoyé par glisser-déposer. Créez-le à la main : **Add file > Create new file**, tapez le nom `.github/workflows/build-apk.yml`, collez le contenu du fichier `build-apk.yml` fourni, puis **Commit changes**.
4. Ouvrez l'onglet **Actions** : la compilation démarre seule (5 à 10 minutes). Une coche verte indique la réussite.
5. Cliquez sur l'exécution réussie, puis en bas sur **koden-chat-apk** pour télécharger un zip contenant `app-debug.apk`.
6. Envoyez `app-debug.apk` sur les téléphones (WhatsApp, câble, Bluetooth) et ouvrez-le. Android demandera d'autoriser l'installation d'applications inconnues : acceptez pour ce fichier.

En cas de croix rouge, ouvrez l'étape en échec et copiez le message d'erreur pour correction.

## Tester le relais multi-sauts

1. Installez l'application sur 3 téléphones A, B et C.
2. Placez A et C assez loin l'un de l'autre pour qu'ils ne se détectent pas (deux pièces séparées, ou 80 m et plus en extérieur), et B au milieu.
3. Vérifiez sur B que l'en-tête affiche « 2 voisin(s) direct(s) ».
4. Envoyez un message depuis A : il doit apparaître sur C en passant par B.
5. Testez le stocker-et-relayer : éteignez le Bluetooth de C, envoyez depuis A, puis rallumez C près de B.

## Organisation du code

| Fichier | Rôle |
| --- | --- |
| `MeshMessage.kt` | Format du message (JSON) : id, expéditeur, destinataire, texte, date, sauts restants |
| `MeshManager.kt` | Cœur du maillage : connexions Nearby, relais, anti-doublon, stocker-et-relayer |
| `MeshService.kt` | Service au premier plan qui garde le maillage actif |
| `Identity.kt` | Identifiant et pseudo locaux |
| `MainActivity.kt` | Interface (Jetpack Compose) : pseudo, autorisations, discussion |

## Prochaines étapes (V1)

- Chiffrement de bout en bout des messages privés (libsodium / Lazysodium).
- Stockage persistant chiffré (Room + SQLCipher).
- Ajout de contacts par QR code, accusés de réception, groupes privés.
- Mode économie de batterie (recherche de voisins par intermittence).
