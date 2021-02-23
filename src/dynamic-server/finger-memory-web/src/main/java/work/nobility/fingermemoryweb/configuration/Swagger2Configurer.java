package work.nobility.fingermemoryweb.configuration;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Contact;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@Configuration
@EnableSwagger2
public class Swagger2Configurer {
  @Bean
  public Docket createRestApi() {
    ApiInfo apiInfo = new ApiInfoBuilder()
        .title("FingerMemory-Web") //标题
        .description("使用Spring Boot开发，且仅用于动态数据的Api文档")
        .contact(new Contact("nobility-zhang",
            "http://nobility.work",
            "nobility-zhang@foxmail.com"))
        .version("1.0")
        .build();
    Docket docket = new Docket(DocumentationType.SWAGGER_2)
        .apiInfo(apiInfo)
        .select()
        .apis(RequestHandlerSelectors.basePackage("work.nobility.fingermemoryweb.controller"))
        .paths(PathSelectors.any())
        .build();
    return docket;
  }
}
