package com.wokcontrol.domicilios.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** v2 · AC-053.3: sin la cadena de conexión a MongoDB el servicio no arranca y lo dice claramente (sin mostrar secretos). */
class ValidacionArranqueTest {

    @Test
    void sinCadenaDeConexionNoArranca() {
        assertThatThrownBy(() -> ValidacionArranque.verificar(new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class).hasMessage(ValidacionArranque.MENSAJE);
        assertThatThrownBy(() -> ValidacionArranque.verificar(new MockEnvironment().withProperty("spring.mongodb.uri", "  ")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ValidacionArranque.verificar(new MockEnvironment().withProperty("spring.mongodb.uri", "localhost:27017")))
                .isInstanceOf(IllegalStateException.class).message().doesNotContain("localhost");
    }

    @Test
    void conCadenaValidaArranca() {
        assertThatCode(() -> ValidacionArranque.verificar(new MockEnvironment()
                .withProperty("spring.mongodb.uri", "mongodb+srv://wok:secreto@cluster0.example.net/wok_domicilios")))
                .doesNotThrowAnyException();
        assertThatCode(() -> ValidacionArranque.verificar(new MockEnvironment()
                .withProperty("spring.mongodb.uri", "mongodb://localhost:27017/wok_domicilios")))
                .doesNotThrowAnyException();
    }
}
