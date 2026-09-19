package com.utfpr.finder.acesso.internal;

import com.utfpr.finder.acesso.AcessoFacade;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
class AcessoController {
    private final AcessoFacade acesso;
    AcessoController(AcessoFacade acesso) { this.acesso = acesso; }

    @GetMapping("/login") String login() { return "login"; }

    @GetMapping("/usuarios")
    String usuarios(Model model) {
        model.addAttribute("usuarios", acesso.listarUsuarios());
        return "usuarios/lista";
    }

    @GetMapping("/usuarios/novo") @PreAuthorize("hasAuthority('usuarios:criar')")
    String novoUsuario() { return "usuarios/form"; }

    @PostMapping("/usuarios")
    String criarUsuario(@RequestParam String nome, @RequestParam String login,
            @RequestParam String senha, @RequestParam String papel, Model model) {
        try {
            acesso.criarUsuario(nome, login, senha, papel);
            return "redirect:/usuarios?criado";
        } catch (IllegalArgumentException | DataIntegrityViolationException erro) {
            model.addAttribute("erro", erro instanceof IllegalArgumentException ? erro.getMessage() : "Login já utilizado.");
            model.addAttribute("nome", nome); model.addAttribute("login", login); model.addAttribute("papel", papel);
            return "usuarios/form";
        }
    }
}
