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
 * {@code /utilisateurs/*} (inchange, voir docs/phase-4a-report.md). Meme posture de securite que
 * l'adaptateur legacy aujourd'hui : {@code authenticated()} seul (regle par defaut de
 * SecurityConfiguration), aucune verification de permission fine - pas une amelioration
 * deliberee, un choix de ne pas durcir une surface neuve au-dela de ce que l'existant offrait
 * deja, dans le meme esprit que "ne pas corriger les bugs connus pendant la migration".
 *
 * <p>Phase 5a : exception a ce principe pour {@link #changePassword} - l'IDOR (n'importe quel
 * utilisateur authentifie pouvait changer le mot de passe de n'importe quel autre) est corrige,
 * en self-only (l'appelant doit etre la cible), sans introduire de nouvelle permission RBAC pour
 * un override admin - voir docs/phase-5a-report.md.
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

  @GetMapping(value = APP_ROOT + "/users/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
  public UserDto findById(@PathVariable("id") Long id) {
    return userService.findById(id);
  }

  @GetMapping(value = APP_ROOT + "/users/find/{email}", produces = MediaType.APPLICATION_JSON_VALUE)
  public UserDto findByEmail(@PathVariable("email") String email) {
    return userService.findByEmail(email);
  }

  @GetMapping(value = APP_ROOT + "/users/all", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<UserDto> findAll() {
    return userService.findAll();
  }

  @DeleteMapping(value = APP_ROOT + "/users/delete/{id}")
  public void delete(@PathVariable("id") Long id) {
    userService.delete(id);
  }

  // Phase 4c : remplace /save/{id}/{title}/utilisateur (StrategyPhotoContext, supprime).
  @PostMapping(value = APP_ROOT + "/users/{id}/photo", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public UserDto updatePhoto(@PathVariable("id") Long id, @RequestBody PhotoUrlRequest request) {
    return userService.updatePhoto(id, request.url());
  }
}
