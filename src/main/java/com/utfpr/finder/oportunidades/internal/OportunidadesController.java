package com.utfpr.finder.oportunidades.internal;

import com.utfpr.finder.acesso.UsuarioAutenticado;
import com.utfpr.finder.estudantes.EstudantesFacade;
import com.utfpr.finder.oportunidades.OportunidadesFacade;
import com.utfpr.finder.oportunidades.OportunidadesFacade.Nova;
import java.time.LocalDate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
class OportunidadesController {
    private final OportunidadesFacade oportunidades;
    OportunidadesController(OportunidadesFacade oportunidades) { this.oportunidades = oportunidades; }

    @GetMapping("/")
    String inicio(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @RequestParam(defaultValue = "") String busca, @RequestParam(defaultValue = "") String aba,
            @RequestParam(defaultValue = "") String campus, Model model) {
        if (aba.isBlank()) aba = usuario.papel().equals("ESTUDANTE") ? "para-mim" : "gestao";
        model.addAttribute("oportunidades", oportunidades.listar(busca, aba, campus));
        model.addAttribute("busca", busca);
        model.addAttribute("aba", aba);
        model.addAttribute("campus", campus);
        model.addAttribute("campi", EstudantesFacade.CAMPI);
        return "oportunidades/lista";
    }
    @GetMapping("/oportunidades/nova") @PreAuthorize("hasAuthority('oportunidades:criar')")
    String nova(Model model) { model.addAttribute("campi", EstudantesFacade.CAMPI); return "oportunidades/form"; }

    @PostMapping("/oportunidades")
    String criar(@RequestParam String titulo, @RequestParam String responsavel, @RequestParam String tipo,
            @RequestParam String modalidade, @RequestParam(defaultValue = "") String localidade,
            @RequestParam String campus, @RequestParam String descricao,
            @RequestParam(defaultValue = "") String curso, @RequestParam(defaultValue = "") String competencias,
            @RequestParam(required = false) LocalDate prazo, @RequestParam String status, Model model) {
        try {
            long id = oportunidades.criar(new Nova(titulo, responsavel, tipo, modalidade, localidade,
                campus, descricao, curso, competencias, prazo, status));
            return "redirect:/oportunidades/" + id + "?criada";
        } catch (IllegalArgumentException erro) {
            model.addAttribute("erro", erro.getMessage());
            model.addAttribute("campi", EstudantesFacade.CAMPI);
            return "oportunidades/form";
        }
    }
    @GetMapping("/oportunidades/{id}")
    String detalhe(@PathVariable long id, Model model) {
        model.addAttribute("oportunidade", oportunidades.buscar(id));
        return "oportunidades/detalhe";
    }
    @PostMapping("/oportunidades/{id}/status")
    String status(@PathVariable long id, @RequestParam String status) {
        if (status.equals("APROVADA") || status.equals("REJEITADA")) oportunidades.revisar(id, status);
        else oportunidades.enviarOuEncerrar(id, status);
        return "redirect:/oportunidades/" + id + "?atualizada";
    }
}
