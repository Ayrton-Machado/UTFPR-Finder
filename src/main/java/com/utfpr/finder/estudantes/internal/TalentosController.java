package com.utfpr.finder.estudantes.internal;

import com.utfpr.finder.estudantes.EstudantesFacade;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
class TalentosController {
    private final EstudantesFacade estudantes;
    TalentosController(EstudantesFacade estudantes) { this.estudantes = estudantes; }

    @GetMapping("/talentos")
    String talentos(@RequestParam(defaultValue = "") String busca,
            @RequestParam(defaultValue = "") String campus, Model model) {
        model.addAttribute("talentos", estudantes.buscar(busca, campus));
        model.addAttribute("busca", busca);
        model.addAttribute("campus", campus);
        model.addAttribute("campi", EstudantesFacade.CAMPI);
        return "talentos/lista";
    }
}
