package com.kfokam48.gestiondestock.identity.presentation.rest;

import static com.kfokam48.gestiondestock.utils.Constants.APP_ROOT;

import com.kfokam48.gestiondestock.identity.application.UserService;
import com.kfokam48.gestiondestock.identity.application.dto.ChangePasswordRequest;
import com.kfokam48.gestiondestock.identity.application.dto.UserDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
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
  public UserDto changePassword(@PathVariable("id") Long id, @RequestBody ChangePasswordRequest request) {
    return userService.changePassword(id, request.motDePasse());
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
}
