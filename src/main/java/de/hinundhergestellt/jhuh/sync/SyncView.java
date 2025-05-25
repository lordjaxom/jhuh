package de.hinundhergestellt.jhuh.sync;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import org.springframework.lang.Nullable;

import java.util.Set;

@Route
//@RouteAlias("xxx")
public class SyncView extends VerticalLayout {

    private static final int DOWNLOAD_EXPIRY_MINUTES = 15;

//    private final DatenabrufService datenabrufService;
//    private final InetOrgPerson currentUser;

    private FormLayout formLayout;
//    private BeanValidationBinder<DatenabrufRequest> formBinder;
    private Span errorMessage;
    private VerticalLayout resultLayout;
    private Anchor download;
    private @Nullable UI currentUI;

    public SyncView() {
        setSpacing(false);
        setWidth("500px");
        getStyle().setMargin("0 auto");

        createHeaderSection();
        createFormSection();
        createResultSection();
        createRefreshSection();
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        currentUI = attachEvent.getUI();
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        currentUI = null;
    }

    private void createHeaderSection() {
        var userInfo = new Span("Angemeldet als: ");
        var logout = new Anchor("#", "Abmelden");
        logout.getStyle().setMargin("0 0 0 auto");

        var userLayout = new HorizontalLayout(userInfo, logout);
        userLayout.setPadding(true);
        userLayout.setWidthFull();
        userLayout.getStyle().setBackgroundColor("var(--lumo-primary-color-10pct)");

        var title = new H3("Datenabruf anonymisierter Akten");
        var description = new Div("Abruf von Aktendaten und Dokumenten anonymisierter Akten in Streitfällen. Der Zugriff wird im " +
                "angegebenen JIRA-Ticket zwecks Nachvollziehbarkeit protokolliert.");

        var headerLayout = new VerticalLayout(userLayout, title, description);
        headerLayout.setWidthFull();
        headerLayout.setPadding(false);

        add(headerLayout);
    }

    private void createFormSection() {
        var jiraTicket = new TextField("JIRA Ticket für berechtigtes Interesse:");
        var clz = new IntegerField("CLZ:");
        var aktenNummer = new BigDecimalField("Aktennummer:");
        var schuldnerIndex = new IntegerField("Schuldner-Index:");

        var datenauswahl = new CheckboxGroup<>("Gewünschte Daten:", "xxx", "yyy");
        datenauswahl.setThemeName("vertical");
        datenauswahl.setValue(Set.of("xxx"));

        errorMessage = new Span();
        errorMessage.getStyle().setColor("var(--lumo-error-text-color)");
        errorMessage.getStyle().setPadding("15px 0");

        var submit = new Button("Daten abrufen");
        submit.addClickListener(this::handleSubmitClick);

        formLayout = new FormLayout(jiraTicket, clz, aktenNummer, schuldnerIndex, datenauswahl, errorMessage, submit);
        formLayout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1, FormLayout.ResponsiveStep.LabelsPosition.TOP)
        );

        add(formLayout);
//
//        formBinder = new BeanValidationBinder<>(DatenabrufRequest.class);
//        formBinder.forField(jiraTicket).asRequired(DatenabrufMessages.INVALID_JIRA_TICKET).bind("jiraTicket");
//        formBinder.forField(clz).asRequired(DatenabrufMessages.INVALID_CLZ).bind("clz");
//        formBinder.forField(aktenNummer).asRequired(DatenabrufMessages.INVALID_AKTEN_NUMMER).withConverter(new BigDecimalToLongConverter())
//                .bind("aktenNummer");
//        formBinder.forField(schuldnerIndex).asRequired(DatenabrufMessages.INVALID_SCHULDNER_INDEX).bind("schuldnerIndex");
//        formBinder.forField(datenauswahl).asRequired(DatenabrufMessages.INVALID_DATENAUSWAHL).bind("datenauswahl");
//
//        formBinder.setStatusLabel(errorMessage);
    }

    private void createResultSection() {
        var result = new Div("Die Datenabfrage war erfolgreich. Der Zugriff wurde im JIRA-Ticket protokolliert. Sie können die " +
                "angeforderten Daten nun herunterladen. Der Download ist %d Minuten lang gültig.".formatted(DOWNLOAD_EXPIRY_MINUTES));
        download = new Anchor("#", "DSGVO-Aktendaten-template.zip");

        resultLayout = new VerticalLayout(result, download);
        resultLayout.setWidthFull();
        resultLayout.setPadding(false);
        resultLayout.getStyle().setMarginTop("1.5em");
        resultLayout.setVisible(false);

        add(resultLayout);
    }

    private void createRefreshSection() {
//        var refresh = new Anchor(RouteConfiguration.forSessionScope().getUrl(ReloadView.class), "Neue Abfrage");
//        refresh.getStyle().setMargin("0 0 0 auto");
//
//        var refreshLayout = new VerticalLayout(refresh);
//        refreshLayout.setWidthFull();
//        refreshLayout.setPadding(false);
//
//        add(refreshLayout);
    }

    private void handleSubmitClick(ClickEvent<Button> event) {
//        try {
//            formLayout.setEnabled(false);
//
//            var request = new DatenabrufRequest();
//            formBinder.writeBean(request);
//
//            var response = datenabrufService.prepareAndCollectAbruf(request);
//            var resource = new StreamResource(response.getFileName(), () -> datenabrufService.provideAbrufDownload(response));
//
//            download.setText(response.getFileName());
//            download.setHref(resource);
//            resultLayout.setVisible(true);
//
//            taskScheduler.schedule(this::handleDownloadExpired, OffsetDateTime.now().plusMinutes(DOWNLOAD_EXPIRY_MINUTES).toInstant());
//        } catch (ValidationException e) {
//            // Validierungsfehler werden automatisch angezeigt
//            formLayout.setEnabled(true);
//        } catch (DatenabrufException e) {
//            errorMessage.setText(e.getMessage());
//            formLayout.setEnabled(true);
//        }
    }

    private void handleDownloadExpired() {
        if (currentUI == null) {
            return; // Sitzung abgelaufen
        }

        currentUI.access(() -> {
            download.setText("Download abgelaufen");
            download.setHref("#");
            download.getStyle().setColor("var(--lumo-error-text-color)");
        });
    }
}
