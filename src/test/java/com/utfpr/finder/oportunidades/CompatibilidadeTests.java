package com.utfpr.finder.oportunidades;

import static org.assertj.core.api.Assertions.assertThat;
import com.utfpr.finder.estudantes.EstudantesFacade.Perfil;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompatibilidadeTests {
    private final Perfil perfil = new Perfil("Campo Mourão", "Engenharia de Software", "5º",
        List.of("java", "sql"), "", "", "", "", "");

    @Test void atendeTodosOsCriterios() {
        assertThat(OportunidadesFacade.compatibilidade(perfil, "Engenharia de Software", List.of("java", "sql")))
            .isEqualTo(100);
    }

    @Test void calculaParcialDeFormaExplicavel() {
        assertThat(OportunidadesFacade.compatibilidade(perfil, "Engenharia de Software", List.of("java", "comunicacao")))
            .isEqualTo(67);
    }

    @Test void oportunidadeSemCriterioServeParaTodos() {
        assertThat(OportunidadesFacade.compatibilidade(perfil, "", List.of())).isEqualTo(100);
    }
}
