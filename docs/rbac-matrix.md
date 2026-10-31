# Matrice RBAC — ressource × action × portée

État au terme de la **Phase 5c**. Cette matrice est la référence pour répondre à « qui a le droit
de faire quoi » ; elle est tenue à jour par le test d'inventaire
`architecture/WriteEndpointPermissionGateTest`, qui échoue si un endpoint d'écriture apparaît sans
être soit gardé, soit exclu avec justification.

## 1. Les deux contrôles, dans cet ordre

| Contrôle | Question posée | Réponse en cas de refus | Introduit en |
|---|---|---|---|
| **Appartenance** | « cette ressource est-elle dans MON organisation ? » | `*_NOT_FOUND` **masqué** (404), identique à un id inexistant | 5b-2a → 5b-2h |
| **Permission** | « ai-je le DROIT de faire cela, dans mon organisation ? » | `*_ACCESS_DENIED` (400) | **5c** |

**L'ordre est non négociable : appartenance → permission → écriture.** Une affectation de portée
`GLOBAL` matche n'importe quel `scopeId` demandé — mais seulement à l'intérieur de l'organisation
de l'affectation. `hasPermission` seul ne prouve donc **jamais** l'isolation inter-tenant : c'est
la classe de contournement trouvée en 5b-2b et retrouvée sur une douzaine d'actions en 5b-2h.
Inverser les deux contrôles la réintroduit, et fait de surcroît fuiter l'existence d'une ressource
d'autrui à travers le code d'erreur.

## 2. Portées (`ScopeType`)

| Portée | `scopeId` | Signification |
|---|---|---|
| `GLOBAL` | ignoré | toute l'organisation ; forme prise par l'affectation de l'admin à l'amorçage d'un tenant |
| `ORGANIZATION` | `organizationId` | référentiel d'organisation (catalogue, tiers, villes, sites, entrepôts, utilisateurs) |
| `SITE` | id du site | opérations attachées à un site (stock, commandes, transferts) |
| `WAREHOUSE` | id du site portant l'entrepôt | utilisé par le reporting |

## 3. Matrice

### Référentiel d'organisation — portée `ORGANIZATION`

| Ressource | Créer | Modifier | Supprimer |
|---|---|---|---|
| Article | `ARTICLE_CREATE` | `ARTICLE_UPDATE` | `ARTICLE_DELETE` |
| Catégorie | `CATEGORY_CREATE` | `CATEGORY_UPDATE` | `CATEGORY_DELETE` |
| Client | `CUSTOMER_CREATE` | `CUSTOMER_UPDATE` | `CUSTOMER_DELETE` |
| Fournisseur | `SUPPLIER_CREATE` | `SUPPLIER_UPDATE` | `SUPPLIER_DELETE` |
| Ville | `CITY_CREATE` | `CITY_UPDATE` | `CITY_DELETE` |
| Entrepôt | `WAREHOUSE_CREATE` | `WAREHOUSE_UPDATE` | `WAREHOUSE_DELETE` |
| Site | `SITE_CREATE` | *(aucun endpoint)* | `SITE_DELETE` **portée `SITE`** |
| Utilisateur | `USER_CREATE` | *(self-only)* | `USER_DELETE` |

`SITE_DELETE` est vérifié à la portée `SITE` (sur le site visé) et non `ORGANIZATION` : supprimer
**un** site est une opération locale, alors que créer un site ne peut pas l'être (le site n'existe
pas encore).

### Opérations de stock — portée `SITE`

| Action | Permission |
|---|---|
| Entrée de stock | `STOCK_RECEIVE` |
| Sortie de stock | `STOCK_ISSUE` |
| Correction d'inventaire | `STOCK_CORRECT` |

### Commande client — portée `SITE` (site de la commande)

| Action | Permission |
|---|---|
| Créer | `CUSTOMER_ORDER_CREATE` |
| Valider | `CUSTOMER_ORDER_VALIDATE` |
| Réserver | `CUSTOMER_ORDER_RESERVE` |
| Préparer | `CUSTOMER_ORDER_PREPARE` |
| Expédier | `CUSTOMER_ORDER_SHIP` |
| Livrer | `CUSTOMER_ORDER_DELIVER` |
| Annuler | `CUSTOMER_ORDER_CANCEL` |

### Commande fournisseur — portée `SITE`

| Action | Permission |
|---|---|
| Créer | `PURCHASE_ORDER_CREATE` |
| Valider | `PURCHASE_ORDER_VALIDATE` |
| Annuler | `PURCHASE_ORDER_CANCEL` |
| Réceptionner une ligne | `PURCHASE_ORDER_RECEIVE` |

### Transfert de stock — portée `SITE`

| Action | Permission | Site évalué |
|---|---|---|
| Créer | `STOCK_TRANSFER_CREATE` | origine |
| Demander | `STOCK_TRANSFER_REQUEST` | origine |
| Approuver | `STOCK_TRANSFER_APPROVE` | origine |
| Préparer | `STOCK_TRANSFER_PREPARE` | origine |
| Expédier | `STOCK_TRANSFER_SHIP` | origine |
| Réceptionner | `STOCK_TRANSFER_RECEIVE` | **destination** |
| Annuler | `STOCK_TRANSFER_CANCEL` | origine |

