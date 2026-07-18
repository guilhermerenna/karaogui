package com.karaogui.backend.auth;

import com.karaogui.backend.error.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

public class PlayerIdentityResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return PlayerIdentity.class.equals(parameter.getParameterType());
    }

    @Override
    public PlayerIdentity resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        PlayerIdentity identity = (PlayerIdentity) request.getAttribute(TokenAuthFilter.IDENTITY_ATTR);
        if (identity == null) {
            throw new UnauthorizedException("UNAUTHORIZED", "Valid session token required.");
        }
        return identity;
    }
}
