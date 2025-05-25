package de.hinundhergestellt.jhuh.sync;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.HasText;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.shared.Tooltip;
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
import org.springframework.scheduling.TaskScheduler;

import java.util.stream.Stream;

import static java.util.Comparator.comparing;

@Route
public class ArtooImportView extends VerticalLayout {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArtooImportView.class);

    private final ArtooImportService importService;

    private TreeGrid<Object> treeGrid;

    public ArtooImportView(ArtooImportService importService) {
        this.importService = importService;

        setWidth("1170px");
        setSpacing(false);
        getStyle().setMargin("0 auto");

        createHeader();
        createTreeGrid();
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
                .setWidth("120px")
                .setFlexGrow(0);
        treeGrid.setDataProvider(new TreeDataProvider());
        treeGrid.setWidthFull();
        treeGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        add(treeGrid);
    }

    private void handleSyncWithSpotifyClick(ClickEvent<Button> event) {
        var button = event.getSource();
        button.setEnabled(false);
        importService.syncWithShopify().whenComplete((unused, throwable) -> {
            button.getUI().ifPresent(ui -> ui.access(() -> {
                var notification = Notification.show("Application submitted!", 5000, Notification.Position.TOP_END);
                notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                button.setEnabled(true);
            }));
        });
    }

    private @Nullable Icon treeItemStatusIcon(Object item) {
        if (!importService.isSyncable(item)) {
            return null;
        }

        Icon icon;
        if (importService.isReadyForSync(item)) {
            icon = VaadinIcon.CHECK.create();
            icon.getStyle().set("color", "var(--lumo-success-color)");
        } else {
            icon = VaadinIcon.WARNING.create();
            icon.getStyle().set("color", "var(--lumo-warning-color)");
            Tooltip.forComponent(icon).withText("Fehler beim Laden der Artikel");
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
        var button = new Button(marked ? "Remove" : "Add");
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
