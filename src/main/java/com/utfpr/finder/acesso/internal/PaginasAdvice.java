package com.utfpr.finder.acesso.internal;

import com.utfpr.finder.acesso.UsuarioAutenticado;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
class PaginasAdvice {
    @ModelAttribute("usuario")
    UsuarioAutenticado usuario(@AuthenticationPrincipal UsuarioAutenticado usuario) { return usuario; }
}