Le site évalué est celui qui porte la conséquence sur le stock : l'origine se désengage, la
destination encaisse. `STOCK_TRANSFER_RECEIVE` est donc, et reste, la seule permission de transfert
évaluée sur le site de destination.

### Vente — portée `SITE`

| Action | Permission |
|---|---|
| Créer | `SALE_CREATE` |

### Catalogue RBAC — portée `GLOBAL`

| Ressource | Lire | Écrire |
|---|---|---|
| Rôle, Permission | `RBAC_MANAGE` | **aucun endpoint HTTP** (catalogues globaux, retirés en 5b-2f/5b-2h) |
| Affectation de rôle | `RBAC_MANAGE` | `RBAC_MANAGE` + appartenance d'organisation par ligne (5b-2c) |

### Reporting — portée `SITE` / `WAREHOUSE`

| Action | Permission |
|---|---|
| Lire le stock d'un site, le stock global, les ruptures | `REPORTING_VIEW` |

## 4. Écritures sans permission métier, et pourquoi

| Endpoint | Raison |
|---|---|
| `POST /tenants/register` | amorçage public : s'exécute **avant** qu'aucun rôle n'existe |
| `DELETE /tenants/delete/{id}`, `POST /tenants/{id}/photo` | self-only (5b-2c) : l'identité du principal est la seule autorisation possible |
| `DELETE /organizations/delete/{id}` | self-only (5b-2c) |
| `POST /users/{id}/password`, `POST /users/{id}/photo` | self-only (5a / 5b-2c) ; aucun override admin n'a été introduit |
| `POST /media/upload` | ne porte aucune ressource métier |
| `POST /articles/{id}/photo`, `/clients/{id}/photo`, `/fournisseurs/{id}/photo` | appartenance vérifiée (5b-2h), **pas** de permission dédiée — dette consciente |
| `POST /utilisateurs/create`, `/utilisateurs/update/password`, `DELETE /utilisateurs/delete/{id}` | adaptateur legacy ; appartenance vérifiée (5b-2h) mais pas la permission — routes à supprimer en 4e |

## 5. Rôles standards — **proposés, non seedés**

**DÉCISION PAR DÉFAUT.** `V8__seed_write_permissions.sql` n'accorde les 34 permissions d'écriture
qu'au rôle **`ADMINISTRATEUR`** existant. Aucun rôle métier n'est créé : découper les
responsabilités d'une entreprise est une décision produit, et seeder des rôles non demandés
donnerait des droits à des utilisateurs sur la seule base d'une supposition. Les quatre rôles
ci-dessous sont une **recommandation d'implémentation**, à créer via
`POST /user-role-assignments/create` et le catalogue de rôles existant.

| Rôle proposé | Permissions | Portée conseillée |
|---|---|---|
| **Magasinier** | `STOCK_RECEIVE`, `STOCK_ISSUE`, `STOCK_CORRECT`, `PURCHASE_ORDER_RECEIVE`, `STOCK_TRANSFER_PREPARE`, `STOCK_TRANSFER_SHIP`, `STOCK_TRANSFER_RECEIVE`, `CUSTOMER_ORDER_PREPARE`, `CUSTOMER_ORDER_SHIP` | `SITE` (son site) |
| **Commercial** | `CUSTOMER_CREATE`, `CUSTOMER_UPDATE`, `SALE_CREATE`, `CUSTOMER_ORDER_CREATE`, `CUSTOMER_ORDER_VALIDATE`, `CUSTOMER_ORDER_RESERVE`, `CUSTOMER_ORDER_DELIVER`, `CUSTOMER_ORDER_CANCEL` | `SITE` (sa boutique) |
| **Acheteur** | `SUPPLIER_CREATE`, `SUPPLIER_UPDATE`, `PURCHASE_ORDER_CREATE`, `PURCHASE_ORDER_VALIDATE`, `PURCHASE_ORDER_CANCEL`, `STOCK_TRANSFER_CREATE`, `STOCK_TRANSFER_REQUEST`, `STOCK_TRANSFER_APPROVE` | `ORGANIZATION` |
| **Lecture seule** | `REPORTING_VIEW` | `GLOBAL` ou `SITE` |

## 6. Ajouter une permission — procédure obligatoire

1. Ajouter le code dans le service qui le vérifie (constante, pas de littéral dispersé).
2. **Créer une nouvelle migration Flyway** (jamais éditer un `V*.sql` existant) qui insère la
   permission **et** l'accorde à `ADMINISTRATEUR`, en `INSERT ... ON CONFLICT DO NOTHING`.
   Sans cela la permission est inobtenable et l'endpoint devient définitivement bloqué — c'est la
   leçon de V7, et `WriteEndpointPermissionGateTest` échoue désormais si on l'oublie.
3. Déclarer l'endpoint dans `GATED` (ou `EXCLUDED` avec justification) du même test.
4. Ajouter le code à `AbstractIntegrationTest.ALL_WRITE_PERMISSIONS` pour que les décors de test
   restent autorisés.
