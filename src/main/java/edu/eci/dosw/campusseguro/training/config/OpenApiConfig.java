package edu.eci.dosw.campusseguro.training.config;

import edu.eci.dosw.campusseguro.training.exception.ErrorResponse;
import edu.eci.dosw.campusseguro.training.security.HeaderAuthenticationFilter;
import edu.eci.dosw.campusseguro.training.security.RequiresPermission;
import edu.eci.dosw.campusseguro.training.security.RolePermissions;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// Local Swagger that follows the integration standards: identity headers, x-permiso / x-roles
// and the common error responses. The official contract lives in the documentation repository.
@Configuration
public class OpenApiConfig {

    private static final String JSON = "application/json";
    private static final String PARAMETERS = "#/components/parameters/";
    private static final String RESPONSES = "#/components/responses/";
    private static final String CORRELATION_HEADER_REF = "#/components/headers/XCorrelationID";
    private static final String CORRELATION_EXAMPLE = "7b0c8f2e-3c1d-4f5a-9e6b-1a2b3c4d5e6f";

    @Bean
    public OpenAPI trainingOpenApi() {
        Components components = new Components()
                .addHeaders("XCorrelationID", new Header()
                        .description("UUID v4 that links logs and audit events. MS07 generates one if missing.")
                        .schema(new StringSchema().format("uuid").example(CORRELATION_EXAMPLE)))
                .addParameters("XUserId", header(HeaderAuthenticationFilter.USER_ID, true,
                        "Id (UUID) of the authenticated user. Set by the API Gateway.",
                        "9b2d4e61-5c3a-4f8e-a7d1-2e6f8c0b4a19"))
                .addParameters("XUserRole", header(HeaderAuthenticationFilter.USER_ROLE, true,
                        "Comma-separated roles of the authenticated user. Set by the API Gateway.",
                        "BRIGADISTA,ESTUDIANTE"))
                .addParameters("XCorrelationId", header(CorrelationIdFilter.HEADER, false,
                        "Request trace id (UUID v4).", CORRELATION_EXAMPLE))
                .addResponses("BadRequest", error("Validation failed: states the field and the rule.",
                        400, "VALIDACION", "Datos inválidos.",
                        List.of(new ErrorResponse.FieldError("name", "El nombre es obligatorio."))))
                .addResponses("Unauthorized", error("Identity headers are missing (X-User-Id / X-User-Role).",
                        401, "NO_AUTENTICADO", "Faltan las cabeceras de identidad.", List.of()))
                .addResponses("Forbidden", error("X-User-Role has none of the roles in x-roles.",
                        403, "PROHIBIDO", "No tiene permiso para realizar esta operación.", List.of()))
                .addResponses("NotFound", error("The resource does not exist.",
                        404, "NO_ENCONTRADO", "La categoría no existe.", List.of()))
                .addResponses("InternalError", error("Unexpected error; details stay in the MS07 log.",
                        500, "ERROR_INTERNO", "Ocurrió un error inesperado. Intente de nuevo más tarde.", List.of()));
        ModelConverters.getInstance().readAll(ErrorResponse.class).forEach(components::addSchemas);

        return new OpenAPI()
                .info(new Info()
                        .title("MS07 Training and AI assistant")
                        .version("v1")
                        .description("Learning center, contents, evaluations, progress and AI tutor of CampusSeguro.\n\n"
                                + "**Identity inputs:** protected endpoints receive `X-User-Id` and `X-User-Role` "
                                + "(comma-separated roles), set by the API Gateway; all of them accept "
                                + "`X-Correlation-ID`. If the identity headers are missing, MS07 answers 401. "
                                + "Each operation declares its permission in `x-permiso` and the roles that have it "
                                + "in `x-roles`."))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("External access (API Gateway)"),
                        new Server().url("http://localhost:8087").description("MS07 directly, development only")))
                .components(components);
    }

    @Bean
    public OperationCustomizer integrationStandardCustomizer() {
        return (operation, handlerMethod) -> {
            RequiresPermission permission = handlerMethod.getMethodAnnotation(RequiresPermission.class);
            if (permission != null) {
                operation.addExtension("x-permiso", permission.value());
                operation.addExtension("x-roles", RolePermissions.rolesWith(permission.value()));
            }
            addHeaders(operation, permission != null);
            addResponses(operation, handlerMethod, permission != null);
            return operation;
        };
    }

    private static void addHeaders(Operation operation, boolean secured) {
        List<Parameter> parameters = new ArrayList<>();
        if (secured) {
            parameters.add(new Parameter().$ref(PARAMETERS + "XUserId"));
            parameters.add(new Parameter().$ref(PARAMETERS + "XUserRole"));
        }
        parameters.add(new Parameter().$ref(PARAMETERS + "XCorrelationId"));
        if (operation.getParameters() != null) {
            parameters.addAll(operation.getParameters());
        }
        operation.setParameters(parameters);
    }

    private static void addResponses(Operation operation, HandlerMethod handlerMethod, boolean secured) {
        ApiResponses responses = new ApiResponses();
        boolean creates = handlerMethod.hasMethodAnnotation(PostMapping.class);
        responses.addApiResponse(creates ? "201" : "200", new ApiResponse()
                .description(creates ? "Created." : "OK.")
                .content(json(schemaOf(handlerMethod))));
        if (hasParameterAnnotated(handlerMethod, PathVariable.class, RequestParam.class, RequestBody.class)) {
            responses.addApiResponse("400", new ApiResponse().$ref(RESPONSES + "BadRequest"));
        }
        if (secured) {
            responses.addApiResponse("401", new ApiResponse().$ref(RESPONSES + "Unauthorized"));
            responses.addApiResponse("403", new ApiResponse().$ref(RESPONSES + "Forbidden"));
        }
        if (hasParameterAnnotated(handlerMethod, PathVariable.class)) {
            responses.addApiResponse("404", new ApiResponse().$ref(RESPONSES + "NotFound"));
        }
        if (operation.getResponses() != null) {
            operation.getResponses().forEach((code, declared) -> {
                if (code.matches("[45]\\d\\d")) {
                    responses.addApiResponse(code, declared.content(json(errorSchema())));
                }
            });
        }
        responses.addApiResponse("500", new ApiResponse().$ref(RESPONSES + "InternalError"));
        responses.values().stream()
                .filter(r -> r.get$ref() == null)
                .forEach(r -> r.addHeaderObject(CorrelationIdFilter.HEADER, new Header().$ref(CORRELATION_HEADER_REF)));
        operation.setResponses(responses);
    }

    @SafeVarargs
    private static boolean hasParameterAnnotated(HandlerMethod handlerMethod,
                                                 Class<? extends Annotation>... annotations) {
        return Arrays.stream(handlerMethod.getMethodParameters())
                .anyMatch(p -> Arrays.stream(annotations).anyMatch(p::hasParameterAnnotation));
    }

    private static Schema<?> schemaOf(HandlerMethod handlerMethod) {
        ResolvableType type = ResolvableType.forMethodReturnType(handlerMethod.getMethod());
        if (ResponseEntity.class.equals(type.toClass())) {
            type = type.getGeneric(0);
        }
        if (List.class.isAssignableFrom(type.toClass())) {
            return new ArraySchema().items(ref(type.getGeneric(0).toClass()));
        }
        return ref(type.toClass());
    }

    private static Schema<?> ref(Class<?> type) {
        return new Schema<>().$ref("#/components/schemas/" + type.getSimpleName());
    }

    private static Schema<?> errorSchema() {
        return ref(ErrorResponse.class);
    }

    private static Content json(Schema<?> schema) {
        return new Content().addMediaType(JSON, new MediaType().schema(schema));
    }

    private static ApiResponse error(String description, int status, String code, String message,
                                     List<ErrorResponse.FieldError> errors) {
        return new ApiResponse()
                .description(description)
                .addHeaderObject(CorrelationIdFilter.HEADER, new Header().$ref(CORRELATION_HEADER_REF))
                .content(new Content().addMediaType(JSON, new MediaType()
                        .schema(errorSchema())
                        .example(new ErrorResponse(status, code, message, errors, CORRELATION_EXAMPLE))));
    }

    private static Parameter header(String name, boolean required, String description, String example) {
        return new Parameter()
                .in("header")
                .name(name)
                .required(required)
                .description(description)
                .schema(new StringSchema().example(example));
    }
}
