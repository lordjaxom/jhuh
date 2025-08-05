package de.hinundhergestellt.jhuh.components

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.applayout.AppLayout
import com.vaadin.flow.component.applayout.DrawerToggle
import com.vaadin.flow.component.html.H1
import com.vaadin.flow.component.icon.AbstractIcon
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.orderedlayout.Scroller
import com.vaadin.flow.component.sidenav.SideNav
import com.vaadin.flow.component.sidenav.SideNavItem
import com.vaadin.flow.router.Layout
import com.vaadin.flow.router.PageTitle
import com.vaadin.flow.router.RouteConfiguration
import com.vaadin.flow.theme.lumo.LumoUtility
import de.hinundhergestellt.jhuh.usecases.labels.LabelGeneratorView
import de.hinundhergestellt.jhuh.usecases.maintenance.MaintenanceView
import de.hinundhergestellt.jhuh.usecases.products.ProductManagerView
import de.hinundhergestellt.jhuh.usecases.shopify.ShopifySynchronizationView
import de.hinundhergestellt.jhuh.usecases.vendors.VendorManagerView

@Layout
@Suppress("unused")
class MainLayout : AppLayout() {

    private val pageTitle: H1

    init {
        val toggle = DrawerToggle()
        pageTitle = H1("Hin- und Hergestellt / Dashboard").apply {
            style.setFontSize("var(--lumo-font-size-l)")
            style.setMargin("0")
        }
        addToNavbar(toggle, pageTitle)

        val sideNav = SideNav()
        sideNav.addItem<ProductManagerView>(VaadinIcon.FILE_TREE.create())
        sideNav.addItem<VendorManagerView>(VaadinIcon.CUBES.create())
        sideNav.addItem<ShopifySynchronizationView>(CustomIcon.SHOPIFY.create())
        sideNav.addItem<LabelGeneratorView>(VaadinIcon.PRINT.create())
        sideNav.addItem<MaintenanceView>(VaadinIcon.COGS.create())

        val scroller = Scroller(sideNav)
        scroller.className = LumoUtility.Padding.SMALL
        addToDrawer(scroller)
    }

    override fun afterNavigation() {
        super.afterNavigation()
        pageTitle.text = "Hin- und Hergestellt / ${content.pageTitle}"
    }
}

private inline fun <reified T: Component> SideNav.addItem(icon: AbstractIcon<*>) =
    addItem(SideNavItem(pageTitle<T>(), routeUrl<T>(), icon))

private inline fun <reified T : Component> pageTitle() =
    T::class.java.getAnnotation(PageTitle::class.java)!!.value

private inline fun <reified T : Component> routeUrl() =
    RouteConfiguration.forSessionScope().getUrl(T::class.java)

private inline val <reified T: Component> T.pageTitle
    get() = this::class.java.getAnnotation(PageTitle::class.java)!!.value
