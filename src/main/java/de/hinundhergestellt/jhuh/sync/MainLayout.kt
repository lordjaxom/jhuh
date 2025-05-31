package de.hinundhergestellt.jhuh.sync

import com.vaadin.flow.component.applayout.AppLayout
import com.vaadin.flow.component.applayout.DrawerToggle
import com.vaadin.flow.component.html.H1
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.orderedlayout.Scroller
import com.vaadin.flow.component.sidenav.SideNav
import com.vaadin.flow.component.sidenav.SideNavItem
import com.vaadin.flow.router.Layout
import com.vaadin.flow.router.PageTitle
import com.vaadin.flow.theme.lumo.LumoUtility

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
        sideNav.addItem(SideNavItem("Shopify-Abgleich", "/artooimport", VaadinIcon.UPLOAD.create()))

        val scroller = Scroller(sideNav)
        scroller.className = LumoUtility.Padding.SMALL
        addToDrawer(scroller)
    }

    override fun afterNavigation() {
        super.afterNavigation()

        val currentPageTitle = content.javaClass.getAnnotation(PageTitle::class.java).value
        pageTitle.text ="Hin- und Hergestellt / $currentPageTitle"
    }
}