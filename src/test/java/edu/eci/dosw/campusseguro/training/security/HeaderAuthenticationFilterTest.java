package edu.eci.dosw.campusseguro.training.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HeaderAuthenticationFilterTest {

    private static final String USER = "9b2d4e61-5c3a-4f8e-a7d1-2e6f8c0b4a19";

    private final HeaderAuthenticationFilter filter = new HeaderAuthenticationFilter();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private Authentication run(MockHttpServletRequest request) throws Exception {
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private MockHttpServletRequest request() {
        return new MockHttpServletRequest("GET", "/api/v1/training/categories");
    }

    private List<String> authorities(Authentication auth) {
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).sorted().toList();
    }

    @Test
    void studentRoleGrantsCommunityPermissionsWithoutPermissionHeader() throws Exception {
        MockHttpServletRequest request = request();
        request.addHeader("X-User-Id", USER);
        request.addHeader("X-User-Role", "ESTUDIANTE");

        Authentication auth = run(request);

        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo(USER);
        assertThat(authorities(auth)).containsExactly(
                Permissions.CONTENT_READ, Permissions.QUIZ_TAKE, Permissions.TUTOR_USE);
    }

    @Test
    void adminRoleOnlyGrantsContentManagement() throws Exception {
        MockHttpServletRequest request = request();
        request.addHeader("X-User-Id", USER);
        request.addHeader("X-User-Role", "ADMIN");

        Authentication auth = run(request);

        assertThat(authorities(auth)).containsExactly(Permissions.CONTENT_MANAGE);
    }

    @Test
    void auditorRoleAuthenticatesWithoutPermissions() throws Exception {
        MockHttpServletRequest request = request();
        request.addHeader("X-User-Id", USER);
        request.addHeader("X-User-Role", "AUDITOR");

        Authentication auth = run(request);

        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).isEmpty();
    }

    @Test
    void multipleRolesGrantUnionOfPermissions() throws Exception {
        MockHttpServletRequest request = request();
        request.addHeader("X-User-Id", USER);
        request.addHeader("X-User-Role", "ESTUDIANTE, ADMIN");

        Authentication auth = run(request);

        assertThat(authorities(auth)).containsExactly(
                Permissions.CONTENT_MANAGE,
                Permissions.CONTENT_READ,
                Permissions.QUIZ_TAKE,
                Permissions.TUTOR_USE);
    }

    @Test
    void unknownRoleDoesNotGrantPermissions() throws Exception {
        MockHttpServletRequest request = request();
        request.addHeader("X-User-Id", USER);
        request.addHeader("X-User-Role", "UNKNOWN_ROLE");

        Authentication auth = run(request);

        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).isEmpty();
    }

    @Test
    void missingUserIdStaysAnonymous() throws Exception {
        MockHttpServletRequest request = request();
        request.addHeader("X-User-Role", "ESTUDIANTE");

        assertThat(run(request)).isNull();
    }

    @Test
    void missingRoleStaysAnonymous() throws Exception {
        MockHttpServletRequest request = request();
        request.addHeader("X-User-Id", USER);

        assertThat(run(request)).isNull();
    }
}
