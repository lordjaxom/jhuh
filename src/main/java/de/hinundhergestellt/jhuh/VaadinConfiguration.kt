package de.hinundhergestellt.jhuh

import com.vaadin.flow.component.page.AppShellConfigurator
import com.vaadin.flow.component.page.Push
import com.vaadin.flow.theme.Theme
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Push
@Theme("jhuh")
@Profile("default")
class VaadinConfiguration : AppShellConfigurator
