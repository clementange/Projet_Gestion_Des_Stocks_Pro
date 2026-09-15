/**
 * Surface publique (DTO) du module {@code catalog} : {@code ArticleDto}/{@code CategoryDto} sont
 * legitimement references par d'autres modules (inventory, purchasing, sales, transfers) pour
 * representer un article/une categorie sans dependre du modele de domaine.
 */
@org.springframework.modulith.NamedInterface("dto")
package com.kfokam48.gestiondestock.catalog.application.dto;
