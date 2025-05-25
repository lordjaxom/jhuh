package de.hinundhergestellt.jhuh;


import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.theme.Theme;
import com.vaadin.flow.theme.lumo.Lumo;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Push
@Theme(themeClass = Lumo.class)
@CssImport(value = "./styles/tree-grid-header.css", themeFor = "vaadin-grid")
@Profile("default")
public class VaadinConfiguration implements AppShellConfigurator {
}
