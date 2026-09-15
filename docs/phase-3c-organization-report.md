# Phase 3c — module `organization` — Rapport de sortie d'incrément

Premier découpage domaine/persistance de toute la migration, sur le module
`organization` (`City`, `Organization`, `Site`, `Warehouse`) comme pilote,
choisi pour l'absence d'invariants métier sur ses entités et pour tester la
technique avant de l'étendre aux 17 autres agrégats. Périmètre strictement
limité à ce module, conformément à l'instruction.

## 1. Découverte préalable à toute écriture de code

Avant d'écrire quoi que ce soit, une revue du plan proposé (voir échange
précédent) a établi que `Organization` et `Site` ne sont pas seulement
consommées via `organization.application.dto` (déjà exposé en
`@NamedInterface`, Phase 1) : elles sont référencées **en direct par
`@ManyToOne`**, comme type Java littéral, depuis les entités JPA d'autres
modules :

- `Site` : `sales.Sale`, `sales.CustomerOrder`, `inventory.Stock`,
  `inventory.StockMovement`, `inventory.application.impl.InventoryFacadeImpl`,
  `purchasing.PurchaseOrder`, `transfers.StockTransfer` (7 fichiers).
- `Organization` : `catalog.Article`, `catalog.Category` (2 fichiers).

`City` et `Warehouse` n'ont, eux, aucun consommateur externe (confirmé par
recherche exhaustive avant de commencer).

Conséquence technique : Hibernate exige que le type effectivement référencé
par un `@ManyToOne` externe reste lui-même mappé comme entité. Créer une
classe JPA séparée sous un autre nom/paquet (option "classes + mapper")
aurait donc nécessité de modifier l'import et le type de champ dans ces 9
fichiers — explicitement interdit pour cet incrément. Seule l'option
`orm.xml` permet de retirer les annotations `jakarta.persistence` de la
classe **sans changer son FQCN ni son paquet**, donc sans toucher un seul
fichier hors `organization`.

## 2. Étape 1 : spike sur `Organization` seule

`Organization` choisie en premier : aucune relation (`@ManyToOne`), un seul
`@Embedded`, cas le plus simple de la chaîne `Warehouse -> Site -> City ->
Organization`.

- Colonnes réelles vérifiées dans `V1__initial_schema.sql` +
  `V5__customer_supplier_and_organization_contact_fields.sql` avant
  d'écrire le XML (pas de confiance aveugle dans les noms d'annotations
  existants) — détail notable : `@Column(name = "taxCode")` (camelCase)
  correspond en réalité à la colonne physique `tax_code` (snake_case),
  transformée par la stratégie de nommage physique de Spring Boot. Pour
  éliminer toute dépendance à ce mécanisme implicite, `orm.xml` déclare le
  nom de colonne physique exact (`tax_code`) directement, sans compter sur
  une transformation identique côté XML.
- `META-INF/orm.xml` créé avec le mapping minimal de `Organization`
  (table, 8 colonnes scalaires, 1 `@Embedded`) ; annotations retirées de
  `Organization.java` (Lombok et `extends AbstractEntity` inchangés).
- **Testé sans aucune configuration Spring Boot additionnelle** (pas de
  `spring.jpa.mapping-resources`), pour vérifier l'auto-découverte comme
  demandé.

