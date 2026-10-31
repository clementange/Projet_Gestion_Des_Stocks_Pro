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
| `services/auth/ApplicationUserDetailsService` | `identity/infrastructure/security/ApplicationUserDetailsService` |

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

Aucun importateur hors du cluster lui-même ; les modules correspondants servent déjà ces données.

| Classe supprimée | Module qui la remplace |
|---|---|
| `model/Client`, `dto/ClientDto` | `sales.domain.model.Customer` |
| `model/Fournisseur` | `purchasing.domain.model.Supplier` |
| `model/Ventes`, `dto/VentesDto` | `sales.domain.model.Sale` |
| `model/CommandeClient`, `dto/CommandeClientDto`, `model/EtatCommande` | `sales.domain.model.CustomerOrder` + `CustomerOrderStatus` |
| `model/CommandeFournisseur` | `purchasing.domain.model.PurchaseOrder` |
| `model/Entreprise`, `repository/EntrepriseRepository` | `tenant.domain.model.Tenant` |

## 5. Historique antérieur à la migration → `legacy` (sous-lot 4e-d)

**CONSERVÉES** : elles seules portent l'historique d'avant la migration (voir §Méthode).

| Classe | Destination | Pourquoi elle ne peut pas disparaître |
|---|---|---|
| `model/LigneVente`, `repository/LigneVenteRepository`, `dto/LigneVenteDto` | `legacy/` | `GET /articles/historique/vente/{id}` |
| `model/LigneCommandeClient` + repository + dto | `legacy/` | `GET /articles/historique/commandeclient/{id}` |
| `model/LigneCommandeFournisseur` + repository + dto | `legacy/` | `GET /articles/historique/commandefournisseur/{id}` |

Elles sont aussi lues par `ArticleServiceImpl.delete`, qui refuse de supprimer un article encore
référencé par une ligne historique. Les placer dans `sales`/`purchasing` créerait un cycle
`catalog → sales` (sales dépend déjà légitimement de `catalog`) : d'où un module `legacy` neutre,
dont dépendent `catalog`, `sales` et `purchasing`, et qui matérialise la dette restante sous un nom
explicite au lieu de la disperser.

## 6. Interceptor

`interceptor/Interceptor` (réécriture SQL d'isolation multi-tenant) est **hors périmètre 4e** par
le paquet, mais le prompt demandait de tester s'il est devenu superflu. Résultat et décision :
voir `docs/phase-4e-report.md` §Interceptor.
