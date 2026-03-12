# ⚔️ TownyConflict

![Build](https://github.com/TON_USERNAME/TownyConflict/actions/workflows/build.yml/badge.svg)
![Java](https://img.shields.io/badge/Java-17-orange)
![Paper](https://img.shields.io/badge/Paper-1.20.4-blue)

Plugin de guerre avancé pour Towny — les **towns** se déclarent la guerre, les **membres de leur nation** peuvent les renforcer, et des **mercenaires** peuvent être recrutés contre de l'argent.

---

## 📦 Dépendances

| Plugin | Rôle |
|---|---|
| [Towny](https://github.com/TownyAdvanced/Towny) | Requis — gestion des towns/nations |
| [Vault](https://github.com/MilkBowl/Vault) | Requis — économie |

---

## 🚀 Installation

1. Télécharger le `.jar` depuis les [Releases](../../releases) ou les [Actions](../../actions)
2. Placer dans votre dossier `plugins/`
3. Redémarrer le serveur
4. Configurer `plugins/TownyConflict/config.yml`
5. `/tc reload` pour appliquer les changements

---

## ⚔️ Fonctionnalités

### Guerres entre Towns
- Déclaration avec coût configurable
- 24h de grâce avant le 1er assaut
- Max 2 guerres simultanées (configurable)
- Cooldown de redéclaration contre la même town

### Options configurables par les joueurs
- ✅ Renforts nationaux (quota selon taille de la nation)
- ✅ Mercenaires autorisés
- ✅ Nations alliées autorisées

### Conditions de victoire (au choix)
- Nombre de victoires d'assauts
- Points de guerre cumulés
- Limite de temps (le camp en tête gagne)

### Récompenses de guerre (configurables)
- Transfert d'argent (% de la banque)
- Revendication de plots
- Vassalisation temporaire
- Non-agression imposée

### Assauts — Push & Hold
Système en **3 points séquentiels** dans le territoire défenseur :
1. **Point Frontière** (10 min)
2. **Point Centre** (10 min)
3. **Point Mairie** (15 min, valeur doublée)

Chaque phase se gagne en contrôlant la zone de capture + kills.

### Mercenaires
- Recrutement via `/tc merc hire <joueur> <montant>`
- 50% à la signature, 50% à la victoire
- Désertion = cooldown 3 jours

### Système de Réputation
- Impacte le coût de déclaration de guerre
- Influence la disponibilité des mercenaires
- Visible publiquement

### Diplomatie
- Traité de paix
- Non-agression
- Cessez-le-feu temporaire

---

## 🎮 Commandes

| Commande | Description |
|---|---|
| `/tc` | Ouvre le menu principal |
| `/tc war declare <town>` | Déclarer une guerre (GUI) |
| `/tc war status` | Voir ses guerres actives |
| `/tc war surrender` | Capituler |
| `/tc assault start <town>` | Lancer un assaut |
| `/tc assault join <town>` | Rejoindre en renfort |
| `/tc merc hire <joueur> <montant>` | Recruter un mercenaire |
| `/tc merc accept/refuse` | Répondre à une offre |
| `/tc treaty` | Menu des traités |
| `/tc reload` | Recharger la config (admin) |
| `/tc admin endwar <t1> <t2>` | Terminer une guerre (admin) |
| `/tc admin setreputation <town> <val>` | Définir la réputation (admin) |

---

## ⚙️ Configuration

Tout est configurable dans `config.yml` **sans recompiler** :

```yaml
war:
  declaration_cost: 500.0
  grace_period_hours: 24
  max_simultaneous_wars: 2
  allow_civil_war: false

assault:
  cooldown_hours: 2
  min_defenders_online: 2
  phases:
    point1:
      duration_seconds: 600
      capture_threshold: 15

mercenaries:
  max_per_team: 3
  upfront_payment_percent: 50

# Activer/désactiver chaque feature individuellement
war:
  options:
    allow_mercenaries:
      enabled: true
      default: true
```

---

## 🔨 Compilation

### Prérequis
- Java 17+
- Maven 3.8+

### Compiler localement
```bash
git clone https://github.com/TON_USERNAME/TownyConflict.git
cd TownyConflict
mvn clean package
# Le JAR est dans target/TownyConflict-1.0.0.jar
```

### Via GitHub Actions
Chaque push sur `main` compile automatiquement le plugin.
Le JAR est disponible dans l'onglet **Actions → Build TownyConflict → Artifacts**.

Lors d'une **Release GitHub**, le JAR est automatiquement attaché.

---

## 📁 Structure du projet

```
TownyConflict/
├── .github/workflows/build.yml   ← CI/CD GitHub Actions
├── pom.xml
└── src/main/
    ├── resources/
    │   ├── plugin.yml
    │   └── config.yml
    └── java/fr/townyconflict/
        ├── TownyConflict.java
        ├── commands/TCCommand.java
        ├── gui/                   ← Tous les menus in-game
        ├── managers/              ← Logique métier
        ├── models/                ← War, Assault, WarReward
        ├── listeners/
        └── utils/
```

---

## 📜 Licence

MIT — libre d'utilisation et de modification.
