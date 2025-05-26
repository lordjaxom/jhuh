package de.hinundhergestellt.jhuh.sync;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.HasText;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.treegrid.TreeGrid;
import com.vaadin.flow.data.provider.hierarchy.AbstractBackEndHierarchicalDataProvider;
import com.vaadin.flow.data.provider.hierarchy.HierarchicalQuery;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.router.Route;
import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProductGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

import java.util.stream.Stream;

import static java.util.Comparator.comparing;

@Route
public class ArtooImportView extends VerticalLayout {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArtooImportView.class);

    private final ArtooImportService importService;

    private TreeGrid<Object> treeGrid;
    private Div progressOverlay;

    private boolean onlyReadyForSync = true;
    private boolean onlyMarkedWithErrors = false;

    public ArtooImportView(ArtooImportService importService) {
        this.importService = importService;

        setHeightFull();
        setWidth("1170px");
//        setSpacing(false);
        getStyle().setMargin("0 auto");

        createHeader();
        createFilters();
        createTreeGrid();
        createProgressOverlay();
    }

    private void createHeader() {
        var title = new H3("Artikel aus ready2order importieren");
        title.setWhiteSpace(HasText.WhiteSpace.NOWRAP);
        title.getStyle().setMargin("auto 0");

        var spacer = new Div();
        spacer.setWidthFull();

        var button = new Button("Sync mit Shopify");
        button.getStyle()
                .setPosition(Style.Position.RELATIVE)
                .setRight("5px");
        button.addClickListener(this::handleSyncWithSpotifyClick);

        var titleLayout = new HorizontalLayout(title, spacer, button);
        titleLayout.setPadding(true);
        titleLayout.setWidthFull();
        titleLayout.getStyle().setBackgroundColor("var(--lumo-primary-color-10pct)");
        add(titleLayout);
    }

    private void createFilters() {
        var spacer = new Div();
        spacer.setWidthFull();

        var readyCheckbox = new Checkbox("Bereit zum Synchronisieren");
        var errorsCheckbox = new Checkbox("Synchronisierte mit Fehlern");

        readyCheckbox.setValue(onlyReadyForSync);
        readyCheckbox.getStyle().setWhiteSpace(Style.WhiteSpace.NOWRAP);
        readyCheckbox.addValueChangeListener(event -> {
            onlyReadyForSync = event.getValue();
            if (event.getValue()) {
                errorsCheckbox.setValue(false);
            }
            treeGrid.getDataProvider().refreshAll();
        });

        errorsCheckbox.setValue(onlyMarkedWithErrors);
        errorsCheckbox.getStyle().setWhiteSpace(Style.WhiteSpace.NOWRAP);
        errorsCheckbox.addValueChangeListener(event -> {
            onlyMarkedWithErrors = event.getValue();
            if (event.getValue()) {
                readyCheckbox.setValue(false);
            }
            treeGrid.getDataProvider().refreshAll();
        });

        var filtersLayout = new HorizontalLayout(spacer, readyCheckbox, errorsCheckbox);
        filtersLayout.setWidthFull();
        add(filtersLayout);
    }

    private void createTreeGrid() {
        treeGrid = new TreeGrid<>();
        treeGrid.addHierarchyColumn(importService::getItemName)
                .setHeader("Bezeichnung")
                .setSortable(false)
                .setFlexGrow(10);
        treeGrid.addColumn(item -> importService.getItemVariations(item).map(String::valueOf).orElse(""))
                .setHeader("Variationen")
                .setSortable(false)
                .setFlexGrow(1);
        treeGrid.addColumn(new ComponentRenderer<>(this::treeItemStatusIcon))
                .setHeader("")
                .setSortable(false)
                .setWidth("32px")
                .setFlexGrow(0);
        treeGrid.addColumn(new ComponentRenderer<>(this::treeItemSyncButton))
                .setHeader("")
                .setSortable(false)
                .setWidth("80px")
                .setFlexGrow(0);
        treeGrid.setDataProvider(new TreeDataProvider());
        treeGrid.expandRecursively(treeGrid.getDataProvider().fetchChildren(new HierarchicalQuery<>(null, null)), Integer.MAX_VALUE);
        treeGrid.setSizeFull();
        treeGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        add(treeGrid);
    }

    private void createProgressOverlay() {
        var progressSpinner = new Div();
        progressSpinner.setClassName("progress-spinner");
        progressOverlay = new Div(progressSpinner);
        progressOverlay.addClassName("progress-overlay");
        progressOverlay.setVisible(false);
        add(progressOverlay);
    }

    private void handleSyncWithSpotifyClick(ClickEvent<Button> event) {
        progressOverlay.setVisible(true);
        importService
                .syncWithShopify()
                .whenComplete((ignored, throwable) -> handleSyncWithSpotifyCompleteAsync(throwable));
    }

    private void handleSyncWithSpotifyCompleteAsync(@Nullable Throwable throwable) {
        getUI().ifPresent(ui -> ui.access(() -> {
            progressOverlay.setVisible(false);
            treeGrid.getDataProvider().refreshAll();

            if (throwable != null) {
                showErrorNotification(throwable);
            }
        }));
    }

    private @Nullable Icon treeItemStatusIcon(Object item) {
        if (!importService.isSyncable(item)) {
            return null;
        }

        var ready = importService.isReadyForSync(item);
        var marked = importService.isMarkedForSync(item);
        Icon icon;
        if (!ready) {
            icon = VaadinIcon.WARNING.create();
            icon.getStyle().set("color", "var(--lumo-warning-color)");
            //Tooltip.forComponent(icon).withText("Fehler beim Laden der Artikel");
        } else if (!marked) {
            icon = VaadinIcon.CIRCLE.create();
            icon.getStyle().set("color", "lightgray");
        } else {
            icon = VaadinIcon.CHECK.create();
            icon.getStyle().set("color", "var(--lumo-success-color)");
        }
        icon.setSize("16px");
        return icon;
    }

    private @Nullable Button treeItemSyncButton(Object item) {
        if (!importService.isSyncable(item)) {
            return null;
        }

        var ready = importService.isReadyForSync(item);
        var marked = importService.isMarkedForSync(item);
        var icon = marked ? VaadinIcon.MINUS.create() : VaadinIcon.PLUS.create();
        icon.setSize("20px");
        icon.setColor(!ready ? "lightgray" : marked ? "red" : "green");
        var button = new Button(icon);
        button.setEnabled(ready);
        button.setHeight("20px");
        button.addClickListener(event -> {
            if (marked) {
                importService.markForSync(item);
            } else {
                importService.unmarkForSync(item);
            }
            treeGrid.getDataProvider().refreshItem(item);
        });
        return button;
    }

    private static void showErrorNotification(Throwable error) {
        var notification = new Notification();
        notification.setPosition(Notification.Position.TOP_CENTER);
        notification.setDuration(Integer.MAX_VALUE);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        var button = new Button(VaadinIcon.CLOSE_SMALL.create(), event -> notification.close());
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        var layout = new HorizontalLayout(VaadinIcon.WARNING.create(), new Text(error.getMessage()), button);
        layout.setAlignItems(FlexComponent.Alignment.CENTER);
        notification.add(layout);
        notification.open();
    }

    private class TreeDataProvider extends AbstractBackEndHierarchicalDataProvider<Object, Void> {

        @Override
        public Stream<Object> fetchChildrenFromBackEnd(HierarchicalQuery<Object, Void> query) {
            return query.getParentOptional()
                    .map(ArtooProductGroup.class::cast)
                    .map(it -> Stream.concat(
                            importService.findProductGroupsByParent(it),
                            importService.findProductsByProductGroup(it)
                    ))
                    .orElseGet(() -> importService.findRootProductGroups().map(Object.class::cast))
                    .filter(it -> !onlyReadyForSync || importService.filterByReadyToSync(it))
                    .filter(it -> !onlyMarkedWithErrors || importService.filterByMarkedWithErrors(it))
                    .sorted(comparing(importService::getItemName));
        }

        @Override
        public boolean hasChildren(Object item) {
            return item instanceof ArtooProductGroup productGroup && productGroup.getTypeId() != 3;
        }

        @Override
        public int getChildCount(HierarchicalQuery<Object, Void> query) {
            return (int) fetchChildren(query).count();
        }
    }
}
