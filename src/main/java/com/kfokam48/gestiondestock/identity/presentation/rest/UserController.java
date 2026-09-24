package com.kfokam48.gestiondestock.identity.presentation.rest;

import static com.kfokam48.gestiondestock.utils.Constants.APP_ROOT;

import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidOperationException;
import com.kfokam48.gestiondestock.identity.application.UserService;
import com.kfokam48.gestiondestock.identity.application.dto.ChangePasswordRequest;
import com.kfokam48.gestiondestock.identity.application.dto.UserDto;
import com.kfokam48.gestiondestock.media.application.dto.PhotoUrlRequest;
import com.kfokam48.gestiondestock.model.auth.ExtendedUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 4a : contrat canonique pour identity.User, en parallele de l'adaptateur legacy
 * {@code /utilisateurs/*} (inchange, voir docs/phase-4a-report.md) - toujours sans verification
 * de permission fine au-dela de ce qui est decrit ci-dessous, dans le meme esprit que "ne pas
 * corriger les bugs connus pendant la migration".
 *
 * <p>Phase 5a : {@link #changePassword} est self-only (l'appelant doit etre la cible), sans
 * introduire de nouvelle permission RBAC pour un override admin - voir docs/phase-5a-report.md.
 *
 * <p>Phase 5b-2c : {@link #findById}/{@link #findByEmail}/{@link #findAll}/{@link #delete} sont
 * scopes au tenant de l'appelant ({@code User.idEntreprise}, pas {@code organizationId}) ;
 * {@link #updatePhoto} devient self-only, meme motif que {@link #changePassword} - voir
 * docs/phase-5b2c-report.md.
 */
@Tag(name = "users")
@RestController
@Slf4j
public class UserController {

  private UserService userService;

  @Autowired
  public UserController(UserService userService) {
    this.userService = userService;
  }

  @PostMapping(value = APP_ROOT + "/users/create", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public UserDto save(@RequestBody UserDto dto) {
    return userService.save(dto);
  }

  @PostMapping(value = APP_ROOT + "/users/{id}/password", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public UserDto changePassword(@PathVariable("id") Long id, @RequestBody ChangePasswordRequest request,
      @AuthenticationPrincipal ExtendedUser principal) {
    requireSelf(id, principal);
    return userService.changePassword(id, request.motDePasse());
  }

  private void requireSelf(Long targetId, ExtendedUser principal) {
    Long callerId = principal == null ? null : principal.getIdUtilisateur();
    if (callerId == null || !callerId.equals(targetId)) {
      log.warn("User {} tried to change the password of user {}", callerId, targetId);
      throw new InvalidOperationException(
          "Vous ne pouvez changer que votre propre mot de passe",
          ErrorCodes.USER_CHANGE_PASSWORD_FORBIDDEN);
    }
  }

  // Phase 5b-2c : meme motif que requireSelf ci-dessus, applique a la photo - voir
  // docs/phase-5b2c-report.md.
  private void requireSelfForPhoto(Long targetId, ExtendedUser principal) {
    Long callerId = principal == null ? null : principal.getIdUtilisateur();
    if (callerId == null || !callerId.equals(targetId)) {
      log.warn("User {} tried to change the photo of user {}", callerId, targetId);
      throw new InvalidOperationException(
          "Vous ne pouvez modifier que votre propre photo",
          ErrorCodes.USER_UPDATE_PHOTO_FORBIDDEN);
    }
  }

  @GetMapping(value = APP_ROOT + "/users/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
  public UserDto findById(@PathVariable("id") Long id, @AuthenticationPrincipal ExtendedUser principal) {
    return userService.findById(id, principal.getIdEntreprise());
  }

  @GetMapping(value = APP_ROOT + "/users/find/{email}", produces = MediaType.APPLICATION_JSON_VALUE)
  public UserDto findByEmail(@PathVariable("email") String email, @AuthenticationPrincipal ExtendedUser principal) {
    return userService.findByEmail(email, principal.getIdEntreprise());
  }

  @GetMapping(value = APP_ROOT + "/users/all", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<UserDto> findAll(@AuthenticationPrincipal ExtendedUser principal) {
    return userService.findAll(principal.getIdEntreprise());
  }

  @DeleteMapping(value = APP_ROOT + "/users/delete/{id}")
  public void delete(@PathVariable("id") Long id, @AuthenticationPrincipal ExtendedUser principal) {
    userService.delete(id, principal.getIdEntreprise());
  }

  // Phase 4c : remplace /save/{id}/{title}/utilisateur (StrategyPhotoContext, supprime).
  @PostMapping(value = APP_ROOT + "/users/{id}/photo", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public UserDto updatePhoto(@PathVariable("id") Long id, @RequestBody PhotoUrlRequest request,
      @AuthenticationPrincipal ExtendedUser principal) {
    requireSelfForPhoto(id, principal);
    return userService.updatePhoto(id, request.url());
  }
}
