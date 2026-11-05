package com.schoolerp.shared.config;

import com.schoolerp.shared.tenancy.PlatformKeyFilter;
import com.schoolerp.shared.tenancy.TenantFilter;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 / Swagger UI configuration with multi-tenant header support and security schemes
 * for both school JWT authentication and platform operator API key authentication.
 */
@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "BearerAuth";
    public static final String PLATFORM_KEY_AUTH = "PlatformKeyAuth";

    @Bean
    public OpenAPI schoolErpOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("School ERP Multi-Tenant API")
                        .version("0.1.0")
                        .description("""
                                Multi-tenant School/University ERP SaaS API (database-per-school).
                                
                                - **Platform APIs (`/api/v1/platform/**`)**: Authorize with `PlatformKeyAuth` (`X-Platform-Key` header, default in dev: `dev-platform-key`).
                                - **Tenant APIs (`/api/v1/**`)**: Provide the school slug in the `X-Tenant-ID` header and authorize with `BearerAuth` (JWT access token from `POST /api/v1/auth/login`).
                                """)
                        .contact(new Contact().name("School ERP Platform Team")))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .name(BEARER_AUTH)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT access token issued by `/api/v1/auth/login`. Carries user ID and tenant slug."))
                        .addSecuritySchemes(PLATFORM_KEY_AUTH, new SecurityScheme()
                                .name(PlatformKeyFilter.PLATFORM_HEADER)
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .description("Platform operator key (`X-Platform-Key`). Dev default: `dev-platform-key`.")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }

    @Bean
    public OperationCustomizer tenantHeaderOperationCustomizer() {
        return (operation, handlerMethod) -> {
            Class<?> beanType = handlerMethod.getBeanType();
            String pkg = beanType.getPackageName();
            boolean isPlatformTenantEndpoint = beanType.getSimpleName().equals("PlatformTenantController")
                    || pkg.endsWith(".platform") && !beanType.getSimpleName().equals("TenantConfigController");
            if (!isPlatformTenantEndpoint) {
                operation.addParametersItem(new Parameter()
                        .in("header")
                        .name(TenantFilter.TENANT_HEADER)
                        .description("Tenant school slug (3-24 lowercase alphanumeric characters, e.g. `greenwood`)")
                        .required(true)
                        .example("greenwood")
                        .schema(new StringSchema()));
            }
            return operation;
        };
    }
}

