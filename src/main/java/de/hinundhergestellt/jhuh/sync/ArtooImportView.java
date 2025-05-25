package de.hinundhergestellt.jhuh.sync;

import com.vaadin.flow.component.HasText;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.grid.HeaderRow;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.shared.Tooltip;
import com.vaadin.flow.component.treegrid.TreeGrid;
import com.vaadin.flow.data.provider.hierarchy.AbstractBackEndHierarchicalDataProvider;
import com.vaadin.flow.data.provider.hierarchy.HierarchicalQuery;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.router.Route;
import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProduct;
import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProductGroup;
import org.springframework.lang.Nullable;

import java.util.stream.Stream;

import static java.util.Comparator.comparing;

@Route
@CssImport(value = "./styles/tree-grid-header.css", themeFor = "vaadin-grid")
public class ArtooImportView extends VerticalLayout {

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
        var titleLayout = new HorizontalLayout(title, spacer, button);
        titleLayout.setPadding(true);
        titleLayout.setWidthFull();
        titleLayout.getStyle().setBackgroundColor("var(--lumo-primary-color-10pct)");
        add(titleLayout);
    }

    private void createTreeGrid() {
        treeGrid = new TreeGrid<>();
        treeGrid.addHierarchyColumn(this::treeItemName)
                .setHeader("Bezeichnung")
                .setSortable(false)
                .setFlexGrow(10);
        treeGrid.addColumn(this::treeItemVariations)
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
        treeGrid.addClassName("border");
        treeGrid.addClassName("compact-header");
        add(treeGrid);
    }

    private String treeItemName(Object item) {
        return switch (item) {
            case ArtooProductGroup group -> group.getName();
            case ArtooProduct product -> product.getName();
            default -> throw new IllegalStateException("Unexpected item " + item);
        };
    }

    private String treeItemVariations(Object item) {
        return switch (item) {
            case ArtooProductGroup group when group.getTypeId() == 3 ->
                    String.valueOf(importService.findProductsByProductGroup(group).count());
            case ArtooProductGroup ignored -> "";
            default -> "0";
        };
    }

    private @Nullable Icon treeItemStatusIcon(Object item) {
        var syncable = treeItemSyncable(item);
        if (syncable == null) {
            return null;
        }

        Icon icon;
        if (syncable) {
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
        var syncable = treeItemSyncable(item);
        if (syncable == null) {
            return null;
        }

        var marked = treeItemMarkedForSync(item);
        var button = new Button(marked ? "Remove" : "Add");
        button.setEnabled(syncable);
        button.setHeight("20px");
        button.addClickListener(event -> {
            switch (item) {
                case ArtooProductGroup group when marked -> importService.unmarkForSync(group);
                case ArtooProductGroup group -> importService.markForSync(group);
                case ArtooProduct product when marked -> importService.unmarkForSync(product);
                case ArtooProduct product -> importService.markForSync(product);
                default -> throw new IllegalStateException("Unexpected item " + item);
            }
            treeGrid.getDataProvider().refreshItem(item);
        });
        return button;
    }

    private @Nullable Boolean treeItemSyncable(Object item) {
        return switch (item) {
            case ArtooProductGroup group when group.getTypeId() == 3 -> importService.isSyncable(group);
            case ArtooProduct product -> importService.isSyncable(product);
            default -> null;
        };
    }

    private boolean treeItemMarkedForSync(Object item) {
        return switch (item) {
            case ArtooProductGroup group when group.getTypeId() == 3 -> importService.isMarkedForSync(group);
            case ArtooProduct product -> importService.isMarkedForSync(product);
            default -> throw new IllegalStateException("Unexpected item " + item);
        };
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
                    .sorted(comparing(ArtooImportView.this::treeItemName));
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
