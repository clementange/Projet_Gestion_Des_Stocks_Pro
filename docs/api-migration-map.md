# Carte de migration du legacy plat (Phase 4e)

Inventaire exhaustif des **43 classes** des paquets plats
(`model/`, `dto/`, `repository/`, `services/`, `controller/`) au début de la Phase 4e, avec pour
chacune sa destination. Les paquets `validator/`, `interceptor/`, `utils/`, `exception/` et
`config/` sont **hors périmètre 4e** (ils ne font pas partie des paquets plats listés par
`CLAUDE.md`), sauf les classes qui y deviennent mortes du fait de 4e.

## Méthode

Chaque classe a été classée à partir de ses **importateurs réels** (`import
com.kfokam48.gestiondestock.<paquet>.<Classe>;`), et non d'une lecture de nom. Trois verdicts :

- **DÉPLACÉE** — toujours utilisée, et doit l'être : va dans le module propriétaire.
- **SUPPRIMÉE** — plus aucun importateur hors du cluster legacy lui-même.
- **CONSERVÉE** — ne peut pas disparaître sans perte fonctionnelle ; la raison est donnée.

Un fait structurant découvert pendant l'investigation : les trois tables plates `lignevente`,
`lignecommandeclient` et `lignecommandefournisseur` contiennent **l'historique antérieur à la
migration**, que les tables des modules ne contiennent pas. Les endpoints
`/articles/historique/**` fusionnent les deux sources. Supprimer ces entités ferait **disparaître
des données de la réponse HTTP** : ce serait une régression fonctionnelle, pas un nettoyage. Elles
sont donc déplacées dans un module `legacy` explicite, et non supprimées — écart assumé par rapport
à l'objectif « vider », documenté ici et dans `docs/phase-4e-report.md`.

## 1. Briques transverses → `shared` (sous-lot 4e-a)

Ces quatre types sont importés par 9 à 24 fichiers de modules. Ce ne sont pas des adaptateurs en
extinction : ce sont des briques partagées mal rangées.

| Classe | Destination | Importateurs modules |
|---|---|---|
| `model/AbstractEntity` | `shared/AbstractEntity` | 23 — super-classe mappée de chaque entité JPA |
| `model/Adresse` | `shared/Adresse` | 6 — embeddable d'adresse |
| `model/auth/ExtendedUser` | `shared/ExtendedUser` | 23 — principal Spring Security |
| `dto/AdresseDto` | `shared/AdresseDto` | 9 — projection HTTP (contrat externe, forme JSON figée) |

Placées dans la **racine** du paquet `shared` : la racine d'un module est son interface publique
par défaut pour Spring Modulith, ce qui évite un `@NamedInterface` sur des briques dont toute la
raison d'être est d'être partagées.

## 2. Authentification → `identity` (sous-lot 4e-b)

| Classe | Destination |
|---|---|
| `controller/AuthenticationController` | `identity/presentation/rest/AuthenticationController` |
| `controller/api/AuthenticationApi` | `identity/presentation/rest/AuthenticationApi` |
| `dto/auth/AuthenticationRequest` | `identity/application/dto/AuthenticationRequest` |
| `dto/auth/AuthenticationResponse` | `identity/application/dto/AuthenticationResponse` |
| `services/auth/ApplicationUserDetailsService` | `config/ApplicationUserDetailsService` — **pas** dans `identity`, voir ci-dessous |

**Le déplacement a révélé un cycle que le paquet plat masquait.** `ApplicationUserDetailsService`
compose `identity` (`UserService`, `UserRoleAssignmentService`) **et** `tenant` (`TenantService`,
pour résoudre `organizationId` depuis `Tenant.idEntreprise`). Or `tenant` dépend déjà de
`identity` : `TenantRegistrationServiceImpl` appelle `UserService`. Le mettre dans `identity`
crée donc un vrai cycle `identity ↔ tenant`, que `ModularityTests` et `ArchitectureRulesTest` ont
immédiatement rejeté — le paquet `services/auth`, exclu du graphe de modules, se contentait de le
cacher. Sa place est la **plomberie technique de sécurité** (`config/`), aux côtés de
`SecurityConfiguration` et `ApplicationRequestFilter` qui l'utilisent tous deux : c'est un
adaptateur d'authentification, il n'appartient à aucun des deux modules. Résoudre le cycle
`identity ↔ tenant` lui-même est une décision d'architecture distincte, **hors périmètre 4e** et
mise en backlog.

`POST /auth/authenticate` est **vivant et public** (c'est lui qui établit l'identité) : seul
l'emplacement du code change, l'URL et le contrat JSON sont inchangés.

## 3. Contrat legacy `/utilisateurs/**` → supprimé (sous-lot 4e-c)

`/users/**` est le contrat canonique depuis la Phase 4a et couvre chaque route de
`/utilisateurs/**`. Les rapports 5b-2h et 5c annonçaient déjà la suppression de ces routes en 4e —
c'est aussi ce qui ferme les deux derniers trous de permission du dépôt (voir
`docs/rbac-matrix.md` §4).

