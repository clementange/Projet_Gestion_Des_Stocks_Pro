# Phase 5g — Fuite inter-tenant sur `GET /reporting/stock/global` — Rapport

## 1. Constat vérifié, et il est pire que la description

Le constat du prompt est exact. `ReportingServiceImpl.getGlobalStockSummary` vérifiait
`REPORTING_VIEW` en portée `GLOBAL` puis retournait `inventoryFacade.findAllStock()`, soit
`stockRepository.findAll()` — **sans filtre d'organisation en SQL ni en Java**. Tout admin de
tenant (qui possède `REPORTING_VIEW`/`GLOBAL` via l'amorçage `ADMINISTRATEUR`) lisait le stock de
tous les autres.

Le rouge montre que la fuite dépasse les quantités. Extrait du corps reçu par l'admin de A :

```json
{"id":2,"article":{"id":2,"codeArticle":"ART-glob-B-b6c1451e", ...
 "organization":{"id":2,"name":"Societe glob-B","email":"glob-B-...@test.local",
                 "phone":"+237600000108","taxCode":"CF-b4588b43",
                 "adresse":{"adresse1":"1 Rue","ville":"Douala","pays":"Cameroun"}}},
 "site":{"id":4,"code":"S-041c0428","name":"Entrepot",
         "city":{"id":4,"name":"Douala","organization":{...}}},
 "quantitePhysique":5.00,"quantiteDisponible":5.00}
```

`StockDto` sérialise l'article **et** le site en profondeur : A récupérait donc aussi le **nom, le
courriel, le téléphone, le code fiscal et l'adresse postale** de la société B, plus la
cartographie de ses sites. C'est une fuite de données d'entreprise, pas seulement d'inventaire.

### La cause de fond

`GLOBAL` signifie « toute **mon** organisation », pas « **toutes** les organisations ». La
permission décidait du **droit** de lire ; personne ne décidait du **périmètre** lu. C'est la même
confusion que celle documentée depuis 5b-2b sur `assignmentAppliesToScope` : une affectation
`GLOBAL` matche n'importe quel `scopeId`, mais **seulement à l'intérieur de son organisation**.

### Pourquoi la matrice ne l'a pas vu

Elle **excluait** l'endpoint, avec la justification `"scope GLOBAL, filtre par RBAC (5b-1)"` —
une affirmation **fausse**, écrite en 5b-1 et jamais revérifiée. Deux leçons :

1. Une exclusion de matrice est une **affirmation technique** qui doit être vérifiée comme du code.
   Celle-ci a survécu à 5b-2a … 5f parce que personne n'a relu sa justification.
2. Une sonde de **statut HTTP** n'aurait rien vu non plus : l'endpoint répond 200 à son
   propriétaire légitime. Il fallait une sonde de **contenu**.

## 2. Correctif — le filtre descend en SQL

```java
@Query("select s from Stock s where s.site.city.organization.id = :organizationId")
List<Stock> findAllByOrganizationId(@Param("organizationId") Long organizationId);

@Query("select s from Stock s where s.site.city.organization.id = :organizationId "
    + "and s.seuilAlerte is not null "
    + "and (s.quantitePhysique - s.quantiteReservee) <= s.seuilAlerte")
List<Stock> findLowStockByOrganizationId(@Param("organizationId") Long organizationId);
```

L'organisation d'une ligne de stock se lit par `Stock → Site → City → Organization` : la ligne
n'en porte pas en propre.

**`findLowStockByOrganizationId` reproduit `Stock.getQuantiteDisponible()` en SQL**
(`quantitePhysique - quantiteReservee`) parce que la quantité disponible est **calculée et non
persistée** — un `where s.quantiteDisponible <= ...` n'aurait pas compilé.

**La variante non filtrée a été supprimée, pas conservée.** `InventoryFacade.findAllStock()` et
`findLowStock()` deviennent `findAllStock(Long organizationId)` et `findLowStock(Long)`. Elles
n'avaient que `reporting` pour appelant, donc aucun coût de compatibilité — et c'est bien leur
existence qui a rendu la fuite possible : laisser une méthode non filtrée disponible, c'est
garantir qu'elle sera appelée un jour.

`getLowStockReport` **ne fuyait pas** : il filtrait déjà en Java via `canAccessSite`. Il chargeait
en revanche la table entière pour en jeter l'essentiel. Le filtre d'organisation est désormais en
SQL ; le filtre Java qui reste ne porte **que** sur le droit de l'utilisateur site par site, que
SQL ne peut pas décider.

## 3. Tests

### `reporting/GlobalStockReportIsScopedToOrganizationTest` (3 tests)

| Test | Vérifie |
|---|---|
| `globalStockReportMustNotExposeAnotherTenantStock` | A voit son stock, jamais celui de B |
| `everyLineOfTheGlobalReportBelongsToTheCallerOrganization` | **chaque** ligne porte un site de l'appelant ; échoue aussi si le rapport est vide, pour ne pas passer sur une liste vide |
| `lowStockReportMustNotExposeAnotherTenantStock` | idem sur `/reporting/stock/low` |

Le marqueur est le `codeArticle`, unique par tenant — pas un id, qui produirait des faux positifs
(un id de site peut coïncider avec un id d'article : erreur commise et corrigée en 5b-2h).

**Un de ces tests passait d'abord pour la mauvaise raison.** `seuilAlerte` n'est écrit par **aucun**
endpoint du projet : `/reporting/stock/low` renvoie donc toujours une liste vide, et
« le stock de B n'apparaît pas » était vrai sur un corps vide. Le test pose maintenant le seuil
directement en base et vérifie **d'abord** que A voit sa propre rupture.

### Matrice : exclusion remplacée par une sonde de contenu

`/reporting/stock/global` est désormais sondé en mode `NO_FOREIGN_DATA` (contenu, pas statut).
**Sonde vérifiée mordante** : en remettant temporairement `findAll()` dans la façade, la matrice
rapporte

```
GET /gestiondestock/v1/reporting/stock/global -> HTTP 200 expose une ressource
    d'un autre tenant (A-91559869)
```

puis redevient verte une fois le correctif remis. Une sonde qu'on n'a pas vue échouer ne prouve
rien.

`/reporting/stock/low` **reste exclu**, avec une justification corrigée : une sonde y serait
trompeuse puisque la réponse est toujours vide (`seuilAlerte` jamais écrit) — elle passerait sans
rien prouver. Son filtrage est couvert par le test dédié, qui pose le seuil.

## 4. Audit des `findAll()` sans paramètre d'organisation

17 appels dans `src/main`, classés par la méthode qui les contient et par l'accessibilité depuis un
contrôleur.

### Fuites réelles : 1, corrigée

| Site | Verdict |
|---|---|
| `InventoryFacadeImpl.findAllStock()` via `getGlobalStockSummary` | **FUITE — corrigée** |

### Pas de fuite — filtre appliqué juste après

Douze méthodes `findAll(Long organizationId)` (ou `callerTenantId`) chargent la table puis filtrent
en Java : `Tenant`, `Site`, `Warehouse`, `Organization`, `StockTransfer`, `CustomerOrder`, `Sale`,
`Customer`, `PurchaseOrder`, `Supplier`, `Category`, `Article`. **Aucune ne fuit** — le filtre suit
immédiatement. Dette de performance et de robustesse, voir §6.

### Pas de fuite — catalogue réellement global

| Endpoint | Pourquoi ce n'est pas une fuite |
|---|---|
| `GET /roles/all` → `roleService.findAll()` | `Role` **n'a aucune colonne d'organisation ni `idEntreprise`** (vérifié) : c'est un catalogue global par conception (5b-2c), gardé par `RBAC_MANAGE`. Il n'y a aucune donnée de tenant à fuiter |
| `GET /permissions/all` → `permissionService.findAll()` | idem |

### Pas de fuite — non atteignable

`UserServiceImpl.findAll()` (variante non filtrée) : aucun contrôleur ne l'appelle —
`UserController` utilise `findAll(principal.getIdEntreprise())`. Conservée pour les appelants
internes, comme documenté en 5b-2c.

**Mon détecteur a produit un faux positif** qu'il faut signaler : il cherchait `organizationId`
dans la signature et a classé `OrganizationServiceImpl.findAll(Long callerOrganizationId)` comme
non scopée à cause de la casse. Lecture du corps : elle filtre bien (self-only). Aucune conclusion
n'a été tirée du seul détecteur.

## 5. `CLAUDE.md` déplacé dans le dépôt

Il vivait **un niveau au-dessus de la racine git**, donc hors de tout commit : ses mises à jour
étaient invisibles pour qui clone le projet ou relit l'historique — écart signalé dans les rapports
L0 et de fin de lot. Il est désormais à `gestion-de-stock-api/CLAUDE.md`, suivi par git, et
Claude Code le découvre toujours (la recherche remonte depuis le répertoire de travail, qui est la
racine du dépôt).

Deux règles tirées de cet incrément y sont ajoutées :

- une permission de portée `GLOBAL` signifie « toute mon organisation », jamais « toutes les
  organisations » : elle décide du droit, jamais du périmètre ;
- le périmètre d'organisation se filtre **en SQL**, pas en Java après un `findAll()`.

## 6. Critères de sortie

| Critère | Statut |
|---|---|
| Rouge d'abord, montré | **Vérifié** — corps complet de la fuite en §1 |
| Filtre en SQL côté repository, pas en Java | **Vérifié** — 2 requêtes `@Query` |
| `findLowStock` ne charge plus toute la table | **Vérifié** |
| Exclusion de matrice retirée pour `/reporting/stock/global` | **Vérifié** |
| Sonde de **contenu**, et vérifiée mordante | **Vérifié** — §3 |
| Audit de tous les `findAll()` sans organisation | **Vérifié** — §4, 17 sites classés |
| `CLAUDE.md` déplacé et commité | **Vérifié** |
| Suite complète verte | **Vérifié** — voir §7 |
| Commits sans signature | **Vérifié** |

## 7. Limites assumées et backlog

- **`/reporting/stock/low` est un endpoint mort en pratique.** `seuilAlerte` n'est écrit par aucun
  endpoint ni aucun service : la colonne reste nulle, le rapport est toujours vide. Le correctif de
  périmètre est juste, mais la fonctionnalité est inachevée. **Hors périmètre 5g** (le prompt
  demandait le filtrage, pas la fonctionnalité) — à décider : exposer un endpoint de réglage du
  seuil, ou retirer le rapport.
- **Les 12 `findAll()` + filtre Java restent.** Ils ne fuient pas, mais c'est exactement la forme
  qui a produit cette fuite : un filtre dans une couche ultérieure est une protection qu'on peut
  oublier d'appliquer. Les descendre en SQL est un chantier mécanique (12 requêtes) qui supprimerait
  la classe de bug, pas seulement cette instance. **Recommandé, au backlog.**
- **Aucune règle automatique n'interdit un nouveau `repository.findAll()` dans un service.** Une
  règle ArchUnit le pourrait (interdire `findAll()` hors des méthodes prenant un `organizationId`),
  mais elle est délicate à écrire sans faux positifs sur les catalogues globaux. Non tentée ici.
- **Les autres justifications d'exclusion de la matrice n'ont pas été re-auditées une par une.**
  Celle de `/reporting/stock/global` était fausse ; rien ne garantit que les ~30 autres soient
  exactes. Un passage de relecture systématique est au backlog — c'est le vrai enseignement de cet
  incrément.
- **Pas d'audit de données.** Si l'API a déjà été exposée, impossible de savoir quels tenants ont
  consulté le stock des autres : l'endpoint ne journalise pas les lectures réussies. Seuls les refus
  sont journalisés (`log.warn`).
