package dev.monkeypatch.rctiming.modules;

import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ModuleInterceptorTest {

    @RequiresModule("screens")
    static class ScreensController {
        public void list() {}
    }

    static class MethodGuarded {
        @RequiresModule("practice")
        public void start() {}

        public void open() {}
    }

    private final ModuleSwitch moduleSwitch = mock(ModuleSwitch.class);
    private final ModuleInterceptor interceptor = new ModuleInterceptor(moduleSwitch);

    private boolean pass(Object bean, String method) throws Exception {
        return interceptor.preHandle(null, null, new HandlerMethod(bean, bean.getClass().getMethod(method)));
    }

    @Test
    void checksTheModuleOfAnAnnotatedController() throws Exception {
        assertThat(pass(new ScreensController(), "list")).isTrue();

        verify(moduleSwitch).require("screens");
    }

    @Test
    void checksTheModuleOfAnAnnotatedMethodOnly() throws Exception {
        pass(new MethodGuarded(), "start");
        pass(new MethodGuarded(), "open");

        verify(moduleSwitch).require("practice");
        verify(moduleSwitch, org.mockito.Mockito.times(1)).require(anyString());
    }

    @Test
    void aSwitchedOffModuleStopsTheRequest() {
        doThrow(new ModuleDisabledException("screens")).when(moduleSwitch).require("screens");

        assertThatThrownBy(() -> pass(new ScreensController(), "list")).isInstanceOf(ModuleDisabledException.class);
    }

    @Test
    void ignoresHandlersThatAreNotControllerMethods() {
        assertThat(interceptor.preHandle(null, null, new Object())).isTrue();

        verify(moduleSwitch, never()).require(anyString());
    }
}
