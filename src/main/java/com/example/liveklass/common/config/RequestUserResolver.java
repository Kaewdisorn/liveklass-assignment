package com.example.liveklass.common.config;

import com.example.liveklass.common.error.BusinessException;
import com.example.liveklass.common.error.ErrorCode;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class RequestUserResolver implements HandlerMethodArgumentResolver {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && RequestUser.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {

        String userIdHeader = webRequest.getHeader(USER_ID_HEADER);
        String userRoleHeader = webRequest.getHeader(USER_ROLE_HEADER);

        if (userIdHeader == null || userIdHeader.isBlank() || userRoleHeader == null || userRoleHeader.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Missing auth headers: X-User-Id, X-User-Role");
        }

        try {
            Long userId = Long.valueOf(userIdHeader);
            UserRole role = UserRole.valueOf(userRoleHeader.toUpperCase());
            return new RequestUser(userId, role);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid auth headers.");
        }
    }
}