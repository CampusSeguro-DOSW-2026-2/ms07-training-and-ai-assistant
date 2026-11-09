package edu.eci.dosw.campusseguro.training.security;

// MS07 permission codes, from the Role-Permission Matrix (x-permiso in the swagger).
public final class Permissions {

    public static final String CONTENT_READ = "ms07:content:read";
    public static final String QUIZ_TAKE = "ms07:quiz:take";
    public static final String TUTOR_USE = "ms07:tutor:use";
    public static final String CONTENT_MANAGE = "ms07:content:manage";

    private Permissions() {
    }
}
