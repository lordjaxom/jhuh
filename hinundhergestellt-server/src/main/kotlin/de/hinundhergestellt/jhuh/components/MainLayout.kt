package de.hinundhergestellt.jhuh.components

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.applayout.AppLayout
import com.vaadin.flow.component.applayout.DrawerToggle
import com.vaadin.flow.component.html.H1
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.orderedlayout.Scroller
import com.vaadin.flow.component.sidenav.SideNav
import com.vaadin.flow.component.sidenav.SideNavItem
import com.vaadin.flow.router.Layout
import com.vaadin.flow.router.PageTitle
import com.vaadin.flow.router.RouteConfiguration
import com.vaadin.flow.theme.lumo.LumoUtility
import de.hinundhergestellt.jhuh.usecases.incoming.IncomingGoodsView
import de.hinundhergestellt.jhuh.usecases.labels.LabelGeneratorView
import de.hinundhergestellt.jhuh.usecases.products.ProductManagerView
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
        sideNav.addItem<ProductManagerView>(VaadinIcon.UPLOAD)
        sideNav.addItem<VendorManagerView>(VaadinIcon.VAADIN_H)
        sideNav.addItem<IncomingGoodsView>(VaadinIcon.ARROW_BACKWARD)
        sideNav.addItem<LabelGeneratorView>(VaadinIcon.PRINT)

        val scroller = Scroller(sideNav)
        scroller.className = LumoUtility.Padding.SMALL
        addToDrawer(scroller)
    }

    override fun afterNavigation() {
        super.afterNavigation()
        pageTitle.text = "Hin- und Hergestellt / ${content.pageTitle}"
    }
}

private inline fun <reified T: Component> SideNav.addItem(icon: VaadinIcon) =
    addItem(SideNavItem(pageTitle<T>(), routeUrl<T>(), icon.create()))

private inline fun <reified T : Component> pageTitle() =
    T::class.java.getAnnotation(PageTitle::class.java)!!.value

private inline fun <reified T : Component> routeUrl() =
    RouteConfiguration.forSessionScope().getUrl(T::class.java)

private inline val <reified T: Component> T.pageTitle
    get() = this::class.java.getAnnotation(PageTitle::class.java)!!.value