**Résultat : succès sans surprise.** `OrganizationModuleIntegrationTest`
(4 tests, dont plusieurs appellent réellement `organizationService.save(...)`,
donc un vrai `INSERT` via l'entité re-mappée) passe au vert du premier
coup. `META-INF/orm.xml` est auto-découvert par l'auto-configuration JPA de
Spring Boot sans propriété `spring.jpa.mapping-resources` — **la ligne de
configuration anticipée dans le plan n'a finalement pas été nécessaire**.
`./mvnw clean verify` complet ensuite : 129 tests, seul l'échec déjà connu
(`domain_must_not_depend_on_jakarta_persistence`) subsiste, désormais à 261
violations au lieu de 272 (les 11 d'`Organization` disparues), aucune
régression ailleurs.

Aucune surprise de configuration au-delà de ce qui était anticipé : passage
direct à l'étape 2, comme prévu.

## 3. Étape 2 : extension à `City`, `Site`, `Warehouse`

Même traitement, même fichier `orm.xml` complété avec les 3 entités
restantes. Colonnes/contraintes vérifiées de la même façon dans
`V1__initial_schema.sql` (`site.code` et `warehouse.code` uniques,
`warehouse.site_id` unique — repris tels quels dans le XML). Annotations
retirées des 3 fichiers `.java` correspondants ; Lombok, `extends
AbstractEntity`, noms de champs strictement inchangés.

**Résultat : succès, sans itération de correction nécessaire.**
`OrganizationModuleIntegrationTest` + `OrganizationControllersHttpIntegrationTest`
(7 tests au total, couvrant création/suppression/garde-fous des 4 entités,
y compris le contrôle `WarehouseValidator` "le site doit être de type
ENTREPOT" et les guards de suppression City/Site/Warehouse) : tous verts.

## 4. Critère de sortie — vérifié point par point

| Critère | Résultat |
|---|---|
| Violations `domain_must_not_depend_on_jakarta_persistence` sur les 4 classes organization disparues | **Vérifié** — `grep` sur le rapport de violation post-fix : 0 occurrence de `organization.domain.model.{Organization,City,Site,Warehouse}`. Total passé de 272 à 227 (45 = les 4 classes × leurs colonnes/relations respectives). |
| Les 17 autres agrégats restent rouges (hors périmètre) | **Vérifié** — la règle échoue toujours, avec 227 violations restantes sur les 17 autres entités, inchangées. |
| Tous les `@SpringBootTest` démarrent et passent sans modification d'assertion | **Vérifié** — `./mvnw clean verify` : 129 tests, 1 seul échec (la règle JPA elle-même, attendue), 0 erreur de démarrage de contexte, 0 assertion modifiée dans un test existant. |
| Les 6 consommateurs externes de `Site` et les 2 d'`Organization` non touchés | **Vérifié** — `git diff --quiet` sur les 9 fichiers (7 pour Site en comptant `InventoryFacadeImpl`, 2 pour Organization) : tous rapportés `UNCHANGED`. `git status --short` hors `organization/` et `META-INF/` : vide. |
| `catalog.Article` non touché | **Vérifié** — inclus dans la vérification ci-dessus. |

## 5. Fichiers modifiés

**Production**, tous dans `organization` sauf le nouveau fichier de mapping
(qui n'appartient à aucun module au sens Java, c'est une ressource globale
`src/main/resources`) :
- `organization/domain/model/Organization.java`
- `organization/domain/model/City.java`
- `organization/domain/model/Site.java`
- `organization/domain/model/Warehouse.java`
- `src/main/resources/META-INF/orm.xml` (nouveau)

**Aucun fichier de test modifié ou ajouté** — les 7 tests
`OrganizationModuleIntegrationTest`/`OrganizationControllersHttpIntegrationTest`
existants constituent le test de non-régression demandé (un contexte qui ne
démarre plus, ou une entité mal mappée, aurait été détecté immédiatement).

**Docs** :
- `docs/migration-notes.md` — l'entrée Phase 2 sur la séparation JPA/domaine
  mise à jour : 4/21 résolues, 17 restantes, et le constat structurel
  (`Organization`/`Site` référencées en JPA direct par d'autres modules) noté
  explicitement pour informer le choix de technique des prochains modules —
  en particulier `catalog.Article`, qui a le même profil.

## 6. Ce qui a été appris pour la suite (pas une décision, un constat)

Le choix (a) classes séparées + mapper vs (b) `orm.xml` n'est pas une
question de goût : il dépend d'un fait vérifiable — est-ce que d'autres
modules référencent cette entité via un `@ManyToOne`/`@OneToOne` **direct**
(pas seulement via `application.dto`) ? Si oui, seul `orm.xml` évite de
toucher ces modules. `catalog.Article` a exactement ce profil (référencé en
JPA direct par `inventory`, `purchasing`, `sales`, `transfers` — 4 modules).
Les autres agrégats sans consommateur externe direct pourraient, eux,
utiliser l'une ou l'autre technique sans cette contrainte — à réévaluer au
cas par cas, pas en appliquant `orm.xml` par défaut partout.

## 7. Zones d'ombre / décisions prises en cours de route

Aucune ambiguïté fonctionnelle. Un point technique mérite d'être noté pour
la suite plutôt que pour validation immédiate : le mapping XML déclare les
noms de colonnes physiques explicitement plutôt que de compter sur la
stratégie de nommage Spring Boot pour transformer un nom "logique" — plus
verbeux, mais élimine une source d'erreur silencieuse (un mapping XML
utilisant un nom logique qui se transforme différemment de l'annotation
équivalente serait passé inaperçu jusqu'à l'échec de `ddl-auto=validate`
au démarrage). Pattern à reconduire pour les modules suivants.

## 8. Prochaine étape

Module `organization` terminé et vérifié vert sur son périmètre.
**N'enchaîne sur aucun autre module sans feu vert explicite** — le choix du
prochain module (catalog, avec son profil `Article` déjà identifié comme
similaire à `Site`, ou un module plus simple d'abord) reste à décider après
ce rapport.