| Route supprimée | Remplacement canonique |
|---|---|
| `POST /utilisateurs/create` | `POST /users/create` |
| `POST /utilisateurs/update/password` | `POST /users/{id}/password` |
| `GET /utilisateurs/{id}` | `GET /users/{id}` |
| `GET /utilisateurs/find/{email}` | `GET /users/find/{email}` |
| `GET /utilisateurs/all` | `GET /users/all` |
| `DELETE /utilisateurs/delete/{id}` | `DELETE /users/delete/{id}` |

Classes supprimées : `controller/UtilisateurController`, `controller/api/UtilisateurApi`,
`services/UtilisateurService`, `services/impl/UtilisateurServiceImpl`, `dto/UtilisateurDto`,
`dto/ChangerMotDePasseUtilisateurDto`, `dto/RolesDto`, `validator/UtilisateurValidator`
(devenu mort), `repository/UtilisateurRepository`, `model/Utilisateur`, `model/Roles`.

**Différence de contrat à connaître** : `UtilisateurDto` exposait le champ mal orthographié
`dateDeNaissance`/`motDePasse` déjà repris à l'identique par `UserDto`, mais `/utilisateurs/**`
renvoyait aussi `roles` (liste `RolesDto`), que `/users/**` ne renvoie pas — le RBAC passe désormais
par `/user-role-assignments/**`.

## 4. Cluster legacy mort → supprimé (sous-lot 4e-d)

**Correction par rapport à la première version de cette carte.** Elle annonçait la suppression de
neuf entités. En lisant les mappings JPA plutôt que les seuls importateurs, il s'avère que les
trois entités de ligne portent un `@ManyToOne` vers leur parent (`LigneVente → Ventes`,
`LigneCommandeClient → CommandeClient → Client` + `EtatCommande`,
`LigneCommandeFournisseur → CommandeFournisseur → Fournisseur`), et que `LigneVenteDto` **expose
`vente` dans son JSON**. Tout ce cluster est donc une seule pièce : il est déplacé, pas supprimé.
Le code a tranché contre le plan.

Ne restaient réellement morts que les cinq doublons de tables déjà servies par un module :

| Classe supprimée | Module qui la remplace |
|---|---|
| `model/Entreprise`, `repository/EntrepriseRepository` | `tenant.domain.model.Tenant` (même table `entreprise`) |
| `model/Utilisateur`, `repository/UtilisateurRepository` | `identity.domain.model.User` (même table `utilisateur`) |
| `model/Roles` | `identity.domain.model.Role` + `UserRoleAssignment` |

`Utilisateur` et `Roles` ne sont devenus morts qu'après 4e-c.

## 5. Historique antérieur à la migration → `legacy` (sous-lot 4e-d)

**CONSERVÉES** : elles seules portent l'historique d'avant la migration (voir §Méthode). 18 classes
déplacées vers `legacy/` : `Client`, `Fournisseur`, `Ventes`, `CommandeClient`,
`CommandeFournisseur`, `EtatCommande`, les trois `Ligne*`, leurs six DTO et leurs trois
repositories.

| Endpoint servi | Classes nécessaires |
|---|---|
| `GET /articles/historique/vente/{id}` | `LigneVente` + `Ventes` + `LigneVenteDto` + `VentesDto` |
| `GET /articles/historique/commandeclient/{id}` | `LigneCommandeClient` + `CommandeClient` + `Client` + `EtatCommande` + DTO |
| `GET /articles/historique/commandefournisseur/{id}` | `LigneCommandeFournisseur` + `CommandeFournisseur` + `Fournisseur` + DTO |

### Le cycle `catalog ↔ legacy`, et son inversion

`ArticleServiceImpl.delete` refuse de supprimer un article encore référencé par une ligne
historique : `catalog` lisait donc les trois repositories legacy. Mais `legacy` dépend de
`catalog` (ses entités portent un `@ManyToOne Article`, ses DTO exposent un `ArticleDto`). Faire de
`legacy` un vrai module révèle le cycle — invisible tant que ces classes vivaient dans des paquets
plats exclus du graphe.

Résolu par **inversion de dépendance** : `catalog` déclare
`catalog.domain.port.ArticleUsagePort` (`Optional<String> findBlockingHistoricalUsage(Long)`) et
`legacy.LegacyArticleUsageAdapter` l'implémente. `catalog` ne connaît plus `legacy` ; les trois
messages d'erreur HTTP sont inchangés au caractère, et l'ordre de vérification (commandes client,
puis fournisseur, puis ventes) est conservé car il décide du message renvoyé quand un article
apparaît dans plusieurs historiques.

`shared` et `legacy` sont des **vrais modules**, volontairement absents de la liste d'exclusion de
`ModuleBoundaries` : c'est précisément ce qui a permis de détecter ce cycle.

## 6. Interceptor

`interceptor/Interceptor` (réécriture SQL d'isolation multi-tenant) est **hors périmètre 4e** par
le paquet, mais le prompt demandait de tester s'il est devenu superflu. Résultat et décision :
voir `docs/phase-4e-report.md` §Interceptor.
