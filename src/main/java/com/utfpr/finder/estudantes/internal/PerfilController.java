package com.utfpr.finder.estudantes.internal;

import com.utfpr.finder.acesso.UsuarioAutenticado;
import com.utfpr.finder.empresas.EmpresasFacade;
import com.utfpr.finder.estudantes.EstudantesFacade;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
class PerfilController {
    private final EstudantesFacade estudantes;
    private final EmpresasFacade empresas;
    PerfilController(EstudantesFacade estudantes, EmpresasFacade empresas) {
        this.estudantes = estudantes; this.empresas = empresas;
    }

    @GetMapping("/perfil")
    String perfil(@AuthenticationPrincipal UsuarioAutenticado usuario, Model model) {
        if (usuario.papel().equals("EMPRESA")) {
            model.addAttribute("perfil", empresas.perfilAtual().orElseThrow());
            return "perfil/empresa-form";
        }
        model.addAttribute("perfil", estudantes.perfilAtual().orElseThrow());
        model.addAttribute("campi", EstudantesFacade.CAMPI);
        return "perfil/form";
    }

    @PostMapping("/perfil")
    String salvar(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @RequestParam(defaultValue = "") String campus, @RequestParam(defaultValue = "") String curso,
            @RequestParam(defaultValue = "") String periodo, @RequestParam(defaultValue = "") String competencias,
            @RequestParam(defaultValue = "") String resumo, @RequestParam(defaultValue = "") String atividades,
            @RequestParam(defaultValue = "") String curriculoUrl, @RequestParam(defaultValue = "") String portfolio,
            @RequestParam(defaultValue = "") String nomeFantasia, @RequestParam(defaultValue = "") String descricao,
            @RequestParam(defaultValue = "") String areas, @RequestParam(defaultValue = "") String competenciasBuscadas,
            @RequestParam(defaultValue = "") String site, @RequestParam(defaultValue = "") String linkedin) {
        if (usuario.papel().equals("EMPRESA"))
            empresas.salvar(nomeFantasia, descricao, areas, competenciasBuscadas, site, linkedin);
        else
            estudantes.salvar(campus, curso, periodo, competencias, resumo, atividades, curriculoUrl, linkedin, portfolio);
        return "redirect:/perfil?salvo";
    }
}
