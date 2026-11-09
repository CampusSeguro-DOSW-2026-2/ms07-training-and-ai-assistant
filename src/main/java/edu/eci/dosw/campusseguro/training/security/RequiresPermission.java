package edu.eci.dosw.campusseguro.training.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Declares the permission an endpoint requires. OpenApiConfig turns it into x-permiso and x-roles.
// Access itself is enforced in SecurityConfig.
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {

    String value();
}
