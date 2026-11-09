package edu.eci.dosw.campusseguro.training.security;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// MS07 permissions of each role (x-roles in the swagger). A user with several roles gets the union.
// ADMIN only manages; AUDITOR has no MS07 permission. Role names are fixed by the matrix.
public final class RolePermissions {

    public static final List<String> ROLES = List.of("ESTUDIANTE", "PROFESOR", "ADMINISTRATIVO", "BRIGADISTA",
            "COORDINADOR", "ORGANIZADOR", "ADMIN", "AUDITOR");

    private static final Set<String> COMMUNITY =
            Set.of(Permissions.CONTENT_READ, Permissions.QUIZ_TAKE, Permissions.TUTOR_USE);

    private static final Map<String, Set<String>> BY_ROLE = Map.of(
            "ESTUDIANTE", COMMUNITY,
            "PROFESOR", COMMUNITY,
            "ADMINISTRATIVO", COMMUNITY,
            "BRIGADISTA", COMMUNITY,
            "COORDINADOR", COMMUNITY,
            "ORGANIZADOR", COMMUNITY,
            "ADMIN", Set.of(Permissions.CONTENT_MANAGE));

    private RolePermissions() {
    }

    public static Set<String> permissionsOf(Collection<String> roles) {
        Set<String> permissions = new HashSet<>();
        for (String role : roles) {
            permissions.addAll(BY_ROLE.getOrDefault(role, Set.of()));
        }
        return permissions;
    }

    public static List<String> rolesWith(String permission) {
        return ROLES.stream()
                .filter(role -> BY_ROLE.getOrDefault(role, Set.of()).contains(permission))
                .toList();
    }
}
