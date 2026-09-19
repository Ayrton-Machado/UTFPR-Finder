package com.utfpr.finder;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {
    @Test void arquiteturaModularPermaneceValida() {
        ApplicationModules.of(FinderApplication.class).verify();
    }
}
