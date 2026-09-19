package com.utfpr.finder.candidaturas.internal;

import com.utfpr.finder.acesso.UsuarioAutenticado;
import com.utfpr.finder.candidaturas.CandidaturasFacade;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
class CandidaturasController {
    private final CandidaturasFacade candidaturas;
    CandidaturasController(CandidaturasFacade candidaturas) { this.candidaturas = candidaturas; }

    @PostMapping("/oportunidades/{id}/candidatar")
    String candidatar(@PathVariable long id, RedirectAttributes redirect) {
        try { candidaturas.candidatar(id); redirect.addFlashAttribute("sucesso", "Candidatura enviada."); }
        catch (DataIntegrityViolationException erro) { redirect.addFlashAttribute("erro", "Você já se candidatou."); }
        catch (IllegalArgumentException erro) { redirect.addFlashAttribute("erro", erro.getMessage()); }
        return "redirect:/oportunidades/" + id;
    }

    @GetMapping("/candidaturas")
    String listar(@AuthenticationPrincipal UsuarioAutenticado usuario, Model model) {
        boolean gerencia = usuario.permissoes().contains("candidaturas:visualizar_organizacao");
        model.addAttribute("candidaturas", gerencia ? candidaturas.daOrganizacao() : candidaturas.minhas());
        model.addAttribute("gerencia", gerencia);
        return "candidaturas/lista";
    }

    @PostMapping("/candidaturas/{id}/status")
    String status(@PathVariable long id, @RequestParam String status) {
        candidaturas.alterarStatus(id, status);
        return "redirect:/candidaturas";
    }
}
