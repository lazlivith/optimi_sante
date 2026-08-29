package com.optimisante.backend.infrastructure.pdf;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.ITemplateResolver;

@Configuration
public class PdfConfig {

    /**
     * Moteur Thymeleaf isolé spécifiquement pour la génération de PDF.
     * Cela permet de ne pas interférer avec d'autres éventuels moteurs de templates web
     * et de cibler directement le répertoire /templates/pdf/.
     */
    @Bean(name = "pdfTemplateEngine")
    public TemplateEngine pdfTemplateEngine() {
        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.addTemplateResolver(pdfTemplateResolver());
        return templateEngine;
    }

    private ITemplateResolver pdfTemplateResolver() {
        ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
        // On cible spécifiquement le sous-dossier pdf/ dans les ressources
        templateResolver.setPrefix("/templates/pdf/");
        templateResolver.setSuffix(".html");
        templateResolver.setTemplateMode(TemplateMode.HTML);
        templateResolver.setCharacterEncoding("UTF-8");
        templateResolver.setOrder(1);
        templateResolver.setCheckExistence(true);
        return templateResolver;
    }
}
